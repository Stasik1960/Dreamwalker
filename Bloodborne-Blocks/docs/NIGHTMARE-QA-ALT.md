# NightmareRunning QA + BASE/ALT checkpoint

Baseline: main `9e60c5ef31fc7504cadcbe43a54ccb84e30133e1`. This pass follows
the owner's NightmareRunning review and ALT specification, not another
discovery or batch-03. The fifteen DOCX screenshots/red boxes were inspected.
This is an implementation/automated-QA checkpoint, **not visual acceptance**.

## Reviewed changes and exact IDs

All IDs below are in namespace `bloodborne_blocks`.

| Reviewed family | Current result |
| --- | --- |
| C008 `o_c008` | `o_c008_1`, `_2`, `_3`, `_4`, `_5`. Screenshot #3/#6 share `_3`, with facing. Five independent items, six source instances. High pedestal unchanged; low pedestals raised to floor. |
| C001 `o_c001` | Still one whole tree. Corrected three positive-Z branch applications in the proven six-carrier layout to opposing artwork at yaw 180. Source cells/RNG guards and six variant IDs retained; four distinct rendered meshes after correction. TRUNK collision, no branch collision. |
| C002 `o_c002` | Raised complete composition; all 66 elements from both source models retained, including small surrounding graves/stones. No split. |
| C1491 `o_c1491` | `o_c1491_a`, `_b`, `_c`: three graves with independent roots and corrected floor height. |
| C1680 `o_c1680` | Only intended suitcase elements retained; explicit two-box collision. |
| C1962 `o_c1962` | `o_c1962_a`, `_b`: independent sacks, corrected floor height. |
| C1979 `o_c1979` | `o_c1979_1` … `_5`, corresponding to the five red boxes, not five carriers. Source component groups: 2+12, 6+13, 3+4+5, 7+9, 8+10. Components 1/11 remain context. |
| C471 `o_c471` | `o_c471_a`, `_b`: individual raised spires. Fully textured geometry is yaw-symmetric, so no redundant facing property. |
| C282 `o_c282` | `o_c282_a`, `_b`: independently placed, opened, picked and broken doors, preserving facing/hinge/open. Manual artwork width is 1.5 each; source-height states retain authored placement. |
| C618 `o_c618` | Canonical bottom raised; lit/unlit artwork, luminance and interaction retained. |
| C654 `o_c654` | `o_c654_a`, `_b`: A/A and B/B sides; four wall-facing orientations and broad simple panel collision. **Owner selected A for migration**; B is manual-only. |
| noCatalog `o_wall_deco_1` | Raised canonical artwork; collision NONE. No invented C-ID. |
| C028 `o_c028` | Removed only the reviewed extra side elements. |
| Unnamed fence | **NEEDS_USER_DEBUG**. No proven ID in image 9; no guessed deletion/hiding. Supply `/bloodborne debug target` while aiming at it. |

C008 #2/#4 happen to have rotationally equivalent rendered geometry, but the
explicit five-object human decision is preserved. #3/#6 equality is checked
against normalized textured geometry before they can share an ID.

Authoring/provenance: `nightmare-qa-report.json`, `nightmare-qa-baseline.json.gz`,
the three `tools/nightmare_*_recipes.py` modules and `build_nightmare_contracts.py`.
C001 correction remains in `build_qa2_trees.py`. No PNG was edited to mask an
unexplained artifact. Existing Catalog B candidates, C-IDs and signatures stay intact.

## Compatibility and honest migration limits

Seven retired composite IDs remain registered/hidden for old saves. They are
not arbitrary aliases to one split child. Together with five prior hidden
tree IDs: 298 logical definitions, **286 user-facing families**, 12 hidden;
47 Contract V2 definitions including retained compatibility families.
The older visible authored logicals receive ALT but are **not newly semantically
reviewed Contract V2 families**.

Raw-source splits use an explicit atomic multi-output transaction: exact raw
pattern/RNG match, all destinations/height bounds/entities/ticks validated,
one source consumption, independent output roots/helper ownership, then commit.
Any collision rejects the whole transaction; context is never absorbed to
make it fit. Raised objects keep the source carrier-level Y as the deliberate
height correction rather than inserting a master into the floor below.
C282 alone keeps the separate original `source_height` state.

**20 of 28 non-tree real source examples migrate; 8 are SAFE_BLOCKED:**

- C282 A/B/C/D: new panel/helper destinations meet original glass panes.
- C654 A/B/C: broad panel destinations meet protected neighboring cells.
- C1491 A: a destination meets the original barrier at (-289,44,-66).

These eight are not migration PASS. Tests require zero committed transactions
and byte-identical source/output fixture trees; the evidence lists exact
coordinates/states. Broad collision has not been removed to force a pass.
Resolving these in-city embedding/ownership conflicts is a separate follow-up.
Free-space positive fixtures exercise all authored split patterns. QA2 complete-tree
source tests remain separate. No full city conversion was run.

`logical/old-logical-migrations.json` explicitly maps all eight old C654
facing/variant combinations to A at the same root, variant and orientation,
with BASE visual. Converter validates old helper ownership; unrelated blocks
and stale links cause a safe refusal. This is an **offline converter rule**,
not a world-load rewrite. Other retired composites remain loadable but are
not silently split on load. Use copies only with `convert_logical_world.py`.

## Two appearance slots

Each visible logical family has `visual=base|alt`, one registry item. Helpers
and hidden compatibility IDs are excluded. 1610 BASE + 1610 ALT states share
the same mesh/anchor/collision/selection/helper data; no visual-state mesh copies.
Missing `visual` in old Minecraft NBT defaults to BASE.

Stable paths:

```
assets/bloodborne_blocks/models/block/logical/<logical_id>/base/<nonvisual-state>.json
assets/bloodborne_blocks/models/block/logical/<logical_id>/alt/<nonvisual-state>.json
```

The bundled ALT is a small parent reference to BASE. A partial pack can replace
only selected ALT files; all others keep BASE. The client prepares models from
the current ResourceManager on resource reload and uses normal baked chunk
quads. No BER, entities, visual ticks or per-position appearance database.

For editable complex geometry the exported JSON uses `bloodborne_polygons`:
vertices `[x,y,z,u,v]`, XYZ in **block units**, UV in 0..16. `#tN` slots are
ordinary declared texture references. Alternatively remove the custom geometry
and author normal vanilla `elements` (vanilla model coordinates 0..16). The
nearest explicit geometry wins; parent texture overrides are inherited and
cycles/missing models/unresolved slots are rejected. Changing visual artwork
does not alter gameplay geometry; do not expect an ALT giant model to grow collision.

Permission 2, player commands:

```
/bloodborne visual get
/bloodborne visual alt
/bloodborne visual base
/bloodborne visual toggle
/bloodborne visual pos1
/bloodborne visual pos2
/bloodborne visual region alt
/bloodborne visual region base
/bloodborne visual region toggle
```

Target commands use the crosshair; helpers resolve to their loaded master.
`pos1`/`pos2` use the player's current block position (move to each corner).
Regions are inclusive, max 32768 cells, loaded chunks only. Roots are deduplicated
even if several helpers are selected. A master outside the box is affected if
its helper is inside. All non-visual properties and existing helper BEs survive.
Output reports scanned cells, unique masters, changed masters and skipped cells/
no-ops. No persistent region rule exists. A dimension change clears the other
endpoint; disconnect/server stop clears selections. No WorldEdit dependency.

## Delivery and reproduction

Files are under `releases/Bloodborne-Blocks/Nightmare-QA-ALT/` at repository root:

- `bloodborne-blocks-2.1.0-alpha.1-nightmare-qa-alt-mc1.20.1.jar`
- `nightmare-qa-alt-gallery-20260923-final.zip`
- `Bloodborne-Alt-Visual-Starter-Kit.zip`

Replace the previous JAR; do not install both (same mod ID/internal version).
The new synthetic gallery includes every 286 visible family, four yaw where
meaningful, bounded open/lit representatives, and three physical BASE/ALT pairs
(1160 total specimens). Neutral white pads are separated by void; creative
flight and the `/tp` entries in `gallery-positions.json` are useful. Per-family
signs and `debug-family-map.json` identify the objects. The arrival pad is safe.
Old galleries/original worlds remain unchanged.

The ALT ZIP is itself a normal 1.20.1 resource pack. Its 1610 editable ALT model
JSONs and copied PNGs represent **post-fix BASE appearance**. Single-texture
models share texture stems; multiple textures use deterministic `__tN` suffixes.
`manifest.json` maps every logical state/model/slot/file and canonical anchor;
animation metadata is included. Edit it outside the ZIP, install at resource-pack
priority above bundled assets, then reload resources. No production art redesign
or intentionally different demonstration texture is included.

From `Bloodborne-Blocks`, with the pinned Java 17 and Python configured:

```
python -B tools/export_alt_visual_starter.py ../releases/Bloodborne-Blocks/Nightmare-QA-ALT/Bloodborne-Alt-Visual-Starter-Kit.zip --force
python -B tools/build_nightmare_gallery.py --output build/a-new-nightmare-gallery
.\gradlew.bat -I tools/verification-direct-resources.init.gradle check build logicalGameTest -PaltStarterKitZip=../releases/Bloodborne-Blocks/Nightmare-QA-ALT/Bloodborne-Alt-Visual-Starter-Kit.zip
```

Set `BLOODBORNE_VANILLA_JAR` to the pinned Minecraft client JAR for source-art
checks; `-PbloodbornePython=<python executable>` selects the interpreter.
The optional direct-resources init script avoids copying ~195k identical files;
it does not filter/change JAR contents. The QA2-only `qa2Checkpoint` task is a
historical gate and must not be used to assert all new source placements migrated.

## Verification and remaining review

The reproducible evidence is in `nightmare-checks/`; release sizes/hashes and
gallery structure checks are recorded with the deliverables. Final results:
**77 Python unit tests, 25/25 dedicated-server GameTests, 2960 orientation checks**;
Gradle `check` and `build` both completed with `BUILD SUCCESSFUL`, including
registry/resource checks. See [commands and limits](nightmare-checks/README.md).
The shipped ALT pack was also read through the actual Java model resolver:
all 1610 models / 2123 texture paths, compared vertex-by-vertex (XYZ/UV) with BASE meshes.
This verifies parsing/dependencies, **not an actual client bake/reload**.
The gallery is checked through vanilla 1.20.1 LevelStorage summaries, not a
claim to have inspected its Singleplayer GUI/rendering.

Coverage: [nightmare-running-review-coverage.md](nightmare-running-review-coverage.md).
NOT_MENTIONED remains: `o_c003`, `o_c046`, `o_c1319`, `o_c474`, `o_c561`,
`o_cases_0`, `o_iron_gate`, `o_iron_railing`. None is marked visually approved.
Future families come from the existing complete Catalog B source-world/resource-pack
candidate pool and subsequent human review, not automated promotion in this pass.

Agent responsibilities in this implementation: `qa_geometry_recipes` handled
isolated artwork/geometry recipes, gallery, ALT resolver checks and test updates;
`qa_atomic_migration` handled atomic split conversion, visual commands, exporter
baseline and migration regressions; `nightmare_integration_review` performed
independent read-only safety/asset reviews. Root inspected visual intent, decided
anchors/compatibility, integrated shared runtime/generated data, corrected issues
found by review and owned final verification/release/Git. No per-family JAR builds.
