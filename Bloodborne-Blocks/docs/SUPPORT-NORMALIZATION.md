# Contract V2: support-plane normalization — 2026-09-24

Scope: the **47 existing Contract V2 families / 740 states**, including BASE/ALT
and 12 hidden compatibility families. This is not a conversion of the remaining
251 non-V2 logical families, not another semantic review, and not batch-03.
No original world, discovery, C-ID, source pattern, texture or raw mesh changed.

## Prepared data, not runtime geometry analysis

`tools/normalize_support_contracts.py` is the common offline finalization gate.
Gradle `normalizeSupportContracts` precedes `processResources`, `check`, all
registered Python checks and JavaExec tasks. Direct command:

```powershell
python -B tools/normalize_support_contracts.py --write --report docs/support-normalization-audit.json
```

Without `--write` it rejects data that needs normalization. Existing/future V2
families use this same pass; no list of C-IDs chooses which families to correct.
Repeat runs are byte-idempotent and retain the first before/after audit when its
output hash still matches. The immutable input for this checkpoint is
`support-normalization-baseline.json.gz`; unit tests normalize it independently
and compare every family to current data.

For FLOOR, compute minimum actual polygon-vertex Y after the prepared render
offset. A negative minimum below epsilon 1e-6 adds `-minY` to that offset.
Already-positive meshes are not lowered. The same offset translates authored
selection/collision primitives; collision below the floor is clipped away.
Reproject helpers from the changed gameplay primitives plus authored
interaction-only cells, never from render bounds. Remove all FLOOR cells with
dy<0. The master cell, pivot, raw mesh and migration source coordinates remain
unchanged. Manual and migrated placements consume the same final state data.

Normalization can change the footprint of a standalone fallback fragment. The
migration resolver still requires an explicit `supersedes_targets` declaration
and strict source containment before preferring a complete reviewed assembly.
Containment covers actual source cells and ownership-proven existing helpers,
including reused helpers, but not future writes of the discarded fallback.
Those prospective cells are never written; foreign-block protections remain.
Converter and independent verifier implement the same boundary.

Non-floor placement requires an explicit axial inward `support_plane` in the
canonical north frame: `normal dot point = distance`, with a documented reason.
The plane and translation rotate around the existing `(0.5,0,0.5)` pivot.
Missing/ambiguous mount planes are not inferred from names or artwork. FLOOR
with conflicting plane metadata is a hard failure, not an ambiguity bypass.
The currently supported runtime placement policies remain FLOOR/WALL_ADJACENT;
this does not implement new ceiling placement mechanics.

## Collision policy

Existing justified semantic primitives win: NONE, TRUNK, POST, SIMPLE_BOX,
TWO_BOX, etc. Ordinary decoration is limited to three global primitives.
Functional DOOR/GATE/FENCE/WALL/STAIRS keep their mechanical policy budgets.
Clipped per-cell storage slices are **not** separate authored collision boxes.

For missing policy or excessive ordinary collision, `support_collision.py`
provides a conservative build-time fallback. It proves closed axis-aligned
compact masses from six full rectangular faces, excludes tiny hardware,
prefers one dominant mass, and only permits two/three substantial separated
masses. It never emits one collider per render cell/element. Explicit tree/post
hints select a central vertical mass; explicit windows can use a thin plane.
Open/sparse/rotated or otherwise unproven art, too many major masses, unsafe
unions and budget-exceeding inputs require manual review. Every facing/visual
state is independently canonicalized and simplified; transferring a proposal
requires equivalent canonical policies and boxes, not just matching state names.
Unresolved invalid collision stops publication rather than silently using NONE.

Current V2 authored collision was already simple: **1020 → 1020** global boxes
summed across all 740 states; ordinary maximum **2**. There is no real current
family with “27 → 1” to claim. That reduction is covered by the explicitly
synthetic `test_future_excessive_collision_synthetic_27_to_one` fixture.
JSON evidence also records source polygon count, primitive count, exact union
volume / render-AABB-volume ratio and multi-box justification. Raw source-cuboid
count is unavailable from untagged meshes; polygons are not labelled as cuboids.

## Audit result and unresolved cases

- 43 FLOOR families; 13 penetrated below the plane and were corrected
  automatically (3 visible and 10 hidden compatibility families).
- Below-floor helpers existed in two families: hidden `o_dead_tree_planter`
  and visible `o_iron_gate`. Neither new footprint needs them. All floor states
  now preserve solid support blocks; old removed helper cells are cleaned by
  existing queued chunk-load ownership validation.
- Four unchanged mount ambiguities: `o_c654`, `o_wall_deco_1`, `o_c654_a`,
  `o_c654_b`. Their precise wall support planes require authored evidence.
- `o_c282_b` retains its functional source-height collision, but is flagged
  `COLLISION_EXCESSIVE_EMPTY_VOLUME`: its volume exceeds the rendered AABB.
  This warning is not silently corrected by changing door gameplay.
- 0 hard audit failures. Full per-state evidence:
  [JSON](support-normalization-audit.json), [Markdown](support-normalization-audit.md).

Old helper cleanup cannot reconstruct an unknown support block that an earlier
version already replaced. This pass prevents new platform damage; it does not
guess historical blocks or mutate the original city to fill old holes.

## New isolated gallery

`build/support-gallery-saves/support-plane-gallery-20260924` contains all
740 states plus six BASE/ALT proof placements: **746 masters / 3144 helpers**.
All 47 families are shown; hidden compatibility versions are explicitly labelled.
The verifier checked **704 floor specimens / 7778 complete support cells**.
Root Y is always 64, white concrete Y=63; there are no C-ID height adjustments.
Pads cover rendered horizontal extent, helpers and master, separated by void.
Use the bundled `gallery-positions.json` teleport commands. Four wall families
remain labelled ambiguous in the audit, not certified as floor-mounted.

```powershell
python -B tools/build_nightmare_gallery.py --contract-only --output build/support-gallery-saves/support-plane-gallery-20260924
python -B tools/package_nightmare_gallery.py build/support-gallery-saves/support-plane-gallery-20260924 ../releases/Bloodborne-Blocks/Support-Normalization/support-plane-gallery-20260924.zip docs/support-checks/gallery-package.json
```

Generation/package destinations must be fresh. Previous delivered worlds and
JARs are preserved. The standard 1.20.1 LevelStorage summary loader accepted
the new world's metadata; this is not a client GUI or visual gameplay check.

Delivery: [Support-Normalization](../../releases/Bloodborne-Blocks/Support-Normalization/).
The runtime version remains `2.1.0-alpha.1`: replace the old JAR, do not install
both. Prior ALT starter resources remain compatible; the runtime applies the
new prepared render transform to the selected visual slot.

See [verification](support-checks/README.md) for actual completed commands and
limits. Visual client acceptance remains outstanding. No full-city conversion
or additional family approval is implied.

## Delegation

- `support_policy_scout`: read-only inventory and generator-path diagnosis.
- `support_collision_simplifier`: bounded offline collision fallback and tests.
- `support_platform_gametest`: actual server platform/placement/ownership tests.
- `support_gallery`: scoped gallery generation and packaging changes.
- `nightmare_integration_review`: independent read-only review; identified the
  FLOOR-plane bypass and unsafe cross-facing collision transfer, both corrected.
- `support_migration_review`: independent read-only review of the migration
  containment correction and existing-helper protection.
- `support_migration_fixture`: additional temporary-world migration regressions.
- Root: normalization, integration/validators, correction of review findings,
  execution of checks, artifact verification, documentation and final delivery.
