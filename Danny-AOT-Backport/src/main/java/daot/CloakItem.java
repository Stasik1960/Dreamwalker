package daot;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CloakItem extends daot.compat.BackportArmorItem implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<Object>> clientRendererConsumer;
   public static Supplier<String> hoodKeyNameSupplier = () -> "H";
   public static volatile boolean renderAsTdxm = false;
   public static volatile boolean renderAsBerserk = false;
   public static volatile boolean berserkInMotion = false;
   public static volatile boolean renderAsTitan = false;
   public static volatile boolean titanInMotion = false;
   private static final RawAnimation TDXM_IDLE = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation BERSERK_IDLE = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation BERSERK_MOVE = RawAnimation.begin().thenLoop("move");

   public CloakItem(ArmorMaterial material, Settings properties) {
      super(material, Type.HELMET, properties);
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, 20, state -> {
         if (renderAsTdxm) {
            state.setAnimation(TDXM_IDLE);
            return PlayState.CONTINUE;
         } else if (renderAsBerserk) {
            state.setAnimation(berserkInMotion ? BERSERK_MOVE : BERSERK_IDLE);
            return PlayState.CONTINUE;
         } else if (renderAsTitan) {
            state.setAnimation(titanInMotion ? BERSERK_MOVE : BERSERK_IDLE);
            return PlayState.CONTINUE;
         } else {
            return PlayState.STOP;
         }
      }));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   public Text getName(ItemStack stack) {
      return (Text)(stack.getItem() == DannysAot.ROYAL_CLOAK
         ? Text.translatable(this.getTranslationKey(stack)).formatted(Formatting.GOLD).formatted(Formatting.BOLD)
         : super.getName(stack));
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      super.appendTooltip(stack, context, tooltip, type);
      if (stack.getItem() == DannysAot.GOLD_CLOAK) {
         tooltip.add(Text.literal("As Ymir Gave, So Shall We.").formatted(Formatting.GOLD, Formatting.BOLD));
      }

      String skin = daot.compat.components.Components.get(stack, DannysAot.CLOAK_SKIN);
      if (skin != null) {
         tooltip.add(Text.literal("Skin: " + skin).formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC));
      }

      String keyName = hoodKeyNameSupplier.get();
      tooltip.add(Text.literal("Press [" + keyName + "] to toggle hood. Disables your nametag.").formatted(Formatting.DARK_GRAY));
      tooltip.add(Text.literal("Reduces Titan targeting radius when hood is up.").formatted(Formatting.GREEN));
   }
}
