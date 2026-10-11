package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.util.math.Direction;

/** Only post-V12 native tread families opt into these vanilla-style states. */
public final class ImportedStairShapes {
    private static final Set<String> TYPES = load("stairTypes");
    private static final Set<String> LADDERS = load("ladderTypes");
    private ImportedStairShapes() {}

    private static Set<String> load(String key) {
        var input = ImportedStairShapes.class.getResourceAsStream("/bloodborne_dw/imported-stair-types.json");
        if (input == null) throw new IllegalStateException("Missing imported functional stair types");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var document = JsonParser.parseReader(reader).getAsJsonObject();
            if (document.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported imported stair types");
            Set<String> result = new HashSet<>();
            for (JsonElement value : document.getAsJsonArray(key)) {
                String path = value.getAsJsonObject().get("path").getAsString();
                if (!path.matches("source_[a-z0-9_]+") || !result.add(path)) throw new IllegalArgumentException("Invalid imported stair type " + path);
            }
            return Set.copyOf(result);
        } catch (Exception failure) { throw new IllegalStateException("Invalid imported stair type manifest", failure); }
    }

    public static boolean isStairPath(String path) { return TYPES.contains(path); }
    public static boolean isLadderPath(String path) { return LADDERS.contains(path); }

    public static int variant(BlockState state) {
        if (state.getBlock() instanceof ImportedLadderBlock) return ImportedLadderBlock.variant(state);
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state.get(CompositeRootBlock.VARIANT);
        int shape = switch (state.get(ImportedStairBlock.SHAPE)) {
            case STRAIGHT -> 0;
            case INNER_LEFT -> 1;
            case INNER_RIGHT -> 2;
            case OUTER_LEFT -> 3;
            case OUTER_RIGHT -> 4;
        };
        return (state.get(ImportedStairBlock.HALF) == BlockHalf.TOP ? 5 : 0) + shape;
    }

    public static BlockState normalize(BlockState state) {
        if (!(state.getBlock() instanceof ImportedStairBlock || state.getBlock() instanceof ImportedLadderBlock)) return state;
        return state.with(CompositeRootBlock.VARIANT, variant(state));
    }

    public static int poseVariant(BlockState state) {
        int result = variant(state);
        return state.getBlock() instanceof ImportedStairBlock block && block.spec.id.getPath().equals("source_stairs_polished_deepslate_stairs_bottom") && state.get(ImportedStairBlock.TRIM) ? result + 10 : result;
    }

    public static int rotation(Direction facing) {
        return switch (facing) { case EAST -> 0; case SOUTH -> 2; case WEST -> 4; case NORTH -> 6; default -> throw new IllegalArgumentException("Stair direction must be horizontal"); };
    }

    public static Direction facing(BlockState state) {
        return switch (Math.floorMod((state.get(CompositeRootBlock.ROTATION) + 1) / 2, 4)) {
            case 0 -> Direction.EAST; case 1 -> Direction.SOUTH; case 2 -> Direction.WEST; default -> Direction.NORTH;
        };
    }

    public static BlockState fromVariant(BlockState state, int variant) {
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state;
        int value = Math.floorMod(variant, 10);
        StairShape shape = switch (value % 5) {
            case 0 -> StairShape.STRAIGHT; case 1 -> StairShape.INNER_LEFT; case 2 -> StairShape.INNER_RIGHT;
            case 3 -> StairShape.OUTER_LEFT; default -> StairShape.OUTER_RIGHT;
        };
        return normalize(state.with(ImportedStairBlock.HALF, value >= 5 ? BlockHalf.TOP : BlockHalf.BOTTOM).with(ImportedStairBlock.SHAPE, shape));
    }

    public static BlockState fromPose(BlockState state, int pose) {
        if (state.getBlock() instanceof ImportedLadderBlock)
            return ImportedLadderBlock.normalize(state.with(ImportedLadderBlock.HEIGHT, pose == 1 ? ImportedLadderBlock.Height.TWO : ImportedLadderBlock.Height.ONE));
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state.with(CompositeRootBlock.VARIANT, pose);
        return fromVariant(state, pose).with(ImportedStairBlock.TRIM, pose >= 10);
    }
}
