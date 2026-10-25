"""Fetch author-published Modrinth versions and verify their published hashes."""
import hashlib,json,shutil,sys,traceback,urllib.parse,urllib.request,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';A=T/'addons';D=A/'downloads'
def get(url):
    req=urllib.request.Request(url,headers={'User-Agent':'Dreamwalker-local-compatibility-check/1.0'})
    with urllib.request.urlopen(req,timeout=90) as response:return response.read()
def api(path):return json.loads(get('https://api.modrinth.com/v2/'+path))
def fetch(version):
    file=next((f for f in version['files'] if f.get('primary')),version['files'][0]);name=file['filename'];assert Path(name).name==name
    target=D/name
    data=target.read_bytes() if target.exists() else get(file['url'])
    for algorithm in ('sha512','sha1'):
        if algorithm in file['hashes']:assert hashlib.new(algorithm,data).hexdigest()==file['hashes'][algorithm]
    if not target.exists():target.write_bytes(data)
    out={'path':str(target),'sha256':hashlib.sha256(data).hexdigest(),'url':file['url'],'version':version['version_number'],'versionId':version['id'],'projectId':version['project_id'],'dependencies':version['dependencies'],'gameVersions':version['game_versions']}
    if target.suffix=='.jar':
        with zipfile.ZipFile(target) as z:out['fabricMod']=json.loads(z.read('fabric.mod.json'))
    return out
def latest_shader(project):
    versions=api('project/'+project+'/version?'+urllib.parse.urlencode({'game_versions':json.dumps(['1.20.1']),'loaders':json.dumps(['iris'])}))
    return next(v for v in versions if v['version_type']=='release' and (project!='sildurs-enhanced-default-shaders' or any('fast' in f['filename'].lower() for f in v['files'])))
def main():
    D.mkdir(parents=True,exist_ok=True)
    if (A/'downloads.json').exists() and not (A/'downloads-before-sodium13.json').exists():shutil.copy2(A/'downloads.json',A/'downloads-before-sodium13.json')
    result={'iris':fetch(api('version/s5eFLITc')),'sodium':fetch(api('version/OihdIimA'))}
    for key,project in (('sildurFast','sildurs-enhanced-default-shaders'),('makeupLow','makeup-ultra-fast-shaders')):
        result[key]=fetch(latest_shader(project))
        meta=api('project/'+project);result[key]['license']=meta['license'];result[key]['sourceUrl']=meta.get('source_url')
    (A/'downloads.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except BaseException:
        A.mkdir(exist_ok=True);(A/'download-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
