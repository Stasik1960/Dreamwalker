# Logical objects: implementation record

This pass continues the existing Bloodborne Architecture mod. Legacy and v2
registry IDs remain readable. The source world is never edited.

## Sequence

1. Audit the complete resource graph and recover exact model/state provenance.
2. Generate logical object definitions and an explicit inverse migration table.
3. Implement object placement, interaction, connections and ownership in the
   existing runtime; preserve the nonblocking chunk validation boundary.
4. Convert a new copy of Ether 2.0.2-positions and independently verify its data.
5. Run data checks, compilation/build and a read-only integration review.

## Resource contract

`bloodborne_blocks/logical/definitions.json` extends the existing definition
format with `logical: true`, `behavior` (`static`, `connected`, `door`, `gate`,
`shutter`, `ladder`, `lantern`, `bench`), optional `connection_family`, and `models`
(complete state key -> mesh key). Existing properties/default/states fields
remain the registry contract. IDs use `o_`; rotations and open/closed are states.

Optional `placement_properties` fixes the state of newly placed/picked items,
without rewriting the default of old saved blocks. This separates standalone
shutters from their preserved wall-backed state. An `attachment_item` refers
to a registered logical item; its owner's `lantern` property is false for new
placement, true only after successful attachment. Conditional loot returns the
installed item once. Attachment preflights all cells on the server.

`logical/geometry.json` uses the existing per-cell geometry schema. Parts retain
the existing `architecture_part` registry ID and `Root`/`Owner` NBT contract.
`logical/meshes.json.gz` stores uncut object polygons (XYZ in block units, UV in
0..16). Mesh keys are internal resources, never registry IDs.

`logical/migration.json` has `schemaVersion: 1` and `rules`. Each rule has:

```json
{
  "source": {"id": "legacy_id", "properties": {}},
  "target": {"id": "o_object", "properties": {"facing": "north"}},
  "offset": [0, 0, 0],
  "components": [{"offset": [0, 0, 0], "id": "m_component", "properties": {"facing": "north"}}]
}
```

Source IDs/properties refer to exact legacy registry states. Components are the
exact v2 expansion at the source origin; `null` means no proven v2 inverse is
available (not an empty group). `offset` locates the logical root relative to
that origin. A rule may include additional legacy members in `members`, using
the same offset/id/properties format as components, for authored multi-model
assemblies. A missing member must never be invented by world conversion.

Optional `supersedes_targets` explicitly permits a complete matched assembly
to replace contained fallback matches. The fallback source must be a strict
subset, the dimension identical, and complete target states different. All
existing source cells and ownership-proven helpers/debris of the fallback must
be contained in the assembly's touched cells. Prospective writes of a discarded
fallback are not existing object parts and do not require containment; they are
never emitted. Existing helpers remain protected even when the fallback would
have reused them rather than classified them as stale.
Only a valid assembly surviving all unrelated overlap conflicts can suppress
a fallback. Same-ID/different-state matches are supported for the statue.
Both converter and independent checker index touched positions; disjoint
objects do not incur an all-pairs scan.

Conflicting/partial/ambiguous matches remain untouched and are recorded. New
logical objects are idempotent conversion outputs. Private position ledgers and
world copies belong under ignored `build/`, not public documentation.

When a matched legacy root/member already owns service cells, the converter
adopts only `architecture_part` blocks with typed `Owner` / `Root` NBT matching
that exact piece and an offset in its exact legacy state geometry. Conventional
door sibling cells are excluded. Proved helpers outside the new footprint are
removed; helpers inside it are rebound to the new root. Other roots' helpers
remain protected. Reports hash legacy geometry in addition to both definition
files, the logical geometry and migration rules; the independent checker
reconstructs the same ownership proof rather than trusting reported edits.

## Input evidence

Local `Ether-Bloodborne-2.0.2-positions.zip` and the `(1)` copy are byte-identical:
SHA-256 `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
The original resource pack is available as `bloodborne.zip` in Downloads.

## Historical alpha baseline

Additive logical registry, object meshes, runtime ownership and the fail-closed
inverse converter are implemented. Offline visual integration rejected 86
automatic candidates which were actually vanilla fallback models. The current
palette is 76 objects / 388 states / 546 exact migration rules, not the earlier
166-object exploratory output or the 80-object pre-mount checkpoint. Three
same-model rotation groups now use `face=floor/wall/ceiling` plus `facing` rather
than four extra experimental IDs. Legacy and v2 registry IDs remain unchanged.

See `NORMALIZATION-ACCEPTANCE.md` for current screenshot-by-screenshot limitations and
verification. Resource validation is not a claim that every requested family or
in-game interaction is finished.
