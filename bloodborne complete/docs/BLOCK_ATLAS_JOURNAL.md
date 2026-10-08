# Imported block sprite atlas registration

The ordinary1.20.1 client diagnostic loaded the copied world and returned through
normal save/exit, then reported `minecraft:missingno` on the ALT door bottom
frame. The imported PNG existed with exact source bytes. Its own identifier
was outside the default `textures/block` and `textures/item` atlas directories.
This affected the base/source, wall/source and tree_source import layouts.

`tools/prepare_block_atlas.py` writes one additive metadata file:
`src/architecture/resources/assets/minecraft/atlases/blocks.json`. Its16
`minecraft:single` entries name only `bloodborne_dw` sprite identifiers. There
are no filters, removals, aliases or vanilla sprite identifiers. The imported
model references, PNG paths, PNG bytes, geometry and UV stay unchanged. No
architectural vanilla model or texture is overridden.

The exact named1.20.1 `AtlasLoader.of` bytecode calls
`ResourceManager.getAllResources` and appends each resource's parsed sources
with `List.addAll`. Therefore the metadata adds sources to the vanilla block
atlas; it does not replace vanilla's source list. The bytecode and original
vanilla source-list hash are recorded in
`reports/BLOCK_ATLAS_LOADER_1_20_1_BYTECODE.txt` and
`reports/BLOCK_ATLAS_ADDITIVE_IMPORT.json`.

The offline check covers85 explicit own texture references, all16 exact source
PNGs and absence of any architectural Minecraft model/texture file. Run the
generator after adding imported architectural textures; `--check` detects
uncovered own texture references. Ordinary client QA must additionally confirm
all authored source, ladder and wall baked models have nonmissing sprites and
that the vanilla STONE sprite remains present. Client acceptance is separate
from this resource-loading check.

The V7 ordinary client check passes on production SHA
`1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9`.
All 16 added sprite identifiers occur in actual baked quads. The 757 checked
models include all 52 composite source models, 192 ladder states, 512 wall
representatives and vanilla STONE. None uses the missing model or missing
sprite; STONE retains its six quads with `minecraft:block/stone`. The client
opens the derived world, saves and exits normally. The metadata in the ordinary
production JAR is byte-exact to the audited additive source file, and that JAR
contains no `assets/minecraft/models` or `assets/minecraft/textures` overrides.
`tools/verify_block_atlas_client.py` independently checks the wrapper, raw client
output and JAR; evidence is `reports/BLOCK_ATLAS_RUNTIME_V7.json` and
`CLIENT_FIRST_SET_MINIMAL_V7.json`. This resource check does not approve art,
Creative UI, shader fidelity or manual gameplay.
