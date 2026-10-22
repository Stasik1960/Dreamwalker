"""Finite immutable gameplay dictionaries, not a world or owner-graph scan."""
import collections,importlib.util,json,sqlite3
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.control_check();db=sqlite3.connect('file:'+str(m.DEFAULT_PLAN/'plan.sqlite').replace('\\','/')+'?mode=ro',uri=True)
try:
    metadata={key:json.loads(value) for key,value in db.execute('SELECT key,value FROM meta')};binding=metadata['binding'];source=m.checked(binding['source']);manifest=m.read(binding['copyManifest']);refs={};evidence=[]
    def walk(tag,path,found):
        if tag.type==10:
            fields=m.compound(tag);kind=fields.get('Kind',m.Tag(8,'')).value
            if kind in ('ARCHITECTURE','BLOCK','RP','ENTITY') and 'Dimension' in fields and 'Registry' in fields:
                uid=fields.get('Instance',fields.get('Uuid',fields.get('UUID')));found.append({'nbtPath':path,'kind':kind,'dimension':fields['Dimension'].value,'registryId':fields['Registry'].value,'uuid':m.canonical(uid.value) if uid and uid.type==11 else None,'rootPacked':fields.get('Root',m.Tag(4,0)).value})
            for key,value in fields.items():walk(value,path+'/'+key,found)
        elif tag.type==9:
            for index,value in enumerate(tag.value):walk(value,path+'/'+str(index),found)
    for rel,row in manifest['files'].items():
        if Path(rel).parent.name!='data' or not rel.endswith('.dat') or not any(key in Path(rel).name for key in ('lever_groups','mechanism_links','mechanism_rules','object_policies','tool_commands')):continue
        path=m.checked(source/rel);m.require(m.sha(path)==row['sha256'],'Immutable gameplay metadata binding changed: '+rel);m.require(path.stat().st_size<=16*1024*1024,'Finite gameplay metadata exceeds bounded audit size')
        nbt=m.read_nbt(path);root=m.compound(nbt.root);data=m.compound(root['data']) if 'data' in root else root;found=[];walk(nbt.root,'',found)
        evidence.append({'path':rel,'sha256':row['sha256'],'bytes':path.stat().st_size,'topLevelFields':{k:{'type':v.type,'count':len(v.value) if isinstance(v.value,(list,dict,str,bytes)) else None} for k,v in data.items()},'references':found})
        for item in found:
            if item['kind']=='ARCHITECTURE' and item['uuid']:
                dim={'minecraft:overworld':'.','minecraft:the_nether':'DIM-1','minecraft:the_end':'DIM1'}.get(item['dimension'],item['dimension'].replace(':','/',1))
                value=refs.setdefault((dim,item['uuid']),{'dimension':dim,'uuid':item['uuid'],'registryId':item['registryId'],'sourceFiles':[]});value['sourceFiles'].append(rel)
    for key,value in refs.items():
        row=db.execute('SELECT pose,x,y,z,kind,reason FROM objects WHERE dim=? AND uuid=?',key).fetchone();value['savedObject']={'pose':row[0],'root':list(row[1:4]),'kind':row[4],'reason':row[5]} if row else None
        value['selectedInPilot']=bool(db.execute('SELECT 1 FROM cleanup_owners WHERE dim=? AND uuid=?',key).fetchone())
    result={'schemaVersion':1,'status':'READ_ONLY_INPUT_AUDIT','worldScan':False,'mapWrites':0,'ownerGraphClosure':False,'files':evidence,'architectureReferences':list(refs.values()),'selectedPilotReferences':sum(v['selectedInPilot'] for v in refs.values())}
    m.atomic_json(m.ROOT/'research'/'full-functional-metadata-audit.json',result)
finally:db.close()
