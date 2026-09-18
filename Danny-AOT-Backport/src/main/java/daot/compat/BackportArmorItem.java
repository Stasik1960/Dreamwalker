package daot.compat;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;

/** 1.21 does not assign durability merely because an item is armor. */
public class BackportArmorItem extends ArmorItem {
    private final ArmorMaterial originalMaterial;

    public BackportArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(new MaterialWithoutDefaultDurability(material), type, settings);
        originalMaterial = material;
    }

    @Override public ArmorMaterial getMaterial() { return originalMaterial; }

    private record MaterialWithoutDefaultDurability(ArmorMaterial delegate) implements ArmorMaterial {
        @Override public int getDurability(Type type) { return 0; }
        @Override public int getProtection(Type type) { return delegate.getProtection(type); }
        @Override public int getEnchantability() { return delegate.getEnchantability(); }
        @Override public SoundEvent getEquipSound() { return delegate.getEquipSound(); }
        @Override public Ingredient getRepairIngredient() { return delegate.getRepairIngredient(); }
        @Override public String getName() { return delegate.getName(); }
        @Override public float getToughness() { return delegate.getToughness(); }
        @Override public float getKnockbackResistance() { return delegate.getKnockbackResistance(); }
    }
}
