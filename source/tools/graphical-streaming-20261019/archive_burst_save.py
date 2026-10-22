from common import *

wait_foreign()
source=OUT/'normal-save';target=OUT/'normal-save-burst-oom'
assert source.resolve().parent==target.resolve().parent==OUT.resolve() and not target.exists()
receipt=json.loads((source/'receipt.json').read_text())
assert receipt['pid']==111980 and receipt['status']=='RUNNING'
assert 'OutOfMemoryError' in (source/'console.log').read_text()
receipt.update(status='FAIL_9G_HEAP_EXHAUSTED_TIMEOUT',error='Five distant probes requested in one tick; 3001 WorldChunk objects in all-object histogram. No normal save or successful reopen.',supervisorTimeoutSeconds=420)
write(source/'receipt.json',receipt)
ordinary(source).rename(target)
