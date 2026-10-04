#!/usr/bin/env python3
"""Audit and safely migrate persisted Bloodborne block-state variants."""
from __future__ import annotations
import argparse, copy, hashlib, json, re, shutil, zipfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from world_io import NbtFile, RegionFile, Tag, TAG_COMPOUND, TAG_LIST, TAG_STRING, compound, read_nbt, write_nbt,section_blocks

EXTENSIONS = {'.dat', '.dat_old', '.nbt', '.schem', '.schematic'}
STATE_RE=re.compile(r'(bloodborne_blocks:[a-z0-9_./-]+)\[([^\]\r\n]*)\]')
ITEM_COMMAND_RE=re.compile(r'(bloodborne_blocks:[a-z0-9_./-]+)\{[^\r\n]{0,1024}?BlockStateTag:\s*\{[^}]*?variant:\s*["\']?([0-9]+)')

def _plain(t: Tag) -> Any:
    if t.type == TAG_COMPOUND: return {k: _plain(v) for k, v in t.value.items()}
    if t.type == TAG_LIST: return [_plain(v) for v in t.value]
    return t.value

def _record(refs, counts, ident, variant, category):
    if not isinstance(ident, str) or not ident: return
    variant = str(variant) if variant is not None else None
    if variant is None: return
    refs[ident].add(variant); counts[category] += 1

def _walk(value, refs, counts, category='nbt'):
    if isinstance(value, Tag):
        if value.type == TAG_COMPOUND:
            fields = value.value
            for key in fields:
                if '[' in key and key.endswith(']'):
                    ident, text = key.split('[', 1); props = dict(x.split('=', 1) for x in text[:-1].split(',') if '=' in x)
                    _record(refs, counts, ident, props.get('variant'), 'palette')
            ident = fields.get('Name') or fields.get('id')
            ident = ident.value if ident and ident.type == TAG_STRING else None
            props = fields.get('Properties') or fields.get('properties')
            if props and props.type == TAG_COMPOUND:
                p = props.value.get('variant')
                if p and p.type == TAG_STRING: _record(refs, counts, ident, p.value, category)
            state = fields.get('BlockStateTag')
            tag = fields.get('tag')
            if tag and tag.type == TAG_COMPOUND:
                state = compound(tag).get('BlockStateTag') or state
            if state and state.type == TAG_COMPOUND:
                p = state.value.get('variant')
                if p and p.type == TAG_STRING: _record(refs, counts, ident, p.value, 'items')
            for name,child in fields.items():
                # Placed section palettes have a separate complete packed-index
                # audit. Unused palette slots are not persisted inventory usage.
                if category=='region'and name=='sections':continue
                _walk(child, refs, counts, category)
        elif value.type == TAG_LIST:
            for child in value.value: _walk(child, refs, counts, category)
        elif value.type==TAG_STRING:
            for match in STATE_RE.finditer(value.value):
                p=dict(x.split('=',1)for x in match[2].split(',')if '='in x)
                _record(refs,counts,match[1],p.get('variant'),'string_states')
            for match in ITEM_COMMAND_RE.finditer(value.value):_record(refs,counts,match[1],match[2],'string_items')
    elif isinstance(value, dict):
        for key in value:
            if isinstance(key, str) and '[' in key and key.endswith(']'):
                ident, text = key.split('[', 1); props = dict(x.split('=', 1) for x in text[:-1].split(',') if '=' in x)
                _record(refs, counts, ident, props.get('variant'), 'palette')
        ident = value.get('Name') or value.get('id')
        props = value.get('Properties') or value.get('properties')
        if isinstance(props, dict): _record(refs, counts, ident, props.get('variant'), 'palette')
        if isinstance(value.get('BlockStateTag'), dict): _record(refs, counts, ident, value['BlockStateTag'].get('variant'), 'items')
        for child in value.values(): _walk(child, refs, counts, category)
    elif isinstance(value, list):
        for child in value: _walk(child, refs, counts, category)

def _files(root: Path):
    for p in sorted(root.rglob('*')):
        if p.is_file() and (p.suffix.lower() in EXTENSIONS or p.name.endswith('.dat_old')): yield p
        elif p.is_file() and p.suffix.lower() == '.mca': yield p

def audit(root: str | Path, *, empty_poi_reference=None) -> dict:
    root = Path(root).resolve(); refs = defaultdict(set); counts = Counter(); hashes = {}
    if not root.is_dir():raise ValueError('audit source directory missing')
    allowed=set();reference_proof=None
    if empty_poi_reference:
        reference=Path(empty_poi_reference).resolve();digest=hashlib.sha256(reference.read_bytes()).hexdigest()
        with zipfile.ZipFile(reference)as archive:
            for entry in archive.infolist():
                parts=Path(entry.filename).parts
                if not entry.is_dir()and len(parts)>=3 and parts[-2]=='poi'and entry.file_size==0:
                    allowed.add('/'.join(parts[1:]))
        hashes[str(reference)]=digest
        reference_proof={'sha256':digest,'allowedPaths':sorted(allowed)}
    scanned = 0
    for path in _files(root):
        try:
            if path.suffix.lower() == '.mca':
                if path.stat().st_size:
                    region = RegionFile.open(path)
                    for chunk in region.chunks():
                        nbt=chunk.nbt().root;_walk(nbt, refs, counts, 'region')
                        for section in compound(nbt).get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                            if 'block_states'not in compound(section):continue
                            palette,indices=section_blocks(section)
                            for index in set(indices):_walk(palette[index],refs,counts,'placed_blocks')
                elif path.parent.name!='entities'and path.relative_to(root).as_posix()not in allowed:
                    raise ValueError('zero-byte non-entity region requires verified empty POI permission')
            else: _walk(read_nbt(path).root, refs, counts)
        except Exception as exc:
            raise ValueError(f'malformed input {path}: {exc}') from exc
        scanned += 1; hashes[str(path)] = hashlib.sha256(path.read_bytes()).hexdigest()
    if not scanned:raise ValueError('no persisted files in audit source')
    return {'complete': True, 'scope':'All persisted NBT including used section palette indices', 'emptyPoiReference':reference_proof, 'hashInputs': hashes, 'counts': {'files': scanned, 'references': sum(counts.values()), 'categories': dict(sorted(counts.items()))}, 'variantReferences': {k: sorted(v) for k, v in sorted(refs.items())}}

def _state_key(ident, props): return ident + '[' + ','.join(f'{k}={props[k]}' for k in sorted(props)) + ']'

def migrate_nbt(nbt: NbtFile, migration: dict) -> tuple[NbtFile, list[dict]]:
    migration=_indexed(migration)
    out = copy.deepcopy(nbt); changes = []
    def visit(t):
        if t.type == TAG_COMPOUND:
            f = t.value; ident_tag = f.get('Name') or f.get('id'); ident = ident_tag.value if ident_tag and ident_tag.type == TAG_STRING else None
            palette = f.get('Properties') or f.get('properties')
            if ident and palette and palette.type == TAG_COMPOUND:
                current = {k: v.value for k, v in palette.value.items() if v.type == TAG_STRING}
                target = _lookup(migration, ident, current)
                if target and target != current.get('variant'):
                    palette.value['variant'] = Tag(TAG_STRING, target); changes.append({'id': ident, 'from': current.get('variant'), 'to': target, 'kind': 'palette'})
            state = f.get('BlockStateTag')
            tag = f.get('tag')
            if tag and tag.type == TAG_COMPOUND:
                state = compound(tag).get('BlockStateTag') or state
            if ident and state and state.type == TAG_COMPOUND:
                p = state.value.get('variant'); old = p.value if p and p.type == TAG_STRING else None
                current = {k: v.value for k, v in state.value.items() if v.type == TAG_STRING}
                target = _lookup(migration, ident, current if old is not None else {})
                if target and target != old: state.value['variant'] = Tag(TAG_STRING, target); changes.append({'id': ident, 'from': old, 'to': target, 'kind': 'item'})
            for c in f.values(): visit(c)
        elif t.type == TAG_LIST:
            for c in t.value: visit(c)
    visit(out.root); return out, changes

def _indexed(migration):
    if '_variantIndex'in migration:return migration
    index=defaultdict(list)
    for source,value in migration.get('states',{}).items():
        ident,text=source.split('[',1)
        old=dict(part.split('=',1)for part in text.rstrip(']').split(',')if '='in part)
        if value.get('id')!=ident:raise ValueError('variant migration cannot change block ID')
        index[(ident,old.get('variant'))].append((old,str(value['properties']['variant'])))
    return {'_variantIndex':index}

def _lookup(migration, ident, props):
    matches={target for old,target in migration['_variantIndex'].get((ident,props.get('variant')),())
             if all(str(v)==old.get(k)for k,v in props.items())}
    if len(matches)>1:raise ValueError('ambiguous migration for '+ident)
    return next(iter(matches))if matches else None

def migrate_tree(source, destination, sidecar):
    source, destination = Path(source).resolve(), Path(destination).resolve()
    if destination == source or source in destination.parents: raise ValueError('destination must be separate and outside source')
    if destination.exists(): raise FileExistsError(destination)
    shutil.copytree(source, destination); result=[]
    migration=_indexed(json.loads(Path(sidecar).read_text(encoding='utf-8')))
    for p in _files(destination):
        if p.suffix.lower()=='.mca':
            if not p.stat().st_size:continue
            region=RegionFile.open(p)
            changed=False
            for c in region.chunks():
                n, ch=migrate_nbt(c.nbt(), migration); result += ch
                if ch:
                    region.set_chunk(c.x,c.z,n,compression=c.compression & 0x7f,timestamp=c.timestamp);changed=True
            if changed:p.write_bytes(region.to_bytes())
        else:
            n=read_nbt(p); n,ch=migrate_nbt(n,migration); result += ch
            if ch: write_nbt(p,n,compressed='gzip' if p.read_bytes()[:2]==b'\x1f\x8b' else None)
    return result

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('root',type=Path); ap.add_argument('--output',type=Path);ap.add_argument('--empty-poi-reference',type=Path); a=ap.parse_args(); report=audit(a.root,empty_poi_reference=a.empty_poi_reference); (a.output or a.root/'persisted-variant-usage.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8'); print(json.dumps(report['counts']))
if __name__ == '__main__': main()
