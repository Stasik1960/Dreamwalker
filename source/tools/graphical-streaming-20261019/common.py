import ctypes,hashlib,json,os,subprocess,time
from ctypes import wintypes
from pathlib import Path
PROJECT=Path(__file__).absolute().parents[2];ROOT=PROJECT.parent
OUT=ROOT/'research/graphical-streaming-20261019';TOOLS=Path(__file__).parent
JAVA_HOME=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma')
JAVA=JAVA_HOME/'bin/java.exe'
CLIENT=PROJECT/'build/runtime-client-rp-streaming7';SERVER=PROJECT/'build/runtime-server-rp-streaming7'
SOURCE=ROOT/'deliverables/collision-streaming-20261018/map/bloodborne';WORLD=SERVER/'bloodborne'
KIT=Path(r'C:\Users\vakir\Desktop\Dreamwalker-RP-Test-20261019')
BASE=ROOT/'deliverables/streaming-6-20261019/game/dreamwalker-bb-materials.1-streaming.6.jar'
PRODUCTION_INPUTS=['gradle.properties','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeBlockEntity.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeClient.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeNetworking.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeInitialSync.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/ShardedLedgerTrackingMixin.java','src/architecture/resources/bloodborne_dw.composite.mixins.json','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/LithiumPaletteAllocationMixin.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ShardedCompositeLedger.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MembershipNbtReader.java']

def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()
def write(p,value):p.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def ordinary(p):
    if p.is_symlink() or getattr(p.lstat(),'st_file_attributes',0)&1024:raise ValueError('Reparse refused: '+str(p))
    return p
def files(folder):
    for p in sorted(ordinary(folder).iterdir()):
        ordinary(p)
        if p.name=='session.lock' or (p.is_dir() and p.name.startswith('dw-') and 'history' in p.name):continue
        if p.is_dir():yield from files(p)
        elif p.is_file():yield p
def manifest(folder):return [{'path':p.relative_to(folder).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)} for p in files(folder)]
def java_processes():
    command="Get-CimInstance Win32_Process -Filter \"name = 'java.exe' OR name = 'javaw.exe'\" | Select-Object ProcessId,ExecutablePath | ConvertTo-Json -Compress"
    p=subprocess.run(['powershell.exe','-NoProfile','-Command',command],capture_output=True,text=True,creationflags=subprocess.CREATE_NO_WINDOW,check=True)
    data=json.loads(p.stdout) if p.stdout.strip() else []
    return data if isinstance(data,list) else [data]
def wait_foreign(own=()):
    OUT.mkdir(parents=True,exist_ok=True)
    while True:
        foreign=[p for p in java_processes() if p['ProcessId'] not in own]
        if not foreign:return
        write(OUT/'waiting-for-other-chat.json',{'status':'WAITING_NO_FOREIGN_PROCESS_STOPPED','utc':time.time(),'processes':foreign})
        time.sleep(10)
def argsfile(path,args):path.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n',encoding='utf-8')

class Counters(ctypes.Structure):
    _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD),('PeakWorkingSetSize',ctypes.c_size_t),('WorkingSetSize',ctypes.c_size_t),('QuotaPeakPagedPoolUsage',ctypes.c_size_t),('QuotaPagedPoolUsage',ctypes.c_size_t),('QuotaPeakNonPagedPoolUsage',ctypes.c_size_t),('QuotaNonPagedPoolUsage',ctypes.c_size_t),('PagefileUsage',ctypes.c_size_t),('PeakPagefileUsage',ctypes.c_size_t),('PrivateUsage',ctypes.c_size_t)]
class MemoryStatus(ctypes.Structure):
    _fields_=[('length',wintypes.DWORD),('load',wintypes.DWORD),('totalPhysical',ctypes.c_ulonglong),('availablePhysical',ctypes.c_ulonglong),('totalPageFile',ctypes.c_ulonglong),('availablePageFile',ctypes.c_ulonglong),('totalVirtual',ctypes.c_ulonglong),('availableVirtual',ctypes.c_ulonglong),('extended',ctypes.c_ulonglong)]
class PdhUnion(ctypes.Union):
    _fields_=[('longValue',wintypes.LONG),('doubleValue',ctypes.c_double),('largeValue',ctypes.c_longlong)]
class PdhValue(ctypes.Structure):
    _fields_=[('status',wintypes.DWORD),('value',PdhUnion)]
class Sampler:
    def __init__(self):
        self.kernel=ctypes.WinDLL('kernel32',use_last_error=True);self.kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];self.kernel.OpenProcess.restype=wintypes.HANDLE
        self.psapi=ctypes.WinDLL('psapi',use_last_error=True);self.psapi.GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.c_void_p,wintypes.DWORD]
        self.kernel.GetProcessTimes.argtypes=[wintypes.HANDLE,*([ctypes.POINTER(wintypes.FILETIME)]*4)]
        self.handles={}
        self.query=wintypes.HANDLE();self.counters={};self.pdh=ctypes.WinDLL('pdh')
        self.pdh.PdhOpenQueryW.argtypes=[wintypes.LPCWSTR,ctypes.c_size_t,ctypes.POINTER(wintypes.HANDLE)]
        self.pdh.PdhAddEnglishCounterW.argtypes=[wintypes.HANDLE,wintypes.LPCWSTR,ctypes.c_size_t,ctypes.POINTER(wintypes.HANDLE)]
        self.pdh.PdhCollectQueryData.argtypes=[wintypes.HANDLE]
        self.pdh.PdhGetFormattedCounterValue.argtypes=[wintypes.HANDLE,wintypes.DWORD,ctypes.POINTER(wintypes.DWORD),ctypes.POINTER(PdhValue)]
        self.pdh.PdhCloseQuery.argtypes=[wintypes.HANDLE]
        if self.pdh.PdhOpenQueryW(None,0,ctypes.byref(self.query))==0:
            for name in ['Pages Input/sec','Page Reads/sec']:
                counter=wintypes.HANDLE()
                if self.pdh.PdhAddEnglishCounterW(self.query,'\\Memory\\'+name,0,ctypes.byref(counter))==0:self.counters[name]=counter
            self.pdh.PdhCollectQueryData(self.query)
    def process(self,label,pid):
        if pid not in self.handles:self.handles[pid]=self.kernel.OpenProcess(0x0410,False,pid)
        c=Counters();c.cb=ctypes.sizeof(c)
        if self.psapi.GetProcessMemoryInfo(self.handles[pid],ctypes.byref(c),ctypes.sizeof(c)):
            row={'label':label,'pid':pid,'workingSet':c.WorkingSetSize,'privateBytes':c.PrivateUsage,'pageFaultCount':c.PageFaultCount,'processLifetimePeakWorkingSet':c.PeakWorkingSetSize}
            created=wintypes.FILETIME();exited=wintypes.FILETIME();kernel=wintypes.FILETIME();user=wintypes.FILETIME()
            if self.kernel.GetProcessTimes(self.handles[pid],ctypes.byref(created),ctypes.byref(exited),ctypes.byref(kernel),ctypes.byref(user)):
                row['cpuSeconds']=((kernel.dwHighDateTime<<32)+kernel.dwLowDateTime+(user.dwHighDateTime<<32)+user.dwLowDateTime)/1e7
            return row
    def sample(self,processes):
        m=MemoryStatus();m.length=ctypes.sizeof(m);self.kernel.GlobalMemoryStatusEx(ctypes.byref(m))
        rates={}
        if self.counters:
            self.pdh.PdhCollectQueryData(self.query)
            for name,counter in self.counters.items():
                value=PdhValue();typ=wintypes.DWORD()
                if self.pdh.PdhGetFormattedCounterValue(counter,0x200,ctypes.byref(typ),ctypes.byref(value))==0 and value.status in [0,1]:rates[name]=value.value.doubleValue
        return {'utc':time.time(),'systemAvailablePhysical':m.availablePhysical,'systemCommitAvailable':m.availablePageFile,'systemPageReadRates':rates,'processes':[r for label,pid in processes if (r:=self.process(label,pid))]}
    def close(self):
        for h in self.handles.values():self.kernel.CloseHandle(h)
        if self.query:self.pdh.PdhCloseQuery(self.query)
