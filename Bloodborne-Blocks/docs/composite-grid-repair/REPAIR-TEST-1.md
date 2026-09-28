# Practical intermediate kit — repair-test.1

This checkpoint remains on `repair/composite-preserving-grid`. It is NOT
beta.4, NOT a FULL world, and does not authorize integration into main.
The existing full-world conversion still has failed protected/composite gates.
No historical memberships were added for this kit.
The current strict shared-binding checker reports 6,518 failed protected
occurrences and 355/3,238 passing atomic graphs on the unchanged diagnostic
world (formerly 356 with the scalar checker). This is not a fresh conversion
or a claim of fewer unresolved groups; see `repair-test-whole-world-status.json`.

## Scope

`tools/build_repair_test_kit.py` verifies the SHA256 of latest-modded-world.zip,
compares every selected source cell against that immutable archive, and uses
the existing converter and frozen owner graphs. Five transactions include
C001, C474, C618, the complete nine-output connected-fence graph
`historical-owner-9ebfaabd9b29ac253a18`, and the two-owner root/helper graph
`historical-owner-00f791aa1871c8986556`.

Source coordinates, masks, models and pivots are unchanged. Artificial floor
and free building areas make these isolated specimens usable. Surrounding city
blocks are intentionally not part of this specimen world. In particular,
C618's adjacent historical panel is not converted or substituted here.
This is not evidence of preserving the surrounding city.

Shared physics is enabled only for the two selected atomic graph fixtures.
The production graph compiler still defaults to disabled. Foreign cells,
root/root collisions and unresolved owner fragments remain fail-closed.

## Verification boundaries

The small world passes independent source/ledger authorization, saved-cell
verification, exact helper audit (26 carriers, no orphans), and a second pass
with zero changes and byte-identical files. These are converter checks, not
gameplay or visual proof.

Dedicated-server GameTests exercise actual item placement from picked items,
cardinal placement, connected railing updates, adjacent construction and
whole-object destruction. Shared runtime tests cover guest bindings, both
removal orders, foreign replacement, strict NBT round trips and unloaded roots.
NBT round trips are not a claim of a full application restart.

Graphical rendering, manual rotation/pivot assessment and actual client
save/exit/restart remain **NOT RUN**. The delivered Russian checklist explicitly
requests them. Do not mark whole-world gates PASS based on these local tests.

## Existing conflict census remains the baseline

Of the 2,862 previously blocked physical groups: 2,380 have positive collision
volume overlap, 437 disjoint volumes sharing a cell, 43 root/root conflicts,
and 2 empty-collision reservations. These exclusive classes and frequent owner
pairs are recorded in `PHYSICAL-CONFLICTS.md`. No new reduced full-world count
is claimed; broad conversion activation awaits practical test feedback.

## Runtime integration

Logical and whole-owner root classes can carry guest bindings without allocating
block entities on ordinary roots. Existing modular city pages retain their
original class. Shared cells union approved owner shapes without modifying any
mask or shifting roots. Plural helper interactions never arbitrarily select
the first owner; target a specific root for picking or actions.

Scalar helper consumers (surface placement, furniture climbing, visual commands
and debug output) now distinguish shared cells. Breaking a root preserves loaded
guest ownership; replacement is rejected synchronously when a guest root is
unloaded, avoiding reliance on a volatile repair queue across restart.
Logical variant models now preserve their variant on ordinary item drops,
matching the existing city-model behavior. The exact C001 tree variant is
checked through pick/drop and item placement. A test helper initially failed
to put the supplied tagged item in the mock player's hand; that fixture was
corrected rather than changing the production placement policy.

Manual insertion of a new root into an occupied helper is still rejected. This
test build must not be described as a completed universal assembly editor.
The isolated railing/upper-owner pair also distinguishes snapshot and gameplay:
the converted snapshot retains the historical east/south connection flags,
whereas normal item placement and neighbor updates recompute fence connections
without the absent surrounding neighbors. Root IDs and guest ownership must
survive that legitimate state transition; identical frozen connection flags
are not promised after manual rebuilding in a different neighborhood.

Two existing GameTest expectations were brought in line with already-approved
contracts: registry coverage includes declared city IDs, and support-plane
assertions account for the two explicit upper-root exceptions. Foreign blocks,
forbidden fragments and geometry/anchor invariants remain checked.
