"""Exact semantic dictionary diff of the five already identified shards."""
import collections,copy
import pilot_dependencies as p

def semantic(nbt):
    root=p.m.compound(nbt.root);data=p.m.compound(root['data']) if 'data'in root else root
    owners=data.get('ownerTable',p.m.Tag(9,[],10)).value;values=data.get('dataTable',p.m.Tag(9,[],10)).value;shapes=data.get('shapeTable',p.m.Tag(9,[],10)).value;cells={};members={}
    for tag in data.get('cells',p.m.Tag(9,[],10)).value:
        fields=p.m.compound(tag);outer={k:v for k,v in fields.items() if k!='refs'};position=p.m.canonical(p.c._typed(p.m,p.m.Tag(10,outer)))
        for ref in fields['refs'].value:
            oi,di,si=ref.value;owner=owners[oi];uid=p.m.owner_uuid(owner);key=p.m.canonical({'position':position,'owner':p.c._typed(p.m,owner),'rootData':p.c._typed(p.m,values[di]),'shape':p.c._typed(p.m,shapes[si])});p.m.require(key not in cells,'Repeated exact semantic contribution');cells[key]={'uuid':uid,'position':p.c._typed(p.m,p.m.Tag(10,outer)),'owner':p.c._typed(p.m,owner),'rootData':p.c._typed(p.m,values[di]),'shape':p.c._typed(p.m,shapes[si])}
    for tag in data.get('ownerMembership',p.m.Tag(9,[],10)).value:
        fields=p.m.compound(tag);owner=owners[fields['ownerRef'].value];uid=p.m.owner_uuid(owner)
        for member in fields.get('members',p.m.Tag(9,[],10)).value:
            item=p.m.compound(member);expanded={k:copy.deepcopy(v) for k,v in item.items() if k!='shapeRef'};expanded['shape']=shapes[item['shapeRef'].value];payload={'owner':p.c._typed(p.m,owner),'outer':p.c._typed(p.m,p.m.Tag(10,{k:v for k,v in fields.items() if k not in ('ownerRef','members')})),'member':p.c._typed(p.m,p.m.Tag(10,expanded))};key=p.m.canonical(payload);p.m.require(key not in members,'Repeated exact semantic member');members[key]={'uuid':uid,**payload}
    other=copy.deepcopy(nbt);fields=p.m.compound(other.root);fields=p.m.compound(fields['data']) if 'data'in fields else fields
    for key in ('ownerTable','dataTable','shapeTable','cells','ownerMembership'):fields.pop(key,None)
    return cells,members,p.typed_sha(other.root),{k:len(data.get(k,p.m.Tag(9,[],10)).value) for k in ('ownerTable','dataTable','shapeTable','cells','ownerMembership')}

def main():
    audit=p.m.read(p.REPAIR/'audit.json');inventory=p.m.read(p.INVENTORY);outside={r['uuid'] for r in inventory['owners'] if r['rootOutsideCopy']};db=p.c.attached_readonly(p.m,p.PLAN);binding=p.m.meta(db,'binding');source=p.Path(binding['source']);rows=[]
    try:
        for error in audit['errors']:
            if 'detail' not in error:continue
            rel=error['detail']['path'];expected=p.m.read_nbt(source/rel);p.m.cleanup_dictionary(p.PriorCleanup(db),'.',expected);actual=p.m.read_nbt(p.m.ROOT/'research/pilot/candidate'/rel);ec,em,eo,es=semantic(expected);ac,am,ao,ass=semantic(actual)
            lost_cells=[ec[key] for key in sorted(set(ec)-set(ac))];new_cells=[ac[key] for key in sorted(set(ac)-set(ec))];lost_members=[em[key] for key in sorted(set(em)-set(am))];new_members=[am[key] for key in sorted(set(am)-set(em))]
            rows.append({'path':rel,'sourceSha256':p.m.sha(source/rel),'actualSha256':p.m.sha(p.m.ROOT/'research/pilot/candidate'/rel),'expectedTableCounts':es,'actualTableCounts':ass,'otherTypedFieldsEqual':eo==ao,'otherTypedFieldsExpectedSha256':eo,'otherTypedFieldsActualSha256':ao,'lostCells':lost_cells,'addedOrChangedCells':new_cells,'lostMembers':lost_members,'addedOrChangedMembers':new_members,'lostOwners':sorted({r['uuid'] for r in lost_cells+lost_members}),'allLostOwnersExactOutside960':all(r['uuid'] in outside for r in lost_cells+lost_members)})
        p.m.atomic_json(p.REPAIR/'shard-semantic-difference.json',{'status':'READ_ONLY_EXACT_SEMANTIC_DIFFERENCE','shards':rows,'mapWrites':0,'databaseWrites':0})
    finally:db.close()

if __name__=='__main__':main()
