# Composite repair checkpoint (2026-09-26)

Work remains on repair/composite-preserving-grid, based on 419b85e. No beta.4
world or release has been produced, and main has not been changed.

Verified partial fixes:
- Explicit physical footprint sidecar, initially identical to authored masks.
- Exact retained-carrier rules recover the 114-cell C001 fixture atomically.
- C474's frozen module union is supported by historical textured polygon
  evidence; the 32-cell fixture converts into its reviewed logical identity.
- Eleven composite regression tests pass. The protected-world checker remains
  explicitly incomplete as a global gate; beta.3 fails its negative check.

## User-approved conflict handling

Reduce collision for proved physical overlaps while retaining appearance and
position. The 128 state reductions are now applied in the physical sidecar,
including cardinal rotations, BASE/ALT parity and lighting-toggle parity.
Exact volume-union containment tests prove no collision expansion. Testing all
protected occurrences leaves exactly the two shared-root conflicts below.
Per-cell primitive limits remain the authored policy limit; clipping may split
a global primitive into multiple disjoint rectangles without increasing the
per-cell limit. Java compilation and six MODDED adapter tests pass.

The C618 obstruction at (-540,42,-33) is a separate original magenta glass
panel, proved by an exact frozen forward mapping with zero MODDED mismatches.
Its cell is retained, while C618 no longer reserves it. A conversion fixture
proves C618 restoration, panel preservation and byte-identical second pass.
See module-provenance.json. The ornament fixture still has no exact producer
in that archive; it must not be assigned to another object by guesswork.

Gradle check passed 51 tasks before the final lighting-toggle closure. After
that closure, the ten composite tests and fourteen production-palette tests
pass. A final runtime resource validation remains necessary before delivery.

Latest targeted Gradle validation passed (geometryCheck,
logicalContractV2Check, test_composite_repair, test_root_exception_evidence,
test_production_palette; 8 tasks, 28 seconds). This validates the final 128
physical reductions, not an unreleased root relocation implementation.

The protected world gate now checks complete exact MODDED technical membership
(114 cells for the real tree fixture), and uses the current physical sidecar.
A negative test leaves a module outside the original 16 raw tree carriers even
though the root is restored: the gate detects that fragment. Unknown logical
roots no longer qualify as automatically acceptable source replacements.
The expanded beta.3 negative run reports 8,996 fragmented objects and 8,023
occurrences without a complete exact archived assembly match. The latter are
explicit failures, not permission to infer or discard source components.
Frequently missing mappings include old modular carved/stepped balustrades;
the frozen mapping says keep-carrier while the MODDED input contains modules.
These require further historical evidence; the checker must not be weakened.

Technical root exceptions are limited to bench/books at (-374,73,-291) and
balustrade/railing at (-560,98,-9). Read-only input inspection proves that the
upper neighboring cell belongs to each moving object's source assembly and
physical footprint, with no second protected physical owner. See
root-exception-evidence.json and inspect_root_exceptions.py.

The upper-cell rebase is a candidate, not implemented runtime behavior.
Two separate evidence tests pass, including cancellation of root displacement
after cardinal rotation around the original pivot and exact equality of every
real mesh vertex for all current states of both families. These tests do not
replace runtime/manual-placement verification.

Before either exception can be applied, prove unchanged world-space mesh,
orientation and render, unchanged other object, no physical expansion,
consistent canonical mapping and manual placement, and second-pass identity.
Do not generalize this into an automatic root conflict resolver. Do not modify
an entire family's anchor merely to solve these two map occurrences.

## Remaining

Integrate the bounded root policy with existing state/runtime/converter paths;
finish composite membership recovery
and independent whole-world gates; convert ONLY latest-modded-world.zip;
verify the whole map and idempotence. Release/main integration must wait for
the required gates. Client graphical verification has not been performed.
