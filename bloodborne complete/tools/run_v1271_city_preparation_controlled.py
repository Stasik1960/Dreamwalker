"""Persist result because the local storage Job wrapper does not relay stdout."""
import json, runpy, sys, traceback
from pathlib import Path
root=Path(__file__).resolve().parents[1]
result=root/'build/v1271-city-preparation-result.json'
result.parent.mkdir(parents=True,exist_ok=True)
sys.argv=[str(Path(__file__).with_name('prepare_v1271_city_compatibility.py')),'--source-project',r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete','--output',str(root/'build/v1271-city-preparation')]
try:
    runpy.run_path(sys.argv[0],run_name='__main__')
    result.write_text(json.dumps({'status':'PASS','exitCode':0},indent=2),encoding='utf8')
except BaseException:
    result.write_text(json.dumps({'status':'FAIL','exitCode':1,'error':traceback.format_exc()},indent=2),encoding='utf8')
    raise
