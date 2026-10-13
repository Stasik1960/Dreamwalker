"""Keep earlier validation failures separate from final acceptance evidence."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1]
def main():
    target=ROOT/'docs/v12.6/validation/attempts';target.mkdir(parents=True,exist_ok=True);records=[]
    for n in range(1,8):
        name=f'v126-native-tests-{n}'; source=ROOT/'build/remaining-transfer'/name
        if not (source/'result.json').exists():continue
        row=json.loads((source/'result.json').read_text(encoding='utf8'))
        folder=target/name;folder.mkdir(exist_ok=True)
        for path in [source/'result.json',source/'gradle.log',ROOT/f'build/test-results/gametest/TEST-{name}.xml']:
            if path.exists():shutil.copyfile(path,folder/path.name)
        records.append({'run':name,'status':row['status'],'finalAcceptanceRun':n==7})
    (target/'index.json').write_text(json.dumps({'status':'PASS_ARCHIVED_ATTEMPTS','runs':records},indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS_ARCHIVED_ATTEMPTS','runs':len(records)}))
if __name__=='__main__':main()
