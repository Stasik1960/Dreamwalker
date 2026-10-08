"""Independently constrain TEMP-name hooks against the immutable pre-review V7 source."""
from __future__ import annotations
import difflib
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CHECKPOINT = ROOT / 'build/delivery/v7/dreamwalker-bb-fabric-1.20.1-v7-full-source.zip'
PREFIX = 'dreamwalker-bb-fabric-1.20.1-source-v7/'
RP = 'src/rp/java/'
ALLOWED = {
    RP + 'dev/dreamwalker/bloodbornerp/object/PlacementObjectItem.java': 2,
    RP + 'dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java': 1,
    RP + 'dev/dreamwalker/bloodbornerp/mob/RpMobEntity.java': 1,
    RP + 'dev/dreamwalker/bloodbornerp/mob/MobRegistry.java': 0,
    RP + 'dev/dreamwalker/bloodbornerp/weapon/TrickWeaponItem.java': 2,
    RP + 'dev/dreamwalker/bloodbornerp/content/BloodVialItem.java': 2,
}
def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()
def normalized(data: bytes) -> str:
    return data.decode('utf8').replace('\r\n', '\n')
def main() -> None:
    delivered = json.loads((CHECKPOINT.parent / 'dreamwalker-bb-fabric-1.20.1-v7-delivery-manifest.json').read_text(encoding='utf8'))
    assert delivered['source_archive']['archive_sha256'] == sha(CHECKPOINT.read_bytes())
    imported = json.loads((ROOT / 'reports/RP_IMPORT.json').read_text(encoding='utf8'))
    original_source = Path(imported['source'])
    assert sha(original_source.read_bytes()) == imported['source_sha256']
    rows, patches = [], []
    with zipfile.ZipFile(CHECKPOINT) as prior, zipfile.ZipFile(original_source) as supplied:
        prior_manifest = json.loads(prior.read(PREFIX + 'reports/RP_SOURCE_INTEGRATION.json'))
        assert prior_manifest['status'] == 'PASS_TWO_DECLARED_NBT_HOOKS_ONLY'
        assert prior_manifest['production_sha256'] == '1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9'
        for row in imported['imported_files']:
            path = row['path']
            if not path.startswith(RP):
                continue
            original = supplied.read(row['source_entry'])
            before = prior.read(PREFIX + path)
            actual = (ROOT / path).read_bytes()
            assert sha(original) == row['sha256']
            text = normalized(actual)
            presentation_lines = [line for line in text.splitlines(True) if 'DebugCatalogue.' in line]
            if path in ALLOWED:
                assert len(presentation_lines) == ALLOWED[path], 'Unexpected presentation hook count: ' + path
                for line in presentation_lines:
                    assert '@Override' in line and ('getName(ItemStack stack)' in line
                        or 'appendTooltip(ItemStack stack,' in line or 'getDefaultName()' in line)
                    assert not any(word in line for word in ('putString', 'put(', 'setCustomName', 'setUuid', 'writeNbt', 'readNbt'))
                reconstructed = ''.join(line for line in text.splitlines(True) if 'DebugCatalogue.' not in line)
                if path.endswith('/MobRegistry.java'):
                    assert reconstructed.count('new dev.dreamwalker.bloodbornedw.debug.CatalogueSpawnEggItem(') == 1
                    reconstructed = reconstructed.replace('new dev.dreamwalker.bloodbornedw.debug.CatalogueSpawnEggItem(', 'new SpawnEggItem(')
                assert reconstructed == normalized(before), 'Non-presentation difference from V7: ' + path
                patches += difflib.unified_diff(normalized(before).splitlines(True), text.splitlines(True),
                    fromfile='immutable-V7/' + path, tofile=path)
            else:
                assert before == actual, 'Unplanned post-V7 RP edit: ' + path
            rows.append({'path': path, 'source_sha256': sha(original), 'immutable_v7_sha256': sha(before),
                'current_sha256': sha(actual), 'original_byte_identical': original == actual,
                'immutable_v7_byte_identical': before == actual, 'presentation_only_change': path in ALLOWED})
        helper = 'src/rp/java/dev/dreamwalker/bloodbornerp/content/SourceLegacyPayload.java'
        assert prior.read(PREFIX + helper) == (ROOT / helper).read_bytes(), 'Live typed-NBT retention helper changed'
    assert len(rows) == 30 and sum(row['original_byte_identical'] for row in rows) == 24
    reference = Path(imported['reference_jar'])
    assert sha(reference.read_bytes()) == imported['reference_jar_sha256']
    resources = imported['reference_jar_resources']
    assert len(resources) == 707
    with zipfile.ZipFile(reference) as full:
        for row in resources:
            original = full.read(row['path'])
            assert sha(original) == row['jar_sha256']
            assert (ROOT / 'src/rp/resources' / row['path']).read_bytes() == original
    patch = ROOT / 'reports/RP_DEBUG_PRESENTATION.patch'
    patch.write_text(''.join(patches), encoding='utf8', newline='')
    report = {'schema': 'dreamwalker-rp-debug-presentation-audit-v1',
        'status': 'PASS_SIX_DECLARED_PRESENTATION_ONLY_CHANGES_AGAINST_IMMUTABLE_V7',
        'checkpoint_zip': str(CHECKPOINT), 'checkpoint_sha256': sha(CHECKPOINT.read_bytes()),
        'checkpoint_matches_delivery_manifest': True,
        'original_source_zip': str(original_source), 'original_source_sha256': imported['source_sha256'],
        'java_files': rows, 'original_java_files': 30, 'original_java_byte_identical': 24,
        'changed_since_v7': sorted(ALLOWED), 'other_original_java_exact_v7': 24,
        'live_nbt_helper_exact_v7': True, 'rp_resource_files_byte_identical': 707,
        'custom_name_semantics': 'Override default names only; ItemStack and Entity CustomName logic remains vanilla.',
        'registry_uuid_payload_and_gameplay_changed': False,
        'temp_catalogue': 'src/architecture/resources/bloodborne_dw/debug_catalogue.json',
        'catalogue_sha256': sha((ROOT / 'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_bytes()),
        'patch': str(patch), 'patch_sha256': sha(patch.read_bytes()),
        'game_save_retention': 'Historical V7 two-NBT-hook proof retained; fresh build/native checks pending parent run.'}
    (ROOT / 'reports/RP_DEBUG_PRESENTATION.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({key: report[key] for key in ('status', 'original_java_byte_identical', 'rp_resource_files_byte_identical')}))
if __name__ == '__main__':
    main()
