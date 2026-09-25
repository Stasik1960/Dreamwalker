> Historical missing-model evidence. The world-cell blocker was resolved
> using the old MODDED map; see [recovery](RECOVERY.md). Lost composite assets
> themselves were not reconstructed.

# Missing transient city models

The frozen city contains 23 `m_*` registry IDs in 33 cells whose model and
collision records are absent from every available runtime artifact. They are
listed with exact dimension, position and saved state in
`missing-model-positions.json`. Counts are 1 cell per ID except
`m_0ea41aaba3b1270b` (3), `m_6e29addcc429a971` (7), and
`m_7e28f29afb24c0b3` (3).

Those 23 IDs / 33 cells describe the source evidence. The aggressive
production-logical conversion consumes 20 of the cells through exact production
mappings, leaving a compatibility gap of 9 IDs / 13 cells in
`cityPaletteMigration`. Both counts are retained so the source gap is not
confused with the smaller residual conversion blocker.

The IDs are absent from the immutable Bloodborne Blocks 2.0.1 JAR, all shipped
2.1 alpha/beta JARs, the only reachable Git `v2` definition/geometry/mesh
blobs, the other local worktree and build outputs, and the user's Minecraft
mods folder. A targeted Downloads/Desktop inventory found no companion
palette or positions report. `Ether-Bloodborne-2.0.zip` is an exact earlier
world copy and contains none of the 23 IDs.

That earlier world does provide provenance. At the same coordinates, 29 of
the 33 cells are `architecture_part` helpers; their persistent owners include
the retained `acacia_stairs`, `birch_stairs`, and `dark_oak_stairs` large-door
roots. The remaining four cells are air. The IDs therefore came from a later
overlap/composition pass, whose generated palette was written to the
`Ether-Bloodborne-2.0.2-positions` world but was not included with the supplied
world or any shipped JAR.

All frozen legacy ornament states and cell fragments were regenerated with the
historical 2.0 toolchain and resources. None produced any of the 23 hashes.
The missing IDs are composition hashes, so their polygons and collision cannot
be recovered from the hash or inferred from neighboring blocks. The city
converter must keep any residual states unresolved and fail closed. It must not
replace them with air, a visually similar module, or a placeholder registry
entry.
