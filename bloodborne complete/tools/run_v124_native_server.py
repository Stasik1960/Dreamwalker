"""Explicit NEW-only native mode marker in a fresh controlled server profile."""
import argparse,json,sys
from pathlib import Path
import run_v11_server as runner
p=argparse.ArgumentParser(add_help=False);p.add_argument('--mode',choices=['legacy-author','audit'],required=True);mode,rest=p.parse_known_args()
original=runner.copy_extra_mods
def copy_with_mode(inputs,mods):
    result=original(inputs,mods)
    (mods.parent/'v124-native-mode.json').write_bytes((json.dumps({'mode':mode.mode,'schemaVersion':1})+'\n').encode('utf8'))
    return result
runner.copy_extra_mods=copy_with_mode
sys.argv=[sys.argv[0],*rest]
runner.main()
