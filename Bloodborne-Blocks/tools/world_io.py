#!/usr/bin/env python3
"""Lossless-enough Minecraft Java NBT/Anvil helpers for offline world tooling.

The module has no third-party dependencies.  It preserves NBT tag types and
compound insertion order, keeps untouched region sectors byte-for-byte, and
uses the post-1.16 non-spanning paletted-container layout used by Minecraft
1.20.1.
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import struct
import zlib
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, MutableMapping, Sequence


TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


@dataclass(eq=True)
class Tag:
    """A typed NBT value. ``list_type`` is meaningful only for TAG_LIST."""

    type: int
    value: Any
    list_type: int | None = None


@dataclass(eq=True)
class NbtFile:
    name: str
    root: Tag


class NbtError(ValueError):
    pass


def invalidate_chunk_lighting(root: MutableMapping[str, Tag]) -> bool:
    """Request a complete relight after edits, for both vanilla and Starlight.

    Starlight 1.1.2 tests the PRESENCE of isLightOn, not its boolean value,
    together with starlight.light_version. Leaving those markers after deleting
    nibble arrays makes state=2 load null data and throws during spawn loading.
    Remove the complete cache contract, including sections outside build height.
    Heightmaps, block data and mod/entity NBT are intentionally not touched here.
    """
    changed = False
    for key in ('isLightOn', 'starlight.light_version'):
        if key in root:
            del root[key]
            changed = True
    for section in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        fields = compound(section)
        for key in ('BlockLight', 'SkyLight', 'starlight.blocklight_state', 'starlight.skylight_state'):
            if key in fields:
                del fields[key]
                changed = True
    return changed


def lighting_cache_errors(root: Mapping[str, Tag]) -> list[tuple]:
    """Detect Starlight's initialized/hidden state without its 2048-byte array."""
    errors = []
    starlight = 'isLightOn' in root and 'starlight.light_version' in root
    for section in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        fields = compound(section)
        y = fields.get('Y', Tag(TAG_BYTE, 0)).value
        for array, state in (('BlockLight', 'starlight.blocklight_state'), ('SkyLight', 'starlight.skylight_state')):
            value = fields.get(array)
            if value is not None and (value.type != TAG_BYTE_ARRAY or len(value.value) != 2048):
                errors.append((y, array, 'invalid_array'))
            elif starlight and fields.get(state, Tag(TAG_INT, 0)).value in (2, 3) and value is None:
                errors.append((y, array, 'initialized_array_missing'))
    return errors


class _Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, count: int) -> bytes:
        end = self.pos + count
        if count < 0 or end > len(self.data):
            raise NbtError("truncated NBT")
        result = self.data[self.pos:end]
        self.pos = end
        return result

    def unpack(self, fmt: str) -> Any:
        size = struct.calcsize(fmt)
        values = struct.unpack(fmt, self.take(size))
        return values[0] if len(values) == 1 else values

    def string(self) -> str:
        size = self.unpack(">H")
        return self.take(size).decode("utf-8", "surrogatepass")


def _read_payload(reader: _Reader, tag_type: int) -> Tag:
    if tag_type == TAG_BYTE:
        return Tag(tag_type, reader.unpack(">b"))
    if tag_type == TAG_SHORT:
        return Tag(tag_type, reader.unpack(">h"))
    if tag_type == TAG_INT:
        return Tag(tag_type, reader.unpack(">i"))
    if tag_type == TAG_LONG:
        return Tag(tag_type, reader.unpack(">q"))
    if tag_type == TAG_FLOAT:
        return Tag(tag_type, reader.unpack(">f"))
    if tag_type == TAG_DOUBLE:
        return Tag(tag_type, reader.unpack(">d"))
    if tag_type == TAG_BYTE_ARRAY:
        size = reader.unpack(">i")
        return Tag(tag_type, reader.take(size))
    if tag_type == TAG_STRING:
        return Tag(tag_type, reader.string())
    if tag_type == TAG_LIST:
        list_type = reader.unpack(">B")
        size = reader.unpack(">i")
        if size < 0:
            raise NbtError("negative list size")
        return Tag(tag_type, [_read_payload(reader, list_type) for _ in range(size)], list_type)
    if tag_type == TAG_COMPOUND:
        result: dict[str, Tag] = {}
        while True:
            child_type = reader.unpack(">B")
            if child_type == TAG_END:
                break
            # Assignment evaluates the right-hand side before the subscript;
            # consume the on-wire name first, then its payload.
            name = reader.string()
            result[name] = _read_payload(reader, child_type)
        return Tag(tag_type, result)
    if tag_type == TAG_INT_ARRAY:
        size = reader.unpack(">i")
        if size < 0:
            raise NbtError("negative int array size")
        if not size:
            values: list[int] = []
        elif size == 1:
            values = [reader.unpack(">i")]
        else:
            values = list(reader.unpack(f">{size}i"))
        return Tag(tag_type, values)
    if tag_type == TAG_LONG_ARRAY:
        size = reader.unpack(">i")
        if size < 0:
            raise NbtError("negative long array size")
        if not size:
            values = []
        elif size == 1:
            values = [reader.unpack(">q")]
        else:
            values = list(reader.unpack(f">{size}q"))
        return Tag(tag_type, values)
    raise NbtError(f"unknown NBT tag type {tag_type}")


def decode_nbt(data: bytes, *, compressed: str | None = None) -> NbtFile:
    if compressed == "gzip":
        data = gzip.decompress(data)
    elif compressed == "zlib":
        data = zlib.decompress(data)
    elif compressed not in (None, "none"):
        raise ValueError(f"unsupported NBT compression: {compressed}")
    reader = _Reader(data)
    root_type = reader.unpack(">B")
    if root_type == TAG_END:
        raise NbtError("root may not be TAG_End")
    result = NbtFile(reader.string(), _read_payload(reader, root_type))
    if reader.pos != len(data):
        raise NbtError(f"{len(data) - reader.pos} trailing bytes after root tag")
    return result


def _write_string(out: bytearray, value: str) -> None:
    encoded = value.encode("utf-8", "surrogatepass")
    if len(encoded) > 65535:
        raise NbtError("NBT string exceeds 65535 encoded bytes")
    out.extend(struct.pack(">H", len(encoded)))
    out.extend(encoded)


def _write_payload(out: bytearray, tag: Tag) -> None:
    tag_type, value = tag.type, tag.value
    if tag_type == TAG_BYTE:
        out.extend(struct.pack(">b", value))
    elif tag_type == TAG_SHORT:
        out.extend(struct.pack(">h", value))
    elif tag_type == TAG_INT:
        out.extend(struct.pack(">i", value))
    elif tag_type == TAG_LONG:
        out.extend(struct.pack(">q", value))
    elif tag_type == TAG_FLOAT:
        out.extend(struct.pack(">f", value))
    elif tag_type == TAG_DOUBLE:
        out.extend(struct.pack(">d", value))
    elif tag_type == TAG_BYTE_ARRAY:
        out.extend(struct.pack(">i", len(value)))
        out.extend(value)
    elif tag_type == TAG_STRING:
        _write_string(out, value)
    elif tag_type == TAG_LIST:
        list_type = tag.list_type if tag.list_type is not None else (value[0].type if value else TAG_END)
        if any(item.type != list_type for item in value):
            raise NbtError("heterogeneous TAG_List")
        out.extend(struct.pack(">Bi", list_type, len(value)))
        for item in value:
            _write_payload(out, item)
    elif tag_type == TAG_COMPOUND:
        for name, child in value.items():
            out.append(child.type)
            _write_string(out, name)
            _write_payload(out, child)
        out.append(TAG_END)
    elif tag_type == TAG_INT_ARRAY:
        out.extend(struct.pack(">i", len(value)))
        if value:
            out.extend(struct.pack(f">{len(value)}i", *value))
    elif tag_type == TAG_LONG_ARRAY:
        out.extend(struct.pack(">i", len(value)))
        if value:
            out.extend(struct.pack(f">{len(value)}q", *value))
    else:
        raise NbtError(f"cannot write NBT tag type {tag_type}")


def encode_nbt(nbt: NbtFile, *, compressed: str | None = None) -> bytes:
    out = bytearray([nbt.root.type])
    _write_string(out, nbt.name)
    _write_payload(out, nbt.root)
    raw = bytes(out)
    if compressed == "gzip":
        return gzip.compress(raw, mtime=0)
    if compressed == "zlib":
        return zlib.compress(raw)
    if compressed not in (None, "none"):
        raise ValueError(f"unsupported NBT compression: {compressed}")
    return raw


def read_nbt(path: str | Path) -> NbtFile:
    path = Path(path)
    data = path.read_bytes()
    compression = "gzip" if data[:2] == b"\x1f\x8b" else None
    return decode_nbt(data, compressed=compression)


def write_nbt(path: str | Path, nbt: NbtFile, *, compressed: str | None = "gzip") -> None:
    Path(path).write_bytes(encode_nbt(nbt, compressed=compressed))


def compound(tag: Tag) -> MutableMapping[str, Tag]:
    if tag.type != TAG_COMPOUND:
        raise NbtError(f"expected TAG_Compound, got {tag.type}")
    return tag.value


def child(tag: Tag, name: str, expected: int | None = None) -> Tag | None:
    value = compound(tag).get(name)
    if value is not None and expected is not None and value.type != expected:
        raise NbtError(f"{name}: expected tag {expected}, got {value.type}")
    return value


@dataclass
class RegionChunk:
    x: int
    z: int
    timestamp: int
    compression: int
    compressed_payload: bytes

    def raw_nbt(self) -> bytes:
        kind = self.compression & 0x7F
        if self.compression & 0x80:
            raise NbtError("external .mcc chunk payloads are not embedded")
        if kind == 1:
            return gzip.decompress(self.compressed_payload)
        if kind == 2:
            return zlib.decompress(self.compressed_payload)
        if kind == 3:
            return self.compressed_payload
        raise NbtError(f"unknown Anvil compression type {self.compression}")

    def nbt(self) -> NbtFile:
        return decode_nbt(self.raw_nbt())


class RegionFile:
    """An Anvil region whose untouched bytes stay unchanged on ``to_bytes``.

    ``set_chunk`` overwrites in place when possible and appends otherwise.  This
    deliberately leaves old oversized sectors orphaned instead of relocating
    unrelated chunks, which protects their compressed bytes and timestamps.
    """

    SECTOR = 4096

    def __init__(self, data: bytes | None = None):
        if data is None:
            data = bytes(self.SECTOR * 2)
        self._original = bytes(data)
        self._original_size = len(data)
        if len(data) < self.SECTOR * 2:
            raise NbtError("region is shorter than its two-sector header")
        # Region files written by a crashed/interrupted process can omit only
        # the unused tail of their final sector.  Validate every referenced
        # byte against the real EOF before supplying that tail in memory.
        if len(data) % self.SECTOR:
            padded_size = math.ceil(len(data) / self.SECTOR) * self.SECTOR
            self._validate_locations(data, padded_size)
            data = data + bytes(padded_size - len(data))
        else:
            self._validate_locations(data, len(data))
        self._data = bytearray(data)
        self._dirty = False

    @classmethod
    def _validate_locations(cls, original: bytes, padded_size: int) -> None:
        """Reject malformed Anvil location tables without trusting padding."""
        if len(original) < cls.SECTOR * 2:
            raise NbtError("region is shorter than its two-sector header")
        allocated: list[tuple[int, int, int]] = []
        for index in range(1024):
            value = int.from_bytes(original[index * 4:index * 4 + 4], "big")
            sector, sectors = value >> 8, value & 0xFF
            if (sector == 0) != (sectors == 0):
                raise NbtError("chunk location has an unpaired offset/count")
            if not sector:
                continue
            if sector < 2:
                raise NbtError("chunk location overlaps the region header")
            start = sector * cls.SECTOR
            end = start + sectors * cls.SECTOR
            if end > padded_size:
                raise NbtError("chunk allocation lies beyond region")
            # Both the length/compression header and declared payload must be
            # genuinely present, not merely covered by synthetic zero padding.
            if start + 5 > len(original):
                raise NbtError("truncated chunk payload header")
            length = int.from_bytes(original[start:start + 4], "big")
            if length < 1 or length + 4 > sectors * cls.SECTOR:
                raise NbtError("invalid chunk length")
            if start + 4 + length > len(original):
                raise NbtError("truncated chunk payload")
            allocated.append((start, end, index))
        allocated.sort()
        for (_, end, index), (next_start, _, next_index) in zip(allocated, allocated[1:]):
            if end > next_start:
                raise NbtError(f"chunk allocations overlap ({index} and {next_index})")

    @classmethod
    def open(cls, path: str | Path) -> "RegionFile":
        return cls(Path(path).read_bytes())

    def _index(self, local_x: int, local_z: int) -> int:
        if not (0 <= local_x < 32 and 0 <= local_z < 32):
            raise ValueError("region-local chunk coordinates must be in 0..31")
        return local_x + local_z * 32

    def _location(self, index: int) -> tuple[int, int]:
        value = int.from_bytes(self._data[index * 4:index * 4 + 4], "big")
        return value >> 8, value & 0xFF

    def get_chunk(self, local_x: int, local_z: int) -> RegionChunk | None:
        index = self._index(local_x, local_z)
        sector, sectors = self._location(index)
        if not sector or not sectors:
            return None
        start = sector * self.SECTOR
        limit = len(self._data) if self._dirty else self._original_size
        if start + 5 > limit:
            raise NbtError("chunk location lies beyond region")
        length = int.from_bytes(self._data[start:start + 4], "big")
        if length < 1 or length + 4 > sectors * self.SECTOR or start + 4 + length > limit:
            raise NbtError("invalid chunk length")
        timestamp = int.from_bytes(self._data[self.SECTOR + index * 4:self.SECTOR + index * 4 + 4], "big")
        return RegionChunk(local_x, local_z, timestamp, self._data[start + 4], bytes(self._data[start + 5:start + 4 + length]))

    def chunks(self) -> Iterator[RegionChunk]:
        for z in range(32):
            for x in range(32):
                chunk = self.get_chunk(x, z)
                if chunk is not None:
                    yield chunk

    def set_chunk(self, local_x: int, local_z: int, nbt: NbtFile | bytes,
                  *, compression: int = 2, timestamp: int | None = None) -> None:
        index = self._index(local_x, local_z)
        raw = encode_nbt(nbt) if isinstance(nbt, NbtFile) else nbt
        if compression == 1:
            payload = gzip.compress(raw, mtime=0)
        elif compression == 2:
            payload = zlib.compress(raw)
        elif compression == 3:
            payload = raw
        else:
            raise ValueError("compression must be 1 (gzip), 2 (zlib), or 3 (none)")
        record = len(payload + b"x").to_bytes(4, "big") + bytes([compression]) + payload
        needed = math.ceil(len(record) / self.SECTOR)
        if needed > 255:
            raise NbtError("chunk exceeds inline Anvil sector limit")
        old_sector, old_count = self._location(index)
        if old_sector and needed <= old_count:
            sector = old_sector
        else:
            sector = len(self._data) // self.SECTOR
            self._data.extend(bytes(needed * self.SECTOR))
        start = sector * self.SECTOR
        capacity = (old_count if sector == old_sector else needed) * self.SECTOR
        self._data[start:start + capacity] = record + bytes(capacity - len(record))
        location = (sector << 8) | needed
        self._data[index * 4:index * 4 + 4] = location.to_bytes(4, "big")
        if timestamp is not None:
            off = self.SECTOR + index * 4
            self._data[off:off + 4] = timestamp.to_bytes(4, "big", signed=False)
        self._dirty = True

    def to_bytes(self) -> bytes:
        # Avoid silently normalizing a read-only truncated input on a no-op.
        return bytes(self._data) if self._dirty else self._original

    def save(self, path: str | Path) -> None:
        Path(path).write_bytes(self.to_bytes())


def palette_bits(palette_size: int) -> int:
    if palette_size < 1:
        raise ValueError("palette may not be empty")
    return max(4, (palette_size - 1).bit_length())


def unpack_palette_indices(longs: Sequence[int], palette_size: int, count: int = 4096) -> list[int]:
    """Decode 1.20.1 block indices; entries never span two 64-bit longs."""
    if palette_size == 1 and not longs:
        return [0] * count
    bits = palette_bits(palette_size)
    per_long = 64 // bits
    expected = math.ceil(count / per_long)
    if len(longs) != expected:
        raise NbtError(f"palette data has {len(longs)} longs, expected {expected} for {palette_size} entries")
    mask = (1 << bits) - 1
    result = [((longs[i // per_long] & 0xFFFFFFFFFFFFFFFF) >> ((i % per_long) * bits)) & mask for i in range(count)]
    if any(value >= palette_size for value in result):
        raise NbtError("palette index out of range")
    return result


def pack_palette_indices(indices: Sequence[int], palette_size: int) -> list[int]:
    """Encode indices as signed TAG_Long values in the 1.20.1 layout."""
    if palette_size == 1:
        if any(indices):
            raise ValueError("single-entry palette accepts only index zero")
        return []
    bits = palette_bits(palette_size)
    per_long = 64 // bits
    mask = (1 << bits) - 1
    result = [0] * math.ceil(len(indices) / per_long)
    for i, value in enumerate(indices):
        if value < 0 or value >= palette_size:
            raise ValueError(f"palette index {value} out of range")
        result[i // per_long] |= (value & mask) << ((i % per_long) * bits)
    return [value - (1 << 64) if value >= (1 << 63) else value for value in result]


def count_palette_indices(longs: Sequence[int], palette_size: int, count: int = 4096) -> Counter[int]:
    """Count packed indices without allocating a 4096-element position list."""
    if palette_size == 1 and not longs:
        return Counter({0: count})
    bits = palette_bits(palette_size)
    per_long = 64 // bits
    expected = math.ceil(count / per_long)
    if len(longs) != expected:
        raise NbtError(f"palette data has {len(longs)} longs, expected {expected} for {palette_size} entries")
    mask = (1 << bits) - 1
    result: Counter[int] = Counter()
    remaining = count
    for signed in longs:
        value = signed & 0xFFFFFFFFFFFFFFFF
        take = min(per_long, remaining)
        for _ in range(take):
            result[value & mask] += 1
            value >>= bits
        remaining -= take
    if any(value >= palette_size for value in result):
        raise NbtError("palette index out of range")
    return result


def block_state_key(entry: Tag) -> str:
    data = compound(entry)
    name = data["Name"].value
    props = data.get("Properties")
    if not props:
        return name
    values = compound(props)
    return name + "[" + ",".join(f"{key}={values[key].value}" for key in sorted(values)) + "]"


def section_blocks(section: Tag) -> tuple[list[Tag], list[int]] | None:
    states = child(section, "block_states", TAG_COMPOUND)
    if states is None:
        return None
    palette_tag = child(states, "palette", TAG_LIST)
    if palette_tag is None or not palette_tag.value:
        return None
    data_tag = child(states, "data", TAG_LONG_ARRAY)
    longs = data_tag.value if data_tag else []
    return palette_tag.value, unpack_palette_indices(longs, len(palette_tag.value))


def _region_origin(path: Path) -> tuple[int, int]:
    parts = path.stem.split(".")
    return int(parts[1]) * 32, int(parts[2]) * 32


def _dimension_for_region(world: Path, path: Path) -> str:
    relative = path.relative_to(world).as_posix()
    if relative.startswith("region/"):
        return "minecraft:overworld"
    if relative.startswith("DIM-1/"):
        return "minecraft:the_nether"
    if relative.startswith("DIM1/"):
        return "minecraft:the_end"
    if "/dimensions/" in "/" + relative:
        before = relative.split("/region/")[0].split("dimensions/", 1)[1].split("/")
        if len(before) >= 2:
            return before[0] + ":" + "/".join(before[1:])
    return relative.rsplit("/region/", 1)[0] or "unknown"


def inventory_world(world: str | Path) -> dict[str, Any]:
    world = Path(world).resolve()
    level = read_nbt(world / "level.dat")
    level_data = compound(compound(level.root)["Data"])
    state_counts: Counter[str] = Counter()
    namespace_counts: Counter[str] = Counter()
    non_air_namespace_counts: Counter[str] = Counter()
    block_entity_counts: Counter[str] = Counter()
    dimension_chunks: Counter[str] = Counter()
    versions: Counter[int] = Counter()
    # Section-granularity bounds avoid expanding every generated terrain block
    # into a Python object. They are conservative by at most 15 blocks/axis.
    bounds: dict[str, list[int] | None] = {"min": None, "max": None}
    section_y_bounds: list[int] = []
    region_files = sorted(world.glob("**/region/r.*.*.mca"))
    chunk_total = 0
    for path in region_files:
        dimension = _dimension_for_region(world, path)
        rx, rz = _region_origin(path)
        region = RegionFile.open(path)
        for stored in region.chunks():
            nbt = stored.nbt()
            root = compound(nbt.root)
            chunk_x = int(root.get("xPos", Tag(TAG_INT, rx + stored.x)).value)
            chunk_z = int(root.get("zPos", Tag(TAG_INT, rz + stored.z)).value)
            chunk_total += 1
            dimension_chunks[dimension] += 1
            if "DataVersion" in root:
                versions[int(root["DataVersion"].value)] += 1
            sections = root.get("sections") or root.get("Sections")
            if sections and sections.type == TAG_LIST:
                for section in sections.value:
                    sy_tag = compound(section).get("Y")
                    if sy_tag is None:
                        continue
                    sy = int(sy_tag.value)
                    section_y_bounds.append(sy)
                    states = child(section, "block_states", TAG_COMPOUND)
                    if states is None:
                        continue
                    palette_tag = child(states, "palette", TAG_LIST)
                    if palette_tag is None or not palette_tag.value:
                        continue
                    palette = palette_tag.value
                    packed_tag = child(states, "data", TAG_LONG_ARRAY)
                    local = count_palette_indices(packed_tag.value if packed_tag else [], len(palette))
                    keys = [block_state_key(entry) for entry in palette]
                    for palette_index, amount in local.items():
                        key = keys[palette_index]
                        namespace = key.split(":", 1)[0]
                        state_counts[key] += amount
                        namespace_counts[namespace] += amount
                        if key != "minecraft:air":
                            non_air_namespace_counts[namespace] += amount
                    non_air = sum(amount for palette_index, amount in local.items() if keys[palette_index] != "minecraft:air")
                    if non_air:
                        low = [chunk_x * 16, sy * 16, chunk_z * 16]
                        high = [low[0] + 15, low[1] + 15, low[2] + 15]
                        bounds["min"] = low if bounds["min"] is None else [min(a, b) for a, b in zip(bounds["min"], low)]
                        bounds["max"] = high if bounds["max"] is None else [max(a, b) for a, b in zip(bounds["max"], high)]
            entities = root.get("block_entities") or root.get("TileEntities")
            if entities and entities.type == TAG_LIST:
                for entity in entities.value:
                    entity_id = compound(entity).get("id")
                    block_entity_counts[str(entity_id.value) if entity_id else "<missing-id>"] += 1
    datapacks = level_data.get("DataPacks")
    datapack_info: dict[str, Any] = {}
    if datapacks and datapacks.type == TAG_COMPOUND:
        for key, value in compound(datapacks).items():
            datapack_info[key.lower()] = [item.value for item in value.value] if value.type == TAG_LIST else value.value
    resource_archives = [p.relative_to(world).as_posix() for p in world.rglob("*.zip")]
    other_mod_blocks = {
        key: value for key, value in sorted(non_air_namespace_counts.items())
        if key not in ("minecraft", "bloodborne_blocks")
    }
    return {
        "format": "bloodborne-world-inventory-v1",
        "source": {"worldFolder": world.name, "offlineCopy": True},
        "level": {
            "dataVersion": level_data.get("DataVersion", Tag(TAG_INT, None)).value,
            "version": _plain(level_data.get("Version")),
            "enabledFeatures": _plain(level_data.get("enabled_features")),
            "dataPacks": datapack_info,
        },
        "anvil": {
            "regionFiles": len(region_files),
            "chunks": chunk_total,
            "chunksByDimension": dict(sorted(dimension_chunks.items())),
            "chunkDataVersions": {str(k): v for k, v in sorted(versions.items())},
            "sectionY": {"min": min(section_y_bounds) if section_y_bounds else None, "max": max(section_y_bounds) if section_y_bounds else None},
            "nonAirSectionBoundsInclusive": bounds,
        },
        "blocks": {
            "totalIncludingAir": sum(state_counts.values()),
            "nonAir": sum(v for k, v in state_counts.items() if k != "minecraft:air"),
            "uniqueStates": len(state_counts),
            "uniqueBlockIds": len({key.split("[", 1)[0] for key in state_counts}),
            "countsByNamespace": dict(sorted(namespace_counts.items())),
            "nonAirCountsByNamespace": dict(sorted(non_air_namespace_counts.items())),
            "stateCounts": dict(sorted(state_counts.items())),
        },
        "blockEntities": {
            "total": sum(block_entity_counts.values()),
            "countsById": dict(sorted(block_entity_counts.items())),
            "helperCount": sum(v for k, v in block_entity_counts.items()
                               if k in ("bloodborne_blocks:architecture_part", "bloodborne:architecture_part")
                               or "helper" in k.lower()),
        },
        "resources": {
            "worldResourcePack": (world / "resources.zip").exists(),
            "zipFilesInWorld": resource_archives,
            "vanillaNonAirBlockCount": non_air_namespace_counts.get("minecraft", 0),
            "bloodborneNonAirBlockCount": non_air_namespace_counts.get("bloodborne_blocks", 0),
            "otherModNonAirBlocksByNamespace": other_mod_blocks,
        },
        "otherMods": {
            "blockNamespaces": other_mod_blocks,
            "blockEntityIds": {k: v for k, v in sorted(block_entity_counts.items())
                               if not k.startswith("minecraft:") and not k.startswith("bloodborne_blocks:")},
            "worldDataFiles": sorted(p.name for p in (world / "data").glob("*") if p.is_file()),
        },
    }


def _plain(tag: Tag | None) -> Any:
    if tag is None:
        return None
    if tag.type == TAG_COMPOUND:
        return {key: _plain(value) for key, value in tag.value.items()}
    if tag.type == TAG_LIST:
        return [_plain(value) for value in tag.value]
    if tag.type == TAG_BYTE_ARRAY:
        return list(tag.value)
    return tag.value


def _main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    inv = sub.add_parser("inventory", help="inventory a copied Java world")
    inv.add_argument("world", type=Path)
    inv.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.command == "inventory":
        result = inventory_world(args.world)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    _main()
