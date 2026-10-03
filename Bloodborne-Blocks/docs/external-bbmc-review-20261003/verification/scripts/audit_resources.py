import argparse, hashlib, json, zipfile, collections, struct, posixpath
from pathlib import Path

cli=argparse.ArgumentParser()
cli.add_argument('--inputs',type=Path,required=True)
cli.add_argument('--output',type=Path,required=True)
cli.add_argument('--project',type=Path,default=Path(__file__).resolve().parents[4])
options=cli.parse_args()
ROOT=options.output;ROOT.mkdir(parents=True,exist_ok=True)
DOWNLOADS=options.inputs
REPO=options.project
FILES=['bbmc_v1_resource (2).zip','bbmc_v13_resource (1).zip','bbmc_v14_resource (1).zip','bbmc_v15_resource (1).zip','Kappa_v5.2.zip','Bloodborne_X_Minecraft_mod_6.0 (1).jar','geckolib-forge-1.18-3.0.57 (1).jar','oculus-mc1.18.2-1.6.4 (1).jar','rubidium-0.5.6 (1).jar']

def audit(p):
    result={'file':str(p),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
    with zipfile.ZipFile(p) as z:
        infos=z.infolist(); names=z.namelist(); nameset=set(names)
        result.update(entries=len(names),crc_failure=z.testzip(),duplicates=[n for n,c in collections.Counter(names).items() if c>1],unsafe_paths=[n for n in names if n.startswith(('/', '\\')) or '..' in n.replace('\\','/').split('/')],uncompressed_bytes=sum(x.file_size for x in infos))
        result['categories']={k:sum(k in n and n.endswith(ext) for n in names) for k,ext in [('models/','.json'),('blockstates/','.json'),('textures/','.png'),('/geo/','.json'),('/animations/','.json'),('/sounds/','.ogg'),('shaders/','.fsh'),('optifine/','.properties')]}
        result['metadata']={n:z.read(n).decode('utf-8','replace') for n in names if n in ['pack.mcmeta','META-INF/mods.toml','META-INF/MANIFEST.MF'] or n.lower().split('/')[-1] in ['license','license.txt','readme.txt','credits.txt']}
        assets={n:hashlib.sha256(z.read(n)).hexdigest() for n in names if n.startswith('assets/') and not n.endswith('/')}
        result['namespace_files']=dict(collections.Counter(n.split('/')[1] for n in assets))
        result['asset_hashes']=assets
        models={}; states={}; invalid=[]; comments=[]; textures={}; refs=[]
        for n in names:
            if n.endswith('.json'):
                try: d=json.loads(z.read(n))
                except Exception as e:
                    raw=z.read(n).decode('utf-8')
                    try:
                        d=json.loads('\n'.join(s for s in raw.splitlines() if not s.lstrip().startswith('//')))
                        comments.append({'path':n,'strict_json_error':str(e),'line_comments_only':True})
                    except Exception:
                        invalid.append({'path':n,'error':str(e)});continue
                if '/models/' in n: models[n]=d
                if '/blockstates/' in n: states[n]=d
            if n.endswith('.png'):
                raw=z.read(n)
                if raw[:8]==b'\x89PNG\r\n\x1a\n':textures[n]=list(struct.unpack('>II',raw[16:24]))
        def modelpath(r):
            ns,_,s=r.partition(':')
            if not _: ns,s='minecraft',r
            return f'assets/{ns}/models/{s}.json'
        def texpath(r):
            ns,_,s=r.partition(':')
            if not _: ns,s='minecraft',r
            return f'assets/{ns}/textures/{s}.png'
        for n,d in models.items():
            if not isinstance(d,dict): continue
            if 'parent' in d: refs.append((n,'parent',modelpath(d['parent'])))
            for v in d.get('textures',{}).values():
                if isinstance(v,str) and not v.startswith('#'):refs.append((n,'texture',texpath(v)))
        def walk(v,src):
            if isinstance(v,dict):
                if isinstance(v.get('model'),str):refs.append((src,'blockstate-model',modelpath(v['model'])))
                for a in v.values():walk(a,src)
            elif isinstance(v,list):
                for a in v:walk(a,src)
        for n,d in states.items():walk(d,n)
        result['invalid_json']=invalid
        result['commented_json']=comments
        result['missing_nonvanilla_references']=[{'source':a,'kind':b,'target':c} for a,b,c in refs if c not in nameset and not c.startswith('assets/minecraft/')]
        result['missing_minecraft_references']=[{'source':a,'kind':b,'target':c} for a,b,c in refs if c not in nameset and c.startswith('assets/minecraft/')]
        result['texture_dimensions']=dict(collections.Counter('x'.join(map(str,v)) for v in textures.values()))
        result['texture_paths']=textures
        result['model_metrics']={'models':len(models),'blockstates':len(states),'elements':sum(len(d.get('elements',[])) for d in models.values() if isinstance(d,dict)),'max_elements':max([len(d.get('elements',[])) for d in models.values() if isinstance(d,dict)] or [0])}
        oversized=[]
        for n,d in models.items():
            if not isinstance(d,dict):continue
            for i,e in enumerate(d.get('elements',[])):
                coords=e.get('from',[])+e.get('to',[])
                if any(x < 0 or x > 16 for x in coords):oversized.append({'model':n,'element':i,'from':e.get('from'),'to':e.get('to')})
        result['oversized_elements_count']=len(oversized)
        result['oversized_examples']=oversized[:20]
        result['top_complex_models']=sorted([{'path':n,'elements':len(d.get('elements',[]))} for n,d in models.items() if isinstance(d,dict)],key=lambda x:x['elements'],reverse=True)[:20]
        result['mod_class_refs']={s:sum(s.encode() in z.read(n) for n in names if n.endswith('.class')) for s in ['software/bernie/geckolib','me/jellysquid','net/coderbot','java/net/','java/lang/Runtime','java/lang/ProcessBuilder']}
        result['interesting_paths']=[n for n in names if any(w in n.lower() for w in ['emissive','license','credit','readme','normal','specular','colormap','cit/','ctm/'])][:100]
    return result

results=[audit(DOWNLOADS/f) for f in FILES]
source=audit(REPO/'reference-inputs/source-resource-pack.zip');results.append(source)
deltas=[]
for a,b in zip(results[:3],results[1:4]):
    ah,bh=a['asset_hashes'],b['asset_hashes'];deltas.append({'from':Path(a['file']).name,'to':Path(b['file']).name,'unchanged':sum(ah[k]==bh[k] for k in ah.keys()&bh.keys()),'changed':sum(ah[k]!=bh[k] for k in ah.keys()&bh.keys()),'added':len(bh.keys()-ah.keys()),'removed':len(ah.keys()-bh.keys()),'changed_paths':[k for k in ah.keys()&bh.keys() if ah[k]!=bh[k]]})
out={'audits':results,'deltas':deltas,'old_pack_exact_match':source['sha256']==results[0]['sha256']}
(ROOT/'resource-analysis.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
summary={'old_pack_exact_match':out['old_pack_exact_match'],'audits':[{k:a[k] for k in ['file','sha256','entries','crc_failure','categories','namespace_files','invalid_json','model_metrics','oversized_elements_count','mod_class_refs','metadata']} for a in results],'deltas':[{k:v for k,v in d.items() if k!='changed_paths'} for d in deltas]}
print(json.dumps(summary,ensure_ascii=False,indent=2))
