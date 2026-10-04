package dev.dreamwalker.bloodbornerp.content;

import dev.dreamwalker.bloodbornerp.RpConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Optional ordinary Minecraft healing; no alternate health system. */
public final class BloodVialItem extends Item {
 public BloodVialItem(){super(new Settings().maxCount(16));}
 @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
  ItemStack stack=player.getStackInHand(hand);
  if(!RpConfig.INSTANCE.bloodVialsEnabled||!player.isAlive()||player.isSpectator()
      ||player.getHealth()>=player.getMaxHealth()||player.getItemCooldownManager().isCoolingDown(this))
   return TypedActionResult.fail(stack);
  if(!world.isClient){
   player.heal(RpConfig.INSTANCE.bloodVialHealing);
   player.getItemCooldownManager().set(this,40);
   if(!player.getAbilities().creativeMode)stack.decrement(1);
   player.playSound(SoundEvents.ENTITY_GENERIC_DRINK,1,1);
  }
  return TypedActionResult.success(stack,world.isClient);
 }
}
