"""Read-only Windows process memory observations; never controls the game."""
import argparse, ctypes, json, time
from ctypes import wintypes
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--launch-report',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
report=json.loads(a.launch_report.read_text(encoding='utf8'));pid=report['pid'];started=time.monotonic()
class Counters(ctypes.Structure):
    _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD)]+[(k,ctypes.c_size_t) for k in ('PeakWorkingSetSize','WorkingSetSize','QuotaPeakPagedPoolUsage','QuotaPagedPoolUsage','QuotaPeakNonPagedPoolUsage','QuotaNonPagedPoolUsage','PagefileUsage','PeakPagefileUsage','PrivateUsage')]
kernel=ctypes.WinDLL('kernel32',use_last_error=True);psapi=ctypes.WinDLL('psapi',use_last_error=True)
kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE
kernel.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
psapi.GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.POINTER(Counters),wintypes.DWORD]
handle=kernel.OpenProcess(0x410,False,pid);assert handle
samples=[];value={'status':'RUNNING','pid':pid,'productionJarSha256':report['artifact_sha256'],'method':'Windows GetProcessMemoryInfo, one observation per second; whole Java process, no attribution to the mod and no forced GC','samples':samples}
try:
    while time.monotonic()-started<1200:
        code=wintypes.DWORD();assert kernel.GetExitCodeProcess(handle,ctypes.byref(code))
        if code.value!=259:
            value.update(status='PASS_PROCESS_OBSERVATION' if code.value==0 else 'FAIL_PROCESS_EXIT',exitCode=code.value);break
        counters=Counters();counters.cb=ctypes.sizeof(counters);assert psapi.GetProcessMemoryInfo(handle,ctypes.byref(counters),counters.cb)
        samples.append({'elapsedSeconds':round(time.monotonic()-started,2),'workingSetBytes':counters.WorkingSetSize,'privateCommitBytes':counters.PrivateUsage,'osPeakWorkingSetBytes':counters.PeakWorkingSetSize})
        if len(samples)%10==0:a.output.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
        time.sleep(1)
    else:value['status']='FAIL_OBSERVATION_TIMEOUT_GAME_NOT_TERMINATED'
finally:
    kernel.CloseHandle.argtypes=[wintypes.HANDLE];kernel.CloseHandle(handle)
value.update(peakWorkingSetBytes=max(r['osPeakWorkingSetBytes'] for r in samples),peakPrivateCommitBytes=max(r['privateCommitBytes'] for r in samples),observedSeconds=round(time.monotonic()-started,2))
a.output.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8');print(json.dumps({k:v for k,v in value.items() if k!='samples'}));assert value['status']=='PASS_PROCESS_OBSERVATION'
