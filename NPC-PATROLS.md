# DreamWalker v22 NPC patrols

Install the same DreamWalker JAR on the Fabric 1.20.1 client and server. Use the existing Stage Director's Wand (`bloodborne_dw:bb_npc_tool`, «Жезл постановщика»). Right-click cycles its modes.

- In the idle sound mode, left-click a DreamWalker NPC to enable or disable that NPC's ambient sounds. Newly spawned and older NPCs are silent while idle until enabled. The attack, movement and death sound hooks are unchanged.
- In patrol mode, left-click the NPC, then left-click the ground for each waypoint. The NPC heads for the new point as soon as it is added. Click the selected NPC again to switch between looping back to the first point and reversing through the points.
- In the original NPC special-action mode, select a patrolling NPC and click a destination. On arrival, it waits 200 server ticks and resumes patrol from the nearest waypoint.
- In clear patrol mode, left-click the NPC to clear its waypoints and stop its movement order.
- Both manual orders and patrols recover after 200 ticks of almost no movement. The fallback requires a loaded, safe landing position on the chosen ground. Routes and idle preferences are stored with the mob.

The new modes act on DreamWalker NPCs. They use the same editing permissions as the existing tool.

## Build and verification

Use the Java 17 and Gradle commands in [BUILDING-FIXES.md](BUILDING-FIXES.md). Package the remapped window and placement overlay with `--version-suffix .forms.6`. Use the original pinned Bush Restoration JAR and the same Original Pick input as for Doors and Fences .2.

The [disposable client harness](Bloodborne-Startup/bush-restoration/tests/client-qa/README.md) uses actual mouse input for the wand and observes the NPC on the client and integrated server. Run the NPC suite with `-Ddw.qa.npc=true`. Run the map captures with `-Ddw.qa.mapNight=true` on a separate copy of the updated map.

The updated Bloodborne map uses the existing Fabric profile and map dependencies, including Yuushya and Handcrafted for the restored rooms. See the [interior and vegetation restoration](DW-City-Visuals/tools/historic_restoration/README.md) and [map lighting](MAP-LIGHTING.md) for locations and procedures.
