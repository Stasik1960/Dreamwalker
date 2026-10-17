package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.GsonBuilder;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

/** One finite static family, with the unchanged owned representation as reference. */
public final class StaticGrassMasonryV1274GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_grass_masonry_v1274",tickLimit=100)
    public void oneNativeStateHasNoEntityItemOrServiceNbtAndRejectsPrivatePayload(TestContext t) {
        var block=StaticGrassMasonryBlock.block();var state=StaticGrassMasonryBlock.state();
        var legacy=CompositeArchitecture.kindBlock(StaticGrassMasonryBlock.LEGACY_PATH).getDefaultState();
        var payload=ImportedTypeAliases.markPayload(null);
        t.assertTrue(Registries.BLOCK.get(StaticGrassMasonryBlock.ID)==block,"Exact one-family static registration");
        t.assertTrue(!((Block)block instanceof BlockEntityProvider)&&!state.hasBlockEntity(),"Ordinary palette state has no BlockEntity provider");
        t.assertTrue(block.getStateManager().getStates().size()==1&&state.getEntries().isEmpty(),"One pose creates one native state and no global state expansion");
        t.assertTrue(block.asItem()==Items.AIR,"No alternate item or catalogue appearance is introduced");
        t.assertTrue(state.getRenderType()==BlockRenderType.MODEL,"Static representation uses the ordinary native chunk model");
        t.assertTrue(StaticGrassMasonryBlock.eligible(legacy,payload),"The prepared city's exact typed payload is eligible");
        int rejected=0;
        var extra=payload.copy();extra.putString("PrivateUserData","preserve");
        var shifted=payload.copy();var xyz=new NbtList();xyz.add(NbtDouble.of(0));xyz.add(NbtDouble.of(0));xyz.add(NbtDouble.of(0));shifted.put("SourceShift",xyz);
        var archived=payload.copy();archived.put("OriginalSourceBlockEntities",new NbtList());
        var malformed=new NbtCompound();malformed.putByte(ImportedTypeAliases.VERSION_KEY,(byte)1);
        var future=payload.copy();future.putInt(ImportedTypeAliases.VERSION_KEY,2);
        for(var unsafe:List.of(extra,shifted,archived,malformed,future,new NbtCompound())) {
            t.assertTrue(!StaticGrassMasonryBlock.eligible(legacy,unsafe),"No private, source provenance, shift or wrongly typed data is discarded");rejected++;
        }
        for(int yaw=1;yaw<8;yaw++){t.assertTrue(!StaticGrassMasonryBlock.eligible(legacy.with(CompositeRootBlock.ROTATION,yaw),payload),"Only the one used city orientation is converted");rejected++;}
        t.assertTrue(!StaticGrassMasonryBlock.eligible(legacy.with(CompositeRootBlock.OPEN,true),payload),"Open pose remains owned");
        t.assertTrue(!StaticGrassMasonryBlock.eligible(legacy.with(CompositeRootBlock.VARIANT,1),payload),"Unused variant remains owned");
        var encoded=NbtHelper.fromBlockState(state);
        t.assertTrue(encoded.getKeys().equals(Set.of("Name"))&&encoded.getString("Name").equals(StaticGrassMasonryBlock.ID.toString()),"Palette NBT stores only the native block ID, without UUID or service fields");
        var decoded=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),encoded);
        t.assertTrue(decoded==state,"Native typed palette codec restores the exact singleton state");
        proof(t,"scope",Map.of("status","PASS_FINITE_STATIC_NO_BE_UUID_NBT","states",1,"rejectedUnsafePayloadsAndYaws",rejected,"paletteNbt",encoded.toString()));t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_grass_masonry_v1274",tickLimit=120)
    public void realNativePlacementMatchesOwnedCollisionOutlineAndCarrierFlags(TestContext t) {
        var world=t.getWorld();var old=t.getAbsolutePos(new BlockPos(2,3,2));var pos=t.getAbsolutePos(new BlockPos(6,3,2));
        var legacy=CompositeArchitecture.kindBlock(StaticGrassMasonryBlock.LEGACY_PATH).getDefaultState();var state=StaticGrassMasonryBlock.state();
        UUID uuid=UUID.randomUUID();dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner=null;
        try {
            world.getChunk(old);world.getChunk(pos);
            var installed=CompositeRuntime.place(world,old,legacy,uuid,null,ImportedTypeAliases.markPayload(null));
            t.assertTrue(installed.outcome()==TransactionCore.Outcome.COMMITTED,"Existing owned reference installs through the unchanged transaction path");
            var root=(CompositeBlockEntity)world.getBlockEntity(old);owner=root.resident();
            t.assertTrue(owner!=null&&owner.instanceId().equals(uuid),"Owned reference retains its individual UUID");
            t.assertTrue(world.setBlockState(pos,state,Block.NOTIFY_ALL),"Static state installs through native setBlockState");
            t.assertTrue(world.getBlockEntity(pos)==null&&world.getChunk(pos).getBlockEntity(pos)==null,"No eager or chunk-local BlockEntity is created");
            var oldCollision=legacy.getCollisionShape(world,old);var newCollision=state.getCollisionShape(world,pos);
            t.assertTrue(oldCollision.getBoundingBoxes().equals(newCollision.getBoundingBoxes())&&newCollision.getBoundingBoxes().equals(VoxelShapes.fullCube().getBoundingBoxes()),"Actual world collision matches the old owner's exact unit cube");
            t.assertTrue(legacy.getOutlineShape(world,old).getBoundingBoxes().equals(state.getOutlineShape(world,pos).getBoundingBoxes()),"Actual world outline preserves the owner's physical selection");
            t.assertTrue(newCollision==state.getCollisionShape(world,pos.add(9,0,0)),"Different cells share one immutable collision object");
            t.assertTrue(legacy.getOpacity(world,old)==state.getOpacity(world,pos)&&state.getOpacity(world,pos)==15,"Unflagged converted source opacity stays15");
            t.assertTrue(legacy.getAmbientOcclusionLightLevel(world,old)==state.getAmbientOcclusionLightLevel(world,pos),"Native carrier AO scalar matches the actual legacy root at its real world position");
            t.assertTrue(legacy.getCullingShape(world,old).isEmpty()&&state.getCullingShape(world,pos).isEmpty(),"Old owner carrier and static carrier both expose empty culling shape");
            t.assertTrue(legacy.isTransparent(world,old)==state.isTransparent(world,pos)&&legacy.getFluidState().equals(state.getFluidState()),"Carrier transparency and fluid stay equal");
            var probe=new net.minecraft.entity.decoration.ArmorStandEntity(world,pos.getX()+.5,pos.getY()+3,pos.getZ()+.5);
            var hit=world.raycast(new RaycastContext(Vec3d.ofCenter(pos).add(0,2,0),Vec3d.ofCenter(pos).add(0,-2,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,probe));probe.discard();
            t.assertTrue(hit.getBlockPos().equals(pos)&&hit.getSide()==Direction.UP&&Math.abs(hit.getPos().y-(pos.getY()+1))<1e-8,"Native collision ray lands at the exact top of the static cube");
            t.assertTrue(root.resident().equals(owner)&&root.payload().equals(ImportedTypeAliases.markPayload(null)),"Static placement never changes the old owner's typed data");
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(pos,state,Block.NOTIFY_ALL);
            t.assertTrue(world.getBlockEntity(pos)==null&&world.getBlockState(pos)==state,"Native remove/reinstall needs no ownership lifecycle");
            proof(t,"world",Map.of("status","PASS_REAL_NATIVE_OWNED_SHAPE_FLAGS_AND_RAYCAST","ownedReferenceUuid",uuid.toString(),"staticBlockEntities",0,"sharedCollision",true));t.complete();
        } finally {
            if(owner!=null)CompositeRuntime.remove(world,owner,null,false);
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);
        }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_grass_masonry_v1274",tickLimit=160)
    public void helperGuestSurvivesStaticCommitAndFailureAfterActualNativeWriteRestoresTypedNbt(TestContext t) {
        var world=t.getWorld();var rootPos=t.getAbsolutePos(new BlockPos(2,5,2));var pos=rootPos.east();var cell=CompositeData.cell(pos);
        var legacy=CompositeArchitecture.kindBlock("source_light_gray_concrete").getDefaultState();
        dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner=null;
        try {
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunk(rootPos.add(x*16,0,z*16));
            var payload=ImportedTypeAliases.markPayload(null);payload.put("TypedGuestProbe",typed());
            var installed=CompositeRuntime.place(world,rootPos,legacy,UUID.randomUUID(),null,payload);
            t.assertTrue(installed.outcome()==TransactionCore.Outcome.COMMITTED,"A real adjacent source cornice installs its ordinary helper guest");
            var root=(CompositeBlockEntity)world.getBlockEntity(rootPos);owner=root.resident();
            t.assertTrue(world.getBlockState(pos).isOf(CompositeArchitecture.CELL)&&world.getBlockEntity(pos) instanceof CompositeBlockEntity,"Pilot target is an actual HELPER with no resident");
            var helper=(CompositeBlockEntity)world.getBlockEntity(pos);helper.set(null,helper.contributions(),typed());
            var helperState=world.getBlockState(pos);var helperNbt=helper.createNbt();var rootNbt=root.createNbt();var guestEntries=List.copyOf(CompositeLedger.get(world).at(cell));
            t.assertTrue(guestEntries.size()==1&&guestEntries.get(0).owner().equals(owner),"Actual ledger holds the adjacent owner guest");
            class Pilot extends FabricCompositeWorld {
                final boolean fail;int captures,writes,restores;boolean actualStaticWrite;
                Pilot(boolean fail){super(t.getWorld(),Map.of());this.fail=fail;}
                @Override public void prepareRollback(Map<Cell,CellSnapshot> expected){captures++;t.assertTrue(expected.keySet().equals(Set.of(cell)),"Only the changed helper gets rollback NBT; guest root is a fresh read precondition");super.prepareRollback(expected);}
                @Override public boolean writeSilently(Cell target,CellSnapshot next){
                    t.assertTrue(target.equals(cell)&&next.data().blockId().equals(StaticGrassMasonryBlock.ID.toString()),"Task-only native-carrier adapter cannot write any other cell/family");writes++;
                    world.setBlockState(pos,StaticGrassMasonryBlock.state(),Block.FORCE_STATE|Block.SKIP_DROPS);
                    actualStaticWrite=world.getBlockState(pos)==StaticGrassMasonryBlock.state()&&world.getBlockEntity(pos)==null;
                    boolean applied=super.writeSilently(target,next);return !fail&&applied;
                }
                @Override public boolean restoreSilently(Cell target,CellSnapshot previous){restores++;return super.restoreSilently(target,previous);}
                TransactionCore.Preparation plan(){var before=read(cell);var expected=new TreeMap<Cell,CellSnapshot>();expected.put(cell,before);for(var guest:before.guests())expected.put(guest.root(),read(guest.root()));var next=new CellSnapshot(CellSnapshot.Kind.FOREIGN,CompositeData.data(StaticGrassMasonryBlock.state(),new NbtCompound()),null,before.guests());return new TransactionCore.Preparation(new TransactionCore.Plan(dimensionKey(),expected,Map.of(cell,next),Map.of()),null);}
            }
            var failing=new Pilot(true);var failure=CompositeRuntime.execute(failing,failing.plan());
            t.assertTrue(failure.outcome()==TransactionCore.Outcome.ROLLED_BACK&&failing.actualStaticWrite&&failing.writes==1&&failing.restores==1&&failing.captures==1,"Injected false return occurs after the real native state/BE replacement, followed by one exact rollback");
            t.assertTrue(world.getBlockState(pos).equals(helperState)&&world.getBlockEntity(pos).createNbt().equals(helperNbt),"Failed native helper replacement restores the entire typed helper NBT exactly");
            t.assertTrue(root.createNbt().equals(rootNbt)&&CompositeLedger.get(world).at(cell).equals(guestEntries),"Failure preserves adjacent guest UUID, full typed root NBT and ledger contributions");
            // Arbitrary helper-private data belongs only to the rollback fault fixture;
            // the executable city's native helper has no such functional payload.
            helper=(CompositeBlockEntity)world.getBlockEntity(pos);helper.set(null,helper.contributions(),new NbtCompound());
            var committed=new Pilot(false);var result=CompositeRuntime.execute(committed,committed.plan());
            t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED&&committed.actualStaticWrite&&committed.restores==0&&world.getBlockEntity(pos)==null,"The same scoped transaction commits as a plain no-BE native block");
            t.assertTrue(root.createNbt().equals(rootNbt)&&CompositeLedger.get(world).at(cell).equals(guestEntries),"Commit preserves the adjacent owner's UUID, typed data and exact guest ledger");
            t.assertTrue(committed.read(cell).guests().equals(List.of(owner)),"Fresh static-carrier reads retain the live guest rather than assigning a new static UUID");
            proof(t,"guest-rollback",Map.of("status","PASS_HELPER_GUEST_COMMIT_AND_REAL_WRITE_TYPED_ROLLBACK","actualNativeWriteBeforeFailure",true,"capturedChangedCells",1,"restoredTypedHelperNbt",true,"guestRootNbtUnchanged",true,"guestLedgerUnchanged",true));t.complete();
        } finally {if(owner!=null)CompositeRuntime.remove(world,owner,null,false);world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);}
    }
    private static NbtCompound typed(){var n=new NbtCompound();n.putByte("byte",(byte)-1);n.putShort("short",(short)300);n.putInt("int",123456);n.putLong("long",Long.MIN_VALUE+9);n.putFloat("float",1.25F);n.putDouble("double",-4.75);n.putByteArray("bytes",new byte[]{-1,0,1});n.putString("string","typed guest and helper");var list=new NbtList();list.add(NbtInt.of(4));list.add(NbtInt.of(7));n.put("list",list);var child=new NbtCompound();child.putBoolean("child",true);n.put("compound",child);n.putIntArray("ints",new int[]{Integer.MIN_VALUE,0,Integer.MAX_VALUE});n.putLongArray("longs",new long[]{Long.MIN_VALUE,0,Long.MAX_VALUE});return n;}
    private static void proof(TestContext t,String name,Map<String,?> values){try{Files.writeString(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("v1274-static-grass-"+name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(values));}catch(Exception failure){throw new IllegalStateException(failure);}}
}
