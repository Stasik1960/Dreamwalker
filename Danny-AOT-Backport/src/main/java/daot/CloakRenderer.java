package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

@Environment(EnvType.CLIENT)
public class CloakRenderer extends GeoArmorRenderer<CloakItem> {
   public CloakRenderer() {
      super(new CloakModel());
   }

   public void updateModelState(LivingEntity entity, ItemStack stack) {
      CloakModel model = (CloakModel)this.getGeoModel();
      model.currentHoodUp = HoodTracker.isHoodUpClient(entity.getUuid());
      String skin = daot.compat.components.Components.get(stack, DannysAot.CLOAK_SKIN);
      boolean useTdxm = "TDXM".equals(skin) && DannysAot.TDXM_AUTHORIZED_UUIDS.contains(entity.getUuid());
      boolean useBerserk = !useTdxm && "BERSERK".equals(skin) && DannysAot.BERSERK_AUTHORIZED_UUIDS.contains(entity.getUuid());
      boolean useAaron = !useTdxm && !useBerserk && "AARON".equals(skin) && DannysAot.AARON_AUTHORIZED_UUIDS.contains(entity.getUuid());
      boolean useRoyalty = !useTdxm && !useBerserk && !useAaron && "ROYALTY".equals(skin) && DannysAot.ROYALTY_AUTHORIZED_UUIDS.contains(entity.getUuid());
      boolean useTitan = !useTdxm
         && !useBerserk
         && !useAaron
         && !useRoyalty
         && "TITAN".equals(skin)
         && DannysAot.TITAN_AUTHORIZED_UUIDS.contains(entity.getUuid());
      boolean useHyper = !useTdxm
         && !useBerserk
         && !useAaron
         && !useRoyalty
         && !useTitan
         && "HYPER".equals(skin)
         && DannysAot.HYPER_AUTHORIZED_UUIDS.contains(entity.getUuid());
      model.currentUseTdxm = useTdxm;
      model.currentUseBerserk = useBerserk;
      model.currentUseRoyalty = useRoyalty;
      model.currentUseTitan = useTitan;
      model.currentUseHyper = useHyper;
      CloakItem.renderAsTdxm = useTdxm;
      CloakItem.renderAsBerserk = useBerserk;
      CloakItem.berserkInMotion = useBerserk && isInMotion(entity);
      CloakItem.renderAsTitan = useTitan;
      CloakItem.titanInMotion = useTitan && isInMotion(entity);
      if (useTdxm) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_TDXM : CloakModel.TEXTURE_TDXM_DOWN;
      } else if (useBerserk) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_BERSERK : CloakModel.TEXTURE_BERSERK_DOWN;
      } else if (useAaron) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_AARON : CloakModel.TEXTURE_AARON_DOWN;
      } else if (useRoyalty) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_ROYALTY : CloakModel.TEXTURE_ROYALTY_DOWN;
      } else if (useTitan) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_TITAN : CloakModel.TEXTURE_TITAN_DOWN;
      } else if (useHyper) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_HYPER : CloakModel.TEXTURE_HYPER_DOWN;
      } else if (stack.getItem() == DannysAot.ROYAL_CLOAK) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_ROYAL : CloakModel.TEXTURE_ROYAL_DOWN;
      } else if (stack.getItem() == DannysAot.BLACK_CLOAK) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_BLACK : CloakModel.TEXTURE_BLACK_DOWN;
      } else if (stack.getItem() == DannysAot.GREEN_SCOUT_CLOAK) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_GREEN_SCOUT : CloakModel.TEXTURE_GREEN_SCOUT_DOWN;
      } else if (stack.getItem() == DannysAot.GREEN_GARRISON_CLOAK) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_GREEN_GARRISON : CloakModel.TEXTURE_GREEN_GARRISON_DOWN;
      } else if (stack.getItem() == DannysAot.GREEN_MILITARY_POLICE_CLOAK) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_GREEN_MILITARY_POLICE : CloakModel.TEXTURE_GREEN_MILITARY_POLICE_DOWN;
      } else if (stack.getItem() == DannysAot.GOLD_CLOAK) {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_GOLD : CloakModel.TEXTURE_GOLD_DOWN;
      } else {
         model.currentTexture = model.currentHoodUp ? CloakModel.TEXTURE_GREEN : CloakModel.TEXTURE_GREEN_DOWN;
      }
   }

   private static boolean isInMotion(LivingEntity entity) {
      return !entity.isOnGround() ? true : entity.getVelocity().horizontalLengthSquared() > 1.0E-5;
   }

   protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
      this.setVisible(true);
   }

   public void preRender(
      MatrixStack poseStack,
      CloakItem animatable,
      BakedGeoModel model,
      @Nullable VertexConsumerProvider bufferSource,
      @Nullable VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      float bpRed, float bpGreen, float bpBlue, float bpAlpha
   ) {
      int colour = daot.compat.RenderColors.pack(bpRed,bpGreen,bpBlue,bpAlpha);
      super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,daot.compat.RenderColors.red(colour), daot.compat.RenderColors.green(colour), daot.compat.RenderColors.blue(colour), daot.compat.RenderColors.alpha(colour));
      boolean slim = this.currentEntity instanceof AbstractClientPlayerEntity player && "slim".equals(player.getModel());
      float scaleX = slim ? 0.75F : 1.0F;
      if (this.rightArm != null) {
         this.rightArm.setScaleX(scaleX);
      }

      if (this.leftArm != null) {
         this.leftArm.setScaleX(scaleX);
      }
   }
}
