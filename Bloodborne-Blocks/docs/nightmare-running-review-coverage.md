# NightmareRunning QA review coverage — frozen QA2 baseline

This is the preserved coverage baseline for the active QA2 gallery before the
NightmareRunning fixes. Current implementation and split IDs are documented in
[NIGHTMARE-QA-ALT.md](NIGHTMARE-QA-ALT.md); baseline counts below are not the new
all-visible gallery's counts. Visual intent remains authoritative in
`NightmareRunning.docx`; the normalized notes only make its reviewed IDs
trackable.  Absence from the notes is **not approval**.

## Scope and totals

Source: `build/reviewed-batch-02-gallery-qa2/gallery-positions.json` from the
QA2 checkpoint (also described by that gallery's `README.txt` as 21 active
families and 104 specimens).  A specimen is one gallery placement, including
each shown facing and, for `o_c001`, each shown tree variant.

| Group | Active families | Gallery specimens |
| --- | ---: | ---: |
| REVIEWED_WITH_FIXES | 12 | 68 |
| NOT_MENTIONED | 8 | 32 |
| NO_CATALOG_OR_LEGACY | 1 | 4 |
| **Total active QA2 gallery** | **21** | **104** |

Hidden compatibility aliases are not gallery specimens and are not counted as
user-facing active families in this baseline.

## REVIEWED_WITH_FIXES

The DOCX/normalized notes explicitly name these current QA2 family IDs.  The
count is a coverage statement only; it does not say the requested fix is
implemented or visually accepted.

| Logical ID | Review ID in notes | Specimens |
| --- | --- | ---: |
| `o_c001` | C001 (tree; current family also retains historical C009 source association) | 24 |
| `o_c002` | C002 | 4 |
| `o_c008` | C008 | 4 |
| `o_c028` | C028 | 4 |
| `o_c1491` | C1491 | 4 |
| `o_c1680` | C1680 | 4 |
| `o_c1962` | C1962 | 4 |
| `o_c1979` | C1979 | 4 |
| `o_c282` | C282 | 4 |
| `o_c471` | C471 | 4 |
| `o_c618` | C618 | 4 |
| `o_c654` | C654 | 4 |

## NOT_MENTIONED

These are active QA2 user-facing gallery families, but are not explicitly
named in the NightmareRunning notes.  **They are not PASS and require an
explicit visual-review decision.**

| Logical ID | Specimens |
| --- | ---: |
| `o_c003` | 4 |
| `o_c046` | 4 |
| `o_c1319` | 4 |
| `o_c474` | 4 |
| `o_c561` | 4 |
| `o_cases_0` | 4 |
| `o_iron_gate` | 4 |
| `o_iron_railing` | 4 |

## NO_CATALOG_OR_LEGACY

| Logical ID | Status | Specimens |
| --- | --- | ---: |
| `o_wall_deco_1` | `noCatalogID`; explicitly identified by DOCX image 12/debug as the decorative wall | 4 |

`o_wall_deco_1` must retain its no-catalog status until the catalog workflow
assigns one; this baseline does not invent a C-ID.

### Unnamed fence screenshot (DOCX image 9)

Status: **NEEDS_USER_DEBUG**.  `build/nightmare-running-inputs/image9.png`
contains no debug report, logical/registry ID, gallery position, or source-world
coordinate.  QA2 `gallery-positions.json` maps placements only to IDs, facings,
positions, and give commands; it has no screenshot-to-placement mapping.

Read-only mesh review produced a non-proof lead: `o_iron_gate` has a tall
vertical mesh (its Contract V2 north/south/east/west render bounds reach
`y=5.0`), which is compatible with the silhouette.  The screenshot alone does
not establish that it is this gate rather than another tall mesh or state, so
no object is hidden, removed, or counted as the screenshot's target.  Obtain a
`/bloodborne debug target` report while aiming at the fence before any action.

## Future candidates (not batch-03)

Future logical models originate in the existing full Catalog B candidate pool,
derived from the correct source world and source resource pack.  They proceed
through subsequent human-review batches; only a semantic human decision may
promote a candidate to a Contract V2 user-facing logical block.  This document
does not start batch-03, rescan discovery, or add any new C-ID.
