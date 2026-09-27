# Full-city diagnostic after intentional retirement

2026-09-27. **FULL_CITY_PASS: FAIL.** Input is the independently verified
retired legacy ZIP (`aed638e52143522abfbb3c63b85f377726727a5af039f532417ae0c6772504d1`).
The existing local `repair/composite-preserving-grid` whole-owner engine was run
read-only as code, writing only another new world copy. Its source/resource
fingerprints are recorded in `first.command.json` and remained unchanged.
This is not a port of that unfinished engine/runtime into rc.1.

No `--recover-city`, `--full-grid`, `--city-compat`, aggressive policy or
cell-level fallback was used. Original ZIP and retirement copy are unchanged.
All 33 retired coordinates remain air in both diagnostic output worlds.

| Check | Actual result |
|---|---|
| Conversion transactions | 26,804 accepted; 0 forced |
| Atomic owner groups | 3,238 total; 3,126 converted; 112 rejected |
| Independent atomic completeness | 3,101 complete; 137 fail the stricter object check |
| Protected-object fragmentation | 1,979 incomplete; FAIL |
| Exact technical memberships | 933 unproven |
| Target registry | 39,996 palette IDs absent; FAIL |
| Converter residuals | 27,584,575 unmatched blocks; 8,432 rejected candidates |
| Helper audit | 59,270 checked; 0 orphan helpers under the repair target geometry |
| Accepted transaction verifier | PASS; 10,009 chunks, 1,003,216,896 checked cells |
| Unrelated data preservation | PASS; all 170 nonterrain files byte-identical |
| Second conversion | 0 changed transactions/cells/chunks |
| Second output | All 186 files byte-identical; 33 retired cells still air |

Atomic rejection reasons: 37 `protected_owner_closure_incomplete`,
42 `shared_root_conflict`, 29 `target_would_overwrite_foreign_block`,
3 `whole_owner_runtime_mapping_missing`, 1 `reserved_by_atomic_owner_group`.
The guards leave those groups untouched. Zero second-pass changes do not make
an incomplete first pass acceptable.

The preservation check found 96,812 changed cells within 98,512 distinct
ledger coordinates, in 826 chunks. These are conversions of known objects,
not additional user-approved retirements. No cell outside that conversion
ledger changed. The failed protected check cannot establish zero protected loss;
its 1,979 count describes incomplete objects, not 1,979 newly deleted objects.

## Remaining evidence boundary

The protected oracle is pinned to the original immutable ZIP; its fresh check
therefore uses that historical baseline. Independent transaction/preservation
checks compare directly against the cleaned input. Retirement has its own exact
33-cell proof; the original oracle has not been silently edited to accept new
holes or to reduce its failure count.

Known-object examples explain why the missing 23 IDs are no longer the blocker:

- A white-wool source root at `(-660,36,-92)` belongs to an UNRESOLVED closure;
  current cells contain old helpers/air instead of the complete historical owner.
  Its adjacent proven oak-log closure cannot authorize the whole combined group.
- A stair seed at `(-730,-31,-71)` has several different known `m_*` pieces and
  non-exact missing/extra polygon counts. Clipped cell meshes lack a saved complete
  owner/root relation. Several possible allocations cannot be distinguished by
  matching a cell model alone.
- Three other rejected groups have available stair art/geometry but lack generated
  runtime mappings because the historical collision exceeds the generator's
  four-box bound. This separate implementation issue does not resolve the 933
  incomplete memberships or the shared-root/foreign-cell conflicts.

The next safe step is an authoritative current composition export linking the
unresolved known cells to whole owner roots, then narrowly fixing mappings and
root conflicts and rerunning the full-world gates. No additional deletion is
approved, and guessing ownership or restoring an earlier layout is not proof.

[Compact metrics](evidence/retirement-rc1/atomic/summary.json) link all fresh reports.
Raw converter/protected/registry reports are compressed without changing their
JSON payloads. Command records and logs include exit codes, durations, exact
arguments and engine fingerprints. The protected command exits 1 as expected.
Earlier malformed exploratory invocations in `build/` are not acceptance proof
and are not included here. Reproduction of this diagnostic needs the exact
separate repair files identified by those fingerprints; release rc.1 does not
claim to contain that uncommitted dependency set.

A release map is not produced from this failed diagnostic. Dedicated/client QA
on a complete converted city is NOT_RUN; startup/RAM/reload/FPS/TPS are
NOT_MEASURED. Current static checks and GameTests cannot substitute for them.
