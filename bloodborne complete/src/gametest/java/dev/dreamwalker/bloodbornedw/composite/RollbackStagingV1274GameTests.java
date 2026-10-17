package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.GsonBuilder;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Real native typed NBT, opaque source-ladder provenance, and partial adapter faults. */
public final class RollbackStagingV1274GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="packed_owner_nbt_v1274",tickLimit=200)
    public void packedOwnerNbtReadsLegacyAndPreservesTypedBeRoundtrip(TestContext t) {
        Fixture f=new Fixture(t);
        try {
            var box=new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(.125,.0625,.0625,.875,.9375,1.0);
            var shape=new Footprint(List.of(box),List.of(box));
            var entry=new CompositeData.Contribution(f.owner,CompositeData.data(f.world.getBlockState(f.rootPos),typed()),shape);
            var entries=List.of(entry);var expected=CompositeData.contributions(entries);
            f.rootEntity().set(f.owner,entries,typed());
            var legacy=f.rootEntity().createNbt();legacy.remove(CompositeOwnerNbt.KEY);legacy.put("owners",expected.copy());
            f.rootEntity().readNbt(legacy);
            t.assertTrue(f.rootEntity().contributions().equals(entries),"Legacy owner list loads with exact UUID, typed root data, and double shapes");
            var packed=f.rootEntity().createNbt();
            t.assertTrue(packed.contains(CompositeOwnerNbt.KEY,NbtElement.BYTE_ARRAY_TYPE)&&!packed.contains("owners"),"New BE save uses only the uncompressed packed owner envelope");
            t.assertTrue(CompositeOwnerNbt.read(packed).equals(expected),"Packed owner envelope preserves the complete typed legacy list");
            var oldOther=legacy.copy();oldOther.remove("owners");var newOther=packed.copy();newOther.remove(CompositeOwnerNbt.KEY);
            t.assertTrue(oldOther.equals(newOther),"Packing changes no payload, resident, source provenance, or other BE field");
            f.rootEntity().readNbt(packed);
            t.assertTrue(f.rootEntity().createNbt().equals(packed)&&f.rootEntity().contributions().equals(entries),"Real attached native BE packed save/read/save is exact");
            assertAllTypes(t,CompositeData.nbt(f.rootEntity().contributions().get(0).rootData().blockEntityNbt()));
            t.assertTrue(f.rootEntity().contributions().get(0).shape().equals(shape),"Fractional DOUBLE collision and selection coordinates remain exact");

            var mixed=packed.copy();mixed.put("owners",expected.copy());
            assertOwnerNbtRejected(t,()->f.rootEntity().readNbt(mixed),"Mixed old/new BE data fails closed");
            t.assertTrue(f.rootEntity().createNbt().equals(packed),"Rejected mixed encoding changes none of the attached BE fields");
            var wrongOuter=new NbtCompound();wrongOuter.putString(CompositeOwnerNbt.KEY,"not bytes");
            assertOwnerNbtRejected(t,()->CompositeOwnerNbt.read(wrongOuter),"Wrong outer NBT type is rejected");
            byte[] bytes=packed.getByteArray(CompositeOwnerNbt.KEY);
            var truncated=new NbtCompound();truncated.putByteArray(CompositeOwnerNbt.KEY,Arrays.copyOf(bytes,bytes.length-1));
            assertOwnerNbtRejected(t,()->CompositeOwnerNbt.read(truncated),"Truncated packed NBT is rejected");
            var trailing=new NbtCompound();trailing.putByteArray(CompositeOwnerNbt.KEY,Arrays.copyOf(bytes,bytes.length+1));
            assertOwnerNbtRejected(t,()->CompositeOwnerNbt.read(trailing),"Trailing packed bytes are rejected");
            var inner=new NbtCompound();inner.put("owners",expected.copy());inner.putInt("extra",1);
            var extra=new NbtCompound();extra.putByteArray(CompositeOwnerNbt.KEY,CompositeData.bytes(inner));
            assertOwnerNbtRejected(t,()->CompositeOwnerNbt.read(extra),"Unknown inner fields are rejected");
            inner=new NbtCompound();inner.putInt("owners",1);
            var wrongList=new NbtCompound();wrongList.putByteArray(CompositeOwnerNbt.KEY,CompositeData.bytes(inner));
            assertOwnerNbtRejected(t,()->CompositeOwnerNbt.read(wrongList),"Non-list owners are rejected");
            var ints=new NbtList();ints.add(NbtInt.of(1));inner=new NbtCompound();inner.put("owners",ints);
            var wrongElement=new NbtCompound();wrongElement.putByteArray(CompositeOwnerNbt.KEY,CompositeData.bytes(inner));
            assertOwnerNbtRejected(t,()->CompositeOwnerNbt.read(wrongElement),"Wrong owner-list element type is rejected");
            var oversized=new NbtCompound();oversized.putByteArray(CompositeOwnerNbt.KEY,new byte[CompositeOwnerNbt.MAX_BYTES+1]);
            assertOwnerNbtRejected(t,()->CompositeOwnerNbt.read(oversized),"Oversized packed payload is rejected before decode");
            var empty=new NbtCompound();CompositeOwnerNbt.write(empty,new NbtList());
            t.assertTrue(empty.contains(CompositeOwnerNbt.KEY,NbtElement.BYTE_ARRAY_TYPE)&&!empty.contains("owners")&&CompositeOwnerNbt.read(empty).isEmpty(),"Empty owner lists use the same packed schema");
            proof(t,"packed-owners",Map.of("status","PASS_NATIVE_LEGACY_AND_PACKED_OWNER_NBT_TYPED_BE_ROUNDTRIP","allNbtTypes",true,"exactDoubleShapes",true,"otherBeFieldsUnchanged",true,"rejectedMalformedCases",8,"emptyPacked",true));
            t.complete();
        } finally { f.close(); }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="rollback_staging_v1274",tickLimit=200)
    public void currentReadsAndUnchangedCleanupDoNotStageRollback(TestContext t) {
        Fixture f = new Fixture(t);
        try {
            ProbeAccess access = f.access();
            for (int i=0;i<4;i++) {
                t.assertTrue(TransactionCore.validateCarrier(access,f.back).live().equals(List.of(f.owner)),"Loaded source owner remains live");
                var prepared=CompositeRuntime.core().pruneStale(access,f.back);
                t.assertTrue(prepared.accepted()&&prepared.plan().writes().isEmpty(),"Live cleanup is an unchanged plan");
                t.assertTrue(CompositeRuntime.execute(access,prepared).outcome()==TransactionCore.Outcome.COMMITTED,"Unchanged cleanup completes");
            }
            t.assertTrue(access.captures==0&&access.writes==0&&access.publications==0&&access.originals().isEmpty(),"Read-only cleanup creates no rollback snapshots or mutations");
            var expected=access.read(f.root);var desired=f.edited(expected);
            var prepared=plan(access,Map.of(f.root,expected),Map.of(f.root,desired));
            var changed=f.rootEntity().payload();changed.putInt("FreshLogicalMutation",42);
            f.rootEntity().set(f.owner,f.rootEntity().contributions(),changed);
            t.assertTrue(!access.read(f.root).equals(expected)&&access.read(f.root).equals(access.read(f.root)),"Repeated reads of the same adapter see the current typed payload");
            t.assertTrue(CompositeRuntime.execute(access,prepared).outcome()==TransactionCore.Outcome.REJECTED&&access.captures==0&&access.writes==0,"Stale payload is rejected before rollback preparation");
            var foreign=access.read(f.chest);f.chestEntity().setStack(0,new ItemStack(Items.DIAMOND,3));
            t.assertTrue(!foreign.equals(access.read(f.chest)),"Foreign inventory checks also read current full NBT");
            proof(t,"reads",Map.of("status","PASS_NATIVE_FRESH_READS_NOOP_ZERO_ROLLBACK","captureCalls",access.captures,"writes",access.writes));
            t.complete();
        } finally { f.close(); }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="rollback_staging_v1274",tickLimit=200)
    public void partialWriteRestoresFullTypedNbtAndLatestOpaqueSourceProvenance(TestContext t) {
        Fixture f=new Fixture(t);
        try {
            ProbeAccess access=f.access();var root=access.read(f.root);var chest=access.read(f.chest);
            var prepared=plan(access,Map.of(f.root,root,f.chest,chest,f.back,access.read(f.back)),
                    Map.of(f.root,f.edited(root),f.chest,chest.withGuests(List.of())));
            // Private provenance is intentionally outside the logical payload; capture it at execute time.
            var latest=typed();latest.putLong("ChangedAfterPreflight",Long.MIN_VALUE+17);
            f.rootEntity().set(f.owner.instanceId(),f.rootPos,f.backPos,latest);
            var before=f.snapshots();access.expectedFull=before;access.failWriteAt=2;access.damageForeignOnFailure=true;
            var result=CompositeRuntime.execute(access,prepared);
            t.assertTrue(result.outcome()==TransactionCore.Outcome.ROLLED_BACK,"A false return after the second actual write restores the transaction: "+result);
            t.assertTrue(access.captures==1&&access.captureKeys.equals(Set.of(f.root,f.chest))&&access.writes==2,"Exactly the two changed cells were staged once; read-only backing excluded");
            t.assertTrue(access.restored.equals(List.of(f.chest,f.root)),"Partially modified cell and earlier cell restore in reverse order");
            f.assertSnapshots(before);
            t.assertTrue(f.rootEntity().provenance().equals(latest),"Rollback restores latest opaque Original provenance, not a preflight copy");
            t.assertTrue(CompositeLedger.get(f.world).at(f.root).equals(f.rootEntries),"BE-only resident contribution fallback is restored exactly");
            t.assertTrue(access.originals().isEmpty()&&access.releases==1,"Full rollback NBT is released after restoration");
            var recovered=f.rootEntity().createNbt();assertAllTypes(t,recovered.getCompound("Original"));
            t.assertTrue(recovered.contains(CompositeOwnerNbt.KEY,NbtElement.BYTE_ARRAY_TYPE)&&!recovered.contains("owners")&&CompositeOwnerNbt.read(recovered).equals(CompositeData.contributions(f.rootEntries)),"Partial-failure rollback restores the packed typed owner list exactly");
            t.assertTrue(CompositeData.nbt(CompositeData.bytes(recovered)).equals(recovered),"Canonical binary codec preserves every NBT type after restoration");
            proof(t,"partial-failure",Map.of("status","PASS_NATIVE_PARTIAL_WRITE_EXACT_TYPED_ROLLBACK","changedCells",2,"captureCalls",access.captures,"restoreOrder",access.restored.toString(),"allNbtTypes",true,"latestOpaqueProvenance",true,"beOnlyContributionsFallback",true,"rollbackReleased",true));
            t.complete();
        } finally { f.close(); }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="rollback_staging_v1274",tickLimit=200)
    public void captureFailureAfterBothCellsLeavesNoMutationAndReleasesSnapshots(TestContext t) {
        Fixture f=new Fixture(t);
        try {
            ProbeAccess access=f.access();var root=access.read(f.root);var chest=access.read(f.chest);
            var prepared=plan(access,Map.of(f.root,root,f.chest,chest),Map.of(f.root,f.edited(root),f.chest,chest.withGuests(List.of())));
            var before=f.snapshots();access.expectedFull=before;access.failAfterCapture=true;
            var result=CompositeRuntime.execute(access,prepared);
            t.assertTrue(result.outcome()==TransactionCore.Outcome.REJECTED&&result.reason().contains("rollback_capture_failed"),"Staging failure is an explicit rejection before mutation");
            t.assertTrue(access.captureKeys.size()==2&&access.writes==0&&access.restored.isEmpty()&&access.publications==0,"Even failure after both captures causes no write, restore, or publication");
            f.assertSnapshots(before);t.assertTrue(access.originals().isEmpty()&&access.releases==1,"Incomplete transaction does not retain full NBT");
            proof(t,"capture-failure",Map.of("status","PASS_NATIVE_CAPTURE_FAILURE_ZERO_MUTATIONS","capturesCompleted",2,"writes",access.writes,"rollbackReleased",true));
            t.complete();
        } finally { f.close(); }
    }

    private static TransactionCore.Preparation plan(ProbeAccess access,Map<Cell,CellSnapshot> expected,Map<Cell,CellSnapshot> writes) {
        return new TransactionCore.Preparation(new TransactionCore.Plan(access.dimensionKey(),expected,writes,Map.of()),null);
    }
    private record NativeSnapshot(BlockState state,NbtCompound full,List<CompositeData.Contribution> effective) {}
    private static final class Fixture implements AutoCloseable {
        final TestContext t;final ServerWorld world;final BlockPos rootPos,backPos,chestPos;final Cell root,back,chest;final Owner owner;
        final Map<Cell,Footprint> geometry;final List<CompositeData.Contribution> rootEntries,backEntries,chestEntries;
        Fixture(TestContext t) {
            this.t=t;world=t.getWorld();rootPos=t.getAbsolutePos(new BlockPos(2,3,2));backPos=rootPos.east();chestPos=rootPos.south(2);
            root=CompositeData.cell(rootPos);back=CompositeData.cell(backPos);chest=CompositeData.cell(chestPos);
            var state=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.SOURCE_CLONE,true).with(PrototypeLadderBlock.FACING,Direction.WEST);
            owner=new Owner(UUID.randomUUID(),net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString(),root);
            SourceLadderRuntime.runInternalWrite(()->{world.setBlockState(backPos,SourceLadderRuntime.BACKING.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);world.setBlockState(rootPos,state,Block.FORCE_STATE|Block.SKIP_DROPS);world.setBlockState(chestPos,Blocks.CHEST.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);});
            rootEntity().set(owner.instanceId(),rootPos,backPos,typed());backEntity().set(owner.instanceId(),rootPos,backPos,typed());
            var payload=typed();payload.putBoolean("MountEdited",true);
            var initial=CompositeRuntime.instance(world,rootPos,state,owner.instanceId(),rootEntity().payload());
            var extended=new TreeMap<Cell,Footprint>(initial.cells());extended.put(chest.subtract(root),Footprint.EMPTY);geometry=Map.copyOf(extended);
            var data=CompositeData.data(state,payload);rootEntries=List.of(new CompositeData.Contribution(owner,data,geometry.get(Cell.ORIGIN)));
            backEntries=List.of(new CompositeData.Contribution(owner,data,geometry.get(back.subtract(root))));
            chestEntries=List.of(new CompositeData.Contribution(owner,data,Footprint.EMPTY));
            rootEntity().set(owner,rootEntries,payload);backEntity().set(null,backEntries,typed());
            // Deliberately preserve BE-only owner metadata on the root and backing.
            CompositeLedger.get(world).put(root,List.of());CompositeLedger.get(world).put(back,List.of());CompositeLedger.get(world).put(chest,chestEntries);
            ItemStack item=new ItemStack(Items.STONE,7);item.setNbt(typed());chestEntity().setStack(0,item);
            t.assertTrue(geometry.containsKey(back.subtract(root)),"Native source footprint contains its physical backing role");
        }
        SourceLadderBlockEntity rootEntity(){return (SourceLadderBlockEntity)world.getBlockEntity(rootPos);}
        SourceLadderBlockEntity backEntity(){return (SourceLadderBlockEntity)world.getBlockEntity(backPos);}
        ChestBlockEntity chestEntity(){return (ChestBlockEntity)world.getBlockEntity(chestPos);}
        ProbeAccess access(){return new ProbeAccess(this);}
        CellSnapshot edited(CellSnapshot old){var payload=CompositeData.nbt(old.data().blockEntityNbt());payload.putInt("TransactionalEdit",123);return new CellSnapshot(old.kind(),old.data().withNbt(CompositeData.bytes(payload)),old.resident(),old.guests());}
        Map<Cell,NativeSnapshot> snapshots(){var result=new TreeMap<Cell,NativeSnapshot>();for(var cell:List.of(root,back,chest)){var pos=CompositeData.pos(cell);var entity=world.getBlockEntity(pos);var effective=CompositeLedger.get(world).at(cell);if(effective.isEmpty()&&entity instanceof CompositeBlockEntity composite)effective=composite.contributions();result.put(cell,new NativeSnapshot(world.getBlockState(pos),entity.createNbt(),List.copyOf(effective)));}return result;}
        void assertSnapshots(Map<Cell,NativeSnapshot> before){var after=snapshots();for(var entry:before.entrySet()){var value=after.get(entry.getKey());t.assertTrue(entry.getValue().equals(value),"Exact native state, typed full NBT, and effective owner contributions restored at "+entry.getKey());}}
        @Override public void close(){SourceLadderRuntime.runInternalWrite(()->{for(var pos:List.of(rootPos,backPos,chestPos)){CompositeLedger.get(world).put(CompositeData.cell(pos),List.of());world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);}});}
    }
    private static final class ProbeAccess extends FabricCompositeWorld {
        final Fixture f;int captures,writes,publications,releases,failWriteAt=-1;boolean failAfterCapture,damageForeignOnFailure;Set<Cell> captureKeys=Set.of();Map<Cell,NativeSnapshot> expectedFull=Map.of();final List<Cell> restored=new ArrayList<>();
        ProbeAccess(Fixture f){super(f.world,Map.of());this.f=f;}
        @Override public Optional<ObjectInstance> describeRoot(Owner owner,CellSnapshot snapshot){return owner.equals(f.owner)&&owner.equals(snapshot.resident())?Optional.of(new ObjectInstance(owner,snapshot.data(),f.geometry)):super.describeRoot(owner,snapshot);}
        @Override public void prepareRollback(Map<Cell,CellSnapshot> expected){f.t.assertTrue(writes==0,"Capture starts before first write");captures++;captureKeys=Set.copyOf(expected.keySet());super.prepareRollback(expected);f.t.assertTrue(originals().keySet().equals(captureKeys),"Full snapshots exist for exactly all changed cells before any mutation");if(failAfterCapture)throw new IllegalStateException("injected_after_second_native_capture");}
        @Override public boolean writeSilently(Cell cell,CellSnapshot next){if(writes==0){f.t.assertTrue(originals().keySet().equals(captureKeys),"Every later-cell snapshot exists before first write");for(var captured:expectedFull.entrySet())if(captureKeys.contains(captured.getKey()))f.t.assertTrue(f.world.getBlockEntity(CompositeData.pos(captured.getKey())).createNbt().equals(captured.getValue().full()),"Every cell still has its exact original NBT before first mutation");}writes++;boolean applied=super.writeSilently(cell,next);if(writes==failWriteAt){if(damageForeignOnFailure&&cell.equals(f.chest))f.chestEntity().setStack(0,ItemStack.EMPTY);return false;}return applied;}
        @Override public boolean restoreSilently(Cell cell,CellSnapshot previous){restored.add(cell);return super.restoreSilently(cell,previous);}
        @Override public void publishCommitted(List<Cell> cells){publications++;super.publishCommitted(cells);}
        @Override public void releaseRollback(){super.releaseRollback();releases++;}
        Map<?,?> originals(){try{var field=FabricCompositeWorld.class.getDeclaredField("originals");field.setAccessible(true);return (Map<?,?>)field.get(this);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    }
    private static NbtCompound typed(){var tag=new NbtCompound();tag.putByte("Byte",(byte)-7);tag.putShort("Short",(short)32001);tag.putInt("Int",Integer.MIN_VALUE+5);tag.putLong("Long",Long.MAX_VALUE-9);tag.putFloat("Float",1.25f);tag.putDouble("Double",-2.125);tag.putByteArray("ByteArray",new byte[]{0,-1,2});tag.putString("String","typed rollback \u041d\u0411\u0422");var list=new NbtList();list.add(NbtShort.of((short)4));list.add(NbtShort.of((short)-5));tag.put("List",list);var nested=new NbtCompound();nested.putBoolean("BooleanByte",true);nested.putUuid("Uuid",new UUID(11,13));tag.put("Compound",nested);tag.putIntArray("IntArray",new int[]{3,-4,Integer.MAX_VALUE});tag.putLongArray("LongArray",new long[]{7,-8,Long.MIN_VALUE});return tag;}
    private static void assertAllTypes(TestContext t,NbtCompound tag){String[] names={"Byte","Short","Int","Long","Float","Double","ByteArray","String","List","Compound","IntArray","LongArray"};for(int i=0;i<names.length;i++)t.assertTrue(tag.contains(names[i],i+1),"Exact native NBT tag type "+names[i]);t.assertTrue(tag.getList("List",NbtElement.SHORT_TYPE).size()==2&&tag.getCompound("Compound").containsUuid("Uuid"),"Nested/list element types remain exact");}
    private static void assertOwnerNbtRejected(TestContext t,Runnable read,String reason){boolean rejected=false;try{read.run();}catch(RuntimeException expected){rejected=true;}t.assertTrue(rejected,reason);}
    private static void proof(TestContext t,String name,Map<String,Object> values){try{Files.writeString(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("v1274-rollback-staging-"+name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(values));}catch(Exception failure){throw new IllegalStateException(failure);}}
}
