"""Capture controlled-process receipts even when Guard does not forward stdio."""
import contextlib,json,runpy,sys,time,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'bench-evidence';task=sys.argv[1]
scripts={'package':'package_bench_release.py','checkpoint':'publish_bench_checkpoint.py','publish':'publish_bench_release.py','install':'install_bench_desktop.py'}
assert task in scripts;start=time.time();code=0
with (D/('delivery-'+task+'.log')).open('w',encoding='utf-8') as log,contextlib.redirect_stdout(log),contextlib.redirect_stderr(log):
 try:runpy.run_path(str(P/'tools'/scripts[task]),run_name='__main__')
 except BaseException:code=1;traceback.print_exc()
(D/('delivery-'+task+'.json')).write_text(json.dumps({'task':task,'exitCode':code,'seconds':time.time()-start},indent=2),encoding='utf-8')
sys.exit(code)
