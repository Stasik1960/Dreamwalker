# NightmareRunning QA + ALT — Minecraft 1.20.1 / Fabric

1. Replace the previous Bloodborne Blocks JAR with
   `bloodborne-blocks-2.1.0-alpha.1-nightmare-qa-alt-mc1.20.1.jar`.
   Do not keep both versions in `mods/`. Internal mod version remains `2.1.0-alpha.1`.
2. Extract `nightmare-qa-alt-gallery-20260923-final.zip` into `saves/`.
   The resulting directory `nightmare-qa-alt-gallery-20260923-final` must contain
   `level.dat` directly. The original city is not part of this gallery ZIP.
3. The gallery has 286 families / 1160 specimens on neutral isolated pads.
   Creative flight is useful. Read `gallery-positions.json` for `/tp` commands
   and `alt-proof-positions.json` for the three BASE/ALT pairs. Use
   `/bloodborne debug target` when reporting issues.
4. `Bloodborne-Alt-Visual-Starter-Kit.zip` is an optional normal resource pack.
   Initially its appearance equals BASE. Extract/edit its ALT model JSONs and
   copied textures; its README and manifest explain model paths/texture slots.
   Repack/install in `resourcepacks/`, enable, reload resources. Select objects
   with `/bloodborne visual alt` (permission level 2).

Regional edits: stand at one corner and run `/bloodborne visual pos1`, move to
the other and run `pos2`, then `/bloodborne visual region alt` or `base`.
The limit is 32768 loaded cells; helpers resolve to masters; no permanent region.

**Not visually accepted yet.** Eight original-world examples are safely blocked
by protected neighboring cells, not silently overwritten. The unnamed fence
needs a new debug screenshot. This is not a full-city conversion release.

Full changes, exact replacement IDs, commands and limitations:
[NIGHTMARE-QA-ALT.md](../../../Bloodborne-Blocks/docs/NIGHTMARE-QA-ALT.md).
Checks: [nightmare-checks](../../../Bloodborne-Blocks/docs/nightmare-checks/).
The eight previously unmentioned QA2 families remain unapproved:
`o_c003`, `o_c046`, `o_c1319`, `o_c474`, `o_c561`, `o_cases_0`, `o_iron_gate`,
`o_iron_railing`.
