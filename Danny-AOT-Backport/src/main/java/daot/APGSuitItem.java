package daot;

import java.util.function.Consumer;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorItem.Type;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.entry.RegistryEntry;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class APGSuitItem extends daot.compat.BackportArmorItem implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<Object>> clientRendererConsumer;

   public APGSuitItem(ArmorMaterial material, Type type, Settings properties) {
      super(material, type, properties);
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         try {
            clientRendererConsumer.accept(consumer);
         } catch (Exception var3) {
         }
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, 20, state -> PlayState.CONTINUE));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public boolean shouldPlayAnimsWhileGamePaused() {
      return false;
   }
}
