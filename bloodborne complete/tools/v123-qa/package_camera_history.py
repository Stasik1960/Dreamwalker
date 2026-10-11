"""Preserve exact client-2 FAIL and client-only build-5 provenance without world writes."""
from __future__ import annotations
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / 'docs/v12.3-outline-icons/history'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    client = ROOT / 'build/runtime-client-v123-outline-icons-2'
    build = ROOT / 'build/v123-qa-build-5'
    qa = json.loads((client / 'v123-client-output.json').read_text(encoding='utf8'))
    result = json.loads((build / 'build-report.json').read_text(encoding='utf8'))
    assert qa['status'] == 'FAIL' and result['status'] == 'PASS' and result['productionJarUnchanged']
    assert len(list((client / 'screenshots').glob('*.png'))) == 8
    mapped = [
        (client / 'client-profile.json', 'client-failed-2-launcher.json'),
        (ROOT / 'build/v123-client-2.json', 'client-failed-2-wrapper.json'),
        (client / 'v123-client-output.json', 'client-failed-2-qa.json'),
        (client / 'launch-console.log', 'client-failed-2-console.log'),
        (build / 'build-report.json', 'qa-build-5.json'),
        (build / 'build.log', 'qa-build-5.log'),
        (build / 'resources/v123-qa-input.json', 'qa-build-5-binding.json'),
    ]
    mapped += [(png, 'client-failed-2-' + png.name) for png in sorted((client / 'screenshots').glob('*.png'))]
    copied = []
    for source, name in mapped:
        target = DEST / name
        assert source.is_file() and not source.is_symlink() and not target.exists(), str(target)
        shutil.copy2(source, target)
        assert sha(source) == sha(target)
        copied.append({'source': str(source), 'file': name, 'bytes': target.stat().st_size, 'sha256': sha(target)})
    note = '''The second native client run completed both icon sheets and six of seven requested outline frames, including the earlier chain helper target. It failed because the final grass camera did not settle at the requested location. The original server fixture camera for grass 90151 had feet Y=127.005 while the fixture floor top was Y=128; the client camera therefore requested a standing player position inside the solid test platform. The original timeout did not record the displaced actual camera, so this is a confirmed invalid fixture pose and the cause of drift was inferred from the collision geometry. No production defect was established.

Client QA correction only: derive the exact original server-tested target center from the saved camera (horizontal distance 3, standing eye height 1.62 and original pitch). Raise camera feet to at least root Y+0.25, recompute pitch using the actual native client standing eye height and require the same target to be within 4.75 blocks. Keep the original fixture camera in the evidence row. The source fixture world, native server helper and production JAR are unchanged. The camera timeout now records expected and actual camera position, eye height, bounding box, feet state and screen. Every requested frame still requires the real native crosshair, actual production owner targeting, saved root UUID and outline/physical shape assertions before capture.

The failed client automatically requested ordinary Minecraft shutdown, and its log records saving the integrated world. All eight native PNGs and raw FAIL evidence are preserved byte for byte. Build 5 is the separately hashed client-only QA addon implementing this correction; its production input SHA remained unchanged. A fresh native client run is required to establish the final result and is collected separately by the parent task. QA addons are excluded from the production mod.
'''
    (DEST / 'client-camera-correction.txt').write_text(note, encoding='utf8')
    output = {'status': 'PASS_COLLECTION', 'failedClientStatus': qa['status'], 'qaBuildStatus': result['status'], 'productionJarSha256': result['productionJarSha256'], 'addonSha256': result['addonSha256'], 'rawByteCopies': copied}
    (DEST / 'camera-history-evidence-index.json').write_text(json.dumps(output, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': 'PASS_COLLECTION', 'rawFiles': len(copied), 'destination': str(DEST)}))

if __name__ == '__main__':
    main()
