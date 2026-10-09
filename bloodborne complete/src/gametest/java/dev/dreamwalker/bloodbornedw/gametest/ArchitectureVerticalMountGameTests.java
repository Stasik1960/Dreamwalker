package dev.dreamwalker.bloodbornedw.gametest;

import java.util.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.util.function.BooleanBiFunction;

/** Actual item placement, atomic contribution edits, typed save and native shapes.
 * The public server height API is exercised here; this is not a GUI/render test. */
public final class ArchitectureVerticalMountGameTests implements FabricGameTest {
    private static final BlockPos LOCAL=new BlockPos(8,5,8);
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=600,batchId="architecture_vertical_mount")
    public void everyCanonicalArchitectureMovesBothShapesAndKeepsUuidThroughResetAndTypedSave(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(LOCAL);ServerPlayerEntity p=player(w);int checked=0;
        try{
            for(var row:DebugCatalogue.entries().stream().filter(e->e.kind().equals("architecture")).toList()){
                clear(w,root);Item item=Registries.ITEM.get(row.registryId());t.assertTrue(item!=Items.AIR,"canonical architecture has an item "+row.temporaryId());
                w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);p.setYaw(0);p.setSneaking(true);p.setPosition(root.getX()+20,root.getY()+5,root.getZ()+20);
                ItemStack offered=new ItemStack(item);p.setStackInHand(Hand.MAIN_HAND,offered);
                ActionResult used=item.useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,new BlockHitResult(Vec3d.of(root.down()).add(.5,1,.5),Direction.UP,root.down(),false)));
                t.assertTrue(used.isAccepted(),"real ordinary item places "+row.temporaryId()+" result="+used+" state="+w.getBlockState(root));
                BlockState state=w.getBlockState(root);var owner=VerticalMount.owner(w,root);t.assertTrue(owner!=null,"native/composite root has UUID before a height edit "+row.temporaryId());
                var initial=CompositeRuntime.instance(w,root,state,owner.instanceId(),((CompositeBlockEntity)w.getBlockEntity(root)).payload());
                VoxelShape physical=global(initial,false),selection=global(initial,true);
                for(double offset:new double[]{.125,-.125,8,0}){
                    t.assertTrue(VerticalMount.setOffset(w,root,offset,p),"height commits "+row.temporaryId()+"/"+offset);
                    CompositeRuntime.drain(w);var be=(CompositeBlockEntity)w.getBlockEntity(root);t.assertTrue(owner.equals(be.resident())&&w.getBlockState(root).getBlock()==state.getBlock()&&VerticalMount.offset(w,root)==offset,"type/root/UUID and exact height survive "+row.temporaryId());
                    var actual=CompositeRuntime.instance(w,root,w.getBlockState(root),owner.instanceId(),be.payload());
                    equal(t,global(actual,false),physical.offset(0,offset,0),"physical volumes translate without scale "+row.temporaryId());
                    equal(t,global(actual,true),selection.offset(0,offset,0),"selection translates with physics "+row.temporaryId());
                    for(var entry:actual.cells().entrySet()){BlockPos cell=CompositeData.pos(owner.root().add(entry.getKey()));t.assertTrue(CompositeRuntime.contributions(w,cell).stream().anyMatch(c->c.owner().equals(owner)),"all shifted owner cells point to fixed UUID/root");}
                    NbtCompound saved=be.createNbt();var reloaded=new CompositeBlockEntity(root,w.getBlockState(root));reloaded.readNbt(saved);t.assertTrue(reloaded.createNbt().equals(saved)&&owner.equals(reloaded.resident()),"typed owner/height save roundtrip "+row.temporaryId());
                    ItemStack picked=CompositeRuntime.pick(w,owner);t.assertTrue(DebugCatalogue.entry(picked).temporaryId().equals(row.temporaryId())&&picked.getSubNbt("CompositePayload")==null&&picked.getSubNbt("BlockEntityTag")==null,"new picked item keeps type, no height/UUID/provenance privileges");
                }
                t.assertTrue(CompositeRuntime.remove(w,owner,p,false).outcome()==TransactionCore.Outcome.COMMITTED,"whole removal after reset "+row.temporaryId());
                t.assertTrue(CompositeLedger.get(w).cells().stream().noneMatch(cell->CompositeLedger.get(w).at(cell).stream().anyMatch(c->c.owner().equals(owner))),"no stale helper or ledger survives deletion "+row.temporaryId());checked++;
            }
            t.assertTrue(checked==18,"all eighteen canonical architecture types were placed and moved");t.complete();
        }finally{clear(w,root);p.discard();}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="architecture_vertical_mount")
    public void HeightEditMayOverlayNativeChestWithoutWritingItsTypedDataAndRejectsLivingOrInvalidBounds(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(LOCAL);ServerPlayerEntity p=player(w);ArmorStandEntity obstruction=null;
        try{
            clear(w,root);w.setBlockState(root.down(),Blocks.STONE.getDefaultState());var block=CompositeArchitecture.kindBlock("prototype_roof");
            t.assertTrue(CompositeRuntime.place(w,root,block.getDefaultState(),UUID.randomUUID(),p).outcome()==TransactionCore.Outcome.COMMITTED,"place an ordinary roof for overlay test");var owner=VerticalMount.owner(w,root);
            BlockPos foreign=root.up(2);w.setBlockState(foreign,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);ChestBlockEntity chest=(ChestBlockEntity)w.getBlockEntity(foreign);chest.setCustomName(Text.literal("Native inventory remains exact"));chest.setStack(0,new ItemStack(Items.DIAMOND,7));NbtCompound before=chest.createNbt();BlockState chestState=w.getBlockState(foreign);
            t.assertTrue(VerticalMount.setOffset(w,root,2,p),"height specifically permits solid native intersection");t.assertTrue(w.getBlockState(foreign).equals(chestState)&&w.getBlockEntity(foreign)==chest&&chest.createNbt().equals(before),"height edit changes no foreign palette, BE identity, inventory or typed NBT");
            equal(t,w.getBlockState(foreign).getCollisionShape(w,foreign),VoxelShapes.fullCube(),"native chest plus authored cube remains a solid union");
            NbtCompound ownBefore=((CompositeBlockEntity)w.getBlockEntity(root)).createNbt();
            for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,1e12,-1e12})t.assertTrue(!VerticalMount.setOffset(w,root,value,p)&&((CompositeBlockEntity)w.getBlockEntity(root)).createNbt().equals(ownBefore),"invalid/build-limit offset rejected before world mutation");
            p.getAbilities().creativeMode=false;p.getAbilities().allowModifyWorld=false;t.assertTrue(!VerticalMount.setOffset(w,root,3,p),"height requires actual construction permission");p.getAbilities().creativeMode=true;p.getAbilities().allowModifyWorld=true;
            obstruction=new ArmorStandEntity(w,root.getX()+.5,root.getY()+4,root.getZ()+.5);w.spawnEntity(obstruction);
            t.assertTrue(!VerticalMount.setOffset(w,root,4,p)&&((CompositeBlockEntity)w.getBlockEntity(root)).createNbt().equals(ownBefore),"solid-block height exception does not waive living-entity obstruction or atomic rollback");obstruction.discard();obstruction=null;
            t.assertTrue(CompositeRuntime.remove(w,owner,p,false).outcome()==TransactionCore.Outcome.COMMITTED&&chest.createNbt().equals(before)&&w.getBlockState(foreign).equals(chestState),"whole removal strips only owner contribution, preserving foreign inventory");t.complete();
        }finally{if(obstruction!=null)obstruction.discard();clear(w,root);p.discard();}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=160,batchId="architecture_vertical_mount")
    public void SourcePairRetainsOpaqueProvenanceWhileBackingPhysicsAndClimbingMoveWithOwner(TestContext t){
        ServerWorld w=t.getWorld();BlockPos visual=t.getAbsolutePos(LOCAL);ServerPlayerEntity p=player(w);
        try{
            clear(w,visual);BlockPos root=visual.south();BlockState original=Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.FACING,Direction.NORTH).with(BeehiveBlock.HONEY_LEVEL,1),physical=Blocks.LADDER.getDefaultState().with(LadderBlock.FACING,Direction.SOUTH);
            w.setBlockState(visual,original,Block.NOTIFY_ALL);w.setBlockState(root,physical,Block.NOTIFY_ALL);NbtCompound raw=w.getBlockEntity(visual).createNbtWithId();raw.putLong("OpaqueLong",Long.MIN_VALUE);raw.putByteArray("OpaqueBytes",new byte[]{-1,0,1});w.getBlockEntity(visual).readNbt(raw);
            raw=w.getBlockEntity(visual).createNbtWithId();var install=new SourceLadderRuntime.Installation(visual,root,original,physical,raw,null,2,PrototypeLadderBlock.Profile.ALT,UUID.randomUUID());t.assertTrue(SourceLadderRuntime.install(w,install,p).committed(),"actual source-pair installation");
            SourceLadderBlockEntity source=(SourceLadderBlockEntity)w.getBlockEntity(root);var owner=source.resident();NbtCompound provenance=source.provenance(),backing=w.getBlockEntity(visual).createNbtWithId();var initial=CompositeRuntime.instance(w,root,w.getBlockState(root),owner.instanceId(),source.payload());
            t.assertTrue(VerticalMount.setOffset(w,root,3,p),"source pair height commits with unchanged source UUID");source=(SourceLadderBlockEntity)w.getBlockEntity(root);
            t.assertTrue(owner.equals(source.resident())&&source.provenance().equals(provenance)&&w.getBlockEntity(visual).createNbtWithId().equals(backing)&&SourceLadderRuntime.resolveRoot(w,visual).equals(root),"fixed original role and opaque Original bytes stay exact, source owner resolves");
            equal(t,w.getBlockState(visual).getCollisionShape(w,visual),VoxelShapes.empty(),"old hidden backing cell contributes no ghost solid at original height");equal(t,w.getBlockState(visual.up(3)).getCollisionShape(w,visual.up(3)),VoxelShapes.fullCube(),"exact source backing cube moves with installation");
            var next=CompositeRuntime.instance(w,root,w.getBlockState(root),owner.instanceId(),source.payload());equal(t,global(next,false),global(initial,false).offset(0,3,0),"source original physical union translates exactly");
            var ladder=(PrototypeLadderBlock)w.getBlockState(root).getBlock();var strip=ladder.climbingShape(w.getBlockState(root)).getBoundingBoxes().get(0);t.assertTrue(VerticalMount.climbing(w,strip.offset(root).offset(0,3,0).contract(.001)).orElseThrow().equals(root)&&VerticalMount.climbing(w,strip.offset(root).contract(.001)).isEmpty(),"climbing zone moves upward, no old-height climb ghost");
            ItemStack picked=CompositeRuntime.pick(w,owner);t.assertTrue(picked.getSubNbt("BlockEntityTag")==null&&picked.getSubNbt("CompositePayload")==null,"source item excludes offset and source installation privileges");
            t.assertTrue(SourceLadderRuntime.remove(w,root,p,false),"whole pair removal after height");t.assertTrue(w.getBlockState(root).isAir()&&w.getBlockState(visual).isAir()&&w.getBlockState(visual.up(3)).isAir(),"root, fixed owned role and all translated helper cells removed");t.complete();
        }finally{clear(w,visual);p.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="architecture_vertical_mount")
    public void OneCombinedCreativeCatalogueOffersCanonicalTypesWeaponsEggsAndSole90009Tool(TestContext t){
        var offered=UnifiedCreativeCatalogue.canonicalItems();Set<Identifier> ids=new HashSet<>();for(ItemStack stack:offered){t.assertTrue(ids.add(Registries.ITEM.getId(stack.getItem()))&&stack.getSubNbt("CompositePayload")==null&&stack.getSubNbt("BlockEntityTag")==null&&(!stack.hasNbt()||!stack.getNbt().toString().contains("VerticalOffset")),"one canonical creative type item without instance UUID/height/provenance: "+Registries.ITEM.getId(stack.getItem())+"");}
        t.assertTrue(Registries.ITEM_GROUP.containsId(UnifiedCreativeCatalogue.ID)&&!Registries.ITEM_GROUP.containsId(new Identifier("bloodborne_dw","prototypes"))&&!Registries.ITEM_GROUP.containsId(new Identifier("bloodborne_dw","composite_prototypes"))&&!Registries.ITEM_GROUP.containsId(new Identifier("bloodborne_rp","adventures")),"combined mod registers one shared category, historical own groups removed");
        for(String id:List.of("bloodborne_dw:composite_builder","bloodborne_dw:prototype_glass_window_03","bloodborne_rp:saw_cleaver","bloodborne_rp:cleric_beast_spawn_egg"))t.assertTrue(ids.contains(new Identifier(id)),"shared creative catalogue includes "+id);
        t.assertTrue(offered.stream().filter(stack->stack.getItem() instanceof BuildingTool).count()==1&&!ids.contains(new Identifier("bloodborne_dw:builder_tool")),"Creative offers sole90009 and hides retired90008");
        t.assertTrue(Registries.ITEM.containsId(new Identifier("bloodborne_dw:builder_tool"))&&DebugCatalogue.entries().stream().anyMatch(entry->entry.temporaryId().equals("90008")&&entry.registryId().equals(new Identifier("bloodborne_dw:builder_tool"))),"retired90008 remains a registered reader with its number reserved for old saves");
        t.assertTrue(!ids.contains(new Identifier("bloodborne_dw","wall_builder"))&&!ids.contains(new Identifier("bloodborne_dw","composite_cell"))&&!ids.contains(new Identifier("bloodborne_rp","furniture_8_placer")),"technical roles/retired duplicate aliases have no creative placement item");t.complete();
    }
    private static ServerPlayerEntity player(ServerWorld w){var p=new ServerPlayerEntity(w.getServer(),w,new GameProfile(UUID.randomUUID(),"VerticalMountTest")){@Override public void sendMessage(Text message,boolean overlay){}};p.getAbilities().allowModifyWorld=true;p.getAbilities().creativeMode=true;p.setPosition(0,200,0);return p;}
    private static VoxelShape global(ObjectInstance object,boolean selection){VoxelShape result=VoxelShapes.empty();for(var entry:object.cells().entrySet())for(var box:selection?entry.getValue().selection():entry.getValue().collision())result=VoxelShapes.union(result,VoxelShapes.cuboid(box.minX()+entry.getKey().x(),box.minY()+entry.getKey().y(),box.minZ()+entry.getKey().z(),box.maxX()+entry.getKey().x(),box.maxY()+entry.getKey().y(),box.maxZ()+entry.getKey().z()));return result;}
    private static void equal(TestContext t,VoxelShape a,VoxelShape b,String message){t.assertTrue(!VoxelShapes.matchesAnywhere(a,b,BooleanBiFunction.NOT_SAME),message+" actual="+a.getBoundingBoxes()+" expected="+b.getBoundingBoxes());}
    private static void clear(ServerWorld w,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-6,-3,-6),root.add(6,29,6)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(w);}
}
