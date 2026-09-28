# Composite regression forensics (before implementation)

Baseline: `419b85eeab56180f0e26272ffc2a2136f6a05a18`.
Broken: `b086e88929a971a2abd184629b3e7a59225304e5`, preserved remotely as
`archive/beta3-grid-fragmentation-broken`. Repair starts at baseline, without
merging or cherry-picking beta.3. Minecraft 1.20.1, Fabric 0.16.10, Yarn build.10,
Java 17, Gradle 8.8/Loom 1.6.12 remain unchanged.

## Evidence and causal path

`fragmentation-diff.json` records exact original, immutable MODDED, beta.2
release and beta.3 negative-build states. The original dimension is
`eh_s2:yharnam`; the archived MODDED map moved it to overworld without moving
these coordinates. MODDED SHA256 remains
`c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
The negative build is diagnostic only, never conversion input.

* **C474:** exact 16-cell source pattern starts at `(-566,85,-165)`, matching
  current raw rule 48, `o_c474[facing=north,visual=base]`. `(-563,85,-165)` is air
  in both immutable inputs, so that playtest master is not an original-map
  anchor. The legacy-only compiled rule expects old carrier IDs, while all 16
  actual source cells contain modular IDs. No modular compiled C474 rule
  survives the frozen mapping gaps. The baseline compatibility palette already
  exposes `city_column_bc0c7c3a_0368[variant=14]` at the source root.
* **C001:** exact 16-source-cell reviewed tree has canonical root
  `(-284,42,-71)`. Original weighted-model guards select
  `variant=tree_cfd3d71f521b`; choosing another visually similar variant is not
  justified. Modular rule 5 expands to 114 technical cells and misses only
  three kept carriers: it expects `minecraft:white_wool/orange_wool/magenta_wool`
  with added properties, but the MODDED world has those in `bloodborne_blocks`.
  The legacy rule instead misses 13 modular source cells. Neither representation
  matches the actual mixed representation. The remaining wool is genuinely
  part of this reviewed tree, not an independent cube.
* **Panel fixtures:** exact frozen migration proves two distinct source panel
  roots, `(-279,42,-49)` and `(-280,42,-50)`. The former's upper module is at
  `(-279,43,-49)`. They must not be merged merely because they look connected.
  Each two-cell panel must retain its own carrier/composite identity.
* **Ladder fixtures:** both modules come from the exact south/bottom/straight
  `waxed_exposed_cut_copper_stairs` source at `(-326,50,-116)`, with offsets
  `(-1,0,1)` and `(0,0,1)`. The vanilla ladder at `(-326,50,-115)` is a separate
  source carrier; its presence must not misidentify the displaced section.
* **Ornament:** source is `dead_horn_coral_fan[waterlogged=false]` at
  `(-332,77,-137)`. The MODDED ID is a historical expanded world ornament,
  without a reverse entry in frozen carrier migration. Its full membership
  still requires historical geometry/provenance reconciliation. It is not
  evidence for inventing a new semantic family.
* **Lantern:** the exact flower-pot source at `(-540,41,-33)` matches reviewed
  C618, raw rule 50 / compiled rule 131,
  `o_c618[facing=north,lit=true,variant=canonical,visual=base]`. All source cells
  match. Conversion is rejected because helper `(-540,42,-33)` would overwrite
  `m_41d523ebebead0fb[facing=west]`. It is a Bloodborne module, not established
  foreign content. `o_lantern` uses a different source carrier and is not a
  substitute. The manually placed coordinate is air in immutable inputs;
  its state is user playtest evidence, not an archived-world state. Setting
  `assembled=false` blindly cannot solve composite placement.

## Why previous green checks were insufficient

Fragmentation predates beta.3 in the conservative fallback. Beta.3 did not
repair it and additionally flattened physical geometry/anchors of 25 native
compatibility IDs and removed verified historical helpers before logical
identity resolution. Resource fingerprints protected 49 authored families,
but neither their presence nor source-component consumption in the world was
required. A valid registry and cell-local collisions therefore passed for a
semantically fragmented map.

The repair must validate source membership and one-root identity before
compatibility fallback and physics/editability. No runtime, converter or model
fix has been made at this forensic checkpoint.

## Oracle work in progress

`protected-world-oracle.json` is an exact-pattern scan with weighted guards,
not yet a final non-overlapping acceptance oracle. It currently records 30,521
matching transactions, including 115 C001 and 10 C474. Existing declared
supersession and physical conflicts must be reconciled before conversion.
For example C474 contains generic railing source matches; those are not
additional independent objects. Other distinct authored objects really share
physical cells (e.g. bags and doors), and cannot be silently overwritten.
Zero-source families and retired/superseded variants remain explicitly listed.

Release gates: **NOT RUN / NOT PASSED**. No repaired world or beta.4 is claimed.
