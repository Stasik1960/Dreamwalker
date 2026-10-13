"""Bind correspondence to actual generated IDs/resources and explicit game evidence.

No build or import is performed. No native verdict is inferred from resource files.
Optional docs/remaining-transfer/object-verification.json may contain per-registry
server/client maps keyed by a variant number or '*' with {status,evidence} records.
"""
import csv, hashlib, json, traceback
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/remaining-transfer'

def read(path):return json.loads(path.read_text(encoding='utf-8'))
def main():
    candidate=read(OUT/'source-candidates.json'); imported=read(OUT/'imported-resources.json');types=read(OUT/'new-types.json')
    rows=read(OUT/'state-correspondence.json')
    assert imported['inputManifestSha256']==hashlib.sha256((OUT/'source-candidates.json').read_bytes()).hexdigest()
    actual={obj['registryId']:obj for obj in imported['objects']}
    numbers={row['registryId']:row['temporaryId'] for row in types['entries']}
    assert len(actual)==len(candidate['objects'])
    resources=[]
    for resource in imported['resources']:
        path=ROOT/'src/architecture/resources'/resource['file']
        assert path.is_file(),str(path)
        assert hashlib.sha256(path.read_bytes()).hexdigest()==resource['sha256'],str(path)
        resources.append(resource['file'])
    native_path=OUT/'object-verification.json'
    native=read(native_path) if native_path.exists() else {}
    artifact=native.get('artifact',{})
    artifact_sha=artifact.get('sha256','')
    if artifact:
        artifact_path=Path(artifact['path'])
        assert artifact_path.is_file(),str(artifact_path)
        assert hashlib.sha256(artifact_path.read_bytes()).hexdigest()==artifact_sha,'Production artifact SHA mismatch'
    phases={'dependency':'artifactDependencyCheck','registration':'registrationCheck','gametest':'gameTestCheck',
            'client_baked':'clientBakedCheck','client_network':'clientNetworkCheck','saved_world':'savedWorldCheck',
            'native_manual':'nativeManualCheck','server':'serverCheck','client':'clientCheck'}
    # Universal proofs cover the complete NEW offered catalogue only. Sampled
    # client/world/manual evidence must name an exact registry and variant.
    universal_phases={'dependency','registration','gametest','client_baked','server','client'}
    planned={'bloodborne_dw:'+obj['path']:obj for obj in candidate['objects']}
    assert set(planned)==set(actual), 'Imported types differ from active candidate catalogue'
    offered={(registry,str(variant['variant'])) for registry,obj in planned.items() for variant in obj['variants']}
    model_target_index={};model_pose_index={};openable_targets=set()
    for registry,obj in planned.items():
        for variant in obj['variants']:
            # Open-only assembly components belong to the same offered variant.
            # sourceModels may list only its closed pose; index both exact poses.
            application_models=[application['model'] for pose in ('closed','open')
                                for application in variant.get(pose,[])]
            target=(registry,str(variant['variant']))
            if 'open' in variant:openable_targets.add(target)
            for model in variant.get('sourceModels',[])+variant.get('sourceAliases',[])+application_models:
                model_target_index.setdefault(model,set()).add(target)
                poses={pose=='open' for pose in ('closed','open')
                       for application in variant.get(pose,[]) if application['model']==model}
                model_pose_index[(model,target)]=poses or {False}
    def model_targets(model):return sorted(model_target_index.get(model,set()))
    def open_only_model(model,registry,variant):return model_pose_index.get((model,(registry,variant)))=={True}
    def evidence(phase,registry,variant,open_only=False):
        assert (registry,variant) in offered,('Evidence target is not a NEW offered state',registry,variant)
        scope=native.get(phase,{})
        entry=scope.get(registry,scope.get('*',{}) if phase in universal_phases else {})
        result=entry.get(variant,entry.get('*',{}) if phase in universal_phases else {})
        # A saved closed owner cannot prove that an OPEN-only component persisted.
        # Completed OPEN save evidence must explicitly state its saved open flag.
        if phase=='saved_world' and open_only and result.get('open') is not True:return 'NOT_RUN',''
        verdict=result.get('status','NOT_RUN')
        assert verdict in ('PASS','FAIL','NOT_RUN'),(phase,registry,variant,verdict)
        if verdict!='NOT_RUN':
            assert artifact_sha,'Artifact binding required for completed evidence'
            assert result.get('artifactSha256')==artifact_sha,('Evidence is not bound to production SHA',phase,registry)
            proof=Path(result['evidence'])
            if not proof.is_absolute():proof=ROOT/proof
            assert proof.is_file(),str(proof)
        return verdict,result.get('evidence','')
    mapped_applications=set()
    for row in rows:
        row['artifactSha256']=artifact_sha
        for key in phases.values():row[key]='NOT_RUN'
        row['evidence']='source_alpha:docs/remaining-transfer/source-alpha-audit.json' if row.get('classification')=='SOURCE_TRANSPARENT_HELPER' else ''
        targets=[]
        for target in row['target'].split(';') if row['target'] else []:
            registry=target.split('[',1)[0]
            if registry not in actual:continue
            variant=target.split('variant=',1)[1].rstrip(']') if '[variant=' in target else '0'
            targets.append((registry,variant))
            mapped_applications.add((registry,variant))
        if not targets:continue
        row['numericId']=';'.join(sorted({numbers[registry] for registry,variant in targets}))
        row['resourceCheck']='PASS'
        row['missing']=''
        if row['sourceModel'] in ('minecraft:block/jungle_trapdoor_bottom','minecraft:block/jungle_trapdoor_open'):
            row['resourceCheck']='PASS_VISIBLE_SOURCE_PARTICLE_DEFECT'
            row['missing']='Original unused particle texture block/texture absent; visible source faces imported. Author replacement not invented.'
        proofs=[]
        for phase,key in phases.items():
            verdicts=[]
            for registry,variant in targets:
                verdict,proof=evidence(phase,registry,variant,open_only_model(row['sourceModel'],registry,variant))
                verdicts.append(verdict)
                if proof:proofs.append(phase+':'+proof)
            row[key]='FAIL' if 'FAIL' in verdicts else 'PASS' if verdicts and all(status=='PASS' for status in verdicts) else 'NOT_RUN'
        row['evidence']=';'.join(sorted(set(proofs)))
    (OUT/'state-correspondence.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    with (OUT/'state-correspondence.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    fields=['sourceModel','sourceStates','registryId','numericId','offeredTargets','offeredOpenStates','kind','missing','status','resourceCheck','artifactSha256',*phases.values(),'evidence']
    model_results=[];model_only_rows=[];mapped_offered=set(mapped_applications)
    with (OUT/'model-correspondence.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader()
        for classification in candidate['modelClassifications']:
            value={key:classification.get(key,'') for key in fields}
            value['sourceStates']=';'.join(classification.get('sourceStates',[]))
            registries=sorted({'bloodborne_dw:'+path for path in classification.get('wholeObjectPaths',[])} or {classification.get('registryId','')})
            imported_ids=[registry for registry in registries if registry in actual]
            targets=model_targets(classification['sourceModel']) if imported_ids else []
            mapped_offered.update(targets)
            value['offeredTargets']=';'.join(registry+'[variant='+variant+']' for registry,variant in targets)
            value['offeredOpenStates']=';'.join(registry+'[variant='+variant+',open='+str(open_flag).lower()+']'
                                              for registry,variant in targets if (registry,variant) in openable_targets
                                              for open_flag in sorted(model_pose_index[(classification['sourceModel'],(registry,variant))]))
            value['registryId']=';'.join(registries)
            value['resourceCheck']=value['serverCheck']=value['clientCheck']='NOT_RUN'
            value['artifactSha256']=artifact_sha
            for key in phases.values():value[key]='NOT_RUN'
            value['evidence']=''
            if imported_ids:
                value['numericId']=';'.join(sorted(numbers[registry] for registry in imported_ids))
                value['status']='TRANSFERRED_RESOURCES'
                value['resourceCheck']='PASS'
                value['missing']=''
                if classification['sourceModel'] in ('minecraft:block/jungle_trapdoor_bottom','minecraft:block/jungle_trapdoor_open'):
                    value['status']='TRANSFERRED_VISIBLE_SOURCE_PARTICLE_DEFECT'
                    value['resourceCheck']='PASS_VISIBLE_SOURCE_PARTICLE_DEFECT'
                    value['missing']='Original unused particle texture block/texture absent; visible source faces imported. Author replacement not invented.'
                linked=[row for row in rows if row['sourceModel']==classification['sourceModel']]
                linked=[row for row in linked if any(target.split('[',1)[0] in actual for target in row['target'].split(';'))]
                proofs={row['evidence'] for row in linked if row.get('evidence')}
                if not linked:
                    assert targets,('Transferred model has no exact active offered target',classification['sourceModel'],imported_ids)
                    model_only_rows.append({'model':classification['sourceModel'],'targets':[registry+'[variant='+variant+']' for registry,variant in targets]})
                for phase,key in phases.items():
                    verdicts=[]
                    for registry,variant in targets:
                        verdict,proof=evidence(phase,registry,variant,open_only_model(classification['sourceModel'],registry,variant))
                        verdicts.append(verdict)
                        if proof:proofs.add(phase+':'+proof)
                    value[key]='FAIL' if 'FAIL' in verdicts else 'PASS' if verdicts and all(status=='PASS' for status in verdicts) else 'NOT_RUN'
                value['evidence']=';'.join(sorted(proofs))
            writer.writerow(value)
            model_results.append(value)
    assert not offered-mapped_offered,('Offered states missing a source model mapping',sorted(offered-mapped_offered))
    report={'status':'PASS','rows':len(rows),'objects':len(actual),'resourceFilesHashVerified':len(resources),
            'manifestSha256':imported['inputManifestSha256'],'productionArtifactSha256':artifact_sha or None,
            'nativeEvidenceFile':str(native_path) if native_path.exists() else None,
            'phaseCounts':{key:dict(Counter(row[key] for row in rows)) for key in phases.values()},
            'modelPhaseCounts':{key:dict(Counter(row[key] for row in model_results)) for key in phases.values()},
            'modelOnlyEvidenceRows':model_only_rows,'unmappedEligibleOfferedStates':len(offered-mapped_offered),
            'offeredStatesWithoutSourceApplications':len(offered-mapped_applications),
            'modelVerdictScope':'Every exact active offeredTargets variant; PASS requires all. offeredOpenStates records meaningful assembly pose applicability. Physical/network/save require explicit per-variant evidence; OPEN-only model save requires explicit open:true.'}
    (OUT/'correspondence-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')

if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'correspondence-verification.json').write_text(json.dumps({'status':'FAIL','traceback':traceback.format_exc()},indent=2),encoding='utf-8');raise
