package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Native common-loader checks. These tests do not claim client input/render validation. */
public final class ToolMigrationGameTests implements FabricGameTest {
    private static final Identifier CURRENT=new Identifier("bloodborne_dw:composite_builder");
    private static final Identifier LEGACY=new Identifier("bloodborne_dw:builder_tool");
    private static final List<String> V10_ORDER=List.of("ROTATE","VARIANT","PROFILE","POSE","MOUNT","LINK","CONNECTIONS","UNLINK","CANCEL","DOGS","DIAGNOSTICS","SELECT","UP","DOWN","RULE_SOURCE","RULE_TARGET","LAMP_SOURCE","LAMP_TARGET");

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="tool_fix_migration")
    public void oldOrdinalsAllLoadToSameActionWithoutMutatingSourceNbt(TestContext c){
        for(int ordinal=0;ordinal<V10_ORDER.size();ordinal++){
            NbtCompound source=legacy(ordinal);NbtCompound before=source.copy();
            ItemStack loaded=ItemStack.fromNbt(source);
            canonical(c,loaded,"ItemStack.fromNbt ordinal "+ordinal);
            c.assertTrue(BuildingTool.action(loaded).name().equals(V10_ORDER.get(ordinal)),"frozen V10 action meaning survived: "+ordinal);
            c.assertTrue(source.equals(before),"migration must leave caller/source save NBT unmodified");
            c.assertTrue(loaded.getCount()==3,"count survives migration rather than clamping saved items");
            c.assertTrue(loaded.getNbt().getDouble("BuilderStep")==.25&&loaded.getNbt().getCompound("OpaqueTypedData").equals(before.getCompound("tag").getCompound("OpaqueTypedData")),"all compatible typed settings survive");
            c.assertTrue(loaded.getNbt().getInt("BuilderAction")==ordinal&&loaded.getNbt().getString("BuilderActionName").equals(V10_ORDER.get(ordinal)),"numeric compatibility value remains and stable action name is added");
            ItemStack roundtrip=ItemStack.fromNbt(loaded.writeNbt(new NbtCompound()));
            c.assertTrue(roundtrip.writeNbt(new NbtCompound()).equals(loaded.writeNbt(new NbtCompound())),"second load is idempotent");
        }
        c.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="tool_fix_migration")
    public void savedInventoryChestAndDroppedItemUseTheRealCommonLoader(TestContext c){
        var player=c.createMockCreativeServerPlayerInWorld();
        NbtList inventory=new NbtList();NbtCompound inventoryStack=legacy(2);inventoryStack.putByte("Slot",(byte)0);inventory.add(inventoryStack);
        player.getInventory().readNbt(inventory);
        canonical(c,player.getInventory().getStack(0),"PlayerInventory.readNbt");
        c.assertTrue(BuildingTool.action(player.getInventory().getStack(0))==BuildingTool.Action.PROFILE,"inventory mode survived");

        BlockPos pos=c.getAbsolutePos(new BlockPos(1,1,1));
        ChestBlockEntity chest=new ChestBlockEntity(pos,Blocks.CHEST.getDefaultState());chest.setWorld(c.getWorld());
        NbtCompound chestNbt=new NbtCompound();NbtList contents=new NbtList();NbtCompound chestStack=legacy(9);chestStack.putByte("Slot",(byte)4);contents.add(chestStack);chestNbt.put("Items",contents);chest.readNbt(chestNbt);
        canonical(c,chest.getStack(4),"ChestBlockEntity.readNbt");
        c.assertTrue(BuildingTool.action(chest.getStack(4))==BuildingTool.Action.DOGS,"chest mode survived");
        NbtCompound savedChest=chest.createNbt();ChestBlockEntity reloadedChest=new ChestBlockEntity(pos,Blocks.CHEST.getDefaultState());reloadedChest.setWorld(c.getWorld());reloadedChest.readNbt(savedChest);
        c.assertTrue(reloadedChest.getStack(4).writeNbt(new NbtCompound()).equals(chest.getStack(4).writeNbt(new NbtCompound())),"new chest save/load preserves migrated state");

        ItemEntity dropped=new ItemEntity(c.getWorld(),pos.getX(),pos.getY(),pos.getZ(),Registries.ITEM.get(CURRENT).getDefaultStack());
        NbtCompound dropNbt=dropped.writeNbt(new NbtCompound());dropNbt.put("Item",legacy(3));dropped.readNbt(dropNbt);
        canonical(c,dropped.getStack(),"ItemEntity.readNbt");
        c.assertTrue(BuildingTool.action(dropped.getStack())==BuildingTool.Action.POSE,"drop mode survived");
        ItemEntity secondDrop=new ItemEntity(c.getWorld(),pos.getX(),pos.getY(),pos.getZ(),ItemStack.EMPTY);secondDrop.readNbt(dropped.writeNbt(new NbtCompound()));
        c.assertTrue(secondDrop.getStack().writeNbt(new NbtCompound()).equals(dropped.getStack().writeNbt(new NbtCompound())),"drop save/load is stable");
        player.discard();c.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="tool_fix_migration")
    public void only90009IsOfferedBut90008RegistryAndNumberRemainReadable(TestContext c){
        c.assertTrue(Registries.ITEM.containsId(LEGACY)&&Registries.ITEM.containsId(CURRENT),"legacy registry adapter and current tool both exist for compatibility");
        long offered=UnifiedCreativeCatalogue.canonicalItems().stream().filter(stack->stack.getItem() instanceof BuildingTool).count();
        c.assertTrue(offered==1,"Creative offers exactly one building tool");
        c.assertTrue(UnifiedCreativeCatalogue.canonicalItems().stream().noneMatch(stack->Registries.ITEM.getId(stack.getItem()).equals(LEGACY)),"Creative does not offer the legacy adapter");
        c.assertTrue(DebugCatalogue.entries().stream().anyMatch(entry->entry.temporaryId().equals("90008")&&entry.registryId().equals(LEGACY)),"retired 90008 remains reserved and has not been reassigned");
        canonical(c,new ItemStack(Registries.ITEM.get(LEGACY)),"historical /give constructor");
        NbtCompound named=legacy(0);named.getCompound("tag").putString("BuilderActionName","DOGS");
        c.assertTrue(BuildingTool.action(ItemStack.fromNbt(named))==BuildingTool.Action.DOGS,"stable named action takes priority over old integer ordinal");
        c.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="tool_fix_contents")
    public void dogsOnEveryCageAreIndependentAndPersistWithUuid(TestContext c){
        for(String asset:List.of("cage_obj_1","cage_obj_2","cage_obj_3")){
            RpObjectEntity a=object(c,asset),b=object(c,asset);UUID aId=a.getUuid(),bId=b.getUuid();
            c.assertTrue(a.supportsDogVisibility()&&a.dogsVisible()&&b.dogsVisible(),"both default-visible cages: "+asset);
            c.assertTrue(a.setDogsVisible(false)&&!a.dogsVisible()&&b.dogsVisible(),"hide changes exactly one instance: "+asset);
            RpObjectEntity restored=object(c,asset);restored.readNbt(a.writeNbt(new NbtCompound()));
            c.assertTrue(!restored.dogsVisible()&&restored.getUuid().equals(aId)&&b.getUuid().equals(bId),"native entity NBT retains dogs and identity: "+asset);
            c.assertTrue(restored.setDogsVisible(true)&&restored.dogsVisible()&&!a.dogsVisible()&&b.dogsVisible(),"restore remains instance-local: "+asset);
        }
        c.assertTrue(!object(c,"chair").supportsDogVisibility(),"static chair has no dog capability");c.complete();
    }

    private static NbtCompound legacy(int action){
        NbtCompound root=new NbtCompound();root.putString("id",LEGACY.toString());root.putByte("Count",(byte)3);
        NbtCompound tag=new NbtCompound();tag.putInt("BuilderAction",action);tag.putDouble("BuilderStep",.25);
        NbtCompound opaque=new NbtCompound();opaque.putIntArray("intArray",new int[]{7,-3,991});opaque.putLong("long",9007199254740993L);opaque.putDouble("exact",.0625);opaque.putString("text","совместимые настройки");tag.put("OpaqueTypedData",opaque);
        root.put("tag",tag);return root;
    }
    private static void canonical(TestContext c,ItemStack stack,String loader){c.assertTrue(!stack.isEmpty()&&Registries.ITEM.getId(stack.getItem()).equals(CURRENT),loader+" resolves to sole 90009");}
    private static RpObjectEntity object(TestContext c,String asset){var entity=Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp",asset)).create(c.getWorld());if(!(entity instanceof RpObjectEntity rp))throw new AssertionError("Missing RP factory: "+asset);return rp;}
}
