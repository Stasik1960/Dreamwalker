import contextlib,json,runpy,sys,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';D.mkdir(exist_ok=True);script=(P/'tools'/sys.argv[1]).resolve();assert script.parent==(P/'tools').resolve();args=sys.argv[2:];name=script.stem;sys.argv=[str(script),*args];code=0
with (D/(name+'.step.log')).open('w',encoding='utf-8') as output,contextlib.redirect_stdout(output),contextlib.redirect_stderr(output):
 try:runpy.run_path(str(script),run_name='__main__')
 except SystemExit as error:code=error.code or 0
 except BaseException:traceback.print_exc();code=1
(D/(name+'.step.json')).write_text(json.dumps({'exitCode':code,'script':script.name},indent=2),encoding='utf-8');raise SystemExit(code)
