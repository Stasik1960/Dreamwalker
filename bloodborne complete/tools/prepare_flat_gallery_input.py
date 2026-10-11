"""Prepare a SHA-bound new-world author marker; never modify original inputs."""
import argparse
import copy
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    catalogue = json.loads((ROOT/'docs/gallery-v12.1/catalogue.json').read_text(encoding='utf-8'))
    assert catalogue['status'] == 'PASS'
    jar = Path(catalogue['productionJar'])
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == catalogue['productionJarSha256']
    updated = copy.deepcopy(catalogue)
    for row in updated['exhibits']:
        row['registryId'], row['itemId'], row['numericId'] = row['registry'], row['item'], row['id']
        row['blockStateTag'] = dict(row['state'])
        if row['registry'] in ('bloodborne_dw:prototype_wall', 'bloodborne_dw:prototype_wall_skin_0'):
            row['blockStateTag'].update(connections='manual', up='true', north='tall', east='none', south='none', west='none', rotation='0')
            row['galleryPoseNote'] = 'Manual tall section displays the retained authored art of this V12 wall type.'
    envelopes = json.loads((ROOT/'docs/gallery-platform-envelopes.json').read_text(encoding='utf-8'))
    marker = {'guard': 'FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY', 'gallerySchema': 'dreamwalker-flat-gallery-v1',
              'revision': 'V12', 'displayRevision': 'V12.1', 'productionJarSha256': catalogue['productionJarSha256'],
              'catalogue': updated, 'localVisualEnvelopes': envelopes['envelopes']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(marker, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    main()
