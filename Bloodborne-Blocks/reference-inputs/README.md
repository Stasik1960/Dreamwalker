# User-supplied original/reference inputs

These archives were explicitly supplied and authorized for publication by the
project owner after the initial technical snapshot. They are copied byte-for-byte,
not extracted, regenerated, sanitized or automatically converted.

| Repository file | Supplied filename | Bytes | SHA-256 |
|---|---|---:|---|
| [source-world.zip](source-world.zip) | `world(3).zip` | 104549304 | `ba8adce8443d1f88c9357ab4aaacfea1564febca79b06bd0932448e7246ab562` |
| [source-resource-pack.zip](source-resource-pack.zip) | `bloodborne (1).zip` | 1359605 | `0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308` |

The resource pack exactly matches the previously available local `bloodborne.zip`
copies. Only one source-pack archive is committed; generated mod assets under
`src/main/resources/` remain necessary build inputs and are not substitute source
archives. Original artwork attribution remains in [ASSET-NOTICE.md](../ASSET-NOTICE.md).

## Read-only validation on 2026-09-21

Both archives pass ZIP CRC checks and have no duplicate entry names or absolute /
parent-traversal paths. Size and SHA-256 match the supplied files. A limited
secret-pattern scan of 55 world text files and 920 pack text files found no
matches; this is not a comprehensive privacy or binary-NBT secret audit.

The world archive has 865 entries. Its `world/level.dat` explicitly records
`Version.Name = 1.20.1` and `DataVersion = 3465`. All 384 terrain region files
were inspected: 113568 chunks, 2725632 block-palette sections, 3649605 palette
entries. Chunk DataVersions are 3465 (97216 chunks) and 2975 (16352 chunks).
No block palette contains a `bloodborne_blocks` identifier. This is consistent
with a pre-Bloodborne-Blocks map, but does not establish that it was the exact
input used for the latest Ether conversion.

The archive is **not vanilla-only**: palettes also contain `supplementaries`,
`croptopia`, `magic_vibe_decorations`, `armourers_workshop`, `biomesoplenty`,
`yuushya`, `twigs`, `handcrafted`, `mcwlights`, `amendments`, `mcwpaths`,
`diagonalfences`, and `construction_deco`. These are palette observations, not
counts of placed blocks. Keep the distinction between vanilla carrier blocks
used by the Bloodborne pack and unrelated mod content in the full world.

Fifty region files are not padded to a 4096-byte boundary and are rejected by
the current strict `world_io.RegionFile` constructor. For this read-only audit,
every referenced record was first checked to lie wholly within the original
file; temporary zero padding was then applied **in memory only** to decode the
remaining 27067 chunks successfully. No archive bytes or converter code were
changed. A future conversion must account for this parser limitation.

The source pack has 1162 ZIP entries, including 734 model JSON files, 174
blockstate JSON files and 217 PNG textures. Its `pack.mcmeta` declares format 8
and description `[Bloodborne x Minecraft - OldTexturePack]`. These are original
metadata, not a claim that the archive has been upgraded or rebuilt.

## Retrieval and provenance

The source-world ZIP is close to GitHub's normal 100 MiB file limit and is stored
with **Git LFS**. To retrieve both LFS audit inputs from the repository root:

```sh
git lfs install
git lfs pull --include="Bloodborne-Blocks/build/logical-world/ether-current-20260921-report.json,Bloodborne-Blocks/reference-inputs/source-world.zip"
```

The source pack is an ordinary Git file. For tools accepting `--original-pack`,
use `reference-inputs/source-resource-pack.zip` when running from `Bloodborne-Blocks/`.
This is an input-path example, not an instruction to rerun generation/migration.

The original/reference world is distinct from the converted
`releases/Bloodborne-Blocks/ether-current-20260921-converted.zip`. Its provenance
as the exact source of the most recent Ether conversion must not be assumed:
that conversion used a different supplied archive, `ether.zip`, recorded in its
ledger. Do not overwrite or mix these maps. Full archives may include player and
mod data; they are published without anonymization under the owner's instruction.
