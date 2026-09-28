# Bloodborne Blocks handoff

**New task, 2026-09-29:** [historical whole-model consolidation](docs/whole-models-handoff/TASK.md).
Start there: compare historical JAR/full-city pairs, select the best base, reuse
the existing manual complex models, resolve visual/physics conflicts with the
user using images, and deliver one complete pair plus a verified Blockbench kit.
It supersedes the old fixed-base/frozen-scope strategy. Preservation requirements
still apply. The following describes the last delivered rc.3 checkpoint, not
the outcome or scope of the new task.

Current work branch: `codex/bloodborne-complete-accepted-repair`.
Runtime/source commit: `9f32ff5ad2c2c8ecace8533749794da3d12aa36b`.
Main remains rc.2 at `6bca311b87856ee251cc1d88f94ba6071ae231cf`.
The accepted TEST3 and local continuation are already integrated; do not
repeat the obsolete explanation that repair was never merged.

The rc.3 checkpoint is one matched JAR/full new rc.2 city copy. Local check,
build and 59 GameTests pass. Independent subset preservation and real repeat
pass; full coverage fails: 4,453 known candidates remain unresolved and
additional source scope is incomplete. The clean packaged-server fresh/save/
restart smoke passes with Bloodborne/Fabric API only. Client interactions and
full-modpack acceptance are not complete. No main merge or release tag.

Use [RELEASE-STATUS](docs/RELEASE-STATUS.md) for current gates and CI, and
[REPORT](docs/complete-accepted-repair/REPORT.md) for the independent family
table and exact restored/residual coordinates. The separate
[AGENT-COMMENTS](docs/complete-accepted-repair/AGENT-COMMENTS.md) explains the
build/coverage bugs. [REPRODUCE](docs/complete-accepted-repair/REPRODUCE.md)
records commands. Artifacts and hashes are in
[delivery.json](docs/complete-accepted-repair/delivery.json).

Continue only from proved source-to-current owner relationships and accepted
contracts. Do not infer ownership from proximity or overwrite foreign roots,
NBT, helpers or other-mod data. Existing archives and the original user
checkout/WIP remain untouched. Do not interpret checkpoint publication or
SUBSET_PASS as full repair or RELEASE_READY.
