# NightmareRunning QA and ALT work allocation

Baseline: `main` at `9e60c5ef31fc7504cadcbe43a54ccb84e30133e1`.
This is a work plan, not an implementation or visual-acceptance report.

## Inputs and authority

- `GPT6_PROMPT_QA_ALT.md`: requested scope, acceptance tests and deliverables.
- `ALT_VISUAL_SPEC.md`: exactly two visual slots and resource-pack/export requirements.
- `NightmareRunning.docx`: authoritative visual intent and red-box boundaries.
- `NightmareRunning_QA_normalized.md`: tracking checklist, not replacement for images.

All fifteen embedded screenshots were extracted without modification and
inspected. The local evidence directory is `build/nightmare-running-inputs`.
No bundled LibreOffice was available; this was original-image inspection and
OOXML text/image association, not a claim to have rendered the DOCX page layout.

Image mapping: C008=5; C001=7; C002=14; C1491=11; C1680=13; C1962=3;
C1979=1; C471=2; C282=8; C618=10; C654=6,4; wall=12; unnamed fence=9; C028=15.

## Preparation already delegated

| Agent | Role | Bounded responsibility |
| --- | --- | --- |
| `alt_runtime_scout` | scout | Trace properties, geometry keys, model baking, palette eligibility and helpers |
| `qa_families_scout` | scout | Locate requested family provenance, compilers and one-source/multiple-output migration constraints |
| `qa_review_coverage` | fabric_builder | Write frozen QA2 coverage report; attempt fence identification without changing objects |
| `qa_statue_boundaries` | scout | Map C008 screenshot numbers to source components; verify #3/#6 rotation equivalence |
| Root | integrator | Inspect every screenshot, hold requirements and resolve architecture/semantic decisions |

Scouts are read-only. Coverage is documentation-only. See
`nightmare-running-review-coverage.md`: 21 active QA2 families / 104 specimens;
12 explicitly reviewed C-families, eight not mentioned, one no-catalog wall.
An unmentioned family is not approved.

The C008 scout supplied preliminary component provenance, not a verified
screenshot-to-component mapping. Manifest component numbers must not be
equated with the red-box numbers without a camera/geometry match. In
particular, screenshot #3/#6 equivalence still needs verification, and the
requested low-pedestal height corrections remain required. Do not use that
preliminary mapping as an approved split recipe.

## Implementation allocation and ownership

Work proceeds in waves with at most three subagents active. The root is the
only writer of shared generated manifests/resources and the only release/Git
integrator. Builders implement isolated source modules or authored recipes;
they must not independently run a generator against shared resources.

| Work package | Assigned role | Exclusive source ownership / acceptance |
| --- | --- | --- |
| Static semantic splits | fabric_builder | Separate authoring module for C008 (five unique statues), C1491 (three graves), C1962 (two sacks), C471 (two spires); screenshot-based boundaries, anchors and yaw tests |
| Composition and artwork evidence | root, then bounded fabric_builder | C001 opposing branch artwork, complete C002 composition, C1979 five red-box parts; retain context and trunk-only tree collision |
| Functional and small visual fixes | fabric_builder | Separate module for C282 two independent doors, C654 two consistent-sided windows, C618 height/lit states, C1680/C028 marked fragments, `o_wall_deco_1` anchor |
| Atomic split migration | fabric_builder after root contract decision | `convert_logical_world.py`, `logical_contract_v2.py`, migration compiler/tests; one source consumption, several independent outputs, all-or-nothing conflict handling |
| Two-slot property and baking | fabric_builder after root design decision | Runtime property/state lookup and client-only model loading; existing rendering retained, independent resource-pack ALT overrides, no duplicated gameplay geometry |
| Visual commands | fabric_builder | New command module/tests; root integrates registration and shared target resolver; permission 2, loaded-world limits, helper-to-master, preserve every non-visual property |
| Exporter and gallery | fabric_builder after final IDs/assets | Reproducible ALT exporter, dependency validation, new gallery generator/tests; no export from the old QA2 family list |
| Independent acceptance | integration_reviewer | Read-only review of persistence, split transactions, command limits, client/server separation, resource reload/fallback and regression evidence |

File ownership is assigned at dispatch. Only one writer may own a file at a
time; overlapping packages run sequentially. No agent may revert another's
changes, publish, rebuild a JAR per family, or write the original world.

## Required architecture gates

1. Current eligibility is **272 visible logical definitions** (277 total minus
   five hidden compatibility trees), not merely the 21 QA2 V2 gallery families.
   Compute eligibility again after the requested splits. Exclude helpers and
   compatibility-only blocks; do not silently omit visible older logicals.
2. Current client code replaces JSON geometry with generated logical meshes.
   Merely adding ALT JSON files cannot satisfy normal resource-pack overrides.
   Agree a static resource-load/bake path and prove partial-pack fallback and
   reload before bulk generation. No BER, entities or per-position database.
3. Current converter rejects shared source cells as double consumption. It
   cannot emit multiple independent masters atomically from one raw carrier.
   Add an explicit authored transaction mechanism, not a global relaxation of
   overlap checks. Validate all outputs/context/entities/ticks before writes.
4. Do not assume every split is a one-to-many raw-world replacement. C654's
   two different sides describe one old window location but two new design
   choices. Establish an explicit migration policy without overlapping two
   new windows, silently choosing artwork, or destroying the old appearance.
5. Retain loadability of old logical IDs/states; missing `visual` defaults to
   BASE. A retired composite is not an alias to an arbitrary one of its parts.
6. Fence image 9 lacks an exact ID: `NEEDS_USER_DEBUG` until uniquely proven.
   A silhouette candidate is not authority to hide/delete a registry entry.

## Integration order and release gate

1. Freeze screenshot-to-source boundaries, final IDs and compatibility/migration decisions.
2. Integrate semantic fixes and guarded raw-source transactions; run narrow tests.
3. Integrate BASE/ALT property, bake fallback and commands; prove old-state loading.
4. Export the post-fix ALT kit and create a fresh readable QA gallery.
5. Independent review; accumulated Python/Java/GameTests, orientation and package checks.
6. One final checkpoint JAR, updated HANDOFF, commit/push to main and remote SHA verification.

Never start batch-03, full discovery, city conversion, broad art redesign or
section rendering. Do not mark this plan, the coverage report, offline previews
or successful automated tests as the owner's Minecraft visual acceptance.
