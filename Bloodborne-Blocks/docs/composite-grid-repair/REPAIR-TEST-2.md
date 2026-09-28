# REPAIR TEST 2 вЂ” intermediate user test

Branch: `repair/composite-preserving-grid`. Pre-change checkpoint: `cf25cbf1f`.
TEST1 checkpoint: `023ff8c26`. This is NOT beta.4/FULL and does not authorize main integration.
Full-world gates remain FAIL; see `repair-test-whole-world-status.json`. No mass conversion or membership search was run.

## A: reproduction boundary and shared root insertion

The exact nine-output TEST1 fixture is retained. Its window root is (-428,100,32),
with singleton helper (-428,101,32). The east wall roots at x=-427 do not share that
helper. The server item test removes the upper east wall and replaces it by clicking
the helper EAST face; deletion leaves AIR and window NBT remains unchanged.
Thus the user's exact refusal in that original scene has NOT been reproduced.
Do not report that the user-selected cell has been diagnosed without coordinates.

A separate constructed valid window/wall pair exercises the helper admission case.
Previously the helper's nonreplaceability rejected the new root before geometry
placement. A narrowly scoped transaction now validates all existing guests, the whole
candidate footprint and entity collisions before vanilla item placement. The existing
carrier BE survives the server replacement callback, preserving guest ownership before
neighbor callbacks. Client prediction restores the prevalidated bindings; server sync
is authoritative. Root/root, foreign, unloaded and capacity failures remain rejected.
The original hand stack is copied before tag normalization, so refusal preserves it.
No helper blanket replacement, root relocation, new collision mask or second root occurs.

Server tests cover both item orders, three remove/replace cycles, each independent
removal, exact ownership NBT round trip, and neighbor connection changes with a guest.
NBT round trip is not a claim of application save/exit/restart or visual client testing.

## B: one proved construction family

The six IDs in the original scene are owner_58bccf82adec219c90f0,
owner_103c69237444338a3df4, owner_53e58987698a7c96e613,
owner_32dc66a6e819fe19c3b8, owner_16c5223d3d45e3ca0c5f,
and owner_a631fe463a1af9f0fe4e. They map to minecraft:stone_brick_wall,
not o_stone_railing. The bounded manifest `city/reviewed-wall-family.json`
records all 66 exact source aliases and proof from the frozen original JAR:
three multipart templates using the same stone_bricks texture.

`building_stone_brick_wall` is a construction adapter in the existing architecture
runtime: four facings, 16 low and 16 tall neighbor masks, all existing whole-owner
mesh and cell geometry references. Pick/drop of confirmed aliases yields this one
building item without frozen connections. All old IDs/states remain registered and
unchanged. The original fixture keeps its historical states; new manual builds update
connections. No unrelated wall/railing family is merged.

Supported: four orientations, pair, corner, T, cross, neighbor removal, tall profiles
under a full solid block. Low cross reuses the existing POSTLESS variant; low cross
WITH a central post is unsupported. No artwork was invented.

## Kit

The five original specimens remain at original coordinates. A clearly constructed
window/wall regression stand and connection examples are added near (25,64,0).
Only the same five source-authorized atomic transactions are converted. The immutable
latest-modded-world.zip input hash remains
c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9.

See release README-RU.md for installation and manual tests. Graphical client and full
restart checks remain for the user. Verification results are recorded with the kit.

## Recorded verification

50/50 dedicated-server GameTests PASS, including C001/C474/C618 and the new item cycles.
City compatibility bootstrap PASS (3,778 blocks / 52,212 states). Three bounded wall
proof tests PASS. Remapped JAR built and embedded source resource bytes verified.
Small world: five transactions, 27 helper carriers, zero orphans, second pass zero.
C282 test fixtures now reserve the existing 32-cube template instead of EMPTY_STRUCTURE;
all assertions unchanged, preventing interference from out-of-template helpers.
