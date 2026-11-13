package dev.dreamwalker.buildingqa;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

final class ConnectedFenceChecks {
    private static final String NS = "bloodborne_dw";
    private static final List<String> STYLES = List.of("93195", "93185_plain", "93185_ornate", "93185_double",
            "93185_post", "93164", "93181", "93214", "90993", "90910", "90969", "93166", "column");

    static void verify(ServerPlayerEntity player) throws ReflectiveOperationException {
        var world = player.getServerWorld();
        BlockPos start = new BlockPos(432, 275, -832);
        world.getChunk(start);
        player.changeGameMode(GameMode.CREATIVE);
        var catalogue = Class.forName("dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue");
        List<?> offered = (List<?>) catalogue.getMethod("canonicalItems").invoke(null);
        for (int i = 0; i < STYLES.size(); i++) {
            String style = STYLES.get(i);
            Identifier id = new Identifier(NS, "connected_fence_" + style);
            BlockPos pos = start.add(i % 4 * 5, 0, i / 4 * 5);
            place(player, pos, new ItemStack(Registries.ITEM.get(id)));
            var state = world.getBlockState(pos);
            require(Registries.BLOCK.getId(state.getBlock()).equals(id), "could not place " + id);
            require(offered.stream().anyMatch(value -> ((ItemStack) value).isOf(state.getBlock().asItem())), "absent from Creative " + id);
            var name = new ItemStack(state.getBlock()).getName().getString();
            require(name.contains(style.equals("column") ? "90853" : style.split("_")[0]), "missing numeric name for " + id + ": " + name);
            if (style.equals("column")) {
                column(player, pos);
            } else {
                Identifier outside = new Identifier("decorative_blocks", "oak_palisade");
                require(Registries.BLOCK.containsId(outside), "cross-mod QA fixture is unavailable");
                for (BlockState neighbor : List.of(Blocks.STONE.getDefaultState(), Blocks.OAK_FENCE.getDefaultState(),
                        Registries.BLOCK.get(new Identifier(NS, "dw_darkness")).getDefaultState(),
                        Registries.BLOCK.get(outside).getDefaultState())) {
                    world.setBlockState(pos.east(), neighbor, Block.NOTIFY_ALL);
                    require(world.getBlockState(pos).get(Properties.EAST), "missing east connection for " + id);
                    require(!world.getBlockState(pos).get(Properties.NORTH), "unwanted north connection for " + id);
                    world.removeBlock(pos.east(), false);
                    require(!world.getBlockState(pos).get(Properties.EAST), "stale east connection for " + id);
                }
                require(top(world, pos, ShapeContext.absent()) == 1 && top(world, pos, ShapeContext.of(player)) == 1.5,
                        "wrong block/player heights " + id);
            }
        }
        var oldOffered = offered.stream().map(value -> Registries.ITEM.getId(((ItemStack) value).getItem()).getPath())
                .filter(path -> path.startsWith("source_assembly_cyan_stained_glass_pane")).toList();
        require(oldOffered.isEmpty(), "old fence variants still in Creative " + oldOffered);
        var legacy = new ItemStack(Registries.ITEM.get(new Identifier(NS, "source_assembly_cyan_stained_glass_pane")));
        legacy.getOrCreateSubNbt("BlockStateTag").putString("variant", "9");
        BlockPos legacyPos = start.south(21);
        place(player, legacyPos, legacy);
        require(Registries.BLOCK.getId(world.getBlockState(legacyPos).getBlock()).getPath().equals("connected_fence_93185_plain"),
                "legacy fence view 10 did not place the straight fence");

        Vec3d initial = player.getPos();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            player.refreshPositionAndAngles(start.getX() + .5, start.getY() + 1.2, start.getZ() - .7, 0, 0);
            player.move(MovementType.SELF, new Vec3d(0, 0, 2));
            require(player.getZ() < start.getZ() + .5, "moving player passed above the one-block collision height");
        } finally {
            player.changeGameMode(GameMode.CREATIVE);
            player.refreshPositionAndAngles(initial.x, initial.y, initial.z, player.getYaw(), player.getPitch());
        }
    }

    private static void column(ServerPlayerEntity player, BlockPos base) {
        var world = player.getServerWorld();
        BlockState upper = world.getBlockState(base.up());
        require(upper.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER, "missing top of column");
        for (BlockPos pos : List.of(base, base.up())) {
            require(top(world, pos, ShapeContext.absent()) == 1 && top(world, pos, ShapeContext.of(player)) == 1,
                    "column collision must fill both vertical levels");
        }
        world.setBlockState(base.west(), Blocks.STONE.getDefaultState());
        require(!world.getBlockState(base).get(Properties.WEST), "column joined a non-fence");
        var fence = Registries.BLOCK.get(new Identifier(NS, "connected_fence_93195"));
        world.setBlockState(base.west(), fence.getDefaultState());
        require(world.getBlockState(base).get(Properties.WEST), "column did not join fence");
        require(!world.getBlockState(base.up()).get(Properties.WEST), "upper column joined a fence at another height");
        require(player.interactionManager.tryBreakBlock(base.up()), "cannot break column in creative");
        require(world.getBlockState(base).isAir(), "column lower half remained after removing upper half");
    }

    private static double top(ServerWorld world, BlockPos pos, ShapeContext context) {
        return world.getBlockState(pos).getCollisionShape(world, pos, context).getMax(Direction.Axis.Y);
    }

    private static void place(ServerPlayerEntity player, BlockPos pos, ItemStack stack) {
        var world = player.getServerWorld();
        world.getChunk(pos);
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        var hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, -.5, 0), Direction.UP, pos.down(), false);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        require(stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "player could not use " + stack);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
