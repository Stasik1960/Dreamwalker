"""RC3 dedicated QA runner; invoked only after the RC3 JAR is installed."""
from __future__ import annotations
import ctypes, hashlib, json, os, subprocess, sys, threading, time
from ctypes import wintypes
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
QA = Path(__file__).resolve().parent / 'server-attempt3'
JAVA = Path(r'C:\Users\vakir\Documents\ChatGPT\DW\Bloodborne-Blocks\build\toolchain\jdk-17.0.20.1\bin\java.exe')
SOURCE = ROOT / 'build/complete-accepted-repair/Bloodborne-City-2.1.0-rc.3-checkpoint'
TIMEOUT = 180

def tree(path):
 return [{'path': p.relative_to(path).as_posix(), 'sha256': hashlib.file_digest(p.open('rb'),'sha256').hexdigest(), 'length':p.stat().st_size} for p in sorted(path.rglob('*')) if p.is_file()]
def copy_stream(source, target):
 with target.open('w', encoding='utf-8', buffering=1) as sink:
  for line in iter(source.readline, ''): sink.write(line)
def rss(pid):
 if os.name == 'nt':
  class COUNTERS(ctypes.Structure):
   _fields_ = [('cb', ctypes.c_ulong), ('PageFaultCount', ctypes.c_ulong), ('PeakWorkingSetSize', ctypes.c_size_t), ('WorkingSetSize', ctypes.c_size_t), ('QuotaPeakPagedPoolUsage', ctypes.c_size_t), ('QuotaPagedPoolUsage', ctypes.c_size_t), ('QuotaPeakNonPagedPoolUsage', ctypes.c_size_t), ('QuotaNonPagedPoolUsage', ctypes.c_size_t), ('PagefileUsage', ctypes.c_size_t), ('PeakPagefileUsage', ctypes.c_size_t)]
  open_process = ctypes.windll.kernel32.OpenProcess
  open_process.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD); open_process.restype = wintypes.HANDLE
  close_handle = ctypes.windll.kernel32.CloseHandle
  close_handle.argtypes = (wintypes.HANDLE,); close_handle.restype = wintypes.BOOL
  get_memory = ctypes.windll.psapi.GetProcessMemoryInfo
  get_memory.argtypes = (wintypes.HANDLE, ctypes.c_void_p, wintypes.DWORD); get_memory.restype = wintypes.BOOL
  handle = open_process(0x0400, False, pid)
  if not handle: return None
  try:
   counters = COUNTERS(ctypes.sizeof(COUNTERS))
   return counters.PeakWorkingSetSize if get_memory(handle, ctypes.byref(counters), ctypes.sizeof(counters)) else None
  finally: close_handle(handle)
 try:
  for row in Path(f'/proc/{pid}/status').read_text().splitlines():
   if row.startswith('VmRSS:'): return int(row.split()[1]) * 1024
 except OSError: pass
 return None
def run(name, checks):
 out, err = QA / f'{name}.stdout.log', QA / f'{name}.stderr.log'
 proc = subprocess.Popen([str(JAVA), '-Xms1G', '-Xmx2G', '-jar', 'fabric-server-launch.jar', 'nogui'], cwd=QA, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
 out_thread = threading.Thread(target=copy_stream, args=(proc.stdout,out), daemon=True); err_thread = threading.Thread(target=copy_stream, args=(proc.stderr,err), daemon=True)
 out_thread.start(); err_thread.start()
 started=time.monotonic(); deadline=started+TIMEOUT; done_at=None; peak=0
 while time.monotonic() < deadline and proc.poll() is None:
  value=rss(proc.pid)
  if value is not None: peak=max(peak,value)
  if done_at is None and out.exists() and 'Done (' in out.read_text(encoding='utf-8', errors='replace'): done_at=time.monotonic()
  if done_at is not None: break
  time.sleep(.25)
 commands=[]
 if done_at is not None:
  for command in checks + ['debug start']:
   proc.stdin.write(command+'\n'); proc.stdin.flush(); commands.append(command)
  until=time.monotonic()+20
  while time.monotonic()<until and proc.poll() is None:
   value=rss(proc.pid)
   if value is not None: peak=max(peak,value)
   time.sleep(.25)
  for command in ['debug stop','forceload query','save-all flush','stop']:
   proc.stdin.write(command+'\n'); proc.stdin.flush(); commands.append(command)
 killed=False
 stop_deadline = time.monotonic() + 30
 while proc.poll() is None and time.monotonic() < stop_deadline:
  value = rss(proc.pid)
  if value is not None: peak = max(peak, value)
  time.sleep(.25)
 if proc.poll() is None:
  proc.kill(); proc.wait(); killed=True
 out_thread.join(2); err_thread.join(2)
 log = out.read_text(encoding='utf-8', errors='replace') if out.exists() else ''
 markers = [item for item in checks if item.startswith('execute if block')]
 expected = [command.split(' run say ', 1)[1] for command in markers]
 return {'name':name,'ownedPid':proc.pid,'startupDone':done_at is not None,'startupSeconds':None if done_at is None else round(done_at-started,3),'peakRamBytes':peak or None,'commands':commands,'exitCode':proc.returncode,'stateMarkers':{marker: marker in log for marker in expected},'statePass':done_at is not None and proc.returncode == 0 and all(marker in log for marker in expected),'killedOwnedPidAfterStopTimeout':killed,'stdout':out.name,'stderr':err.name,'scope':'dedicated no-player startup/loaded-chunk TPS only; no movement/use/client PASS'}
def main():
 jars=list((QA/'mods').glob('bloodborne-blocks-2.1.0-rc.3.jar'))
 if len(jars)!=1: raise SystemExit('READY-JAR missing or ambiguous')
 if not JAVA.is_file(): raise SystemExit('pinned JDK17 missing')
 before=tree(SOURCE)
 checks=['forceload add -332 -137','forceload add -672 -81','forceload add -169 -30','forceload add -257 -225','forceload add -257 -226',
         'execute if block -332 77 -137 bloodborne_blocks:o_books[facing=north,variant=books_1,visual=base] run say QA_BOOK_STATE_OK',
         'execute if block -672 35 -81 bloodborne_blocks:o_grass_0[facing=north,visual=base] run say QA_GRASS_STATE_OK',
         'execute if block -169 51 -30 bloodborne_blocks:o_c001[facing=north,variant=tree_78dd02703b12,visual=base] run say QA_TREE_STATE_OK',
         'execute if block -257 66 -225 bloodborne_blocks:o_shuttered_window[facing=east,open=false,visual=base] run say QA_WINDOW_STATE_OK',
         'execute if block -257 67 -226 bloodborne_blocks:building_stone_brick_wall[connection=retained_9b3ffe0b2156b73eea29,facing=north] run say QA_WALL_STATE_OK',
         'data get block -672 36 -81','data get block -169 52 -30','data get block -257 67 -225']
 runs=[run('fresh',checks),run('restart',checks)]
 after=tree(SOURCE)
 result={'status':'COMPLETED','jarSha256':hashlib.file_digest(jars[0].open('rb'),'sha256').hexdigest(),'runs':runs,'sourceWorldUnchanged':before==after,'sourceWorldTreeBefore':before,'sourceWorldTreeAfter':after,'qaWorldTreeAfter':tree(QA/'world')}
 (QA/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
if __name__ == '__main__': main()
