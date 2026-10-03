"""Restore pinned generation inputs from Git/LFS, without changing live resources."""
import argparse,hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE_REF='b5acd39e494e4efa505b65d63631230993a897a9'
def run():
    snapshot=ROOT/'build/unified-source';snapshot.mkdir(parents=True,exist_ok=True);hashes={}
    for scope in ('city','logical'):
        prefix=f'Bloodborne-Blocks/src/main/resources/bloodborne_blocks/{scope}/'
        names=subprocess.check_output(['git','ls-tree','-r','--name-only',SOURCE_REF,'--',prefix],cwd=ROOT.parent,text=True).splitlines()
        for name in names:
            content=subprocess.check_output(['git','show',SOURCE_REF+':'+name],cwd=ROOT.parent)
            if content.startswith(b'version https://git-lfs.github.com/spec/v1'):
                content=subprocess.check_output(['git','lfs','smudge'],cwd=ROOT.parent,input=content)
                if content.startswith(b'version https://git-lfs.github.com/spec/v1'):raise ValueError('LFS data unavailable: '+name)
            path=snapshot/scope/name[len(prefix):];path.parent.mkdir(parents=True,exist_ok=True)
            if path.exists()and path.read_bytes()!=content:
                if path.suffix!='.json'or json.loads(path.read_bytes())!=json.loads(content):raise ValueError('immutable snapshot changed: '+str(path))
                content=path.read_bytes()
            path.write_bytes(content);hashes[name]=hashlib.sha256(content).hexdigest()
    audit=ROOT/'docs/model-duplication-audit-2026-10-02/model-duplication-audit-2026-10-02.zip'
    with zipfile.ZipFile(audit)as z:
        for member in ('model-duplication-audit/technical/audit.json',):
            data=z.read(member);path=ROOT/'build/model-audit-read'/member;path.parent.mkdir(parents=True,exist_ok=True)
            if path.exists()and path.read_bytes()!=data:raise ValueError('immutable audit changed')
            path.write_bytes(data);hashes[member]=hashlib.sha256(data).hexdigest()
    (ROOT/'build/unified-input-hashes.json').write_text(json.dumps({'sourceCommit':SOURCE_REF,'files':hashes},indent=2)+'\n',encoding='utf8');print('Prepared pinned city/logical source and audit',len(hashes))
if __name__=='__main__':run()
