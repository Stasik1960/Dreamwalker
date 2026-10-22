"""Read-only inventory of the two bounded pilot copies after failed bootstrap."""
import importlib.util,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.control_check();report={'schemaVersion':1,'mapWrites':0,'variants':{}}
for variant in ('baseline','candidate'):
    target=m.ROOT/'research'/'pilot'/variant;checkpoint=m.read(target.parent/(variant+'-copy-progress.json'));expected=checkpoint['files'];changed=[];missing=[];extra=[]
    for rel,wanted in expected.items():
        path=m.checked(target/rel)
        if not path.is_file():missing.append(rel)
        else:
            actual=m.sha(path)
            if actual!=wanted:changed.append({'path':rel,'sourceCopySha256':wanted,'currentSha256':actual,'bytes':path.stat().st_size})
    for path in m.files(target):
        rel=path.relative_to(target).as_posix()
        if rel not in expected:extra.append({'path':rel,'sha256':m.sha(path),'bytes':path.stat().st_size})
    report['variants'][variant]={'copyReceiptSha256':m.sha(target.parent/(variant+'-copy.json')),'knownFiles':len(expected),'changed':changed,'missing':missing,'extra':extra}
m.atomic_json(m.ROOT/'research'/'pilot'/'bootstrap-recovery-inventory.json',report)
print(json.dumps(report,indent=2))
