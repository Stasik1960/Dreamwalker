"""Root-only controlled interruption of the one explicitly named owned resume.

No process discovery, job termination, map access or planner/schema writer.
The verified process HANDLE is terminated; the companion distinguishes this
external interruption from a natural Python exception or a completed run.
"""
import argparse, ctypes, datetime, hashlib, json, os, sqlite3, sys, time
from ctypes import wintypes as w
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[2]
ROOT = SOURCE.parent
RUN = '20261023T104808-migrate'
PYTHON = Path(r'C:\Users\vakir\miniconda3\python.exe')
EXPECTED = {'migrate.py':'056efec209523e3a48d6e66dd3082300c843ea1666c008555d2a2ad20a23781c',
            'native_resume.py':'3a3f3f7b1fecef1320da43114ccfba459ab4b7d8e90487fc19f0002c8ea26ff1',
            'native_certificates.py':'eac031fad7d2202b80a33f3a2a36e00209e5f2a94737849af28bae7a676880c9',
            'context_stage.py':'f04362e1021287ac16f613d85ff85033d92f5b315c4bb71112df45b3f09b6d45'}
ARGS = ['--plan','../research/native-grid-plan','resume-native-compose']


def require(ok, message):
    if not ok: raise RuntimeError(message)


def sha(path):
    value=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1<<20),b''):value.update(block)
    return value.hexdigest()


def ref(path):return {'path':str(Path(path).resolve()),'sha256':sha(path)}
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def normal(path):return os.path.normcase(os.path.abspath(path))


def write_new(path, value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:
        json.dump(value,stream,ensure_ascii=False,indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())


def snapshot(db):
    def meta(key):
        row=db.execute('SELECT value FROM meta WHERE key=?',(key,)).fetchone()
        return json.loads(row[0]) if row else None
    cursor=meta('nativeComposeCursor')
    require(cursor and cursor['phase']=='NATIVE_TRANSFER','Not an unfinished native-transfer phase')
    for key in ('applyStarted','offlineApplied','compositionGeneration','compositionsSha256'):
        require(not meta(key),'Abort: apply/composition completion exists: '+key)
    for table in ('placements','compositions'):
        require(not db.execute('SELECT 1 FROM '+table+' LIMIT 1').fetchone(),'Abort: finalized rows exist: '+table)
    return {'phase':cursor['phase'],'cursor':cursor,'sourceBinding':meta('binding'),
            'sourceLocalRetentionJournal':meta('sourceLocalRetentionJournal'),
            'sourceVisualReservationJournal':meta('sourceVisualReservationJournal'),
            'nativeLocalRetention':meta('nativeLocalRetention'),
            'noApplyOrFinalCompositions':True,'noPlacements':True}


def verify_progress(db, before, after):
    require(before['phase']==after['phase'] and before['sourceBinding']==after['sourceBinding']
            and before['nativeLocalRetention']==after['nativeLocalRetention'],'Phase/source/accepted retention changed')
    for key in ('batchesCommitted','rowsProcessedSinceAdmission'):
        require(after['cursor'].get(key,0)>=before['cursor'].get(key,0),'Committed cursor regressed: '+key)
    for name,count in (('sourceLocalRetentionJournal','events'),('sourceVisualReservationJournal','contacts')):
        old,new=before[name],after[name]
        require((new or {}).get(count,0)>=(old or {}).get(count,0),'Committed journal regressed: '+name)
        if old and new and old[count]==new[count]:require(old==new,'Journal head changed without an append: '+name)
    old,new=before['sourceLocalRetentionJournal'],after['sourceLocalRetentionJournal']
    if old:
        row=db.execute('SELECT digest FROM source_local_retentions WHERE sequence=?',(old['events'],)).fetchone()
        require(row and row[0]==old['head'],'Previous local journal prefix changed')
    if new:
        row=db.execute('SELECT sequence,digest FROM source_local_retentions ORDER BY sequence DESC LIMIT 1').fetchone()
        require(row==(new['events'],new['head']),'Recovered local journal tail differs from metadata')


def api():
    k=ctypes.WinDLL('kernel32',use_last_error=True)
    k.OpenProcess.argtypes=[w.DWORD,w.BOOL,w.DWORD];k.OpenProcess.restype=w.HANDLE
    k.CloseHandle.argtypes=[w.HANDLE];k.CloseHandle.restype=w.BOOL
    k.GetProcessTimes.argtypes=[w.HANDLE,*([ctypes.POINTER(w.FILETIME)]*4)];k.GetProcessTimes.restype=w.BOOL
    k.QueryFullProcessImageNameW.argtypes=[w.HANDLE,w.DWORD,w.LPWSTR,ctypes.POINTER(w.DWORD)];k.QueryFullProcessImageNameW.restype=w.BOOL
    k.GetExitCodeProcess.argtypes=[w.HANDLE,ctypes.POINTER(w.DWORD)];k.GetExitCodeProcess.restype=w.BOOL
    k.TerminateProcess.argtypes=[w.HANDLE,w.UINT];k.TerminateProcess.restype=w.BOOL
    k.WaitForSingleObject.argtypes=[w.HANDLE,w.DWORD];k.WaitForSingleObject.restype=w.DWORD
    return k


def exit_code(k, handle):
    value=w.DWORD();require(k.GetExitCodeProcess(handle,ctypes.byref(value)),str(ctypes.WinError(ctypes.get_last_error())))
    return value.value


def command_line(handle):
    class UnicodeString(ctypes.Structure):
        _fields_=[('Length',w.USHORT),('MaximumLength',w.USHORT),('Buffer',ctypes.c_void_p)]
    nt=ctypes.WinDLL('ntdll');query=nt.NtQueryInformationProcess
    query.argtypes=[w.HANDLE,w.ULONG,ctypes.c_void_p,w.ULONG,ctypes.POINTER(w.ULONG)];query.restype=ctypes.c_long
    size=w.ULONG();query(handle,60,None,0,ctypes.byref(size))
    require(ctypes.sizeof(UnicodeString)<=size.value<=65536,'Unknown process command-line size')
    buffer=ctypes.create_string_buffer(size.value)
    require(query(handle,60,buffer,size.value,ctypes.byref(size))>=0,'Cannot read verified-handle process command line')
    value=ctypes.cast(buffer,ctypes.POINTER(UnicodeString)).contents
    require(value.Buffer and value.Length%2==0,'Invalid process Unicode command line')
    text=ctypes.wstring_at(value.Buffer,value.Length//2)
    shell=ctypes.WinDLL('shell32');shell.CommandLineToArgvW.argtypes=[w.LPCWSTR,ctypes.POINTER(ctypes.c_int)];shell.CommandLineToArgvW.restype=ctypes.POINTER(w.LPWSTR)
    kernel=ctypes.WinDLL('kernel32');kernel.LocalFree.argtypes=[ctypes.c_void_p];kernel.LocalFree.restype=ctypes.c_void_p
    count=ctypes.c_int();argv=shell.CommandLineToArgvW(text,ctypes.byref(count));require(argv,'Cannot parse actual command line')
    try:return text,[argv[index] for index in range(count.value)]
    finally:kernel.LocalFree(argv)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pid',type=int,required=True);parser.add_argument('--expected-created-ms',type=int,required=True)
    parser.add_argument('--run',choices=(RUN,),required=True);a=parser.parse_args()
    require(os.name=='nt' and a.pid==10912 and a.pid!=os.getpid()
            and a.expected_created_ms==1792752488560,'This utility is restricted to the explicitly owned process')
    control=read(Path(r'C:\Users\vakir\CodexControl\storage-mode.json'))
    require(control['Mode']=='LocalC' and control['ExternalMigrationPaused'],'Expected paused E migration and LocalC')
    Path(r'C:\Users\vakir\CodexControl\C-return-result.txt').read_text(encoding='utf-8-sig')
    directory=ROOT/'research/processes';receipt=directory/(RUN+'.json');companion=directory/(RUN+'.controlled-stop.json')
    require(not receipt.exists() and not companion.exists(),'Abort: natural receipt or prior stop evidence already exists')
    inputs=directory/(RUN+'.inputs');sources={}
    for filename,wanted in EXPECTED.items():
        archive=inputs/filename;require(sha(archive)==wanted,'Captured source archive changed: '+filename)
        require(sha(SOURCE/'tools/native-grid-city'/filename)==wanted,'Live frozen source tuple changed: '+filename)
        sources[str(SOURCE/'tools/native-grid-city'/filename)]={'sha256':wanted,'archive':str(archive)}
    k=api();handle=k.OpenProcess(0x00100000|0x1000|0x0400|0x0001,False,a.pid)
    require(handle,str(ctypes.WinError(ctypes.get_last_error())))
    stopped=False;actual=None;before=None;after=None;error=None;recovered=False
    try:
        times=[w.FILETIME() for _ in range(4)]
        require(k.GetProcessTimes(handle,*[ctypes.byref(value) for value in times]),'Cannot query creation time')
        created=(((times[0].dwHighDateTime<<32)|times[0].dwLowDateTime)-116444736000000000)//10000
        require(created==a.expected_created_ms,'PID creation time mismatch; do not terminate')
        size=w.DWORD(32768);image=ctypes.create_unicode_buffer(size.value)
        require(k.QueryFullProcessImageNameW(handle,0,image,ctypes.byref(size)) and normal(image.value)==normal(PYTHON),'Actual executable differs')
        text,argv=command_line(handle)
        require(len(argv)==6 and normal(argv[0])==normal(PYTHON)
                and normal(SOURCE/argv[1])==normal(SOURCE/'tools/native-grid/run_task.py')
                and normal(SOURCE/argv[2])==normal(SOURCE/'tools/native-grid-city/migrate.py')
                and argv[3:]==ARGS,'Actual command line differs from the owned wrapper/task')
        require(exit_code(k,handle)==259,'Abort: process completed naturally')
        database=ROOT/'research/native-grid-plan/plan.sqlite'
        db=sqlite3.connect('file:'+database.as_posix()+'?mode=ro',uri=True,timeout=3)
        try:db.execute('PRAGMA query_only=ON');db.execute('BEGIN');before=snapshot(db)
        finally:db.close()
        require(not receipt.exists() and exit_code(k,handle)==259,'Abort: natural completion before stop')
        require(k.TerminateProcess(handle,1),str(ctypes.WinError(ctypes.get_last_error())));stopped=True
        require(k.WaitForSingleObject(handle,30000)==0,'Owned process did not exit within 30 seconds')
        actual=exit_code(k,handle);require(actual==1,'Actual OS exit code differs from controlled interruption')
        require(not receipt.exists(),'Natural/wrapper receipt appeared; preserve it')
        # Ordinary SQLite recovery/lock acquisition, no planner/schema writer.
        db=sqlite3.connect('file:'+database.as_posix()+'?mode=rw',uri=True,timeout=30)
        try:
            db.execute('BEGIN IMMEDIATE');db.rollback();recovered=True
            db.execute('PRAGMA query_only=ON');db.execute('BEGIN');after=snapshot(db);verify_progress(db,before,after)
        finally:db.close()
    except BaseException as failure:
        if not stopped:raise
        error=str(failure)
        if actual is None:actual=exit_code(k,handle)
    finally:k.CloseHandle(handle)
    require(stopped,'No controlled interruption occurred')
    now=datetime.datetime.now(datetime.timezone.utc)
    evidence={'schemaVersion':1,'status':'PASS' if error is None else 'FAIL','kind':'CONTROLLED_NATIVE_RESUME_INTERRUPTION',
              'evidenceNature':'ROOT_CONTROLLED_INTERRUPTION','stopped':actual is not None and actual!=259,'controlledInterruption':True,'runId':RUN,
              'pid':a.pid,'expectedCreatedMs':a.expected_created_ms,'actualCreatedMs':created,'actualExitCode':actual,
              'executable':image.value,'commandLine':text,'argv':argv,'utc':now.isoformat(),
              'sourceArchives':sources,'cursorBefore':before,'cursorAfter':after,'sqliteRecoveryTransactionRolledBack':recovered,
              'noPlannerRowsWritten':True,'noMapWrites':True,'utility':ref(__file__),'utilityArgs':sys.argv[1:],'error':error}
    write_new(companion,evidence)
    require(actual==1 and not receipt.exists(),'Do not replace a natural receipt or invent an actual exit code')
    recovered_receipt={'status':'FAIL','exitCode':actual,'script':str(SOURCE/'tools/native-grid-city/migrate.py'),'args':ARGS,
                       'seconds':round(time.time()-a.expected_created_ms/1000,3),'log':str(directory/(RUN+'.log')),'inputSources':sources,
                       'kind':'CONTROLLED_INTERRUPTION_RECEIPT','evidenceNature':'ROOT_CONTROLLED_INTERRUPTION','interrupted':True,
                       'secondsMeasurement':'Wall time since verified OS process creation; original wrapper duration was interrupted',
                       'interruptionReceipt':ref(companion)}
    write_new(receipt,recovered_receipt)
    print(json.dumps({'status':evidence['status'],'companion':ref(companion),'recoveredWrapperReceipt':ref(receipt)}))
    require(error is None,error or 'Controlled stop did not pass')


if __name__=='__main__':main()
