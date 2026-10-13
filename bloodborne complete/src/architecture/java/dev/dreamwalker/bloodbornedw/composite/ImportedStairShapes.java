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
        int value = state.get(CompositeRootBlock.VARIANT);
        return value < 10 ? value : 0;
    }

    /** One existing palette property encodes the five shapes in each half. */
    public static BlockHalf half(BlockState state) {
        return variant(state) >= 5 ? BlockHalf.TOP : BlockHalf.BOTTOM;
    }
    public static StairShape shape(BlockState state) {
        return switch (variant(state) % 5) {
            case 0 -> StairShape.STRAIGHT; case 1 -> StairShape.INNER_LEFT; case 2 -> StairShape.INNER_RIGHT;
            case 3 -> StairShape.OUTER_LEFT; default -> StairShape.OUTER_RIGHT;
        };
    }
    public static BlockState withHalf(BlockState state, BlockHalf half) {
        return state.with(CompositeRootBlock.VARIANT, variant(state) % 5 + (half == BlockHalf.TOP ? 5 : 0));
    }
    public static BlockState withShape(BlockState state, StairShape shape) {
        int index = switch (shape) {
            case STRAIGHT -> 0;
            case INNER_LEFT -> 1;
            case INNER_RIGHT -> 2;
            case OUTER_LEFT -> 3;
            case OUTER_RIGHT -> 4;
        };
        return state.with(CompositeRootBlock.VARIANT, index + (half(state) == BlockHalf.TOP ? 5 : 0));
    }

    public static BlockState normalize(BlockState state) {
        if (!(state.getBlock() instanceof ImportedStairBlock || state.getBlock() instanceof ImportedLadderBlock)) return state;
        return state.with(CompositeRootBlock.VARIANT, variant(state));
    }

    public static int poseVariant(BlockState state) {
        int result = variant(state);
        return state.getBlock() instanceof ImportedStairBlock && state.contains(ImportedStairBlock.TRIM) && state.get(ImportedStairBlock.TRIM) ? result + 10 : result;
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
        return state.with(CompositeRootBlock.VARIANT, Math.floorMod(variant, 10));
    }

    public static BlockState fromPose(BlockState state, int pose) {
        if (state.getBlock() instanceof ImportedLadderBlock)
            return ImportedLadderBlock.normalize(state.with(ImportedLadderBlock.HEIGHT, pose == 1 ? ImportedLadderBlock.Height.TWO : ImportedLadderBlock.Height.ONE));
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state.with(CompositeRootBlock.VARIANT, pose);
        state = fromVariant(state, pose);
        return state.contains(ImportedStairBlock.TRIM) ? state.with(ImportedStairBlock.TRIM, pose >= 10) : state;
    }
}
