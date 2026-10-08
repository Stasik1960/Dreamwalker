# Bounded first-set preservation audit

The recognized first-set plan contains42 whole objects and112 unique source
member cells in10 terrain chunks: two18-cell trees, two2-cell doors, one each
wood window, thin window, wall and roof, and34 two-cell source ladder pairs.
Two additional door roots are verified source air. The34 source beehive block
entities have typed NBT recorded in the plan; two honey0 caps are excluded.
These counts describe the reviewed subset, not object frequencies in the city.

The actual frozen-subset derivative excludes the EAST door whose member chunks
are absent. Its review scope is41 objects and110 source members. Independent
`verify_source_review_preparation.py` checks the prepared pre-load derivative:
The latest immutable prepared-v6 copy retains70 terrain chunks and49 native BEs
byte-exact. One terrain chunk has a single palette Name change that maps exactly
two original Hunter Lamp technical light cells to the verified compatibility
AirBlock; its packed indices/data stay exact. The75 unchanged compressed chunks
preserve payloads/timestamps. There are50 logged typed-field changes:15 entity
IDs,32 item IDs including nested inventories, one explicit weapon form, the
isolated fixture name and that single technical-light palette Name.
All275 manifest member/root/nonmember preconditions decode from the original
frozen archive. The source lamp manager remains byte-exact, including its
duplicate registration; the separate new RP node uses the original custom lamp
ID and the distinct actual entity UUID. The source/provenance archives are
byte-identical. The corrected original seven-movement reference SHA and all
swept chunk-preload flags also pass. Evidence: `SOURCE_REVIEW_PRELOAD_INDEPENDENT_V6.json`.

The independent post-save audit of source-author-v7 passes with460 declared
logical block changes, zero unplanned nonmember block changes and zero foreign
BE changes across the71 original terrain chunks. All41 final root states pass;
40 composite/ladder owner masks and complete typed source provenance pass, with
the one-cell wall checked as its exact frozen state. Both source lights remain
the declared ID and the live parity probe confirms emission9 and empty shapes.
All15 RP entities keep their UUID/ID, exact typed prepared original NBT in a live
SourceLegacyPayload envelope, and every unhandled source field. No top-level
source fields are dropped. The60 other live entity field differences are listed,
so this does not claim identical live entity NBT. The1157 chunk metadata/light/tick
differences and4363 additional generated chunks are separate save-boundary
results. Evidence: `SOURCE_REVIEW_POSTSAVE_INDEPENDENT_V7.json`; the previous
v3 audit retains the two lost technical-light failures as historical evidence.

All V7 native runs use production SHA
`1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9`.
The source author, production-only reopen and persisted QA replay pass with normal
exit0. The author deliberately rolls back the whole41-object/110-cell batch
before committing, preserving native state, typed NBT, ledger and item-drop count.
The saved marker, source signature, UUID, root data and every footprint binding
then satisfy the persisted second-run no-op check.

An independent comparison of the production reopen against the authored world
checks4434 chunks and472 native/DW BEs: zero logical block or typed BE changes,
all41 roots exact, and15 RP source envelopes unchanged without recursive nesting.
The independently checked persisted QA replay has the same ownership/native/RP
results. Its1024 new flat-layer cells are recorded only in a generated chunk
absent from the original input, outside all source and owner cells; their exact
bedrock/dirt/grass layers and generator settings prove ordinary generation
progress. These are not hidden under an unchanged-whole-save claim. The reopen
comparison to prepared input lists1587 chunk metadata differences separately.
Evidence: `SOURCE_REVIEW_POSTSAVE_REOPEN_INDEPENDENT_V7.json`,
`SOURCE_REVIEW_SECOND_SAVE_INDEPENDENT_V7.json` and
`SOURCE_REVIEW_PERSISTED_REPLAY_INDEPENDENT_V7.json`.

`tools/archive_source_review.py --revision v7` requires all matching native and
independent proof hashes before creating
`build/prototype/First-set-migrated-source-fixture.zip`. It archives the exact
production-only reopened world, retaining all4363 additional generated chunks.
All37 archived files are byte-verified against the stopped world; only root
`session.lock` is omitted. Source/world/input/artifact hashes remain unchanged.
The deterministic ZIP has38 entries including its root directory,18793792
uncompressed bytes, and SHA
`daae8e9c8fd51f612b63fe6076af1e6a59874b4acc06afdfef9f202c071ba228`.
Evidence: `FIRST_SET_SOURCE_REVIEW_SCENE_V7.json`. This bounded41-object fixture
and its ordinary generated surroundings are not a converted full city.

`FIRST_SET_MIGRATION_PRESERVATION_AUDIT.json` independently checks the whitelist,
root classification, protected tree grass and plan/source hashes. It does not
convert a save. Archive-preservation and actual migrated-save tests remain
separate from the passing runtime GameTests.

| Source case | Alignment and preservation contract |
| --- | --- |
| Trees | Use lowest owned melon Y119 as root and NBT `SourceShift` DOUBLE list `[0,-1,0]`. Canonical Y118 is foreign grass. All18 frozen source coordinate selections stay one object; no visual AABB clearing. |
| Doors | Source NORTH root `[-93,58,-211]` and EAST root `[-59,75,-113]` are air over stone. Consume exactly each lower/header pair and preserve surrounding construction. Closed source-art equivalence and new moving-leaf physics are separate evidence. |
| Windows | Same owned source root, selected source variant frozen. Wood has dripstone support; thin needs no floor support. Neighbor brick/wall cells remain native even when geometry overlaps them. |
| Wall | Replace only `[154,58,-976]`; freeze manual source connections. The other wall below is foreign and must retain its state and collision. |
| Roof | Same source root with north-local `SourceShift [0,-0.1875,0.3125]`; rotate XZ once by global ROTATION. It affects render, collision and selection independently of support MountY. |
| Ladders | Recognize exactly beehive honey1 plus adjacent native ladder, freeze RNG at the original visual position. SourceLadderRuntime retains a static UUID-linked backing and typed provenance. Honey0 caps and all other cells stay native. |

All required composite supports in these recorded source coordinates are full
grass, stone or dripstone, so ordinary computed MountY is0. The below-wall case
uses the separate wall runtime. General mounting tests on partial support do
not establish source-coordinate equivalence by themselves. Picks/drops remove
technical source compensation so subsequent normal builds use canonical anchors.

Ordinary `CompositeRuntime.place` correctly refuses an occupied source root.
A recognized-source conversion must therefore own one rollback journal around
exact source clearing and placement. Its preflight must verify every expected
source state and complete typed BE NBT, loaded touched cells, support, permissions,
root ownership and actual entity obstruction. Journal the union of source members,
new roots and the effective shifted sparse target footprint, including old owner
ledger entries. Only recognized members may be cleared; foreign cells remain
their actual native blocks and BE instances.

Use FORCE_STATE/SKIP_DROPS without intermediate neighbor notifications while
clearing recognized native carriers. If clearing or placement fails, restore all
changed native snapshots and affected ledger entries before stable publication;
do not replace unrelated BEs merely because they were read. Publish final updates
for the union of consumed source cells and target writes: a cleared carrier may
fall outside the new sparse target mask. Retain source provenance for every
consumed member. Keep source hive contents as opaque typed provenance rather than
turning them into new RP entities or normal item ownership.

Persist a migration key and source signature with the allocated owner UUID. On a
second run, require the same key, registry kind, state/art, compensation and live
ownership mask, then report a no-op. A changed source state/NBT, partial previous
installation, root UUID mismatch or foreign root is a conflict that preserves
current data. Do not allocate a new UUID or clear neighbors to hide a conflict.

Before any Minecraft load, independently compare the input and converted copied
save. Whitelist only declared member/root/helper states, new DW owner BEs and
ledger records. Expand each changed section's4096 states so palette reorderings
cannot hide differences. Compare all unplanned typed BE NBT, entities/UUIDs,
inventories, chunk non-section NBT, scheduled ticks and unrelated files. Unchanged
chunks retain compressed payload and timestamp exactly. Record authorized source
BE removal as byte/typed-NBT archival provenance. The first-set runtime foreign
chest test adds BE instance preservation, which offline files cannot prove.

Minecraft1.18→1.20 DataFixer changes, lighting recalculation and ordinary game
ticks need a separate before-load/after-load diff; source ticks cannot be assumed
frozen. The original integrated-client physics probe ran a real ticking world.
Its source archive remains untouched. A migrated technical fixture is not proof
of exact save preservation or client visual acceptance.

The source tree west foliage gap passes3m. Central original wool and melon stop
at about1.2m; the proposed narrow continuous lower stem and passable central upper
billboard intentionally differ. Record that proposal for TASK§8 user review.
Passing movement tests do not approve that physical policy. No full-city conversion,
mass catalog generation or final numeric ID assignment is authorized by this audit.

At the exact original coordinates, all seven unconverted Fabric movements match
the corrected original Forge sweep. After conversion the west tree gap stays3m;
the open door passes4m through both the center and the original side route. The
window's native neighboring brick barrier remains0.199999988m and the ladder
entry remains-1.012499988m. Central upper tree movement changes1.2m→3m, the lower
melon movement changes1.2m→1.575m, and the functional closed door blocks its
source side route. These are explicit proposed physics changes requiring user
review, while the open door retains the original available passage.
