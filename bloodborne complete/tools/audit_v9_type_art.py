"""Read-only V9 compatibility/art audit against the immutable delivered V8 source.

No gameplay/visual PASS is inferred. The report proves only exact imported art,
append-only TEMP allocation and compatibility descriptor relationships.
"""
import hashlib
import json
import argparse
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
SOURCE=ROOT/'build/delivery/v8/dreamwalker-bb-fabric-1.20.1-review-v8-full-source.zip'
PREFIX='dreamwalker-bb-fabric-1.20.1-full-source-review-v8/'
def sha(data):return hashlib.sha256(data).hexdigest()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path);args=parser.parse_args()
    with zipfile.ZipFile(SOURCE) as archive:
        def old(path):return archive.read(PREFIX+'src/architecture/resources/'+path)
        legacy_path='bloodborne_dw/composite/prototype_thin_window.json'
        legacy_bytes=(RES/legacy_path).read_bytes();legacy=json.loads(legacy_bytes)
        before=json.loads(old(legacy_path));new=json.loads((RES/'bloodborne_dw/composite/prototype_glass_window_02.json').read_text(encoding='utf8'))
        assert legacy==before,'Legacy three-form installed/source descriptor altered'
        assert new['variants']==[before['variants'][1]],'New window02 art differs from exact V8 source variant1'
        assert new['globalOrientations']==4 and new['orientationStepDegrees']==90
        tree_path='bloodborne_dw/composite/prototype_tree.json';tree=json.loads((RES/tree_path).read_text(encoding='utf8'));old_tree=json.loads(old(tree_path))
        assert len(tree['variants'])==1 and tree['variants'][0]['poses']['closed']['parts']==old_tree['variants'][1]['poses']['closed']['parts'],'Accepted V8 tree artwork changed'
        tree_parts=tree['variants'][0]['poses']['closed']['parts']
        assert len(tree_parts)==18 and sum('/lower_source_' in part['model'] for part in tree_parts)==2
        roof_path='bloodborne_dw/composite/prototype_roof.json';roof=json.loads((RES/roof_path).read_text(encoding='utf8'));old_roof=json.loads(old(roof_path))
        assert roof['variants'][0]['poses']['closed']['parts']==old_roof['variants'][0]['poses']['closed']['parts'],'Roof source artwork changed'
        assert roof['variants'][0]['poses']['closed']['collision']==[{'from':[0,0,0],'to':[16,16,16]}],'Roof physical grid cube is not exact one cell'
        table=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf8'))
        baseline=json.loads(old('bloodborne_dw/debug_catalogue.json'))
        previous={entry['registryId']:entry['temporaryId'] for entry in baseline['entries']}
        actual={entry['registryId']:entry['temporaryId'] for entry in table['entries']}
        assert len(table['entries'])==len(set(actual.values()))==117
        assert all(actual[id]==number for id,number in previous.items())
        assert all(entry['finalId'] is None for entry in table['entries'])
        appended=[entry for entry in table['entries'] if entry['registryId'] not in previous]
        assert len(appended)==10 and {entry['temporaryId'] for entry in appended}=={str(i) for i in range(90010,90020)}
        reserved=[entry for entry in table['entries'] if entry.get('retiredNumberReserved')]
        assert len(reserved)==7 and all(entry['canonicalRegistryId'] in actual for entry in reserved)
        wall_loot=json.loads((RES/'data/bloodborne_dw/loot_tables/blocks/prototype_wall.json').read_text(encoding='utf8'));loot_rows=[]
        assert wall_loot==json.loads(old('data/bloodborne_dw/loot_tables/blocks/prototype_wall.json'))
        pickaxe=json.loads((RES/'data/minecraft/tags/blocks/mineable/pickaxe.json').read_text(encoding='utf8'))
        for material in [0,1,2,3,4,5,7]:
            registry='bloodborne_dw:prototype_wall_skin_'+str(material);path='data/bloodborne_dw/loot_tables/blocks/prototype_wall_skin_'+str(material)+'.json';data=(RES/path).read_bytes()
            current=json.loads(data)
            def old_id(value):
                if isinstance(value,dict):
                    if value.get('type')=='minecraft:item' and value.get('name')==registry:value['name']='bloodborne_dw:prototype_wall'
                    for child in value.values():old_id(child)
                elif isinstance(value,list):
                    for child in value:old_id(child)
            old_id(current);assert current==wall_loot,'Independent type changed primary loot behavior: '+registry
            assert registry in pickaxe['values'],'Independent wall type lost primary mining tool behavior: '+registry
            loot_rows.append({'path':path,'sha256':sha(data),'nativeDataNamespace':True,'onlyItemIdDiffersFromPrimary':True,'primarySurvivesExplosionPreserved':True,'pickaxeTagPreserved':True})
        checked=[]
        # Only the original imported/art models and PNG closure: runtime state
        # descriptors, item registrations and collision changes are separate.
        for name in archive.namelist():
            start=PREFIX+'src/architecture/resources/'
            if not name.startswith(start):continue
            path=name[len(start):]
            relevant=(path.startswith('assets/bloodborne_dw/models/block/wall/')
                      or path.startswith('assets/bloodborne_dw/models/tree_proposal/')
                      or path.startswith('assets/bloodborne_dw/models/roof/')
                      or '/models/base/source/minecraft/block/hold/window_0' in path
                      or '/models/alt/prototype_windows/thin/' in path
                      or '/models/base/source/minecraft/block/hold/wood_ladder_0' in path)
            if not relevant or not path.endswith('.json'):continue
            data=archive.read(name);current=(RES/path).read_bytes()
            assert current==data,'Imported source model bytes changed: '+path
            checked.append({'path':path,'sha256':sha(data),'bytes':len(data)})
        assert len(checked)>40
        textures={}
        visited=set()
        def closure(model_path):
            if model_path in visited:return
            visited.add(model_path)
            current_model=(RES/model_path).read_bytes();assert current_model==old(model_path),'Imported source model dependency changed: '+model_path
            if not any(row['path']==model_path for row in checked):checked.append({'path':model_path,'sha256':sha(current_model),'bytes':len(current_model)})
            model=json.loads(current_model)
            for value in model.get('textures',{}).values():
                if value.startswith('#'):continue
                namespace,local=value.split(':',1) if ':' in value else ('minecraft',value)
                texture='assets/'+namespace+'/textures/'+local+'.png'
                if not (RES/texture).is_file():continue # Vanilla source-atlas dependency; not a custom PNG claim.
                current=(RES/texture).read_bytes();assert current==old(texture),'Imported PNG bytes changed: '+texture
                textures[texture]={'path':texture,'sha256':sha(current),'bytes':len(current)}
            parent=model.get('parent')
            if parent and parent not in ('builtin/entity','builtin/generated'):
                namespace,local=parent.split(':',1) if ':' in parent else ('minecraft',parent)
                parent_path='assets/'+namespace+'/models/'+local+'.json'
                if (RES/parent_path).is_file():closure(parent_path)
        for row in checked:closure(row['path'])
        for part in tree_parts+roof['variants'][0]['poses']['closed']['parts']:
            for key in ('model','altModel'):
                namespace,local=part[key].split(':',1);closure('assets/'+namespace+'/models/'+local+'.json')
    report={'schema':'dreamwalker-v9-type-art-audit-v1','status':'PASS_READ_ONLY_APPEND_ONLY_TYPES_AND_EXACT_IMPORTED_ART',
            'baselineSourceSha256':sha(SOURCE.read_bytes()),'legacyThreeFormDescriptorJsonEqual':True,
            'legacyThreeFormDescriptorCurrentSha256':sha(legacy_bytes),'window02VariantEqualsOriginalVariant1':True,
            'ordinaryGlazingYawDegrees':[0,90,180,270],'ordinaryGlazingTypes':['90004','90010'],
            'acceptedTree':{'oldAcceptedVariant':1,'newCanonicalVariant':0,'artPartsDeepEqualV8Accepted':True,'parts':18,'lowerParts':2,'upperParts':16,'selectionVolumes':len(tree['variants'][0]['poses']['closed']['selection']),'collisionVolumes':len(tree['variants'][0]['poses']['closed']['collision'])},
            'roof':{'sourceArtPartsDeepEqualV8':True,'physicalVolume':'one exact root grid cube [0,0,0]..[16,16,16]','collisionVolumes':1},
            'legacyWindow03':'Only installed/source compatibility. New items canonicalize to90010 without inherited angular pose or source-placement privileges.',
            'originalTempNumbersUnchanged':107,'catalogueRowsIncludingReserved':117,'appendedTempTypes':appended,
            'retiredRpTempNumbersReserved':reserved,'stateAliases':table['stateAliases'],
            'independentWallLootCompatibility':loot_rows,
            'importedModelFilesByteExact':checked,'importedPngFilesByteExact':list(textures.values()),
            'actualRuntimeItemPlacement':'SEPARATE_NATIVE_GAMETEST_EVIDENCE_REQUIRED',
            'actualClientBakes':'SEPARATE_ORDINARY_CLIENT_QA_EVIDENCE_REQUIRED','manualVisualAcceptance':'NOT_RUN','finalNumericIdsAssigned':False,'wholeCityConversion':False}
    if args.jar:
        selected={row['path'] for row in checked}|set(textures)|{legacy_path,'bloodborne_dw/composite/prototype_glass_window_02.json',tree_path,roof_path,'bloodborne_dw/debug_catalogue.json','data/bloodborne_dw/loot_tables/blocks/prototype_wall.json','data/minecraft/tags/blocks/mineable/pickaxe.json'}|{row['path'] for row in loot_rows}
        with zipfile.ZipFile(args.jar) as artifact:
            for path in sorted(selected):assert artifact.read(path)==(RES/path).read_bytes(),'Built JAR differs from audited source resource: '+path
        report['productionJar']={'path':str(args.jar.resolve()),'sha256':sha(args.jar.read_bytes()),'artAndCatalogueResourceMembersByteExactToCurrentSource':len(selected)}
    target=ROOT/'reports/V9_TYPE_ART_AUDIT.json'
    if target.exists():
        data=target.read_bytes();history=ROOT/'reports/v9-history'/('V9_TYPE_ART_AUDIT-'+sha(data)+'.json');history.parent.mkdir(parents=True,exist_ok=True)
        if history.exists():assert history.read_bytes()==data
        else:history.write_bytes(data)
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':report['status'],'modelFilesByteExact':len(checked),'pngFilesByteExact':len(textures),'originalIdsUnchanged':107,'appendedTypes':10,'reservedCatalogueRows':117}))

if __name__=='__main__':main()
