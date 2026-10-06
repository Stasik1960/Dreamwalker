package dev.dreamwalker.bloodbornerp.gametest;

import dev.dreamwalker.bloodbornerp.weapon.TrickWeaponItem;
import dev.dreamwalker.bloodbornerp.weapon.WeaponForm;
import dev.dreamwalker.bloodbornerp.weapon.WeaponRegistry;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/** Server-side checks for single-stack trick-weapon forms and their vanilla modifiers. */
public final class RpWeaponGameTests implements FabricGameTest {
 private static final List<TrickWeaponItem> WEAPONS = List.of(WeaponRegistry.SAW_CLEAVER, WeaponRegistry.SAW_SPEAR, WeaponRegistry.BOOM_HAMMER);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_weapons")
 public void formSwapPreservesStackDataAndReplacesOnlyWeaponModifiers(TestContext context) {
  for (TrickWeaponItem weapon : WEAPONS) {
   ItemStack stack = weapon.getDefaultStack();
   stack.setCustomName(Text.literal("hunter proof"));
   stack.addEnchantment(Enchantments.UNBREAKING, 1);
   stack.setDamage(17);
   NbtCompound customModifier = new NbtCompound();
   customModifier.putString("Name", "external_modifier");
   customModifier.putString("AttributeName", "generic.max_health");
   customModifier.putDouble("Amount", 2.0D);
   NbtList attributes = stack.getOrCreateNbt().getList("AttributeModifiers", NbtElement.COMPOUND_TYPE);
   attributes.add(customModifier);
   stack.getOrCreateNbt().put("AttributeModifiers", attributes);
   TrickWeaponItem.setForm(stack, WeaponForm.EXTENDED);
   context.assertTrue(stack.getMaxCount() == 1 && TrickWeaponItem.form(stack) == WeaponForm.EXTENDED
    && stack.getName().getString().equals("hunter proof") && stack.getEnchantments().size() == 1 && stack.getDamage() == 17,
    "form swap preserves one-stack, name, enchantment, and durability for " + weapon.familyId());
   context.assertTrue(hasModifier(stack, "external_modifier") && hasModifier(stack, "bloodborne_rp_weapon_damage")
    && hasModifier(stack, "bloodborne_rp_weapon_speed"), "form swap retains external modifiers and installs current vanilla attributes");
   TrickWeaponItem.setForm(stack, WeaponForm.FOLDED);
   context.assertTrue(TrickWeaponItem.form(stack) == WeaponForm.FOLDED && hasModifier(stack, "external_modifier")
    && Double.valueOf(weapon.profile(stack).attackDamage()).equals(modifierAmount(stack, "bloodborne_rp_weapon_damage")),
    "folded profile atomically replaces only its own modifiers for " + weapon.familyId());
  }
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_weapons")
 public void extendedBoomHammerHitFoldsAndStartsVanillaCooldown(TestContext context) {
  ServerWorld world = context.getWorld();
  var player = context.createMockSurvivalPlayer();
  ItemStack stack = WeaponRegistry.BOOM_HAMMER.getDefaultStack();
  TrickWeaponItem.setForm(stack, WeaponForm.EXTENDED);
  player.setStackInHand(Hand.MAIN_HAND, stack);
  var target = EntityType.PIG.create(world);
  context.assertTrue(target != null, "pig target factory exists");
  target.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(3, 2, 3)), 0.0F, 0.0F);
  world.spawnEntity(target);
  WeaponRegistry.BOOM_HAMMER.postHit(stack, target, player);
  context.assertTrue(TrickWeaponItem.form(stack) == WeaponForm.FOLDED && target.isOnFire()
   && player.getItemCooldownManager().isCoolingDown(WeaponRegistry.BOOM_HAMMER),
   "extended Boom Hammer hit uses normal fire, folds, and applies cooldown");
  target.discard(); player.discard(); context.complete();
 }

 private static boolean hasModifier(ItemStack stack, String name) { return modifierAmount(stack, name) != null; }
 private static Double modifierAmount(ItemStack stack, String name) {
  NbtList modifiers = stack.getOrCreateNbt().getList("AttributeModifiers", NbtElement.COMPOUND_TYPE);
  for (int index = 0; index < modifiers.size(); index++) if (name.equals(modifiers.getCompound(index).getString("Name"))) return modifiers.getCompound(index).getDouble("Amount");
  return null;
 }
}
