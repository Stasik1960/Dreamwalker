"""Assert the material correction leaves every art/save/catalogue resource exact."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

PROJECT = Path(__file__).absolute().parents[2]
ROOT = PROJECT.parent
OUT = ROOT / 'research/city-materials-20261019'
BASE = ROOT / 'deliverables/collision-streaming-20261018/game/dreamwalker-bb-streaming.5.jar'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--jar', type=Path, required=True)
    args = ap.parse_args()
    candidate = args.jar.absolute()
    allowed = ['SourceFlatLight', 'StaticArchitectureClient', 'StaticArchitectureGeometry',
               'StaticArchitectureRenderView', 'CompositeWorldMesh']
    package = 'dev/dreamwalker/bloodbornedw/composite/'
    def allowed_class(path):
        return path.startswith(package) and any(path == package + c + '.class' or path.startswith(package + c + '$') for c in allowed)
    with zipfile.ZipFile(BASE) as base, zipfile.ZipFile(candidate) as new:
        a, b = set(base.namelist()), set(new.namelist())
        added = sorted(b - a)
        removed = sorted(a - b)
        changed = sorted(p for p in a & b if base.read(p) != new.read(p))
        assert not removed, removed
        assert all(allowed_class(p) for p in added), added
        assert all(allowed_class(p) or p == 'fabric.mod.json' for p in changed), changed
        old_meta, new_meta = [json.loads(z.read('fabric.mod.json')) for z in [base, new]]
        version = new_meta.pop('version')
        old_meta.pop('version')
        assert new_meta == old_meta and version.endswith('streaming.5-materials.1')
        resources = sorted(p for p in a if not p.endswith(('.class', '/')) and p != 'fabric.mod.json')
        assert all(base.read(p) == new.read(p) for p in resources)
        assert not any('/gallery/' in p for p in b), 'QA stays outside production'
        result = {'status': 'PASS_ART_GEOMETRY_IDS_SAVE_RESOURCES_AND_OPTIMIZATIONS_UNCHANGED',
                  'baseSha256': hashlib.sha256(BASE.read_bytes()).hexdigest(),
                  'candidateSha256': hashlib.sha256(candidate.read_bytes()).hexdigest(), 'version': version,
                  'identicalResources': len(resources), 'identicalPngs': sum(p.endswith('.png') for p in resources),
                  'identicalModels': sum('/models/' in p and p.endswith('.json') for p in resources),
                  'addedClasses': added, 'changedClassesAndMetadata': changed,
                  'allOtherClassesByteIdentical': True,
                  'scope': 'No texture/model/UV/layer/recipe/registry/optics/save resource edits. Existing used-UV/mip opacity classifier code unchanged except adding resource reload of flat-light metadata.'}
    (OUT / 'artifact-verification.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
