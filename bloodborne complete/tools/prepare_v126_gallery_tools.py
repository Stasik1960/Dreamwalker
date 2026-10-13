"""New isolated QA tools; previous gallery sources/releases remain immutable."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def put(p,s):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf8',newline='\n')
def main():
    names=['build_gallery_v125_addon','prepare_gallery_v125_input','verify_gallery_v125','finalize_gallery_v125_world','prepare_v125_delivery_world','prepare_gallery_v125_capture','package_v125_release','record_v125_acceptance']
    for name in names:
        source=ROOT/'tools'/f'{name}.py';target=ROOT/'tools'/f'{name.replace("v125","v126")}.py'
        assert not target.exists()
        put(target,source.read_text(encoding='utf8').replace('v125','v126').replace('V125','V126').replace('v12.5','v12.6').replace('V12.5','V12.6').replace('city-review','functional-assemblies'))
    source=ROOT/'tools/gallery-v125/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV125Author.java'
    s=source.read_text(encoding='utf8').replace('v125','v126').replace('V125','V126').replace('V12.5','V12.6')
    s=s.replace('row.addProperty("registrationsMerged",false);row.addProperty("doorPropertiesChanged",false);','row.addProperty("registrationsMerged",true);row.addProperty("doorPropertiesChanged",row.get("section").getAsString().equals("doors"));')
    s=s.replace('label(label,row.get("russianLabel").getAsString(),"",row.get("section").getAsString(),"Сборка для просмотра")','label(label,row.get("russianLabel").getAsString(),row.get("primaryId").getAsString(),row.get("section").getAsString(),row.has("open")&&row.get("open").getAsBoolean()?"Открыто на 90°":"Цельный объект")')
    put(ROOT/'tools/gallery-v126/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV126Author.java',s)
    p=ROOT/'tools/verify_gallery_v126.py';s=p.read_text(encoding='utf8').replace("assert not group['registrationsMerged'] and not group['doorPropertiesChanged']","assert group['registrationsMerged'] and len(group['components'])==1")
    put(p,s)
    p=ROOT/'tools/prepare_gallery_v126_input.py';s=p.read_text(encoding='utf8')
    s=s.replace("numbers = {row['registryId']: row for row in debug['entries']}","numbers = {row['registryId']: row for row in debug['entries']}\n    assembly_plan=json.loads((ROOT/'build/v126-assembly-plan/assembly-manifest.json').read_text(encoding='utf8'))\n    hidden={n for g in assembly_plan['assemblies'] for n in g['hideIndependentPartIds']}|{'90479'}\n    retired.update(r['registryId'] for r in debug['entries'] if r['temporaryId'] in hidden)\n    installations={g['primaryRegistryId']:g['installationPose'] for g in assembly_plan['assemblies']}")
    s=s.replace("row['variant'] = 0\n            row['state'] = {'variant': '0', 'rotation': '0', 'open': 'false', 'profile': 'base'}","row['variant'] = installations.get(registry,0)\n            row['state'] = {'variant': str(row['variant']%16), 'rotation': '0', 'open': 'false', 'profile': 'base'}")
    start=s.index("        preview_file=ROOT/");end=s.index("        boxes=[component_geometry",start)
    s=s[:start]+'''        plan=json.loads((ROOT/'build/v126-assembly-plan/assembly-manifest.json').read_text(encoding='utf8'))
        doors=json.loads((ROOT/'build/v126-door-plan/door-manifest.json').read_text(encoding='utf8'))
        previews=[]
        definitions=[(g['primaryRegistryId'],g['primaryId'],g['installationPose'],g['displayName'],'assemblies',False) for g in plan['assemblies'] if not g['doorRequiresLeafPartitionAndOpenPose']]
        for g in doors['doors']:
            descriptor=json.loads(archive.read('bloodborne_dw/composite/'+g['registryId'].split(':')[1]+'.json'))
            number=next(r['temporaryId'] for r in json.loads(archive.read('bloodborne_dw/debug_catalogue.json'))['entries'] if r['registryId']==g['registryId'])
            for opened in (False,True):definitions.append((g['registryId'],number,g['variant'],descriptor['displayName'],'doors',opened))
        for registry,number,pose,name,section,opened in definitions:
            specpath='bloodborne_dw/composite/'+registry.split(':')[1]+'.json'
            component={'number':1,'relativePosition':[0,0,0],'registryId':registry,'mainId':number,
                'properties':{'variant':str(pose%16),'rotation':'0','profile':'base','open':str(opened).lower()},
                'mountY':0,'sourceShift':[0,0,0],'payload':{'ImportedTypeVersion':1},
                'descriptor':'src/architecture/resources/'+specpath,'sourceApplications':[]}
            component['descriptorSha256']=hashlib.sha256(archive.read(specpath)).hexdigest()
            row={'key':'V126-'+section+'-'+number+('-open' if opened else '-closed'),'section':section,
                 'primaryId':number,'russianLabel':name,'open':opened,'components':[component],
                 'registrationsMerged':True,'doorPropertiesChanged':section=='doors',
                 'status':'WORKING_ONE_PIECE_TYPE_RMB_90_DEGREES' if section=='doors' else 'APPROVED_ONE_PIECE_TYPE'}
            row['localVisualBounds']=component_geometry(component,archive,models)
            previews.append(row)
        city=json.loads((ROOT/'docs/v12.6/city/city-final-aggregated.json').read_text(encoding='utf8'))
''' +s[end:]
    # The historical default table remains complete; the V12.6 resource is a scoped override.
    s=s.replace("defaults=json.loads(archive.read('bloodborne_dw/imported-collision-v126.json'))","defaults=json.loads(archive.read('bloodborne_dw/imported-collision-v125.json'))")
    put(p,s)
    print('Prepared fresh V12.6 gallery tooling; final production/native verification still required')
if __name__=='__main__':main()
