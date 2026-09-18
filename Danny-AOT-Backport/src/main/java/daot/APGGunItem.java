package daot;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import daot.compat.components.DataComponentTypes;
import daot.compat.components.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.Animation.LoopType;
import software.bernie.geckolib.util.GeckoLibUtil;

public class APGGunItem extends Item implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<RenderProvider>> clientRendererConsumer;
   private static final String LOADED_KEY = "ApgLoaded";

   public APGGunItem() {
      super(new Settings().maxCount(1));
      SingletonGeoAnimatable.registerSyncedAnimatable(this);
   }

   public static boolean isLoaded(ItemStack stack) {
      if (!(stack.getItem() instanceof APGGunItem)) {
         return false;
      } else {
         NbtComponent data = daot.compat.components.Components.get(stack, DataComponentTypes.CUSTOM_DATA);
         if (data == null) {
            return true;
         } else {
            NbtCompound tag = data.copyNbt();
            return !tag.contains("ApgLoaded") ? true : tag.getBoolean("ApgLoaded");
         }
      }
   }

   public static void setLoaded(ItemStack stack, boolean loaded) {
      if (stack.getItem() instanceof APGGunItem) {
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, d -> d.apply(tag -> tag.putBoolean("ApgLoaded", loaded)));
      }
   }

   public static void triggerAnim(PlayerEntity player, ItemStack stack, String animName) {
      if (stack.getItem() instanceof APGGunItem gun) {
         if (player.getWorld() instanceof ServerWorld serverLevel) {
            gun.triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", animName);
         }
      }
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      tooltip.add(Text.literal("Anti-Personnel Gun").formatted(Formatting.DARK_GRAY));
      if (isLoaded(stack)) {
         tooltip.add(Text.literal("Loaded").formatted(Formatting.GREEN));
         tooltip.add(Text.literal("Press R to unload cartridge").formatted(Formatting.YELLOW));
      } else {
         tooltip.add(Text.literal("Empty").formatted(Formatting.RED));
         tooltip.add(Text.literal("Press R to load a cartridge").formatted(Formatting.YELLOW));
      }

      tooltip.add(Text.literal("Equip with APG ODM gear to use").formatted(Formatting.DARK_GRAY));
      super.appendTooltip(stack, context, tooltip, type);
   }

   @Override
   public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
      return false;
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(
         new AnimationController(this, "controller", 0, state -> PlayState.STOP)
            .triggerableAnim("fire", RawAnimation.begin().then("fire", LoopType.HOLD_ON_LAST_FRAME))
            .triggerableAnim("load", RawAnimation.begin().then("load", LoopType.HOLD_ON_LAST_FRAME))
            .triggerableAnim("unload", RawAnimation.begin().then("unload", LoopType.HOLD_ON_LAST_FRAME))
      );
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
