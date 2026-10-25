import runpy,sys,traceback
from pathlib import Path
O=Path(__file__).resolve().parent;name=sys.argv[1];assert '/' not in name and '\\' not in name
with (O/(name+'.log')).open('w',encoding='utf-8') as f:
 sys.stdout=sys.stderr=f
 try:runpy.run_path(str(O/(name+'.py')),run_name='__main__')
 except BaseException:(O/(name+'-error.txt')).write_text(traceback.format_exc(),encoding='utf-8');raise
