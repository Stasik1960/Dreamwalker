package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

/** Additional pose slots for imported wall families that exceed their original state budgets. */
public final class WallArtBanks {
    public static final int BRICK = 36;
    public static final int DEEPSLATE = 12;
    public static final int STONE_BRICK = 9;
    public static final String STONE_BRICK_PATH = "source_assembly_stone_brick_wall";
    private static final int SMALL_WALL = 2;
    private static final IntProperty SMALL_BANK = IntProperty.of("art_bank", 0, SMALL_WALL);
    private static final IntProperty STONE_BANK = IntProperty.of("art_bank", 0, STONE_BRICK);

    private WallArtBanks() {}

    public static boolean smallWall(String path) {
        return switch (path) {
            case "source_assembly_cobbled_deepslate_wall", "source_assembly_granite_wall",
                 "source_assembly_end_stone_brick_wall" -> true;
            default -> false;
        };
    }

    public static int variantBudget(String path) {
        return switch (path) {
            case "source_assembly_brick_wall" -> (BRICK + 1) * 16;
            case "source_assembly_deepslate_brick_wall" -> (DEEPSLATE + 1) * 16;
            case STONE_BRICK_PATH -> (STONE_BRICK + 1) * 16;
            default -> smallWall(path) ? (SMALL_WALL + 1) * 16 : 0;
        };
    }

    public static final class Small extends ImportedCompactBlock {
        public Small(CompositeSpec spec) {
            super(spec);
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            super.appendProperties(builder);
            builder.add(SMALL_BANK);
        }
    }

    public static final class StoneBrick extends ImportedCompactBlock {
        public StoneBrick(CompositeSpec spec) {
            super(spec);
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            super.appendProperties(builder);
            builder.add(STONE_BANK);
        }
    }
}
