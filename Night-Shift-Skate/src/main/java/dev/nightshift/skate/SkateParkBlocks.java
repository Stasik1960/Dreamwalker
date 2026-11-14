package dev.nightshift.skate;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public final class SkateParkBlocks {
    public static final Block PAVING = register("park_paving", new Block(
            FabricBlockSettings.copyOf(Blocks.POLISHED_DEEPSLATE).strength(2.6f, 6.0f)));
    public static final Block DECK = register("park_deck", new SlabBlock(
            FabricBlockSettings.copyOf(Blocks.DEEPSLATE_TILE_SLAB).strength(2.6f, 6.0f)));
    public static final SkateRampBlock LOW_RAMP = register("low_ramp", new SkateRampBlock(0.5,
            FabricBlockSettings.copyOf(Blocks.POLISHED_DEEPSLATE).nonOpaque().strength(2.6f, 6.0f)));
    public static final SkateRampBlock STEEP_RAMP = register("steep_ramp", new SkateRampBlock(1.0,
            FabricBlockSettings.copyOf(Blocks.POLISHED_DEEPSLATE).nonOpaque().strength(2.6f, 6.0f)));
    public static final Block RAIL = register("park_rail", new OrientedShapeBlock(
            railShape(Direction.Axis.X), railShape(Direction.Axis.Z),
            FabricBlockSettings.copyOf(Blocks.IRON_BARS).nonOpaque().strength(5.0f, 7.0f)));
    public static final Block LEDGE = register("park_ledge", new OrientedShapeBlock(
            ledgeShape(Direction.Axis.X), ledgeShape(Direction.Axis.Z),
            FabricBlockSettings.copyOf(Blocks.POLISHED_BLACKSTONE_BRICKS).nonOpaque().strength(2.8f, 6.0f)));

    private SkateParkBlocks() {}

    public static void register() {
        // Loading this class registers the blocks and their items before registries freeze.
    }

    private static <T extends Block> T register(String path, T block) {
        Registry.register(Registries.BLOCK, NightShiftSkate.id(path), block);
        Registry.register(Registries.ITEM, NightShiftSkate.id(path), new BlockItem(block, new Item.Settings()));
        return block;
    }

    private static VoxelShape railShape(Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            return VoxelShapes.union(
                    Block.createCuboidShape(0, 12, 6, 16, 15, 10),
                    Block.createCuboidShape(2, 0, 7, 4, 12, 9),
                    Block.createCuboidShape(12, 0, 7, 14, 12, 9));
        }
        return VoxelShapes.union(
                Block.createCuboidShape(6, 12, 0, 10, 15, 16),
                Block.createCuboidShape(7, 0, 2, 9, 12, 4),
                Block.createCuboidShape(7, 0, 12, 9, 12, 14));
    }

    private static VoxelShape ledgeShape(Direction.Axis axis) {
        return axis == Direction.Axis.X
                ? VoxelShapes.union(Block.createCuboidShape(0, 0, 3, 16, 8, 13),
                        Block.createCuboidShape(0, 8, 2, 16, 9, 14))
                : VoxelShapes.union(Block.createCuboidShape(3, 0, 0, 13, 8, 16),
                        Block.createCuboidShape(2, 8, 0, 14, 9, 16));
    }

    private static final class OrientedShapeBlock extends Block {
        private static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;
        private final VoxelShape xShape;
        private final VoxelShape zShape;

        private OrientedShapeBlock(VoxelShape xShape, VoxelShape zShape, Settings settings) {
            super(settings);
            this.xShape = xShape;
            this.zShape = zShape;
            setDefaultState(getStateManager().getDefaultState().with(AXIS, Direction.Axis.X));
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext context) {
            return getDefaultState().with(AXIS, context.getHorizontalPlayerFacing().getAxis());
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return state.get(AXIS) == Direction.Axis.X ? xShape : zShape;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return state.get(AXIS) == Direction.Axis.X ? xShape : zShape;
        }

        @Override
        public BlockState rotate(BlockState state, BlockRotation rotation) {
            if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
                return state.cycle(AXIS);
            }
            return state;
        }

        @Override
        public BlockState mirror(BlockState state, BlockMirror mirror) {
            return state;
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(AXIS);
        }
    }
}
