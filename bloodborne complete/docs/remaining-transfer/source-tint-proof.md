# Source grass palette and scoped rendering

The v15 source pack contains `assets/minecraft/textures/colormap/grass.png`, SHA-256 `d34580a3adbdce78a83b2c77a9d2a590cfa28ba2c96a4407d99ff402b44f29c9`. It is 256 × 256, has 242 distinct opaque RGBA values, and is not white. Its default grass climate sample, temperature 0.5 / humidity 1.0, selects pixel (127,127), RGB (119,122,127), `0x777A7F`. The full PNG was copied unchanged to `assets/bloodborne_dw/textures/remaining/colormap/grass.png`; it does not override a Minecraft resource. `imported-resources.json` binds exact source bytes as `kind=texture`, `role=source_colormap`.

The source `optifine/color.properties` contains only `lilypad=3c8207`. There is no active finished imported artwork with a tinted lily-pad face. The source foliage palette has no active finished imported artwork that uses a native foliage color provider. These resources therefore have no added renderer consumer in this stage.

The exhaustive resolved source graph identifies six active models with non-negative face `tintindex`, all index 0. Three source carriers have the native grass color provider:

| Imported ID | Source model | Source carrier | Tint |
| --- | --- | --- | --- |
| `bloodborne_dw:source_grass_block` | `minecraft:block/grass_block` | `minecraft:grass_block[snowy=false]` | Authored grass palette |
| `bloodborne_dw:source_bricks_grass_block_0` | `minecraft:block/bricks/grass_block_0` | `minecraft:grass_block[snowy=false]` | Authored grass palette |
| `bloodborne_dw:source_potted_fern` | `minecraft:block/potted_fern` → `tinted_flower_pot_cross` | `minecraft:potted_fern` | Authored grass palette |
| `bloodborne_dw:source_allium` | `minecraft:block/allium` | `minecraft:allium` | White, native provider absent |
| `bloodborne_dw:source_quartz_stairs_outer_1` | `minecraft:block/quartz_stairs_outer_1` | `minecraft:quartz_stairs` | White, native provider absent |
| `bloodborne_dw:source_stairs_cut_copper_stairs_inner_right` | `minecraft:block/stairs/cut_copper_stairs_inner_right` | `minecraft:cut_copper_stairs` | White, native provider absent |

Each tinted model is used only on its stated source carrier. No finished colored/uncolored carrier variants need splitting. The tint adjustment touches only the first three exact IDs and no model, face, UV, descriptor, block state, ID, behavior, collision or source placement offset.

`RemainingSourceTint` preserves the native grass lookup formula: clamp the biome's float temperature and downfall to [0,1], multiply humidity by temperature, and truncate `(1-temperature)*255` / `(1-humidity*temperature)*255` to the palette coordinates. Biome grass-color overrides and `GrassColorModifier` still apply. The placed object calls `ClientWorld.calculateColor` at its root position, retaining the current native biome blend radius and rounding. Inventory rendering uses the authored default climate, independent of the player's biome. The existing baked renderer applies the supplied RGB only to quads with `hasColor`; untinted faces remain white. Existing objects and all other new IDs receive exactly (1,1,1).

Downfall is private in Minecraft 1.20.1. The implementation obtains its exact float once per biome through `Biome.NETWORK_CODEC`; the codec contains weather and effects, with no generation or spawn dependencies. Plain `JsonOps` is valid for encoding its sound entries: `RegistryElementCodec.encode` explicitly encodes `RegistryEntry.value()` when operations do not supply a registry. No access mixin or global color provider was added. The palette is read through the resource manager and reloaded by a source-specific client resource listener.

Primary evidence comes from the locally cached named Minecraft 1.20.1 classes, not inferred JSON presence. `tools/inspect_remaining_tint.py` writes class bytecode hashes, `javap -c -p` output, source palette histograms and the exhaustive active tinted-model list below `build/remaining-transfer/tint-primary-evidence/`. In particular, `BlockModelRenderer.renderQuads` branches on `BakedQuad.hasColor`; `BlockColors.create` registers the same grass provider for grass block and potted fern; `GrassColors.getDefaultColor` uses exactly (0.5,1); and `ClientWorld.calculateColor` implements blending. `ClientWorld.getColor` cannot accept an arbitrary new resolver because its cache contains only predefined resolvers, so the source path uses `calculateColor` directly.

Static source/resource evidence: **PASS**. Final combined JAR SHA `9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117` also passed actual native `renderParts` vertex RGB QA in `build/runtime-client-remaining-native-final4/remaining-transfer-client-qa.json`. All three mapped IDs emitted exactly the independently decoded authored palette's world RGB `0x6F7578` and inventory RGB `0x777A7F` on tinted faces, with white untinted faces. The independent source-bricks and protected original double-door controls remained white. All five cases: **PASS**. Explicit F3+T reload and additional biome fixtures: **NOT_RUN**; their lookup follows the verified native formulas, but those checks are not inferred from this client run.
