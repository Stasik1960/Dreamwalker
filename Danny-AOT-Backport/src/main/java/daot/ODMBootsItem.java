package daot;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ArmorItem.Type;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ODMBootsItem extends daot.compat.BackportArmorItem implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   public static final float FALL_DAMAGE_REDUCTION = 0.7F;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<RenderProvider>> clientRendererConsumer;

   public ODMBootsItem(ArmorMaterial material, Type type, Settings properties) {
      super(material, type, properties);
      SingletonGeoAnimatable.registerSyncedAnimatable(this);
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      tooltip.add(Text.literal("70% Fall Damage Reduction").formatted(Formatting.GREEN));
      super.appendTooltip(stack, context, tooltip, type);
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "odm_boots_controller", 0, state -> PlayState.STOP));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
