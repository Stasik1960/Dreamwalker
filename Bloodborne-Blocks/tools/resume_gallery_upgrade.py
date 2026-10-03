"""Durable offline gallery migration using the tested assembly engine.

The checkpoint pointer changes only after a complete world snapshot and its
operation log exist. An interrupted working copy is never a resume source.
"""
from __future__ import annotations
import argparse, gc, gzip, hashlib, json, os, shutil, tempfile
from collections import Counter
from dataclasses import fields, is_dataclass
from pathlib import Path
from upgrade_gallery_city import (CITY, StreamingWorld, compile_city_rules, city_files,
    find_candidates, initially_eligible, spatial_order, apply_rule, add, hash_tree, audit_helpers)


def stable(value):
    if is_dataclass(value):return {f.name:stable(getattr(value,f.name)) for f in fields(value)}
    if isinstance(value,dict):return {k:stable(v) for k,v in value.items()}
    if isinstance(value,(set,frozenset)):
        return sorted((stable(v) for v in value),key=lambda v:json.dumps(v,sort_keys=True))
    if isinstance(value,(tuple,list)):return [stable(v) for v in value]
    return value


def write_json(path,value):
    path=Path(path);temporary=path.with_suffix(path.suffix+'.tmp')
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.suffix=='.gz':
        with gzip.open(temporary,'wt',encoding='utf8',compresslevel=1) as stream:
            json.dump(value,stream,ensure_ascii=False,separators=(',',':'))
    else:
        with temporary.open('w',encoding='utf8') as stream:
            json.dump(value,stream,ensure_ascii=False,separators=(',',':'))
            stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.replace(temporary,path)


def read_json(path):
    path=Path(path)
    if path.suffix=='.gz':
        with gzip.open(path,'rt',encoding='utf8') as stream:return json.load(stream)
    return json.loads(path.read_bytes())


class Checkpoints:
    def __init__(self,root):
        self.root=Path(root);self.root.mkdir(parents=True,exist_ok=True)
        self.head=self.root/'checkpoint-head.json'
    def load(self):
        if not self.head.exists():return None
        head=read_json(self.head)
        for name in (head['state'],):
            if Path(name).is_absolute() or '..' in Path(name).parts:raise ValueError('unsafe checkpoint path')
        state=read_json(self.root/head['state'])
        if Path(state['citySnapshot']).is_absolute() or '..' in Path(state['citySnapshot']).parts:raise ValueError('unsafe snapshot path')
        snapshot=self.root/state['citySnapshot']
        if hash_tree(snapshot)!=state['snapshotHashes']:raise ValueError('checkpoint snapshot changed')
        return state
    def save(self,state,world=None):
        number=int(state.get('checkpoint',0))+1
        folder=self.root/f'cp-{number:06d}'
        while folder.exists():number+=1;folder=self.root/f'cp-{number:06d}'
        folder.mkdir()
        if world is not None:
            world.save();shutil.copytree(world.root,folder/'city')
            state['citySnapshot']=(folder/'city').relative_to(self.root).as_posix()
            state['snapshotHashes']=hash_tree(folder/'city')
        state['checkpoint']=number
        metadata=folder/'state.json.gz';write_json(metadata,state)
        write_json(self.head,{'state':metadata.relative_to(self.root).as_posix()})
        print(f'saved checkpoint {number}: phase={state["phase"]} cursor={state.get("cursor",0)} converted={len(state["report"]["converted"])}',flush=True)


def candidate(row):return int(row[0]),row[1],tuple(row[2])


def row_for(dim,origin,rule):
    return {'dimension':dim,'position':list(origin),'target':rule.target[0],'source':rule.source_reference,
            'tp':f'/execute in {dim} run tp @s {origin[0]} {origin[1]+2} {origin[2]}'}


def record_success(state,index,dim,origin,rules,diagnostics,consumed):
    rule=rules[index];row=row_for(dim,origin,rule);report=state['report']
    for p in (rule.source,)+rule.members:consumed[(dim,add(origin,p.offset))]=len(report['converted'])
    report['converted'].append(row);state['convertedKeys'].append([index,dim,list(origin)])
    choice=diagnostics.get('ownerChoices',{}).get(rule.source_reference)
    if choice:report['ownerChoices'].append({**row,**choice})
    choice=diagnostics.get('documentChoices',{}).get(rule.target[0].split(':')[-1])
    if choice:report['documentChoices'].append({**row,**choice})


def finish(source,output,report_path,manifest,paths,state,view,city):
    report=state['report'];original=state['sourceHashes']
    if output.exists():raise ValueError('output already exists without completed matching checkpoint')
    stage=Path(tempfile.mkdtemp(prefix='gallery-final-',dir=output.parent))
    result=stage/'gallery';shutil.copytree(source,result)
    for p in paths:shutil.copy2(view/p,result/p)
    manifest['cityContextOriginal']=manifest['cityContext']
    manifest['cityContext']=[{'path':p.as_posix(),'sha256':hashlib.sha256((result/p).read_bytes()).hexdigest()} for p in paths]
    report['counts']={'converted':len(report['converted']),'rejected':len(report['rejected']),
                      'reasons':dict(Counter(r['reason'] for r in report['rejected']))}
    manifest['cityUpgrade']={'sourceGallery':source.name,'counts':report['counts'],'cityNowDiffersFromStandalone':True}
    manifest['cityCases']=report['rejected']+report['overlappingChoices']+report['ownerChoices']+report['documentChoices']
    specimen_tps={r['id']:r.get('tp','') for r in manifest.get('specimens',[])}
    for row in report['ownerChoices']:
        for alt in row['alternatives']:alt['galleryTp']=specimen_tps.get(alt['id'].split(':')[-1],'')
    write_json(result/'gallery-manifest.json',manifest)
    review=['# Обновление города в галерее','','Отдельная Main-City не изменена. Площадки и промежутки в 3 блока сохранены.','',
            '| Статус | Объект | Причина | Переход |','|---|---|---|---|']
    for status,key in [('заменён','converted'),('сохранён для проверки','rejected'),
                       ('пересекающийся шаблон — проверить','overlappingChoices'),('пункт документа — проверить','documentChoices')]:
        review.extend(f'| {status} | {r["target"]} | {r.get("reason",r["source"])} | `{r["tp"]}` |' for r in report[key])
    review.extend(['','## Выбор между одинаковыми исходными деталями','',
                   '| Город | Выбранная модель | Варианты в галерее |','|---|---|---|'])
    for row in report['ownerChoices']:
        review.append(f'| `{row["tp"]}` | {row["chosen"]} | '+ '; '.join(f'{a["id"]}: `{a["galleryTp"]}`' for a in row['alternatives'])+' |')
    (result/'City-Upgrade.md').write_text('\n'.join(review)+'\n',encoding='utf8')
    excluded={p.as_posix() for p in paths}|{'gallery-manifest.json','City-Upgrade.md'}
    changed=[p for p,h in original.items() if p not in excluded and hashlib.sha256((result/p).read_bytes()).hexdigest()!=h]
    if changed:raise ValueError('exhibit or metadata file changed: '+str(changed))
    report['unchangedGalleryFiles']=[p for p in original if p not in excluded]
    if original!=hash_tree(source):raise ValueError('source mutated')
    report['status']='complete'
    # A final world is never exposed until all mutation/audit gates pass. The
    # complete checkpoint can reconstruct the report if interrupted here.
    os.replace(result,output);write_json(report_path,report)
    state['phase']='complete';state['outputHashes']=hash_tree(output)
    return report


def upgrade_resumable(source,output,report_path,progress,city=CITY,interval=5000):
    source,output,report_path,progress,city=(Path(p).resolve() for p in (source,output,report_path,progress,city))
    if any(source==p or source in p.parents or p in source.parents for p in (output,progress)):raise ValueError('outputs must be outside source')
    if output==progress or output in progress.parents or progress in output.parents:raise ValueError('checkpoint and final output must be separate')
    if any(p==root or root in p.parents or p in root.parents for root in (city,city.parent/'logical') for p in (output,progress)):raise ValueError('outputs must be outside resources')
    if any(p==report_path or p in report_path.parents for p in (source,output,progress,city,city.parent/'logical')):raise ValueError('report must be outside worlds and resources')
    if interval<1:raise ValueError('positive checkpoint interval required')
    output.parent.mkdir(parents=True,exist_ok=True)
    original=hash_tree(source);manifest=read_json(source/'gallery-manifest.json');paths=city_files(manifest)
    for r in manifest['cityContext']:
        if original[r['path']]!=r['sha256']:raise ValueError('city source hash changed: '+r['path'])
    rules,diagnostics=compile_city_rules(city)
    fingerprint=hashlib.sha256()
    for name in ('upgrade_gallery_city.py','resume_gallery_upgrade.py'):
        fingerprint.update(Path(__file__).with_name(name).read_bytes())
    for rule in rules:fingerprint.update(json.dumps(stable(rule),sort_keys=True,separators=(',',':')).encode())
    fingerprint.update(json.dumps(diagnostics,sort_keys=True,separators=(',',':')).encode())
    definitions={d['id']:d for scope in (city,city.parent/'logical') for d in read_json(scope/'definitions.json')['blocks']}
    geometry=read_json(city/'geometry.json')
    for p in (city/'geometry.json',city.parent/'logical/geometry.json'):
        fingerprint.update(p.read_bytes())
    fingerprint.update(json.dumps(definitions,sort_keys=True).encode())
    from atomic_owner_groups import reservations
    from source_mapping_archive import archive
    reserved=reservations(rules);archive.cache_clear();gc.collect()
    checkpoints=Checkpoints(progress);state=checkpoints.load()
    identity={'source':str(source),'output':str(output),'reportPath':str(report_path),'rulesHash':fingerprint.hexdigest(),'sourceHashes':original}
    if state is not None and any(state.get(k)!=v for k,v in identity.items()):raise ValueError('checkpoint inputs or rules changed')
    if state is not None and state['phase']=='complete':
        if hash_tree(output)!=state['outputHashes']:raise ValueError('completed output changed')
        write_json(report_path,state['report']);return state['report']
    if state is not None and state['phase']=='finalize' and output.exists():
        # Recover the tiny window between the atomic final directory rename and
        # checkpoint/report commit; require the snapshot's exact city payloads.
        if any(hashlib.sha256((output/p).read_bytes()).hexdigest()!=state['snapshotHashes'][p.as_posix()] for p in paths):raise ValueError('uncommitted final output does not match snapshot')
        excluded={p.as_posix() for p in paths}|{'gallery-manifest.json','City-Upgrade.md'}
        if any(hashlib.sha256((output/p).read_bytes()).hexdigest()!=h for p,h in original.items() if p not in excluded):raise ValueError('uncommitted output changed exhibits')
        state['report']['unchangedGalleryFiles']=[p for p in original if p not in excluded]
        specimen_tps={r['id']:r.get('tp','') for r in manifest.get('specimens',[])}
        for row in state['report']['ownerChoices']:
            for alt in row['alternatives']:alt['galleryTp']=specimen_tps.get(alt['id'].split(':')[-1],'')
        final_report=read_json(output/'gallery-manifest.json')['cityUpgrade']['counts']
        state['report']['counts']=final_report;state['report']['status']='complete'
        write_json(report_path,state['report']);state['phase']='complete';state['outputHashes']=hash_tree(output);checkpoints.save(state)
        return state['report']
    if output.exists():raise ValueError('final output must be new')
    view=Path(tempfile.mkdtemp(prefix='working-',dir=progress))
    if state is None:
        shutil.copy2(source/'level.dat',view/'level.dat')
        for p in paths:(view/p).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source/p,view/p)
        state={**identity,'phase':'scan','cursor':0,'found':[],'eligible':[],'convertedKeys':[],'consumed':[],
               'report':{'schemaVersion':1,'status':'staging','compiler':diagnostics,'converted':[],'rejected':[],
                         'overlappingChoices':[],'ownerChoices':[],'documentChoices':[],'followUpPasses':[]}}
    else:shutil.copytree(progress/state['citySnapshot'],view,dirs_exist_ok=True)
    world=StreamingWorld(view,{});entities=world.block_entities()
    if 'citySnapshot' not in state:checkpoints.save(state,world)
    print(f'compiled {len(rules)} rules; loaded {len(world.chunks)} chunks; resume phase={state["phase"]}',flush=True)
    if state['phase']=='scan':
        def scanned(cursor,found):
            state['cursor']=cursor;state['found']=[[i,d,list(p)] for i,d,p in sorted(found,key=spatial_order)];checkpoints.save(state)
        candidates=find_candidates(world,rules,progress=scanned,resume={'cursor':state['cursor'],'found':state['found']})
        write_json(progress/'candidates.json.gz',candidates)
        state['report']['candidatePatterns']=len(candidates);state['phase']='eligible';state['cursor']=0;state.pop('found',None);checkpoints.save(state)
    else:candidates=[candidate(r) for r in read_json(progress/'candidates.json.gz')]
    spatial=sorted(candidates,key=spatial_order)
    if state['phase']=='eligible':
        for n in range(state['cursor'],len(spatial)):
            index,dim,origin=spatial[n]
            if initially_eligible(world,dim,origin,rules[index],reserved):state['eligible'].append([index,dim,list(origin)])
            state['cursor']=n+1
            if (n+1)%interval==0:checkpoints.save(state)
        state['phase']='apply';state['cursor']=0;checkpoints.save(state)
        print(f'initially eligible patterns={len(state["eligible"])}',flush=True)
    eligible={candidate(r) for r in state['eligible']}
    consumed={(dim,tuple(point)):int(index) for dim,point,index in state['consumed']}
    def save_mutations():
        state['consumed']=[[dim,list(point),i] for (dim,point),i in consumed.items()]
        checkpoints.save(state,world)
    if state['phase']=='apply':
        queue=[r for r in candidates if r in eligible]
        for n in range(state['cursor'],len(queue)):
            index,dim,origin=queue[n]
            reason=apply_rule(world,entities,dim,origin,rules[index],definitions,geometry,reserved)
            if reason is None:record_success(state,index,dim,origin,rules,diagnostics,consumed)
            state['cursor']=n+1
            if (n+1)%interval==0:save_mutations()
        state['phase']='followup';state['cursor']=0;state['passAdditional']=0;state['passPending']=[];state['passPartial']={};save_mutations()
    if state['phase']=='followup':
        while True:
            partial=Counter(state['passPartial'])
            for n in range(state['cursor'],len(spatial)):
                index,dim,origin=spatial[n];rule=rules[index]
                if world.get(dim,add(origin,rule.source.offset))==rule.source.state:
                    if all(world.get(dim,add(origin,p.offset))==p.state for p in (rule.source,)+rule.members):
                        reason=apply_rule(world,entities,dim,origin,rule,definitions,geometry,reserved)
                        if reason is None:
                            record_success(state,index,dim,origin,rules,diagnostics,consumed);state['passAdditional']+=1
                        elif reason!='different_source_weighted_visual':state['passPending'].append({**row_for(dim,origin,rule),'reason':reason})
                    else:partial[rule.target[0]]+=1
                state['cursor']=n+1
                if (n+1)%interval==0:state['passPartial']=dict(partial);save_mutations()
            additional=state['passAdditional'];state['report']['followUpPasses'].append(additional)
            print(f'follow-up pass {len(state["report"]["followUpPasses"])}: converted={additional} blocked={len(state["passPending"])}',flush=True)
            if additional==0:
                state['report']['rejected']=state['passPending'];state['report']['partialAnchorPatterns']=dict(partial)
                converted_keys={candidate(r) for r in state['convertedKeys']}
                overlaps=[]
                for index,dim,origin in sorted(eligible-converted_keys,key=spatial_order):
                    rule=rules[index]
                    affected=sorted({consumed[(dim,add(origin,p.offset))] for p in (rule.source,)+rule.members if (dim,add(origin,p.offset)) in consumed})
                    if affected:overlaps.append({**row_for(dim,origin,rule),'reason':'incomplete_after_prior_conversion',
                        'chosenOperations':affected,'chosenTargets':[state['report']['converted'][i]['target'] for i in affected]})
                state['report']['overlappingChoices']=overlaps;state['phase']='audit';state['cursor']=0;save_mutations();break
            if len(state['report']['followUpPasses'])>len(candidates):raise ValueError('migration did not reach a fixed point')
            state['cursor']=0;state['passAdditional']=0;state['passPending']=[];state['passPartial']={};save_mutations()
    del entities,world,consumed;gc.collect()
    if state['phase']=='audit':
        audit=audit_helpers(StreamingWorld(view,{}),city)
        if not audit['ok']:raise ValueError('saved helper audit failed: '+json.dumps(audit['orphans'][:5]))
        state['report']['helpers']={'checked':audit['checked'],'orphans':len(audit['orphans'])}
        state['phase']='finalize';checkpoints.save(state)
    if state['phase']=='finalize':
        result=finish(source,output,report_path,manifest,paths,state,view,city);checkpoints.save(state);return result
    raise ValueError('unknown migration phase')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--report',required=True,type=Path);p.add_argument('--progress',required=True,type=Path)
    a=p.parse_args();r=upgrade_resumable(a.source,a.output,a.report,a.progress);print(json.dumps(r['counts']))
