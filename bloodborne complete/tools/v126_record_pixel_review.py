"""Bind explicit visual observations to immutable raw native screenshots."""
import argparse,hashlib,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--indices',type=Path,nargs='+',required=True)
    p.add_argument('--observations',type=Path,nargs='+',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    captures={};frames={};digest=None
    for path in a.indices:
        index=read(path);digest=digest or index['productionJarSha256'];assert digest==index['productionJarSha256']
        profile=read(Path(index['clientProfile']));run=Path(profile['run_directory']);capture=run/'client-capture-output.json'
        assert sha(capture)==index['captureSha256'];captures[index['captureSha256']]=str(capture)
        for row in index['frames']:
            key=(index['captureSha256'],row['fileName']);assert key not in frames
            assert sha(Path(row['path']))==row['sha256'];frames[key]=row
    observations={}
    for path in a.observations:
        value=read(path);assert value['status']=='PASS_ASSIGNED_PIXELS' and value['productionJarSha256']==digest
        for row in value['frames']:
            key=(value['captureSha256'],row['fileName']);actual=frames[key]
            assert key not in observations and row['status']=='PASS_OBSERVED_PIXELS' and row['observation'].strip()
            assert row['key']==actual['key'] and row['sha256']==actual['sha256'];observations[key]=row
    assert set(observations)==set(frames),'Every captured frame needs a concrete visual observation'
    output=ROOT/'docs/v12.6/native/captures';assert not output.exists();output.mkdir(parents=True)
    for capture_sha,path in captures.items():
        run=Path(path).parent;destination=output/run.name;destination.mkdir()
        for name in ['client-capture-output.json','client-capture-input.json','launch-console.log']:shutil.copyfile(run/name,destination/name)
        for key,row in frames.items():
            if key[0]==capture_sha:shutil.copyfile(Path(row['path']),destination/row['fileName'])
    result={'status':'PASS','productionJarSha256':digest,'reviewedFrames':len(frames),
            'reviewMethod':'Explicit observations of native Minecraft screenshots; contact sheets only aid navigation; original PNGs preserved.',
            'frames':list(observations.values()),'captures':captures,'physicalMouseKeyboard':'NOT_RUN','frameRateBenchmark':'NOT_RUN'}
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS','reviewedFrames':len(frames)}))
if __name__=='__main__':main()
