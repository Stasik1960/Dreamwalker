"""Record completed assigned native review with raw-byte carry and explicit visibility."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/v12.4-dedup'
RUN = ROOT / 'build/runtime-client-v124-comparison-final-2'
DIGEST = '4c9e202cff6ae5acd17386a30547726948a0d0d9babf4e529c84ec5c51b0c640'
PAGES = set(range(81, 108)) | set(range(152, 177))
OPENED_FINAL = {171, 172, 173, 174, 175, 176}


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def main():
    capture = read(RUN / 'v124-client-comparison-output.json')
    assert capture['captureComplete'] and capture['productionJarSha256'] == DIGEST
    scan = read(ROOT / 'build/v124-stair-final2-review-scan.json')
    assert not scan['notYetCaptured']
    carried = {row['page']: row for row in scan['byteIdenticalPreviouslyReviewed']}
    fresh = {row['page'] for row in scan['freshRawReviewRequired']}
    assert fresh == {172, 173, 175, 176} and fresh <= OPENED_FINAL
    frames, pairs, alternate, transparent = [], [], [], []
    for frame in capture['frames']:
        if frame['page'] not in PAGES:
            continue
        image = RUN / 'screenshots' / frame['fileName']
        assert hashlib.sha256(image.read_bytes()).hexdigest() == frame['sha256']
        if frame['page'] in carried:
            assert frame['sha256'] == carried[frame['page']]['sha256']
        else:
            assert frame['page'] in OPENED_FINAL
        frames.append({'page': frame['page'], 'fileName': frame['fileName'], 'sha256': frame['sha256'],
                       'status': 'PASS_OBSERVED', 'pairsActuallyReviewed': len(frame['pairs']),
                       'reviewProvenance': 'ACTUAL_ORIGINAL_FINAL2_PNG_OPENED' if frame['page'] in OPENED_FINAL
                       else 'EXACT_ORIGINAL_PNG_BYTES_OF_ACTUALLY_REVIEWED_C30_FRAME',
                       'alsoExactPreviouslyReviewedBytes': frame['page'] in carried})
        pairs.extend(frame['pairs'])
        for row in frame['pairs']:
            if row.get('kind') != 'MIGRATION_ALT_VIEW':
                continue
            item = {key: row[key] for key in ['oldRegistryId', 'oldVariant', 'viewYawDegrees', 'viewPitchDegrees']}
            item.update(fileName=frame['fileName'], sha256=frame['sha256'])
            if row['oldId'] == '90141' or row['oldId'] == '93223' and row['oldVariant'] == 5:
                item.update(status='SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED',
                            visualVisibility='NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE')
                transparent.append(item)
            else:
                item['status'] = 'CULLED_BACK_FACE' if row['oldVariant'] == 4 and row['viewYawDegrees'] == 125 else 'VISIBLE_SOURCE_MATCH'
                item['observation'] = {
                    (3, 125): 'Matching broad gray vertical strip is visible before and after.',
                    (3, 305): 'Matching narrow edge of the gray strip is visible before and after.',
                    (4, 125): 'Opaque authored one-sided strip faces away; both sides of the comparison are culled.',
                    (4, 305): 'Matching gray vertical strip is visible before and after.'
                }[(row['oldVariant'], row['viewYawDegrees'])]
                alternate.append(item)
    assert {row['page'] for row in frames} == PAGES and len(frames) == 52
    assert len(alternate) == len(transparent) == 4
    model_visibility = [row for row in alternate if (row['oldVariant'], row['viewYawDegrees']) in {(3, 125), (4, 305)}]
    assert len(model_visibility) == 2 and all(row['status'] == 'VISIBLE_SOURCE_MATCH' for row in model_visibility)
    native_ids = {row['oldRegistryId'] for row in read(DOC / 'stair-review/functional-type-fragment.json')['mappings']}
    native = [row for row in pairs if not row.get('kind') and row['oldRegistryId'] in native_ids]
    functional = [row for row in pairs if row.get('kind') == 'FUNCTIONAL_NEW_STATE']
    assert len(native) == 315 and len(functional) == 200
    alpha_proof = read(DOC / 'transparent-93223-v5-source-proof.json')
    assert alpha_proof['productionJarSha256'] == DIGEST and alpha_proof['nonzeroAlphaSamples'] == 0
    result = {
        'schemaVersion': 1, 'status': 'PASS_ASSIGNED_REVIEW_WITH_EXPLICIT_SOURCE_TRANSPARENCY',
        'productionJarSha256': DIGEST, 'reviewer': 'v124_stair_types', 'reviewedFrames': len(frames),
        'captureSha256': hashlib.sha256((RUN / 'v124-client-comparison-output.json').read_bytes()).hexdigest(),
        'rawGameDirectory': str(RUN),
        'reviewMethod': 'Original native PNGs; 48 previously opened C30 images carried only after exact raw byte SHA equality; 6 fresh images opened explicitly.',
        'rawImagesModified': False, 'assignedCoverage': ['81-107', '152-176'], 'frames': frames,
        'nativeMappingsReviewed': 315, 'nativeStairMappingsReviewed': 313, 'nativeLadderMappingsReviewed': 2,
        'nativeMappingsAppearance': 'PASS_OBSERVED_PRESERVED_SOURCE_UV_GEOMETRY_ROTATION_AND_TRIM',
        'functionalFormsReviewed': 200, 'functionalFormsObservedPass': 200,
        'correctedFresh93028Corners': {'states': [{'variant': variant, 'trim': trim} for trim in [False, True] for variant in [7, 9]],
                                      'pages': [172, 173], 'status': 'PASS_ACTUAL_NATIVE_OPAQUE_EXTERIOR_WALLS'},
        'retainedPairsReviewed': sum(row.get('kind') == 'RETAINED_REVIEW' for row in pairs),
        'retainedStairMaterials': 'KEEP19_NOTICEABLY_DIFFERENT_AUTHORED_SURFACES',
        'materialRationale': 'stair-review/material-differences.md', 'failures': [],
        'alternateOneSidedModelsVisible': True, 'alternateViewVisibility': alternate,
        'alternateModelVisibility': model_visibility, 'transparentViewVisibility': transparent,
        'alternateSourceModelsVisible': True,
        'sourceAlternateObservation': '90245 original and current framed stone relief are visible and match on page176 yaw215.',
        'sourceVisibilityExceptions': [
            {'oldRegistryId': 'bloodborne_dw:source_addon_floor_panels_empty', 'oldVariant': 0,
             'mainId': '90133', 'variant': 8, 'status': 'SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED',
             'visualVisibility': 'NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE',
             'evidence': 'one-sided-source-alpha-audit.json: source UP face samples256alpha0pixels; actual native pages175 both negativepitch views are blank as source.'},
            {'oldRegistryId': 'bloodborne_dw:source_assembly_red_stained_glass_pane', 'oldVariant': 5,
             'mainId': '93214', 'variant': 12, 'status': 'SOURCE_FULLY_TRANSPARENT_STATE_PRESERVED',
             'visualVisibility': 'NOT_APPLICABLE_FULLY_TRANSPARENT_SOURCE',
             'evidence': 'transparent-93223-v5-source-proof.json: exact finalJAR/source parts and22nonzero-area faces/1144alpha0samples; both native125/305views preserve blank source.'}
        ],
        'sourceCorrectionProofs': ['stair-render-c30-correction-proof.json', 'stair-c30-legacy-art-proof.json',
                                   'stair-face-alpha-corrected.json', 'transparent-93223-v5-source-proof.json'],
        'physicalMouseKeyboard': 'NOT_RUN', 'frameRateBenchmark': 'NOT_RUN'
    }
    proof_paths = ['one-sided-source-alpha-audit.json'] + result['sourceCorrectionProofs']
    result['sourceProofDigests'] = [{'path': path, 'sha256': hashlib.sha256((DOC / path).read_bytes()).hexdigest()}
                                   for path in proof_paths]
    (DOC / 'native-final2-stair-review.json').write_bytes((json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({key: value for key, value in result.items() if key in {
        'status', 'productionJarSha256', 'reviewedFrames', 'nativeMappingsReviewed',
        'functionalFormsReviewed', 'functionalFormsObservedPass', 'retainedPairsReviewed'}}))


if __name__ == '__main__':
    main()
