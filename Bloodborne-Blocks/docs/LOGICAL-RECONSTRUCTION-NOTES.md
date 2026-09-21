# Remaining reconstruction evidence

These are unresolved design inputs, not implemented objects. Coordinates below
are original model units (16 per block); they are not private world positions.
Do not infer an object's purpose from its vanilla carrier ID.

## Ornate balustrades (#6, #8)

The prior assignment of `deepslate_tile_stairs` and
`smooth_red_sandstone_stairs` to this case was wrong: their authored straight
models are windows. Their `*_inner.json` / `*_outer.json` vanilla fallbacks
remain unsuitable for Bloodborne corners. The actual reviewed balustrades use
`warped_trapdoor_open` and the top `cut_copper_stairs` variants. These now have
whole connected families; see `NORMALIZATION-ACCEPTANCE.md` for unverified work.

The `warped_trapdoor` carrier has several genuinely different oversized meshes:

- `warped_trapdoor_bottom_0`: `[-8,-2,-1]..[24,3,21]` plus
  `[-8,3,5]..[24,16,15]`.
- `warped_trapdoor_open`: Y range `-16..15`.
- `warped_trapdoor_open_1`: `[-8,-16,1]..[24,32,11]`.
- `warped_trapdoor_open_2`: Y range `-16..32`, thin rails near Z=3 and Z=16.
- `warped_trapdoor_top`: `[-8,0,5]..[24,16,15]`.

Further work must establish the whole bay's lower/upper/corner correspondence
and exact placement grid from source geometry and map neighbourhoods. The
existing smooth `o_stone_railing` does not satisfy these two ornate families.

## Multiple lamps / statue assembly (#22)

`dead_fire_coral_block` has 14 elements, not one streetlamp mesh:

- Elements 0–5 and 6–11 are two post/lamp groups, centred in X/Z at `(20,-3)`
  and `(-3,20)` respectively. Lamp bodies are elements 3 and 9.
- Elements 12–13 are a central two-block-tall assembly centred at `(8,8)`.

`dead_tube_coral_block` also has 14 elements: central assembly 0–1, lamp groups
2–7 and 8–13 centred at `(19.5,19.5)` and `(-3.5,-3.5)`; lamp bodies 5 and 11.

The standalone `lantern` source is six elements with different dimensions and
placement, despite sharing a lamp texture. Reusing it at an assumed integer
offset is not geometrically exact. `asset_1_spirelamp_001` is a separate whole
model, a single element bounded by `[-9.5,0,-9.5]..[25.5,32,25.5]`.

Separating one composite source into independently removable lamps needs
explicit one-to-many migration output and attachment placement, with an exact
local offset encoded in a state/model. The current one-root migration contract
does not provide this. Extending it is within the user's requested scope, but
must preserve all old visual positions and reject occupied attachment cells.
Do not substitute a new whole composite object and call lamp separation done.

## Side-bracket street pole (#10)

A further authored-model scan for tall asymmetric geometry did not establish a
match. `iron_ore` (five broad slabs/plates, 48 units high), `gilded_blackstone`
(five concentric/stacked elements, 48 units high), and `spruce_planks` (three
clock-like planes) are not the pictured thin pole with an offset top bracket.
Do not reclassify them on texture names alone. It may be an assembly of smaller
source pieces; the tall-model scan cannot rule that out. A source-world anchor
coordinate or targeted block ID was requested non-blockingly to narrow the
neighbourhood reconstruction.
