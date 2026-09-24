# Production palette cleanup — 2026-09-24

Historical cleanup checkpoint. **Superseded by the 56-family functional restoration**:
see [current report](PRODUCTION-RESTORATION.md). Counts below describe the earlier
33-family build, not the current runtime. Frozen evidence is deliberately retained.

Authoritative runtime palette: **33 logical blocks/items + one internal helper**.
This is a cleanup of the existing implementation, not a new mod, city conversion,
or a claim that every architectural feature in Yharnam has been reconstructed.
The user confirmed that only the correct vanilla source world must be preserved;
old modded saves and QA galleries do **not** justify retaining obsolete registry IDs.

## Cause and measured result

Three generations were loaded into the same registry: 503 resource-pack carrier
blocks, 46,236 per-cell modular blocks, and 298 logical objects. Later manual splits
and merges were added while superseded composites and compatibility IDs survived.
Source carriers, mesh fragments and orientation alternatives thus became game objects
without independent semantic approval. Runtime loading now requires exact agreement
between definitions, Contract V2 and the production manifest. The old loaders and
item/placement compatibility redirects were removed, not merely hidden in Creative.

| Metric | Before | After |
|---|---:|---:|
| Logical definitions | 298 | 33 |
| Visible logical items | 286 | 33 |
| Total registered blocks, including helper | 47,038 | 34 |
| Hidden historical logical IDs | 12 | 0 |
| Initial audit `COMPATIBILITY_ONLY` category | 5 | 0 |
| Blockstate JSON files | 47,038 | 34 |
| Model JSON files | 101,361 | 558 |
| Byte-identical duplicate model files | 41,730 | 40 |
| Unique referenced logical textured meshes | 1,552 | 222 |
| PNG files | 323 | 24 |
| Duplicate PNG contents | 47 | 0 |
| QA specimens, last support gallery | 746 | 33 |

Exact final JAR sizes/hashes are in [resource comparison](production-resource-comparison.json)
and [artifact verification](production-checks/artifact.json). The previous JAR was
97,371,528 bytes; the production JAR is approximately 0.85 MB (over 99% smaller).
This reduction comes primarily from removing unapproved runtime layers, not from
claiming every former object was preserved in fewer bytes.

The source-tree pruning removed 195,143 obsolete generated files in its initial
apply. Git preserves the exact removed paths and prior contents. This is not a
history purge. Original archives and historical release files were not modified.

The 265 excluded logical definitions are individually recorded:
**6 fragments, 6 superseded composites, 1 orientation duplicate, 1 remaining
compatibility-only family, 251 unresolved families**. Categories are exclusive:
old hidden tree/door fragments are counted as fragments, not twice as compatibility.
`UNRESOLVED` means no independently approved final object after reconciliation;
it does not mean proven useless, rare, or geometrically identical to something else.

The 40 identical model wrappers remain stable item/visual override paths; their
large polygon payloads are not duplicated. BASE/ALT remain states of the same ID.
Unpainted ALT slots inherit BASE; physics, anchor, helpers and gameplay are unchanged.
The 222-entry mesh catalog has no duplicate payloads or unreferenced entries.
The renderer remains baked/chunk based, not a dynamic block-entity renderer.
JSON `elements` geometry (1,015 → 1 unique entry) is a separate metric: the remaining
entry is the internal helper model, while real logical artwork resides in the meshes.

## Manual corrections have priority

| Review | Final production meaning |
|---|---|
| C001 / C009 | One complete `o_c001` tree/item, with six whole-tree variants. Four old branch/half IDs and `o_dead_tree_planter` are gone. |
| C008 | Four distinct statue IDs: `_1`, `_2`, `_3`, `_5`. `_4` is exactly `_2` with facing −90°. The source assembly still produces six placements atomically, not four statues total. |
| C1491 | Three graves, `_a`, `_b`, `_c`; old composite removed. |
| C1962 | Two sacks, `_a`, `_b`; old composite removed. |
| C1979 | Five reviewed groups; source components 1/11 remain context. |
| C471 | Two spires, `_a`, `_b`; no redundant facing property. |
| C282 | One `o_c282` double-door item/master, no `_a`/`_b` leaf items. |
| C654 | Windows `_a` and `_b`, A/A and B/B. Correct source pattern selects A; no old two-sided composite. |

The machine-readable before/final ID table is
[production-review-reconciliation.json](production-review-reconciliation.json).
Debug now distinguishes Source Review, Logical/Object ID and Semantic part.
Inherited misleading bookshelf labels for reviewed sacks/spires were corrected in
English/Russian and gallery labels; C1979 uses a neutral reviewed-composition label.

### C282, precisely

The source pack supplies one panel (`dark_door_1`), not an authored frame. The
manual centre seam divides it into two leaves in **one** logical object. At canonical
facing, pivots are `(x,z)=(-1,0.4375)` and `(2,0.4375)`, not the object centre.
Opening rotates left/right leaves +90°/−90° around their respective outer hinges;
the master stays fixed. Whole-object facing then orients both closed/open models.
The floor master is one block below the reviewed carrier origin, preserving world
art position. Collision and owned helpers change atomically with the state.

Source glass panes remain foreign context, never silently claimed by the door.
If original context occupies a required helper cell, placement/conversion is
**SAFE_BLOCKED**, with no partial writes; this remains a migration limitation.
Actual pane-coordinate tests cover this rejection and both blocked opening/closing.
No successful city conversion is implied by finding a source occurrence.

## Full-source evidence, not automatic semantic publication

Read-only scan of all **33 `eh_s2:yharnam` terrain regions / 21,503 chunks** found
27,119,750 indexed carrier cells and 2,605 observed states. The universe comes from
the resource-pack overrides plus archived source mapping, not small category seeds.
It produces 158,590 directed state/offset neighbor edges with conditional frequencies.
Actual positional weighted variants use Minecraft 1.20.1 RNG semantics; vectorized
selection is checked against the separately verified scalar implementation.

64 exact historical reviewed patterns were tested; 52 occurred, totaling 1,856
pattern matches. These are overlapping evidence counts, **not** disjoint objects,
placement-safe conversions, or newly approved families. Current pruned migration
compiles 52 rules, including 11 atomic split transactions, all targeting production IDs.
Current patterns are preserved separately from historical scan signatures/origins.

All carrier coordinates/state IDs are regenerable from the immutable original and
stored locally in `build/production-usage/positions.npz`; they are intentionally not
another committed 48 MB world-derived cache. Reviewed exact occurrence coordinates,
per-state applications, orientation, weighted results and neighbor evidence are in
[source usage](production-source-usage.json.gz) / [summary](production-source-usage-summary.json).

Important limits: the graph measures six-axis carrier adjacency, not arbitrary
semantic boundaries. `carrier_boundary_faces_by_state` means no other indexed carrier
in that cell direction; it is **not** accurate world-space visible-face/occlusion
analysis. [Geometry audit](production-geometry-audit.json) measures transformed mesh
edge connectivity/topology, not whether adjacent world geometry belongs to one object.
Neither metric grants publication. This pass does not solve generic whole-city
semantic reconstruction or exact world-space face exposure.

## Evidence and reproduction

- [Frozen baseline](production-baseline.json): original 298-family inventory, before pruning.
- [Reconciled inventory](production-palette-inventory.json): every former logical ID,
  source usage, provisional/final status and migration relevance.
- [Production manifest](production-logical-palette.json): approved final IDs, semantics,
  raw and compiled source patterns, occurrences, state relationships and exclusions.
- [Provenance index](production-provenance-graph.json), [pruning](production-resource-pruning.json),
  [resource metrics](production-resource-comparison.json).
- `production-authoring-inputs.json.gz` freezes reviewed definitions/contracts/meshes
  before deletion; `pre-production-source-mapping.json.gz` retains source-carrier mapping
  as offline evidence, never runtime registry data.

Compiler: `python -B tools/build_production_palette.py` creates a validated dry-run
stage. `--apply` publishes only the explicit managed-resource plan. Original ZIP hash
checks precede generation. Do not rerun historical carrier/modular generators over the
production output. Their evidence remains for audits, not as an alternative live palette.
For scan reproduction use `tools/scan_production_usage.py`; `--reuse-scan` validates
input/cache hashes before reusing decoded cells. Supply the actual vanilla client asset
JAR via `BLOODBORNE_VANILLA_JAR` if it is not in the default Loom cache.

## QA and remaining boundaries

See [executed checks](production-checks/README.md). Fresh production gallery has one
canonical BASE specimen per final ID, 93 owned helpers and 327 solid white support
cells. It is a disposable fixture, not the city. Both original source ZIP hashes remain
unchanged. No batch-03 or production city conversion was run.

Human work still needed is targeted: client visual acceptance of the 33 specimens;
identification/debug of the previously unspecified fence; semantic decisions before
promoting any of the 251 unresolved candidates; physical mount-plane clarification for
the three retained wall families (`o_wall_deco_1`, `o_c654_a/b`). Do not ask for a fresh
review of hundreds of fragments as if they were accepted production objects.
Some exact source placements remain blocked by foreign context and must not be
reported as converted. Old modded saves/QA galleries are intentionally incompatible
with this pruned registry. Keep their historical JAR if inspecting those artifacts.
