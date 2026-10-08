"""Freeze five-digit IDs from RECOGNIZED object instances, never source cells.

Does not run on candidate catalogs. Final allocation is gated by first-prototype
acceptance in the workflow; this module implements the invariant for later use.
"""
from collections import Counter
import copy

MAX_ID=99999
def allocate(objects,instances,previous=None):
    objects=list(objects);by_key={o['stable_key']:o for o in objects}
    if len(by_key)!=len(objects):raise ValueError('Duplicate stable object key')
    for obj in objects:
        if obj.get('semantic_status')!='CONFIRMED' or not obj.get('independent_installable'):
            raise ValueError('Unconfirmed object must not receive final ID: '+obj['stable_key'])
    counts=Counter();seen=set()
    for instance in instances:
        key=instance['stable_key']
        if key not in by_key:raise ValueError('Uncatalogued logical instance')
        if instance.get('recognition_status')!='CONFIRMED':raise ValueError('Ambiguous instance blocks frequency freezing')
        token=(instance['dimension'],instance['instance_key'])
        if token in seen:raise ValueError('Logical instance counted twice')
        if not instance.get('source_members'):raise ValueError('Instance has no source membership evidence')
        seen.add(token);counts[key]+=1
    ledger=copy.deepcopy(previous or {'schema':'dreamwalker-id-ledger-v1','next_id':1,'entries':[]})
    entries=ledger['entries'];claimed={e['stable_key']:e for e in entries};numbers=set()
    for entry in entries:
        value=entry['number']
        if len(value)!=5 or not value.isdigit() or not 1<=int(value)<=MAX_ID or value in numbers:
            raise ValueError('Invalid/duplicate/reserved ID in ledger')
        numbers.add(value)
    if len(claimed)!=len(entries):raise ValueError('Duplicate stable key in ledger')
    next_id=max(int(ledger.get('next_id',1)),max((int(n)+1 for n in numbers),default=1))
    # Initial order: actual logical instances desc, exact Unicode stable key asc.
    # Later ledger IDs remain stable, including tombstones, regardless of count.
    new_keys=sorted(set(by_key)-set(claimed),key=lambda k:(-counts[k],k))
    if next_id+len(new_keys)-1>MAX_ID:raise ValueError('Five-digit range exhausted; explicit user decision required')
    for key in new_keys:
        number=f'{next_id:05}';next_id+=1
        entry={'number':number,'registry_id':'bloodborne_dw:'+number,'stable_key':key,'active':True,
               'initial_instance_count':counts[key],'latest_instance_count':counts[key]}
        entries.append(entry);claimed[key]=entry
    for key,entry in claimed.items():
        entry['active']=key in by_key
        entry['latest_instance_count']=counts[key]
    ledger['next_id']=next_id
    ledger['initial_tie_break']='Python Unicode code-point ascending stable_key; no locale/case folding'
    ledger['count_definition']='one confirmed logical instance per (dimension,instance_key), irrespective of orientations/states/helper-cell count'
    return ledger
