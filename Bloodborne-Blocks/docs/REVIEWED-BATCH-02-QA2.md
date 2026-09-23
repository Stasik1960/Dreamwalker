# Batch-02 QA2 — whole trees, target debug, readable gallery

Scope: the three requested QA corrections only. No batch-03, full discovery,
city conversion, original-world writes or unrelated family changes.

## Complete tree correction

The earlier C001/C009 SPLIT interpretation was wrong. Each six-log source
proposal spans wings of **two adjacent trees**, not two usable tree objects.
A bounded read of 13 source chunks around four frozen review examples found
eight complete trees. Each contains four central wool carriers and twelve
log-atlas wing carriers. The full boundary and original states are frozen in
`reviewed-batch-02-qa2-trees.json`; the Catalog B C-IDs/signatures and historical
human answers are unchanged.

- One construction ID/item: `bloodborne_blocks:o_c001` (Review C001, also C009).
- Six full visual variants are states of this block, not six registry IDs.
  They retain the source pack's weighted artwork. Each has four yaw states.
- One complete mesh per state. No component renderer, entity or new BE class.
- Master: central `white_wool` carrier minus `(0,1,0)`, at the visual base;
  local anchor `(0,0,0)`, pivot `(0.5,0,0.5)`. Placement/yaw never moves it.
- Collision policy `TRUNK`: one authored box `(0.25,0,0.25)..(0.75,10,0.75)`.
  The existing runtime clips that box into the master and nine vertical helper
  cells. Its union is the original box. No helpers/collision on branches.
- One outline box `(0,0,0)..(1,10,1)`; root/helper pick and break address the
  same complete tree. The inventory preview is scaled for its 12-block height.
- `o_c001_a/b`, `o_c009_a/b`, `o_dead_tree_planter` stay registered, retaining
  their old placed-state geometry for save compatibility. They are hidden from
  creative; inventory-only aliases redirect their pick/placement to `o_c001`.
  They are deliberately **not** world `PaletteAliases`: replacing each wing
  independently would duplicate trees. Already-placed old logical assemblies
  are not automatically regrouped by this checkpoint.

Current `original-v2` migration uses six guarded rules for the two proven
16-cell source layouts and positional weighted variants. Complete matches are
converted atomically; incomplete/foreign-occupied cases are preserved. Old
partial-tree raw rules are disabled in this mode. Historical `original-v2-poc`
is retained only for frozen regression fixtures. Unobserved mirrored/rotated
raw layouts are not guessed; runtime manual placement supports all four yaws.

Rebuild these reviewed resources without discovery:

```powershell
python -B -X utf8 tools/build_qa2_trees.py
```

The historical `build_reviewed_contracts.py` also applies this correction last.
`compile_reviewed_migration.py` allows the explicit alternate compiler only for
`o_c001`; unrelated families cannot silently bypass its generic geometry proof.

## Debug command

Aim at a block and run `/bloodborne debug target` or `/bloodborne debug`.
Permission level 2 / commands-enabled singleplayer is required. Click the chat
report to copy. The server uses a bounded 16-block outline ray without loading
missing chunks. Valid `architecture_part` ownership resolves to the master;
ordinary blocks and stale helpers are clearly `NON-LOGICAL`. Nothing is saved,
scheduled or mutated by the command.

Typical abbreviated output:

```text
Review ID: C001
Logical ID: bloodborne_blocks:o_c001
Master: 64 64 32
Target: HELPER
Helper offset: 0 1 0
Facing: north
Canonical anchor: 0 0 0 (local cell; pivot 0.5 0 0.5)
Collision policy: TRUNK
Selection policy: TRUNK
Contract schema: v2
```

The full output also contains `State`, source-family and pattern information.
Pattern information is the contract's catalog pattern list, **not** a claim to
know which historical source instance produced the targeted placed block;
per-placement source provenance is not stored.

## New gallery and installation

- JAR: `releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-batch-02-qa2-20260923-mc1.20.1.jar`.
- ZIP: `releases/Bloodborne-Blocks/reviewed-batch-02-gallery-qa2.zip`.
- Local generated folder: `Bloodborne-Blocks/build/reviewed-batch-02-gallery-qa2`.
- 104 specimens: 21 active Contract V2 families; all six tree variants × four
  facings are first. `gallery-positions.json` retains the list format and adds
  variant-specific `/give` values. Facings are 40 blocks apart.
- Platform: `minecraft:white_concrete`, a light neutral full cube. Preferred
  `smooth_sandstone` is overridden by the source pack/mapping, so is unsafe as
  a non-carrier QA floor. The generator verifies the selected floor against
  the complete stored carrier index and mapping, without another world scan.
- Safe spawn `(16,66,6)` has a dedicated light platform beneath it.
- `level.dat` uses an engine-written, sanitized Minecraft 1.20.1 template,
  Anvil `version=19133`, data version 3465 and stable vanilla packs/features.
  See `GALLERY-METADATA-QA-2026-09-23.md` for the earlier list-blocker diagnosis.

Replace the previous Bloodborne JAR (do not install two copies), then extract
the ZIP's world folder into the actual instance's `saves`. Requires Minecraft
1.20.1, Java 17, Fabric Loader 0.16.10+ and Fabric API 0.92.9+1.20.1. Original
city data and older gallery folders/archives are untouched.

```powershell
python -B -X utf8 tools/build_reviewed_gallery.py
# Refuses an existing output; use --output <fresh-folder> for another copy.
```

## Reproducible checks

From `Bloodborne-Blocks` (substitute a local Java 17 and Python with numpy):

```powershell
$env:JAVA_HOME=(Resolve-Path 'build/toolchain/jdk-17.0.20.1').Path
.\gradlew.bat -I tools/verification-direct-resources.init.gradle `
  -PbloodbornePython=C:/Users/vakir/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe `
  check build logicalGameTest qa2Checkpoint
python -B -X utf8 tools/verify_qa2_checkpoint.py --jar ../releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-batch-02-qa2-20260923-mc1.20.1.jar --report build/qa2-checkpoint-recheck.json
```

Checks include all six exact tree rules, eight bounded real source examples,
four simultaneous neighboring-tree pair fixtures (two independent masters,
16 distinct source cells each), incomplete/conflicting refusal, idempotence,
source immutability, trunk-only runtime shapes and root/helper pick/break.
Debug GameTests cover registration, permission, non-player calls, actual player
raycasts for both aliases, ordinary/master/helper targets and stale/unloaded
ownership. Other reviewed family regression coverage is retained.

`qa2Checkpoint` depends on `check`, `logicalGameTest` and `remapJar`. It binds
the current Java/tooling/authoring/critical resource inputs, reports and JAR
hashes into `build/qa2-build-proof.json`. The verifier rejects a changed input,
JAR or report and requires all six new tree/debug GameTests plus the baseline.
The proof-recording flag is an internal Gradle step, not an alternative to tests.

Final execution on 2026-09-23: **BUILD SUCCESSFUL** for
`check build logicalGameTest qa2Checkpoint`; **17/17 GameTests PASS**,
**1008 orientation checks PASS**, 28 retained non-tree source examples and
18 complete-tree checks PASS (six rules, eight actual source trees, four pairs).
All **191,919** source resources were compared with the remapped release JAR;
the packaged mixin check also passed. The server shut down normally after tests.
The gallery metadata was separately accepted by the official 1.20.1 summary loader.

The final machine-readable result is `reviewed-batch-02-qa2-checkpoint.json`.
Small supporting reports, GameTest XML and input/report/JAR hash proof are in
`qa2-checks/`. This recorded proof is evidence for this build, not a substitute
for rerunning tests on another checkout. No caches or GameTest world are published.

Release JAR: 95,353,268 bytes; SHA-256
`b7ef8d69d31efd10a9e857caecfc80d7abf6aab8f007659d6774e6d3abef7a13`.
Gallery ZIP: 289,999 bytes; SHA-256
`d17b76ac2b5523d4c363f04b483bcf871455ac95da49649d1c68ce43218f11cd`.

[Offline mesh comparison](reviewed-batch-02-qa2-tree-preview.png) shows the
old trunk/wings and the new complete tree; it is supplementary, not Minecraft QA.
Client GUI/gameplay visual acceptance remains the owner's next step. Send
screenshots together with the debug output for the next per-object bug pass;
do not start batch-03 or city conversion automatically.
