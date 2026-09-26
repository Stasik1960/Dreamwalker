# Composite repair checkpoint (2026-09-26)

Work remains on repair/composite-preserving-grid, based on 419b85e. No beta.4
world or release has been produced, and main has not been changed.

Verified partial fixes:
- Explicit physical footprint sidecar, initially identical to authored masks.
- Exact retained-carrier rules recover the 114-cell C001 fixture atomically.
- C474's frozen module union is supported by historical textured polygon
  evidence; the 32-cell fixture converts into its reviewed logical identity.
- Seven composite regression tests pass. The protected-world checker remains
  explicitly incomplete as a global gate; beta.3 fails its negative check.

## User-approved conflict handling

Reduce collision for proved physical overlaps while retaining appearance and
position. The proposed 48 state reductions have NOT been applied yet.

Technical root exceptions are limited to bench/books at (-374,73,-291) and
balustrade/railing at (-560,98,-9). Read-only input inspection proves that the
upper neighboring cell belongs to each moving object's source assembly and
physical footprint, with no second protected physical owner. See
root-exception-evidence.json and inspect_root_exceptions.py.

The upper-cell rebase is a candidate, not implemented runtime behavior.
Two separate evidence tests pass, including cancellation of root displacement
after cardinal rotation around the original pivot. These arithmetic tests do
not replace actual mesh/runtime/manual-placement verification.

Before either exception can be applied, prove unchanged world-space mesh,
orientation and render, unchanged other object, no physical expansion,
consistent canonical mapping and manual placement, and second-pass identity.
Do not generalize this into an automatic root conflict resolver. Do not modify
an entire family's anchor merely to solve these two map occurrences.

## Remaining

Integrate the bounded root policy with existing state/runtime/converter paths;
apply and validate physical reductions; finish composite membership recovery
and independent whole-world gates; convert ONLY latest-modded-world.zip;
verify the whole map and idempotence. Release/main integration must wait for
the required gates. Client graphical verification has not been performed.
