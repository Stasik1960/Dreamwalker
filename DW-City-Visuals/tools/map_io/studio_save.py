"""Typed palette and sharded owner edits for a disposable Java 1.20.1 save."""
from collections import defaultdict
import gzip
from pathlib import Path

from .world_io import (NbtFile, RegionFile, Tag, compound, decode_nbt, encode_nbt,
                       invalidate_chunk_lighting, pack_palette_indices, section_blocks)

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
HELPER = "bloodborne_dw:composite_cell"
SHARD_DIR = Path("data/bloodborne_dw_owner_shards")


def list_tag(items=(), kind=10):
    items = list(items)
    return Tag(9, items, kind if items else 0)


def state_tag(name, properties=None):
    fields = {"Name": Tag(8, name)}
    if properties:
        fields["Properties"] = Tag(10, {key: Tag(8, value) for key, value in sorted(properties.items())})
    return Tag(10, fields)


def properties(tag):
    return {key: value.value for key, value in compound(compound(tag).get("Properties", Tag(10, {}))).items()}


def name(tag):
    return compound(tag)["Name"].value


def coordinates(tag):
    return tuple(compound(tag)[axis].value for axis in ("x", "y", "z"))


def shape_tag(blocks, outline):
    def boxes(rows):
        return list_tag((list_tag((Tag(6, coord) for coord in box), 6) for box in rows), 9)
    return Tag(10, {"collision": boxes(blocks), "selection": boxes(outline)})


EMPTY_SHAPE = shape_tag([], [])


def chunk_key(pos):
    return pos[0] // 16, pos[2] // 16


def intern(table, value):
    try:
        return table.index(value)
    except ValueError:
        table.append(value)
        return len(table) - 1


class ChunkEdit:
    def __init__(self, original):
        self.original = original
        self.nbt = original.nbt()
        self.fields = compound(self.nbt.root)
        self.sections = {compound(section)["Y"].value: section for section in self.fields["sections"].value}
        self.entities = {coordinates(be): be for be in self.fields.get("block_entities", list_tag()).value}
        self.palette = {}
        self.changed = defaultdict(dict)
        self.entity_change = False

    def cell(self, pos):
        x, y, z = pos
        height = y // 16
        index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        if height not in self.palette:
            self.palette[height] = section_blocks(self.sections[height]) if height in self.sections else None
        return height, index

    def state(self, pos):
        height, index = self.cell(pos)
        values = self.palette[height]
        return state_tag("minecraft:air") if values is None else values[0][values[1][index]]

    def set_state(self, pos, state):
        height, index = self.cell(pos)
        if self.palette[height] is None:
            if height not in self.sections:
                raise ValueError(f"No saved block section at {pos}")
            self.palette[height] = ([state_tag("minecraft:air")], [0] * 4096)
        self.changed[height][index] = state

    def set_entity(self, pos, tag):
        if tag is None:
            self.entities.pop(pos, None)
        else:
            self.entities[pos] = tag
        self.entity_change = True

    def flush(self, region):
        for height, changes in self.changed.items():
            palette, data = self.palette[height]
            for index, value in changes.items():
                data[index] = intern(palette, value)
            used = sorted(set(data))
            compact = {old: index for index, old in enumerate(used)}
            states = {"palette": list_tag(palette[old] for old in used)}
            if len(used) > 1:
                states["data"] = Tag(12, pack_palette_indices([compact[i] for i in data], len(used)))
            compound(self.sections[height])["block_states"] = Tag(10, states)
        if self.changed:
            self.fields.pop("Heightmaps", None)
            invalidate_chunk_lighting(self.fields)
        if self.entity_change:
            self.fields["block_entities"] = list_tag(self.entities.values())
        if self.changed or self.entity_change:
            region.set_chunk(self.original.x, self.original.z, self.nbt, compression=self.original.compression,
                             timestamp=self.original.timestamp)


class WorldEdit:
    def __init__(self, root):
        self.root = root
        self.regions = {}
        self.chunks = {}

    def region(self, cx, cz):
        path = self.root / "region" / f"r.{cx // 32}.{cz // 32}.mca"
        if path not in self.regions:
            self.regions[path] = RegionFile.open(path)
        return self.regions[path]

    def at(self, pos):
        cx, cz = key = chunk_key(pos)
        if key not in self.chunks:
            original = self.region(cx, cz).get_chunk(cx % 32, cz % 32)
            if original is None:
                raise ValueError(f"No saved chunk at {pos}")
            self.chunks[key] = ChunkEdit(original)
        return self.chunks[key]

    def static_cells(self):
        for path in sorted((self.root / "region").glob("r.*.*.mca")):
            _, rx, rz, _ = path.name.split(".")
            rx, rz = int(rx), int(rz)
            for original in self.region(rx * 32, rz * 32).chunks():
                if b"bloodborne_dw:static_architecture_" not in original.raw_nbt():
                    continue
                cx, cz = rx * 32 + original.x, rz * 32 + original.z
                xbase, zbase = cx * 16, cz * 16
                chunk = self.at((xbase, 0, zbase))
                for height, section in chunk.sections.items():
                    values = chunk.palette.setdefault(height, section_blocks(section))
                    if values is None:
                        continue
                    palette, data = values
                    candidates = {i: static_index(tag) for i, tag in enumerate(palette)
                                  if name(tag).startswith("bloodborne_dw:static_architecture_")}
                    if not candidates:
                        continue
                    for index, value in enumerate(data):
                        if value in candidates:
                            pos = (xbase + (index & 15), height * 16 + (index >> 8), zbase + ((index >> 4) & 15))
                            yield pos, candidates[value], palette[value]

    def flush(self):
        for (cx, cz), chunk in self.chunks.items():
            chunk.flush(self.region(cx, cz))
        for path, region in self.regions.items():
            if region._dirty:
                region.save(path)


def static_index(state):
    identifier = name(state)
    if not identifier.startswith("bloodborne_dw:static_architecture_"):
        return -1
    return int(identifier.rsplit("_", 1)[1]) * 128 + int(properties(state)["part"])


class OwnerShard:
    def __init__(self, path, cx, cz):
        self.path = path
        if path.is_file():
            self.nbt = decode_nbt(path.read_bytes(), compressed="gzip")
        else:
            self.nbt = NbtFile("", Tag(10, {"Schema": Tag(3, 1), "Dictionary": Tag(1, 1),
                    "ChunkX": Tag(3, cx), "ChunkZ": Tag(3, cz)}))
        self.fields = compound(self.nbt.root)
        if self.fields["Dictionary"].value != 1 or self.fields["Schema"].value != 1:
            raise ValueError(f"Unsupported owner shard {path}")
        self.owners, self.data, self.shapes = (self.fields.get(key, list_tag()).value
            for key in ("ownerTable", "dataTable", "shapeTable"))
        self.cells = {tuple(row["pos"].value): row["refs"].value
                      for row in map(compound, self.fields.get("cells", list_tag()).value)}
        self.members = {owner_uuid(self.owners[row["ownerRef"].value]): row
                        for row in map(compound, self.fields.get("ownerMembership", list_tag()).value)}
        self.dirty = False

    def expand(self, refs):
        return [Tag(10, {"owner": self.owners[o], "rootData": self.data[d], **compound(self.shapes[s])})
                for o, d, s in (ref.value for ref in refs)]

    def remove(self, pos, uuids):
        old = self.cells.get(pos, [])
        kept = [row for row in old if owner_uuid(self.owners[row.value[0]]) not in uuids]
        if len(old) != len(kept):
            self.cells[pos] = kept
            self.dirty = True
        return len(old) - len(kept)

    def add(self, pos, owner, data, shape):
        refs = self.cells.setdefault(pos, [])
        refs.append(Tag(11, [intern(self.owners, owner), intern(self.data, data), intern(self.shapes, shape)]))
        if len(refs) > 32:
            raise ValueError(f"Owner limit at {pos}")
        self.dirty = True

    def set_members(self, owner, shapes):
        self.members[owner_uuid(owner)] = {"ownerRef": Tag(3, intern(self.owners, owner)),
            "members": list_tag(Tag(10, {"offset": Tag(11, list(offset)), "shapeRef": Tag(3, intern(self.shapes, shape))})
                                for offset, shape in sorted(shapes.items()))}
        self.dirty = True

    def flush(self):
        if not self.dirty:
            return
        # Remove dead dictionary slots after replacing an ID or moving its root.
        owner_refs = {i for refs in self.cells.values() for i, _, _ in (r.value for r in refs)}
        owner_refs.update(row["ownerRef"].value for row in self.members.values())
        data_refs = {i for refs in self.cells.values() for _, i, _ in (r.value for r in refs)}
        shape_refs = {i for refs in self.cells.values() for _, _, i in (r.value for r in refs)}
        shape_refs.update(compound(m)["shapeRef"].value for row in self.members.values() for m in row["members"].value)
        indices = [{old: new for new, old in enumerate(sorted(refs))}
                   for refs in (owner_refs, data_refs, shape_refs)]
        for row in self.members.values():
            row["ownerRef"] = Tag(3, indices[0][row["ownerRef"].value])
            for tag in row["members"].value:
                fields = compound(tag)
                fields["shapeRef"] = Tag(3, indices[2][fields["shapeRef"].value])
        self.fields["cells"] = list_tag(Tag(10, {"pos": Tag(11, list(pos)), "refs": list_tag(
            (Tag(11, [lookup[index] for lookup, index in zip(indices, ref.value, strict=True)]) for ref in refs), 11)})
            for pos, refs in sorted(self.cells.items()) if refs)
        self.fields["ownerMembership"] = list_tag(Tag(10, row) for row in self.members.values())
        for key, table, mapping in zip(("ownerTable", "dataTable", "shapeTable"),
                                      (self.owners, self.data, self.shapes), indices, strict=True):
            self.fields[key] = list_tag(table[index] for index in mapping)
        self.path.write_bytes(encode_nbt(self.nbt, compressed="gzip"))


def owner_uuid(owner):
    return tuple(compound(owner)["uuid"].value)


def uuid_order(uuid):
    return ((uuid[0], uuid[1] & 0xffffffff), (uuid[2], uuid[3] & 0xffffffff))


def rewrite_be(pos, old, resident, payload, entries):
    fields = {} if old is None else dict(compound(old))
    fields.update({"id": Tag(8, HELPER), **{axis: Tag(3, value) for axis, value in zip("xyz", pos)},
                   "payload": payload})
    if resident is None:
        fields.pop("resident", None)
    else:
        fields["resident"] = resident
    fields.pop("owners", None)
    fields["OwnerDataV1"] = Tag(7, encode_nbt(NbtFile("", Tag(10, {"owners": list_tag(entries)}))))
    return Tag(10, fields)
