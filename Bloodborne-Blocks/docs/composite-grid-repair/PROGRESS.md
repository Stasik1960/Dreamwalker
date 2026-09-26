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

The upper-cell rebase is now implemented as an explicit saved root_anchor
state for these two families, with canonical remaining the default. The
converter enables it only at the two approved coordinates, with exact source
membership and a preserved-neighbor state guard. No generic relocation exists.
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

Finish composite membership recovery
and independent whole-world gates; convert ONLY latest-modded-world.zip;
verify the whole map and idempotence. Release/main integration must wait for
the required gates. Client graphical verification has not been performed.

## Root-state verification (2026-09-26)

- Full Gradle check PASS: 52 tasks, 3m38s; log
  build/root-state-full-check-final.log. Isolated JDK17 was used; stale daemon
  metadata still mentions an unrelated missing system JDK.
- Two real headless Fabric GameTests PASS, covering both families in four
  directions, foreign-cell preservation, helper creation/removal, seating and
  pick-state preservation. These are not graphical client tests.
- Bench (-374,73,-291): immutable input membership verified; conversion fixture,
  independent output checker and byte-identical second pass PASS. Replacing
  the preserved neighbor with a foreign block rejects the exception.
- Balustrade (-560,98,-9): actual map conversion remains UNRESOLVED. Frozen
  expected modules are not the actual assembly. Polygon comparison found six
  additional polygons at each of y98 and y99; none may be discarded by guess.
- verify_root_state_baseline.py proves all canonical resource payloads equal
  baseline 419b85e after removing the explicit new property/state variants.
  The other 47 production families are unchanged.
- Physical reduction report now includes 136 entries (upper-state duplicates
  included); upper states rebase the already reduced mask without expansion.

The earlier paragraphs describing the rebase as evidence-only are superseded
by this section. No final world or beta.4 release has been generated.

## Exact historical wall recovery

Reconstructed complete textured meshes from the checksum-frozen 2.0.1 JAR,
using its original carrier blockstates/models and historical clipping code.
59 source states of red_nether_brick_wall/mossy_cobblestone_wall have exact
single-cell matches with source attribution and no missing/extra polygons.
Mappings already handled by the frozen migration are not duplicated. The
new evidence is historical-wall-mesh-mappings.json; regeneration is checked
by test_historical_wall_mappings.py. No runtime art/physics was changed.

Three new tests PASS: complete evidence reconstruction, absence of ambiguous
targets, conversion with preserved foreign neighbor and byte-identical second
pass checked by the independent world validator. Related adapter, composite
and explicit-root suites PASS through Gradle (5 tasks, 54s), log
build/historical-wall-gradle-check-final.log.

Expanded negative world gate still FAILS beta.3, as required: 9,551 fragmented
objects, 6,689 unresolved technical memberships (down from 8,023). Fragmented
counts are not comparable directly to the earlier gate because saved default
root_anchor state is now included. No positive whole-world PASS is claimed.
The 1,334 newly proven memberships do not imply a delivered converted map.

Stepped balustrade sample (-676,35,-312) is not admitted: 33 expected polygons
are contained in an actual 39-polygon mesh. The extra six form a separate
spirelamp_002 textured slab (y=0..0.1875). Its owner remains to be proved;
neither containment alone nor its appearance authorizes discarding it.

Subsequent exact neighbor proof now identifies that slab: sandstone_stairs
at (-676,34,-312), offset (0,1,0). Its six textured polygons plus the wall's
33 exactly equal the observed 39, with no missing/extra polygons. Verified
source states and the actual MODDED state are frozen in
stepped-wall-overlap-evidence.json; verify_stepped_wall_overlap.py reproduces
the proof. Conversion is still unresolved: it must recover both whole objects
atomically, including the rest of the neighboring object's source membership.
Do not turn this evidence into a rule that consumes the merged cell for the
wall alone or treats the residual slab as an independent object.

## Membership proof continuation

The exact carrier manifest now covers 89 states, including sandstone_wall.
The independent negative gate reports 6,248 unresolved memberships, down by
441 from 6,689. Mixed wall/decor modules remain excluded; a regression test
explicitly rejects m_37ae347a7c9d73ae as a complete wall. Four wall tests PASS.

Read-only full-artwork audit separated exact, shared and missing geometry.
The new historical producer closure audit checks every admitted source object's
complete component set. It can explain old opaque-dominant suppression only
when the opaque cube's own original carrier component is exact, its textures
are fully opaque, all six exterior faces cover the cell, and the occluder's
complete source object closes in the same evidence graph. These cubes remain
separate owners; this is not permission to consume or overwrite them.

The (-560,98,-9) conflict has a closed historical graph of eight source objects
and 27 cells. Three regression tests PASS: positive full graph, removed remote
component rejects closure, and unproven source attribution rejects occlusion.
The test monkeypatches readers only; immutable input ZIPs are never modified.

All 6,866 relevant source seeds were audited with radius 3/node limit 128.
5,173 of the 6,248 occurrences have historical owner closure evidence; 1,075
remain unresolved. Full evidence is historical-owner-closures.json.gz, with
hash/counts in membership-proof-progress.json. These are NOT world PASS counts.
Converter integration for grouped owners is still pending; the existing world
gate correctly continues to fail. A larger bounded diagnostic retry is being
run only for the 265 traces that hit the node limit.

Targeted Gradle check PASS: six tasks, 1m10s, including wall evidence,
MODDED adapter, owner-closure negatives, composite and root exceptions.
Log: build/membership-closure-gradle-check.log. No JAR/world release created,
no independent compatibility fallback added, and main remains untouched.

### Expanded bounds and recovered conversion ledgers

Retries only for traces hitting the 128/512-node bounds used limits 512/2048.
Merged evidence now proves 5,315 of the 6,248 remaining historical memberships;
933 remain unresolved. The compressed evidence and progress hash are updated.
No converter/world gate consumes this diagnostic evidence automatically.

Recovered original local conversion reports from the existing Dreamwalker
checkout. Thirteen reports are frozen in historical-conversion-ledgers.json.gz
with original hashes and bundle hash in its manifest. They are evidence data,
not instructions and not an alternative world input. Do not replay them.

audit_historical_omissions.py verifies every claim against exact original source
state, full textured incoming fragment, recorded owner/root and unchanged
current protected state. It found 337 matching fragment contexts across 149
source roots among the unresolved cases; 221 candidate contexts were rejected.
These witnesses explain historical refusal to write into occupied cells, NOT
permission to overwrite those cells or a complete membership/world PASS.
Keep the whole current block and block-entity context intact until an atomic
multi-object conversion/preservation proof is implemented.

Example: the books at (-644,83,-25) have two pieces explicitly refused by
world-static-grid-v2 at (-644,83,-23) and (-643,83,-23). Both incoming textured
meshes match exactly and the existing protected helpers still occupy those
cells. Four new tests PASS: positive exact witness, changed context rejection,
wrong source-state rejection and incomplete textured-fragment rejection.

Current repair checkpoint still has no released JAR or FULL map. Required next
steps remain: finish unresolved memberships and preservation constraints;
integrate proven owner groups atomically in the existing converter/runtime;
pass whole-world gates; then beta.4 JAR + ONE FULL world from the immutable
MODDED ZIP, zero-change second pass, and finally history-preserving main work.

### Atomic integration (supersedes evidence-only status above)

See [ATOMIC-INTEGRATION.md](ATOMIC-INTEGRATION.md) for the implemented existing
converter/runtime integration, full-world results and exact remaining lists.
3,238 connected groups compiled; 356 groups / 2,471 owners converted atomically.
2,882 groups stay fail-closed. New protected scan: 6,518 failed objects, 933
unresolved memberships (2 proven current-owner omission contexts, 931 still
insufficient). 53 root conflicts in 43 groups are explicitly listed; no new
technical-root moves were made. Independent full-world edit verification,
zero-orphan helper audit and byte-identical zero-change second pass PASS.
Composite/preservation/registry gates remain FAIL. No beta.4/FULL release or
main changes. Continue targeted physical conflict and membership work from
this implementation rather than returning to unbounded evidence exploration.
