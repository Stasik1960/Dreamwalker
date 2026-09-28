"""Project authored contract primitives into legacy per-cell storage, not mesh elements."""
import copy
import itertools
import json
import math
from pathlib import Path

from logical_contract_v2 import cell as checked_cell, check_box

ROOT=Path(__file__).resolve().parents[1]
LOGICAL=ROOT/'src/main/resources/bloodborne_blocks/logical'


def clip_cell(boxes, cell):
    """Existing storage projection; selection may intentionally extend beyond ownership."""
    result=[]
    for box in boxes:
        clipped=[max(0,box[i]-cell[i]) for i in range(3)]+[min(1,box[i+3]-cell[i]) for i in range(3)]
        if all(clipped[i]<clipped[i+3] for i in range(3)):result.append(clipped)
    return result


def split_collision(boxes, cells):
    """Split only within an explicit mask, proving a partition of every input box.

    Every slice is an intersection with a disjoint unit cell. Exact coverage of
    each original box proves preservation of their union, also for overlapping
    primitives. Empty collision never removes an interaction/ownership cell.
    No mesh, selection, anchor or source identity participates in this operation.
    """
    if cells is None:raise ValueError('UNPROVEN_PHYSICAL_MASK')
    mask=[checked_cell(c) for c in cells]
    if not mask or len(mask)!=len(set(mask)) or len(mask)>512:
        raise ValueError('INVALID_PHYSICAL_MASK')
    owned=set(mask)
    for box in boxes:
        check_box(box)
        # Do not round away a positive sliver outside the declared mask.
        spans=[range(math.floor(box[i]),math.ceil(box[i+3])) for i in range(3)]
        if math.prod(len(span) for span in spans)>len(mask):
            raise ValueError('COLLISION_OUTSIDE_PHYSICAL_MASK: box='+str(box))
        outside=set(itertools.product(*spans))-owned
        if outside:raise ValueError('COLLISION_OUTSIDE_PHYSICAL_MASK: cells='+str(sorted(outside)))
        slices=[b for c in mask for b in clip_cell([box],c)]
        before=math.prod(box[i+3]-box[i] for i in range(3))
        after=math.fsum(math.prod(b[i+3]-b[i] for i in range(3)) for b in slices)
        if not math.isclose(before,after,rel_tol=1e-12,abs_tol=1e-12):
            raise ValueError('COLLISION_VOLUME_NOT_CONSERVED')
    return {','.join(map(str,c)):clip_cell(boxes,c) for c in mask}


def normalize_collision_cells(state, approved_cells=None, *, protected=False):
    """Conservative normalization of the existing GeometryState storage format.

    A caller must supply independent mask authority; compiled cells alone do not
    authorize a repair. Protected/unproved/outside-mask states remain untouched.
    The caller records the returned reason together with the exact ID/state key.
    """
    if protected:return state, 'PROTECTED'
    if approved_cells is None:return state, 'UNPROVEN_PHYSICAL_MASK'
    try:
        names=state.get('cells',{})
        mask={','.join(map(str,checked_cell(c))) for c in approved_cells}
        if set(names)!=mask:raise ValueError('APPROVED_MASK_DIFFERS_FROM_EXISTING_CELLS')
        global_boxes=[]
        for name,data in names.items():
            c=tuple(map(int,name.split(',')))
            for box in data['collision']:
                check_box(box)
                global_boxes.append([v+c[i%3] for i,v in enumerate(box)])
        split=split_collision(global_boxes,approved_cells)
    except (ValueError,KeyError,TypeError) as error:return state,str(error)
    if all(data['collision']==split[name] for name,data in names.items()):return state,'ALREADY_CELL_LOCAL'
    result=copy.deepcopy(state)
    for name,boxes in split.items():result['cells'][name]['collision']=boxes
    return result,'NORMALIZED'


def profile(family):
    result={}
    for key,state in family['states'].items():
        try:collision=split_collision(state['collision_footprint']['boxes'],state['interaction_footprint']['cells'])
        except ValueError as error:raise ValueError(f"{family['id']}[{key}]: {error}") from error
        cells={}
        for cell in state['interaction_footprint']['cells']:
            name=','.join(map(str,cell))
            cells[name]={'outline':clip_cell(state['selection_footprint']['boxes'],cell),'collision':collision[name]}
        result[key]={'cells':cells,'anchor':family['canonical_anchor']['cell'],
                     'render_offset':state['render_mesh']['offset'],
                     'globalOutline':state['selection_footprint']['boxes'][0]}
    return {'states':result}


def build():
    path=LOGICAL/'geometry.json';data=json.loads(path.read_text())
    for family in json.loads((LOGICAL/'contracts-v2.json').read_text())['families']:
        if family.get('authority')=='user':data['blocks'][family['id']]=profile(family)
    path.write_text(json.dumps(data,separators=(',',':'),sort_keys=True)+'\n',encoding='utf8')


if __name__=='__main__':build()
