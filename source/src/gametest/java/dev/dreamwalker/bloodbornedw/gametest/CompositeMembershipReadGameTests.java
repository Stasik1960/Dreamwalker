package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;

/** Exact saved masks, not today's geometry, remain the authority for a cold
 * root. Each fixture owns an independent tiny synthetic shard, not a map copy. */
public final class CompositeMembershipReadGameTests implements FabricGameTest {
    private record Fixture(ShardedCompositeLedger ledger,Path file,Owner a,Owner b,long key,NbtCompound saved){}
    private static Fixture fixture(TestContext t,boolean dictionary)throws Exception {
        ServerWorld w=t.getWorld();Cell root=new Cell(80_008,80,80_008);long key=ChunkPos.toLong(root.x()>>4,root.z()>>4);
        Owner a=new Owner(UUID.randomUUID(),"bloodborne_dw:source_original_tree",root),b=new Owner(UUID.randomUUID(),a.registryId(),root.add(new Cell(1,0,0)));
        Footprint shape=new Footprint(List.of(new Box(0,0,0,.25,1,1),new Box(.75,0,0,1,1,1)),List.of(new Box(0,0,0,1,1,1)));
        Footprint other=new Footprint(List.of(),List.of(new Box(.1,.2,.3,.8,.9,1)));
        NbtCompound n=new NbtCompound();n.putInt("Schema",1);n.putInt("ChunkX",root.x()>>4);n.putInt("ChunkZ",root.z()>>4);n.putBoolean("Dictionary",dictionary);
        NbtList owners=new NbtList();owners.add(CompositeData.owner(a));owners.add(CompositeData.owner(b));n.put("ownerTable",owners);
        NbtList shapes=new NbtList();for(var s:List.of(shape,other)){NbtCompound tag=new NbtCompound();tag.put("collision",CompositeData.boxes(s.collision()));tag.put("selection",CompositeData.boxes(s.selection()));shapes.add(tag);}n.put("shapeTable",shapes);
        NbtList memberships=new NbtList();int ref=0;
        for(var owner:List.of(a,b)){
            NbtCompound row=new NbtCompound();row.putInt("ownerRef",ref);row.put("owner",CompositeData.owner(owner));NbtList members=new NbtList();
            for(var offset:List.of(Cell.ORIGIN,new Cell(-1,2,1))){NbtCompound m=new NbtCompound();m.putIntArray("offset",new int[]{offset.x(),offset.y(),offset.z()});m.putInt("shapeRef",ref);m.put("collision",CompositeData.boxes(ref==0?shape.collision():other.collision()));m.put("selection",CompositeData.boxes(ref==0?shape.selection():other.selection()));members.add(m);}
            row.put("members",members);memberships.add(row);ref++;
        }
        n.put("ownerMembership",memberships);n.put("cells",new NbtList());n.put("dataTable",new NbtList());
        Path directory=Files.createTempDirectory(w.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT),"membership-qa-");
        Path file=directory.resolve("owners-"+(root.x()>>4)+"."+(root.z()>>4)+".nbt");NbtIo.writeCompressed(n,file.toFile());
        var constructor=ShardedCompositeLedger.class.getDeclaredConstructor(ServerWorld.class,Path.class,Set.class,Runnable.class);constructor.setAccessible(true);
        var ledger=constructor.newInstance(w,directory,new TreeSet<>(Set.of(key)),(Runnable)()->{throw new AssertionError("Inspection dirtied index");});
        t.assertTrue(w.getChunkManager().getWorldChunk(root.x()>>4,root.z()>>4)==null,"fixture root is truly unloaded");return new Fixture(ledger,file,a,b,key,n);
    }
    @SuppressWarnings("unchecked") private static Map<Owner,Map<Cell,Footprint>> full(Fixture f)throws Exception {
        var method=ShardedCompositeLedger.class.getDeclaredMethod("read",long.class);method.setAccessible(true);Object shard=method.invoke(f.ledger,f.key);
        var field=shard.getClass().getDeclaredField("membership");field.setAccessible(true);return (Map<Owner,Map<Cell,Footprint>>)field.get(shard);
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80,batchId="cold_membership")
    public void dictionaryMasksMatchFullReaderWithoutRetainingOrPublishingColdShards(TestContext t)throws Exception {
        var f=fixture(t,true);byte[] before=Files.readAllBytes(f.file);var expected=full(f);long reads=((Number)f.ledger.metrics().get("ownerShardReads")).longValue();
        var actual=f.ledger.savedMemberships(Set.of(f.a,f.b));
        t.assertTrue(expected.equals(actual),"dictionary masks preserve exact collision/selection boxes, openings, offsets and owners");
        t.assertTrue(((Number)f.ledger.metrics().get("ownerShardReads")).longValue()==reads+1,"two requested owners share one root read");
        t.assertTrue(((Number)f.ledger.metrics().get("cachedOwnerShards")).intValue()==0,"cold inspection retains no shard, cell graph or chunk reference");
        t.assertTrue(Arrays.equals(before,Files.readAllBytes(f.file)),"inspection writes no saved bytes");
        boolean immutable=false;try{actual.get(f.a).clear();}catch(UnsupportedOperationException expectedFailure){immutable=true;}t.assertTrue(immutable,"published inspection masks cannot be edited");t.complete();
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80,batchId="cold_membership")
    public void legacyMasksStayExactAndColdReadsObserveLaterSavedChanges(TestContext t)throws Exception {
        var f=fixture(t,false);var first=f.ledger.savedMemberships(Set.of(f.a));
        t.assertTrue(first.size()==1&&first.get(f.a).equals(full(f).get(f.a)),"legacy shard returns only requested complete saved membership");
        NbtCompound changed=f.saved.copy();changed.getList("ownerMembership",10).getCompound(0).getList("members",10).getCompound(0).put("collision",CompositeData.boxes(List.of()));NbtIo.writeCompressed(changed,f.file.toFile());
        var next=f.ledger.savedMemberships(Set.of(f.a));t.assertTrue(!first.equals(next)&&next.get(f.a).get(Cell.ORIGIN).collision().isEmpty(),"no retained memo hides authoritative edits or fills openings");
        t.assertTrue(!first.get(f.a).get(Cell.ORIGIN).collision().isEmpty(),"prior immutable snapshot remains independent");t.complete();
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80,batchId="cold_membership")
    public void corruptRequestedMembershipStillFailsAndNeverForcesRootLoad(TestContext t)throws Exception {
        var f=fixture(t,true);NbtCompound changed=f.saved.copy();changed.getList("ownerMembership",10).getCompound(0).getList("members",10).getCompound(0).putInt("shapeRef",99);NbtIo.writeCompressed(changed,f.file.toFile());
        boolean failed=false;try{f.ledger.savedMemberships(Set.of(f.a));}catch(IllegalStateException expected){failed=true;}t.assertTrue(failed,"invalid requested shape reference fails rather than fabricating a mask");
        NbtIo.writeCompressed(f.saved,f.file.toFile());t.assertTrue(f.ledger.savedMemberships(Set.of(f.a)).get(f.a).equals(full(f).get(f.a)),"a later valid read is not poisoned by the failure");
        t.assertTrue(t.getWorld().getChunkManager().getWorldChunk(f.a.root().x()>>4,f.a.root().z()>>4)==null,"neither success nor failure loads the remote chunk");t.complete();
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=200,batchId="membership_native_save")
    public void unrequestedOwnerBudgetStillValidated(TestContext t)throws Exception {
        var f=fixture(t,true);NbtCompound changed=f.saved.copy();changed.getList("ownerMembership",10).getCompound(1).put("members",new NbtList());NbtIo.writeCompressed(changed,f.file.toFile());
        boolean failed=false;try{f.ledger.savedMemberships(Set.of(f.a));}catch(IllegalStateException expected){failed=true;}
        t.assertTrue(failed,"unrequested owner's empty mask is still rejected by metadata scan");
        t.assertTrue(((Number)f.ledger.metrics().get("cachedOwnerShards")).intValue()==0,"failed thin metadata scan retains no cold shard");t.complete();
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=200,batchId="membership_native_save")
    public void affectedStaticRemovalAndNativeDiskReloadPreserveSharedNeighbour(TestContext t)throws Exception {
        new CompositeShapeReuseGameTests().selectiveStaticRemovalPreservesSharedNeighbourThroughNativeDiskChunkReload(t);
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=180,batchId="membership_door")
    public void doorMovementAndOppositeSideClicksPreserveOpening(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));PlayerEntity player=t.createMockSurvivalPlayer();
        try{
            clear(w,root);w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            player.setPosition(root.getX()+.5,root.getY(),root.getZ()+6);
            var state=CompositeArchitecture.kindBlock("prototype_double_door").getDefaultState();
            t.assertTrue(CompositeRuntime.place(w,root,state,UUID.randomUUID(),player).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"native door placement commits");
            var owner=((CompositeBlockEntity)w.getBlockEntity(root)).resident();
            t.assertTrue(travel(player,root)<1.5,"closed leaves stop actual player movement");
            click(t,w,root,player,Direction.NORTH,new Vec3d(.5,.8,.125));
            t.assertTrue(w.getBlockState(root).get(CompositeRootBlock.OPEN),"north click opens door");
            t.assertTrue(Math.abs(travel(player,root)-3)<1e-6,"opened passage has its exact free volume");
            click(t,w,root,player,Direction.SOUTH,new Vec3d(-.9375,.8,.125));
            t.assertTrue(!w.getBlockState(root).get(CompositeRootBlock.OPEN)&&owner.equals(((CompositeBlockEntity)w.getBlockEntity(root)).resident()),"opposite-side click closes the same owner");
            t.assertTrue(travel(player,root)<1.5,"closing restores physical collision");t.complete();
        }finally{clear(w,root);player.discard();}
    }
    private static double travel(PlayerEntity player,BlockPos root){player.setPosition(root.getX()+.5,root.getY()+.25,root.getZ()-1);double before=player.getZ();player.move(net.minecraft.entity.MovementType.SELF,new Vec3d(0,0,3));return player.getZ()-before;}
    private static void click(TestContext t,ServerWorld w,BlockPos root,PlayerEntity player,Direction side,Vec3d local){
        Vec3d target=Vec3d.of(root).add(local),eye=target.add(0,0,side==Direction.NORTH?-3:3),d=target.subtract(eye);
        player.setPosition(eye.x,eye.y-player.getStandingEyeHeight(),eye.z);player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setHeadYaw(player.getYaw());player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
        var hit=w.raycast(new net.minecraft.world.RaycastContext(eye,target.add(0,0,side==Direction.NORTH?1:-1),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        t.assertTrue(hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK,"native selection ray reaches door");var selected=w.getBlockState(hit.getBlockPos());
        t.assertTrue(selected.getBlock().onUse(selected,w,hit.getBlockPos(),player,net.minecraft.util.Hand.MAIN_HAND,hit).isAccepted(),"real selected door part resolves its owner");
    }
    private static void clear(ServerWorld w,BlockPos root){for(BlockPos p:BlockPos.iterate(root.add(-5,-3,-5),root.add(5,5,5)))w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(w);}
}
