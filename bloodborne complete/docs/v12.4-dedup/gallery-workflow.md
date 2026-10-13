# V12.4 fresh gallery workflow

The new tools read the final combined production archive. They do not alter
the original v16 map, earlier galleries, V12 source models or resource packs.
World outputs must stay in a fresh directory under `bloodborne complete/build`.
Run controlled project processes through `CodexControl/Guard.ps1 -Mode Operation`.

1. `tools/prepare_gallery_v124_input.py --production-jar FINAL_JAR --output INPUT_JSON`
   creates the explicit `dreamwalker-flat-gallery-v124` marker from the exact
   shipped aliases, catalogue and model resources. Each NEW working type has
   one main station in its ordinary canonical state. Retired IDs are excluded,
   but their source-map presence is included when classifying the canonical
   type. The old error/red stations stay removed.
2. `tools/build_gallery_v124_addon.py --production-jar FINAL_JAR --output FRESH_BUILD_DIR`
   builds only the opt-in QA author and capture add-on. The production JAR hash
   must remain unchanged. The author accepts only the marker and a fresh
   isolated flat world; it places architecture, RP objects and framed items
   using the existing saved ownership/mount APIs.
3. Author through the established isolated-server harness. New direct composite
   placements carry `ImportedTypeVersion=1`; V12 payloads remain unchanged.
   Native V12 walls use ordinary AUTO connections. The original diagonal post
   keeps its intrinsic native canonical restriction.
4. `tools/finalize_gallery_v124_world.py --world FRESH_WORLD --manifest AUTHOR_OUTPUT`
   adds Russian navigation and an index to this newly created world only.
5. `tools/verify_gallery_v124.py --world FRESH_WORLD --manifest AUTHOR_OUTPUT --production-jar FINAL_JAR --report REPORT_JSON`
   proves typed roots/UUIDs, ledger contributions, saved states, source geometry,
   actual platform support, model-sized platforms, exactly two empty cells
   between platforms, labels and frame supports. It also compares the main
   working types with the final archive and verifies V12 type IDs/items remain.
6. Open/save/reopen with production and its normal dependencies, without the
   author add-on, then run the verifier with `--compare PREVIOUS_REPORT`.
   A native client launch and rendered capture remain separate checks;
   saved-NBT PASS does not replace them.

Final execution is bound to production JAR SHA-256
`4c9e202cff6ae5acd17386a30547726948a0d0d9babf4e529c84ec5c51b0c640`.
The final-2 author created 675 supported exhibits: all 572 NEW working types,
80 original V12 placeable types and 23 nonplaceable Creative entries in frames.
The 473 compatibility aliases and all 60 previous red/error stations are absent.
There are zero diagnostic platforms. Source-analysis metadata is retained under
`historicalSourceAuditProvenance`; current counts/commits have their own fields.

`gallery-before-reopen-final2.json`, `gallery-after-server-final2.json` and
`gallery-after-client-final2.json` verify every exhibit, typed UUID/NBT/state,
ownership ledger, Russian ID label, active visual support plane, platform size
and two-cell gaps. They are PASS. The production-only server reopened and saved
the world without the author add-on. The native integrated client captured 48
unmodified 1280×720 frames at render distance 8 and then stopped/saved normally.
`gallery-client-final.json` binds this native run and all 675 preserved objects.
Pixel observations are separately recorded in the three assigned review
reports. All 48 original screenshots have been inspected; the combined result
is PASS in `gallery-pixel-final.json`, with exact capture and image hashes.

The release world is the client-reopened
`build/runtime-client-v124-flatgallery-client-final-2/saves/prototype-fixture`.
Normal use requires only the production mod and its documented dependencies;
QA author/camera code is excluded from the combined JAR.

The earlier C30 gallery camera failure is preserved in
`gallery-client-final-attempt1-fail.json`. It is not final acceptance: the fresh
4c9e run uses clear alternate camera positions and render distance 8. No V12
models, original v16 map, previous gallery or source resource pack were changed.
