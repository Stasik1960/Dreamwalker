# Reconciliation — 2026-09-25

## Inputs and authority

- Fetched main: `99a7779313025eeb5081d8229abb9fb6e77f3ae1`.
- Agony baseline: `733c8323e455adb4ae811aa53dff69239ac2720f`.
- Friend handoff archive: all 14 manifest entries matched size and SHA-256.
- MODDED input: `reference-inputs/latest-modded-world.zip`, SHA-256 `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
- Original reference only: `reference-inputs/source-world.zip`, SHA-256 `4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51`.
- Source pack: SHA-256 `0f2c3d64a1d60734ae0786d128b522ea6bbd175f26f5d164bed5522c46898308`.

Archive main task/latest four client reports supersede historical DOCX semantics where explicitly changed. Existing partial fixes are retained. No global palette rebuild or unrelated family restoration.

## Partial work decisions

| Area | Classification | Continuation |
|---|---|---|
| Ladder03 | KEEP_AND_CONTINUE | Keep reflected wall plane, helper stacking and contact fix; exercise travel at all nine cell seams. |
| Landing01 | KEEP_AND_CONTINUE | Keep nine cell-local deck slices; test deck support for all four facings. |
| Statue lantern | KEEP_BUT_FIX | Keep collision-equivalent transition persistence and source lit artwork; replace guessed sockets with explicit source geometry evidence. |
| Window | KEEP_AND_CONTINUE | Keep ordinary placement and 1x2 ownership. Historical embedded behavior is SUPERSEDED_BY_LATEST_REQUIREMENT; old saved states belong in offline migration. |
| Gallery | KEEP_BUT_FIX | Update old 3x3 frame to 1x2; include none/unlit/lit statues; use registered architecture_part helper ID. |

## Additional review

A clean Windows checkout exposed CRLF-sensitive fingerprint hashes; normalize only text line endings before hashing, keeping immutable evidence and binary hashes unchanged. A stale restored-migration test still demanded the deleted embedded property; now checks current state and open/closed preservation. No foreign process/network execution found in main Java sources during targeted scan; this is a scoped review, not a security certification.

The global collision gate rejects collision outside owned cells. Authored cross-cell primitives are clipped by runtime into cell-local shapes, so rejecting every cross-cell authoring box would be incorrect. Target footprint tests separately constrain window/ladder physical ownership; no visual-only helper expansion was introduced.

## Verification boundary

GRAPHICAL_CLIENT_ACCEPTANCE_NOT_RUN. Dedicated server GameTests and offline model/NBT checks do not substitute for graphical acceptance. Final build/release proof is recorded separately after the final resource snapshot is frozen.

Statue attachments now use exact cardinal rotations after canonical 5-decimal mesh quantization; 6576 rotation comparisons pass without weakening the orientation gate. Source socket metadata retains six-decimal centers.
