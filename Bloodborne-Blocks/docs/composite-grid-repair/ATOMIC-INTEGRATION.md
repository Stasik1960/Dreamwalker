# Atomic integration checkpoint — 2026-09-26

This supersedes the evidence-only integration status in the older progress log.
It is **not** a beta.4 or playable-world release. Main remains untouched.

## Implemented

The frozen 5,315 proven historical memberships feed the existing converter.
Overlapping traces are merged by exact source owner, shared observed cell, and
protected multi-carrier assembly membership. This produces 3,238 connected
transactions, not individual cell replacements. The old conversion ledgers
are never replayed.

Every transaction reserves its entire observed source set even if preflight
fails or a component is missing. Ordinary aliases cannot consume its fragments
or write into the reserved set. Every output uses the existing root/helper
runtime. Foreign blocks, foreign block entities, scheduled ticks, absent
components, conflicting roots and overlapping output masks reject the whole
transaction. Aggressive mode cannot override an atomic group's obstruction.

The current production contracts supply protected objects. The same static
geometry/mesh runtime additionally represents 863 exact historical source
states as **whole owners**, with four rotations around the source pivot.
These are not cell pages: their complete meshes and physical masks stay with
one root. No source-position shift, mesh rescaling, heuristic collision
expansion, ticking system, or alternative world format was introduced.
Eight source states lacking an admissible root/mask remain unsupported.
Existing city pages and all 49 production families retain their identities.

The independent cell palette is explicitly blocked while this unresolved
composite work is present. There is no independent `city_*` fallback for
shared or unknown pieces. Whole-owner mesh loading has its own bounded
`owner_` resource stream; original cell-local mesh bounds remain unchanged.

## Fresh full-world result

Input: **only** `reference-inputs/latest-modded-world.zip`, SHA-256
`c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.

10,009 chunks; 24,034 accepted transactions in total, no forced writes.
Of these, **356 atomic groups / 2,471 historical owners** converted together.
The remaining 2,882 atomic groups were left unchanged:

| Group category | Count |
| --- | ---: |
| Shared physical cells / root or destination conflict | 2,862 |
| Exact closure but incomplete converter/runtime support | 20 |

There are **53 shared-root cells in 43 groups**. Do not extend the two approved
technical-root exceptions to these cases. All coordinates and owners are in
[atomic-root-conflicts.tsv](atomic-root-conflicts.tsv).

The new protected gate reports **6,518 failed protected objects** and **933
unresolved memberships**. The latter number was recalculated, not carried
forward as an assumed target. Its classification is separate from group counts:

| Unresolved membership category | Count |
| --- | ---: |
| Historical omission with independently proven current owner | 2 |
| Insufficient complete evidence | 931 |

The focused ledger follow-up proves six omitted fragment contexts belonging
to those two memberships: source roots `(-644,83,-25)` and `(-447,113,45)`.
It verifies current helper NBT, actual root state and every helper in the
frozen current owner's geometry. This is **preservation evidence**, not yet a
complete conversion authorization. Fifteen other helper contexts have an
incomplete current-owner closure; 316 witnesses are not current helper-owner
proofs. None were guessed or promoted to membership PASS.

Lists: [atomic-unresolved-memberships.tsv](atomic-unresolved-memberships.tsv).
Full group conflicts, mappings and omission evidence:
[atomic-world-residuals.json.gz](atomic-world-residuals.json.gz).

## Checks and release gates

- Independent checker PASS: 1,003,216,896 block positions, 170 unchanged
  external files; input and logical/whole-owner resource hashes verified.
- Atomic regressions PASS: successful multi-owner transaction, missing member,
  foreign source/destination, shared root, blocked runtime owner, no alias
  escape, independent ledger authorization, and exact high-balustrade exception.
- Targeted Gradle regressions PASS, including existing composite and both
  explicit-root exception tests.
- Java compile, city runtime/whole-owner meshes and production mesh checks PASS.
- Helpers: **13,352 checked, zero orphans**.
- Separate second invocation: **zero changes**, all **186 files byte-identical**.
  The later run with extended resource-hash guarding is byte-identical to that
  first/second pair as well.
- Client visual/in-game QA: **NOT_RUN**. No claim of visual playtesting.
- `NO_COMPOSITE_FRAGMENTATION`: **FAIL**.
- `PROTECTED_WORLD_OBJECT_PRESERVED`: **FAIL**.
- Whole-world: **FAIL**.
- Old/unregistered cells: **27,637,095** remain in this diagnostic world;
  independent palette replacement was intentionally not used to hide them.

Verification: [atomic-integration-verification.json](atomic-integration-verification.json).
Frozen reports: [conversion](atomic-world-conversion.json.gz),
[whole-world gate](atomic-world-gate.json.gz).

## Continue from here

Do not redo the evidence search or re-create the converter. Address the exact
physical-mask conflicts listed in the new scan, keeping complete owner meshes,
source pivots and all failed-group reservations. Do not move additional roots.
Use the eight unsupported runtime-state reasons and the two current-owner
omission proofs for bounded follow-up, never a broad spatial heuristic.

Diagnostic command (requires a new output directory):

```powershell
python -B tools/convert_logical_world.py reference-inputs/latest-modded-world.zip build/atomic-owner-world-verified --source-mode modded --atomic-owner-groups --expected-source-sha256 c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9 --report build/atomic-owner-world-verified.json --progress
```

No beta.4 JAR, FULL delivery archive, main integration, or weakened gate is
authorized by this checkpoint. All required release gates must pass first.
