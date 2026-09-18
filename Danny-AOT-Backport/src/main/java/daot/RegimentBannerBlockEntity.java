package daot;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RegimentBannerBlockEntity extends BlockEntity implements GeoBlockEntity {
   private static final RawAnimation SWAY_ANIM = RawAnimation.begin().thenLoop("sway");
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private RegimentType regimentType = RegimentType.GARRISON;

   public RegimentBannerBlockEntity(BlockPos pos, BlockState state) {
      super(DannysAot.REGIMENT_BANNER_BLOCK_ENTITY, pos, state);
   }

   public RegimentType getRegimentType() {
      return this.regimentType;
   }

   public void setRegimentType(RegimentType type) {
      this.regimentType = type;
      this.markDirty();
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "sway", 0, state -> {
         state.getController().setAnimation(SWAY_ANIM);
         return PlayState.CONTINUE;
      }));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   protected void writeNbt(NbtCompound nbt) {
      super.writeNbt(nbt);
      nbt.putString("Regiment", this.regimentType.asString());
   }

   @Override
   public void readNbt(NbtCompound nbt) {
      super.readNbt(nbt);
      if (nbt.contains("Regiment")) {
         this.regimentType = RegimentType.fromName(nbt.getString("Regiment"));
      }
   }

   @Override
   public NbtCompound toInitialChunkDataNbt() {
      NbtCompound tag = super.toInitialChunkDataNbt();
      tag.putString("Regiment", this.regimentType.asString());
      return tag;
   }

   @Override
   public Packet<ClientPlayPacketListener> toUpdatePacket() {
      return BlockEntityUpdateS2CPacket.create(this);
   }
}
