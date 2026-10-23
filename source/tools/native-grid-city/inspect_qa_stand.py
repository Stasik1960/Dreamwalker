"""Read only the twelve explicitly reported failed QA-door cells and their ledgers.

This is an observation tool, never restoration or mutation authorization. It
opens two bounded terrain records and their exact local owner-shard files.
"""
import argparse,hashlib,struct,uuid
from pathlib import Path
import migrate as m
import context_stage as c
from pilot_dependencies import read_slot,chunk_decode

REPORT_SHA='573dbc1f451f5b7313e2f4406d6fae74dd1b40cfe65053d817348e4c44d5f419'
OWNER='b9eb3995-1f6a-47c6-915b-bf455cd1e30b'
ROOT=(1024,225,111)
BASE=(1024,225,112)
BASE_INDEX=1713

def typed(tag):return c._typed(m,tag)
def typed_sha(tag):return m.digest(typed(tag))
def fields(nbt):
    root=m.compound(nbt.root)
    return m.compound(root['data']) if 'data' in root else root
def position(row):
    if 'pos' in row:return tuple(row['pos'].value)
    return tuple(row[k].value for k in ('x','y','z'))
def owner_id(owner):return m.owner_uuid(owner)

def inspect(world,report_path,expected_sha,out):
    m.require(expected_sha==REPORT_SHA and m.sha(report_path)==expected_sha,'Only the exact failed twelve-cell QA attempt is authorized')
    m.require(m.checked(world)==m.checked(m.ROOT/'research/pilot/candidate'),'Only the existing candidate QA stand may be inspected')
    report=m.read(report_path);installed=report['installedWholeObjects'];m.require(report['status']=='FAIL' and len(installed)==1,'Unexpected failed fixture report')
    fixture=installed[0];m.require(fixture['ownerUuid']==OWNER,'Unexpected test-owned identity')
    points={tuple(at) for at in fixture['closedOpenCells']};expected={(1024,y,z) for y in range(225,229) for z in range(110,113)}
    m.require(points==expected and len(points)==12,'The report must name exactly the approved twelve cells')
    uid=m.canonical(list(struct.unpack('>4i',uuid.UUID(OWNER).bytes)));chunks=sorted({(x>>4,z>>4) for x,y,z in points})
    paths={};rows=[];issues=[];shards=[]
    def bind(path):
        path=m.checked(path);before=m.sha(path) if path.is_file() else None;paths[str(path)]=before;return before
    for cx,cz in chunks:
        path=world/'region'/('r.%d.%d.mca'%(cx>>5,cz>>5));bind(path);record=read_slot(path,cx,cz);m.require(record,'Approved QA terrain record is absent')
        root=m.compound(chunk_decode(record[0],record[1]).root);m.require((root['xPos'].value,root['zPos'].value)==(cx,cz),'Terrain record address mismatch')
        sections=c._sections(m,root);be_map={tuple(m.compound(tag)[k].value for k in ('x','y','z')):tag for tag in root.get('block_entities',m.Tag(9,[],10)).value}
        for at in sorted(points):
            if (at[0]>>4,at[2]>>4)!=(cx,cz):continue
            state=m.source_state(sections,at);be=be_map.get(at);value={'position':list(at),'state':state,'blockEntity':None}
            if be:
                data=m.compound(be);owners=m.be_owners(data);owner_ids=sorted({owner_id(m.compound(v)['owner']) for v in owners}|({owner_id(data['resident'])} if 'resident'in data else set()))
                value['blockEntity']={'typedSha256':typed_sha(be),'typedValue':typed(be),'ownerUuids':owner_ids,'nativeGridBase':data.get('NativeGridBase',m.Tag(3,-1)).value}
                if any(v!=uid for v in owner_ids):issues.append({'kind':'FOREIGN_QA_CELL_OWNER','position':list(at),'owners':owner_ids})
                if 'NativeGridBase'in data and (at!=BASE or data['NativeGridBase'].type!=3 or data['NativeGridBase'].value!=BASE_INDEX):issues.append({'kind':'UNEXPECTED_NATIVE_BASE','position':list(at)})
            elif state['registryId'].startswith('bloodborne_dw:native_grid_'):
                if at!=BASE or state!=c.grid_state(BASE_INDEX):issues.append({'kind':'UNEXPECTED_PLAIN_NATIVE_CELL','position':list(at),'state':state})
            elif state!=m.AIR:issues.append({'kind':'NONEMPTY_CELL_WITHOUT_TEST_BE','position':list(at),'state':state})
            rows.append(value)
        shard=world/'data/bloodborne_dw_owner_shards'/('owners-%d.%d.nbt'%(cx,cz));before=bind(shard)
        item={'chunk':[cx,cz],'path':str(shard),'sha256':before,'testOwnerRows':[],'testContributions':[],'testMembership':[],'allStandRefs':[]}
        if before:
            data=fields(m.read_nbt(shard));owners=data.get('ownerTable',m.Tag(9,[],10)).value;values=data.get('dataTable',m.Tag(9,[],10)).value;shapes=data.get('shapeTable',m.Tag(9,[],10)).value
            indices={i for i,owner in enumerate(owners) if owner_id(owner)==uid};item['testOwnerRows']=[{'index':i,'typed':typed(owners[i])} for i in sorted(indices)]
            for tag in data.get('cells',m.Tag(9,[],10)).value:
                cell=m.compound(tag);at=position(cell)
                for ref in cell['refs'].value:
                    oi,di,si=ref.value;m.require(0<=oi<len(owners) and 0<=di<len(values) and 0<=si<len(shapes),'Invalid local ledger reference')
                    if at in points:item['allStandRefs'].append({'position':list(at),'ownerUuid':owner_id(owners[oi]),'ref':list(ref.value)})
                    if oi in indices:
                        item['testContributions'].append({'position':list(at),'owner':typed(owners[oi]),'rootData':typed(values[di]),'shape':typed(shapes[si])})
                        if at not in points:issues.append({'kind':'TEST_OWNER_OUTSIDE_APPROVED_TWELVE','position':list(at)})
                    elif at in points:issues.append({'kind':'FOREIGN_STAND_LEDGER_REF','position':list(at),'ownerUuid':owner_id(owners[oi])})
            for tag in data.get('ownerMembership',m.Tag(9,[],10)).value:
                if m.compound(tag)['ownerRef'].value in indices:item['testMembership'].append(typed(tag))
        shards.append(item)
    global_path=world/'data/bloodborne_dw_composite_owners.dat';before=bind(global_path);global_info={'path':str(global_path),'sha256':before}
    if before:
        data=fields(m.read_nbt(global_path));global_info['fields']={key:{'type':tag.type,'length':len(tag.value) if hasattr(tag.value,'__len__') else None} for key,tag in sorted(data.items())}
        if 'OwnerShardChunks'in data:
            actual=set(data['OwnerShardChunks'].value);global_info['approvedChunksIndexed']=[{'chunk':[cx,cz],'present':((cz&0xffffffff)<<32|(cx&0xffffffff)) in actual or (((cz&0xffffffff)<<32|(cx&0xffffffff))-(1<<64)) in actual} for cx,cz in chunks]
    for name,before in paths.items():m.require((m.sha(name) if Path(name).is_file() else None)==before,'A QA stand input changed while being inspected: '+name)
    result={'schemaVersion':1,'status':'OBSERVED_EXACT_QA_STAND' if not issues else 'BLOCKED_FOREIGN_OR_UNEXPECTED_STAND_DATA','kind':'BOUNDED_TWELVE_CELL_QA_DOOR_OBSERVATION','report':{'path':str(report_path),'sha256':expected_sha},'world':str(world),'ownerUuid':OWNER,'ownerIntArray':uid,'root':list(ROOT),'expectedBase':{'position':list(BASE),'index':BASE_INDEX},'cells':rows,'shards':shards,'globalIndex':global_info,'issues':issues,'inputFileHashes':paths,'sourceSha256':m.sha(__file__),'worldWrites':0,'databaseReads':0,'databaseWrites':0,'restorationAuthorized':False}
    m.atomic_json(out,result);return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--world',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--expected-report-sha256',required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();m.control_check()
    result=inspect(m.checked(args.world),m.checked(args.report),args.expected_report_sha256,args.out);print(m.canonical({'status':result['status'],'out':str(args.out),'sha256':m.sha(args.out),'cells':len(result['cells']),'issues':result['issues']}))
if __name__=='__main__':main()
