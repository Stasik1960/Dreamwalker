# Technical audit snapshot — 2026-09-21

This is the current, incomplete `Bloodborne-Blocks` working implementation,
published for technical review. It is not acceptance of the 23 requested visual
fixes. Packaging this snapshot does not modify gameplay code or rerun generation,
world conversion, or the build.

## Start here

- Current acceptance/limitations: [NORMALIZATION-ACCEPTANCE.md](NORMALIZATION-ACCEPTANCE.md).
- Generated counts: [logical-generation-v3.json](logical-generation-v3.json).
- Reviewed families: [logical-families-v3.json](logical-families-v3.json).
- Palette coverage: [logical-palette-classification-v3.json](logical-palette-classification-v3.json).
- Runtime: `src/main/java/dev/dreamwalker/bloodborneblocks/`.
- Tests: `src/test/`, `src/gametest/`, and `tools/test_*.py`.
- Logical contracts: `src/main/resources/bloodborne_blocks/logical/` —
  `definitions.json`, `geometry.json`, `meshes.json.gz`, `migration.json`,
  `hidden-items.json`.
- Previous mappings remain under `src/main/resources/bloodborne_blocks/`,
  including `v2/`, aliases, source definitions, geometry and source provenance.
- `tools/convert_logical_world.py` and `tools/check_logical_world.py` implement
  conversion and independent verification. Do not repeat the original modular
  migration or regenerate the v2 palette merely to inspect this snapshot.

`LOGICAL-OBJECTS-STATUS.md`, older coverage/audit reports, and the older parts of
`HANDOFF.md` describe historical stages, not the current acceptance result.
The legacy `VERSION` file still reads `1.1.0`; the actual Gradle and Fabric
metadata version is `2.1.0-alpha.1`. This discrepancy is recorded, not fixed by
this publication operation.

## Download the diagnostic evidence

The requested full conversion ledger is retained at its original project path:

`build/logical-world/ether-current-20260921-report.json`

It is **200,853,774 bytes** and is tracked with Git LFS, not a normal Git blob.
GitHub's code/raw view may expose only its small LFS pointer. After cloning:

```sh
git lfs install
git lfs pull --include="Bloodborne-Blocks/build/logical-world/ether-current-20260921-report.json"
```

SHA-256 of the actual JSON:
`7769401c5e99417a99085cab7599dc0d879e171d3b8f38bdaf435afb30f6d731`.

Also retained as unique evidence:

- `build/logical-world/ether-current-20260921-verification.json` — the existing
  compact verification result, including original local paths and hashes;
- `build/test-results/gametest/TEST-logical-gametest.xml` — the earlier seven-test
  server result. It is historical evidence, not a new run during publication.

These are explicit exceptions to ignoring `build/`. Caches, compiled classes,
dependencies, logs, intermediate worlds, duplicate repeat ledgers and previews
that can be regenerated are not part of this snapshot.

## What the current pipeline actually does

1. Conversion recognizes exact declared `ID + properties + relative offset`
   patterns. Legacy mode uses `source` plus `members`; v2 mode uses `components`.
   It does not infer new object types from repeated arrangements in the world.
2. Rule generation uses models/blockstates, source-pack evidence, semantic
   categorization and the v2 mapping. The converter reads generated manifests
   and world blocks, not the resource pack or a model-similarity classifier.
3. There are 256 families: 20 curated and 236 auto-model families; 1,324 states
   and meshes; 1,631 migration rules. Only 479 rules have non-null v2 component
   arrays; 1,152 are legacy-only. A family with four target facings does not
   necessarily have four inverse v2 patterns.
4. Rotations are explicitly generated. Auto target facing follows the source
   model application's yaw, rather than assuming the carrier-facing label is
   correct. Conversion does not perform generic rotated/mirrored matching.
   Runtime mirror remaps direction properties; it does not reflect arbitrary
   asymmetric mesh geometry.
5. Migration origin is `foundPosition - componentOffset`; target root is origin
   plus the rule offset (currently zero for every rule). Placement instead uses
   `root = placementCell - geometryAnchor(state, side)`. Geometry anchor is the
   componentwise minimum occupied cell; WEST/DOWN/NORTH placement uses the
   corresponding maximum coordinate. It is not an authored semantic pivot.
6. Full visual meshes are generated from transformed source model faces and
   explicitly offset applications. They are pre-oriented per state and rendered
   by the root. Runtime does not assemble the visible mesh by discovering nearby
   legacy blocks. Logical render offsets are zero in the examples below.
7. Collision/outline are generated from transformed element AABBs split across
   cells, not exact polygon collision. Soft vegetation has no collision; planar
   decorations in selected categories are excluded. Optional ownership bounds
   clip cell ownership without moving the rendered mesh. Alpha filtering of
   planar cells is not pixel-perfect collision.
8. `architecture_part` helpers store one owner ID and an absolute root position.
   Helpers supply cell-local collision and delegate pick/use/break to the master;
   logical outline covers the whole object. Shared ownership is not implemented.
9. Creative hiding is static coverage-based proof, not a census of successful
   world conversions. Creative v2 modules require exact coverage of all known
   reachable uses; unknown provenance or uncovered uses retain the item. Current
   counts hide 1,429 former creative items and retain 1,303. Old registry IDs stay.
10. `partial_or_wrong_group` means an exact required component is missing or
    different. `target_would_overwrite_foreign_block` means a full pattern matched
    but its destination is occupied. `ambiguous_overlap_or_double_consumption`
    rejects competing overlapping candidates unless an explicit containment
    priority applies. `superseded_by_complete_assembly` is a successful larger
    assembly replacing a smaller fallback, not an unresolved object.
11. `unresolved_v2_component` counts known inverse-component positions consumed
    by no accepted candidate. `unmatchedModules` counts other module states;
    it is inventory information, not a count of broken objects. Rejected
    candidates are not unique object counts.
12. Missing/merged old components are not reconstructed approximately; foreign
    blocks (including invisible light blocks) are not overwritten. Rotated
    element AABBs can over-collide. Fourteen floor families have anchor Y=+1,
    putting the manually placed root into a continuous supporting floor;
    this is a static calculation, not a live-game reproduction. Full structure
    copy/rotation also needs review because helper Root NBT is absolute.

Relevant code: `build_logical_objects.py` (`source_rule`, `curated_objects`,
`auto_objects`, `authored_polys`, `geometry_for`), `convert_logical_world.py`
(`candidates`, `reject_overlaps`, `apply`), `logical_palette.py`,
`ArchitectureBlockItem`, `GeometryRuntime`, and `ArchitecturePartBlock`.

## Latest map evidence and concrete examples

Latest conversion: **59,792 accepted rules** across **95 target families**;
59,031 v2-mode and 761 legacy-mode entries. **36,547** entries consumed multiple
source positions; **23,245** consumed one. Do not describe all 59,792 as
multi-block assembly reconstructions. Independent verification checked 10,969
chunks, 1,097,613,312 block slots and 206 unchanged external files. A second
conversion accepted zero rules; all 646 files were byte-identical.

Rule numbers below are zero-based indices in logical `migration.json`. All
positions are in the overworld, all short IDs use `bloodborne_blocks:`.

### Successful tree

Root `(-452,66,-299)`, rule 0, target `o_dead_tree_planter[facing=north]`.

| Legacy component | Relative coordinate | Properties |
|---|---|---|
| white_wool | (0,0,0) | assembled=false,facing=north |
| orange_wool | (0,3,0) | assembled=false,facing=north |
| magenta_wool | (0,6,0) | assembled=false,facing=north |

All three match; destination is clear or owned. The migration root stays at the
original carrier. Placement anchor is `(-1,-1,-1)`. Rotation requires the exact
declared direction for all three members. Tree collision excludes planar art;
46 outline cells, 14 collision cells, one master and 45 helpers.

### Identical tree that remained fragmented

Root `(-468,66,-305)`, same rule, components, offsets, facing and geometry as
above. All three source members match, but relative `(1,0,0)`, world position
`(-467,66,-305)`, contains `minecraft:light[level=5,waterlogged=false]`.
Result: `target_would_overwrite_foreign_block`. The three legacy members and
the light remain unchanged. This is destination occupancy, not failed recognition.

### Converted ladder with a different manual-placement origin

Root `(-443,35,-324)`, rule 660, target `o_ladder_02[facing=north]`.
Original carrier is `waxed_exposed_cut_copper_stairs`; the actual map has:

| v2 component | Relative coordinate | facing |
|---|---|---|
| m_3f34715811db4bea | (-1,0,0) | north |
| m_14b8398d2e3b7d16 | (-1,0,1) | north |
| m_15143494d78dd187 | (0,0,0) | north |
| m_3a361d01b38398fe | (0,0,1) | east |
| m_3bec216fdfd618b6 | (1,0,0) | north |
| m_213bf4a09dee8f70 | (1,0,1) | north |

Exact match succeeds. All six geometry cells have collision and outline; one
master and five helpers are installed. Placement anchor is `(-1,0,0)` and render
offset `(0,0,0)`. For UP placement into cell P, root becomes `P+(1,0,0)`;
migration instead keeps the old origin. This is a proven difference in anchor
conventions, not a claimed live reproduction of incorrectly rendered placement.

The source archive is currently absent from its former Desktop path. These
examples were checked using saved output chunks plus the before/after ledger.
The current definitions, geometry and migration hashes match that ledger.

## Build and publication boundaries

Minecraft 1.20.1; Java 17; checked-in Gradle 8.8 wrapper. Build normally with
`./gradlew.bat check build` on Windows or `bash ./gradlew check build` on Linux.
The original pack is needed for regeneration, not for building checked-in assets.
`./gradlew.bat logicalGameTest` runs the isolated server-side interaction tests.
Do not assume the historical seven passing tests prove visual correctness or
the floor-origin case above. The most recent audit could not rerun Java tests
with its configured JRE/cache paths; publication does not claim a fresh build.

The existing runtime JAR is copied without rebuilding or replacing the earlier
published alpha, using a dated snapshot filename under `releases/Bloodborne-Blocks/`.
Its internal version remains `2.1.0-alpha.1`; install only one Bloodborne JAR.

The user explicitly requested publication of the **complete converted map**
after the initial source-only request. Its separate ZIP includes player data,
mod data and world configuration without anonymization. It is not a source pack,
a temporary world, or a fresh conversion. Keep the map's other mods installed.
The user subsequently also supplied and authorized the original/reference world
and resource pack: see [reference-inputs/README.md](../reference-inputs/README.md)
for archive hashes, provenance distinctions and the additional LFS download.
Caches, dependency extractions, intermediate worlds, diagnostic archives and
local agent configurations remain excluded. See the release directory for the
converted map and runtime JAR with checksums.
