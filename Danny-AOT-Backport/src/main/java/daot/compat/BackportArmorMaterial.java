package daot.compat;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.item.ArmorItem.Type;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** The 1.20 material contract; item settings retain each item's actual durability. */
public record BackportArmorMaterial(Identifier id, Map<Type,Integer> protection,
        int enchantability, SoundEvent sound, Supplier<Ingredient> repair,
        float toughness, float knockbackResistance) implements ArmorMaterial {
    @Override public int getDurability(Type type) {
        return switch(type) { case HELMET -> 165; case CHESTPLATE -> 240;
            case LEGGINGS -> 225; case BOOTS -> 195; };
    }
    @Override public int getProtection(Type type) { return protection.getOrDefault(type, 0); }
    @Override public int getEnchantability() { return enchantability; }
    @Override public SoundEvent getEquipSound() { return sound; }
    @Override public Ingredient getRepairIngredient() { return repair.get(); }
    @Override public String getName() { return id.toString(); }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }
}
