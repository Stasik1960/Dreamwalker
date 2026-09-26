# Physical conflict census — 2026-09-26

Input: frozen post-integration scan at `5e0b41b53`, not a new membership search.
Reproduce with `python tools/classify_physical_conflicts.py`. Full per-cell
owner roots, offsets and collision boxes are in `physical-conflict-census.json.gz`.

## Exclusive primary classes

| Class | Atomic groups |
| --- | ---: |
| Positive collision-volume overlap | 2,380 |
| Disjoint collision volumes sharing a block cell | 437 |
| Two roots require the same cell | 43 |
| Empty-collision reservation | 2 |
| Total | 2,862 |

Priority for groups containing several classes: root/root, positive overlap,
disjoint volumes, empty collision. Contact counts are not group counts:
27,945 positive overlaps, 7,757 disjoint contacts, 263 empty contacts and 53
root/root contacts. Empty collision does not by itself authorize removal of
an outline/selection cell or a root.

## Systemic ownership restriction

2,812 groups contain root/helper contacts and no root/root contact. Only seven
groups contain exclusively helper/helper contacts. The remaining 43 contain
root/root contacts. Across all groups there are 23,445 root/helper contacts,
12,520 helper/helper contacts and 53 root/root contacts.

Both converter and runtime currently assume one owner per occupied block cell.
Consequently even proven disjoint collision volumes are rejected. Removing
real collision is not an acceptable workaround. Supporting helper/helper
sharing alone would resolve at most seven groups; root/helper sharing is the
major class. Root/root conflicts stay blocked without separate exact proof.

## Frequent owner pairs

Pairs count groups and can overlap within a group. Historical Minecraft IDs
below identify source carriers, not a claim about the objects' appearance.

| Pair | Groups |
| --- | ---: |
| stone railing / polished blackstone | 387 |
| stone railing / smooth red sandstone stairs | 325 |
| stepped balustrade / diorite stairs | 322 |
| stone railing / diorite stairs | 246 |
| acacia fence / diorite stairs | 211 |
| brick wall / diorite stairs | 204 |
| high balustrade / sandstone stairs | 187 |

No mask shrink, root move, independent city fallback, membership expansion or
gate exemption is authorized by this census. The 931 insufficient-evidence
memberships are deliberately outside this work phase.

## First runtime correction

Whole historical owners previously inherited the modular page lifecycle skip.
`GeometryRuntime.usesHelpers` now distinguishes whole owners from cell pages:
placement, removal, repair and same-block state transitions service their
helpers. Compilation and `cityCompatibilityCheck` pass (52,084 states).
This fixes lifecycle, not shared-cell admission. The whole-world gate rerun on
the unchanged diagnostic conversion is exactly equal to the integration scan:
356 accepted atomic groups, 6,518 failed protected occurrences, overall FAIL.
See `physical-lifecycle-gate.json`. No conversion mask or root changed.
