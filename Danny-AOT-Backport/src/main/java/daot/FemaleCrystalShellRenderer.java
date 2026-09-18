package daot;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import daot.compat.ClientSkins.SkinTextures;
import daot.compat.ClientSkins.Model;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class FemaleCrystalShellRenderer extends GeoEntityRenderer<FemaleCrystalShellEntity> {
   private PlayerEntityModel<?> widePlayerModel;
   private PlayerEntityModel<?> slimPlayerModel;
   private UUID cachedSkinUUID = null;
   private Identifier cachedSkinTexture = null;
   private boolean cachedSlim = false;

   public FemaleCrystalShellRenderer(Context context) {
      super(context, new FemaleCrystalShellModel());
      this.shadowRadius = 1.5F;
      this.widePlayerModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);
      this.slimPlayerModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
   }

   public RenderLayer getRenderType(FemaleCrystalShellEntity entity, Identifier texture, VertexConsumerProvider bufferSource, float partialTick) {
      return RenderLayer.getEntityTranslucent(texture);
   }

   public void render(
      FemaleCrystalShellEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      this.renderPlayerClone(entity, poseStack, bufferSource, packedLight);
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
   }

   private void renderPlayerClone(FemaleCrystalShellEntity entity, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null || mc.player.getVehicle() != entity || mc.options.getPerspective() != Perspective.FIRST_PERSON) {
         Identifier skinTexture = null;
         boolean slim = false;

         for (Entity passenger : entity.getPassengerList()) {
            if (passenger instanceof AbstractClientPlayerEntity clientPlayer) {
               SkinTextures skin = daot.compat.ClientSkins.get(clientPlayer);
               skinTexture = skin.texture();
               slim = skin.model() == Model.SLIM;
               break;
            }
         }

         if (skinTexture == null) {
            UUID shifterUUID = entity.getShifterUUID();
            if (shifterUUID == null) {
               return;
            }

            SkinTextures skin = this.resolveSkin(shifterUUID);
               skinTexture = skin.texture();
               slim = skin.model() == Model.SLIM;
               this.cachedSkinUUID = shifterUUID;
               this.cachedSkinTexture = skinTexture;
               this.cachedSlim = slim;
         }

         PlayerEntityModel<?> playerModel = slim ? this.slimPlayerModel : this.widePlayerModel;
         playerModel.child = false;
         playerModel.sneaking = false;
         playerModel.leaningPitch = 0.0F;
         resetModelPart(playerModel.head);
         resetModelPart(playerModel.hat);
         resetModelPart(playerModel.body);
         resetModelPart(playerModel.rightArm);
         resetModelPart(playerModel.leftArm);
         resetModelPart(playerModel.rightLeg);
         resetModelPart(playerModel.leftLeg);
         playerModel.hat.visible = true;
         playerModel.jacket.visible = true;
         playerModel.leftSleeve.visible = true;
         playerModel.rightSleeve.visible = true;
         playerModel.leftPants.visible = true;
         playerModel.rightPants.visible = true;
         resetModelPart(playerModel.jacket);
         resetModelPart(playerModel.leftSleeve);
         resetModelPart(playerModel.rightSleeve);
         resetModelPart(playerModel.leftPants);
         resetModelPart(playerModel.rightPants);
         poseStack.push();
         poseStack.translate(0.0, 0.95, 0.0);
         poseStack.scale(0.9375F, 0.9375F, 0.9375F);
         poseStack.translate(0.0, 1.501, 0.0);
         poseStack.scale(1.0F, -1.0F, -1.0F);
         VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderLayer.getEntityTranslucent(skinTexture));
         playerModel.render(poseStack, vertexConsumer, packedLight, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
         poseStack.pop();
      }
   }

   private static void resetModelPart(ModelPart part) {
      part.pitch = 0.0F;
      part.yaw = 0.0F;
      part.roll = 0.0F;
      part.visible = true;
   }

   private SkinTextures resolveSkin(UUID uuid) {
      return daot.compat.ClientSkins.resolve(uuid);
   }
}
