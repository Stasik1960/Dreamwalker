# Structural normalization — experimental integration

> Historical alpha baseline. The ongoing rejected-result follow-up and its
> current 23-case acceptance matrix are in [NORMALIZATION-ACCEPTANCE.md](NORMALIZATION-ACCEPTANCE.md).
> Counts, screenshots mapping and verification results below have not been
> refreshed for that work and must not be cited as its acceptance evidence.

This is a continuation of the existing mod, not a new migration from scratch.
The experimental version is `2.1.0-alpha.1`. Do not treat it as acceptance of all
23 screenshot cases or as a production-world upgrade.

## Implemented infrastructure

- Additive `o_*` logical registry; all old registry IDs remain readable.
- One item places a root and its owned service cells. Pick/use/removal delegate
  to the root; service cells have no separate inventory item. Whole-object
  selection and cell-local collision use the same generated geometry profiles.
- Facing, open/closed and neighbour connections are properties, not separate
  registry IDs for the curated families. Existing nonlogical objects retain
  their old behavior.
- State changes preflight loaded chunks, occupied cells and entity collision;
  failed replacement restores the previous root/helpers. A stale helper is
  rejected unless it belongs to the current root's actual geometry footprint.
- Inventory conversion uses exact legacy states and unambiguous component
  provenance; ambiguous items are preserved. Crate variants are not inferred
  from their misleading vanilla carrier names.
- Offline world conversion matches complete proven groups, writes a new copy,
  records exact before/after states, and preserves foreign blocks/entities.
  Duplicate equivalent inverse rules are deduplicated; conflicting rules and
  partial groups are rejected. Scheduled block/fluid ticks prevent conversion
  at affected positions. Re-running must not duplicate converted objects.

Current generated counts: **76 objects, 388 states/meshes, 546 migration rules**.
Only 31 fully covered dry legacy IDs and 91 proven v2 item aliases are hidden
from creative inventory; their registry entries are not deleted.

The same-model `lightning_rod`, `oak_wood` and `chain_0` orientation groups each
have one logical ID and twelve `face=floor/wall/ceiling` × `facing` states.
Placement uses the clicked surface; pick normalizes orientation. Four previously
generated experimental IDs were removed, not legacy/v2 registry IDs. All 380
pre-merge state meshes, geometry and metadata remain exact in their corresponding
new states, and all 546 source/member/offset migration rules are preserved.
Eight additional ceiling/yaw poses are rigid rotations of the same artwork;
they do not invent additional source-world matches.

## Screenshot acceptance matrix

“Implemented” below means code/resources exist, not that a Minecraft gameplay
test was performed. Current offline rendered sheets are under
`build/logical-preview-mounts`; older preview directories are diagnostic history.

| # | Case | Implemented scope and remaining work |
|---|---|---|
| 1 | Bushes | Whole authored bush models, including `o_grass_0`, no fragment items for unambiguous migrated states. Log-carrier tree fragments are explicitly excluded from bush inference. |
| 2 | Books | `o_books_0` / `o_books_test` restore whole authored piles as one placed object. |
| 3 | Crates | Whole reviewed crate/container models and exact source-state inventory mapping. Ambiguous v2 composite items are not relabelled blindly. Gameplay placement/pick still needs verification. |
| 4 | Trees | One proven white/orange/magenta-wool three-part tree family: `o_dead_tree_planter`. Evidence: 130 exact source-map triples at offsets +3/+6. Other log-carrier trees and their branching layouts remain unresolved. |
| 5 | Vertical fence gap | `o_stone_railing` normalizes the smooth stone/mossy-cobblestone-wall carrier with neighbour properties and tall side geometry under a block above. Seam behavior needs in-game verification. |
| 6 | Ornate balustrade | Not completed. `deepslate_tile_stairs` has custom top/bottom straight meshes; native fallback inner/outer states are not proof of valid Bloodborne corners. No safe complete connected family is claimed. |
| 7 | Iron railing / relief | `o_iron_railing` has 16 neighbour states. Proven importer-added faces are removed from eligible authored meshes. The separate upper relief and every visible stray pixel are not yet individually accepted. |
| 8 | Stone balustrade | Not completed. `smooth_red_sandstone_stairs` and `warped_trapdoor` expose distinct custom sections; `o_stone_railing` from #5 must not be mistaken for this ornate family. |
| 9 | Tiles / curbs | Not completed. Floor panels, snow-layer carriers and corner/end pieces still need exact artwork classification and a neighbour-state family. No arbitrary native stair corner is substituted. |
| 10 | Lamp post without lamp | Not completed. The tall pole with the side bracket still needs an exact source-model/assembly match. `dead_fire_coral_block` / `dead_tube_coral_block` are multi-lamp assemblies, and `o_lantern` is a small hanging lantern, not this street pole. Standalone lanterns do not satisfy this requirement. |
| 11 | Stone pillar | Not completed as the complete pictured stack. Existing cap/base/column modules remain preserved until a finite assembly is proven. |
| 12 | Windows | `o_tall_arched_window` joins exact warped-stair lower/header pairs (+2; 45 source-map pairs). The authored model includes wall/backing surfaces and needs further visual reconstruction before claiming a clean standalone window. |
| 13 | Doors | Three whole door/header families (`o_acacia_door`, `o_birch_door`, `o_dark_oak_door`) with open/closed states; headers remain fixed while leaves open. Player passage, redstone expectations and drops need gameplay acceptance. |
| 14 | Wall corners | Not completed. No guessed association of neighboring architectural trim or brick modules. |
| 15 | Facade ornaments | Some complete authored ornaments/statues are normalized automatically. The pictured crest, arch and corbel assemblies are not all identified or joined. |
| 16 | Shutters | `o_shuttered_window` has open/closed artwork and interaction. Original masonry backing is deliberately preserved, so it is not yet a separately mountable shutter-only object. |
| 17 | Toothed spire | `o_toothed_stone_rib` restores a complete authored three-block rib. Repeated ribs are not guessed into an entire variable-height spire. |
| 18 | Ladder | Reviewed `o_ladder_01..03` use ladder semantics; the ladder_03 carrier is separated from gravestone mappings. Vanilla fallback stairs are excluded. Real climbing/collision/placement remains untested in Minecraft. |
| 19 | Column flanges | Not completed. No blanket collision removal from unrelated masonry. Exact flange/column ownership still needs reconstruction. |
| 20 | Graves | Authored individual tombstone_0..9 and gathered-grave models are whole objects. Separate source-model assemblies, if any, are not guessed from adjacency alone. |
| 21 | Iron gates | `o_iron_gate` joins proven lower/header pairs (+3; nine source-map pairs), with a double-leaf open state. The two +6 arrangements are deliberately not consumed as +3 pairs. |
| 22 | Lantern / statue hand | Standalone reviewed lantern objects and proven planar-import cleanup exist. Snapping the separate lantern to the correct statue hand is not implemented. |
| 23 | Stray polygons | Full resource graph/UV/culling/planar audit exists. Only demonstrably inflated source planes/additional faces are restored; flagged polygons are not automatically deleted and PNGs were not edited. |

## Reproducible checks

Use the repository's pinned Java 17 / Gradle 8.8 wrapper, without upgrading
Fabric, Yarn or Loom:

```powershell
.\gradlew.bat check build --no-daemon --console=plain
python -X utf8 tools/check_logical_resources.py
$env:BLOODBORNE_VANILLA_JAR = 'PATH_TO_1.20.1_CLIENT.jar'
python -X utf8 tools/test_logical_objects.py
python -X utf8 tools/test_logical_world.py
python -X utf8 tools/test_verification_guards.py
python -O -X utf8 tools/test_verification_guards.py
python -O -X utf8 tools/check_external_resources.py --self-test
python -X utf8 tools/check_external_resources.py --minecraft-jar 'PATH_TO_1.20.1_CLIENT.jar'
```

The four Java checks are data/registry fixtures, not a Minecraft launch. Their
test-only class loader applies the pinned Fabric Loader package-access fix to
named Minecraft classes and mirrors Fabric Registry Sync's bootstrap redirect
(`Registries.init()` instead of the vanilla init-and-freeze operation).
It also binds references as Registry Sync's `SimpleRegistryMixin` does. The
registry fixture explicitly performs `BlockItem.appendBlocks`, matching Registry
Sync's `BlockItemTracker` callback, and checks the registry map, `asItem()` result
and non-air items. Other Fabric mixins, client/server startup, world ticks and the
rendering lifecycle are not exercised. The launcher is not part of the runtime JAR.

After an interrupted resource-copy task, `tools/check_staged_resources.py
--repair-staging` can repair only `build/resources/main` and compare every byte
with the inputs. It refuses unexpected output files rather than deleting them.
Only after that check passes, with resource inputs unchanged, a retry may use
`check build -x processResources`; a clean build still uses the full command above.
`tools/check_packaged_resources.py PATH_TO_RUNTIME.jar` then checks ZIP integrity,
every packaged source resource, all stale resources (including root-level files),
and the exact compiled production-class set. Resources must be byte-identical,
except Loom's mixin-JSON reformatting and injection of the pinned generated refmap
name; all other mixin JSON fields must remain exact. Only the manifest and verified
mixin refmaps are allowed additional resources. The injected refmap is compared
byte-for-byte with the compiler output. Run this check before cleaning the
compiled classes. Verification guards remain active under Python `-O`.

Regeneration (not required for a normal Gradle build) also needs the original
pack and a vanilla 1.20.1 client JAR for inherited model/texture resolution:

```powershell
$env:BLOODBORNE_VANILLA_JAR = 'PATH_TO_1.20.1_CLIENT.jar'
python -X utf8 tools/build_logical_objects.py --original-pack 'PATH_TO_bloodborne.zip'
python -X utf8 tools/audit_logical_objects.py --original-pack 'PATH_TO_bloodborne.zip'
python -X utf8 tools/render_logical_preview.py
```

A missing source pack fails before generation changes any output. The removed
86 exploratory `o_*` asset sets were generated false positives (vanilla fallback
artwork), not old mod resources. They are reproducible from earlier exploratory
output; no original-world or legacy/v2 resource files were deleted.

World conversion commands (output must not already exist):

```powershell
python -X utf8 tools/convert_logical_world.py SOURCE.zip build/logical-world/OUTPUT --report build/logical-world/REPORT.json --progress
python -X utf8 tools/check_logical_world.py SOURCE.zip build/logical-world/OUTPUT build/logical-world/REPORT.json --resources src/main/resources/bloodborne_blocks/logical
```

The report contains private coordinates; keep it and world copies under ignored
`build/`. `unresolvedV2` counts only unmatched *candidate* component positions.
`unmatchedModules` aggregates all other old modules by dimension/state, with at
most four coordinate examples; it does not imply those modules are defective.

## Verification record

- Independent logical resource graph/state/migration validation passed for the
  current 76-object output; no missing local models/textures in that graph.
- Generator regression tests: 15 passed (including source-proof gating,
  missing-pack fail-closed behavior, and positive-boundary faces not allocating
  empty adjacent helper cells, and small floor-contact margins not allocating
  a lower helper layer). The test imports the generator and requires
  `BLOODBORNE_VANILLA_JAR`, just like regeneration. Mount grouping also tests
  distinct artwork/UV-lock isolation, exact source x/y mappings, all twelve
  state combinations and preservation of existing unrelated hashed IDs.
- Synthetic world tests passed: cross-chunk/dimension matching, copy-only ZIP
  and directory inputs, repeated conversion, partial/conflicting groups,
  duplicate inverse aliases, foreign block entities, fluid ticks, stale helpers,
  missing helper corruption and bounded unmatched-module reporting. Negative
  tests also reject forged/omitted ledger entries, choosing one side of an
  ambiguous group, reports that overlap protected inputs, and malformed helper
  NBT types. Source-root helper adoption is checked against exact legacy
  profiles, including multi-member assemblies and exclusion of conventional
  door siblings. Final fixture: 6 accepted objects, 15 rejected candidates,
  2 dimensions.
- Independent review identified and prompted fixes for state transition
  rollback, stale helper ownership and overly broad auto-model classification.
- The current copy `build/logical-world/Ether-Bloodborne-logical-v3-mounts`
  contains 24,375 accepted object conversions. The independent checker scanned
  10,009 chunks / 1,003,216,896 section block slots, verified source/resource
  hashes and the independently reconstructed complete accepted-object set,
  and confirmed that all 170 non-block-region files stayed unchanged.
- The accepted objects span 27 target families: stone railing
  (19,859), bushes (2,861), bags (341), barrels (133), complete trees (106),
  doors (60), and other proved groups. Not every family is represented or
  safely matchable on this input map. `check_logical_mount_transition.py`
  independently compared the preserved pre-mount baseline
  (`build/logical-mount-baseline`) with current resources: all 380 existing
  render meshes, geometry profiles, state metadata and all 546 source rules
  remain exact after the explicit target ID/state normalization.
- A second conversion to `build/logical-world/Ether-Bloodborne-logical-v3-mounts-repeat`
  accepted 0 objects; SHA-256 manifests of all 186 files are identical to the
  validated first output. This second directory is a test artifact, not a
  separate newer world release.
- 6,579 first-pass candidates were rejected safely; 16,913 candidate v2
  component positions remain unresolved. The 27,498,708 other v2 module positions
  include ordinary city masonry and are not a defect count. Full private
  ledgers/rejection details are in `build/logical-world/*-report.json`.
- Earlier directories containing `validated`, `idempotence`, `second-pass` or
  `v3-final` are superseded diagnostic checkpoints, not the latest world output.
- Complete staged-resource byte comparison passed: 190,143 files / 149,386,939
  bytes, canonical SHA-256 manifest
  `138b98f9bfa1483f88058076be6e69c863c2ba8bf99d78984b158b6d219d7f5c`.
- The complete resource audit covered 96,170 models / 46,816 blockstate files;
  JSON/model/parent/local-texture reference errors: zero. All 503 legacy and
  46,236 v2 registry definitions remain present.
- Additional external-reference audit against the pinned vanilla client JAR
  passed: all 96,170 local models, 46,816 blockstates, 49,223 distinct blockstate
  model references and 232 v2/logical mesh texture references were checked.
  Missing concrete models/parents/textures, parent cycles, JSON errors and
  unresolved render-required face/particle/item-layer variables: zero.
  The 51 remaining unused/template texture aliases are informational, not
  missing render textures. Report: `build/external-resource-audit.json`.
  Synthetic inheritance, required-variable, cycle and item-model checks passed
  normally and under Python `-O`. This is static graph validation, not a game render.
- Four Java fixture checks passed for current mounted output: geometry
  (42,269 legacy states, 2,644 profiles, 20,287 cells), logical objects
  (76 / 388 meshes), logical registry/items (76 / 546 rules), and modular data
  (503 legacy / 46,236 modules and meshes). Mount checks cover all 72
  surface/player-direction combinations, every yaw rotation and mirror,
  and inventory orientation normalization.
- Verification-script corruption regressions: 20 passed both normally and with
  Python `-O`. These reject changed packaged bytes, stale root resources, leaked
  test classes, missing resources/refmaps, duplicate ZIP entries, additional
  states/meshes/profiles, changed behavior/schema, geometry artwork and migration
  offsets, wrong Loom injections and corrupted generated refmaps. The
  mount-transition check also compares complete allowed state sets,
  referenced mesh/profile sets and invariant metadata, not just old-state presence.
- Gradle completed **BUILD SUCCESSFUL in 20m 29s**, all 11 tasks executed.
  Actual command: `.\gradlew.bat check build -x processResources --no-daemon
  --console=plain`, after the complete byte-verified staging described above.
  Java 17 / wrapper 8.8 and dependency versions were unchanged. Packaged mixin
  isolation/refmap validation passed for `2.1.0-alpha.1`.
  Runtime JAR: `build/libs/bloodborne-blocks-2.1.0-alpha.1.jar` (93,588,297 bytes).
  Full packaged-resource equality verification passed under Python `-O`:
  190,143 resources, the same canonical source manifest as staging, exact
  production-class names, no stale entries, and exact generated refmap bytes.
  Only Loom's verified mixin-JSON transformation is exempt from byte equality.
  JAR SHA-256:
  `939a472f3cbfc3bd3052a60655d5392cfda91d278015cf4971d43fe404fb41c4`.
- No Minecraft client/server world was launched. Bootstrap/data checks and
  software previews do not verify gameplay, chunk-frustum rendering, lighting,
  drop counts, redstone, or all 23 user examples.
