"""Read-only final combined-JAR audit against the released V12.3 and frozen source.

Run only after the parent freezes the final JAR. Never modifies source or JARs.
The sole output is the requested JSON evidence report.
"""
from __future__ import annotations
from collections import Counter
from pathlib import Path
import argparse,copy,hashlib,importlib.util,io,json,subprocess,traceback,zipfile

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/v12.4-dedup'
BASELINE_JAR=ROOT.parent/'releases/Dreamwalker-BB/v12.3-outline-icons/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.3-outline-icons.jar'
EXPECTED_BASELINE_SHA='34efa3a0f8a1506c97a754fb5c887db977ca489b3cc514c84748de07e3688054'
VANILLA=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
RESOURCE_ROOTS=['src/architecture/resources/','src/rp/resources/']
NEW_CLASSES={'ImportedTypeAliases','ImportedTypeMigration','ImportedArtworkBlock',
             'ImportedArtworkStates','ImportedStairBlock','ImportedStairShapes','ImportedLadderBlock'}
NEW_METADATA={'bloodborne_dw/imported-type-aliases.json','bloodborne_dw/imported-stair-types.json'}
VARIANT_SHAPES={'straight','inner_left','inner_right','outer_left','outer_right','original_trim'}

def sha(data):return hashlib.sha256(data).hexdigest()
def read_json(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def canonical(ref):return ref if ':' in ref else 'minecraft:'+ref
def model_path(ref):
    namespace,local=canonical(ref).split(':',1)
    return f'assets/{namespace}/models/{local}.json'
def texture_path(ref):
    namespace,local=canonical(ref).split(':',1)
    return f'assets/{namespace}/textures/{local}.png'
def logical_variant(mapping,kind):
    v=int(mapping['newVariant']);props=mapping.get('properties',{})
    if kind=='artwork':return 16*int(props.get('art_bank',0))+v
    if kind=='stairs':return v+(10 if str(props.get('trim','false')).lower()=='true' else 0)
    return v
def references(value,keys=('model','altModel')):
    result=[]
    if isinstance(value,dict):
        result.extend(value[k] for k in keys if k in value and isinstance(value[k],str))
        for child in value.values():result.extend(references(child,keys))
    elif isinstance(value,list):
        for child in value:result.extend(references(child,keys))
    return result

def audit(args):
    failures=[];notes=[];counts=Counter()
    fail=lambda code,detail:failures.append({'code':code,'detail':detail})
    final_path=args.jar.resolve();before_path=args.baseline_jar.resolve()
    assert final_path.is_file() and before_path.is_file()
    assert final_path!=before_path
    final_bytes=final_path.read_bytes();before_bytes=before_path.read_bytes()
    production_sha=sha(final_bytes);baseline_sha=sha(before_bytes)
    if baseline_sha!=EXPECTED_BASELINE_SHA:fail('BASELINE_RELEASE_SHA',baseline_sha)
    with zipfile.ZipFile(io.BytesIO(final_bytes)) as finalzip,zipfile.ZipFile(io.BytesIO(before_bytes)) as oldzip,zipfile.ZipFile(VANILLA) as vanilla:
        final={p:finalzip.read(p) for p in finalzip.namelist() if not p.endswith('/')}
        old={p:oldzip.read(p) for p in oldzip.namelist() if not p.endswith('/')}
        for label,z in [('final',finalzip),('baseline',oldzip)]:
            duplicate=[p for p,n in Counter(z.namelist()).items() if n>1]
            if duplicate:fail('DUPLICATE_ZIP_ENTRIES',{'jar':label,'entries':duplicate})
            if z.testzip():fail('ZIP_CRC',label)
        vanilla_names=set(vanilla.namelist())
        def jar_json(p):
            if p not in final:raise ValueError('Missing production resource '+p)
            return json.loads(final[p])
        base=read_json(DOC/'baseline-content.json')
        manifest=jar_json('bloodborne_dw/imported-type-aliases.json')
        expected_manifest=read_json(DOC/'type-state-mapping.json')
        if manifest!=expected_manifest:fail('MANIFEST_DIFFERS_REVIEWED_PLAN','Production JAR differs from final review manifest')
        if manifest['baselineCommit']!=base['baselineCommit']:fail('BASELINE_COMMIT',manifest['baselineCommit'])
        groups={g['primaryRegistryId']:g for g in manifest['groups']}
        mapped={(m['oldRegistryId'],m['oldVariant']):m for m in manifest['mappings']}
        if len(mapped)!=len(manifest['mappings']):fail('DUPLICATE_STATE_MAPPING',len(mapped))
        for m in manifest['mappings']:
            if not m['oldRegistryId'].startswith('bloodborne_dw:source_') or not m['newRegistryId'].startswith('bloodborne_dw:source_'):
                fail('PROTECTED_V12_MAPPING',m)
            if m['oldRegistryId'].split(':')[1] not in base['descriptors']:fail('UNKNOWN_OLD_MAPPING_ID',m)
        artwork_plan={g['mainRegistryId']:g for g in read_json(DOC/'general-functional-groups.json')['groups']}
        allowed_descriptor_paths={f"bloodborne_dw/composite/{registry.split(':')[1]}.json" for registry in groups}
        changed_old=[]
        for p,data in old.items():
            if p not in final:fail('REMOVED_BASELINE_ENTRY',p);continue
            if final[p]==data:
                if '/models/' in p:counts['unchangedBaselineModels']+=1
                if '/textures/' in p:counts['unchangedBaselineTextures']+=1
                if p.startswith('assets/minecraft/'):counts['unchangedVanillaNamespaceAssets']+=1
                continue
            changed_old.append(p)
            if p.startswith('assets/'):
                # No previously released artwork/texture/blockstate changes are
                # authorized, even for an alias removed from Creative.
                fail('CHANGED_BASELINE_ASSET',p)
        source_checker=importlib.util.spec_from_file_location('v124_protection_allowlist',ROOT/'tools/check_v124_preservation.py')
        checker=importlib.util.module_from_spec(source_checker);source_checker.loader.exec_module(checker)
        allowed_source=checker.ALLOW
        allowed_class_prefixes=set()
        for p in allowed_source:
            if '/java/' in p and p.endswith('.java'):allowed_class_prefixes.add(p.split('/java/',1)[1][:-5])
        for p in changed_old:
            if p.startswith('assets/'):continue
            if p in allowed_descriptor_paths or p in {'bloodborne_dw/remaining-catalogue.json','bloodborne_dw/debug_catalogue.json'}:continue
            if p.endswith('.class'):
                stem=p[:-6].split('$',1)[0]
                if stem not in allowed_class_prefixes:fail('UNDOCUMENTED_CHANGED_BASELINE_CLASS',p)
                else:counts['documentedSharedHookClasses']+=1
                continue
            if p=='fabric.mod.json':
                before=json.loads(old[p]);after=json.loads(final[p]);before.pop('version',None);after.pop('version',None)
                if before!=after:fail('UNEXPECTED_MOD_METADATA_CHANGE',p)
            elif p=='META-INF/MANIFEST.MF':
                def nonversion(data):return [line for line in data.decode().splitlines() if not line.startswith(('Implementation-Version:','Specification-Version:'))]
                if nonversion(old[p])!=nonversion(final[p]):fail('UNEXPECTED_JAR_MANIFEST_CHANGE',p)
            else:fail('UNDOCUMENTED_CHANGED_BASELINE_RESOURCE',p)
        for p in sorted(set(final)-set(old)):
            if p in NEW_METADATA:counts['newApprovedMetadata']+=1;continue
            if p.endswith('.class'):
                stem=p[:-6].split('$',1)[0]
                if stem.startswith('dev/dreamwalker/bloodbornedw/composite/') and stem.rsplit('/',1)[1] in NEW_CLASSES:
                    counts['newApprovedProductionClasses']+=1;continue
                fail('UNDOCUMENTED_NEW_CLASS',p);continue
            if p.startswith('assets/bloodborne_dw/models/block/imported_stairs/') and p.endswith('.json'):
                components=p.split('/');i,shape=components[-2],components[-1][:-5]
                approved=any(g['primaryId']==i and g['kind']=='stairs' for g in groups.values())
                if approved and shape in VARIANT_SHAPES:counts['newApprovedStairModels']+=1;continue
            fail('UNDOCUMENTED_ADDED_RESOURCE',p)
        qa_markers=('v124qa/','v123qa/','gametest/','gallery/','v124-qa-input','v124-baseline-content','v124-visual-comparison')
        leaked=[p for p in final if any(marker in p for marker in qa_markers)]
        if leaked:fail('QA_ARTIFACTS_IN_PRODUCTION',leaked)
        # All source descriptors remain registered. General artwork unions copy
        # approved source variants exactly, including physics/selection data.
        for path,original in base['descriptors'].items():
            resource='bloodborne_dw/composite/'+path+'.json';current=jar_json(resource)
            registry='bloodborne_dw:'+path;group=groups.get(registry)
            if not group:
                if current!=original:fail('NONPRIMARY_DESCRIPTOR_CHANGED',path)
                counts['unchangedNonprimaryDescriptors']+=1;continue
            if group['kind']=='artwork':
                expected=copy.deepcopy(original);expected['variants']=[]
                plan=artwork_plan.get(registry)
                if not plan:fail('MISSING_APPROVED_ARTWORK_PLAN',registry);continue
                for rep in plan['representatives']:
                    expected['variants'].append(copy.deepcopy(base['descriptors'][rep['registryId'].split(':')[1]]['variants'][rep['oldVariant']]))
                if current!=expected:fail('PRIMARY_VARIANT_UNION_DIFFERS_APPROVED',registry)
                counts['approvedArtworkUnions']+=1
            elif group['kind']=='stairs':
                expected_count=20 if group['primaryId']=='93028' else 10
                if len(current['variants'])!=expected_count:fail('STAIR_POSE_COUNT',registry)
                if not current.get('functionalStairStates'):fail('STAIR_STATE_MARKER',registry)
                for index,v in enumerate(current['variants']):
                    pose=v['poses']['closed'];boxes=pose.get('collision',[])
                    if not 2<=len(boxes)<=3:fail('STAIR_CUBOID_BUDGET',{'id':registry,'pose':index,'boxes':len(boxes)})
                    if pose.get('selection')!=boxes:fail('STAIR_SELECTION_DIFFERS_PHYSICS',{'id':registry,'pose':index})
                    if not pose.get('simpleCollisionByRotation') or len(pose['simpleCollisionByRotation'])!=8:fail('STAIR_ROTATED_PHYSICS',{'id':registry,'pose':index})
                counts['approvedNativeStairDescriptors']+=1
            elif group['kind']=='ladder':
                if len(current['variants'])!=2:fail('LADDER_HEIGHT_STATES',registry)
                counts['approvedLadderDescriptors']+=1
        catalogue=jar_json('bloodborne_dw/debug_catalogue.json');remaining=jar_json('bloodborne_dw/remaining-catalogue.json')
        entries={r['registryId']:r for r in catalogue['entries']}
        for row in base['originalRows']:
            if entries.get(row['registryId'])!=row:fail('ORIGINAL_V12_CATALOGUE_ROW_CHANGED',row['registryId'])
        original_numbers=[r['temporaryId'] for r in base['debugCatalogue']['entries']]
        final_numbers=[r['temporaryId'] for r in catalogue['entries']]
        if len(final_numbers)!=1322 or len(set(final_numbers))!=1322 or final_numbers!=original_numbers:
            fail('FIVE_DIGIT_RESERVATIONS_CHANGED',{'before':len(original_numbers),'after':len(final_numbers)})
        if {r['path'] for r in remaining['objects']}!=set(base['descriptors']):fail('REMOVED_REGISTERED_DESCRIPTOR','Existing old IDs must remain as hidden adapters')
        for g in groups.values():
            for member in g['members']:
                if member!=g['primaryRegistryId'] and entries[member].get('canonicalRegistryId')!=g['primaryRegistryId']:
                    fail('ALIAS_NOT_HIDDEN',member)
        counts['reservedFiveDigitNumbers']=len(final_numbers);counts['originalV12CatalogueRows']=len(base['originalRows'])
        # Verify closure of actual in-game roots. Parameterized template models
        # may intentionally contain unresolved #face inputs until a child uses
        # them; a concrete installed/item model may not.
        model_cache={};visited=set();reachable=set();texture_dependencies=set();templates=[]
        def bytes_for(p):
            if p in final:return final[p]
            if p in vanilla_names:return vanilla.read(p)
            raise ValueError('Missing dependency '+p)
        def resolved(ref,chain=()):
            ref=canonical(ref)
            if ref in chain:raise ValueError('Cyclic model parent '+str(chain+(ref,)))
            if ref in model_cache:return model_cache[ref]
            p=model_path(ref);raw=json.loads(bytes_for(p));parent=raw.get('parent','');value={}
            if parent and not parent.startswith(('builtin/','minecraft:builtin/')):
                value=copy.deepcopy(resolved(parent,chain+(ref,)))
            textures={**value.get('textures',{}),**raw.get('textures',{})}
            value.update(raw);value['textures']=textures;model_cache[ref]=value;visited.add(p)
            for override in raw.get('overrides',[]):
                if 'model' in override:concrete(override['model'])
            return value
        def texture(ref,bindings):
            seen=set()
            while ref.startswith('#'):
                if ref in seen:raise ValueError('Cyclic texture alias '+str(seen))
                seen.add(ref);key=ref[1:]
                if key not in bindings:raise ValueError('Unbound texture alias '+ref)
                ref=bindings[key]
            p=texture_path(ref);bytes_for(p);texture_dependencies.add(p)
        def concrete(ref):
            ref=canonical(ref)
            if ref in reachable:return
            reachable.add(ref)
            try:
                model=resolved(ref);bindings=model.get('textures',{})
                for element in model.get('elements',[]):
                    for face in element.get('faces',{}).values():texture(face['texture'],bindings)
                for key,value in bindings.items():texture(value,bindings)
            except Exception as e:fail('MODEL_DEPENDENCY_CLOSURE',{'model':ref,'reason':str(e)})
        roots=[]
        for p in final:
            if p.startswith('assets/') and '/models/item/' in p and p.endswith('.json'):
                _,namespace,_,*parts=p.split('/');roots.append(namespace+':'+ '/'.join(parts)[:-5])
            elif p.startswith('assets/') and '/blockstates/' in p and p.endswith('.json'):
                roots.extend(references(jar_json(p),keys=('model',)))
            elif p.startswith('bloodborne_dw/composite/') and p.endswith('.json'):
                for variant in jar_json(p)['variants']:
                    for pose in variant['poses'].values():
                        roots.extend(references(pose.get('parts',[])))
        for ref in sorted(set(roots)):concrete(ref)
        # Check unused source templates' parent and literal texture dependency
        # closure too, recording parameter inputs separately from finished roots.
        for p in final:
            if not p.startswith('assets/') or '/models/' not in p or not p.endswith('.json'):continue
            _,namespace,_,*parts=p.split('/');ref=namespace+':'+ '/'.join(parts)[:-5]
            if ref in reachable:continue
            try:
                model=resolved(ref);bindings=model.get('textures',{})
                for value in bindings.values():
                    if not value.startswith('#'):texture(value,bindings)
                for e in model.get('elements',[]):
                    for face in e.get('faces',{}).values():
                        try:texture(face['texture'],bindings)
                        except ValueError as exc:
                            if 'Unbound texture alias' in str(exc):templates.append({'model':ref,'parameter':face['texture']})
                            else:raise
            except Exception as e:fail('UNUSED_MODEL_PARENT_CLOSURE',{'model':ref,'reason':str(e)})
        counts['concreteModelRoots']=len(reachable);counts['resolvedModelDependencies']=len(visited)
        counts['resolvedTextureDependencies']=len(texture_dependencies);counts['unusedParameterizedTemplateFaces']=len(templates)
        frozen_hashes=read_json(DOC/'baseline-protected-hashes.json')['files'];source_hooks=[]
        for p,digest in frozen_hashes.items():
            current=ROOT/p
            if not current.is_file():fail('REMOVED_PROTECTED_SOURCE',p)
            elif sha(current.read_bytes())!=digest:
                if p in allowed_source:source_hooks.append(p)
                else:fail('UNDOCUMENTED_SOURCE_CHANGE',p)
            else:counts['unchangedProtectedSourceFiles']+=1
        contract='docs/COLLISION_IMPORT_HANDOFF.md'
        baseline_contract=subprocess.check_output(['git','show',base['baselineCommit']+':bloodborne complete/'+contract],cwd=ROOT.parent)
        if sha((ROOT/contract).read_bytes())!=sha(baseline_contract):fail('AUTHOR_COLLISION_CONTRACT_CHANGED',contract)
        # Every production resource is checked against the frozen working source,
        # ensuring the artifact contains the reviewed current descriptors/assets.
        for prefix in RESOURCE_ROOTS:
            for file in (ROOT/prefix).rglob('*'):
                if not file.is_file():continue
                p=file.relative_to(ROOT/prefix).as_posix()
                if p=='fabric.mod.json':continue
                if p not in final:fail('CURRENT_SOURCE_RESOURCE_NOT_PACKAGED',prefix+p)
                elif final[p]!=file.read_bytes():fail('JAR_DIFFERS_CURRENT_SOURCE_RESOURCE',prefix+p)
        result={'schema':'dreamwalker-v124-final-resource-audit-v1','status':'FAIL' if failures else 'PASS',
          'productionJar':str(final_path),'productionJarSha256':production_sha,
          'baselineJar':str(before_path),'baselineJarSha256':baseline_sha,'baselineCommit':base['baselineCommit'],
          'counts':dict(counts),'approvedChangedEntries':changed_old,'documentedNewOnlySharedHooks':source_hooks,
          'collisionContractSha256':sha(baseline_contract),'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED',
          'qaClassesExcluded':not leaked,'failures':failures,'notes':notes,
          'nativeGameplay':'NOT_RUN_BY_RESOURCE_AUDIT'}
        if sha(final_path.read_bytes())!=production_sha or sha(before_path.read_bytes())!=baseline_sha:
            result['status']='FAIL';result['failures'].append({'code':'INPUT_MUTATED_DURING_READONLY_AUDIT','detail':'JAR hash changed while checking'})
        return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--baseline-jar',type=Path,default=BASELINE_JAR)
    parser.add_argument('--report',type=Path,default=DOC/'resource-audit.json')
    args=parser.parse_args();args.report.parent.mkdir(parents=True,exist_ok=True)
    try:result=audit(args)
    except Exception:
        result={'status':'FAIL','productionJar':str(args.jar),'failure':traceback.format_exc()}
    args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if result['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
