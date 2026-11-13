package dev.dreamwalker.qa;

import java.util.List;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class BuildingNeighborsQa {
    private static final String WALL = "source_assembly_brick_wall";
    private static final String FENCE = "source_assembly_nether_brick_fence";
    private static final String PANE = "source_assembly_brown_stained_glass_pane";
    private static final String SLAB = "restored_slab_5ff0bf7f58a8b6d4";
    private static final BlockPos GATE = new BlockPos(446, 298, -836);
    private static final BlockPos WALL_TO_STONE = GATE.add(0, 0, 3);
    private static final BlockPos WALL_TO_DECOR = GATE.add(3, 0, 3);
    private static final BlockPos WALL_TO_THIRD_PARTY = GATE.add(6, 0, 3);
    private static final BlockPos WALL_REMOVAL = GATE.add(9, 0, 3);
    private static final BlockPos SIGN = GATE.add(0, 0, 6);
    private static final BlockPos SLAB_PLACE = GATE.add(4, 0, 6);

    private BuildingNeighborsQa() {}

    public static String start(ServerWorld world, ServerPlayerEntity player) throws ReflectiveOperationException {
        for (BlockPos pos : List.of(GATE, WALL_TO_STONE, WALL_TO_DECOR, WALL_TO_THIRD_PARTY,
                WALL_REMOVAL, SIGN, SLAB_PLACE, WALL_TO_THIRD_PARTY.east())) world.getChunk(pos);

        var pane = compact(PANE);
        var imported = Class.forName("dev.dreamwalker.bloodbornedw.composite.AutoStoneFence");
        require((boolean) imported.getMethod("matches", BlockState.class).invoke(null, pane),
                "pane fixture is outside the imported connecting family: " + pane);
        set(world, GATE, Blocks.COBBLESTONE_WALL.getDefaultState());
        set(world, GATE.east(), pane);
        require(world.getBlockState(GATE).get(WallBlock.EAST_SHAPE) != WallShape.NONE,
                "vanilla wall must connect to an imported pane");
        set(world, GATE.east(), compact(FENCE));
        require(world.getBlockState(GATE).get(WallBlock.EAST_SHAPE) == WallShape.NONE,
                "wall must not connect to a fence");
        set(world, GATE, Blocks.GLASS_PANE.getDefaultState());
        set(world, GATE.east(), compact(WALL));
        require(world.getBlockState(GATE).get(HorizontalConnectingBlock.EAST),
                "vanilla pane must connect to an imported wall");
        set(world, GATE, Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.NORTH));
        set(world, GATE.east(), Blocks.AIR.getDefaultState());
        set(world, GATE.east(), compact(WALL));
        require(world.getBlockState(GATE).get(FenceGateBlock.IN_WALL), "gate must lower beside an imported wall");

        set(world, WALL_TO_STONE, compact(WALL));
        set(world, WALL_TO_STONE.east(), Blocks.STONE.getDefaultState());
        set(world, WALL_TO_DECOR, compact(WALL));
        set(world, WALL_TO_DECOR.east(), imported(SLAB + "_full"));
        set(world, WALL_REMOVAL, compact(WALL));
        set(world, WALL_REMOVAL.east(), Blocks.STONE.getDefaultState());
        set(world, WALL_REMOVAL.east(), Blocks.AIR.getDefaultState());
        set(world, WALL_TO_THIRD_PARTY, compact(WALL));
        var thirdParty = Registries.BLOCK.getIds().stream()
                .filter(id -> id.getNamespace().equals("handcrafted") || id.getNamespace().equals("yuushya"))
                .sorted()
                .map(Registries.BLOCK::get)
                .map(Block::getDefaultState)
                .filter(state -> state.isSideSolidFullSquare(world, WALL_TO_THIRD_PARTY.east(), Direction.WEST)
                        && !Block.cannotConnect(state))
                .findFirst().orElseThrow(() -> new AssertionError("Missing solid Handcrafted or Yuushya test support"));
        set(world, WALL_TO_THIRD_PARTY.east(), thirdParty);

        set(world, SIGN, imported(SLAB + "_full"));
        var standing = Blocks.OAK_SIGN.getDefaultState();
        var sideSign = Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.EAST);
        require(standing.canPlaceAt(world, SIGN.up()), "DreamWalker top face must support standing sign");
        require(sideSign.canPlaceAt(world, SIGN.east()), "DreamWalker side face must support wall sign");
        require(place(player, new ItemStack(Items.OAK_SIGN), SIGN, Direction.UP).isAccepted(),
                "real sign item rejected DreamWalker support");
        require(world.getBlockState(SIGN.up()).isOf(Blocks.OAK_SIGN), "sign did not occupy cell above support");
        require(place(player, new ItemStack(Items.OAK_SIGN), SIGN, Direction.EAST).isAccepted(),
                "real wall sign item rejected DreamWalker support");
        require(world.getBlockState(SIGN.east()).isOf(Blocks.OAK_WALL_SIGN)
                && world.getBlockState(SIGN.east()).get(WallSignBlock.FACING) == Direction.EAST,
                "wall sign did not occupy the east-facing side");
        set(world, SIGN, imported(SLAB + "_half"));
        require(!(world.getBlockState(SIGN.up()).getBlock() instanceof AbstractSignBlock)
                && !(world.getBlockState(SIGN.east()).getBlock() instanceof AbstractSignBlock),
                "signs must detach when their support face is lost");

        return "PASS standing/wall signs and reciprocal gate/wall/pane";
    }

    public static String verifyAfterTicks(ServerWorld world) {
        require(reaches(world, GATE.east(), Direction.WEST), "imported wall did not connect back to vanilla gate");
        require(reaches(world, WALL_TO_STONE, Direction.EAST), "imported wall did not connect to vanilla stone");
        require(reaches(world, WALL_TO_DECOR, Direction.EAST), "imported wall did not connect to DreamWalker full form");
        require(reaches(world, WALL_TO_THIRD_PARTY, Direction.EAST),
                "imported wall did not connect to third-party full block");
        require(!reaches(world, WALL_REMOVAL, Direction.EAST), "imported wall arm did not retract after removal");
        return "PASS imported wall recalculation: vanilla gate, vanilla stone, mod full block, third-party full block, removal";
    }

    public static String verifyDistinctForms(ServerWorld world, ServerPlayerEntity player) {
        int modelForms = 0;
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (id.getNamespace().equals("bloodborne_dw") && id.getPath().matches(".+_form_[0-9]+")) modelForms++;
        }
        require(modelForms == 926, "expected 926 fixed model registry IDs, found " + modelForms);
        var registry = Registries.BLOCK.getIds();
        long slabForms = registry.stream().filter(id -> id.getNamespace().equals("bloodborne_dw")
                && id.getPath().matches("restored_slab_(?:(?:decor|overlay)_)?[0-9a-f]{16}_(half|upper|full)")).count();
        require(slabForms == 567, "expected 567 fixed slab registry IDs, found " + slabForms);
        var floor = SLAB_PLACE;
        set(world, floor, Blocks.STONE.getDefaultState());
        var stack = new ItemStack(imported(SLAB + "_upper").getBlock());
        require(place(player, stack, floor, Direction.UP).isAccepted(), "fixed upper slab failed placement");
        var placed = world.getBlockState(floor.up());
        require(placed.isOf(imported(SLAB + "_upper").getBlock()), "fixed upper slab registry was not preserved");
        var collision = placed.getCollisionShape(world, floor.up());
        require(!collision.isEmpty() && collision.getMin(Direction.Axis.Y) >= 0.49
                && collision.getMax(Direction.Axis.Y) >= 0.99, "fixed upper slab did not retain upper-half collision");
        return "PASS 926 fixed model and 567 slab IDs and fixed upper slab placement";
    }

    private static ActionResult place(ServerPlayerEntity player, ItemStack item, BlockPos support, Direction face) {
        var point = Vec3d.ofCenter(support).add(face.getOffsetX() * .5, face.getOffsetY() * .5, face.getOffsetZ() * .5);
        var hit = new BlockHitResult(point, face, support, false);
        return ((BlockItem) item.getItem()).place(new ItemPlacementContext(player, Hand.MAIN_HAND, item, hit));
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    private static boolean reaches(ServerWorld world, BlockPos pos, Direction side) {
        var shape = world.getBlockState(pos).getCollisionShape(world, pos);
        return !shape.isEmpty() && (side == Direction.WEST
                ? shape.getMin(Direction.Axis.X) <= .00001 : shape.getMax(Direction.Axis.X) >= .99999);
    }

    private static BlockState compact(String name) {
        var state = imported(name);
        for (var property : state.getProperties()) {
            if (property instanceof BooleanProperty value && value.getName().equals("compact")) return state.with(value, true);
        }
        throw new AssertionError("Missing compact property on " + name);
    }

    private static BlockState imported(String name) {
        var id = new Identifier("bloodborne_dw", name);
        require(Registries.BLOCK.containsId(id), "Missing test block " + id);
        return Registries.BLOCK.get(id).getDefaultState();
    }

    private static void require(boolean value, String explanation) {
        if (!value) throw new AssertionError(explanation);
    }
}
