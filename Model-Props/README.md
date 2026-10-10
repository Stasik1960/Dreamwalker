# Model Props — Fabric 1.20.1

Model Props turns ordinary Java/Blockbench model JSON files into inert handheld
items with synchronized keyframe animations and action sounds. The server owns
the catalog and stores one shared hand transform for every model.

When an F7-managed model is uploaded again, omitting animations keeps the old
clip bundle. Unselected sounds are also kept while their clips still exist;
selecting a new OGG replaces that action or clip sound.

## Requirements

- Minecraft Java Edition 1.20.1
- Fabric Loader 0.16.14 or newer
- Fabric API 0.92.6+1.20.1 or newer for Minecraft 1.20.1
- Java 17
- The mod JAR on the server and every client

## Install

1. Put `modelprops-fabric-1.20.1-1.3.1.jar` and Fabric API in the server's and
   clients' `mods` folders.
2. Start the server once. The mod creates `config/modelprops` and an example cube.
3. Put model files in `config/modelprops/models` and PNG files in
   `config/modelprops/textures` **on the server**. This is one global server
   catalog, not a separate catalog per world.
4. Run `/modelprops reload`. Connected clients receive the new catalog
   automatically; no external resource-pack host is required.

## File layout and IDs

```text
config/modelprops/
├─ models/
│  ├─ chair.json                    -> modelprops:chair
│  └─ mypack/
│     └─ furniture/chair.json       -> mypack:furniture/chair
├─ textures/
│  ├─ chair.png
│  └─ mypack/furniture/chair.png
├─ animations/                       (optional merged Model Props animation JSON)
├─ sounds/                           (optional action-or-clip-name.ogg files)
└─ transforms.json                  (written by the server)
```

The first folder under `models` is treated as the namespace. A JSON directly in
`models` receives the `modelprops` namespace. Use lowercase file names containing
letters, digits, `_`, `-`, and `.`.

The supported input is the vanilla **Java Block/Item** JSON format produced by
Blockbench: `textures`, `elements`, `from`, `to`, element `rotation`, face `uv`,
face `rotation`, and `display` transforms. Flat `item/generated`-style files with
`layer0` are also accepted. Bedrock entity and GeckoLib model JSON are different
formats and are not supported.

Example:

```json
{
  "display_name": "Wooden Chair",
  "textures": {
    "0": "mypack:furniture/chair"
  },
  "elements": [
    {
      "from": [2, 0, 2],
      "to": [14, 8, 14],
      "faces": {
        "north": { "uv": [0, 0, 16, 8], "texture": "#0" },
        "south": { "uv": [0, 0, 16, 8], "texture": "#0" }
      }
    }
  ]
}
```

`display_name` is an optional Model Props extension. Without it, a readable name
is generated from the file name. A texture reference `mypack:path/name` first
looks for `textures/mypack/path/name.png`; an unnamespaced `path/name` looks for
`textures/path/name.png`. References to textures already present
in Minecraft or another installed resource pack also work.

## In game

- All loaded variants appear in the **Model Props** creative tab.
- Press **F7** to open the two-tab upload menu. Choose the model and PNG, then an
  optional set of animation JSON files and Ogg Vorbis files for individual
  actions or named clips. Up to 32 source JSON files can be selected repeatedly
  or together; the menu merges their clips and rejects duplicate clip names or
  conflicting action mappings. One **Upload everything** button installs the
  complete bundle atomically. Files can also be dragged onto the menu.
- Press **F6** while holding a configured item. A sole manual clip starts at
  once; multiple manual clips open a paginated picker.
- `/modelprops list` lists IDs.
- `/modelprops give <players> <model> [count]` gives a model (operator level 2).
- `/modelprops reload` safely rescans JSON/PNG and resynchronizes players.
- `/modelprops reset <model>` resets its shared hand transform.
- Hold a Model Props item and press **F8** to open the live transform editor.

The F7 upload applies the selected PNG to every face in a cuboid model (or to
`layer0` in a flat item model), installs both files in the server catalog, and
immediately synchronizes all connected players. If the requested ID belongs to
a manually installed model, the server keeps it and chooses a free `_2`, `_3`,
and so on. Re-uploading a model previously created by this menu replaces that
upload. Only operators with permission level 2 may upload.

Only operators with permission level 2 can save edits. Scale, XYZ position, and
XYZ rotation are validated by the server, written atomically to
`config/modelprops/transforms.json`, and broadcast to everyone. The adjustment is
applied only in first- and third-person hand render modes; GUI, dropped-item, and
item-frame rendering keep the JSON model's own `display` settings.

## CPM-style animation format

Animations use a safe, standalone keyframe JSON inspired by CPM's editor. A
standalone `.cpmproject` animation cannot be attached reliably because CPM tracks
refer to the original CPM model's internal `storeID` values. Use the included
[`examples/animation_example.json`](examples/animation_example.json) as a template.
Select [`examples/animation_extra_example.json`](examples/animation_extra_example.json)
alongside it to try multi-file merging and the F6 picker immediately.

For a manual install, put the merged sidecar at
`animations/<namespace>/<model-path>.json`, for example
`animations/mypack/furniture/chair.json`.

```json
{
  "format_version": 1,
  "clips": {
    "idle": {
      "duration_ms": 2000,
      "loop": true,
      "interpolation": "smooth",
      "tracks": {
        "root": [
          { "time_ms": 0, "position": [0, 0, 0] },
          { "time_ms": 1000, "position": [0, 1, 0], "rotation": [0, 8, 0] },
          { "time_ms": 2000, "position": [0, 0, 0] }
        ]
      }
    }
  }
}
```

Supported animation actions are `idle`, `equip`, `swing`, `use`, `attack`, and `custom`.
Clips with those names map automatically, or an `actions` object can map an
action to another clip. Tracks support position (model pixels), rotation
(degrees), scale, and `step`, `linear`, or `smooth` interpolation. `root` affects
the entire item. To animate one cuboid, assign `"modelprops_id": "blade"` (or
`name`) to that element and use `blade` as the track target; unnamed elements are
`element_0`, `element_1`, and so on.

F7 can merge several files into one model bundle containing up to 32 clips.
Automatic `idle`, `equip`, `swing`, `use`, and `attack` mappings keep their old
behavior; other named clips, plus the clip mapped to `custom`, are available from
the F6 picker.

For manual installs, sounds live at
`sounds/<namespace>/<encoded-model-path>/<action-or-clip>.ogg`. Slashes inside a model
path are encoded as `%2F`; for example `mypack:furniture/chair` uses
`sounds/mypack/furniture%2Fchair/dance.ogg` for the `dance` clip. F7 handles this
automatically; its Sound button cycles through fixed actions and discovered clips.

Action sounds must be Ogg Vorbis (not Opus), mono/stereo, 8–48 kHz, and at most
30 seconds. Animation starts and sound playback share the same server-authorized
event, so nearby players see and hear the same action.
Legacy sounds remain supported for `equip`, `swing`, `use`, `attack`, and
`custom`. A named clip may have its own OGG when its file-safe name contains
ASCII letters, digits, `_`, `-`, or `.`, excluding `.`, `..`, and reserved
device names such as `CON`. `idle` is normally a looping background animation
and does not trigger sound by itself.

Named clip sounds and direct clip selection require Model Props **1.3.0** on
both the server and client. Existing action-based models and sounds remain
backward compatible.

## Limits and safety

- 512 models per catalog
- 8192 synchronized textures per catalog
- 256 KiB per JSON
- 4 MiB per PNG
- 512 KiB per animation JSON
- 32 sounds per model, 2 MiB per OGG and 8 MiB total
- 64 MiB total synchronized catalog
- One active upload per player, with checksums and strict chunk ordering
- Paths are normalized and cannot leave `config/modelprops`

PNG dimensions are limited to 4096×4096 and 16 million pixels; a model may have
up to 1024 elements. F7 merges up to 32 source animation files; the resulting
bundle may contain up to 32 clips of 60 seconds,
2048 tracks, and 8192 keyframes total (512 per track).
- Existing stacks whose model was removed remain items but render as a missing
  model until that ID returns

The custom renderer intentionally supports the common vanilla cuboid subset. It
does not currently implement parent-model inheritance, tint indices, emissive
faces, per-face culling, element-rotation `rescale`, or Blockbench's non-vanilla
freeform meshes.

## Build from source

On Windows with Java 17:

```powershell
./gradlew.bat build
```

The remapped JAR is written to `build/libs`.
