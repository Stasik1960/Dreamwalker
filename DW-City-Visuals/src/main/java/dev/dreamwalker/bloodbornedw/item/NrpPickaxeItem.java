package dev.dreamwalker.bloodbornedw.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;

/** Netherite mining behavior with no durability or combat modifiers. */
final class NrpPickaxeItem extends PickaxeItem {
    private static final ToolMaterial MATERIAL = new ToolMaterial() {
        private final ToolMaterial netherite = ToolMaterials.NETHERITE;
        @Override public int getDurability() { return 0; }
        @Override public float getMiningSpeedMultiplier() { return netherite.getMiningSpeedMultiplier(); }
        @Override public float getAttackDamage() { return 0.0F; }
        @Override public int getMiningLevel() { return netherite.getMiningLevel(); }
        @Override public int getEnchantability() { return netherite.getEnchantability(); }
        @Override public Ingredient getRepairIngredient() { return netherite.getRepairIngredient(); }
    };

    NrpPickaxeItem() {
        super(MATERIAL,0,-2.8F,new Item.Settings().maxCount(1).fireproof());
    }

    @Override public boolean isEnchantable(ItemStack stack) { return stack.getCount() == 1; }

    @Override public boolean isSuitableFor(BlockState state) {
        return DreamWalkerMining.isDreamWalkerBlock(state) || super.isSuitableFor(state);
    }

    @Override public float getMiningSpeedMultiplier(ItemStack stack,BlockState state) {
        return DreamWalkerMining.isDreamWalkerBlock(state) ? MATERIAL.getMiningSpeedMultiplier() : super.getMiningSpeedMultiplier(stack,state);
    }

    @Override public Multimap<EntityAttribute,EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return ImmutableMultimap.of();
    }
}
