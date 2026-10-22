Dreamwalker BB: city streaming and optional DH district pilot (2026-10-18)

Production changes are in src/architecture. Ordinary `gradlew build` does not
require Distant Horizons and does not include the optional adapter. The current
mod_version in gradle.properties identifies the candidate used in native QA.
Lighting, texture/model/catalog data, wire and save formats were preserved.

Reproduce the focused production validation with validate.ps1 -Tests -Case NAME.
It builds the mod, checks transaction/rollback invariants and runs 12 actual
Minecraft GameTests. The local wrapper intentionally refuses another Java
process and uses the project's configured Java 17 / cached WorldEdit API.
Run this through the workspace storage Guard as required by AGENTS.md.

prepare_native.py / run_native_case.py / analyze_native.py record a native
singleplayer route in an isolated copied QA save. They are workstation QA
helpers, not an installer and not portable launcher settings. They preserve
the user's original world/profile. See the final report for timing scope and
the old brick phase's frame-sample cap. Do not compare aggregate old-route
frame percentiles as though they covered every frame.

Optional DH build:
  gradlew remapDhPilotJar -Pdh_pilot_jar=ABSOLUTE_PATH_TO_DH_JAR
Pin Distant Horizons 3.3.3 for Minecraft 1.20.1, official version 6UnEfsRQ:
  https://modrinth.com/mod/distanthorizons/version/6UnEfsRQ
SHA256 a537a71171d935540b56b7598d0de2c106f6f5026336c713d89a1d9ea0ea14e3
The optional adapter is client-only and accepts only this DH release.

export_dh_scene.py is an offline, read-only district exporter. It needs Python,
numpy and Pillow, the original bbmc_v15_resource.zip, Minecraft 1.20.1 client
JAR, this project's architecture resources and the matching production JAR.
Pass --world --out --bounds --resource-pack --minecraft-jar --mod-jar explicitly
outside the prepared workstation. --bounds X0,Z0,X1,Z1 must align to chunks and
span at most 128 by 128 blocks. static-pose-materials.json contains only finite
material/model mappings; it contains no world coordinates or owner identities.
world_io.py is the project's existing NBT/Anvil reader, copied unchanged.

bind_dh_scene.py is a standalone Windows Python utility. With Minecraft closed:
  python bind_dh_scene.py --scene EXPORTED_SCENE --world SAVE_FOLDER --main-jar MOD_JAR --check
Remove --check to add the scene folder. It refuses an open/locked world, reparse
points and an existing scene; verifies data/catalog hashes and binds the exact
save path. It never changes region files, player data or game settings.

Native pilot result: the API renders the exported district with its ordinary
target chunk absent, but visual validation FAILED. The selection-box proxy
distorts the spire and leaves floating details. This is a diagnostic adapter,
not a recommended gameplay add-on. A useful city LOD needs actual posed visual
model geometry and alpha-aware export, followed by representative city tests.

The scene represents one frozen 128x128 district, not the entire city. It uses
selection geometry on a half-block grid and area-weighted original texture
colors, not textured visual models. Windows, roofs, glass and silhouettes can
differ; edits require re-export. The adapter works in singleplayer Overworld.
It registers bounded DH custom box groups without loading remote FULL chunks.
Its read-only flag is scoped to that bound frozen scene and is restored when
released. It is a pilot; consult the native screenshots/results before use.
The QA-only dreamwalker-dh-pilot-qa.json opt-in disables DH distant generation
through an in-process API override. Do not copy this marker into a user profile.

Third-party DH remains unmodified. License and source links are provided with
the optional pilot package. Ordinary production use needs neither DH nor this
export/binding tool.
