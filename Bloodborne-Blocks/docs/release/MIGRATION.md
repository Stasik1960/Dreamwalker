# 2.1.0-rc.1 migration status

**No production migration is approved.** This is a verification candidate based
on `b086e88929a971a2abd184629b3e7a59225304e5`. Only version metadata changes in
the runtime resources; no registry aliases, NBT schema changes or repair
converter/runtime port are included. Replacing a beta/legacy JAR with rc.1 is
not a verified world upgrade.

Keep the original world archive and existing JAR unchanged. Do not open the
legacy world with the candidate: unregistered IDs have no proven lossless
runtime migration. Do not apply `--recover-city` to satisfy the current gate;
it replaces unknown composites with a previous helper/air layout.

Resume only after the authoritative source data for the 23 missing composite
IDs is available. Validate that data against the exact frozen source, build
real chunk/ItemStack fixtures, and prove load/place/break/use/save/restart
compatibility. Then audit the full world read-only, convert a separate copy
using whole-owner atomic transactions, and run the independent preservation
and byte-identical second-pass checks. Keep player data and other-mod data
unchanged. A failed attempt is abandoned in favour of another copy of the
original archive; the original is never overwritten.

Dedicated and client acceptance, including resource reload, chunk boundaries
and performance measurements, are required before a release. The current
[status](../RELEASE-STATUS.md) records the actual gates and required input.
The locally verified artifact hashes are in [SHA256SUMS.txt](SHA256SUMS.txt).
