"""One bounded, journaled repair of the matched pilot bootstrap metadata.

Default mode is read-only for worlds and stages a concrete repair plan outside
them. Only root runs --execute. The immutable map and copy receipts never change.
"""
import argparse,importlib.util,os,shutil,traceback
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
REPAIR=m.ROOT/'research'/'pilot'/'bootstrap-repair'
EXTRA={'data/fabricRegistry.dat','serverconfig/ftbessentials.snbt','session.lock'}

def current_hash(path):return m.sha(path) if path.is_file() else None
def sync_copy(source,target):
    target=m.checked(target);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(m.checked(source),target)
    with target.open('r+b') as stream:os.fsync(stream.fileno())

def author_plan():
    db=m.connect(m.DEFAULT_PLAN)
    try:
        m.require(m.meta(db,'scope') and not m.meta(db,'applyStarted'),'Only the unapplied bounded pilot may repair bootstrap metadata')
        binding=m.meta(db,'binding')
    finally:db.close()
    source=m.checked(binding['source']);manifest=m.read(binding['copyManifest']);worlds={};operations=[];REPAIR.mkdir(parents=True,exist_ok=True)
    for variant in ('baseline','candidate'):
        target=m.checked(m.ROOT/'research'/'pilot'/variant);copy_path=target.parent/(variant+'-copy.json');copy=m.read(copy_path);checkpoint=m.read(target.parent/(variant+'-copy-progress.json'));expected=checkpoint['files']
        m.require(copy['status']=='PASS' and copy['source']==str(source) and copy['sourceCopyManifestSha256']==binding['copyManifestSha256'] and checkpoint['binding']=={k:copy[k] for k in checkpoint['binding']},'Committed source-copy binding changed')
        actual={p.relative_to(target).as_posix():m.sha(p) for p in m.files(target)}
        m.require(set(expected)<=set(actual),'The failed pilot lost a source-copy file')
        extras=set(actual)-set(expected);m.require(extras<=EXTRA and (variant=='baseline' or not extras),'Unexpected post-bootstrap file; no broad cleanup is allowed: '+str(extras))
        changes=[rel for rel in expected if expected[rel]!=actual[rel]]
        m.require(set(changes)<= {'level.dat','level.dat_old'},'Unexpected game writes require a separate exact recovery review: '+str(changes))
        # No chunk is regenerated, decoded, or resaved by this correction.
        m.require(all(actual[rel]==wanted for rel,wanted in copy['regionSha256'].items()),'A cropped terrain region changed after failed startup')
        after=dict(expected)
        for rel in changes:
            src=m.checked(source/rel);m.require(m.sha(src)==manifest['files'][rel]['sha256']==expected[rel],'Original metadata bytes differ from committed source copy')
            stage=REPAIR/'staged'/variant/rel;sync_copy(src,stage)
            operations.append({'variant':variant,'path':rel,'kind':'replace','beforeSha256':actual[rel],'afterSha256':m.sha(stage),'staged':str(stage),'reason':'restore_exact_immutable_metadata'})
        level=m.compound(m.compound(m.read_nbt(source/'level.dat').root)['Data']);spawn=[level['Spawn'+a].value for a in 'XYZ'];m.require(spawn==[285,34,-842],'The reviewed original spawn binding changed')
        rel='data/bloodborne_dw_composite_owners.dat';src=m.checked(source/rel);m.require(actual[rel]==expected[rel]==manifest['files'][rel]['sha256']==m.sha(src),'Pilot global ledger has unreviewed writes')
        shards={p:expected[p] for p in expected if Path(p).parent.name=='bloodborne_dw_owner_shards'}
        m.require(len(shards)==407 and all(actual[p]==wanted for p,wanted in shards.items()),'The exact 407 copied owner shards must remain byte-identical')
        nbt=m.read_nbt(src);proof=m.crop_pilot_global_index(nbt,shards)
        stage=REPAIR/'staged'/variant/rel;stage.parent.mkdir(parents=True,exist_ok=True);m.write_nbt(stage,nbt);after[rel]=m.sha(stage)
        operations.append({'variant':variant,'path':rel,'kind':'replace','beforeSha256':actual[rel],'afterSha256':after[rel],'staged':str(stage),'reason':'filter_only_missing_owner_shard_index_entries'})
        for rel in sorted(extras):operations.append({'variant':variant,'path':rel,'kind':'quarantine','beforeSha256':actual[rel],'afterSha256':None,'reason':'preserve_failed_bootstrap_generated_file_outside_matched_world'})
        worlds[variant]={'target':str(target),'copyReceipt':str(copy_path),'copyReceiptSha256':m.sha(copy_path),'selectedChunkSetSha256':copy['selectedChunkSetSha256'],
                         'sourceSpawn':spawn,'beforeFiles':actual,'afterFiles':after,'ledger':{'path':'data/bloodborne_dw_composite_owners.dat','sourceSha256':expected['data/bloodborne_dw_composite_owners.dat'],'afterSha256':after['data/bloodborne_dw_composite_owners.dat'],'shards':shards,**proof},'regionSha256':copy['regionSha256']}
    m.require(worlds['baseline']['afterFiles']==worlds['candidate']['afterFiles'],'The corrected copies must have identical source file bytes before migration')
    plan={'schemaVersion':1,'status':'DRY_RUN','kind':'BOUNDED_PILOT_BOOTSTRAP_REPAIR','source':str(source),'sourceCopyManifest':binding['copyManifest'],'sourceCopyManifestSha256':binding['copyManifestSha256'],
          'plannerSha256':m.sha(__file__),'migrationSha256':m.sha(m.__file__),'worlds':worlds,'operations':operations,'mapWrites':0,'immutableSourceUnchanged':True}
    path=REPAIR/'plan.json';m.require(not path.exists(),'The one concrete bootstrap repair plan already exists; inspect it or execute it')
    m.atomic_json(path,plan);print(str(path));print('DRY_RUN: %d exact replacements/quarantines, 0 world writes'%len(operations))

def execute(path):
    path=m.checked(path);plan=m.read(path);m.require(path==REPAIR/'plan.json' and plan['kind']=='BOUNDED_PILOT_BOOTSTRAP_REPAIR' and plan['status']=='DRY_RUN','Only the task concrete repair plan is executable')
    m.require(m.sha(__file__)==plan['plannerSha256'] and m.sha(m.__file__)==plan['migrationSha256'],'Repair implementation changed after dry-run review; regenerate a new reviewed plan')
    m.require(m.sha(plan['sourceCopyManifest'])==plan['sourceCopyManifestSha256'],'Immutable source manifest changed')
    db=m.connect(m.DEFAULT_PLAN)
    try:m.require(m.meta(db,'scope') and not m.meta(db,'applyStarted'),'Bootstrap repair is forbidden after migration starts')
    finally:db.close()
    # Validate ALL source-copy files before the first mutation. Resume admits
    # only the exact old or planned new bytes, never an unrelated game write.
    allowed={v:set(w['beforeFiles']) for v,w in plan['worlds'].items()}
    operations={(op['variant'],op['path']):op for op in plan['operations']}
    for variant,world in plan['worlds'].items():
        target=m.checked(world['target']);m.require(target==m.ROOT/'research'/'pilot'/variant,'Unexpected pilot repair target')
        m.require(m.sha(world['copyReceipt'])==world['copyReceiptSha256'],'Copy receipt changed after repair review')
        for rel,before in world['beforeFiles'].items():
            op=operations.get((variant,rel));after=op['afterSha256'] if op else before
            m.require(current_hash(m.checked(target/rel)) in (before,after),'Changed file after repair review: '+variant+'/'+rel)
        for item in m.files(target):
            rel=item.relative_to(target).as_posix();m.require(rel in allowed[variant] or rel.endswith('.bootstrap-writing') and (variant,rel.removesuffix('.bootstrap-writing')) in operations,'Unexpected file after repair review: '+rel)
    for number,op in enumerate(plan['operations']):
        world=plan['worlds'][op['variant']];target=m.checked(Path(world['target'])/op['path']);backup=m.checked(REPAIR/'failed-bootstrap-evidence'/op['variant']/op['path']);journal=REPAIR/'journals'/('%03d.json'%number)
        if current_hash(target)==op['afterSha256']:
            m.require(backup.is_file() and m.sha(backup)==op['beforeSha256'],'Resumed mutation has no intact original-byte evidence');continue
        m.require(current_hash(target)==op['beforeSha256'],'Pre-mutation bytes changed')
        if backup.exists():m.require(m.sha(backup)==op['beforeSha256'],'Backup evidence changed')
        else:
            temporary=backup.with_name(backup.name+'.writing');sync_copy(target,temporary);os.replace(temporary,backup)
        m.atomic_json(journal,{'schemaVersion':1,'planSha256':m.sha(path),'operation':op,'backup':str(backup),'backupSha256':m.sha(backup),'phase':'BEFORE_SWAP'})
        if op['kind']=='quarantine':os.replace(target,backup)
        else:
            stage=m.checked(op['staged']);m.require(m.sha(stage)==op['afterSha256'],'Staged exact metadata changed');temporary=target.with_name(target.name+'.bootstrap-writing');sync_copy(stage,temporary);os.replace(temporary,target)
        m.atomic_json(journal,{'schemaVersion':1,'planSha256':m.sha(path),'operation':op,'backup':str(backup),'backupSha256':m.sha(backup),'phase':'COMMITTED'})
    worlds={}
    for variant,world in plan['worlds'].items():
        actual={p.relative_to(world['target']).as_posix():m.sha(p) for p in m.files(Path(world['target']))};m.require(actual==world['afterFiles'],'Final corrected pilot bytes differ from reviewed repair plan')
        worlds[variant]={k:v for k,v in world.items() if k not in ('beforeFiles','afterFiles')};worlds[variant]['fileSetSha256']=m.digest(actual)
    receipt={'schemaVersion':1,'status':'PASS','kind':plan['kind'],'source':plan['source'],'sourceCopyManifestSha256':plan['sourceCopyManifestSha256'],'repairPlan':str(path),'repairPlanSha256':m.sha(path),'worlds':worlds,'immutableSourceUnchanged':True,'terrainRegionWrites':0}
    m.atomic_json(REPAIR/'receipt.json',receipt);print(str(REPAIR/'receipt.json'))

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--execute',type=Path);a=ap.parse_args();m.control_check()
    try:
        execute(a.execute) if a.execute else author_plan();m.atomic_json(REPAIR/'last-invocation.json',{'status':'PASS','mode':'execute' if a.execute else 'dry-run'})
    except BaseException:
        m.atomic_json(REPAIR/'last-invocation.json',{'status':'FAIL','mode':'execute' if a.execute else 'dry-run','error':traceback.format_exc()});raise
