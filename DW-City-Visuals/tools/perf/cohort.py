"""Immutable logical cohort lock shared by launcher and independent analysis."""
import hashlib,json
from pathlib import Path

def digest_json(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True).encode('utf-8')).hexdigest()

def seal_cohort(path,lock,run_name):
    path=Path(path);logical=digest_json(lock)
    value={'schema':1,'firstRun':run_name,'profileLockSha256':logical,'lock':lock}
    path.parent.mkdir(parents=True,exist_ok=True)
    try:
        with path.open('x',encoding='utf-8') as stream:json.dump(value,stream,indent=2)
    except FileExistsError:
        stored=json.loads(path.read_text(encoding='utf-8-sig'))
        if stored.get('profileLockSha256')!=digest_json(stored['lock']):raise AssertionError('Stored cohort lock integrity failure')
        if stored['profileLockSha256']!=logical:
            changed=[key for key in sorted(set(lock)|set(stored['lock'])) if lock.get(key)!=stored['lock'].get(key)]
            raise AssertionError('Cohort changed before process launch: '+', '.join(changed))
    return logical
