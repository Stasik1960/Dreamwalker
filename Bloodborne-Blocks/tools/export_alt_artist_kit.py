"""Production-only artist package built from the existing ALT pack exporter.

Runtime IDs and assets are never modified. Friendly names are mapped explicitly
back to exact runtime paths and checked by hashes before packaging.
"""
from __future__ import annotations
import argparse,csv,gzip,hashlib,io,json,zipfile
from pathlib import Path
from export_alt_visual_starter import export,write_zip,json_bytes,model_file,texture_file

ROOT=Path(__file__).resolve().parents[1]
def sha(data):return hashlib.sha256(data).hexdigest()
def export_artist(resources:Path, output:Path, template:Path):
    template_manifest=export(resources,template)
    logical=resources/'bloodborne_blocks/logical'
    definitions=json.loads((logical/'definitions.json').read_bytes())['blocks']
    contracts={f['id']:f for f in json.loads((logical/'contracts-v2.json').read_bytes())['families']}
    production=json.loads((ROOT/'docs/production-logical-palette.json').read_bytes())['objects']
    ids={r['id'] for r in production if r['status']=='PRODUCTION'}
    if ids!={r['id'] for r in definitions} or ids!=set(template_manifest['families']):
        raise ValueError('artist kit must contain exactly the current production palette')
    meshes=json.loads(gzip.decompress((logical/'meshes.json.gz').read_bytes()))
    entries={};rows=[];family_manifests={}
    def add(path,data,runtime,kind,ident):
        if path in entries and entries[path]!=data:raise ValueError('friendly filename collision: '+path)
        if path not in entries:
            entries[path]=data;rows.append({'family':ident,'file':path,'runtime_path':runtime,'sha256':sha(data),'kind':kind})
        return path
    with zipfile.ZipFile(template) as pack:
        for d in sorted(definitions,key=lambda d:d['id']):
            ident=d['id'];prefix='ALT-Art-Kit/by-block/'+ident
            info={'id':ident,'canonical_anchor':contracts[ident]['canonical_anchor'],'states':{},'files':[]}
            for state,model in sorted(d['visual_models'].items()):
                visual=dict(v.split('=',1) for v in state.split(','))['visual']
                folder='BASE_REFERENCE' if visual=='base' else 'ALT_WORK'
                stem=model.rsplit('/',1)[1];friendly=ident+'__'+stem
                runtime='assets/'+model.split(':',1)[0]+'/models/'+model.split(':',1)[1]+'.json'
                source=model_file(resources,model)
                data=source.read_bytes() if visual=='base' else pack.read(runtime)
                path=add(prefix+'/'+folder+'/models/'+friendly+'.json',data,runtime,'model',ident)
                record={'model':model,'file':path,'mesh':d['models'][state],'textures':[]}
                if visual=='base':
                    mesh=meshes[d['models'][state]]
                    meshpath=prefix+'/BASE_REFERENCE/meshes/'+d['models'][state]+'.json'
                    add(meshpath,json_bytes(mesh),'bloodborne_blocks/logical/meshes.json.gz#'+d['models'][state],'reference_mesh',ident)
                    record['reference_mesh']=meshpath
                    for texture in sorted({p['texture'] for p in mesh['polygons']}):
                        original=texture_file(resources,texture)
                        if original is None:raise ValueError('missing BASE PNG: '+texture)
                        texture_runtime='assets/'+texture.split(':')[0]+'/textures/'+texture.split(':')[1]+'.png'
                        name=ident+'__'+texture.replace(':','__').replace('/','__')+'.png'
                        dest=add(prefix+'/'+folder+'/textures/'+name,original.read_bytes(),texture_runtime,'texture',ident)
                        record['textures'].append({'resource':texture,'file':dest,'runtime_path':texture_runtime})
                        meta=original.with_suffix('.png.mcmeta')
                        if meta.is_file():add(dest+'.mcmeta',meta.read_bytes(),texture_runtime+'.mcmeta','animation',ident)
                else:
                    spec=template_manifest['families'][ident]['states'][state]
                    for slot,t in spec['texture_slots'].items():
                        runtime_texture=t['file']
                        if runtime_texture is None:raise ValueError('ALT texture must be packaged for editing: '+t['source'])
                        name=ident+'__'+Path(runtime_texture).name
                        dest=add(prefix+'/'+folder+'/textures/'+name,pack.read(runtime_texture),runtime_texture,'texture',ident)
                        record['textures'].append({'slot':slot,'resource':t['resource'],'source':t['source'],'file':dest,'runtime_path':runtime_texture})
                        if runtime_texture+'.mcmeta' in pack.namelist():add(dest+'.mcmeta',pack.read(runtime_texture+'.mcmeta'),runtime_texture+'.mcmeta','animation',ident)
                info['states'][state]=record
            info['files']=[row for row in rows if row['family']==ident]
            family_manifests[ident]=info
            entries[prefix+'/info.json']=json_bytes(info)
    manifest={'schema_version':1,'families':family_manifests,'files':rows,'template_sha256':sha(template.read_bytes()),'visual_only':True}
    entries['ALT-Art-Kit/manifest.json']=json_bytes(manifest)
    csv_out=io.StringIO(newline='');writer=csv.DictWriter(csv_out,fieldnames=['family','file','runtime_path','sha256','kind']);writer.writeheader();writer.writerows(rows)
    entries['ALT-Art-Kit/manifest.csv']=csv_out.getvalue().encode('utf-8-sig')
    entries['ALT-Art-Kit/README_RU.md']='''# ALT Art Kit — Bloodborne Blocks

Каждая папка by-block соответствует одному production-предмету. BASE_REFERENCE содержит точные JSON исходных моделей и PNG; meshes содержит развёрнутую геометрию для моделей с bloodborne_mesh. ALT_WORK содержит готовые редактируемые модели bloodborne_polygons и их PNG. Координаты вершин — блоки, UV — 0..16.

Имена сделаны для работы художника. Не копируйте папку by-block напрямую в игру: перенесите изменённые файлы в пути runtime_path из info.json/manifest.csv внутри ALT-ResourcePack-Template.zip. Этот шаблон уже имеет рабочую структуру ресурс-пака Minecraft 1.20.1. Установите его выше ресурсов мода, а предмету выберите visual=alt.

Меняйте изображения, UV и геометрию ALT. Не меняйте registry IDs, anchor, helper-блоки, коллизию, packets или игровые состояния. BASE_REFERENCE — справочный оригинал. Изменения одной общей картинки для нескольких состояний согласуйте по manifest; одинаковые runtime_path должны получать одинаковые файлы. Сохраняйте PNG.mcmeta рядом с PNG.

Авторство исходного ресурспака сохранено в проекте; этот комплект не меняет права на исходные материалы.
'''.encode('utf8')
    for row in rows:
        if sha(entries[row['file']])!=row['sha256']:raise ValueError('manifest hash mismatch')
    write_zip(output,entries,False)
    return {'families':len(ids),'states':sum(len(f['states']) for f in family_manifests.values()),'files':len(entries),'sha256':sha(output.read_bytes()),'template_sha256':sha(template.read_bytes())}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('template',type=Path);p.add_argument('--resources',type=Path,default=ROOT/'src/main/resources');a=p.parse_args();print(json.dumps(export_artist(a.resources,a.output,a.template)))
