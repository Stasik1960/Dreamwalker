# Historical MODDED cell recovery

The local `_bloodborne_rebuild/output/ether-v2-positionfix` map supplies
coordinate-specific evidence for the 33 unresolved input cells: 30 owned
`architecture_part` cells and three air cells. All 30 owner roots have the
same coordinates and complete states in the immutable latest MODDED input.
The four original reference regions are preserved in
`reference-inputs/city-recovery-reference.zip` (evidence excerpt, not a world
conversion input). SHA-256:
`8a8e95fd2bb71b749c7dd525bf663412b86b795764660b6ea28ebc071005dd28`.

`recovery-plan.json` records every exact before/after state, coordinate and
owner. `tools/city_recovery.py` verifies the reference checksum, rereads each
reference cell and owner, and checks current ownership/geometry. Foreign
block entities and scheduled ticks cause failure. Only the exact missing
state at the exact coordinate can be repaired. A second invocation skips
already repaired/converted cells.

Use `convert_logical_world.py --source-mode modded --city-compat --recover-city`
with the immutable latest MODDED ZIP and its required checksum. Recovery
runs before the existing logical converter: old owned door parts can then
be correctly consumed or removed with their owner. Neither runtime code nor
production families are changed. The independent checker revalidates the
recovery evidence and composes it with the logical ledger. The preservation
checker verifies the complete before/after chain and unrelated typed NBT.

This restores an evidenced previous layout; it does not reconstruct the
missing transient composite model assets bit for bit. The current world's
other cells remain subject only to the existing reviewed conversion rules.
World verification and delivery results will be recorded separately after
both conversion policies complete. In-game checks are excluded by user request.
