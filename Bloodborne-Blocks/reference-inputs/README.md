# Original pre-Bloodborne-Blocks world and resource pack

The project owner identifies the current `ether.zip` as the original world
**before any replacement with Bloodborne-Blocks blocks**. It supersedes the
incorrect world supplied earlier. Only this corrected world is present in the
current source tree, under the stable name `source-world.zip`.

The archives are published under the owner's explicit instruction and copied
byte-for-byte, not regenerated, sanitized or automatically converted. Do not use
an earlier revision of `source-world.zip` as the source world for further work.

| Repository file | Supplied filename | Bytes | SHA-256 |
|---|---|---:|---|
| [source-world.zip](source-world.zip) | `ether.zip` | 104878288 | `4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51` |
| [source-resource-pack.zip](source-resource-pack.zip) | `bloodborne (1).zip` | 1359605 | `0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308` |

The resource pack exactly matches the previously available local `bloodborne.zip`
copies. Only one source-pack archive is committed; generated mod assets under
`src/main/resources/` remain necessary build inputs and are not substitute source
archives. Original artwork attribution remains in [ASSET-NOTICE.md](../ASSET-NOTICE.md).

## Read-only validation on 2026-09-21

The replacement archive's size and SHA-256 match the supplied `ether.zip`.
The previous world's validation statistics are intentionally not carried over:
they describe the incorrect input, not this archive.

The current ZIP passes CRC validation and has 914 entries, without duplicate or
unsafe paths. `ether/level.dat` explicitly records `Version.Name = 1.20.1` and
`DataVersion = 3465`. All 384 terrain region files and 113588 chunks were
inspected with zero errors. No block palette contains `bloodborne_blocks`.
A limited secret-pattern scan of 72 text files found no matches; this is not a
comprehensive privacy or binary-NBT secret audit.

This is a world before Bloodborne-Blocks replacement, not a vanilla-only modpack:
block palettes also reference `croptopia`, `twigs`, `supplementaries`, `yuushya`,
`magic_vibe_decorations`, `handcrafted`, `biomesoplenty`, `amendments`,
`diagonalfences`, `mcwlights`, `mcwpaths`, `construction_deco`, and
`armourers_workshop`. The namespace check concerns palettes, not entities or POI.

The current archive contains 49 region files without final 4096-byte alignment.
All referenced chunk records were checked against the original file lengths
before temporary zero padding was applied in memory for the read-only parser.
The current strict `world_io.RegionFile` constructor otherwise rejects those
files. No archive bytes or converter code were changed; future conversion must
account for this input-format limitation.

The source pack has 1162 ZIP entries, including 734 model JSON files, 174
blockstate JSON files and 217 PNG textures. Its `pack.mcmeta` declares format 8
and description `[Bloodborne x Minecraft - OldTexturePack]`. These are original
metadata, not a claim that the archive has been upgraded or rebuilt.

## Retrieval and provenance

The source-world ZIP exceeds GitHub's normal 100 MiB file limit and is stored
with **Git LFS**. To retrieve both LFS audit inputs from the repository root:

```sh
git lfs install
git lfs pull --include="Bloodborne-Blocks/build/logical-world/ether-current-20260921-report.json,Bloodborne-Blocks/reference-inputs/source-world.zip"
```

The source pack is an ordinary Git file. For tools accepting `--original-pack`,
use `reference-inputs/source-resource-pack.zip` when running from `Bloodborne-Blocks/`.
This is an input-path example, not an instruction to rerun generation/migration.

This original world is distinct from the converted
`releases/Bloodborne-Blocks/ether-current-20260921-converted.zip`. The historical
conversion ledger records a different archive, also named `ether.zip`, with
SHA-256 `f91156480c8726d2dd3a911be97113a2accd2b28ab5db8c285ce86b5e112c7d2`.
The filename alone does not identify the input: use the current hash in the
table above for all new work on the original map. Replacing this source archive
does not regenerate the existing converted release or its report.

Do not overwrite or mix original and converted maps. Full archives may include
player and mod data; they are published without anonymization under the owner's
instruction.
