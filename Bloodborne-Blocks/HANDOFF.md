# Bloodborne Blocks handoff

## Current disposition

See [RELEASE-STATUS.md](docs/RELEASE-STATUS.md). The selected audit/source
baseline is `origin/main` at
`b086e88929a971a2abd184629b3e7a59225304e5`
(`origin/archive/beta3-grid-fragmentation-broken`). It is **not** certified for
release: RELEASE CANDIDATE is BLOCKED and RELEASE_READY is FAIL.

No certified stable commit is known. Do not infer that beta.3 artifacts, its
offline checks, or earlier repair diagnostics establish full city, protected
object, graphical-client or restart coverage.

## Separate work

Repair checkpoint `3d07` and local catalog WIP are separate, unmerged work.
They must be reviewed against the selected baseline and complete release gates
before they can affect a release decision.

## Next owner

Use the release status as the authority for required evidence. Keep generated
runtime resources and historical source material intact. Do not publish or
replace release artifacts while the status remains blocked.

Older detailed handoffs are preserved under
[docs/history/main-b086e8892](docs/history/main-b086e8892/).