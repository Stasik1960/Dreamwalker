import ctypes,json,os,signal,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence'/sys.argv[1]
assert 'Failed to start the minecraft server' in (D/'console.log').read_text(encoding='utf-8',errors='replace')
pid=json.loads((D/'process.json').read_text())['pid'];k=ctypes.WinDLL('kernel32',use_last_error=True);k.OpenProcess.restype=ctypes.c_void_p
h=k.OpenProcess(0x1000,False,pid);assert h;buf=ctypes.create_unicode_buffer(32768);size=ctypes.c_ulong(len(buf));k.QueryFullProcessImageNameW.argtypes=[ctypes.c_void_p,ctypes.c_ulong,ctypes.c_wchar_p,ctypes.POINTER(ctypes.c_ulong)]
assert k.QueryFullProcessImageNameW(h,0,buf,ctypes.byref(size));assert Path(buf.value).resolve()==(P/'build-tools/jdk/bin/java.exe').resolve();k.CloseHandle.argtypes=[ctypes.c_void_p];k.CloseHandle(h);os.kill(pid,signal.SIGTERM)
