package daot;

import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RegimentBannerItem extends BlockItem implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final RegimentType regimentType;
   private final Block wallBlock;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<RenderProvider>> clientRendererConsumer;

   public RegimentBannerItem(Block standingBlock, Block wallBlock, RegimentType regimentType) {
      super(standingBlock, new Settings().maxCount(16));
      this.wallBlock = wallBlock;
      this.regimentType = regimentType;
   }

   public RegimentType getRegimentType() {
      return this.regimentType;
   }

   @Override
   public String getTranslationKey() {
      return this.getOrCreateTranslationKey();
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

   @Override
   protected BlockState getPlacementState(ItemPlacementContext context) {
      Direction clickedFace = context.getSide();
      BlockPos pos = context.getBlockPos();
      World level = context.getWorld();
      if (clickedFace.getAxis().isHorizontal()) {
         BlockState wallState = this.wallBlock.getPlacementState(context);
         if (wallState != null && wallState.canPlaceAt(level, pos)) {
            return wallState;
         }
      }

      BlockState standingState = this.getBlock().getPlacementState(context);
      return standingState != null && standingState.canPlaceAt(level, pos) ? standingState : null;
   }

   @Override
   protected boolean postPlacement(BlockPos pos, World world, PlayerEntity player, ItemStack stack, BlockState state) {
      boolean result = super.postPlacement(pos, world, player, stack, state);
      if (world.getBlockEntity(pos) instanceof RegimentBannerBlockEntity banner) {
         banner.setRegimentType(this.regimentType);
      }

      return result;
   }
}
