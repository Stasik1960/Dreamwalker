import contextlib,json,runpy,sys,time,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'utility-evidence';task=sys.argv[1]
scripts={'checkpoint':'publish_utility_checkpoint.py','package':'package_utility_release.py','install':'install_utility_desktop.py','publish':'publish_utility_release.py','preview':'generate_utility_portal_options.py'}
assert task in scripts;start=time.time();code=0;receipt=D/('delivery-'+task+'.json');receipt.write_text(json.dumps({'task':task,'status':'RUNNING'}),encoding='utf-8')
with (D/('delivery-'+task+'.log')).open('w',encoding='utf-8') as log,contextlib.redirect_stdout(log),contextlib.redirect_stderr(log):
 try:runpy.run_path(str(P/'tools'/scripts[task]),run_name='__main__')
 except BaseException:code=1;traceback.print_exc()
receipt.write_text(json.dumps({'task':task,'exitCode':code,'seconds':time.time()-start},indent=2),encoding='utf-8');sys.exit(code)
