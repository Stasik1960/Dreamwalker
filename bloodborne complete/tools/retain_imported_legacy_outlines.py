"""Package exact pre-simplification NEW aiming data, not physical collision data."""
import gzip
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'docs/collision-simplification/default-physical-before.jsonl.gz'
RESOURCE = ROOT/'src/architecture/resources/bloodborne_dw/imported-legacy-outlines.json.gz'
REPORT = ROOT/'docs/collision-simplification/legacy-outline-retention.json'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def vertical_center_hit(boxes, x=8, z=8):
    values = [b['to'][1] for b in boxes if b['from'][0] <= x <= b['to'][0] and b['from'][2] <= z <= b['to'][2]]
    return max(values) if values else None


def main():
    catalogue = json.loads((ROOT/'src/architecture/resources/bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    with gzip.open(SOURCE, 'rt', encoding='utf-8') as stream:
        history = {row['id']:row for row in (json.loads(line) for line in stream)}
    objects = []
    descriptor_hashes = {}
    variants = poses = original_boxes = 0
    for entry in catalogue['objects']:
        descriptor = ROOT/'src/architecture/resources/bloodborne_dw/composite'/f"{entry['path']}.json"
        data = descriptor.read_bytes()
        current = json.loads(data)
        old = history[current['id']]
        assert current['simplePhysicalCollision'] is True
        assert len(old['variants']) == len(current['variants'])
        for index, variant in enumerate(old['variants']):
            variants += 1
            assert set(variant['poses']) == set(current['variants'][index]['poses'])
            for pose_name, boxes in variant['poses'].items():
                poses += 1
                original_boxes += len(boxes)
                assert all(set(b)=={'from','to'} and all(b['from'][i]<b['to'][i] for i in range(3)) for b in boxes)
        objects.append(old)  # exact original lists; never infer from visual artwork.
        descriptor_hashes[str(descriptor.relative_to(ROOT))] = sha(data)
    assert len(objects)==1045 and variants==1802 and poses==1803
    document = {'schemaVersion':1, 'units':16,
        'purpose':'Aiming/outline/picking only: exact pre-simplification source_ collision lists. Never physical collision, registry entries or authored Collision Studio data.',
        'sourceHistorySha256':sha(SOURCE.read_bytes()), 'objects':objects}
    packed = gzip.compress((json.dumps(document, ensure_ascii=False, separators=(',', ':'))+'\n').encode('utf-8'), mtime=0)
    RESOURCE.write_bytes(packed)
    stored = json.loads(gzip.decompress(RESOURCE.read_bytes()))
    assert stored['objects'] == objects
    for relative, digest in descriptor_hashes.items():
        assert sha((ROOT/relative).read_bytes()) == digest
    fern = history['bloodborne_dw:source_potted_fern']['variants'][0]['poses']['closed']
    current_fern = json.loads((ROOT/'src/architecture/resources/bloodborne_dw/composite/source_potted_fern.json').read_text(encoding='utf-8'))['variants'][0]['poses']['closed']['collision']
    report = {'schema':'dreamwalker-v122-legacy-outline-retention-v1', 'status':'PASS_EXACT_ORIGINAL_DATA_PACKAGED',
        'resource':str(RESOURCE.relative_to(ROOT)), 'resourceSha256':sha(packed), 'sourceHistorySha256':sha(SOURCE.read_bytes()),
        'objects':len(objects), 'variants':variants, 'poses':poses, 'originalAimingBoxes':original_boxes,
        'descriptorFilesUnchanged':len(descriptor_hashes), 'retiredIdsExcluded':['bloodborne_dw:source_jungle_trapdoor_bottom','bloodborne_dw:source_jungle_trapdoor_open'],
        'confirmedPreFixRegression':{'id':'bloodborne_dw:source_potted_fern', 'ray':'Vertical through local x=z=8/16; downward from above pot.', 'oldAimingHitY':vertical_center_hit(fern)/16, 'simplifiedPhysicalHitY':vertical_center_hit(current_fern)/16, 'oldBoxes':len(fern), 'simplifiedPhysicalBoxes':len(current_fern)},
        'runtimeStrategy':'Unregistered CompositeSpec without new physical marker reuses original CompositeSpec distribution and CompositeSourceShift. Only NEW contextSelection/empty-ledger root outline fallback read this archive. Existing selection lists, V12 behavior and physical defaults remain untouched.',
        'limitations':'Data provenance and source-path proof; actual outline/raycast/picking need Minecraft QA after rebuild.'}
    REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':
    main()
