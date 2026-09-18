package daot;

import java.util.function.Consumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Item.Settings;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class StrwsItem extends Item implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<RenderProvider>> clientRendererConsumer;

   public StrwsItem(Settings properties) {
      super(properties);
      SingletonGeoAnimatable.registerSyncedAnimatable(this);
   }

   @Override
   public ActionResult useOnBlock(ItemUsageContext context) {
      World level = context.getWorld();
      BlockPos base = context.getBlockPos().offset(context.getSide());
      Direction facing = context.getHorizontalPlayerFacing().getOpposite();
      if (!StrwsMultiblock.canPlace(level, base, facing)) {
         return ActionResult.FAIL;
      } else {
         if (!level.isClient) {
            StrwsMultiblock.place(level, base, facing);
            BlockSoundGroup sound = DannysAot.STRWS_BLOCK.getDefaultState().getSoundGroup();
            level.playSound(null, base, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            PlayerEntity player = context.getPlayer();
            if (player == null || !player.getAbilities().creativeMode) {
               context.getStack().decrement(1);
            }
         }

         return ActionResult.success(level.isClient);
      }
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
