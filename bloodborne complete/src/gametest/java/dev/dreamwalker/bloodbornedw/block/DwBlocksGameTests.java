package dev.dreamwalker.bloodbornedw.block;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Regression coverage for native carrier states; runs entirely on the dedicated GameTest server. */
public final class DwBlocksGameTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "dw_blocks", tickLimit = 40)
    public void carrierStatesKeepNativeShapeAndVisualIsIndependent(TestContext context) {
        DwBlocks.Entry stairs = first(StairsBlock.class);
        if (stairs != null) {
            BlockState vanilla = stairs.sourceBlock().getDefaultState().with(StairsBlock.FACING, net.minecraft.util.math.Direction.EAST).with(StairsBlock.HALF, BlockHalf.TOP);
            BlockState carrier = DwBlocks.carrierState(vanilla);
            BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
            context.assertTrue(carrier.get(StairsBlock.FACING) == vanilla.get(StairsBlock.FACING) && carrier.get(StairsBlock.HALF) == vanilla.get(StairsBlock.HALF), "stairs preserve native placement properties");
            context.assertTrue(carrier.getCollisionShape(context.getWorld(), pos).equals(vanilla.getCollisionShape(context.getWorld(), pos)), "stairs preserve exact native collision shape");
            context.assertTrue(carrier.with(DwBlocks.VISUAL, Visual.ALT).get(StairsBlock.FACING) == carrier.get(StairsBlock.FACING), "ALT changes only visual state");
            context.assertTrue(DwBlocks.sourceState(carrier.with(DwBlocks.VISUAL, Visual.ALT)).equals(vanilla), "source conversion omits visual and restores native state");
        }
        DwBlocks.Entry fence = first(FenceBlock.class);
        if (fence != null) {
            BlockState carrier = fence.block().getDefaultState();
            context.setBlockState(new BlockPos(4, 2, 1), carrier);
            context.setBlockState(new BlockPos(5, 2, 1), carrier);
            context.assertTrue(context.getBlockState(new BlockPos(4, 2, 1)).get(Properties.EAST), "adjacent native carrier fences actually connect through native tags");
        }
        DwBlocks.Entry door = first(DoorBlock.class);
        if (door != null) {
            BlockState carrier = door.block().getDefaultState();
            context.assertTrue(carrier.contains(DoorBlock.OPEN) && carrier.contains(DoorBlock.HALF), "door retains open and double-block state");
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "dw_blocks", tickLimit = 200)
    public void everyCarrierKeepsSourceSchemaAndBaseAltCollision(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(10, 2, 10));
        for (DwBlocks.Entry entry : DwBlocks.entries()) {
            context.assertTrue(DwBlocks.sourceState(entry.block().getDefaultState()).equals(entry.sourceBlock().getDefaultState()), "default differs for " + entry.identifier());
            context.assertTrue(entry.block().getLootTableId().toString().equals("bloodborne_dw:blocks/" + entry.id()), "loot table differs for " + entry.identifier());
            context.assertTrue(entry.block().getJumpVelocityMultiplier() == entry.sourceBlock().getJumpVelocityMultiplier(), "jump multiplier differs for " + entry.identifier());
            if (entry.sourceBlock() instanceof net.minecraft.block.Stainable stain) context.assertTrue(entry.block() instanceof net.minecraft.block.Stainable && ((net.minecraft.block.Stainable)entry.block()).getColor() == stain.getColor(), "native glass color lost for " + entry.identifier());
            Set<String> source = names(entry.sourceBlock().getStateManager().getProperties());
            Set<String> carrier = names(entry.block().getStateManager().getProperties());
            context.assertTrue(carrier.containsAll(source) && carrier.size() == source.size() + 1 && carrier.contains(DwBlocks.VISUAL.getName()), "schema differs for " + entry.identifier() + " source=" + source + " carrier=" + carrier);
            for (BlockState state : entry.block().getStateManager().getStates()) {
                BlockState vanilla = DwBlocks.sourceState(state);
                context.assertTrue(sameShape(state.getCollisionShape(context.getWorld(), pos), vanilla.getCollisionShape(context.getWorld(), pos)), "collision differs for " + entry.identifier() + " " + state.getEntries());
                context.assertTrue(sameShape(state.with(DwBlocks.VISUAL, Visual.ALT).getCollisionShape(context.getWorld(), pos), vanilla.getCollisionShape(context.getWorld(), pos)), "ALT collision differs for " + entry.identifier());
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "dw_blocks", tickLimit = 200)
    public void optionalWorldEditSelectionSavesOnlySelectedCuboid(TestContext context) throws Exception {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("worldedit")) { context.complete(); return; }
        var player=context.createMockCreativeServerPlayerInWorld(); var world=context.getWorld();
        var adapter=Class.forName("com.sk89q.worldedit.fabric.FabricAdapter");
        Object actor=adapter.getMethod("adaptPlayer", net.minecraft.server.network.ServerPlayerEntity.class).invoke(null,player);
        Object weWorld=adapter.getMethod("adapt", net.minecraft.world.World.class).invoke(null,world);
        Class<?> we=Class.forName("com.sk89q.worldedit.WorldEdit"); Object instance=we.getMethod("getInstance").invoke(null);
        Object manager=we.getMethod("getSessionManager").invoke(instance); Object session=manager.getClass().getMethod("get",Class.forName("com.sk89q.worldedit.session.SessionOwner")).invoke(manager,actor);
        var selector=Class.forName("com.sk89q.worldedit.regions.selector.CuboidRegionSelector"); Class<?> weWorldType=Class.forName("com.sk89q.worldedit.world.World"); Class<?> regionSelector=Class.forName("com.sk89q.worldedit.regions.RegionSelector");
        Object incomplete=selector.getConstructor(weWorldType).newInstance(weWorld);
        session.getClass().getMethod("setRegionSelector",weWorldType,regionSelector).invoke(session,weWorld,incomplete);
        var source=player.getCommandSource().withLevel(2); var dispatcher=world.getServer().getCommandManager().getDispatcher();
        int before=dev.dreamwalker.bloodbornedw.visual.VisualService.snapshot(world).getList("rules",10).size();
        try { dispatcher.execute("bb visual alt selection 00001",source); context.assertTrue(false,"incomplete selection accepted"); } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        context.assertTrue(dev.dreamwalker.bloodbornedw.visual.VisualService.snapshot(world).getList("rules",10).size()==before,"failed selection changed rules");
        var vector=Class.forName("com.sk89q.worldedit.math.BlockVector3"); BlockPos first=context.getAbsolutePos(new BlockPos(1,1,1)),last=context.getAbsolutePos(new BlockPos(5,5,5));
        var at=vector.getMethod("at",int.class,int.class,int.class); Object min=at.invoke(null,first.getX(),first.getY(),first.getZ()),max=at.invoke(null,last.getX(),last.getY(),last.getZ());
        Object complete=selector.getConstructor(weWorldType,vector,vector).newInstance(weWorld,min,max);session.getClass().getMethod("setRegionSelector",weWorldType,regionSelector).invoke(session,weWorld,complete);
        dispatcher.execute("bb visual alt selection 00001",source); var block=DwBlocks.byId("00001").block().getDefaultState();
        context.assertTrue(dev.dreamwalker.bloodbornedw.visual.VisualService.effective(world,first,block)==Visual.ALT,"WorldEdit cuboid command failed");
        context.assertTrue(dev.dreamwalker.bloodbornedw.visual.VisualService.effective(world,last.add(1,0,0),block)==Visual.BASE,"WorldEdit command changed outside selection");
        context.complete();
    }

    private static Set<String> names(Iterable<net.minecraft.state.property.Property<?>> properties) {
        Set<String> result = new HashSet<>();
        for (var property : properties) result.add(property.getName());
        return result;
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "dw_blocks", tickLimit = 40)
    public void architectureDoesNotBecomeVanillaDirtOrConcrete(TestContext context) {
        var world = context.getWorld();
        for (String source : new String[] {"dirt_path", "farmland"}) {
            var entry = DwBlocks.bySource(new net.minecraft.util.Identifier("minecraft", source));
            if (entry == null) continue;
            BlockPos relative = new BlockPos(source.equals("dirt_path") ? 2 : 5, 2, 2);
            context.setBlockState(relative.down(), net.minecraft.block.Blocks.STONE);
            context.setBlockState(relative, entry.block().getDefaultState());
            context.setBlockState(relative.up(), net.minecraft.block.Blocks.STONE);
            entry.block().scheduledTick(entry.block().getDefaultState(), world, context.getAbsolutePos(relative), world.random);
            if (entry.sourceBlock() instanceof net.minecraft.block.FarmlandBlock) {
                context.assertTrue(!entry.block().hasRandomTicks(entry.block().getDefaultState()), "architectural farmland ages");
                entry.block().onLandedUpon(world, entry.block().getDefaultState(), context.getAbsolutePos(relative), context.createMockCreativeServerPlayerInWorld(), 4.0F);
            }
        }
        var powder = first(net.minecraft.block.ConcretePowderBlock.class);
        if (powder != null) {
            BlockPos relative = new BlockPos(8, 2, 2), pos = context.getAbsolutePos(relative);
            context.setBlockState(relative.down(), net.minecraft.block.Blocks.STONE);
            context.setBlockState(relative, powder.block().getDefaultState());
            context.setBlockState(relative.east(), net.minecraft.block.Blocks.WATER);
            BlockState result = powder.block().getStateForNeighborUpdate(powder.block().getDefaultState(), net.minecraft.util.math.Direction.EAST, net.minecraft.block.Blocks.WATER.getDefaultState(), world, pos, pos.east());
            context.assertTrue(result.getBlock() == powder.block(), "water hardened architectural powder");
            ((net.minecraft.block.ConcretePowderBlock) powder.block()).onLanding(world, pos, result, net.minecraft.block.Blocks.WATER.getDefaultState(), null);
        }
        var redstone = first(net.minecraft.block.RedstoneOreBlock.class);
        var sponge = DwBlocks.bySource(new net.minecraft.util.Identifier("minecraft", "sponge"));
        if (sponge != null) {
            BlockPos relative = new BlockPos(11, 2, 2);
            context.setBlockState(relative, sponge.block().getDefaultState());
            context.setBlockState(relative.east(), net.minecraft.block.Blocks.WATER);
            context.assertTrue(context.getBlockState(relative).getBlock() == sponge.block(), "architectural sponge became wet sponge");
            context.assertTrue(context.getBlockState(relative.east()).isOf(net.minecraft.block.Blocks.WATER), "architectural sponge drained map water");
        }
        if (redstone != null) context.assertTrue(redstone.block().hasRandomTicks(redstone.block().getDefaultState().with(Properties.LIT, true)), "native mechanical random ticks were suppressed");
        context.runAtTick(8, () -> {
            for (String source : new String[] {"dirt_path", "farmland"}) {
                var entry = DwBlocks.bySource(new net.minecraft.util.Identifier("minecraft", source));
                if (entry != null) context.assertTrue(context.getBlockState(new BlockPos(source.equals("dirt_path") ? 2 : 5, 2, 2)).getBlock() == entry.block(), "covered architecture became dirt: " + source);
            }
            if (powder != null) context.assertTrue(context.getBlockState(new BlockPos(8, 2, 2)).getBlock() == powder.block(), "water converted architectural powder");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "dw_blocks", tickLimit = 40)
    public void potAcceptsNumericPlantsAndRetainsNumericFamilyAndVisual(TestContext context) {
        var empty = DwBlocks.bySource(new net.minecraft.util.Identifier("minecraft", "flower_pot"));
        var plant = DwBlocks.bySource(new net.minecraft.util.Identifier("minecraft", "spruce_sapling"));
        var filled = DwBlocks.bySource(new net.minecraft.util.Identifier("minecraft", "potted_spruce_sapling"));
        context.assertTrue(empty != null && plant != null && filled != null, "missing pot test families");
        BlockPos relative = new BlockPos(2, 2, 2), pos = context.getAbsolutePos(relative);
        var world = context.getWorld(); var player = context.createMockCreativeServerPlayerInWorld();
        context.setBlockState(relative.down(), net.minecraft.block.Blocks.STONE);
        context.setBlockState(relative, empty.block().getDefaultState().with(DwBlocks.VISUAL, Visual.ALT));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(plant.block()));
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
        world.getBlockState(pos).onUse(world, player, net.minecraft.util.Hand.MAIN_HAND, hit);
        context.assertTrue(context.getBlockState(relative).getBlock() == filled.block() && context.getBlockState(relative).get(DwBlocks.VISUAL) == Visual.ALT, "numeric plant did not enter numeric pot with ALT");
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        world.getBlockState(pos).onUse(world, player, net.minecraft.util.Hand.MAIN_HAND, hit);
        context.assertTrue(context.getBlockState(relative).getBlock() == empty.block() && context.getBlockState(relative).get(DwBlocks.VISUAL) == Visual.ALT, "empty pot escaped numeric namespace");
        context.complete();
    }

    private static boolean sameShape(net.minecraft.util.shape.VoxelShape first, net.minecraft.util.shape.VoxelShape second) {
        return first.getBoundingBoxes().equals(second.getBoundingBoxes());
    }

    private static DwBlocks.Entry first(Class<? extends Block> type) {
        for (DwBlocks.Entry entry : DwBlocks.entries()) if (type.isInstance(entry.sourceBlock())) return entry;
        return null;
    }
}
