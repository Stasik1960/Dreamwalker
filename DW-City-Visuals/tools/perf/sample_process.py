"""Bounded read-only Windows RAM sampler for one explicitly owned benchmark PID.

Uses native GetProcessMemoryInfo; no taskkill, game interaction or heap-GC request.
The caller launches this alongside its benchmark process and waits for completion.
"""
from __future__ import annotations
import argparse, ctypes, datetime, json, time
from ctypes import wintypes
from pathlib import Path

class Counters(ctypes.Structure):
    _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD),
              ('PeakWorkingSetSize',ctypes.c_size_t),('WorkingSetSize',ctypes.c_size_t),
              ('QuotaPeakPagedPoolUsage',ctypes.c_size_t),('QuotaPagedPoolUsage',ctypes.c_size_t),
              ('QuotaPeakNonPagedPoolUsage',ctypes.c_size_t),('QuotaNonPagedPoolUsage',ctypes.c_size_t),
              ('PagefileUsage',ctypes.c_size_t),('PeakPagefileUsage',ctypes.c_size_t),
              ('PrivateUsage',ctypes.c_size_t)]

class MemoryStatus(ctypes.Structure):
    _fields_=[('length',wintypes.DWORD),('load',wintypes.DWORD),
              ('totalPhysical',ctypes.c_ulonglong),('availablePhysical',ctypes.c_ulonglong),
              ('totalPageFile',ctypes.c_ulonglong),('availablePageFile',ctypes.c_ulonglong),
              ('totalVirtual',ctypes.c_ulonglong),('availableVirtual',ctypes.c_ulonglong),
              ('extended',ctypes.c_ulonglong)]

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--pid',type=int,required=True)
    ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--seconds',type=int,default=900)
    ap.add_argument('--interval',type=float,default=1)
    args=ap.parse_args()
    if args.pid<=0 or not 1<=args.seconds<=4000 or not .25<=args.interval<=5:
        ap.error('Explicit positive PID and bounded seconds/interval required')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    if args.output.exists():raise FileExistsError('Preserve existing process measurements')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
    kernel.OpenProcess.restype=wintypes.HANDLE
    kernel.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
    kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    kernel.GlobalMemoryStatusEx.argtypes=[ctypes.POINTER(MemoryStatus)]
    psapi=ctypes.WinDLL('psapi',use_last_error=True)
    psapi.GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.c_void_p,wintypes.DWORD]
    handle=kernel.OpenProcess(0x0410,False,args.pid)
    if not handle:raise ctypes.WinError(ctypes.get_last_error())
    started=time.monotonic();next_sample=started;previous_write_flush_ns=None
    try:
        with args.output.open('x',encoding='utf-8') as stream:
            while time.monotonic()-started<args.seconds:
                schedule_late_ns=max(0,int((time.monotonic()-next_sample)*1e9))
                query_started_ns=time.perf_counter_ns()
                code=wintypes.DWORD()
                if not kernel.GetExitCodeProcess(handle,ctypes.byref(code)) or code.value!=259:break
                c=Counters();c.cb=ctypes.sizeof(c)
                if not psapi.GetProcessMemoryInfo(handle,ctypes.byref(c),ctypes.sizeof(c)):
                    raise ctypes.WinError(ctypes.get_last_error())
                m=MemoryStatus();m.length=ctypes.sizeof(m)
                if not kernel.GlobalMemoryStatusEx(ctypes.byref(m)):raise ctypes.WinError(ctypes.get_last_error())
                native_query_ns=time.perf_counter_ns()-query_started_ns
                row={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
                     'epochMillis':time.time_ns()/1e6,'samplerElapsedSeconds':time.monotonic()-started,
                     'scheduleLateNs':schedule_late_ns,'nativeQueryNs':native_query_ns,
                     'previousWriteFlushNs':previous_write_flush_ns,
                     'pid':args.pid,'workingSetBytes':c.WorkingSetSize,'privateBytes':c.PrivateUsage,
                     'processLifetimePeakWorkingSetBytes':c.PeakWorkingSetSize,
                     'processLifetimePeakPagefileUsageBytes':c.PeakPagefileUsage,
                     'pageFaultCount':c.PageFaultCount,'systemAvailablePhysicalBytes':m.availablePhysical,
                     'systemAvailableCommitBytes':m.availablePageFile,
                     'source':'GetProcessMemoryInfo(PROCESS_MEMORY_COUNTERS_EX); Working Set and Private Bytes are different measures'}
                write_started_ns=time.perf_counter_ns()
                stream.write(json.dumps(row,separators=(',',':'))+'\n');stream.flush()
                previous_write_flush_ns=time.perf_counter_ns()-write_started_ns
                next_sample+=args.interval
                time.sleep(max(0,next_sample-time.monotonic()))
    finally:kernel.CloseHandle(handle)

if __name__=='__main__':main()
