package dev.dreamwalker.bloodbornedw.architecture;

import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Lazy saved-stack migration. No inventory, entity or chunk scan is required. */
public final class BuilderToolMigration {
    public static final Identifier LEGACY_ID=new Identifier("bloodborne_dw","builder_tool");
    public static final Identifier CURRENT_ID=new Identifier("bloodborne_dw","composite_builder");
    public static final int VERSION=1;
    private BuilderToolMigration() {}

    /** Called at ItemStack.fromNbt, common to inventory/chest/entity and nested container loaders. */
    public static NbtCompound migrateNbt(NbtCompound source) {
        if(!LEGACY_ID.toString().equals(source.getString("id")))return source;
        NbtCompound result=source.copy();result.putString("id",CURRENT_ID.toString());
        NbtCompound tag=result.getCompound("tag");tag.putInt("BuilderToolMigrationVersion",VERSION);
        if(!tag.contains("BuilderActionName",NbtElement.STRING_TYPE))
            tag.putString("BuilderActionName",tag.contains("BuilderAction",NbtElement.NUMBER_TYPE)?BuildingTool.legacyAction(tag.getInt("BuilderAction")).name():BuildingTool.Action.SELECT.name());
        result.put("tag",tag);return result;
    }

    /** Newly created historical /give stacks also resolve to the sole offered tool after registration. */
    public static ItemConvertible canonicalItem(ItemConvertible item) {
        if(item==null||!LEGACY_ID.equals(Registries.ITEM.getId(item.asItem()))||!Registries.ITEM.containsId(CURRENT_ID))return item;
        return Registries.ITEM.get(CURRENT_ID);
    }

    /** Use-time fallback for a live old stack created before canonical registration. */
    public static ItemStack normalize(ItemStack stack) {
        if(stack.isEmpty()||!LEGACY_ID.equals(Registries.ITEM.getId(stack.getItem()))||!Registries.ITEM.containsId(CURRENT_ID))return stack;
        return ItemStack.fromNbt(migrateNbt(stack.writeNbt(new NbtCompound())));
    }
}
