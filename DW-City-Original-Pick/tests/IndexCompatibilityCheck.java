package dev.dreamwalker.citypick;

import java.io.DataInputStream;
import java.util.List;

/** Runs the actual add-on's lookup against entries from the two released dimensions. */
public final class IndexCompatibilityCheck {
    private static final String OLD = "fbefe5684ae68a9659fdf0b3eaf62a4e9936d0c5c3cf07f662e5fac8339eb112";
    private static final String BUSH = "2bd740ff6ad95102f4c04521f2587a7516b432bd26ff6203dc10cd4d4fb88574";
    private static final String NS = "bloodborne_dw:";
    private static final FragmentRay MISSES_GEOMETRY = new FragmentRay(-1, -1, -1, -1, 0, 0);

    public static void main(String[] args) throws Exception {
        String version;
        try (var data = new DataInputStream(IndexCompatibilityCheck.class.getResourceAsStream("/city_original_pick/poses.bin"))) {
            require(data.readInt() == 0x44575031, "bad pose table magic");
            require(data.readUTF().equals(BUSH), "pose table targets the wrong city catalog");
            version = data.readUTF();
        }
        require(version.matches(".*\\.forms\\.[123]"), "test requires the Distinct Forms distribution");

        PickIndex index = new PickIndex();
        var oldDimension = new PickIndex.ChunkKey("bloodborne_dw", "bloodborne_old", -1, -1);
        expect(index, oldDimension, 514739, 661, version, "source_cobbled_deepslate_stairs");
        expect(index, oldDimension, 508673, -791, version, "source_bricks_grass_block_0");
        require(index.find(oldDimension, 508673, -791, BUSH, version + ".wrong", MISSES_GEOMETRY).isEmpty(),
                "static fragment accepted the wrong release");
        require(index.find(oldDimension, 514739, 661, OLD, version, MISSES_GEOMETRY).isEmpty(),
                "native fragment accepted the wrong catalog");
        require(index.find(oldDimension, 514739, 662, BUSH, version, MISSES_GEOMETRY).isEmpty(),
                "changed fragment still returned the previous source");

        var residual = new PickIndex.ChunkKey("minecraft", "overworld", -9, -67);
        expect(index, residual, PickIndex.cellKey(-136, 27, -1061), 157579, version, "source_red_nether_bricks");
        int shared = PickIndex.cellKey(-136, 28, -1061);
        expect(index, residual, shared, 157580, version,
                "source_polished_blackstone", "source_red_nether_bricks");
        expect(index, residual, shared, 157580, version, new FragmentRay(0.4, 1.2, 0.3, 0, -1, 0),
                "source_polished_blackstone");
        expect(index, residual, shared, 157580, version, new FragmentRay(0.4, 0.8, 0.3, 0, -1, 0),
                "source_red_nether_bricks");
        var removed = new PickIndex.ChunkKey("minecraft", "overworld", -8, -69);
        expect(index, removed, PickIndex.cellKey(-118, 16, -1102), 157651, version);

        var pillar = new PickIndex.ChunkKey("minecraft", "overworld", 8, -50);
        int base = PickIndex.cellKey(143, 89, -791);
        expect(index, pillar, base, 4089, version,
                new FragmentRay(-1, 0.2, 0.5, 1, 0, 0), "source_dark_oak_trapdoor_open");
        expect(index, pillar, base, 4089, version,
                new FragmentRay(-1, 0.85, 0.1, 1, 0, 0), "source_assembly_deepslate_brick_wall");
        expect(index, pillar, PickIndex.cellKey(143, 90, -791), 24069, version,
                "source_dark_oak_trapdoor_open");
        expect(index, pillar, PickIndex.cellKey(143, 91, -791), 25150, version,
                "source_dark_oak_trapdoor_open");

        var slabs = new PickIndex.ChunkKey("bloodborne_dw", "bloodborne_old", -1, -10);
        expect(index, slabs, 542046, 3784, version, "restored_slab_5ff0bf7f58a8b6d4_upper");
        var slab = index.find(slabs, 542046, 3784, BUSH, version, MISSES_GEOMETRY).get(0);
        require(slab.properties().equals(java.util.Map.of("facing", "north", "waterlogged", "false")),
                "slab did not preserve its dynamic properties");

        System.out.println("PASS City Original Pick native, static, residual, removed, distinct slabs, and both sides of the mixed pilaster route");
    }

    private static void expect(PickIndex index, PickIndex.ChunkKey chunk, int cell, int visual, String version,
            String... expected) throws Exception {
        expect(index, chunk, cell, visual, version, MISSES_GEOMETRY, expected);
    }

    private static void expect(PickIndex index, PickIndex.ChunkKey chunk, int cell, int visual, String version,
            FragmentRay ray, String... expected) throws Exception {
        var wanted = List.of(expected).stream().map(id -> NS + id).toList();
        var actual = index.find(chunk, cell, visual, BUSH, version, ray).stream()
                .map(PickIndex.Pose::registryId).toList();
        require(actual.equals(wanted), "fragment " + visual + ": expected " + wanted + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
