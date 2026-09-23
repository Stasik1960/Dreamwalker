# Full-inventory attachment GameTest isolation

The accumulated 25-test run initially had 24 passes and one failure in the
pre-existing `statueLanternDetachWithFullSurvivalInventory`: immediate nearby
ItemEntity query returned zero after a successful detach. Cleaning up an earlier
mock ServerPlayer correctly through PlayerManager did not resolve this failure.

Read-only inspection of saved test-world entity regions after the failures found
the supposedly missing items, each **one `bloodborne_blocks:o_lanterns`**:

- earlier 24-test layout: `(270.5, -54.68000000715256, -5.5)`;
- final 25-test layout before isolation fix: `(282.5, -54.68000000715256, -5.5)`.

Evidence came from `build/gametest/world/entities/r.0.-1.mca` using the existing
`world_io.RegionFile` NBT reader. This disposable test world is not distributed.
`moveOutside` put the player at relative `(-6,4,-6)`. At the far end of the
expanded test layout that neighboring chunk was outside entity tracking;
the drop existed/persisted but was absent from the immediate entity query.

Only this test's player position now uses relative `(0,4,0)`, away from the
statue but inside the template chunk. The same exact-one-item assertion remains.
Production `LogicalAttachments` code was not changed, and this test-only fix
does not require rebuilding the accumulated runtime JAR.

Independent review of pinned Minecraft 1.20.1 bytecode confirmed that
`ServerPlayerEntity.dropItem` spawns the item and `ServerEntityManager.addEntity`
stores it even in an untracked section, while `SectionedEntityCache.forEachInBox`
(used by `getEntitiesByClass`) skips sections whose status cannot track entities.
The final rerun completed with **all 25 required GameTests passed**; the exact
one-item assertion passed without changing attachment behavior.
