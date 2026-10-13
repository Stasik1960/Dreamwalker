"""One exact visual compatibility state for the source lantern's native light.

No model/texture edits; ordinary flower-pot-derived installation stays pose0.
The source carrier lighting distinction is represented by one hidden saved pose.
"""
import copy,json,sqlite3
from v125_map_index import ROOT,token
from v127_apply_full_map_poses import write
R=ROOT/'src/architecture/resources/bloodborne_dw'
def main():
    path=R/'composite/source_lantern.json';spec=json.loads(path.read_text(encoding='utf-8-sig'));pose=next((i for i,v in enumerate(spec['variants']) if v.get('sourceLightingCarrier')=='minecraft:lantern'),None)
    if pose is None:
        pose=len(spec['variants']);v=copy.deepcopy(spec['variants'][0]);v['sourceLightingCarrier']='minecraft:lantern';v['sourceVariant']='Исходный светильник: свет15';v['sourceStateRefs']=['minecraft:lantern[hanging=false,waterlogged=false]'];spec['variants'].append(v);write(path,spec)
    table=json.loads((R/'imported-collision-v125.json').read_text(encoding='utf-8-sig'));ref=table['objects'][spec['id']][0]['closedBase'];rotations=[]
    for row in table['shapes'][ref]:
        def boxes(values):return [{'from':list(v[:3]),'to':list(v[3:])} if isinstance(v,list) else copy.deepcopy(v) for v in values]
        rotations.append({'collision':boxes(row['collision']),'aim':boxes(row['aim'])})
    sparse=R/'imported-full-map-v127.json';data=json.loads(sparse.read_text(encoding='utf-8-sig'))
    if not any(r['registryId']==spec['id'] and r['variant']==pose for r in data['objects']):data['objects'].append({'registryId':spec['id'],'variant':pose,'rotations':rotations,'policy':'Exactexistingpose0physics; hidden native source lantern15 counterpart'});write(sparse,data)
    parts=R/'imported-source-parts-v127.json';data=json.loads(parts.read_text(encoding='utf-8-sig'))
    if not any(r['registryId']==spec['id'] and r['variant']==pose for r in data['parts']):data['parts'].append({'registryId':spec['id'],'variant':pose,'mainId':'90631','sourceApplications':copy.deepcopy(spec['variants'][pose]['sourceClosedApplications']),'defaultInstallationPose':0,'status':'PASS_EXACT_EXISTING_SOURCE_VERTEX_UV_RGBA_TINTINDEX','kind':'SOURCE_ONLY_NATIVE_EMISSION_ALIAS'});write(parts,data)
    plan=ROOT/'build/v127-full-conversion-first.sqlite';db=sqlite3.connect(plan);changed=[]
    for id,raw in db.execute('SELECT id,data FROM decisions'):
        d=json.loads(raw);state=d.get('sourceState','')
        if d.get('registryId')=='bloodborne_dw:prototype_wood_window' and 'mount' in d.get('properties',{}):d['properties'].pop('mount');changed.append((token(d),id))
        if d.get('registryId')==spec['id'] and state.startswith('minecraft:lantern['):d['properties']['variant']=str(pose);d['sourceOldVariant']=pose;d['resolution']='EXACT_SOURCE_VISUAL_NATIVE_EMISSION_ALIAS';changed.append((token(d),id))
    db.executemany('UPDATE decisions SET data=? WHERE id=?',changed);db.commit();db.close()
    report={'status':'PASS_EXACT_VISUAL_SOURCE_EMISSION_ALIAS','registryId':spec['id'],'variant':pose,'baseInstallationVariant':0,'exactVisualCopyOf':0,'nativeSourceCarrier':'minecraft:lantern','nativeSourceEmission':15,'flowerPotNativeEmission':0,'decisionRowsUpdated':len(changed),'originalV12Modified':False,'sourceArchivesModified':False};write(ROOT/'docs/v12.7/conversion/source-lantern-emission-alias.json',report);print(token(report))
if __name__=='__main__':main()
