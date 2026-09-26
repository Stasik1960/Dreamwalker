"""Read-only physical conflict census of the frozen post-integration scan."""
import gzip,json,hashlib
from collections import Counter,defaultdict
from pathlib import Path
from atomic_owner_groups import ROOT,compile_groups
from convert_logical_world import DEFAULT_RESOURCES,add
from modded_world_adapter import compile_modded_rules
from logical_contract_v2 import load_contracts

def census():
    data=json.loads(gzip.decompress((ROOT/'docs/composite-grid-repair/atomic-world-residuals.json.gz').read_bytes()))
    wanted={g['transaction']:g for g in data['groups'] if g['category']=='shared-cell / root conflict'}
    rules,_,_=compile_modded_rules(DEFAULT_RESOURCES);rules,_=compile_groups(rules,DEFAULT_RESOURCES)
    geometry=json.loads((DEFAULT_RESOURCES.parent/'city/geometry.json').read_bytes())
    contracts,_=load_contracts(DEFAULT_RESOURCES)
    logical={f['id']:f['states'] for f in contracts['families']}
    runtime=json.loads((DEFAULT_RESOURCES.parent/'city/owner-runtime-mappings.json').read_bytes())
    names={v['id']:k for k,v in runtime['states'].items()}
    reasons=Counter(); pairs=Counter(); rows=[]
    def boxes(out,offset):
        ident=out.target[0].split(':')[1];key=','.join(k+'='+v for k,v in out.target[1])
        if ident in logical:
            result=[]
            for b in logical[ident][key]['physical_footprint']['boxes']:
                c=[max(0,b[i]-offset[i]) for i in range(3)]+[min(1,b[i+3]-offset[i]) for i in range(3)]
                if all(c[i]<c[i+3] for i in range(3)):result.append(c)
            return result
        v=geometry['blocks'][ident]['states'][key];v=geometry.get('profiles',{}).get(v.get('ref'),v)
        return v['cells'][','.join(map(str,offset))]['collision']
    for r in rules:
        if r.transaction_id not in wanted:continue
        dim,origin=r.allowed_origins[0];occupied=defaultdict(list)
        for out in r.outputs:
            root=add(origin,out.root_offset)
            for offset in out.shape:
                occupied[add(root,offset)].append((out,root,offset,boxes(out,offset)))
        counts=Counter();contacts=[];families=set()
        for p,owners in occupied.items():
            if len(owners)<2:continue
            for a_idx,a in enumerate(owners):
                for b in owners[a_idx+1:]:
                    roots=a[2]==(0,0,0) and b[2]==(0,0,0)
                    empty=not a[3] or not b[3]
                    intersects=any(all(max(x[i],y[i])<min(x[i+3],y[i+3])-1e-8 for i in range(3)) for x in a[3] for y in b[3])
                    category='root-root' if roots else 'empty-collision reservation' if empty else 'solid-volume overlap' if intersects else 'disjoint collision inside one cell'
                    counts[category]+=1
                    label=lambda x:names.get(x[0].target[0],x[0].target[0]).split('[')[0]
                    pair=tuple(sorted((label(a),label(b))))
                    families.add(pair)
                    contacts.append({'position':p,'category':category,'owners':[{'target':x[0].target,'root':x[1],'offset':x[2],'boxes':x[3]} for x in (a,b)]})
        # Exclusive primary category, plus all-contact counts below.
        primary=next((c for c in ('root-root','solid-volume overlap','disjoint collision inside one cell','empty-collision reservation') if counts[c]),'destination conflict only')
        reasons[primary]+=1
        for pair in families:pairs[pair]+=1
        rows.append({'transaction':r.transaction_id,'primary':primary,'contacts':contacts,'contactCounts':dict(counts)})
    role_groups=Counter();role_contacts=Counter()
    for row in rows:
        roles=set()
        for contact in row['contacts']:
            roots=sum(tuple(o['offset'])==(0,0,0) for o in contact['owners'])
            role=('helper-helper','root-helper','root-root')[roots]
            roles.add(role);role_contacts[role]+=1
        role_groups[' + '.join(sorted(roles))]+=1
    assert len(rows)==2862 and sum(reasons.values())==2862
    result={'inputSha256':hashlib.sha256((ROOT/'docs/composite-grid-repair/atomic-world-residuals.json.gz').read_bytes()).hexdigest(),
            'groups':len(rows),'primaryCounts':dict(reasons),'roleGroupCounts':dict(role_groups),'roleContactCounts':dict(role_contacts),
            'contactCounts':dict(sum((Counter(r['contactCounts']) for r in rows),Counter())),
            'mostFrequentPairs':[{'families':p,'groups':n} for p,n in pairs.most_common(30)],'details':rows}
    dest=ROOT/'docs/composite-grid-repair/physical-conflict-census.json.gz'
    dest.write_bytes(gzip.compress((json.dumps(result,separators=(',',':'))+'\n').encode(),mtime=0))
    print(json.dumps({k:v for k,v in result.items() if k!='details'},indent=2))

if __name__=='__main__':census()
