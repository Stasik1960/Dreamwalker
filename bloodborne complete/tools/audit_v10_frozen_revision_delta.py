"""Exact frozen JAR/source correspondence; no Minecraft or world access."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def sha(data):return hashlib.sha256(data).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--baseline-manifest',type=Path,required=True)
    p.add_argument('--current-manifest',type=Path,required=True)
    p.add_argument('--report',type=Path,required=True)
    p.add_argument('--allowed-class',action='append',default=[],help='Exact reviewed class path; same class-family $ inners only. Replaces legacy async allowlist.')
    p.add_argument('--allowed-metadata-resource',action='append',default=[],help='Exact reviewed non-art metadata path, never assets/catalogue/descriptors.')
    p.add_argument('--revision-description',default='executable snapshot behavior intentionally changed')
    a=p.parse_args();assert not a.report.exists(),'Preserve prior revision evidence'
    old=json.loads(a.baseline_manifest.read_bytes());current=json.loads(a.current_manifest.read_bytes())
    jars=[Path(row['artifact']) for row in [old,current]]
    for path,row in zip(jars,[old,current]):assert path.stat().st_size==row['bytes'] and sha(path.read_bytes())==row['sha256']
    with zipfile.ZipFile(jars[0]) as first,zipfile.ZipFile(jars[1]) as second:
        assert first.testzip() is None and second.testzip() is None
        names=[set(z.namelist()) for z in [first,second]]
        resources=[{n for n in rows if not n.endswith('.class') and not n.endswith('/')} for rows in names]
        metadata=set(a.allowed_metadata_resource)
        assert all(not n.startswith(('assets/','bloodborne_dw/','bloodborne_rp/')) and not n.endswith(('.png','.geo.json','.animation.json')) for n in metadata),'Art/catalogue/descriptor exception is forbidden'
        assert resources[0]^resources[1] <= metadata,'Unexpected resource member roster changed'
        protected=(resources[0]|resources[1])-metadata
        assert all(n in resources[0]&resources[1] and first.read(n)==second.read(n) for n in protected),'Protected non-class JAR resource changed'
        rows=[]
        for name in sorted(names[0]|names[1]):
            before=first.read(name) if name in names[0] else None;after=second.read(name) if name in names[1] else None
            if before!=after:rows.append({'path':name,'baselineSha256':sha(before) if before is not None else None,'currentSha256':sha(after) if after is not None else None,'baselineBytes':len(before) if before is not None else None,'currentBytes':len(after) if after is not None else None})
        classes=[row for row in rows if row['path'].endswith('.class')]
        changed_metadata=[row for row in rows if not row['path'].endswith('.class')]
        if a.allowed_class:
            assert all(n.endswith('.class') and n.startswith('dev/') for n in a.allowed_class),'Exact project class paths required'
            stems=[n.removesuffix('.class') for n in a.allowed_class]
            assert all(any(row['path']==stem+'.class' or row['path'].startswith(stem+'$') for stem in stems) for row in classes),'Unexpected executable class outside reviewed exact family allowlist'
        else:
            allowed={'CompositeShapeSnapshots','CompositeRuntime','CompositeLedger','CompositeBlockEntity','CompositeArchitecture','CompositeShapeMixin','VerticalMount','SourceLadderBlockEntity','SourceBackingBlock','PrototypeWallBlock'}
            assert all(Path(row['path']).name.split('$')[0].removesuffix('.class') in allowed for row in classes),'Unexpected executable class delta outside async shape fix'
        assert all(row['path'] in metadata for row in changed_metadata),'Unexpected non-art metadata delta'
        art={n for n in resources[1] if n.startswith('assets/')}
        resource_rows=[{'path':n,'sha256':sha(second.read(n)),'bytes':len(second.read(n))} for n in sorted(protected)]
    frozen_source=[]
    for row in current['mainSourceInputs']:
        path=ROOT/row['path'];raw=path.read_bytes();assert len(raw)==row['bytes'] and sha(raw)==row['sha256'],'Source changed after freeze: '+row['path']
        frozen_source.append(row)
    report={'schema':'dw-v10-frozen-reviewed-revision-resource-delta-v1' if a.allowed_class or metadata else 'dw-v10-frozen-async-revision-resource-delta-v1',
        'status':'PASS_EXACT_ART_AND_PROTECTED_RESOURCES_ONLY_EXPLICIT_EXECUTABLE_METADATA_DELTA' if a.allowed_class or metadata else 'PASS_EXACT_JAR_RESOURCES_UNCHANGED_ONLY_DECLARED_ASYNC_CLASSES_DIFFER',
        'baseline':{'jar':str(jars[0]),'sha256':old['sha256'],'manifest':str(a.baseline_manifest),'manifestSha256':sha(a.baseline_manifest.read_bytes())},
        'current':{'jar':str(jars[1]),'sha256':current['sha256'],'manifest':str(a.current_manifest),'manifestSha256':sha(a.current_manifest.read_bytes())},
        'counts':{'allNonClassResourceMembers':len(resources[1]),'protectedUnchangedResourceMembers':len(protected),'allAssets':len(art),'png':sum(n.endswith('.png') for n in art),'geo':sum(n.endswith('.geo.json') for n in art),'animations':sum(n.endswith('.animation.json') for n in art),'changedClassesOrAddedClasses':len(classes),'changedMetadataMembers':len(changed_metadata),'unchangedClassMembers':len([n for n in names[0]&names[1] if n.endswith('.class')])-sum(row['baselineSha256'] is not None for row in classes),'verifiedCurrentFrozenSourceFiles':len(frozen_source)},
        'changedExecutableMembers':classes,'changedMetadataMembers':changed_metadata,'unchangedResources':resource_rows,
        'reviewedDeltaAllowlist':{'classFamilies':a.allowed_class or 'legacy async class allowlist','metadataResources':sorted(metadata)},
        'revisionDescription':a.revision_description,
        'limits':['Exact art/geometry/UV/PNG/animations/catalogue/descriptors retained; '+a.revision_description+'.','Current native proof is bound separately; previous ordinary client/server passes are not current revision runtime proof.','No world, saved UUID, source input or historical archive was read/written by this audit.']}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':report['status'],'counts':report['counts'],'report':str(a.report)},ensure_ascii=False))


if __name__=='__main__':main()
