"""Merge a remapped overlay into pinned v22, retaining untouched compressed records."""
import argparse, hashlib, io, json, struct, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'baseline/mod.jar'
EXPECTED = '1f2e71c6203a9b3794d9c3cf6cf6cbe6e0a93a750584dc9df967bd2cd166e621'
def sha(b): return hashlib.sha256(b).hexdigest()
def records(blob):
    z = zipfile.ZipFile(io.BytesIO(blob))
    if z.testzip(): raise ValueError('ZIP CRC failure')
    infos = z.infolist()
    if len({i.filename for i in infos}) != len(infos): raise ValueError('Duplicate ZIP entries')
    if len(infos) >= 65535: raise ValueError('ZIP64 unsupported')
    result = {}
    p = z.start_dir
    for i in infos:
        if blob[p:p+4] != b'PK\x01\x02': raise ValueError('Invalid central directory')
        nl, el, cl = struct.unpack_from('<HHH', blob, p+28)
        size = 46+nl+el+cl
        result[i.filename] = [i, blob[p:p+size], None, z.read(i)]
        p += size
    ordered = sorted(infos, key=lambda i:i.header_offset)
    for n,i in enumerate(ordered):
        end = ordered[n+1].header_offset if n+1 < len(ordered) else z.start_dir
        result[i.filename][2] = blob[i.header_offset:end]
    return result

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--base', type=Path, default=BASE)
    ap.add_argument('--overlay', type=Path)
    ap.add_argument('--output', type=Path)
    ap.add_argument('--inventory-only', action='store_true')
    ap.add_argument('--receipt', type=Path, default=ROOT/'package-receipt.json')
    ap.add_argument('--entrypoint', action='append', default=[], metavar='SIDE=CLASS')
    ap.add_argument('--mixin', action='append', default=[], metavar='RESOURCE.json')
    ap.add_argument('--replace-entry', action='append', default=[], help='Explicit existing resource replacement; protected runtime is always forbidden')
    args = ap.parse_args()
    raw = args.base.read_bytes()
    if sha(raw) != EXPECTED: raise ValueError('Baseline SHA256 mismatch')
    old = records(raw)
    if args.inventory_only:
        inventory = {'base':str(args.base),'sha256':sha(raw),'entries':{n:{'sha256':sha(v[3]),'compressedRecordSha256':sha(v[2]),'crc':v[0].CRC,'size':v[0].file_size} for n,v in old.items()}}
        args.receipt.write_text(json.dumps(inventory, indent=2)+'\n')
        print(str(args.receipt)); return
    if not args.overlay or not args.output: ap.error('--overlay and --output required')
    if args.output.resolve() in (args.base.resolve(), args.overlay.resolve()): raise ValueError('Output must be new file')
    if args.output.exists(): raise ValueError('Output already exists')
    new = records(args.overlay.read_bytes())
    meta = json.loads(old['fabric.mod.json'][3])
    overlay_meta = json.loads(new.pop('fabric.mod.json')[3]) if 'fabric.mod.json' in new else {}
    new.pop('META-INF/MANIFEST.MF',None)
    all_names = old.keys() | new.keys()
    def add_entry(side, entry):
        klass = entry if isinstance(entry,str) else entry['value']
        if klass.replace('.','/')+'.class' not in all_names: raise ValueError('Missing entrypoint class '+klass)
        values = meta.setdefault('entrypoints',{}).setdefault(side,[])
        if entry not in values: values.append(entry)
    for side, entries in overlay_meta.get('entrypoints',{}).items():
        for entry in entries: add_entry(side,entry)
    for spec in args.entrypoint:
        side, entry = spec.split('=',1); add_entry(side,entry)
    for mix in overlay_meta.get('mixins',[]) + args.mixin:
        name = mix if isinstance(mix,str) else mix['config']
        if name not in all_names: raise ValueError('Missing mixin resource '+name)
        data = json.loads((new.get(name) or old[name])[3])
        refmap = data.get('refmap')
        if refmap and refmap not in all_names: raise ValueError('Missing mixin refmap '+refmap)
        if mix not in meta.setdefault('mixins',[]): meta['mixins'].append(mix)
    meta['version'] += '-' + overlay_meta.get('custom', {}).get('dreamwalker:revisionSuffix', 'altvisual.8')
    # Existing IDs, dependencies, old entrypoint order, mixins and refmaps stay intact.
    replacements = set(args.replace_entry)
    for name in new.keys() & old.keys():
        if new[name][3] == old[name][3]: continue
        protected = name.endswith('.class') or 'refmap' in name.lower() or 'mixins' in name.lower() or name == 'native-visual.accesswidener' or name.startswith('META-INF/jars/')
        if protected or name not in replacements: raise ValueError('Forbidden baseline replacement: '+name)
    changes = {n:v for n,v in new.items() if n not in old or v[3] != old[n][3]}
    data = io.BytesIO()
    with zipfile.ZipFile(data,'w',compression=zipfile.ZIP_DEFLATED) as z:
        i=zipfile.ZipInfo('fabric.mod.json',(1980,1,1,0,0,0)); i.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(i,json.dumps(meta,indent=2,ensure_ascii=False)+'\n')
    changes.update(records(data.getvalue()))
    merged = dict(old); merged.update(changes)
    out = bytearray(); central = bytearray()
    for name, val in merged.items():
        i,c,local,_ = val
        if len(out) >= 0xffffffff: raise ValueError('ZIP64 unsupported')
        c=bytearray(c); struct.pack_into('<I',c,42,len(out))
        out.extend(local); central.extend(c)
    offset=len(out); out.extend(central)
    out.extend(struct.pack('<4s4H2IH',b'PK\x05\x06',0,0,len(merged),len(merged),len(central),offset,0))
    check=records(bytes(out))
    unchanged=[n for n in old if n not in changes]
    assert all(check[n][2] == old[n][2] and check[n][3] == old[n][3] for n in unchanged)
    assert set(old).issubset(check)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('xb') as f: f.write(out)
    receipt={'baseSha256':sha(raw),'overlaySha256':sha(args.overlay.read_bytes()),'output':str(args.output),'outputSha256':sha(out),'version':meta['version'],'unchangedRawRecords':len(unchanged),'changedEntries':sorted(changes),'crcCheck':'PASS','oldEntriesRetained':True}
    args.receipt.write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))
if __name__ == '__main__': main()
