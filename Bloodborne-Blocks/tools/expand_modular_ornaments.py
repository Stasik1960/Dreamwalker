"""Split static architectural mountings at overlaps instead of moving masonry."""
from repair_modular_overlaps import *
from world_edit import WorldEditor,unpack_position,AIR
from build_modular_palette import dump,OUT,FACING
from convert_modular_world import props
from modular_mesh import model
import numpy as np

def expand(target,all_static=False,output=None):
    rows=[] if all_static else json.loads((ROOT/'docs/world-overlap-repair-v2.json').read_text())['unresolved']
    definitions={d['id']:d for d in json.loads((RES/'bloodborne_blocks/definitions.json').read_text())['blocks']}
    semantics=json.loads((ROOT/'docs/semantic-catalog-v2.json').read_text(encoding='utf-8-sig'))
    geometry=json.loads((RES/'bloodborne_blocks/geometry.json').read_text());palette=load_palette();world=WorldEditor(target)
    eligible={i for i,s in semantics.items() if s['category']=='ornament' and not s.get('keep_large') and definitions[i]['kind'] not in INTERACTIVE_KINDS}
    helpers=collections.defaultdict(list);helper_roots={};seeds=[];empty_candidates={}
    for path in sorted(target.glob('region/*.mca')):
        for c in RegionFile.open(path).chunks():
            data=compound(c.nbt().root)
            if all_static:
                cx,cz=int(data['xPos'].value),int(data['zPos'].value)
                for section in data.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                    sd=compound(section)
                    if 'block_states' not in sd:continue
                    bs=compound(sd['block_states']);candidates=[]
                    for index,entry in enumerate(bs['palette'].value):
                        name=compound(entry)['Name'].value;ident=name.removeprefix(NS)
                        if not name.startswith(NS) or ident not in definitions:continue
                        text=block_state_key(entry)
                        if text not in empty_candidates:
                            state={**definitions[ident]['default'],**props(entry)}
                            g=resolved_geometry(geometry,ident,statekey(state))
                            empty_candidates[text]=not g.get('cells')
                        if ident in eligible or empty_candidates[text]:candidates.append(index)
                    if not candidates:continue
                    array=unpack_fast(bs);sy=int(sd['Y'].value)
                    for index in candidates:
                        for local in np.flatnonzero(array==index):
                            i=int(local);seeds.append((cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)))
            for be in data.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                bd=compound(be)
                if bd.get('id',Tag(8,'')).value==NS+'architecture_part':
                    root=unpack_position(bd['Root'].value);pos=tuple(bd[a].value for a in ('x','y','z'));helpers[root].append(pos);helper_roots[pos]=root
    pending=collections.defaultdict(list);queue=collections.deque();expanded=[];templates={};published=set()
    def add(pos,piece):
        pos=tuple(pos)
        if pos not in pending:queue.append(pos)
        pending[pos].append(piece)
    for row in rows:
        for piece in row['incomingPieces']:add(row['position'],piece)
    for pos in seeds:
        if pos not in pending:pending[pos]=[];queue.append(pos)
    empty_removed=0;empty_visual_cache={}
    while queue:
        pos=queue.popleft();entry=world.get(pos)
        if entry is None:continue
        name=compound(entry)['Name'].value;root=helper_roots.get(pos,pos) if name==NS+'architecture_part' else pos
        original=world.get(root)
        if original is None:continue
        ident=compound(original)['Name'].value.removeprefix(NS)
        if ident not in definitions:continue
        state={**definitions[ident]['default'],**props(original)};key=statekey(state);text=block_state_key(original)
        if empty_candidates.get(text):
            if text not in empty_visual_cache:
                bs=json.loads((ASSETS/'blockstates'/f'{ident}.json').read_text())
                empty_visual_cache[text]=not any(e.get('faces') for group in applications(bs,state) for e in model(group[0]['model']).get('elements',[]))
            if empty_visual_cache[text]:
                world.put(root,AIR);world.remove_entity(root);empty_removed+=1
                for hp in helpers.get(root,[]):
                    current=world.get(hp)
                    if current is not None and compound(current)['Name'].value==NS+'architecture_part':world.put(hp,AIR);world.remove_entity(hp)
                continue
        if ident not in eligible:continue
        if text not in templates:
            bs=json.loads((ASSETS/'blockstates'/f'{ident}.json').read_text());cells=collections.defaultdict(list)
            for group in applications(bs,state):
                for cell,polys in cells_for_app(json.dumps(group[0],sort_keys=True)).items():cells[cell].extend(polys)
            g=resolved_geometry(geometry,ident,key);parts=[]
            for offset,polys in cells.items():
                collision=g.get('cells',{}).get(','.join(map(str,offset)),{}).get('collision',[])
                semantic=dict(semantics[ident])
                if 'Пустая' in semantic['ru']:semantic['ru']='Архитектурная секция'
                value=palette.piece(polys,collision,semantic,ident,False,definitions[ident]['states'][key][2]);parts.append((offset,value))
            templates[text]=parts
        world.put(root,AIR);world.remove_entity(root)
        for hp in helpers.get(root,[]):
            current=world.get(hp)
            if current is not None and compound(current)['Name'].value==NS+'architecture_part':world.put(hp,AIR);world.remove_entity(hp)
        expanded.append({'position':list(root),'oldState':text,'sections':len(templates[text])})
        if len(expanded)%500==0:print('Expanded roots',len(expanded),'pending cells',len(pending),flush=True)
        for offset,value in templates[text]:
            dst=tuple(root[a]+offset[a] for a in range(3));published.add(value['id'])
            add(dst,{'id':value['id'],'facing':value['properties']['facing'],'root':list(root),'owner':ident,'rootState':text})
    unresolved=[];compositions={};written=0
    for pos,incoming in pending.items():
        if not incoming:continue
        current=world.get(pos)
        if current is None:unresolved.append({'position':list(pos),'protectedBlock':'missing:chunk','incomingPieces':incoming,'category':'missing_chunk'});continue
        name=compound(current)['Name'].value;pieces=list(incoming)
        if name.startswith(NS+'m_'):pieces.append({'id':name.removeprefix(NS),'facing':props(current)['facing']})
        elif name not in AIR_NAMES:
            unresolved.append({'position':list(pos),'protectedBlock':block_state_key(current),'incomingPieces':incoming,'category':'remaining_overlap'});continue
        ck=tuple(sorted(set((p['id'],p['facing']) for p in pieces)))
        if ck not in compositions:
            result,_=compose(palette,[{'id':i,'facing':f} for i,f in ck],source='world_ornament_sections');compositions[ck]=result
        result=compositions[ck];world.put(pos,tag_state(result['id'],result['properties']));written+=1
    print('Saving',len(palette.blocks),'types,',len(world.dirty),'changed chunks',flush=True)
    palette.public.update(published);palette.save();world.save()
    # Technical variants become ordinary cell-local items in the creative palette.
    keep=json.loads((OUT/'legacy-creative.json').read_text());dump(OUT/'legacy-creative.json',[i for i in keep if i not in {parse_state(e['oldState'])[0].removeprefix(NS) for e in expanded}])
    out={'applied':True,'expandedRoots':len(expanded),'removedProvenEmptyRoots':empty_removed,'writtenCells':written,'newVisibleSections':len(published),'expanded':expanded,'changes':list(world.changes.values()),'unresolved':unresolved}
    dump(output or ROOT/'docs/world-ornaments-v2.json',out);print('Expanded',len(expanded),'roots, removed',empty_removed,'empty roots; wrote',written,'cells, unresolved',len(unresolved),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('target',type=Path);parser.add_argument('--all-static',action='store_true');parser.add_argument('--output',type=Path);a=parser.parse_args();expand(a.target.resolve(),a.all_static,a.output)
