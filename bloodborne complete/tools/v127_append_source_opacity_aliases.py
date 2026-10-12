"""Split exact authored poses only where original native carrier opacity differs.

Hidden source-state counterparts preserve geometry, UV, source shifts and fitted
physics. Default/builder pose indices never move. No original V12 descriptor is
eligible. Inputs are measured native-source opacity conflicts, never guesses.
"""
import collections,copy,json,sqlite3
from v125_map_index import ROOT,token
from v127_apply_full_map_poses import write
R=ROOT/'src/architecture/resources/bloodborne_dw';O=ROOT/'docs/v12.7/conversion'
def main():
    conflicts=json.loads((ROOT/'build/v127-source-opacity-conflicts-from-tests10.json').read_text(encoding='utf-8-sig'));sparse=json.loads((R/'imported-full-map-v127.json').read_text(encoding='utf-8-sig'));parts=json.loads((R/'imported-source-parts-v127.json').read_text(encoding='utf-8-sig'));tables=[json.loads((R/f'imported-collision-v{n}.json').read_text(encoding='utf-8-sig')) for n in [125,126]];follow=json.loads((R/'imported-type-aliases-v127.json').read_text(encoding='utf-8-sig'))['mappings'];existing=O/'source-property-aliases.json';rows=json.loads(existing.read_text(encoding='utf-8-sig'))['rows'] if existing.exists() else [];db=sqlite3.connect(ROOT/'build/v127-full-conversion-first.sqlite');decisions={i:json.loads(raw) for i,raw in db.execute('SELECT id,data FROM decisions')};updated={};specs={}
    catalogue={r['registryId']:r['temporaryId'] for r in json.loads((R/'debug_catalogue.json').read_text(encoding='utf-8-sig'))['entries']}
    def fitted(reg,pose):
        for r in sparse['objects']:
            if r['registryId']==reg and r['variant']==pose:return copy.deepcopy(r['rotations'])
        for r in follow:
            if r['newRegistryId']==reg and r['newVariant']==pose:return fitted(r['oldRegistryId'],r['oldVariant'])
        for table in reversed(tables):
            if reg in table['objects']:
                refs=table['objects'][reg];ref=refs[pose if pose<len(refs) else 0]['closedBase'];out=[]
                for row in table['shapes'][ref]:
                    def b(values):return [{'from':list(v[:3]),'to':list(v[3:])} if isinstance(v,list) else copy.deepcopy(v) for v in values]
                    out.append({'collision':b(row['collision']),'aim':b(row['aim'])})
                return out
        raise ValueError('Missing fitted physics '+reg+'/'+str(pose))
    for group in conflicts:
        reg=group['registryId'];assert reg.startswith('bloodborne_dw:source_'),reg;old=group['variant'];levels=group['nativeLevels'];base=max(levels,key=lambda level:sum(r['count'] for r in levels[level]));spec=specs.setdefault(reg,json.loads((R/'composite'/f'{reg.split(":")[1]}.json').read_text(encoding='utf-8-sig')))
        for level,states in levels.items():
            if level==base:continue
            alias=next((r for r in rows if r['registryId']==reg and r['oldVariant']==old and r['opacity']==int(level) and r['open']==group['open']),None)
            if alias is None:
                pose=len(spec['variants']);assert pose< (480 if reg.endswith('source_assembly_brick_wall') else 160 if reg.endswith('source_assembly_deepslate_brick_wall') else 128),(reg,pose);v=copy.deepcopy(spec['variants'][old]);v['sourcePropertyAlias']=True;v['sourceVariant']=str(v.get('sourceVariant',''))+' · исходная светопроницаемость'+level;v['sourceStateRefs']=[r['state'] for r in states];spec['variants'].append(v);alias={'registryId':reg,'oldVariant':old,'newVariant':pose,'open':group['open'],'opacity':int(level),'baseOpacity':int(base),'sourceStates':[r['state'] for r in states],'sourceNativeCaseIds':[r['caseId'] for r in states],'geometryAndUvExactCopyOf':old};rows.append(alias);sparse['objects'].append({'registryId':reg,'variant':pose,'rotations':fitted(reg,old),'policy':'Exact existing pose physics; hidden measured native-source opacity alias'});parts['parts'].append({'registryId':reg,'variant':pose,'mainId':catalogue[reg],'sourceApplications':copy.deepcopy(v.get('sourceClosedApplications',[])),'defaultInstallationPose':old,'status':'PASS_EXACT_EXISTING_VERTEX_UV_RGBA_TINTINDEX','kind':'SOURCE_ONLY_NATIVE_OPACITY_ALIAS'})
            for state in states:
                for id,d in decisions.items():
                    if d.get('registryId')==reg and d.get('sourceState')==state['state'] and int(d.get('properties',{}).get('variant',0))+16*int(d.get('properties',{}).get('art_bank',0))==old:
                        pose=alias['newVariant'];d['properties']['variant']=str(pose%16)
                        if len(spec['variants'])>16:d['properties']['art_bank']=str(pose//16)
                        d['resolution']='EXACT_SOURCE_VISUAL_NATIVE_OPACITY_ALIAS';d['sourceOldVariant']=pose;updated[id]=d
    for reg,spec in specs.items():
        write(R/'composite'/f'{reg.split(":")[1]}.json',spec)
        if len(spec['variants'])>16:
            current=next((g for g in sparse['groups'] if g['registryId']==reg),None)
            if current is None:sparse['groups'].append({'registryId':reg,'poses':len(spec['variants']),'bankMax':7,'policy':'NEW-only exact source opacity aliases; old saved states default bank0'})
            else:current['poses']=len(spec['variants'])
            for d in decisions.values():
                if d.get('registryId')==reg and d.get('action') in {'COMPACT','OWNER'} and 'art_bank' not in d.get('properties',{}):d['properties']['art_bank']='0'
    for id,d in decisions.items():
        if d.get('registryId') in specs:updated[id]=d
    write(R/'imported-full-map-v127.json',sparse);write(R/'imported-source-parts-v127.json',parts);write(existing,{'schemaVersion':1,'status':'PASS_MEASURED_NATIVE_SOURCE_OPACITY_SPLITS','rows':rows,'originalV12Modified':False});db.executemany('UPDATE decisions SET data=? WHERE id=?',[(token(d),id) for id,d in updated.items()]);db.commit();db.close();print(token({'status':'PASS','aliasPoses':len(rows),'types':len(specs),'hiddenSourceParts':len(parts['parts']),'decisionRowsUpdated':len(updated),'bankedNewTypes':[reg for reg,s in specs.items() if len(s['variants'])>16]}))
if __name__=='__main__':main()
