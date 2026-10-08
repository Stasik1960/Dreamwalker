# Independent wall prototype

Status: implemented source-backed first-set candidate; numeric catalog ID is not assigned. Server and client acceptance are recorded separately by the shared verification workflow. This document does not claim a completed conversion of every source wall.

The new `bloodborne_dw:prototype_wall` block imports the original polished-deepslate post, low arm and eight ordered weighted tall-arm models. Parent geometry and inventory display are frozen from official Minecraft **1.18.2**, not inherited from the target client. Fifteen JSON model records and eight original PNGs are listed with source hashes in `reports/WALL_ASSET_IMPORT.json`. The eight tall textures retain weights `[20,1,1,1,5,1,1,1]`; new placement selects once and stores `material=0..7`. Post and low arms retain their original fixed texture. `BASE` and `ALT` wrappers are independently addressable, with ALT initially falling back to the original art.

Source proof: `[154,58,-976]` contains `minecraft:polished_deepslate_wall[east=low,north=low,south=low,up=true,waterlogged=false,west=none]`. Its independent frozen counterpart is:

```mcfunction
setblock ~ ~ ~ bloodborne_dw:prototype_wall[connections=manual,rotation=0,north=true,east=true,south=true,west=false,post=true,course=low,material=0,profile=base,waterlogged=false]
```

Authored post bounds are `[4,0,4]..[12,16,12]`, low arm `[5,0,0]..[11,14,8]`, tall arm `[5,0,0]..[11,16,8]`, in model units. Cardinal rendering retains these exact source cuboids and UVs. Odd yaw adds one source-relative 45° transform around `[8,8,8]`; culling flags are removed only for these diagonal models. Collision and selection have separate methods and use thin geometry. A bounded 32-strip approximation leaves real diagonal corners empty; a full enclosing square is never used as collision. This prototype reserves only its root block cell. Neighbor art, collision or visual overlap does not imply ownership and does not cause an AABB-based placement refusal.

Ordinary items follow player yaw in eight steps. `AUTO` cardinal poses connect only to the new wall family. No vanilla wall, stairs or carrier block can turn the new wall into another source object. A builder step or an odd pose freezes the current arm/post recipe as `MANUAL`, preserving that recipe while turning it. `/bb wall rotate` and `wall_builder` step 45°. Sneaking with the tool toggles profile. Commands `/bb wall course low|tall`, `/bb wall material 0..7`, `/bb wall visual base|alt|toggle`, `/bb wall connections manual|auto` and `/bb wall debug` edit or inspect this isolated prototype. Switching to AUTO deliberately restores a cardinal grid pose and recomputes own-family neighbors.

Placement checks the actual collision surface at the top of the block below. A top slab works because its real upper face reaches the root boundary. A bottom slab is **NOT_SUPPORTED** in this prototype: its upper surface is half a block below the authored base, and no lowered render/collision pose is implemented. This refusal preserves the foreign slab and item instead of leaving a floating wall. Thin neighboring decorations are not refused on bounding boxes. Actual entity collision and the occupied root destination are checked.

Pick/drop items store material, profile, course, connection mode and yaw. MANUAL items also retain exact arm/post flags. Water is recomputed at the destination. Ordinary unselected stacks retain their remaining count and NBT unchanged. Save palettes contain normal string properties and need no fixed numeric object ID.

The current module has one uniform low/tall course per instance. Mixed-height source recipes need their own catalog evidence and membership rules; no mixed-height conversion is claimed. There are 32,768 property combinations in this prototype.

The original generated 592-clause multipart table exhausted a real ordinary client's 4 GB heap during resource baking. A diagnostic copy changing only that wall table and a missing builder alias loaded the same scene and exited normally at 4 GB; it then independently exposed missing imported atlas sprites. That diagnostic is not a playable production artifact. `reports/CLIENT_DIAGNOSTIC_WALL_MODEL_ISOLATION_V5.json` and its classification preserve this evidence.

The replacement Fabric blockstate resolver binds every state directly to an appearance key. Forty existing source primitive resources bake once to 160 native quarter-turn/UV variants; 4,608 lightweight appearance wrappers share the immutable primitive quads. Water and AUTO/MANUAL do not alter art, and material is visually irrelevant to LOW arms. Each actual visual key has its own particle/appearance wrapper. Native model-manager rerender checks cover material, profile, yaw, course, arm recipe and post changes. Server properties, model geometry, textures, rotation semantics and physics are unchanged. `tools/audit_wall_model_provider.py` independently compares every state against the preserved old selector table: all ordered model IDs, native X/Y rotations, UV-lock values and weights match, with zero mismatches. Existing intrinsic diagonal element rotations remain applied once.

The first actual shared-provider client (V6) loaded/saved/exited normally at 4 GB and passed preceding source/atlas/inventory/ladder observations, but its first wall arm returned zero quads. Native `ModelLoader` bytecode proves it links parents only for top-level `modelsToBake`; Fabric `DelegatingUnbakedModel.setParents` is empty, so dependency-only primitive wrappers had not inherited geometry. The corrected top-level state delegate forwards that parent-link phase once to the shared bank while retaining Fabric's bake/cache delegation. It adds no extra top-level primitive bakes. QA records parent-link status and all 160 primitive geometry counts before wall checks.

`CLIENT_FIRST_SET_MINIMAL_V7.json` records actual PASS on production SHA `1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9`: ordinary 4 GB client, successful native bake/atlas/inventory observations, 512 representative wall states, all six rerender changes, exact shared-cache counts, normal world save and exit0. This verifies the resource and load issue. It does not certify manual graphics, gameplay or measured heap allocation; shader and full-mod client evidence are separately recorded. Every original source model/texture remains independently addressable and the old multipart bytes are preserved for comparison.

For a local disposable new-build scene, prepare a stone platform, obtain the Creative prototype wall and wall builder, and place a straight low MANUAL segment:

```mcfunction
fill ~-4 ~-1 ~-4 ~4 ~-1 ~4 minecraft:stone
setblock ~ ~ ~ bloodborne_dw:prototype_wall[connections=manual,rotation=0,north=true,south=true,east=false,west=false,post=false,course=low,material=4,profile=base]
give @s bloodborne_dw:prototype_wall 16
give @s bloodborne_dw:wall_builder
```

Turn the placed segment eight times, walk through its empty corners, target its visible bar, pick it, and place the picked item on a top slab. These commands are a separate test scene; they do not generate the requested production gallery or modify the original source save.
