"""Resource-only planning archive; never a runtime build or deliverable JAR."""
import json,zipfile
from pathlib import Path
from prepare_gallery_v125_input import main
ROOT=Path(__file__).resolve().parents[1]
def run():
    target=ROOT/'build/v125-gallery-plan';target.mkdir(exist_ok=True)
    old=ROOT.parent/'releases/Dreamwalker-BB/v12.4-dedup/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.4-dedup.jar'
    files={}
    for directory in ('src/architecture/resources','src/rp/resources','src/main/resources'):
        base=ROOT/directory
        if base.exists():files.update({f.relative_to(base).as_posix():f for f in base.rglob('*') if f.is_file()})
    archive=target/'resource-planning-only.zip'
    with zipfile.ZipFile(old) as original,zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as out:
        for name in original.namelist():
            if name not in files:out.writestr(name,original.read(name))
        for name,file in files.items():out.write(file,name)
    import sys
    sys.argv=['prepare_gallery_v125_input.py','--production-jar',str(archive),'--output',str(target/'planning-marker.json')];main()
    d=json.loads((target/'planning-marker.json').read_text(encoding='utf8'))
    print(json.dumps({'status':'PASS_PLANNING_ONLY','counts':d['catalogue']['summary'],'reviews':len(d['reviewPlatforms']),'cityOwners':len(d['city']['owners']),'cityBounds':d['city']['localVisualBounds']}))
if __name__=='__main__':run()
