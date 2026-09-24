# Production coverage restoration — 2026-09-24

## Result and boundaries

**24/251 audit-filtered historical families covered**: 21 restored directly
(**21/21 required functional families**) and three old prop families explicitly
merged into the C003 successors. Production contains **56
logical blocks/items**, one internal helper, **1780 states / 770 textured meshes**.
The other **227** audit-filtered families are explicitly `INTENTIONALLY_DEFERRED`
pending semantic review. Their individual IDs/reasons are in
[coverage.json](production-restoration-checks/coverage.json), under `exclusions`.
This is not a claim of complete resource-pack coverage or complete-city migration.

Inputs were the frozen audit, cached `world-state-usage.json.gz`, source mapping,
source-pack geometry, and saved manual decisions. No discovery or source-world
rescan was run. Original archives, C-IDs and discovery signatures remain unchanged.

## Restored behavior

- `o_acacia_door`, `o_birch_door`, `o_dark_oak_door`: preserved authored fixed
  header/frame and two hinge-rotated leaves; V2 master does not rotate on use.
  Closed/open gameplay footprints use explicit two-panel primitives, not the
  bounding box of the whole door. No standalone leaf-items. Exact raw stair pairs
  produce one closed door with its floor-root offset; complete dark-oak patterns
  supersede the overlapping single-carrier C282 interpretation only when complete.
- `o_shuttered_window`: raw trapdoor open state retained; two open-wing collision
  primitives exclude the stationary wall/background panel. Wall-adjacent origin.
- Six connected families: stone railing, ornate/carved/stepped/high balustrades,
  stone curb. Connection properties replace direction-specific registry IDs.
  Asymmetric artwork retains explicit facing as well as connection mask.
- Three ladders: climbable root/owned helper cells; no obstructive collision.
- Six light families: candles, lanterns, wall lantern, lantern, lightning rod,
  oak-wood carrier family. `lit` toggles server luminance 0/15.
- Two benches: seating uses the authored global seat plank, not a helper-clipped
  sliver of the backrest. Seat height is checked on the dedicated server.

`tools/restore_functional.py` uses archived geometry/source rules as evidence;
it authors new contracts, simple collision primitives and raw vanilla patterns.
It does not re-enable legacy registry loaders or hidden compatibility items.

## Authoritative C003 correction

| Source components | Production family | Model variants |
|---|---|---|
| 1, 3, 6, each independently | `o_barrel` | `barrel_0..5` |
| 2 | `o_books` | `books_0..1` |
| 4 | `o_bag` | `bag_0..3` |
| 5 | `o_cases_0` | `cases_0` |

Each source cell migrates independently at its original XYZ. RNG guards reproduce
the resource-pack weighted choice using that source position. The three barrels
are never one transaction or one multi-block. `visual=base|alt` is orthogonal to
`variant`; CASES has only one model and therefore no singleton Minecraft property.
`o_c003` is superseded and removed from production block/item resources.
The former `o_barrel_0`, `o_books_0`, `o_bag_0` IDs are explicitly
`MERGED_WITH_SUCCESSOR`, not deferred or re-registered as separate variants.

The saved C003 decision/history is updated without rewriting discovery. Earlier
reviewed `o_c046`, `o_c1319`, `o_c1962_a/b` remain available as approved palette
objects, but their overlapping raw-source rules explicitly redirect to CASES,
BOOKS and BAG respectively, preventing competing migrations. C002 and C1979 are
not split/merged/reinterpreted. Tests compare their source patterns and textured
geometry against frozen authoring inputs.

## Independent coverage / exclusions

`docs/required-production-families.json` is a handwritten required set, **not**
generated from filtered production selection. `tools/production_coverage.py`
joins frozen source mapping and cached census to required family, V2, runtime
registry input and gallery specimen. Live registry is independently tested by
Fabric GameTests. All historical exclusions require an approved status and reason.
Zero-use alternative carriers need a per-carrier reason (`soul_lantern` and candles),
not merely an observed sibling carrier.

Regression checks remove a required family from all three generated manifests
and still require failure; also cover missing source rules, gallery omissions,
successor integrity, C003 independent occurrences and old semantic preservation.
The legacy-mode converter now refuses empty legacy rules when V2 patterns exist,
before copying anything. Current raw-source migration requires
`--source-mode original-v2`; no production city conversion was performed here.

## Gallery and remaining QA

Fresh `production-functional-gallery-20260924`: 56 canonical objects + six open
interactive examples + two ALT proofs = **64 specimens**, 457 owned helper cells.
No historical/compatibility IDs. Wall-origin specimens are elevated only within
this QA layout so below-origin helpers cannot replace the white platform.

Collision is deliberately simple gameplay geometry, not a union of every mesh
element. C003 artwork support lift is at most one source pixel, while its world
root is unchanged; curb uses a documented 1/8-block support correction. Integer
floor-root shifts for restored assemblies preserve their raw world artwork position.
Seven wall-mounted families retain ambiguous mounting-plane warnings from the
support audit. These are not silently declared visually approved.

Client visual acceptance remains required: doors/frame hinges, all light variants,
wall mount depth, ladder appearance, C003 item/placed variants, and BASE/ALT proof.
Automated success does not replace that review. Detailed executable evidence and
artifact hashes are in [checks](production-restoration-checks/README.md).

## Agents

- `restore_functional_scout`: read-only source geometry, hinges and carrier evidence.
- `functional_restore_builder`: initial V2 compiler, gallery/coverage/tests,
  synthetic migrations and fail-closed converter guard.
- `production_integration_review`: independent runtime/migration review; identified
  collision, seating, raw-pattern and default-converter risks before handoff.
- Root: C003 compiler/decision, integration and collision corrections, coverage
  hardening, final tests, artifact verification and release documentation.
