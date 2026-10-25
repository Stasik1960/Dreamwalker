import runpy,sys,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[1];name=sys.argv[1];assert '/' not in name and '\\' not in name
with (P/'build-tools'/('step-'+name+'.log')).open('w',encoding='utf-8') as log:
 sys.stdout=sys.stderr=log
 try:runpy.run_path(str(P/'tools'/(name+'.py')),run_name='__main__')
 except BaseException:traceback.print_exc();raise
