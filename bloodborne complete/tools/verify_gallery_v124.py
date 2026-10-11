"""Independent saved-world proof for the visual-sized V12.4 gallery.

Preserves all previous typed ownership/frame/world checks, then verifies the
new per-size grids and support plane against saved mount metadata and actual
model dependencies in the exact production JAR. Inputs remain read-only.
"""
from __future__ import annotations
from collections import defaultdict
import itertools
import inspect
import json
import math
from pathlib import Path
import struct
import sys
import zipfile
import verify_flat_gallery as legacy
from world_io import RegionFile,Tag,compound
from prepare_gallery_v124_input import ArchiveGeometry,composite_visual,native_visual
from gallery_platform_plan import rotate

EPS=1e-5

def near(a,b,message):
    legacy.require(len(a)==len(b) and all(abs(x-y)<EPS for x,y in zip(a,b)),message)

def grid(rows):
    slots=defaultdict(dict);rectangles=[];horizontal=vertical=0
    for row in rows:
        legacy.require(row['section']!='error' and row['kind']!='diagnostic','Removed red object returned')
        x,z,w,d=legacy.platform(row);bounds=row['bounds'];local=row['localVisualBounds']
        legacy.require(w==math.ceil(local[3]-local[0]-1e-8)+2 and d==math.ceil(local[5]-local[2]-1e-8)+2,'Platform is larger/smaller than rendered geometry +2: '+row['key'])
        clearance=row.get('surfaceClearance',0)
        legacy.require(clearance in (0,1/1024),'Unexpected display clearance')
        legacy.require(not clearance or abs(local[4]-local[1])<1e-8,'Only zero-Y-thickness art can use anti-z-fighting lift')
        legacy.require(abs(bounds[1]-64-clearance)<EPS,'Rendered object floats above/is buried below platform: '+row['key'])
        legacy.require(bounds[0]>=x-EPS and bounds[3]<=x+w+EPS and bounds[2]>=z-EPS and bounds[5]<=z+d+EPS,'Rendered object leaves platform: '+row['key'])
        legacy.require(row.get('renderedBottomOnPlatform'),'Missing explicit visual support proof')
        label=row.get('labelPos')
        legacy.require(label and label[1]==64 and label[0] in (x,x+w-1) and label[2] in (z,z+d-1) and label!=row['root'],'Label is not a separate platform corner: '+row['key'])
        legacy.require(label[0]+1<=bounds[0]+EPS or label[0]>=bounds[3]-EPS or label[2]+1<=bounds[2]+EPS or label[2]>=bounds[5]-EPS,'Label cell overlaps rendered model: '+row['key'])
        key=(row['row'],row['column']);legacy.require(key not in slots[row['gridId']],'Repeated grid slot')
        slots[row['gridId']][key]=row;rectangles.append((x,z,x+w,z+d,row['key']))
    for grid_id,values in slots.items():
        for (r,c),row in values.items():
            x,z,w,d=legacy.platform(row)
            right=values.get((r,c+1));below=values.get((r+1,c))
            if right:
                rx,rz,rw,rd=legacy.platform(right);legacy.require(rx-(x+w)==2 and rz==z and rw==w and rd==d,'Horizontal 2-cell gap differs');horizontal+=1
            if below:
                bx,bz,bw,bd=legacy.platform(below);legacy.require(bz-(z+d)==2 and bx==x and bw==w and bd==d,'Vertical 2-cell gap differs');vertical+=1
    rectangles.sort()
    for i,a in enumerate(rectangles):
        for b in rectangles[i+1:]:
            if b[0]>=a[2]+2:break
            legacy.require(a[2]+2<=b[0] or b[2]+2<=a[0] or a[3]+2<=b[1] or b[3]+2<=a[1],'Platform grids overlap/lose 2-cell gap: '+a[4]+' / '+b[4])
    return {'status':'PASS','horizontalNeighbors':horizontal,'verticalNeighbors':vertical,
            'uniformSizeGrids':len(slots),'renderSizedPlatforms':len(rows),'renderedBottomOnPlatform':len(rows),
            'gridSlots':{key:len(value) for key,value in slots.items()}}

def float32(value):return struct.unpack('f',struct.pack('f',value))[0]

def model_rp_bounds(row,data,archive):
    identifier=row['registry'].split(':')[1];catalog=json.loads(archive.read('assets/bloodborne_rp/catalog.json'));spec=catalog[identifier]
    model=json.loads(archive.read('assets/bloodborne_rp/'+spec['model']))
    bones=model['minecraft:geometry'][0]['bones'];lookup={b['name']:b for b in bones}
    factor=float32(spec['scale'])*data.get('Scale',1);yaw,pitch=data['Rotation'];position=data['Pos'];points=[]
    def mirror(point):return [-point[0],point[1],point[2]]
    def around(point,node):
        pivot=mirror(node.get('pivot',[0,0,0]));angles=node.get('rotation',[0,0,0])
        point=rotate(point,0,-angles[0],pivot);point=rotate(point,1,-angles[1],pivot);return rotate(point,2,angles[2],pivot)
    for bone in bones:
        for cube in bone.get('cubes',[]):
            origin=cube.get('origin',[0,0,0]);size=cube.get('size',[0,0,0]);inflate=cube.get('inflate',0)
            edges=[(origin[i]-inflate,origin[i]+size[i]+inflate) for i in range(3)]
            transformed=[]
            for point in itertools.product(*edges):
                point=around(mirror(point),cube);parent=bone;visited=set()
                while parent:
                    legacy.require(parent['name'] not in visited,'Cyclic RP bones');visited.add(parent['name']);point=around(point,parent);parent=lookup.get(parent.get('parent'))
                transformed.append([v/16 for v in point])
            # Java ordinaryVisualBoxes rotates each transformed cube AABB.
            box=[min(p[i] for p in transformed) for i in range(3)]+[max(p[i] for p in transformed) for i in range(3)]
            if identifier=='ladder' and bone['name']=='bottom':
                if not data.get('Open',False):dy,dz=228/16,-2/16
                else:
                    tick=48-data.get('LadderDeployTicks',0)
                    dy=(0 if tick>=48 else 228+(181-228)*tick/20 if tick<=20 else 181*(46.4-tick)/26.4 if tick<=46.4 else 3*(tick-46.4)/.8 if tick<=47.2 else 3*(48-tick)/.8)/16
                    dz=(-2 if tick<=46.4 else -2+(tick-46.4)/.8 if tick<=47.2 else -(48-tick)/.8)/16
                box[1]+=dy;box[4]+=dy;box[2]+=dz;box[5]+=dz
            for point in itertools.product(*[(box[i],box[i+3]) for i in range(3)]):
                point=[v*factor for v in point];point=rotate(point,2,pitch,[0,0,0]);point=rotate(point,1,yaw-90,[0,0,0]);points.append([point[i]+position[i] for i in range(3)])
    legacy.require(points,'Empty RP model')
    return [min(point[i] for point in points) for i in range(3)]+[max(point[i] for point in points) for i in range(3)]

def visual_saved(world,manifest,jar):
    roots={};entities={};signs={}
    for path in sorted((world/'region').glob('r.*.*.mca')):
        for chunk in RegionFile.open(path).chunks():
            for tag in compound(chunk.nbt().root).get('block_entities',Tag(9,[],10)).value:
                value=compound(tag)
                if 'resident' in value:roots[tuple(value[k].value for k in ('x','y','z'))]=legacy.plain(tag)
                if value.get('id',Tag(8,'' )).value=='minecraft:sign':signs[tuple(value[k].value for k in ('x','y','z'))]=legacy.plain(tag)
    for path in sorted((world/'entities').glob('r.*.*.mca')):
        if path.stat().st_size:
            for chunk in RegionFile.open(path).chunks():
                for tag in compound(chunk.nbt().root).get('Entities',Tag(9,[],10)).value:entities[legacy.entity_uuid(tag)]=legacy.plain(tag)
    architecture=rp=frames=0
    with zipfile.ZipFile(jar) as archive:
        models=ArchiveGeometry(archive,Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
        for row in manifest['exhibits']:
            sign=signs[tuple(row['labelPos'])]
            legacy.require(sign['front_text']==sign['back_text'],'Saved Russian label differs between sides: '+row['key'])
            if row['kind']=='architecture':
                root=row['root'];payload=roots[tuple(root)]['payload'];path='bloodborne_dw/composite/'+row['registry'].split(':')[1]+'.json'
                if path in archive.namelist():
                    spec=json.loads(archive.read(path));local=composite_visual(spec,row,models);shift=payload.get('SourceShift',[0,0,0])
                else:local=native_visual(row,archive,models);shift=[0,0,0]
                near(local,row['localVisualBounds'],'Manifest visual model bounds differ from production JAR: '+row['key'])
                mount=payload.get('MountY',0)+payload.get('VerticalOffset',0)
                origin=[root[0]+shift[0],root[1]+shift[1]+mount,root[2]+shift[2]]
                bounds=[local[i]+origin[i] for i in range(3)]+[local[i+3]+origin[i] for i in range(3)]
                near(bounds,row['bounds'],'Saved mount does not put rendered model on platform: '+row['key']);architecture+=1
            elif row['kind']=='rp_object':
                bounds=model_rp_bounds(row,entities[row['uuid']],archive)
                near(bounds,row['bounds'],'Saved RP geometry is floating/buried or differs from exact model: '+row['key']);rp+=1
            elif row['kind']=='item_frame':
                frame=entities[row['uuid']];support=row['frameSupport']
                legacy.require(frame['Facing']==2 and frame['Fixed'] and frame['Item']['id']==row['item'],'Frame orientation/item differs')
                near(frame['Pos'],[support[0]+.5,support[1]+.5,support[2]-.03125],'Frame is not attached to planned pedestal')
                frames+=1
            else:raise ValueError('Unsupported current exhibit kind')
    return {'status':'PASS','architectureModelAndSavedMounts':architecture,'rpModelAndSavedPoses':rp,
            'attachedFramedItems':frames,'savedRussianIdLabelsBothSides':len(manifest['exhibits']),'renderedBottomY':64,'planarAntiZFightClearance':1/1024,
            'planarExhibits':[row['key'] for row in manifest['exhibits'] if row.get('surfaceClearance',0)>0],
            'geometrySource':'Exact production model elements/bones + saved typed mount/Pos; no physics or selection envelope'}

def functional_catalogue(manifest,jar):
    """Verify working types from the shipped registrations, not a copied count."""
    with zipfile.ZipFile(jar) as archive:
        aliases=json.loads(archive.read('bloodborne_dw/imported-type-aliases.json'))
        remaining=json.loads(archive.read('bloodborne_dw/remaining-catalogue.json'))
        debug=json.loads(archive.read('bloodborne_dw/debug_catalogue.json'))
        stairs=json.loads(archive.read('bloodborne_dw/imported-stair-types.json'))
    retired={member for group in aliases['groups'] for member in group['members'] if member!=group['primaryRegistryId']}
    expected={'bloodborne_dw:'+row['path'] for row in remaining['objects']}-retired
    rows=[row for row in manifest['exhibits'] if row.get('origin')!='V12' and row['kind']=='architecture']
    actual=[row['registry'] for row in rows]
    legacy.require(len(actual)==len(set(actual)),'A NEW working type appears on multiple main platforms')
    legacy.require(set(actual)==expected,'Gallery working types differ from final production catalogue')
    legacy.require(all(row['registry'] not in retired for row in manifest['exhibits']),'Hidden compatibility adapter is displayed as a working type')
    numbers={row['registryId']:row['temporaryId'] for row in debug['entries']}
    stair_types={'bloodborne_dw:'+row['path'] for row in stairs['stairTypes']}
    for row in rows:
        legacy.require(row['item']==row['registry'] and row['id']==numbers[row['registry']],'Wrong canonical item/numeric ID')
        state=row['placedState']
        legacy.require(state.get('variant')=='0' and state.get('rotation')=='0','NEW main platform does not use its ordinary canonical pose')
        if row['registry'] in stair_types:
            legacy.require(state['variant']=='0' and state.get('connections')=='auto','Main native stair is not a normally connected lower straight stair')
    original=legacy.read(Path(__file__).resolve().parents[1]/'docs/gallery-v12.1/catalogue.json')
    frozen={row['key']:(row['registry'],row['item'],row['id']) for row in original['exhibits'] if row['origin']=='V12' and row['section']!='error'}
    retained={row['key']:(row['registry'],row['item'],row['id']) for row in manifest['exhibits'] if row.get('origin')=='V12' and row['kind']!='item_frame'}
    legacy.require(frozen==retained,'Original V12 gallery types/IDs/items were changed')
    for row in manifest['exhibits']:
        if row['registry'].startswith('bloodborne_dw:prototype_wall') and row['registry']!='bloodborne_dw:prototype_wall_skin_1':
            legacy.require(row['placedState']['connections']=='auto','Original V12 wall connections were forced to manual in the gallery')
    return {'status':'PASS','newWorkingTypes':len(rows),'oneMainStationPerNewWorkingType':True,
            'hiddenCompatibilityTypesExcluded':len(retired),'originalV12BuildingTypesRetained':len(frozen),
            'nativeWallPlacementConnections':'AUTO (original diagonal-post native exception retained)'}


def main():
    args=sys.argv;world=Path(args[args.index('--world')+1]);report_path=Path(args[args.index('--report')+1]);manifest=legacy.read(Path(args[args.index('--manifest')+1]));jar=Path(args[args.index('--production-jar')+1])
    legacy.require(manifest['schema']=='dreamwalker-flat-gallery-v124','Wrong gallery generation')
    planar=[row['key'] for row in manifest['exhibits'] if row.get('surfaceClearance',0)>0]
    legacy.require(sorted(planar)==sorted(manifest['planarExhibits']) and manifest['planarAntiZFightClearance']==1/1024,'Planar anti-z-fighting exception differs from the exact final-production source planes')
    # Preserve established typed/NBT checks while reading the new explicit safe label position.
    saved_main=inspect.getsource(legacy.main)
    before="sign = signs.get((px, floor_y + 1, pz))"
    legacy.require(before in saved_main,'Legacy label check changed; review adapter')
    saved_main=saved_main.replace(before,"sign = signs.get(tuple(row['labelPos']))")
    exec(compile(saved_main,str(Path(legacy.__file__)),'exec'),legacy.__dict__)
    legacy.grid=grid;legacy.main()
    report=legacy.read(report_path);report['schema']='dreamwalker-flat-gallery-v124-disk-verification';report['savedVisualSupport']=visual_saved(world,manifest,jar)
    report['functionalCatalogue']=functional_catalogue(manifest,jar)
    report['removedRedExhibits']=len(manifest['removedRedExhibits']);legacy.write(report_path,report)

if __name__=='__main__':
    try:main()
    except Exception as failure:
        if '--report' in sys.argv:legacy.write(Path(sys.argv[sys.argv.index('--report')+1]),{'schema':'dreamwalker-flat-gallery-v124-disk-verification','status':'FAIL','failure':repr(failure)})
        raise
