"""Read-only final-JAR preservation audit for the V12.3 outline/icon release.

Uses immutable published V12 and V12.2 artifacts and the exact working baseline.
No build, world mutation, resource generation, extraction or Minecraft process.
Runtime/client checks are deliberately recorded separately.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
BASELINE = '772804c157bd28e0122a21f255b64c82d4ec5a43'
OLD = REPO/'releases/Dreamwalker-BB/v12/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.jar'
PREVIOUS = REPO/'releases/Dreamwalker-BB/v12.2-simple-collisions/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.2-simple-collisions.jar'
OLD_SHA = 'baaf38ab7449c2336dff7f882ee26677002b99b046238160f5d8b8d87e23ddf6'
PREVIOUS_SHA = 'ab2c7806fe0bfce94cdd081924fe9a643b087caa9b8b1ca7b17fcb35f3874c32'
DW = 'dev/dreamwalker/bloodbornedw/'
RP = 'dev/dreamwalker/bloodbornerp/'
NEW_SOURCES = {
    'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ImportedSelectionShapes.java',
    'src/rp/java/dev/dreamwalker/bloodbornerp/client/RpPlacementItemRenderer.java',
}
NEW_CLASSES = {path.split('/java/', 1)[1].removesuffix('.java') for path in NEW_SOURCES}


def sha(value):
    return hashlib.sha256(value).hexdigest()


def read_json(archive, entry):
    return json.loads(archive.read(entry))


def class_owner(entry):
    return entry.removesuffix('.class').split('$', 1)[0]


def git(*arguments):
    return subprocess.check_output(['git', *arguments], cwd=REPO)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--report', type=Path, default=ROOT/'docs/v12.3-outline-icons/preservation-and-resource-audit.json')
    parser.add_argument('--allow-source', action='append', default=[],
                        help='Reviewed changed production source path relative to mod, exact path only')
    parser.add_argument('--icon-manifest', type=Path,
                        help='Optional explicit icon map manifest, bound by SHA for evidence')
    args = parser.parse_args()
    final = args.jar.resolve()
    report = {'schema': 'dreamwalker-v123-preservation-resource-audit-v1',
              'updatedUtc': datetime.now(timezone.utc).isoformat(), 'status': 'FAIL',
              'baselineCommit': BASELINE, 'artifact': str(final), 'artifactSha256': sha(final.read_bytes()),
              'publishedV12Sha256': sha(OLD.read_bytes()), 'previousV122Sha256': sha(PREVIOUS.read_bytes()),
              'runtimeSelectionVerification': 'NOT_RUN_BY_THIS_STATIC_AUDIT',
              'nativeClientIconAppearanceVerification': 'NOT_RUN_BY_THIS_STATIC_AUDIT',
              'performanceBenchmark': 'NOT_RUN', 'failures': []}
    failures = report['failures']

    def check(condition, message, **detail):
        if not condition:
            failures.append(dict(message=message, **detail))

    check(report['publishedV12Sha256'] == OLD_SHA, 'Published V12 input changed')
    check(report['previousV122Sha256'] == PREVIOUS_SHA, 'Published V12.2 input changed')
    allowed_sources = set(args.allow_source)
    allowed_classes = {path.split('/java/', 1)[1].removesuffix('.java') for path in allowed_sources if '/java/' in path}
    with zipfile.ZipFile(OLD) as old, zipfile.ZipFile(PREVIOUS) as previous, zipfile.ZipFile(final) as current:
        for label, archive in [('old', old), ('previous', previous), ('current', current)]:
            check(len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate archive member', archive=label)
            check(archive.testzip() is None, 'Archive CRC failed', archive=label)
        names = {name for name in current.namelist() if not name.endswith('/')}
        previous_names = {name for name in previous.namelist() if not name.endswith('/')}
        old_names = {name for name in old.namelist() if not name.endswith('/')}
        # Authorised old changes are ONLY the RP placer icons previously using
        # the armor stand. Their corresponding server item/entity implementations
        # are separately required to remain byte-exact.
        icon_entries = {name for name in old_names if name.startswith('assets/bloodborne_rp/models/item/') and name.endswith('_placer.json') and (read_json(old, name).get('parent') == 'minecraft:item/armor_stand' or read_json(old, name).get('textures', {}).get('layer0') == 'minecraft:item/armor_stand')}
        check(len(icon_entries) == 76, 'Expected 69 canonical and 7 retained compatibility armor-stand placer icons', actual=len(icon_entries))
        report['authorizedOldPlacerItemModels'] = sorted(icon_entries)
        old_protected = {name for name in old_names if name.startswith(('assets/', 'bloodborne_dw/composite/')) and name not in icon_entries}
        protected_differences = [name for name in sorted(old_protected) if name not in names or old.read(name) != current.read(name)]
        report['protectedV12AssetsAndDescriptorsCount'] = len(old_protected)
        report['protectedV12AssetsAndDescriptorsDifferences'] = protected_differences
        check(not protected_differences, 'Unauthorised V12 asset/descriptor changed', entries=protected_differences)
        rp_classes = {name for name in old_names if name.startswith(RP) and name.endswith('.class') and class_owner(name) != RP+'client/BloodborneRpClient'}
        rp_changes = [name for name in sorted(rp_classes) if name not in names or old.read(name) != current.read(name)]
        report['protectedV12RpServerAndExistingRendererClassesCount'] = len(rp_classes)
        report['protectedV12RpClassDifferences'] = rp_changes
        check(not rp_changes, 'Existing RP server/entity/model/renderer class changed', entries=rp_changes)
        wall_classes = {name for name in old_names if name.startswith(DW+'architecture/wall/') or name.endswith(('/DiagonalPostFenceMixin.class', '/CatalogueMigration.class'))}
        wall_changes = [name for name in sorted(wall_classes) if name not in names or old.read(name) != current.read(name)]
        report['protectedV12WallAndMigrationClassesCount'] = len(wall_classes)
        report['protectedV12WallClassDifferences'] = wall_changes
        check(not wall_changes, 'V12 native wall/migration class changed', entries=wall_changes)
        compat_classes = {name for name in previous_names if name.startswith(DW+'compat/collision/')}
        compat_changes = [name for name in sorted(compat_classes) if name not in names or previous.read(name) != current.read(name)]
        report['collisionAuthorContractClassesCount'] = len(compat_classes)
        report['collisionAuthorContractClassDifferences'] = compat_changes
        check(not compat_changes, 'Exact-state author collision contract changed', entries=compat_changes)
        c0 = read_json(old, 'bloodborne_dw/debug_catalogue.json')
        c1 = read_json(current, 'bloodborne_dw/debug_catalogue.json')
        c2 = read_json(previous, 'bloodborne_dw/debug_catalogue.json')
        check(c1['entries'][:len(c0['entries'])] == c0['entries'] and c1.get('stateAliases') == c0.get('stateAliases'), 'V12 IDs/rows/state aliases changed')
        check(c1 == c2, 'Current registry catalogue changed from V12.2')
        report['protectedV12CatalogueRowsCount'] = len(c0['entries'])
        report['currentCatalogueRowsCount'] = len(c1['entries'])
        descriptors = {name for name in previous_names if name.startswith('bloodborne_dw/composite/') and name.endswith('.json')}
        descriptor_changes = [name for name in sorted(descriptors) if name not in names or previous.read(name) != current.read(name)]
        report['allPhysicalAndStateDescriptorsCount'] = len(descriptors)
        report['physicalAndStateDescriptorDifferences'] = descriptor_changes
        check(not descriptor_changes, 'Existing physical boxes, variants, pose, model, or dependency descriptor changed', entries=descriptor_changes)
        catalogue_entry = 'bloodborne_dw/remaining-catalogue.json'
        check(current.read(catalogue_entry) == previous.read(catalogue_entry), 'Remaining catalogue changed')
        imported = read_json(current, catalogue_entry)['objects']
        paths = {row['path'] for row in imported}
        check(len(paths) == len(imported) == 1045, 'Expected 1045 unique surviving new objects', actual=len(paths))
        variant_count = pose_count = rotation_count = empty_rotation_count = 0
        for path in sorted(paths):
            entry = f'bloodborne_dw/composite/{path}.json'
            check(entry in names, 'New object descriptor missing', path=path)
            if entry not in names:
                continue
            document = read_json(current, entry)
            check(document['id'] == 'bloodborne_dw:'+path, 'Descriptor registry identity changed', path=path)
            check(document.get('simplePhysicalCollision') is True, 'New simplified physical policy marker missing', path=path)
            check(f'assets/bloodborne_dw/blockstates/{path}.json' in names, 'New blockstate resource missing', path=path)
            check(f'assets/bloodborne_dw/models/item/{path}.json' in names, 'New item resource missing', path=path)
            variant_count += len(document['variants'])
            for variant in document['variants']:
                for pose in variant['poses'].values():
                    pose_count += 1
                    rotations = pose.get('simpleCollisionByRotation', [])
                    rotation_count += len(rotations)
                    empty_rotation_count += sum(not boxes for boxes in rotations)
                    check(len(rotations) == 8, 'New pose missing a physical rotation', path=path)
        report['newObjectCoverage'] = dict(objects=len(paths), variants=variant_count, poses=pose_count,
                                          rotations=rotation_count, emptyPhysicalRotations=empty_rotation_count)
        check((variant_count, pose_count, rotation_count) == (1802, 1803, 14424), 'Unexpected surviving new state coverage')
        changed = [name for name in sorted(previous_names & names) if previous.read(name) != current.read(name)]
        missing = sorted(previous_names-names)
        added = sorted(names-previous_names)
        report['changedPreviousArchiveEntries'] = changed
        report['addedArchiveEntries'] = added
        report['missingPreviousArchiveEntries'] = missing
        # Icon resource changes are exact-path authorised; all remaining old
        # resource/data entries must remain byte-exact. Class deltas are tied to
        # the root's explicit, reviewed production source allowlist.
        allowed_data = icon_entries | {'fabric.mod.json'}
        unknown = [name for name in changed if name not in allowed_data and not (name.endswith('.class') and class_owner(name) in allowed_classes)]
        check(not unknown, 'Unreviewed previous archive entry changed', entries=unknown)
        check(not missing, 'Previously present archive entry removed', entries=missing)
        unknown_added = [name for name in added if not (name.endswith('.class') and class_owner(name) in NEW_CLASSES)]
        check(not unknown_added, 'Unreviewed archive entry added', entries=unknown_added)
        for entry in sorted(icon_entries):
            icon = read_json(current, entry)
            check(icon.get('parent', '').removeprefix('minecraft:') == 'builtin/entity', 'RP placer icon does not opt into its actual model renderer', entry=entry, parent=icon.get('parent'))
        metadata = read_json(current, 'fabric.mod.json')
        report['version'] = metadata['version']
        check('v12.3' in metadata['version'], 'New metadata version was not advanced')
        report['metadataDependencies'] = metadata.get('depends', {})
        check(metadata.get('id') == 'bloodborne_dw', 'Primary mod ID changed')
        check(not any(name.startswith(('dev/dreamwalker/bloodbornedw/outlineqa/', 'dev/dreamwalker/bloodbornedw/remainingqa/', 'dev/dreamwalker/bloodbornedw/collisionqa/', 'dev/dreamwalker/bloodbornedw/gallery/')) for name in names), 'QA/gallery helper shipped inside production JAR')
        changed_icon_count = sum(name in changed for name in icon_entries)
        report['authorizedPlacerIconsChangedCount'] = changed_icon_count
        check(changed_icon_count == len(icon_entries), 'Some old armor-stand placer icon remained unchanged')
        if args.icon_manifest:
            manifest = json.loads(args.icon_manifest.read_text(encoding='utf8'))
            rows = manifest['rows']
            canonical = sum(not row['compatibilityAlias'] for row in rows)
            aliases = sum(row['compatibilityAlias'] for row in rows)
            check((len(rows), canonical, aliases) == (76, 69, 7), 'RP icon manifest coverage differs', total=len(rows), canonical=canonical, aliases=aliases)
            check({row['oldResource'] for row in rows} == icon_entries, 'RP icon manifest does not map all exact original placer entries')
            for row in rows:
                check(sha(current.read(row['oldResource'])) == row['newItemModelSha256'], 'Manifest item model hash differs from production', item=row['item'])
                for entry_key, hash_key in [('sourceModel', 'sourceModelSha256'), ('sourceTexture', 'sourceTextureSha256')]:
                    entry = row[entry_key]
                    check(entry in names and sha(current.read(entry)) == row[hash_key], 'Manifest source asset hash differs from production', item=row['item'], resource=entry)
            report['iconManifestCoverage'] = dict(total=len(rows), canonical=canonical, compatibilityAliases=aliases)
    # Snapshot exact baseline production tree. Never compare against old reports
    # or overwrite them; all new evidence is under this revision's own folder.
    prefix = ROOT.relative_to(REPO).as_posix()+'/'
    source_changes = []
    unauthorized = []
    baseline_bytes = git('archive', '--format=zip', BASELINE, '--', prefix+'src/architecture', prefix+'src/rp', prefix+'docs/COLLISION_IMPORT_HANDOFF.md')
    with zipfile.ZipFile(io.BytesIO(baseline_bytes)) as baseline_tree:
        tracked = [name for name in baseline_tree.namelist() if not name.endswith('/')]
        for full in tracked:
            relative = full.removeprefix(prefix)
            path = REPO/full
            prior = baseline_tree.read(full)
            if not path.is_file() or prior != path.read_bytes():
                source_changes.append(relative)
                resource_icon = relative.startswith('src/rp/resources/') and relative.removeprefix('src/rp/resources/') in icon_entries
                if relative not in allowed_sources and not resource_icon and relative != 'src/architecture/resources/fabric.mod.json':
                    unauthorized.append(relative)
    report['reviewedSourceAllowlist'] = sorted(allowed_sources)
    report['changedBaselineProductionFiles'] = source_changes
    report['unauthorizedBaselineProductionChanges'] = unauthorized
    check(not unauthorized, 'Unauthorised protected production source changed', paths=unauthorized)
    tracked_relative = {name.removeprefix(prefix) for name in tracked}
    added_sources = sorted(path.relative_to(ROOT).as_posix() for folder in ('src/architecture', 'src/rp') for path in (ROOT/folder).rglob('*') if path.is_file() and path.relative_to(ROOT).as_posix() not in tracked_relative)
    unknown_added_sources = [path for path in added_sources if path not in NEW_SOURCES]
    report['newProductionSourceFiles'] = added_sources
    check(not unknown_added_sources, 'Unreviewed production source added', paths=unknown_added_sources)
    contract = ROOT/'docs/COLLISION_IMPORT_HANDOFF.md'
    report['collisionAuthorContractDocumentSha256'] = sha(contract.read_bytes())
    if args.icon_manifest:
        report['iconManifest'] = dict(path=str(args.icon_manifest.resolve()), sha256=sha(args.icon_manifest.read_bytes()))
    report['scopeLimit'] = 'Static exact-byte preservation and resource/state coverage only. Runtime shape raycasts, ordinary pick/break, synchronization and real client icon/framebuffer checks require their separate evidence.'
    report['status'] = 'PASS' if not failures else 'FAIL'
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps({key:report[key] for key in ('status', 'artifactSha256', 'newObjectCoverage', 'authorizedPlacerIconsChangedCount', 'failures')}, ensure_ascii=False))
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
