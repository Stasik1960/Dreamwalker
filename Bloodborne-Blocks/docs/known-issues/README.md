# Known issues and release limits

Current authority: [RELEASE-STATUS.md](../RELEASE-STATUS.md) and
[status.json](../release/status.json). Candidate `2.1.0-rc.1` uses runtime
baseline `b086e88929a971a2abd184629b3e7a59225304e5`. RELEASE_READY is FAIL.
No repair runtime changes have been ported.

The immediate blocker is missing authoritative composition/model/collision and
ownership data for the 23 transient `m_*` IDs (33 cells) in the immutable MODDED
input. The historical recovery reverts these cells to an earlier helper/air
layout. It does not establish lossless compatibility with the current unknown
composites and is prohibited under the current fail-closed requirement.
See the required input and next step in the release status.

Legacy fixtures, whole-owner full-city conversion, dedicated restart, real
client interactions and startup/RAM/reload/FPS/TPS remain unverified. Static
checks and 37 GameTests do not prove those gates. Read the
[migration guide](../release/MIGRATION.md) before using the candidate.

Historical beta.3 evidence, TEST3, dirty repair diagnostics and the 355/356
payload discrepancy remain available in [AUDIT.md](../release/AUDIT.md) and
[the previous status](../release/evidence/status-beta3.json). Their old gate
results are not adopted for rc.1. The five beta.3 class differences remain
unclassified, but rc.1 has its own artifact identity and makes no binary
equivalence claim. This historical difference is not the current input blocker.
