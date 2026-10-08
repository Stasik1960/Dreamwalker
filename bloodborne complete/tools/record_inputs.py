"""Hash user inputs and record reproducible source provenance; no source writes."""
from __future__ import annotations
import hashlib, json, subprocess, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INPUTS = [
    ('assignment', 'C:/Users/vakir/Downloads/new_prompt_revised.txt'),
    ('world', 'C:/Users/vakir/Downloads/bbmc_v16_map (1).zip'),
    ('resource_pack', 'C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip'),
    ('rp_runtime', 'C:/Users/vakir/Limacina/project/dw/mods/bloodborne-rp-1.0.0-rp.2-full-local.jar'),
    ('rp_source', 'C:/Users/vakir/Downloads/Bloodborne-RP-Fixes-1.20.1-20261006/sources/bloodborne-rp-1.0.0-rp.2-full-source.zip'),
    ('shader_settings', 'C:/Users/vakir/Downloads/Kappa_BB.zip (1).txt'),
    ('shader', 'C:/Users/vakir/Downloads/Kappa_v5.2.zip'),
    ('modpack', 'C:/Users/vakir/Limacina/project/dw/mods.zip'),
]

def additional_references():
    """Separate original Forge QA basis; never relabel the eight user inputs."""
    references = [
        ('source_forge_bloodborne', Path('C:/Users/vakir/Downloads/Bloodborne_X_Minecraft_mod_6.0.jar')),
        ('source_forge_geckolib', Path('C:/Users/vakir/Downloads/geckolib-forge-1.18-3.0.57.jar')),
        ('source_minecraft_metadata', ROOT/'inputs/extracted/vanilla-1.18.2/version.json'),
        ('source_minecraft_client', ROOT/'inputs/extracted/vanilla-1.18.2/client.jar'),
        ('source_minecraft_client_mappings', ROOT/'inputs/extracted/vanilla-1.18.2/client-mappings.txt'),
        ('source_forge_client_profile', ROOT/'build/source-client-forge-1.18.2/versions/1.18.2-forge-40.2.0/1.18.2-forge-40.2.0.json'),
    ]
    rows=[]
    vanilla=json.loads((ROOT/'inputs/extracted/vanilla-1.18.2/version.json').read_text(encoding='utf8'))
    for role,path in references:
        row={'role':role,'name':path.name,'path':path.as_posix(),'size':path.stat().st_size,'sha256':sha256(path),
             'purpose':'Isolated original-source Forge client physics QA; excluded from the Fabric product JAR'}
        if role in ('source_forge_bloodborne','source_forge_geckolib'):
            with zipfile.ZipFile(path) as archive:
                manifest=archive.read('META-INF/MANIFEST.MF').decode('utf8').replace('\r','')
                values=dict(line.split(': ',1) for line in manifest.splitlines() if ': ' in line)
                row['implementation_version']=values.get('Implementation-Version')
                row['mods_toml_sha256']=hashlib.sha256(archive.read('META-INF/mods.toml')).hexdigest()
            row['filename_version']='6.0' if role=='source_forge_bloodborne' else '3.0.57'
            row['mod_id']='bloodborne' if role=='source_forge_bloodborne' else 'geckolib3'
        if role in ('source_minecraft_client','source_minecraft_client_mappings'):
            download=vanilla['downloads']['client' if role=='source_minecraft_client' else 'client_mappings']
            row['official_url']=download['url'];row['official_sha1']=download['sha1']
            if hashlib.sha1(path.read_bytes()).hexdigest()!=download['sha1']:raise RuntimeError('Official Minecraft source checksum mismatch: '+str(path))
        if role=='source_minecraft_metadata':
            row['minecraft_version']=vanilla['id']
            if vanilla['id']!='1.18.2':raise RuntimeError('Wrong source Minecraft metadata version')
        if role=='source_forge_client_profile':
            profile=json.loads(path.read_text(encoding='utf8'))
            row.update(profile_id=profile['id'],minecraft_inherits=profile['inheritsFrom'])
            if (profile['id'],profile['inheritsFrom'])!=('1.18.2-forge-40.2.0','1.18.2'):raise RuntimeError('Wrong installed original Forge client profile')
        rows.append(row)
    return rows
def sha256(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for data in iter(lambda: f.read(1024*1024), b''): h.update(data)
    return h.hexdigest()
def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT.parent,text=True).strip()
def main():
    result = {'schema':'dreamwalker-inputs-v1','recorded_date':'2026-10-07','timezone':'Europe/Sofia',
        'artifact_name':'dreamwalker-bb-fabric-1.20.1','mod_id':'bloodborne_dw','rp_namespace':'bloodborne_rp',
        'inputs':[{'role':role,'name':Path(path).name,'path':path,'size':Path(path).stat().st_size,'sha256':sha256(path)} for role,path in INPUTS],
        'repository':{'url':'https://github.com/Stasik1960/Dreamwalker','working_head':git('rev-parse','HEAD'),
        'branch':git('branch','--show-current'),'remote_heads':git('ls-remote','origin','refs/heads/main','refs/heads/bloodborne'),
        'specified_main':'12e35e0d282837510d79f3b8b33c3ebc088cf380','specified_bloodborne':'ca01e3d94630691acf43d8132606bc5f55c628e0'},
        'world':{'filename_version':'v16','source_minecraft':'PENDING_NBT','dimensions':'PENDING_NBT'},
        'resource_pack':{'filename_version':'v15'},
        'target':{'minecraft':'1.20.1','java':17,'loom':'1.6.12','yarn':'1.20.1+build.10:v2',
            'loader':'PENDING_MODPACK_AUDIT','fabric_api':'PENDING_MODPACK_AUDIT','geckolib':'PENDING_MODPACK_AUDIT','build_status':'NOT_RUN'},
        'reuse':[{'source':'Bloodborne-Blocks/tools/world_io.py','repository_sha':git('rev-parse','HEAD'),
            'sha256':sha256(ROOT/'tools/world_io.py'),'purpose':'typed NBT/Anvil decoding only; no historical mappings'}]}
    with zipfile.ZipFile(INPUTS[2][1]) as z: result['resource_pack']['pack_mcmeta']=json.loads(z.read('pack.mcmeta'))
    output=ROOT/'INPUTS.json'
    if output.exists():
        old=json.loads(output.read_text(encoding='utf-8'))
        for prior,new in zip(old['inputs'], result['inputs']):
            if (prior['path'],prior['sha256']) != (new['path'],new['sha256']): raise RuntimeError('Original input changed: '+new['path'])
        for name in ['world','target']: result[name]={**result[name],**old.get(name,{})}
        for name in ['priority_clarification','additional_reference_inputs']:
            if name in old:result[name]=old[name]
        for reference in result.get('additional_reference_inputs',[]):
            if sha256(reference['path'])!=reference['sha256']:raise RuntimeError('Original reference input changed: '+reference['path'])
        # The inspected repository basis remains stable after a checkpoint commit.
        result['repository']['source_basis_head']=old['repository'].get('source_basis_head',old['repository']['working_head'])
        result['reuse']=old.get('reuse',result['reuse'])
        if old.get('additional_reference_inputs'):
            result['additional_reference_inputs']=additional_references()
            prior={row['role']:row for row in old['additional_reference_inputs']}
            for row in result['additional_reference_inputs']:
                if row['role'] in prior and (prior[row['role']]['path'],prior[row['role']]['sha256'])!=(row['path'],row['sha256']):
                    raise RuntimeError('Original reference input changed: '+row['path'])
            result['additional_reference_integrity_recheck']={'status':'PASS','reference_input_count':len(result['additional_reference_inputs']),
                'source_minecraft':'1.18.2','source_forge':'40.2.0','date':'2026-10-07'}
    else:
        result['repository']['source_basis_head']=result['repository']['working_head']
    result['integrity_recheck']={'status':'PASS','input_count':len(INPUTS),'date':'2026-10-07'}
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Recorded '+str(len(result['inputs']))+' immutable source hashes.')
if __name__=='__main__': main()
