package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock.Profile;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderItem;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;

/** Dedicated-server checks. These do not constitute visual or Creative UI acceptance. */
public final class PrototypeLadderGameTests implements FabricGameTest {
    private static final BlockPos ROOT = new BlockPos(3, 3, 3);
    private static final double EPSILON = 1e-6;
    private static final ChunkTicketType<ChunkPos> RELOAD_LEASE=ChunkTicketType.create("dreamwalker_ordinary_ladder_reload_test",Comparator.comparingLong(ChunkPos::toLong));

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100, batchId = "prototype_ladder")
    public void all192OrdinaryStatesKeepIndependentSemanticsThroughNeighborUpdates(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PrototypeLadderBlock block = ladder();
        Set<String> properties = block.getStateManager().getProperties().stream().map(property -> property.getName()).collect(Collectors.toSet());
        context.assertTrue(properties.equals(Set.of("facing", "diagonal", "waterlogged", "variant", "profile", "source_clone","freestanding")), "eight-pose orientation, water, art, independent section and explicit source installation role are exposed");
        context.assertTrue(block.getStateManager().getStates().size() == 384, "192 ordinary schema states and192 explicit source-clone schema states per registered ladder type");
        try {
            for (BlockState state : block.getStateManager().getStates().stream().filter(s -> !s.get(PrototypeLadderBlock.SOURCE_CLONE)).toList()) {
                clear(world, root); BlockPos backing = root.offset(state.get(PrototypeLadderBlock.FACING).getOpposite());
                backing(world,root,state,Blocks.STONE.getDefaultState());
                world.setBlockState(root, state, Block.NOTIFY_ALL);
                for (Direction direction : Direction.values()) for (BlockState neighbor : List.of(Blocks.AIR.getDefaultState(), Blocks.BEEHIVE.getDefaultState(), Blocks.OAK_DOOR.getDefaultState(), Blocks.STONE.getDefaultState())) {
                    BlockState result = block.getStateForNeighborUpdate(state, direction, neighbor, world, root, root.offset(direction));
                    context.assertTrue(result.equals(state), "a valid backing keeps the exact independent ladder state: " + state + " / " + direction + " / " + neighbor.getBlock());
                }
                for (Direction direction : Direction.values()) if (!PrototypeLadderBlock.backingDirections(state).contains(direction)) {
                    BlockPos neighborPos = root.offset(direction);
                    for (BlockState neighbor : List.of(Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.HONEY_LEVEL, 1), Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.HONEY_LEVEL, 2), Blocks.STONE.getDefaultState(), Blocks.AIR.getDefaultState())) {
                        world.setBlockState(neighborPos, neighbor, Block.NOTIFY_ALL);
                        context.assertTrue(world.getBlockState(root).equals(state), "real neighbor placement/removal cannot turn the ladder into a source cap or another honey family: " + state + " / " + direction);
                    }
                }
            }
            context.complete();
        } finally { clear(world, root); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140, batchId = "prototype_ladder")
    public void actualBlockItemPlacesAll96SupportedCombinations(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer(); moveOutside(context, player);
        int placements = 0;
        try {
            for (int yaw=0;yaw<8;yaw++) for (int variant = 0; variant < 3; variant++) for (Profile profile : Profile.values()) for (boolean wet : List.of(false, true)) {
                BlockState expected=PrototypeLadderBlock.withYaw(state(Direction.NORTH,variant,profile,wet),yaw);Direction facing=expected.get(PrototypeLadderBlock.FACING);
                clear(world, root); BlockPos wall = root.offset(facing.getOpposite());
                backing(world,root,expected,Blocks.DIAMOND_ORE.getDefaultState());player.setYaw(yaw*45F);
                if (wet) world.setBlockState(root, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
                ItemStack stack = art(variant, profile); stack.setCount(2);
                // Picked or hand-edited art NBT cannot force placement orientation or water state.
                stack.getOrCreateSubNbt("BlockStateTag").putString("facing", facing.getOpposite().asString());
                stack.getOrCreateSubNbt("BlockStateTag").putString("diagonal",Boolean.toString(!expected.get(PrototypeLadderBlock.DIAGONAL)));
                stack.getOrCreateSubNbt("BlockStateTag").putString("waterlogged", Boolean.toString(!wet));
                NbtCompound originalNbt = stack.getNbt().copy();
                ActionResult result = use(PrototypeArchitecture.LADDER_ITEM, world, player, stack, wall, facing);
                context.assertTrue(result.isAccepted() && world.getBlockState(root).equals(expected), "BlockItem placement respects wall, frozen art and local water: expected=" + expected + "; actual=" + world.getBlockState(root) + "; result=" + result);
                context.assertTrue(stack.getCount() == 1 && stack.getNbt().equals(originalNbt), "one successful placement consumes one item without mutating source art NBT");
                context.assertTrue(world.getBlockState(wall).isOf(Blocks.DIAMOND_ORE), "backing block is preserved");
                for (Direction direction : Direction.values()) context.assertTrue(!world.getBlockState(root.offset(direction)).isOf(ladder()), "one-cell ladder creates no implicit cap or helper at " + direction);
                placements++;
            }
            context.assertTrue(placements == 96, "all supported schema combinations were placed through the real BlockItem");
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60, batchId = "prototype_ladder")
    public void unsupportedAndOccupiedPlacementLeaveItemsAndForeignBlocksUntouched(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer(); moveOutside(context, player);
        try {
            clear(world, root);
            ItemStack stack = art(2, Profile.ALT); stack.setCount(3); NbtCompound beforeNbt = stack.getNbt().copy();
            ActionResult unsupported = use(PrototypeArchitecture.LADDER_ITEM, world, player, stack, root, Direction.NORTH);
            context.assertTrue(!unsupported.isAccepted() && world.getBlockState(root).isAir(), "free air without a solid side backing refuses ladder placement");
            context.assertTrue(stack.getCount() == 3 && beforeNbt.equals(stack.getNbt()), "unsupported placement preserves count and exact item NBT");
            BlockPos wall = root.south(); world.setBlockState(wall, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(root, Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            ActionResult occupied = use(PrototypeArchitecture.LADDER_ITEM, world, player, stack, wall, Direction.NORTH);
            context.assertTrue(!occupied.isAccepted() && world.getBlockState(root).isOf(Blocks.GOLD_BLOCK), "occupied destination refuses placement and preserves the foreign block");
            context.assertTrue(stack.getCount() == 3 && beforeNbt.equals(stack.getNbt()), "occupied placement preserves count and exact item NBT");
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 80, batchId = "prototype_ladder")
    public void builderRejectsWrongBackingThenRotatesWithoutChangingArt(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer(); moveOutside(context, player);
        player.getAbilities().creativeMode=true;
        try {
            clear(world, root); BlockState before = state(Direction.NORTH, 2, Profile.ALT, true);
            world.setBlockState(root.south(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL); world.setBlockState(root, before, Block.NOTIFY_ALL);
            ItemStack tool = new ItemStack(PrototypeArchitecture.BUILDER_TOOL); tool.getOrCreateNbt().putString("test_marker", "unchanged"); NbtCompound nbt = tool.getNbt().copy();
            ActionResult rejected = BuildingTool.applyBlock(player,root,BuildingTool.Action.ROTATE);
            context.assertTrue(!rejected.isAccepted() && world.getBlockState(root).equals(before), "45-degree rotation without both new touching backing faces leaves the exact old state");
            context.assertTrue(tool.getCount() == 1 && tool.getNbt().equals(nbt), "rejected builder action preserves its tool");
            for (Direction direction : Direction.Type.HORIZONTAL) world.setBlockState(root.offset(direction), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            BlockState current = before;
            for (int turn = 0; turn < 8; turn++) {
                BlockState expected = ladder().step45(current);
                context.assertTrue(BuildingTool.applyBlock(player,root,player.isSneaking()?BuildingTool.Action.PROFILE:BuildingTool.Action.ROTATE).isAccepted(), "supported builder rotation succeeds at turn " + turn);
                context.assertTrue(world.getBlockState(root).equals(expected), "rotation changes yaw alone and preserves variant, ALT and waterlogging");
                current = expected;
            }
            context.assertTrue(world.getBlockState(root).equals(before) && tool.getNbt().equals(nbt), "eight server LKM action-adapter rotations return the original state without modifying the item");
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100, batchId = "prototype_ladder")
    public void pickAndLootPreserveArtForEveryStateWithoutCopyingFacingOrWater(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer(); moveOutside(context, player);
        try {
            for (BlockState state : ladder().getStateManager().getStates().stream().filter(s -> !s.get(PrototypeLadderBlock.SOURCE_CLONE)).toList()) {
                clear(world, root); backing(world,root,state,Blocks.STONE.getDefaultState());
                world.setBlockState(root, state, Block.NOTIFY_ALL);
                assertArt(context, state, ladder().getPickStack(world, root, state), "pick");
                List<ItemStack> drops = Block.getDroppedStacks(state, world, root, null, player, ItemStack.EMPTY);
                context.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 1, "loot yields exactly one independent ladder item");
                assertArt(context, state, drops.get(0), "loot");
            }
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100, batchId = "prototype_ladder")
    public void removingBackingDropsExactlyOneArtPreservingLadder(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        try {
            for (int yaw=0;yaw<8;yaw++) for (int variant = 0; variant < 3; variant++) for (Profile profile : Profile.values()) {
                BlockState before=PrototypeLadderBlock.withYaw(state(Direction.NORTH,variant,profile,false),yaw);Direction facing=before.get(PrototypeLadderBlock.FACING);
                clear(world, root); BlockPos support = root.offset(facing.getOpposite());
                backing(world,root,before,Blocks.STONE.getDefaultState()); world.setBlockState(root, before, Block.NOTIFY_ALL);
                world.removeBlock(support, false);
                context.assertTrue(world.getBlockState(root).isAir(), "native unsupported ladder disappears when its backing is removed: " + before);
                List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class, new Box(root).expand(2), entity -> entity.getStack().getItem() instanceof PrototypeLadderItem);
                context.assertTrue(drops.size() == 1 && drops.get(0).getStack().getCount() == 1, "one support removal produces one item without duplication");
                assertArt(context, before, drops.get(0).getStack(), "support-removal drop");
            }
            context.complete();
        } finally { clear(world, root); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100, batchId = "prototype_ladder")
    public void climbableTagAndThinCollisionWorkForAllOrientations(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer();
        try {
            for (BlockState state : ladder().getStateManager().getStates().stream().filter(s -> !s.get(PrototypeLadderBlock.SOURCE_CLONE)).toList()) {
                clear(world, root); backing(world,root,state,Blocks.STONE.getDefaultState());
                world.setBlockState(root, state, Block.NOTIFY_ALL);
                context.assertTrue(state.isIn(BlockTags.CLIMBABLE), "the shipped data tag marks the new independent ladder climbable");
                VoxelShape collision = state.getCollisionShape(world, root); VoxelShape outline = state.getOutlineShape(world, root, ShapeContext.absent());
                boolean diagonal=state.get(PrototypeLadderBlock.DIAGONAL);
                VoxelShape playerCollision=state.getCollisionShape(world,root,ShapeContext.of(player));
                context.assertTrue(diagonal?playerCollision.isEmpty():!playerCollision.isEmpty(),"only diagonal player collision is disabled; independent selection and native absent-context shape remain");
                context.assertTrue(!collision.isEmpty() && !outline.isEmpty() && collision.getBoundingBoxes().size() == 1 && outline.getBoundingBoxes().size() == 1, "collision and independent pick shape each use exactly one cached simple box");
                Box bounds = collision.getBoundingBox(); Direction.Axis axis = state.get(PrototypeLadderBlock.FACING).getAxis();
                double depth = axis == Direction.Axis.X ? bounds.maxX - bounds.minX : bounds.maxZ - bounds.minZ;
                double occupiedArea=collision.getBoundingBoxes().stream().mapToDouble(box->(box.maxX-box.minX)*(box.maxZ-box.minZ)).sum();
                context.assertTrue(bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0 && bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1 && (diagonal?occupiedArea<.5:depth<.18) && Math.abs(bounds.maxY - bounds.minY - 1) < EPSILON, "cardinal collision stays thin; the deliberately coarse diagonal box occupies less than half a cell");
                if(diagonal)assertDiagonalPhysics(context,collision,outline,root);else assertPlaneBlocksMovement(context, collision, axis);
                player.refreshPositionAndAngles(root.getX() + .5, root.getY(), root.getZ() + .5, 0, 0);
                context.assertTrue(player.isClimbing(), "vanilla player climb detection recognizes the new block: " + state);
                moveOutside(context, player); context.assertTrue(!player.isClimbing(), "climb detection does not leak into unrelated cells");
            }
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100, batchId = "prototype_ladder")
    public void builderProfileTogglePreservesEveryPhysicalAndSemanticProperty(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer(); moveOutside(context, player); player.setSneaking(true);
        player.getAbilities().creativeMode=true;
        try {
            for (BlockState before : ladder().getStateManager().getStates().stream().filter(s -> !s.get(PrototypeLadderBlock.SOURCE_CLONE)).toList()) {
                clear(world, root); BlockPos support = root.offset(before.get(PrototypeLadderBlock.FACING).getOpposite());
                backing(world,root,before,Blocks.DIAMOND_BLOCK.getDefaultState()); world.setBlockState(root, before, Block.NOTIFY_ALL);
                Box collision = before.getCollisionShape(world, root).getBoundingBox(), outline = before.getOutlineShape(world, root).getBoundingBox();
                ItemStack tool = new ItemStack(PrototypeArchitecture.BUILDER_TOOL);
                context.assertTrue(BuildingTool.applyBlock(player,root,player.isSneaking()?BuildingTool.Action.PROFILE:BuildingTool.Action.ROTATE).isAccepted(), "sneaking builder profile toggle succeeds");
                BlockState after = world.getBlockState(root);
                context.assertTrue(after.equals(before.cycle(PrototypeLadderBlock.PROFILE)), "profile toggle changes exactly one property");
                context.assertTrue(collision.equals(after.getCollisionShape(world, root).getBoundingBox()) && outline.equals(after.getOutlineShape(world, root).getBoundingBox()), "BASE and declared ALT have identical physical and selection geometry");
                context.assertTrue(world.getBlockState(support).isOf(Blocks.DIAMOND_BLOCK) && tool.getCount() == 1, "profile toggle preserves backing and tool count");
            }
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60, batchId = "prototype_ladder")
    public void allThree384StateSchemasRoundTripThroughMinecraftPaletteNbt(TestContext context) {
        ServerWorld world = context.getWorld();
        int decoded = 0;
        for (int art=0;art<3;art++)for (BlockState state : PrototypeArchitecture.ladderBlock(art).getStateManager().getStates()) {
            NbtCompound saved = NbtHelper.fromBlockState(state);
            context.assertTrue(saved.getString("Name").equals(net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString()), "save palette uses each independent artistic registered block ID");
            BlockState loaded = NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK), saved.copy());
            context.assertTrue(loaded.equals(state), "Minecraft NBT state codec preserves exact orientation, waterlogging, frozen variant and declared profile: " + state);
            decoded++;
        }
        context.assertTrue(decoded == 1152, "all three384-state save palettes roundtripped without losing installation or independent-section fields"); context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 80, batchId = "prototype_ladder")
    public void newMainItemDeterministicallySelectsArtZeroAndInvalidNbtIsNormalized(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer(); moveOutside(context, player);
        try {
            for (boolean invalid : List.of(false, true)) for (Direction facing : Direction.Type.HORIZONTAL) {
                clear(world, root); BlockPos wall = root.offset(facing.getOpposite()); world.setBlockState(wall, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                ItemStack stack = new ItemStack(PrototypeArchitecture.LADDER_ITEM, 2);
                if (invalid) { stack.getOrCreateSubNbt("BlockStateTag").putString("variant", "1000"); stack.getOrCreateSubNbt("BlockStateTag").putString("profile", "unreviewed"); }
                NbtCompound original = stack.getNbt() == null ? null : stack.getNbt().copy();
                context.assertTrue(use(PrototypeArchitecture.LADDER_ITEM, world, player, stack, wall, facing).isAccepted(), "unselected or malformed art item still obtains a valid new placement");
                BlockState frozen = world.getBlockState(root);
                context.assertTrue(frozen.isOf(PrototypeArchitecture.ladderBlock(0))&&PrototypeLadderBlock.artVariant(frozen)==0&&frozen.get(PrototypeLadderBlock.PROFILE) == Profile.BASE, "new primary item is deterministic art0 and defaults invalid profile to BASE");
                for (Direction direction : Direction.values()) context.assertTrue(ladder().getStateForNeighborUpdate(frozen, direction, Blocks.AIR.getDefaultState(), world, root, root.offset(direction)).equals(frozen), "neighbor changes cannot reroll newly selected art");
                assertArt(context, frozen, ladder().getPickStack(world, root, frozen), "newly selected art pick");
                context.assertTrue(stack.getCount() == 1 && (original == null ? stack.getNbt() == null : original.equals(stack.getNbt())), "placement freezes the world state without rewriting the remaining unselected source items");
            }
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_ladder")
    public void allEightPosesUseRealPartialVerticalFacesAndPreserveAdjacentDecor(TestContext context){
        ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();moveOutside(context,player);
        try{
            for(int yaw=0;yaw<8;yaw++){
                clear(world,root);BlockState expected=PrototypeLadderBlock.withYaw(state(Direction.NORTH,1,Profile.ALT,false),yaw);player.setYaw(yaw*45F);
                for(Direction direction:PrototypeLadderBlock.backingDirections(expected)){
                    BlockState stair=Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING,direction.getOpposite());
                    world.setBlockState(root.offset(direction),stair,Block.NOTIFY_ALL);
                    context.assertTrue(!stair.isFullCube(world,root.offset(direction)),"test uses a real non-full-cube stair support");
                }
                context.assertTrue(expected.canPlaceAt(world,root),"actual suitable stair back face supports yaw "+yaw);
                Direction decor=expected.get(PrototypeLadderBlock.FACING);world.setBlockState(root.offset(decor),Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                ItemStack item=art(1,Profile.ALT);BlockPos clicked=root.offset(expected.get(PrototypeLadderBlock.FACING).getOpposite());
                context.assertTrue(use(PrototypeArchitecture.LADDER_ITEM,world,player,item,clicked,expected.get(PrototypeLadderBlock.FACING)).isAccepted()&&world.getBlockState(root).equals(expected),"real eight-yaw placement accepts suitable partial supports and adjacent decor");
                context.assertTrue(world.getBlockState(root.offset(decor)).isOf(Blocks.GOLD_BLOCK),"neighbor decor is preserved without bounding-box reservations");
                if((yaw&1)!=0){Direction second=PrototypeLadderBlock.backingDirections(expected).get(1);world.setBlockState(root.offset(second),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(world.getBlockState(root).isAir(),"removing either diagonal backing removes the unsupported section");}
                for(SlabType half:List.of(SlabType.TOP,SlabType.BOTTOM)){
                    clear(world,root);backing(world,root,expected,Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,half));
                    context.assertTrue(expected.canPlaceAt(world,root),"a real centered attachment pad on the "+half+" slab face supports an ordinary section");
                    ItemStack slabItem=art(1,Profile.ALT);
                    context.assertTrue(use(PrototypeArchitecture.LADDER_ITEM,world,player,slabItem,clicked,expected.get(PrototypeLadderBlock.FACING)).isAccepted()&&world.getBlockState(root).equals(expected),"actual item mounts on partial slab support at yaw "+yaw+" / "+half);
                }
            }context.complete();
        }finally{clear(world,root);player.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_ladder")
    public void flatWallClickWorksFromDiagonalViewForCreativeAndPickedItemsWithoutTechnicalState(TestContext context){
        ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();moveOutside(context,player);
        try{
            for(boolean creative:List.of(false,true))for(Direction facing:Direction.Type.HORIZONTAL){
                clear(world,root);BlockPos wall=root.offset(facing.getOpposite());world.setBlockState(wall,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                player.getAbilities().creativeMode=creative;player.setYaw((PrototypeLadderBlock.yaw(state(facing,0,Profile.BASE,false))+1)*45F);
                ItemStack item=art(2,Profile.ALT);item.setCount(2);NbtCompound before=item.getNbt().copy();
                context.assertTrue(use(PrototypeArchitecture.LADDER_ITEM,world,player,item,wall,facing).isAccepted(),"ordinary item works while looking diagonally at a single flat wall in creative="+creative+" / "+facing);
                BlockState placed=world.getBlockState(root);context.assertTrue(placed.equals(state(facing,2,Profile.ALT,false))&&!placed.get(PrototypeLadderBlock.SOURCE_CLONE)&&ordinaryOwner(world,root),"wall click chooses a working cardinal mount with art alone, fresh persistent owner without hidden source role");
                context.assertTrue(world.getBlockState(wall).isOf(Blocks.STONE)&&before.equals(item.getNbt()),"creative/ordinary placement keeps the foreign wall and source item NBT");
                ItemStack picked=ladder().getPickStack(world,root,placed);assertArt(context,placed,picked,"middle pick ordinary");world.setBlockState(root,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                context.assertTrue(use(PrototypeArchitecture.LADDER_ITEM,world,player,picked,wall,facing).isAccepted()&&world.getBlockState(root).equals(placed),"middle-picked item immediately places the same ordinary art with no helper");
            }
            clear(world,root);world.setBlockState(root.south(),Blocks.OAK_FENCE.getDefaultState(),Block.NOTIFY_ALL);ItemStack denied=art(1,Profile.ALT);denied.setCount(2);NbtCompound saved=denied.getNbt().copy();
            context.assertTrue(!use(PrototypeArchitecture.LADDER_ITEM,world,player,denied,root.south(),Direction.NORTH).isAccepted()&&world.getBlockState(root).isAir(),"a central fence post without contact at the cell boundary is a genuinely unsuitable side attachment");
            context.assertTrue(denied.getCount()==2&&saved.equals(denied.getNbt()),"invalid contact does not consume an item or rewrite NBT");
            world.setBlockState(root.south(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);player.getAbilities().allowModifyWorld=false;
            context.assertTrue(!use(PrototypeArchitecture.LADDER_ITEM,world,player,denied,root.south(),Direction.NORTH).isAccepted()&&world.getBlockState(root).isAir()&&denied.getCount()==2&&saved.equals(denied.getNbt()),"denied ordinary placement leaves exact world and item state");
            context.complete();
        }finally{player.getAbilities().allowModifyWorld=true;clear(world,root);player.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_ladder")
    public void joinedOrdinarySectionsActuallyClimbAndMiddleRemovalPreservesIndependentNeighbors(TestContext context){
        ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();moveOutside(context,player);
        try{
            clear(world,root);world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            for(int y=0;y<3;y++){
                BlockPos section=root.up(y);world.setBlockState(section,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(section.south(),Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                context.assertTrue(use(PrototypeArchitecture.LADDER_ITEM,world,player,art(y,Profile.ALT),section.south(),Direction.NORTH).isAccepted(),"each joined section is placed with its real ordinary item");
            }
            BlockState bottom=world.getBlockState(root),middle=world.getBlockState(root.up()),top=world.getBlockState(root.up(2));
            player.refreshPositionAndAngles(root.getX()+.5,root.getY()+.01,root.getZ()+.48,0,0);player.setHeadYaw(0);player.setVelocity(Vec3d.ZERO);player.setOnGround(false);
            double startY=player.getY();int climbingSteps=0;
            for(int step=0;step<30;step++){
                if(player.isClimbing())climbingSteps++;
                player.travel(new Vec3d(0,0,1));
            }
            context.assertTrue(climbingSteps>=15&&player.getY()-startY>1.25,"actual native player.travel climbs across joined ladder cells against their wall; steps="+climbingSteps+", deltaY="+(player.getY()-startY)+", pos="+player.getPos());
            moveOutside(context,player);context.assertTrue(world.breakBlock(root.up(),true,player),"native break removes the chosen middle section");
            context.assertTrue(world.getBlockState(root).equals(bottom)&&world.getBlockState(root.up(2)).equals(top)&&world.getBlockState(root.up()).isAir(),"joined vertical sections remain independently supported objects after middle removal");
            List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(3),entity->entity.getStack().getItem() instanceof PrototypeLadderItem);
            context.assertTrue(drops.size()==1&&drops.get(0).getStack().getCount()==1,"one native middle break yields one item");assertArt(context,middle,drops.get(0).getStack(),"joined middle drop");
            for(int y=0;y<3;y++)context.assertTrue(world.getBlockState(root.up(y).south()).isOf(Blocks.DIAMOND_BLOCK)&&(y==1?world.getBlockEntity(root.up(y))==null:ordinaryOwner(world,root.up(y))),"ordinary join/removal never owns or rewrites the three foreign support blocks");
            context.complete();
        }finally{for(int y=0;y<3;y++){world.setBlockState(root.up(y),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root.up(y).south(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);}clear(world,root);player.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=400,batchId="ordinary_ladder_reload")
    public void ordinaryItemPlacementSurvivesActualChunkSaveUnloadReloadWithoutSourceOwnerState(TestContext context){
        ServerWorld world=context.getWorld();BlockPos origin=context.getAbsolutePos(ROOT);ChunkPos chunk=new ChunkPos((origin.getX()>>4)+768,(origin.getZ()>>4)+768);BlockPos root=new BlockPos(chunk.x*16+8,80,chunk.z*16+8);world.getChunk(chunk.x,chunk.z);
        PlayerEntity player=context.createMockSurvivalPlayer();moveOutside(context,player);world.setBlockState(root,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root.south(),Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.TOP),Block.NOTIFY_ALL);
        context.assertTrue(use(PrototypeArchitecture.LADDER_ITEM,world,player,art(2,Profile.ALT),root.south(),Direction.NORTH).isAccepted(),"place a genuine ordinary item on partial support before saving");player.discard();BlockState expected=world.getBlockState(root),support=world.getBlockState(root.south());NbtCompound saved=NbtHelper.fromBlockState(expected),savedOwner=world.getBlockEntity(root).createNbt();
        world.getChunkManager().save(true);for(int x=chunk.x-1;x<=chunk.x+1;x++)for(int z=chunk.z-1;z<=chunk.z+1;z++){ChunkPos p=new ChunkPos(x,z);world.getChunkManager().removeTicket(ChunkTicketType.UNKNOWN,p,0,p);}
        int[] phase={0};long[] since={0};context.runAtEveryTick(()->{
            if(phase[0]==0){if(world.getChunkManager().getWorldChunk(chunk.x,chunk.z)!=null)return;world.getChunk(chunk.x,chunk.z);world.getChunkManager().addTicket(RELOAD_LEASE,chunk,0,chunk);phase[0]=1;since[0]=context.getTick();return;}
            if(phase[0]==1&&context.getTick()-since[0]>=2){context.assertTrue(world.getBlockState(root).equals(expected)&&NbtHelper.fromBlockState(world.getBlockState(root)).equals(saved)&&ordinaryOwner(world,root)&&world.getBlockEntity(root).createNbt().equals(savedOwner),"actual disk reload retains ordinary variant/profile/water/pose and no source-clone BE");context.assertTrue(world.getBlockState(root.south()).equals(support)&&expected.canPlaceAt(world,root),"actual disk reload preserves its real partial support and a valid mount");assertArt(context,expected,ladder().getPickStack(world,root,expected),"reloaded ordinary pick");world.setBlockState(root,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root.south(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.getChunkManager().removeTicket(RELOAD_LEASE,chunk,0,chunk);phase[0]=2;context.complete();}
        });
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_ladder")
    public void realCornerCameraPlacementHasPlayerOnlyEmptyPhysicsAndKeepsSelectionClimbing(TestContext context){
        ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();moveOutside(context,player);
        var armor=net.minecraft.entity.EntityType.ARMOR_STAND.create(world);
        try{
            for(int yaw:List.of(1,3,5,7))for(int art=0;art<3;art++){
                clear(world,root);BlockState expected=PrototypeLadderBlock.withYaw(state(Direction.NORTH,art,Profile.ALT,false),yaw);backing(world,root,expected,Blocks.STONE.getDefaultState());
                Direction backing=PrototypeLadderBlock.backingDirections(expected).get(0);player.setYaw(yaw*45F);player.setHeadYaw(player.getYaw());ItemStack item=PrototypeArchitecture.ladderItem(art).getDefaultStack();item.getOrCreateSubNbt("BlockStateTag").putString("profile","alt");
                context.assertTrue(use(item.getItem(),world,player,item,root.offset(backing),backing.getOpposite()).isAccepted()&&world.getBlockState(root).equals(expected),"real two-wall corner follows the diagonal camera without opposite180 fallback:"+yaw+" / art"+art);
                context.assertTrue(expected.getCollisionShape(world,root,ShapeContext.of(player)).isEmpty()&&!expected.getCollisionShape(world,root,ShapeContext.of(armor)).isEmpty(),"only player is passable; nonplayer/placement physics remains a real cached box");
                context.assertTrue(!expected.getOutlineShape(world,root,ShapeContext.of(player)).isEmpty()&&expected.isIn(BlockTags.CLIMBABLE),"selection and climb declaration survive empty player collision");
                player.setPosition(root.getX()+.5,root.getY()+.01,root.getZ()+.5);player.setVelocity(Vec3d.ZERO);context.assertTrue(player.isClimbing(),"real player recognizes the corner climb cell");Vec3d start=player.getPos();Direction away=backing.getOpposite();Vec3d delta=Vec3d.of(away.getVector()).multiply(1.05);player.move(net.minecraft.entity.MovementType.SELF,delta);
                context.assertTrue(player.getPos().subtract(start).squaredDistanceTo(delta)<1e-8,"player actually crosses diagonal authored volume into the free front cell, with native walls retained");
                ItemStack picked=expected.getBlock().getPickStack(world,root,expected);assertArt(context,expected,picked,"corner picked independent ID");moveOutside(context,player);
            }context.complete();
        }finally{clear(world,root);player.discard();armor.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=140,batchId="prototype_ladder")
    public void ordinaryTopSurfaceStacksClimbBothWaysAndRemainIndependentAfterMiddleAndFoundationRemoval(TestContext context){
        ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();moveOutside(context,player);
        try{
            clear(world,root);world.setBlockState(root.down(),Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM),Block.NOTIFY_ALL);ItemStack unsupported=art(0,Profile.ALT);
            context.assertTrue(!use(unsupported.getItem(),world,player,unsupported,root.down(),Direction.UP).isAccepted()&&world.getBlockState(root).isAir()&&unsupported.getCount()==1,"unsupported fractional foundation is explicitly refused; no floating new section or consumed item");
            clear(world,root);world.setBlockState(root.down(),Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.TOP),Block.NOTIFY_ALL);player.setYaw(0);player.setHeadYaw(0);
            for(int art=0;art<3;art++){BlockPos section=root.up(art);ItemStack item=art(art,Profile.ALT);context.assertTrue(use(item.getItem(),world,player,item,section.down(),Direction.UP).isAccepted(),"ordinary upper-face click installs a distinct artistic section atop surface/previoussection");BlockState actual=world.getBlockState(section);context.assertTrue(actual.get(PrototypeLadderBlock.FREESTANDING)&&!actual.get(PrototypeLadderBlock.SOURCE_CLONE)&&ordinaryOwner(world,section)&&actual.isOf(PrototypeArchitecture.ladderBlock(art)),"new top-standing section is the requested ordinary art type with a new native UUID without source/backing role");}
            BlockState bottom=world.getBlockState(root),middle=world.getBlockState(root.up()),top=world.getBlockState(root.up(2));
            player.refreshPositionAndAngles(root.getX()+.5,root.getY()+.01,root.getZ()+.5,0,0);player.setHeadYaw(0);player.setVelocity(Vec3d.ZERO);player.setOnGround(false);double startY=player.getY();int climbing=0;
            for(int step=0;step<24;step++){if(player.isClimbing())climbing++;player.travel(new Vec3d(0,0,1));}
            context.assertTrue(climbing>=12&&player.getY()-startY>1.25,"real player.travel climbs a top-standing stack without an external side wall; steps="+climbing+" delta="+(player.getY()-startY));
            double upperY=player.getY();player.setVelocity(0,-.25,0);for(int step=0;step<20;step++)player.travel(Vec3d.ZERO);context.assertTrue(upperY-player.getY()>.5&&player.getY()>=root.getY()-EPSILON,"ordinary controlled fall descends the stack and respects its real foundation");
            moveOutside(context,player);context.assertTrue(world.breakBlock(root.up(),true,player),"native middle break succeeds");context.assertTrue(world.getBlockState(root).equals(bottom)&&world.getBlockState(root.up(2)).equals(top)&&world.getBlockState(root.up()).isAir(),"only chosen section is removed; upper/lower independent sections survive");
            var drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(3),entity->entity.getStack().getItem() instanceof PrototypeLadderItem);context.assertTrue(drops.size()==1,"native middle break yields one canonical art item");assertArt(context,middle,drops.get(0).getStack(),"standing middle drop");
            world.setBlockState(root.down(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(world.getBlockState(root).equals(bottom)&&world.getBlockState(root.up(2)).equals(top),"removing clicked foundation does not cascade through independent standing sections");
            NbtCompound palette=NbtHelper.fromBlockState(top);context.assertTrue(NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),palette).equals(top),"standing support role survives real native palette codec");
            context.complete();
        }finally{for(int y=0;y<3;y++)world.setBlockState(root.up(y),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);clear(world,root);player.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_ladder")
    public void creativePickDropExposeDistinctTemporaryArtIdsAndOldToolVariantCannotChangeType(TestContext context){
        ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();moveOutside(context,player);player.getAbilities().creativeMode=true;
        try{
            for(int art=0;art<3;art++){clear(world,root);BlockState expected=state(Direction.NORTH,art,Profile.ALT,false);world.setBlockState(root.south(),Blocks.STONE.getDefaultState());ItemStack creative=PrototypeArchitecture.ladderItem(art).getDefaultStack();creative.getOrCreateSubNbt("BlockStateTag").putString("profile","alt");context.assertTrue(use(creative.getItem(),world,player,creative,root.south(),Direction.NORTH).isAccepted()&&world.getBlockState(root).equals(expected),"each real Creative primary item deterministically installs its own art ID");
                ItemStack picked=expected.getBlock().getPickStack(world,root,expected);assertArt(context,expected,picked,"Creative middle pick");String number=List.of("90006","90018","90019").get(art);context.assertTrue(dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(picked).temporaryId().equals(number)&&picked.getName().getString().contains(number)&&dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(expected).temporaryId().equals(number),"held/picked and placed debug type all display distinct expected TEMP"+number);
                ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);tool.getOrCreateNbt().putInt("BuilderAction",1);context.assertTrue(!BuildingTool.applyBlock(player,root,BuildingTool.Action.VARIANT).isAccepted()&&world.getBlockState(root).equals(expected),"legacy VARIANT ordinal1 is retained but cannot change independent artistic type");
                world.breakBlock(root,true,player);var drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(2),entity->entity.getStack().getItem() instanceof PrototypeLadderItem);context.assertTrue(drops.size()==1,"one native break has one canonical artistic drop");assertArt(context,expected,drops.get(0).getStack(),"distinct ID drop");
            }context.complete();
        }finally{clear(world,root);player.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_ladder")
    public void cheapShapeCountsAndReusedMovementQueryCostsAreMeasuredAgainstPriorDiagonalStrips(TestContext context){
        ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);
        try{
            Class<?> oldBuilder=Class.forName("dev.dreamwalker.bloodbornedw.architecture.RotatedSectionShape");var rotate=oldBuilder.getDeclaredMethod("rotate",dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box.class,int.class);rotate.setAccessible(true);
            long coldStart=System.nanoTime();VoxelShape oldShape=(VoxelShape)rotate.invoke(null,new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(0,0,13.19785/16,1,1,1),1);long oldCold=System.nanoTime()-coldStart;
            VoxelShape newShape=PrototypeLadderBlock.withYaw(ladder().getDefaultState(),1).getCollisionShape(world,root);
            context.assertTrue(oldShape.getBoundingBoxes().size()>1&&newShape.getBoundingBoxes().size()==1,"prior diagonal strip shape becomes one cached coarse rectangle");
            for(BlockState state:ladder().getStateManager().getStates())context.assertTrue(state.getCollisionShape(world,root).getBoundingBoxes().size()==1&&state.getOutlineShape(world,root).getBoundingBoxes().size()==1,"every one of384 schema states retains single-box absent-context physics and selection");
            long[] oldTimes=new long[5],newTimes=new long[5];double[] sink={0};for(int warm=0;warm<2;warm++){queryCost(oldShape,10000,sink);queryCost(newShape,10000,sink);}for(int round=0;round<5;round++){if((round&1)==0){oldTimes[round]=queryCost(oldShape,25000,sink);newTimes[round]=queryCost(newShape,25000,sink);}else{newTimes[round]=queryCost(newShape,25000,sink);oldTimes[round]=queryCost(oldShape,25000,sink);}}
            context.assertTrue(Double.isFinite(sink[0]),"native movement queries produce finite offsets");
            System.out.println("DW_LADDER_V9_PHYSICS_BENCHMARK={\"schema\":\"ladder-native-query-v9\",\"oldDiagonalBoxes\":"+oldShape.getBoundingBoxes().size()+",\"newDiagonalAbsentContextBoxes\":1,\"newDiagonalPlayerBoxes\":0,\"newCardinalBoxes\":1,\"newOutlineBoxes\":1,\"ordinaryHelperCells\":0,\"sourceHelperCells\":1,\"registeredStatesPerType\":384,\"registeredTypes\":3,\"iterationsPerRound\":25000,\"warmupRounds\":2,\"oldColdShapeNs\":"+oldCold+",\"oldQueryNs\":"+Arrays.toString(oldTimes)+",\"newQueryNs\":"+Arrays.toString(newTimes)+",\"sink\":"+sink[0]+",\"sameProcessAndInputs\":true,\"wholeCityPerformanceClaim\":false}");
            context.complete();
        }catch(ReflectiveOperationException failure){throw new AssertionError("Cannot construct the historical diagonal shape for the bounded comparison",failure);}
    }
    private static long queryCost(VoxelShape shape,int iterations,double[] sink){List<VoxelShape> shapes=List.of(shape);Box mover=new Box(.02,.2,-.4,.62,1.8,-.05);long start=System.nanoTime();for(int i=0;i<iterations;i++)sink[0]+=VoxelShapes.calculateMaxOffset(Direction.Axis.Z,mover,shapes,(i&1)==0?1.25:.75);return System.nanoTime()-start;}
    private static void backing(ServerWorld world,BlockPos root,BlockState state,BlockState support){for(Direction direction:PrototypeLadderBlock.backingDirections(state))world.setBlockState(root.offset(direction),support,Block.NOTIFY_ALL);}
    private static void assertDiagonalPhysics(TestContext context,VoxelShape collision,VoxelShape outline,BlockPos root){
        Box occupied=collision.getBoundingBoxes().stream().filter(box->box.maxX-box.minX>.005&&box.maxZ-box.minZ>.005).findFirst().orElseThrow();
        double x=(occupied.minX+occupied.maxX)/2;
        context.assertTrue(outline.raycast(new Vec3d(root.getX()+x,root.getY()+.5,root.getZ()-1),new Vec3d(root.getX()+x,root.getY()+.5,root.getZ()+2),root)!=null,"diagonal source section remains ray selectable");
        Box mover=new Box(occupied.minX+.001,.2,occupied.minZ-.12,occupied.maxX-.001,.8,occupied.minZ-.01);
        context.assertTrue(VoxelShapes.calculateMaxOffset(Direction.Axis.Z,mover,List.of(collision),1)<1,"diagonal section stops actual movement through an occupied strip");
        boolean gap=false;
        for(double cx:List.of(.04,.96))for(double cz:List.of(.04,.96)){
            Box corner=new Box(cx-.03,.2,cz-.03,cx+.03,.8,cz+.03);
            if(!VoxelShapes.matchesAnywhere(collision,VoxelShapes.cuboid(corner),BooleanBiFunction.AND)){
                Vec3d start=new Vec3d(root.getX()+cx-.02,root.getY()+.4,root.getZ()+cz-.02),end=start.add(.04,0,.04);
                context.assertTrue(outline.raycast(start,end,root)==null,"an empty corner does not select the source section");gap=true;
            }
        }
        context.assertTrue(gap,"diagonal physics keeps at least one real empty cell corner");
    }
    private static PrototypeLadderBlock ladder() {
        if (PrototypeArchitecture.LADDER == null || PrototypeArchitecture.LADDER_ITEM == null || PrototypeArchitecture.BUILDER_TOOL == null) throw new AssertionError("prototype architecture registration did not run");
        return PrototypeArchitecture.LADDER;
    }
    private static BlockState state(Direction facing, int variant, Profile profile, boolean wet) {
        return PrototypeArchitecture.ladderBlock(variant).getDefaultState().with(PrototypeLadderBlock.FACING, facing).with(PrototypeLadderBlock.VARIANT, variant).with(PrototypeLadderBlock.PROFILE, profile).with(PrototypeLadderBlock.WATERLOGGED, wet);
    }
    private static ItemStack art(int variant, Profile profile) { return ladder().artisticStack(state(Direction.NORTH, variant, profile, false)); }
    private static ActionResult use(net.minecraft.item.Item item, ServerWorld world, PlayerEntity player, ItemStack stack, BlockPos clicked, Direction side) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return (item instanceof PrototypeLadderItem?stack.getItem():item).useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(clicked).add(Vec3d.of(side.getVector()).multiply(.5)), side, clicked, false)));
    }
    private static void assertArt(TestContext context, BlockState expected, ItemStack stack, String reason) {
        context.assertTrue(stack.isOf(PrototypeArchitecture.ladderItem(PrototypeLadderBlock.artVariant(expected))) && PrototypeLadderItem.variant(stack) == PrototypeLadderBlock.artVariant(expected) && PrototypeLadderItem.profile(stack) == expected.get(PrototypeLadderBlock.PROFILE), reason + " retains independent art ID and declared profile");
        NbtCompound tag = stack.getSubNbt("BlockStateTag");
        context.assertTrue(tag != null && tag.getKeys().equals(Set.of("variant", "profile")), reason + " carries artistic state without forcing a later facing or water state");
        context.assertTrue(tag.contains("variant", 8) && tag.getString("variant").equals(Integer.toString(PrototypeLadderBlock.artVariant(expected)))
                && tag.contains("profile", 8) && tag.getString("profile").equals(expected.get(PrototypeLadderBlock.PROFILE).asString()), reason + " stores exact valid NBT strings, including default variant 0 and BASE");
    }
    private static void assertPlaneBlocksMovement(TestContext context, VoxelShape shape, Direction.Axis axis) {
        Box bounds = shape.getBoundingBox(); boolean high = (axis == Direction.Axis.X ? bounds.minX : bounds.minZ) > .5;
        Box mover = axis == Direction.Axis.X
                ? new Box(high ? .2 : bounds.maxX + .01, .2, .25, high ? bounds.minX - .01 : .8, 1.6, .75)
                : new Box(.25, .2, high ? .2 : bounds.maxZ + .01, .75, 1.6, high ? bounds.minZ - .01 : .8);
        double requested = high ? 1 : -1;
        double allowed = VoxelShapes.calculateMaxOffset(axis, mover, List.of(shape), requested);
        context.assertTrue(Math.abs(allowed) < Math.abs(requested), "the thin authored collision stops lateral motion");
    }
    private static void moveOutside(TestContext context, PlayerEntity player) {
        BlockPos outside = context.getAbsolutePos(new BlockPos(-4, 5, -4));
        player.refreshPositionAndAngles(outside.getX() + .5, outside.getY(), outside.getZ() + .5, 0, 0);
    }
    private static void clear(ServerWorld world, BlockPos root) {
        // removeBlock intentionally retains fluid; tests need explicit AIR between wet and dry fixtures.
        world.setBlockState(root, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        for (BlockPos pos : BlockPos.iterate(root.add(-2, -1, -2), root.add(2, 1, 2))) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(root).expand(3), entity -> true)) item.discard();
    }
    private static boolean ordinaryOwner(ServerWorld world,BlockPos pos){return world.getBlockEntity(pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity own&&!(own instanceof dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity)&&own.resident()!=null&&own.payload().isEmpty();}

}
