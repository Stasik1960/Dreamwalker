# Known issues and release limits

Current authority: [RELEASE-STATUS.md](../RELEASE-STATUS.md) and
[status.json](../release/status.json). Candidate `2.1.0-rc.1` uses runtime
baseline `b086e88929a971a2abd184629b3e7a59225304e5`. RELEASE_READY is FAIL.
No repair runtime changes have been ported to this candidate.

`BB-COMPOSITE-INPUT` is closed by explicit user-approved intentional retirement,
not asset reconstruction: exactly 23 IDs / 33 positions were changed to air in
a new world copy. Independent verification found no other deletion, no orphan
helpers and no nonterrain file changes. See [RETIREMENT.md](../release/RETIREMENT.md).
The original ZIP is immutable; old `--recover-city` is not the approved path.

Full-city conversion remains incomplete for known objects: unresolved exact
owner membership, shared-root and foreign-cell conflicts and three missing
whole-owner runtime mappings remain in the existing repair engine. Guards must
not be weakened and those objects must not use a per-cell fallback. The retired
legacy copy still contains 43,634 IDs absent from the rc.1 registry; retirement
PASS is not permission to load that world in rc.1.

Dedicated restart, real client interactions and startup/RAM/reload/FPS/TPS on
a complete converted city remain unverified. Static checks and 37 GameTests
cannot establish those gates. Read the [migration guide](../release/MIGRATION.md).

Historical beta.3, TEST3 and pre-retirement rc.1 evidence remain preserved.
Their results are scoped to their inputs and source fingerprints; they do not
validate a changed converter, current working tree or production release.
