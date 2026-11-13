package dev.dreamwalker.citypick;

import java.util.List;

/** Verifies the packaged map data against the examples that motivated the feature. */
public final class PickIndexCheck {
    private record Example(int x, int y, int z, int visual, String registry, String variant,
                           double eyeX, double feetY, double eyeZ, double yaw, double pitch) {}
    private static final String CATALOG = "fbefe5684ae68a9659fdf0b3eaf62a4e9936d0c5c3cf07f662e5fac8339eb112";
    private static final String VERSION =
            "0.1.0-v12.7.4-visual-physics.8-building-water.3-stutter.1-visual.4-rc3-dwfix.22-cityeditor.2.1-cornerlight.2-rgb.2-altfix.1-cityfollowup.1-rgbrelief.1-bbhalf.1-builder.1.studio.2-nocollide.1";
    private static final List<Example> EXAMPLES = List.of(
            new Example(93, 124, -708, 68060, "source_addon_wall_deco_3", "2",
                    97.484, 122.90780, -707.696, 88.4, 5.2),
            new Example(447, 44, -838, 40471, "source_black_wool", "0",
                    444.405, 43.00313, -838.782, -64.2, 5.0),
            new Example(445, 44, -831, 35446, "source_cyan_glazed_terracotta", "0",
                    444.809, 43.00313, -833.297, -9.6, 9.2),
            new Example(445, 43, -835, 88116, "source_gray_wool", "0",
                    445.021, 43.00313, -836.632, -7.9, 38.7)
    );

    public static void main(String[] args) throws Exception {
        var table = new PickIndex();
        for (var e : EXAMPLES) {
            var chunk = new PickIndex.ChunkKey("minecraft", "overworld", e.x >> 4, e.z >> 4);
            int cell = PickIndex.cellKey(e.x, e.y, e.z);
            var ray = ray(e);
            var result = table.find(chunk, cell, e.visual, CATALOG, VERSION, ray);
            require(result.size() == 1, "Expected one aimed source, found " + result);
            var pose = result.get(0);
            require(pose.registryId().equals("bloodborne_dw:" + e.registry), "Wrong registry at " + e);
            require(e.variant.equals(pose.properties().get("variant")), "Wrong item variant at " + e);
            require(table.find(chunk, cell, e.visual + 1, CATALOG, VERSION, ray).isEmpty(), "Replaced visual picked");
            require(table.find(chunk, cell, e.visual, "invalid", VERSION, ray).isEmpty(), "Wrong catalog picked");
            require(table.find(chunk, PickIndex.cellKey(e.x, -64, e.z), e.visual, CATALOG, VERSION, ray).isEmpty(),
                    "Unindexed position picked");
            var wrong = new PickIndex.ChunkKey("minecraft", "the_end", e.x >> 4, e.z >> 4);
            require(table.find(wrong, cell, e.visual, CATALOG, VERSION, ray).isEmpty(), "Other dimension picked");
            System.out.println("PASS " + e.x + "," + e.y + "," + e.z + " -> " + pose.registryId()
                    + " variant " + pose.properties().get("variant"));
        }
        var e = EXAMPLES.get(1);
        var chunk = new PickIndex.ChunkKey("minecraft", "overworld", e.x >> 4, e.z >> 4);
        var ambiguous = table.find(chunk, PickIndex.cellKey(e.x, e.y, e.z), e.visual, CATALOG, VERSION,
                new FragmentRay(2, 2, 2, 0, 1, 0));
        require(ambiguous.size() == 2 && ambiguous.stream().anyMatch(
                pose -> pose.registryId().endsWith("source_cyan_glazed_terracotta")),
                "Ambiguous hit must expose both real source choices");
        var oldChunk = new PickIndex.ChunkKey("minecraft", "overworld", 228 >> 4, -815 >> 4);
        int oldCell = PickIndex.cellKey(228, 51, -815);
        var oldRay = new FragmentRay(-2, .5, .5, 1, 0, 0);
        var legacy = table.find(oldChunk, oldCell, -4662, CATALOG, VERSION, oldRay);
        require(legacy.size() == 1 && legacy.get(0).registryId().equals("bloodborne_dw:source_weathered_cut_copper"),
                "Wrong original for a retained static architecture fragment");
        require(table.find(oldChunk, oldCell, -4662, CATALOG, "wrong version", oldRay).isEmpty(),
                "Legacy recipe used against a different Dreamwalker version");
        System.out.println("PASS 228,51,-815 -> bloodborne_dw:source_weathered_cut_copper");
        System.out.println("PASS coordinate/visual/catalog gates, both overlap candidates");
    }

    private static FragmentRay ray(Example e) {
        double yaw = Math.toRadians(e.yaw), pitch = Math.toRadians(e.pitch);
        return new FragmentRay(e.eyeX - e.x, e.feetY + 1.62 - e.y, e.eyeZ - e.z,
                -Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new AssertionError(error);
    }
}
