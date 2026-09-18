package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtils;

@Environment(EnvType.CLIENT)
public final class GeoModelHelper {
   private GeoModelHelper() {
   }

   public static void renderModel(MatrixStack poseStack, BakedGeoModel model, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
      for (GeoBone bone : model.topLevelBones()) {
         renderBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
      }
   }

   private static void renderBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
      poseStack.push();
      RenderUtils.prepMatrixForBone(poseStack, bone);
      if (!bone.isHidden()) {
         for (GeoCube cube : bone.getCubes()) {
            poseStack.push();
            renderCube(poseStack, cube, buffer, packedLight, packedOverlay, colour);
            poseStack.pop();
         }
      }

      if (!bone.isHidingChildren()) {
         for (GeoBone child : bone.getChildBones()) {
            renderBone(poseStack, child, buffer, packedLight, packedOverlay, colour);
         }
      }

      poseStack.pop();
   }

   private static void renderCube(MatrixStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
      RenderUtils.translateToPivotPoint(poseStack, cube);
      RenderUtils.rotateMatrixAroundCube(poseStack, cube);
      RenderUtils.translateAwayFromPivotPoint(poseStack, cube);
      Matrix3f normalMatrix = poseStack.peek().getNormalMatrix();
      Matrix4f poseMatrix = new Matrix4f(poseStack.peek().getPositionMatrix());

      for (GeoQuad quad : cube.quads()) {
         if (quad != null) {
            Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
            RenderUtils.fixInvertedFlatCube(cube, normal);

            for (GeoVertex vertex : quad.vertices()) {
               Vector3f pos = vertex.position();
               Vector4f transformedPos = poseMatrix.transform(new Vector4f(pos.x(), pos.y(), pos.z(), 1.0F));
               buffer.vertex(
                  transformedPos.x(),
                  transformedPos.y(),
                  transformedPos.z(),
                  daot.compat.RenderColors.red(colour), daot.compat.RenderColors.green(colour), daot.compat.RenderColors.blue(colour), daot.compat.RenderColors.alpha(colour),
                  vertex.texU(),
                  vertex.texV(),
                  packedOverlay,
                  packedLight,
                  normal.x(),
                  normal.y(),
                  normal.z()
               );
            }
         }
      }
   }
}
