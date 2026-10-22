"""Record root's visual statue decisions, historic protection and exact aliases."""
import hashlib,json
from pathlib import Path
P=Path(__file__).resolve().parents[2];R=P/'src/architecture/resources';H=Path(__file__).resolve().parent;O=P.parent/'research'
doc=json.loads((H/'statue-ids.json').read_text(encoding='utf-8'))
aliases=json.loads((R/'bloodborne_dw/imported-type-aliases-v127.json').read_text(encoding='utf-8'))
maps=next((v for k,v in aliases.items() if isinstance(v,list) and v and isinstance(v[0],dict) and 'oldRegistryId' in v[0]),[])
canonical={}
for e in doc['entries']:
    if e.get('models'):canonical[e['registryId']]=e
nonstatues={'bloodborne_dw:source_hold_statue_2':'Standalone carved pedestal; no sculptural person/creature/animal/bust in either model',
 'bloodborne_dw:source_addon_darkoak_door_bottom':'Standalone architectural plinth/panel; no sculptural figure in either variant',
 'bloodborne_dw:source_addon_darkoak_door_top':'Alias of architectural plinth/panel variant'}
protected={e['registryId'] for e in json.loads((H/'protected-v12-ids.json').read_text(encoding='utf-8'))['entries']}
for e in doc['entries']:
    rid=e['registryId'];target=rid
    mapping=next((m for m in maps if m['oldRegistryId']==rid and m.get('oldVariant',0)==0),None)
    if not e.get('models') and mapping:
        target=mapping['newRegistryId'];e['canonicalRegistryId']=target;e['canonicalVariant']=mapping.get('newVariant',0)
        parent=canonical.get(target)
        if parent:
            variants=[v for v in parent.get('variants',[]) if v['variant']==e['canonicalVariant']]
            e['variants']=variants;e['models']=[m for v in variants for m in v['models']]
    e['protectedV12']=rid in protected
    e['evidence']='research/statue-previews/sheet-0.png; generated from untouched source model JSON and original PNG textures'
    if rid in nonstatues or target in nonstatues:
        e['classification']='architecture_not_statue';e['basis']=nonstatues.get(rid,nonstatues.get(target))
    elif rid.startswith('bloodborne_rp:'):
        e['classification']='preserved_whole_entity';e['basis']='Actual RP entity route; never a decorative BlockEntity conversion candidate'
    elif e.get('models'):
        e['classification']='whole_statue';e['basis']='Textured model inspection confirms sculptural human/creature/animal/bust; own authored pedestal remains part of source model'
    else:
        e['classification']='preserve_unconfirmed_statue';e['basis']='Candidate alias unresolved; preserve this specific type locally'
doc['status']='TYPE_CLASSIFICATION_CONFIRMED';doc['previewSha256']=hashlib.sha256((O/'statue-previews/sheet-0.png').read_bytes()).hexdigest()
(H/'statue-ids.json').write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8')
(O/'classification-status.json').write_text(json.dumps({'status':'CONFIRMED','protectedV12RegistryCount':len(protected),'statues':sum(e['classification']=='whole_statue' for e in doc['entries']),'notStatues':[e['registryId'] for e in doc['entries'] if e['classification']=='architecture_not_statue'],'unconfirmed':[e['registryId'] for e in doc['entries'] if e['classification']=='preserve_unconfirmed_statue']},ensure_ascii=False,indent=2),encoding='utf-8')
