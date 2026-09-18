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

public class MarleyArmbandItem extends daot.compat.BackportArmorItem implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private final String geoPath;
   private final String texturePath;
   public static Consumer<Consumer<Object>> clientRendererConsumer;

   public MarleyArmbandItem(ArmorMaterial material, Settings properties, String geoPath, String texturePath) {
      super(material, Type.CHESTPLATE, properties);
      this.geoPath = geoPath;
      this.texturePath = texturePath;
   }

   public String getGeoPath() {
      return this.geoPath;
   }

   public String getTexturePath() {
      return this.texturePath;
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, 20, state -> PlayState.STOP));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
