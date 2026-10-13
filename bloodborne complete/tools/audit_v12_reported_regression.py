"""Independent read-only comparison of published V12 with delivered V12.2."""
import hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
OUT=ROOT/'docs/v12-reported-regression';OUT.mkdir(parents=True,exist_ok=True)
OLD=REPO/'releases/Dreamwalker-BB/v12/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.jar'
NEW=REPO/'releases/Dreamwalker-BB/v12.2-simple-collisions/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.2-simple-collisions.jar'
sha=lambda b:hashlib.sha256(b).hexdigest()
with zipfile.ZipFile(OLD) as old,zipfile.ZipFile(NEW) as new:
    old_names={n for n in old.namelist() if not n.endswith('/')};new_names=set(new.namelist())
    changed=[];missing=[]
    for n in sorted(old_names):
        if n not in new_names:missing.append(n)
        elif old.read(n)!=new.read(n):changed.append({'entry':n,'oldSha256':sha(old.read(n)),'newSha256':sha(new.read(n))})
    c0=json.loads(old.read('bloodborne_dw/debug_catalogue.json'));c1=json.loads(new.read('bloodborne_dw/debug_catalogue.json'))
    old_rows=c0['entries'];r0={r['temporaryId']:r for r in old_rows}
    protected_rows_exact=c1['entries'][:len(old_rows)]==old_rows and c0.get('stateAliases')==c1.get('stateAliases')
    protected_resources=[n for n in old_names if n.startswith(('assets/','bloodborne_dw/composite/'))]
    resource_deltas=[r for r in changed if r['entry'] in protected_resources]
    old_native={k:r0[k] for k in ('90002','90011','90012')}
    textures={f'{i}':sha(old.read(f'assets/bloodborne_dw/textures/wall/source/minecraft/block/polished_deepslate_wall_{i}.png')) for i in (0,6)}
    wall_classes=[n for n in old_names if n.startswith('dev/dreamwalker/bloodbornedw/architecture/wall/') or n.endswith('/DiagonalPostFenceMixin.class') or n.endswith('/CatalogueMigration.class')]
    wall_equal=all(n in new_names and old.read(n)==new.read(n) for n in wall_classes)
    rp=[n for n in old_names if n.startswith('dev/dreamwalker/bloodbornerp/')]
    rp_equal=all(n in new_names and old.read(n)==new.read(n) for n in rp)
    allowed={'fabric.mod.json','bloodborne_dw/debug_catalogue.json'}
    changed_classes=[r['entry'] for r in changed if r['entry'].endswith('.class')]
    other_changes=[r['entry'] for r in changed if r['entry'] not in allowed and not r['entry'].endswith('.class')]
baseline=json.loads((ROOT/'docs/remaining-transfer/baseline.json').read_text(encoding='utf8'))
allowed_sources={
 'src/architecture/java/dev/dreamwalker/bloodbornedw/DreamwalkerBb.java',
 'src/architecture/java/dev/dreamwalker/bloodbornedw/debug/UnifiedCreativeCatalogue.java',
 'src/architecture/java/dev/dreamwalker/bloodbornedw/link/CompositeMechanismBridge.java',
 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/RpClimbingMixin.java',
 'src/architecture/resources/bloodborne_dw/debug_catalogue.json',
 'src/architecture/resources/fabric.mod.json',
}
allowed_sources.update('src/architecture/java/dev/dreamwalker/bloodbornedw/composite/'+name+'.java' for name in ('CompositeClient','CompositeItem','CompositeRuntime','CompositeSourceShift','CompositeSpec','FabricCompositeWorld'))
implementation_changes=[]
for relative,digest in baseline['implementation'].items():
    path=ROOT/relative
    if not path.is_file() or sha(path.read_bytes())!=digest:implementation_changes.append(relative)
unexpected_sources=[p for p in implementation_changes if p not in allowed_sources]
allowed_classes={p.split('/java/',1)[1].removesuffix('.java') for p in allowed_sources if '/java/' in p}
unknown_classes=[p for p in changed_classes if p.removesuffix('.class').split('$',1)[0] not in allowed_classes]
manifest=json.loads((ROOT/'docs/gallery-v12.2/GALLERY-MANIFEST.json').read_text(encoding='utf8'))
old_native_poses=[{k:r.get(k) for k in ('key','id','state','blockStateTag','placedState','root','verticalOffset')} for r in manifest['exhibits'] if r['section']=='v12' and r['kind']=='architecture']
failures=[]
for label,ok in [('catalogueRowsAndAliases',protected_rows_exact),('oldAssetsAndDescriptors',not resource_deltas),('wallClassesAndMigration',wall_equal),('allRpClasses',rp_equal),('noMissingOldEntries',not missing),('onlyReviewedSharedSourcesChanged',not unexpected_sources),('onlyReviewedSharedClassesChanged',not unknown_classes),('noOtherOldNonclassChanges',not other_changes)]:
    if not ok:failures.append(label)
report={'schema':'dreamwalker-v12-reported-regression-static-v1','status':'PASS' if not failures else 'FAIL',
 'publishedV12Jar':str(OLD),'publishedV12Sha256':sha(OLD.read_bytes()),'deliveredV122Jar':str(NEW),'deliveredV122Sha256':sha(NEW.read_bytes()),
 'oldEntryCount':len(old_names),'oldCatalogueRows':len(old_rows),'oldRowsAndAliasesExact':protected_rows_exact,
 'oldAssetsAndCompositeDescriptorsChecked':len(protected_resources),'oldResourceDifferences':resource_deltas,
 'wallClassesChecked':len(wall_classes),'wallClassesAndMigrationExact':wall_equal,'rpClassesChecked':len(rp),'rpClassesExact':rp_equal,
 'wall90002And90011AlreadyInV12':old_native,'oldWallTextureSha256':textures,'wallTall0And6TextureBytesIdentical':textures['0']==textures['6'],
 'changedOldEntries':changed,'changedOldClasses':changed_classes,'missingOldEntries':missing,'otherNonclassChanges':other_changes,
 'changedOldImplementationSources':implementation_changes,'unexpectedSourceChanges':unexpected_sources,'unexpectedClassChanges':unknown_classes,
 'galleryNativeV12Poses':old_native_poses,'failures':failures,
 'scopeLimit':'Byte/source/resource comparison and explicit shared-hook review; not an exhaustive manual gameplay test of every old mechanism. Native wall placement comparison is reported separately.'}
(OUT/'static-comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({k:report[k] for k in ('status','oldEntryCount','oldCatalogueRows','wallClassesAndMigrationExact','rpClassesExact','wallTall0And6TextureBytesIdentical','changedOldClasses','failures')},ensure_ascii=False))
