package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Independent current-V12.6 fixtures for the separately reviewed V12.7 clones. */
public final class ImportedFollowupV127GameTests implements FabricGameTest {
    private record Case(String oldId,String primaryId,int count,int primaryOffset) {}
    private static final List<Case> CASES=List.of(new Case("90329","90328",2,1),new Case("90776","90435",1,2),
        new Case("90940","90435",2,3),new Case("90903","90899",4,7),new Case("90706","90320",2,2),
        new Case("90758","90421",2,2),new Case("90871","90870",2,1));
    private static CompositeRootBlock block(String number) {
        // Raw immutable registrations, rather than the canonical debug alias.
        var registry=DebugCatalogue.entries().stream().filter(row->row.temporaryId().equals(number)).findFirst().orElseThrow().registryId();
        return (CompositeRootBlock)Registries.BLOCK.get(registry);
    }
    private static NbtCompound payload() {
        var n=new NbtCompound();n.putInt(ImportedTypeAliases.VERSION_KEY,1);n.putString("OpaqueEvent","retained-current-v126");
        n.putUuid("LinkedInstance",UUID.randomUUID());n.putDouble("MountY",.375);n.putDouble("VerticalOffset",.125);n.putBoolean("MountEdited",true);
        var shift=new NbtList();shift.add(NbtDouble.of(.25));shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(-.375));n.put("SourceShift",shift);return n;
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="v127_exact_followup_aliases")
    public void everyCurrentVersionOneAliasKeepsNativeItemAndEveryYawFittedPhysics(TestContext t) {
        int states=0;
        for(var fixture:CASES)for(int pose=0;pose<fixture.count;pose++) {
            var oldBlock=block(fixture.oldId);var primary=block(fixture.primaryId);
            var oldItem=new ItemStack(oldBlock.asItem(),3);oldItem.setNbt(payload());
            var art=oldItem.getOrCreateSubNbt("BlockStateTag");art.putString("variant",Integer.toString(pose));art.putString("profile","alt");art.putString("rotation","5");
            var saved=oldItem.writeNbt(new NbtCompound());var nativeRead=ItemStack.fromNbt(saved);
            t.assertTrue(nativeRead.isOf(primary.asItem())&&nativeRead.getCount()==3,"Current version1 native item read becomes one primary installer "+fixture.oldId+"/"+pose);
            t.assertTrue(nativeRead.getSubNbt("BlockStateTag").getString("variant").equals(Integer.toString(fixture.primaryOffset+pose)),"Exact appended pose, no default0 fallback");
            t.assertTrue(nativeRead.getNbt().getString("OpaqueEvent").equals("retained-current-v126")&&nativeRead.getNbt().getUuid("LinkedInstance").equals(oldItem.getNbt().getUuid("LinkedInstance")),"Typed settings survive native item read");
            t.assertTrue(ItemStack.areEqual(nativeRead,ItemStack.fromNbt(nativeRead.writeNbt(new NbtCompound()))),"Current primary native item read is idempotent");
            for(int yaw=0;yaw<8;yaw++)for(var profile:CompositeRootBlock.Profile.values()) {
                BlockState old=oldBlock.getDefaultState().with(CompositeRootBlock.VARIANT,pose).with(CompositeRootBlock.ROTATION,yaw).with(CompositeRootBlock.PROFILE,profile);
                var settings=payload();var next=ImportedTypeAliases.target(old);var migrated=ImportedTypeAliases.transformPayload(old,settings);
                t.assertTrue(next.getBlock()==primary&&next.get(CompositeRootBlock.VARIANT)==fixture.primaryOffset+pose&&next.get(CompositeRootBlock.ROTATION)==yaw,"Exact type/pose/yaw followup "+fixture.oldId);
                t.assertTrue(migrated.equals(settings),"Followup retains already-current yaw-local mount, source shift and unrelated typed payload");
                t.assertTrue(CompositeSourceShift.footprint(oldBlock.spec,old,settings).equals(CompositeSourceShift.footprint(primary.spec,next,migrated)),"Every8yaw BASE/ALT physical and selectable footprint is the previous fitted footprint "+fixture.oldId+"/"+pose+"/"+yaw);
                t.assertTrue(!ImportedTypeAliases.needsMigration(next,migrated),"No second followup migration on primary appended pose");
            }
            states++;
        }
        t.assertTrue(states==15,"Every15 independent current pose fixture covered");t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="v127_exact_followup_aliases")
    public void nativeSavedCurrentAliasesReopenWithUuidSettingsAndOwnedCellsPreserved(TestContext t) {
        var world=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(5,32,5));int migratedCount=0;
        for(var fixture:CASES)for(int pose=0;pose<fixture.count;pose++) {
            var oldBlock=block(fixture.oldId);var primary=block(fixture.primaryId);
            var old=oldBlock.getDefaultState().with(CompositeRootBlock.VARIANT,pose).with(CompositeRootBlock.ROTATION,3);
            UUID uuid=UUID.randomUUID();var settings=payload();var legacy=CompositeRuntime.instance(world,root,old,uuid,settings);
            var access=new FabricCompositeWorld(world,Map.of(legacy.owner(),legacy));
            var placed=CompositeRuntime.execute(access,CompositeRuntime.core().placement(access,legacy,List.of()));
            t.assertTrue(placed.outcome()==TransactionCore.Outcome.COMMITTED,"Install actual current version1 old root fixture "+fixture.oldId+"/"+pose);
            try {
                var before=(CompositeBlockEntity)world.getBlockEntity(root);var serialized=before.createNbt();before.readNbt(serialized.copy());
                t.assertTrue(before.payload().getInt(ImportedTypeAliases.VERSION_KEY)==1,"Fixture is actual previously-current V12.6 payload, not version0");
                ImportedTypeMigration.forget(world);ImportedCollisionMigration.forget(world);ImportedTypeMigration.loadedRoot(world,legacy.owner());
                var after=(CompositeBlockEntity)world.getBlockEntity(root);var state=world.getBlockState(root);
                t.assertTrue(after.resident().instanceId().equals(uuid)&&state.getBlock()==primary&&state.get(CompositeRootBlock.VARIANT)==fixture.primaryOffset+pose,"Native saved owner keeps UUID and exact appended source pose");
                t.assertTrue(after.payload().equals(settings),"Native actual BE payload settings/mount/sourceShift survive exactly");
                var expected=CompositeRuntime.instance(world,root,state,uuid,settings);
                t.assertTrue(expected.cells().equals(legacy.cells()),"Whole physical and selection cell footprint preserved");
                for(var cell:CompositeLedger.get(world).cells())t.assertTrue(CompositeLedger.get(world).at(cell).stream().noneMatch(entry->entry.owner().equals(legacy.owner())),"No stale retired ledger owner after native replacement");
                var copied=state.getBlock().getPickStack(world,root,state);
                t.assertTrue(copied.isOf(primary.asItem()),"Native creative pick takes the single primary item");
                var once=after.createNbt();ImportedTypeMigration.loadedRoot(world,after.resident());
                t.assertTrue(after.createNbt().equals(once),"Loaded canonical appended state idempotent");migratedCount++;
            } finally {
                if(world.getBlockEntity(root) instanceof CompositeBlockEntity be&&be.resident()!=null)CompositeRuntime.remove(world,be.resident(),null,false);
            }
        }
        t.assertTrue(migratedCount==15,"Every15 actual current saved pose migrated and copied");t.complete();
    }
}
