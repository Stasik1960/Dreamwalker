"""Verify every restored byte from release volumes without duplicating the worlds."""
from pathlib import Path
import subprocess,sys,json
P=Path(__file__).resolve().parents[2];O=P.parent.parent/'DW-native-grid-github-transfer-20261022'
assert (O/'history-append-receipt.json').exists()
result=subprocess.run([sys.executable,str(O/'restore_bundle.py'),'--assets',str(O/'release-assets'),'--verify-only'],cwd=O,creationflags=subprocess.CREATE_NO_WINDOW)
assert result.returncode==0,'Restore verification failed'
report=json.loads((O/'release-assets/verification.json').read_text());assert report['status']=='PASS_ALL_FILES_VERIFIED';print(json.dumps(report),flush=True)
