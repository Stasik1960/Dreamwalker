# Known issues and release limits

The current authority is [RELEASE-STATUS.md](../RELEASE-STATUS.md): RELEASE
CANDIDATE is BLOCKED and RELEASE_READY is FAIL. No certified stable commit is
known.

The selected source/audit baseline is `origin/main`
`b086e88929a971a2abd184629b3e7a59225304e5`
(`origin/archive/beta3-grid-fragmentation-broken`). Beta.3 offline evidence does
not establish protected-object coverage, full-city conversion, graphical client
acceptance or restart coverage.

Historical `6518` is a repair diagnostic and local `1979` is a different dirty
repair run. Neither is attributable to `main` or proof of release readiness.
Repair `3d07` and local catalog WIP are separate, unmerged work.

Scoped blockers B01–B07 are enumerated in [status.json](../release/status.json).
They cover stable-baseline selection, legacy ID/ItemStack compatibility,
whole-owner full-city preservation, real runtime/restart/client QA, missing
performance measurements, the unreproduced 355/356 group delta, and five
unclassified class-file differences between the published and rebuilt JAR.

The original TEST3 `knownReleaseBlockers: []` is superseded for readiness
interpretation by [the scoped erratum](../release/test3-scope-erratum.json).
The original historical file and its SHA remain unchanged.
