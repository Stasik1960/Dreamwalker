"""Append current revision status to the immutable transfer correspondence."""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def checked_report(path, digest):
    report=json.loads(path.read_text(encoding='utf-8'))
    values=[report[key] for key in ('productionJarSha256','jarSha256','artifact_sha256') if key in report]
    assert report['status']=='PASS' and values and all(value==digest for value in values), 'Evidence does not match exact final JAR: '+str(path)
    return report


def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True);p.add_argument('--physics-report',type=Path,required=True);p.add_argument('--resources-report',type=Path,required=True);p.add_argument('--preservation-report',type=Path,required=True);a=p.parse_args()
    digest=hashlib.sha256(a.jar.read_bytes()).hexdigest()
    physics=checked_report(a.physics_report,digest);resources=checked_report(a.resources_report,digest);preservation=checked_report(a.preservation_report,digest)
    assert physics['newRegistryCount']==resources['objects']==1045 and physics['offeredVariantCount']==resources['variants']==1802 and physics['exactGeometryStates']==57664
    assert preservation['checks']['protected_v12_rows_exact'] and preservation['checks']['v12_specs_rp_and_vanilla_assets_exact'] and preservation['checks']['all_surviving_visual_assets_exact']
    with zipfile.ZipFile(a.jar) as archive:
        current={}
        for name in ('retired-source-objects.json','remaining-catalogue.json','debug_catalogue.json'):
            blob=archive.read('bloodborne_dw/'+name)
            assert blob==(ROOT/'src/architecture/resources/bloodborne_dw'/name).read_bytes(), 'Source catalogue differs from exact JAR: '+name
            current[name]=json.loads(blob)
    retired=current['retired-source-objects.json']
    ids={r['registryId'] for r in retired['objects']}
    active=current['remaining-catalogue.json']
    available={'bloodborne_dw:'+r['path'] for r in active['objects']}
    numbers={r['registryId']:r['temporaryId'] for r in current['debug_catalogue.json']['entries']}
    reserved={r['registryId']:r['reservedNumber'] for r in retired['objects']}
    with (ROOT/'docs/remaining-transfer/model-correspondence.csv').open(encoding='utf-8-sig',newline='') as stream:
        reader=csv.DictReader(stream);fields=reader.fieldnames;rows=list(reader)
    covered={target.strip() for row in rows for target in row['registryId'].split(';') if target.strip()}
    assert available<=covered, 'Current registry types absent from correspondence: '+str(sorted(available-covered))
    offered={target.strip() for row in rows for target in row['offeredTargets'].split(';') if target.strip()}
    expected={f'bloodborne_dw:{obj["path"]}[variant={variant["variant"]}]' for obj in active['objects'] for variant in obj['variants']}
    assert expected<=offered, 'Current offered variants absent from correspondence: '+str(sorted(expected-offered))
    evidence=';'.join(path.resolve().relative_to(ROOT.resolve()).as_posix() for path in (a.physics_report,a.resources_report,a.preservation_report))
    policy=json.loads((ROOT/'docs/collision-simplification/policy-report.json').read_text(encoding='utf-8'))
    assert {case['id'] for case in policy['cases']}==available, 'Static physical policy is not the complete active registry'
    current_fields=['v122Status','v122PhysicalCheck','v122ResourceCheck','v122Targets','v122NativeEveryVariant','v122ArtifactSha256','v122Evidence']
    for row in rows:
        targets=[]
        for target in dict.fromkeys(value.strip() for value in row['registryId'].split(';') if value.strip()):
            if target in ids:
                status='REMOVED_BY_USER_DAMAGED_NUMBER_RESERVED';physical=resource='NOT_APPLICABLE_RETIRED'
            elif target in available:
                status='KEPT_ART_SIMPLIFIED_PHYSICS';physical=resource='PASS'
            else:
                assert target in numbers and not target.startswith('bloodborne_dw:source_'), 'Unclassified registry target: '+target
                status='PRESERVED_PREVIOUS_OBJECT';physical='NOT_APPLICABLE_V12';resource='PASS_PRESERVATION'
            targets.append(dict(registryId=target,numericId=reserved.get(target,numbers.get(target)),status=status,physicalCheck=physical,resourceCheck=resource))
        if not targets:
            status='SOURCE_DEFECT_OR_EXCLUSION_UNCHANGED';physical=resource='NOT_APPLICABLE_NO_REGISTERED_OBJECT'
        else:
            status=';'.join(dict.fromkeys(target['status'] for target in targets));physical=';'.join(dict.fromkeys(target['physicalCheck'] for target in targets));resource=';'.join(dict.fromkeys(target['resourceCheck'] for target in targets))
        row.update(v122Status=status,v122PhysicalCheck=physical,v122ResourceCheck=resource,v122Targets=json.dumps(targets,ensure_ascii=False,separators=(',',':')),
            v122NativeEveryVariant='NOT_RUN_EXHAUSTIVE_MOUSE_KEYBOARD',v122ArtifactSha256=digest,
            v122Evidence=evidence)
    dest=ROOT/'docs/v12.2-simplified-collisions';dest.mkdir(parents=True,exist_ok=True)
    with (dest/'correspondence-v122.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=fields+current_fields);writer.writeheader();writer.writerows(rows)
    with (dest/'collision-policy.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        fields=['registryId','numericId','variant','pose','policy','beforeBoxes','afterBoxes','yaw0Boxes','yaw45Boxes','reason','authorExport','v122ArtifactSha256']
        writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader()
        for case in policy['cases']:
            writer.writerow(dict(registryId=case['id'],numericId=numbers[case['id']],variant=case['variant'],pose=case['pose'],policy=case['kind'],beforeBoxes=case['beforeBoxes'],afterBoxes=case['afterBoxes'],yaw0Boxes=json.dumps(case['rotation0TrimmedBoxes'],separators=(',',':')),yaw45Boxes=json.dumps(case['rotation45TrimmedBoxes'],separators=(',',':')),reason=case['evidence'],authorExport='NOT_SUPPLIED_NOT_APPLIED',v122ArtifactSha256=digest))
    print(json.dumps({'status':'PASS','sourceModelRows':len(rows),'activeRegistryTypesCovered':len(available),'offeredVariantsCovered':len(expected),'currentPhysicsPoses':len(policy['cases']),'productionJarSha256':digest}))


if __name__=='__main__':main()
