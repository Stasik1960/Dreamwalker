"""Collect final native client proof without changing images or the test world."""
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'build/runtime-client-v123-outline-icons-3'
DEST = ROOT / 'docs/v12.3-outline-icons/client'
JAR = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.3-outline-icons.jar'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
read = lambda p: json.loads(p.read_text(encoding='utf8'))


def main():
    evidence = read(RUN / 'v123-client-output.json')
    profile = read(RUN / 'client-profile.json')
    digest = sha(JAR)
    assert evidence['productionJarSha256'] == profile['artifact_sha256'] == digest
    assert evidence['status'] == 'CAPTURE_COMPLETE' and evidence['captureComplete']
    assert evidence['capturedFrames'] == len(evidence['frames']) == 9
    icons = [f for f in evidence['frames'] if f['type'] == 'icons']
    outlines = [f for f in evidence['frames'] if f['type'] == 'outline']
    assert len(icons) == 2 and len(outlines) == 7
    assert sum(len(f['icons']) for f in icons) == 69
    assert all(i['builtin3dModel'] and i['sourceBakedPoseUnchanged'] for f in icons for i in f['icons'])
    assert all(f['status'] == 'PASS' for f in evidence['frames'])
    assert all(f['actualUuid'] == f['uuid'] for f in outlines)
    # Java writes native Windows console bytes directly into the redirected handle.
    # Check ASCII shutdown markers without rewriting or guessing its code page.
    console = (RUN / 'launch-console.log').read_bytes()
    assert b'All dimensions are saved' in console and b'Stopping!' in console
    files = [(RUN / 'client-profile.json', 'client-profile.json'),
             (ROOT / 'build/v123-client-3.json', 'launcher-report.json'),
             (RUN / 'v123-client-output.json', 'native-client-output.json'),
             (RUN / 'launch-console.log', 'launch-console.log')]
    for frame in evidence['frames']:
        filename = frame['fileName']
        assert Path(filename).name == filename
        path = RUN / 'screenshots' / filename
        assert sha(path) == frame['sha256'] and path.stat().st_size == frame['bytes']
        files.append((path, 'screenshots/' + filename))
    entries = []
    for source, relative in files:
        target = DEST / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        assert not target.exists(), 'Never overwrite evidence: ' + str(target)
        shutil.copyfile(source, target)
        assert sha(source) == sha(target)
        entries.append({'path': relative, 'bytes': target.stat().st_size, 'sha256': sha(target)})
    result = {'schema': 'dreamwalker-v123-client-evidence-v1', 'status': 'PASS',
              'productionJarSha256': digest, 'nativeFrames': 9, 'canonicalIcons': 69,
              'outlineExamples': 7, 'sourceBakedTransformsPreserved': 'PASS',
              'normalClientAndWorldShutdown': 'PASS_LOG_EVIDENCE',
              'physicalMouseKeyboard': 'NOT_RUN', 'performanceBenchmark': 'NOT_RUN',
              'hiddenCompatibilityAliasesDirectRendering': 'NOT_RUN', 'files': entries}
    (DEST / 'evidence-index.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    # The parent agent opened all seven final native frames with view_image.
    visual = {'schema': 'dreamwalker-v123-outline-visual-review-v1', 'status': 'PASS',
              'reviewer': 'Parent agent: direct view_image inspection of all seven final, unmodified native PNGs',
              'nativeClientRun': RUN.name, 'productionJarSha256': digest,
              'method': 'Simple ordinary rectangular active-cell outlines are visible; no former strips around model details. Minecraft naturally clips multi-cell object outlines to the actual hit cell.',
              'passableException': 'Chain 90086 and grass 90151 retain a simple picking box with zero player collision, as explicitly requested by the user.',
              'frames': [{'fileName': f['fileName'], 'sha256': f['sha256'],
                          'outlineBoxes': f['outlineBoxes'], 'physicalBoxes': f['physicalBoxes'],
                          'status': 'PASS'} for f in outlines]}
    (DEST / 'outline-visual-review.json').write_text(json.dumps(visual, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in result.items() if k != 'files'}))


if __name__ == '__main__':
    main()
