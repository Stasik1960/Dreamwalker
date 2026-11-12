"""Append exact native backgrounds after removing proven grass quads."""
from collections import defaultdict
import hashlib
import json
import shutil
import struct
from zipfile import ZIP_DEFLATED, ZipFile

from probe_bush import JAR, NATIVE


def encode_json(data):
    return json.dumps(data,ensure_ascii=False,separators=(',',':'),sort_keys=True).encode()


class BushNativeCatalog:
    def __init__(self):
        with ZipFile(JAR) as jar:
            source=jar.read(NATIVE+'catalog.json')
            self.original_hash=hashlib.sha256(source).hexdigest()
            self.catalog=json.loads(source)
            self.layout=json.loads(jar.read(NATIVE+'visual_physics.json'))
            self.repairs=json.loads(jar.read(NATIVE+'visual-repairs.json'))
            self.flags=jar.read('bloodborne_dw/editor2-native-flags.bin')
            self.bounds=jar.read(NATIVE+'selection-bounds.bin')
        assert self.layout['catalogSha256']==self.repairs['catalogSha256']==self.original_hash
        self.cells=self.catalog['cells']
        self.meshes=self.catalog['meshes']
        self.wholes={c['cell'] for o in self.catalog['wholeObjects'] for c in o['cells']}
        self.base_count=len(self.cells)
        self.mesh_count=len(self.meshes)
        self.split_ids={}
        self.new_mesh_bytes={}
        self.extra_flags=[]
        self.mesh_lookup={}
        self.shape_lookup={tuple(map(tuple,boxes)):i for i,boxes in enumerate(self.catalog['shapes'])}
        self.retained={}
        self.records={}

    def load_meshes(self, source_indices):
        needed={self.cells[i]['mesh'] for i in source_indices}
        with ZipFile(JAR) as jar, jar.open(NATIVE+'meshes.bin') as stream:
            assert struct.unpack('>II',stream.read(8))==(0x4e475131,self.mesh_count)
            for index,entry in enumerate(self.meshes):
                count,=struct.unpack('>I',stream.read(4))
                assert count==entry['quadCount']
                raw=stream.read(139*count)
                assert len(raw)==139*count
                if index in needed:
                    self.records[index]=tuple(raw[i:i+139] for i in range(0,len(raw),139))
            assert not stream.read(1)

    def bridge_ready(self,index):
        cell=self.cells[index]
        return (index not in self.wholes and cell.get('alt') is None and cell['fluid'] is None
                and cell['optics']==0 and cell['localFunction'] is None and not cell['climbing'])

    def can_split(self,index):
        cell=self.cells[index]
        return index not in self.wholes and cell.get('alt') is None and str(index) not in self.repairs['visuals']

    def empty_after(self,index,removed):
        cell=self.cells[index]
        return (len(removed)==self.meshes[cell['mesh']]['quadCount'] and
                cell['collision']==cell['culling']==0 and self.bridge_ready(index) and
                cell['baseState'] is None)

    def outline(self,cell,remaining):
        # Keep every old outline box which touches retained geometry, plus all
        # collision boxes. Brush-only outline boxes have no independent role.
        vertices=[tuple(struct.unpack_from('>3f',raw,11+32*i) for i in range(4))
                  for raw in remaining]
        bounds=[tuple(f(v[axis] for v in quad) for f in (min,max) for axis in range(3))
                for quad in vertices]
        def touching(a,b):
            return all(min(a[i+3],b[i+3])-max(a[i],b[i])>=-0.004 for i in range(3))
        shapes=self.catalog['shapes']
        boxes=set(map(tuple,shapes[cell['collision']]))
        for box in shapes[cell['outline']]:
            if any(touching(box,quad) for quad in bounds):
                boxes.add(tuple(box))
        key=tuple(sorted(boxes))
        if key not in self.shape_lookup:
            self.shape_lookup[key]=len(shapes)
            shapes.append(key)
        return self.shape_lookup[key]

    def split(self,index,removed):
        key=(index,tuple(sorted(removed)))
        if not removed:
            return index
        if key in self.split_ids:
            return self.split_ids[key]
        assert self.can_split(index)
        old=self.cells[index]
        raw=self.records[old['mesh']]
        assert all(0<=i<len(raw) for i in removed)
        retained=tuple(q for i,q in enumerate(raw) if i not in removed)
        blob=b''.join(retained)
        mesh=self.mesh_lookup.get(blob)
        if mesh is None:
            mesh=len(self.meshes)
            self.mesh_lookup[blob]=mesh
            self.meshes.append({'quadCount':len(retained)})
            self.new_mesh_bytes[mesh]=blob
            self.extra_flags.append(self.flags[old['mesh']] if retained else 0)
        else:
            self.extra_flags[mesh-self.mesh_count]|=self.flags[old['mesh']] if retained else 0
        next_cell=dict(old)
        next_cell['mesh']=mesh
        next_cell['outline']=self.outline(old,retained)
        next_cell['key']=hashlib.sha256(encode_json(['bush-residue-v1',old['key'],key[1]])).hexdigest()
        new_index=len(self.cells)
        self.cells.append(next_cell)
        self.layout['cellToPhysics'].append(self.layout['cellToPhysics'][index])
        self.split_ids[key]=new_index
        self.retained[new_index]=(index,tuple(sorted(removed)))
        return new_index

    def package(self,output):
        catalog=encode_json(self.catalog)
        next_hash=hashlib.sha256(catalog).hexdigest()
        self.layout['catalogSha256']=self.repairs['catalogSha256']=next_hash
        self.layout['compatibleCatalogs']=sorted(set(self.layout['compatibleCatalogs'])|{self.original_hash})
        assert len(self.flags)==self.mesh_count
        assert self.bounds[:6]==b'NGS1\x00\x40' and self.bounds[6:70].decode()==self.original_hash
        replacements={
            NATIVE+'catalog.json':catalog,
            NATIVE+'visual_physics.json':encode_json(self.layout),
            NATIVE+'visual-repairs.json':encode_json(self.repairs),
            NATIVE+'selection-bounds.bin':self.bounds[:6]+next_hash.encode()+self.bounds[70:],
            'bloodborne_dw/editor2-native-flags.bin':self.flags+bytes(self.extra_flags)
        }
        with ZipFile(JAR) as source,ZipFile(output,'x',ZIP_DEFLATED,compresslevel=4) as target:
            for basename in ('native-benches.json','native-windows.json'):
                path='bloodborne_dw/repair/'+basename
                raw=source.read(path)
                assert self.original_hash.encode() in raw
                replacements[path]=raw.replace(self.original_hash.encode(),next_hash.encode())
            for entry in source.infolist():
                name=entry.filename
                if name==NATIVE+'meshes.bin':
                    with source.open(entry) as input_stream,target.open(name,'w') as output_stream:
                        assert struct.unpack('>II',input_stream.read(8))==(0x4e475131,self.mesh_count)
                        output_stream.write(struct.pack('>II',0x4e475131,len(self.meshes)))
                        shutil.copyfileobj(input_stream,output_stream,1024*1024)
                        for mesh,blob in self.new_mesh_bytes.items():
                            output_stream.write(struct.pack('>I',self.meshes[mesh]['quadCount']))
                            output_stream.write(blob)
                elif name in replacements:
                    target.writestr(name,replacements[name])
                else:
                    with source.open(entry) as input_stream,target.open(entry,'w') as output_stream:
                        shutil.copyfileobj(input_stream,output_stream,1024*1024)
        return next_hash
