# Bloodborne Blocks handoff

## Current disposition

See [RELEASE-STATUS.md](docs/RELEASE-STATUS.md). Verification candidate
`2.1.0-rc.1` uses the selected runtime
baseline is `origin/main` at
`b086e88929a971a2abd184629b3e7a59225304e5`
(`origin/archive/beta3-grid-fragmentation-broken`). The 23 unavailable
composites were intentionally retired from a copied input world and the copy
was converted to the current grid. It is still **not** certified for production:
dedicated-server restart and graphical-client acceptance remain required.

No certified stable commit is known. The candidate has a new JAR identity;
equivalence to the published beta.3 binary is not claimed. Do not infer that beta.3 artifacts, its
offline checks, or earlier repair diagnostics establish full city, protected
object, graphical-client or restart coverage.

## Separate work

Repair checkpoint `3d07` and local catalog WIP are separate, unmerged work.
They must be reviewed against the selected baseline and complete release gates
before they can affect a release decision.

## Next owner

Use the release status as the authority for required evidence. Keep generated
runtime resources and historical source material intact. Do not replace the
frozen input archive; use only the release-world copy and its ledger.

Older detailed handoffs are preserved under
[docs/history/main-b086e8892](docs/history/main-b086e8892/).
