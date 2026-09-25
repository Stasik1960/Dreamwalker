# City compatibility continuation — 2026-09-25

Scope: finish city compatibility on top of the existing 49-family beta. The
user excluded client/server playtesting and will perform it themselves.

## Implementation

- Historical assets come from the checksum-verified 2.0.1 JAR; observed states
  come from the frozen MODDED inventory. No vanilla-world substitution.
- 2,865 bounded module pages (at most 16 variants each) and 49 observed native
  carriers share the existing ArchitectureBlock/GeometryRuntime/static mesh
  renderer. There is no new entity renderer or ticking block-entity system.
- 45,616 observed module states map to 44,378 deduplicated, cell-local meshes.
  The city registry has 48,632 states; production remains 49 families / 1,644
  states. Shared geometry profiles avoid repeated preparation.
- Historical module collisions contain at most four boxes per cell. Native
  detailed collisions are conservatively merged to four boxes, with one
  outline box per cell. Soft vegetation module collisions remain empty.
- Full cubes retain opacity and face culling. Polygon data is loaded only on
  the client. Picked/dropped variants keep their state, name and inventory mesh.
- Page IDs are frozen to the reviewed inventory checksum. Future additions
  must append pages; repaging an existing save is prohibited.

## Conversion and preservation

`convert_logical_world.py --source-mode modded --city-compat` first runs the
existing production conversion to a fixed point, then changes only remaining
module palette entries. This latter step makes **no coordinate changes**.
The independent checker verifies the resource hashes, all block cells, ledger
authorization and nonterrain files. The preservation verifier also checks
typed NBT outside authorized edits. A global helper audit checks both owner
geometry and missing helper entities.

Rejected production assemblies remain in their original compatible geometry:
incomplete groups, weighted visual variants, ambiguous overlaps, foreign
decor and helpers owned by other objects are not forcibly erased. This is
retention of authored sections, not a claim that every such group became a
new logical production object.

The 240 vanilla-reference differences remain historical material/state
differences; the production ledger is unchanged from beta.1. The reference
world is not used to overwrite MODDED edits or invent coordinate corrections.

## Remaining blocker

The original inventory has 23 transient composite IDs / 33 cells without
their generated assets. Production conversion resolves some of those cells;
the aggressive copy retains **9 missing IDs / 13 cells**. See
[missing evidence](MISSING-MODELS.md) and
[remaining positions](remaining-aggressive-models.json).

The provided 1.2.1 JAR was also inspected: it has no `m_*` palette, and no
missing IDs. Its native definitions/geometry equal 2.0.1, although 296 model
JSONs differ. It does not supply the missing composite assets. See
`user-jar-1.2.1-check.json`.

Normal conversion fails closed if any city ID remains unknown. The explicit
`--allow-unresolved-city` switch creates diagnostic copies while preserving
unknown cells and marking registry QA **FAIL**. Such copies are **not final
playable cities** and must not replace the live world.

Static/offline checks do not establish in-game performance or graphical
acceptance. No Minecraft client or dedicated server was launched for this
continuation, as requested.
