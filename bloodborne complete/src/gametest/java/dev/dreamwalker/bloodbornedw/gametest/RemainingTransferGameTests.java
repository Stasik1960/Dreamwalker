package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornedw.link.CompositeMechanismBridge;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** New source composites only. Native client input and whole-server rejoin are separate checks. */
public final class RemainingTransferGameTests implements FabricGameTest {
    @BeforeBatch(batchId="v12_groups")
    public static void preloadExistingFixtureBorder(ServerWorld world) {
        // The old RP fixtures call getChunk then require immediate entity
        // tracking. The enlarged suite places their border fixture at x225;
        // load its small border before the batch without changing old tests.
        for(int x=12;x<=16;x++)for(int z=-2;z<=2;z++) {
            world.setChunkForced(x,z,true);world.getChunk(x,z);
        }
    }

    /** The original v9 immediate-RP-link fixture crosses from chunk19 to20.
     * Force its small test border before the batch, so world.getEntity(UUID)
     * can resolve freshly spawned targets during its same-tick assertions. */
    @BeforeBatch(batchId="v9_mechanisms")
    public static void preloadExistingMechanismFixtureBorder(ServerWorld world) {
        for(int x=18;x<=22;x++)for(int z=-2;z<=2;z++){
            world.setChunkForced(x,z,true);world.getChunk(x,z);
        }
    }
    private static List<CompositeRootBlock> added() {
        return CompositeArchitecture.blocks().stream().filter(block->block.spec.id.getPath().startsWith("source_"))
            .filter(block->ImportedTypeAliases.canonicalRegistry(block.spec.id.toString()).equals(block.spec.id.toString())&&!(block instanceof ImportedStairBlock))
            .sorted(Comparator.comparing(block->block.spec.id.toString())).toList();
    }
    private static ServerPlayerEntity player(ServerWorld world) {
        var player=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"TransferProbe"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet,PacketCallbacks callbacks) {}
        };
        player.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,player);
        player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        player.getAbilities().allowModifyWorld=true;
        return player;
    }
    private static ItemStack art(CompositeRootBlock block,int variant,CompositeRootBlock.Profile profile) {
        return block.art(poseState(block,variant).with(CompositeRootBlock.PROFILE,profile),ImportedTypeAliases.markPayload(null));
    }
    private static BlockState poseState(CompositeRootBlock block,int pose){BlockState state=block.getDefaultState().with(CompositeRootBlock.VARIANT,pose%16);if(block instanceof ImportedArtworkBlock)state=state.with(ImportedArtworkBlock.ART_BANK,pose/16);if(block instanceof ImportedLadderBlock)state=state.with(ImportedLadderBlock.HEIGHT,pose==0?ImportedLadderBlock.Height.ONE:ImportedLadderBlock.Height.TWO);return state;}
    private static void preload(ServerWorld world,BlockPos root,CompositeRootBlock block,int variant,int yaw) {
        var state=poseState(block,variant).with(CompositeRootBlock.ROTATION,yaw);
        Set<BlockPos> required=new HashSet<>();
        for(var offset:block.spec.footprint(state,0).keySet())required.add(root.add(offset.x(),offset.y(),offset.z()));
        if(block.spec.openable)for(var offset:block.spec.footprint(state.with(CompositeRootBlock.OPEN,true),0).keySet())required.add(root.add(offset.x(),offset.y(),offset.z()));
        required.add(root.down());
        // Ordinary placement also inspects adjacent vanilla carriers, because
        // fences/walls can protrude from them. Far-away fixtures must preload
        // that one-cell halo, rather than only the owner's own footprint.
        Set<Long> chunks=new HashSet<>();
        for(var cell:required)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            var adjacent=cell.add(dx,0,dz);int x=adjacent.getX()>>4,z=adjacent.getZ()>>4;
            long chunk=((long)x&0xffffffffL)|(((long)z&0xffffffffL)<<32);
            if(chunks.add(chunk)){world.setChunkForced(x,z,true);world.getChunk(adjacent);}
        }
    }
    private static Owner place(TestContext t,ServerPlayerEntity player,BlockPos root,CompositeRootBlock block,int variant,int yaw,ItemStack stack) {
        var world=t.getWorld();preload(world,root,block,variant,yaw);
        world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        player.refreshPositionAndAngles(root.getX()+.5,root.getY(),root.getZ()-8,yaw*45,0);
        player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(root.down()).add(0,.5,0),Direction.UP,root.down(),false);
        var result=stack.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        t.assertTrue(result.isAccepted(),"registered item places "+block.spec.id+" variant="+variant+" yaw="+yaw+"; "+CompositeRuntime.lastResult());
        var state=world.getBlockState(root);
        t.assertTrue(state.isOf(block)&&CompositeSpec.poseVariant(state)==variant&&state.get(CompositeRootBlock.ROTATION)==yaw,"ordinary item retains exact ID/art/yaw: "+block.spec.id);
        t.assertTrue(world.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()!=null,"one resident owner exists: "+block.spec.id);
        var own=(CompositeBlockEntity)world.getBlockEntity(root);
        t.assertTrue(own.resident().registryId().equals(block.spec.id.toString()),"owner identity is the new registered ID");
        return own.resident();
    }
    private static Set<BlockPos> cells(ServerWorld world,Owner owner) {
        var root=CompositeData.pos(owner.root());var entity=(CompositeBlockEntity)world.getBlockEntity(root);
        var instance=CompositeRuntime.instance(world,root,world.getBlockState(root),owner.instanceId(),entity.payload());
        Set<BlockPos> cells=new HashSet<>();for(var offset:instance.cells().keySet())cells.add(root.add(offset.x(),offset.y(),offset.z()));return cells;
    }
    private static void removed(TestContext t,ServerPlayerEntity player,Owner owner) {
        var world=t.getWorld();var occupied=cells(world,owner);
        t.assertTrue(CompositeRuntime.remove(world,owner,player,false).outcome()==TransactionCore.Outcome.COMMITTED,"owner removal commits "+owner.registryId());
        CompositeRuntime.drain(world);
        for(var cell:occupied)t.assertTrue(CompositeRuntime.contributions(world,cell).stream().noneMatch(row->row.owner().equals(owner)),"removed owner has no orphan at "+cell);
        t.assertTrue(world.getBlockState(CompositeData.pos(owner.root())).isAir(),"whole object root is released");
    }

    /** At most one bounded object case per server tick; never thousands of placements in one tick. */
    private static void eachTick(TestContext t,ServerPlayerEntity player,List<Runnable> cases) {
        for(int index=0;index<cases.size();index++) {
            var check=cases.get(index);
            t.runAtTick(index+1,()->{try {check.run();} catch(RuntimeException|Error failure) {if(player!=null)player.discard();throw failure;}});
        }
        t.runAtTick(cases.size()+2,()->{if(player!=null)player.discard();t.complete();});
    }
    private static BlockHitResult aim(TestContext t,ServerPlayerEntity player,Owner owner) {
        var world=t.getWorld();
        for(var cell:cells(world,owner))for(var row:CompositeRuntime.contributions(world,cell))if(row.owner().equals(owner))for(var bounds:world.getBlockState(cell).getOutlineShape(world,cell,ShapeContext.of(player)).getBoundingBoxes()) {
            double x=cell.getX()+(bounds.minX+bounds.maxX)/2,y=cell.getY()+(bounds.minY+bounds.maxY)/2,z=cell.getZ()+bounds.minZ;
            player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.refreshPositionAndAngles(x,y-player.getStandingEyeHeight(),z-2,0,0);
            if(owner.equals(CompositeRuntime.target(world,cell,player)))return new BlockHitResult(new Vec3d(x,y,z),Direction.NORTH,cell,false);
        }
        throw new AssertionError("New door has no reachable ordinary selection part: "+owner.registryId());
    }
    private static void use(TestContext t,ServerPlayerEntity player,Owner owner) {
        var hit=aim(t,player,owner);var state=t.getWorld().getBlockState(hit.getBlockPos());
        t.assertTrue(state.getBlock().onUse(state,t.getWorld(),hit.getBlockPos(),player,Hand.MAIN_HAND,hit).isAccepted(),"ordinary block/helper use callback accepts new door selection");
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60000,batchId="remaining_transfer_placement")
    public void everyNewVariantPlacesWithAllEightYawAndExactPhysicalSelectionContract(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(64,96,64));var player=player(world);
        var additions=added();t.assertTrue(!additions.isEmpty(),"remaining transfer registers new source composites");
        List<Runnable> cases=new ArrayList<>();
        for(var block:additions)for(int variant=0;variant<block.spec.variants.size();variant++)for(int yaw:new int[]{0,1,2,3,4,5,6,7}) {
            final int selected=variant;
            cases.add(()->{
                var owner=place(t,player,root,block,selected,yaw,art(block,selected,CompositeRootBlock.Profile.BASE));
                boolean collision=false,selection=false;
                for(var cell:cells(world,owner))for(var row:CompositeRuntime.contributions(world,cell))if(row.owner().equals(owner)) {
                    collision|=!row.shape().collision().isEmpty();selection|=!row.shape().selection().isEmpty();
                    t.assertTrue(CompositeSpec.poseVariant(CompositeData.state(row.rootData()))==selected,"all helper contributions refer to the chosen pose");
                }
                t.assertTrue(selection,"every finished object is a real selection target: "+block.spec.id+" variant="+selected);
                boolean expectedCollision=block.spec.simplePhysicalCollision?block.spec.simplePhysicalFootprint(world.getBlockState(root),0,0,0,0).values().stream().anyMatch(parts->!parts.isEmpty()):!block.spec.variants.get(selected).closed().collision().isEmpty();
                t.assertTrue(collision==expectedCollision,"physical emptiness follows the oriented, face-cropped pose: "+block.spec.id+" variant="+selected+" yaw="+yaw);
                removed(t,player,owner);
            });
        }
        eachTick(t,player,cases);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60000,batchId="remaining_transfer_copy")
    public void everyNewVariantPickCopiesOnlyArtWithNewUuidAndTypedNbtRoundTrips(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(4096,96,64));var player=player(world);
        List<Runnable> cases=new ArrayList<>();
        for(var block:added())for(int variant=0;variant<block.spec.variants.size();variant++) {
            final int selected=variant;
            cases.add(()->{
                var profile=block.spec.variants.get(selected).closed().parts().stream().anyMatch(part->!part.model().equals(part.altModel()))?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE;
                var owner=place(t,player,root,block,selected,2,art(block,selected,profile));
                var entity=(CompositeBlockEntity)world.getBlockEntity(root);var payload=entity.payload();payload.putLong("TransferOpaqueLong",Long.MIN_VALUE);payload.putString("TransferOpaqueText","сохранено");entity.set(owner,entity.contributions(),payload);
                var saved=entity.createNbt();var loaded=new CompositeBlockEntity(root,world.getBlockState(root));loaded.readNbt(saved);
                t.assertTrue(loaded.resident().equals(owner)&&loaded.payload().equals(payload)&&loaded.contributions().equals(entity.contributions()),"typed owner/payload/contributions round-trip exactly: "+block.spec.id);
                var picked=CompositeRuntime.pick(world,owner);var art=picked.getSubNbt("BlockStateTag");
                t.assertTrue(picked.isOf(block.asItem())&&art!=null&&Integer.parseInt(art.getString("variant"))+(block instanceof ImportedArtworkBlock?16*Integer.parseInt(art.getString("art_bank")):0)==selected&&art.getString("profile").equals(profile.asString()),"pick retains exact new artistic pose/profile");
                t.assertTrue(picked.getSubNbt("CompositePayload")==null&&picked.getSubNbt("BlockEntityTag")==null,"pick does not carry owner UUID, typed payload or mechanisms");
                removed(t,player,owner);
                var copy=place(t,player,root,block,selected,6,picked);
                var copied=(CompositeBlockEntity)world.getBlockEntity(root);
                t.assertTrue(!copy.instanceId().equals(owner.instanceId())&&copied.payload().isEmpty(),"copied art has independent UUID and clean instance payload");
                t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.PROFILE)==profile,"copied item restores ALT only when selected");
                removed(t,player,copy);
            });
        }
        eachTick(t,player,cases);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60000,batchId="remaining_transfer_neighbors")
    public void newObjectsIgnoreVanillaNeighborMechanicsAndNativeBreakKeepsUnrelatedBlocks(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(8192,96,64));var player=player(world);
        List<Runnable> cases=new ArrayList<>();
        for(var block:added())cases.add(()->{
                var owner=place(t,player,root,block,0,0,art(block,0,CompositeRootBlock.Profile.BASE));
                var state=world.getBlockState(root);
                for(var direction:Direction.values()) {
                    var neighbor=root.offset(direction);
                    // The pure neighbor callback is valid in every direction. Replacing
                    // an occupied helper would be deliberate whole-object destruction,
                    // rather than an unrelated vanilla neighbor change.
                    var updated=state.getStateForNeighborUpdate(direction,Blocks.STONE.getDefaultState(),world,root,neighbor);
                    t.assertTrue(updated.equals(state)&&world.getBlockState(root).equals(state),"neighbors cannot turn "+block.spec.id+" into vanilla carrier state");
                    boolean ownHelper=CompositeRuntime.contributions(world,neighbor).stream().anyMatch(row->row.owner().equals(owner));
                    boolean requiredSupport=block.spec.requiredSupport&&neighbor.equals(root.add(block.spec.supportOffset.x(),block.spec.supportOffset.y(),block.spec.supportOffset.z()));
                    if(ownHelper||requiredSupport)continue;
                    var previous=world.getBlockState(neighbor);
                    world.setBlockState(neighbor,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);
                    t.assertTrue(world.getBlockState(root).equals(state)&&((CompositeBlockEntity)world.getBlockEntity(root)).resident().equals(owner),"adding independent neighbors retains ID/state/UUID");
                    world.setBlockState(neighbor,previous,Block.NOTIFY_ALL);CompositeRuntime.drain(world);
                    t.assertTrue(world.getBlockState(root).equals(state)&&((CompositeBlockEntity)world.getBlockEntity(root)).resident().equals(owner),"removing neighbors retains ID/state/UUID");
                }
                var occupied=cells(world,owner);var independent=root.add(48,0,48);while(occupied.contains(independent))independent=independent.add(32,0,32);
                world.getChunk(independent);world.setBlockState(independent,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                player.refreshPositionAndAngles(root.getX()+.5,root.getY(),root.getZ()-2,0,0);
                player.interactionManager.tryBreakBlock(root);CompositeRuntime.drain(world);
                t.assertTrue(world.getBlockState(root).isAir(),"native server break removes the full object "+block.spec.id);
                for(var cell:occupied)t.assertTrue(CompositeRuntime.contributions(world,cell).stream().noneMatch(row->row.owner().equals(owner)),"native break clears every owned helper contribution");
                t.assertTrue(world.getBlockState(independent).isOf(Blocks.GOLD_BLOCK),"whole-object removal preserves unrelated vanilla block");world.removeBlock(independent,false);
        });
        eachTick(t,player,cases);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60000,batchId="remaining_transfer_catalogue")
    public void newRegistryIdsHaveUniqueReservedNumbersRussianNamesAndAllVariantsOffered(TestContext t) {
        var stacks=UnifiedCreativeCatalogue.canonicalItems();Set<String> numbers=new HashSet<>();
        t.assertTrue(stacks.stream().noneMatch(stack->dev.dreamwalker.bloodbornedw.architecture.CatalogueMigration.retired(Registries.ITEM.getId(stack.getItem()).getPath())),"old retired variants/tools remain excluded from the offered catalogue");
        List<Runnable> cases=new ArrayList<>();
        for(var block:added())cases.add(()->{
            var entry=DebugCatalogue.entry(block.spec.id);
            t.assertTrue(entry!=null&&entry.registryId().equals(block.spec.id),"new object has exact catalogue entry "+block.spec.id);
            int number=Integer.parseInt(entry.temporaryId());
            t.assertTrue(entry.temporaryId().matches("[0-9]{5}")&&(number>=90021&&number<=90999||number>=93001)&&numbers.add(entry.temporaryId()),"new architecture uses a free unique five-digit number outside reserved RP/retired ranges");
            t.assertTrue(entry.name().matches(".*[А-Яа-яЁё].*"),"new object has Russian name "+block.spec.id);
            t.assertTrue(Registries.ITEM.get(block.spec.id)==block.asItem(),"new object has its registered item");
            t.assertTrue(stacks.stream().filter(stack->stack.isOf(block.asItem())).count()==1,"one installation item per functional new type "+block.spec.id);
        });
        eachTick(t,null,cases);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60000,batchId="remaining_transfer_doors")
    public void newDoorsOpenCloseByOrdinaryCallbackAndBridgeRejectsOldInstanceWhilePickKeepsOpenArt(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(12288,96,64));var player=player(world);List<Runnable> cases=new ArrayList<>();
        for(var block:added())if(block.spec.openable)for(int variant=0;variant<block.spec.variants.size();variant++) {
            final int selected=variant;
            cases.add(()->{
                var owner=place(t,player,root,block,selected,0,art(block,selected,CompositeRootBlock.Profile.BASE));
                var reference=CompositeMechanismBridge.reference(world,owner);
                t.assertTrue(MechanismLinks.inspect(world.getServer(),reference).availability()==MechanismLinks.Availability.LOADED,"new openable object is an exact loaded bridge target");
                var wrong=new MechanismLinks.TargetRef(reference.kind(),reference.dimension(),UUID.randomUUID(),reference.root(),reference.registryId());
                t.assertTrue(MechanismLinks.inspect(world.getServer(),wrong).availability()==MechanismLinks.Availability.STALE&&!MechanismLinks.applyEffect(world.getServer(),wrong,true,false)&&!world.getBlockState(root).get(CompositeRootBlock.OPEN),"bridge rejects a forged UUID at the correct registry/root");
                use(t,player,owner);
                t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&((CompositeBlockEntity)world.getBlockEntity(root)).resident().equals(owner),"ordinary new door open preserves exact identity");
                var picked=CompositeRuntime.pick(world,owner);
                t.assertTrue(picked.getSubNbt("BlockStateTag")!=null&&picked.getSubNbt("BlockStateTag").getString("open").equals("true"),"pick captures the selected open art");
                use(t,player,owner);t.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN),"ordinary new door closes without vanilla carrier mechanics");
                t.assertTrue(MechanismLinks.applyEffect(world.getServer(),reference,true,false)&&world.getBlockState(root).get(CompositeRootBlock.OPEN),"typed bridge opens the same new instance");
                t.assertTrue(MechanismLinks.applyEffect(world.getServer(),reference,false,false)&&!world.getBlockState(root).get(CompositeRootBlock.OPEN),"typed bridge closes the same new instance");
                removed(t,player,owner);
                var copy=place(t,player,root,block,selected,0,picked);
                t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&!copy.instanceId().equals(owner.instanceId()),"ordinary copied item retains open art with a fresh UUID");
                t.assertTrue(MechanismLinks.inspect(world.getServer(),reference).availability()==MechanismLinks.Availability.STALE&&!MechanismLinks.applyEffect(world.getServer(),reference,false,false)&&world.getBlockState(root).get(CompositeRootBlock.OPEN),"old bridge identity cannot operate replacement at reused coordinates");
                removed(t,player,copy);
            });
        }
        t.assertTrue(!cases.isEmpty(),"new finished door/trapdoor openable states are registered");eachTick(t,player,cases);
    }
}
