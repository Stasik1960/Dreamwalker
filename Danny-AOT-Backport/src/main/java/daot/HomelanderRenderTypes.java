package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;

@Environment(EnvType.CLIENT)
public final class HomelanderRenderTypes extends RenderLayer {
   public static final RenderLayer BEAM = of(
      "dannys-aot:homelander_beam",
      VertexFormats.POSITION_COLOR,
      DrawMode.QUADS,
      256,
      false,
      true,
      MultiPhaseParameters.builder()
         .program(COLOR_PROGRAM)
         .writeMaskState(COLOR_MASK)
         .transparency(LIGHTNING_TRANSPARENCY)
         .depthTest(LEQUAL_DEPTH_TEST)
         .cull(DISABLE_CULLING)
         .build(false)
   );
   public static final RenderLayer TENTACLE = of(
      "dannys-aot:butcher_tentacle",
      VertexFormats.POSITION_COLOR,
      DrawMode.QUADS,
      256,
      false,
      false,
      MultiPhaseParameters.builder()
         .program(COLOR_PROGRAM)
         .writeMaskState(COLOR_MASK)
         .transparency(TRANSLUCENT_TRANSPARENCY)
         .depthTest(LEQUAL_DEPTH_TEST)
         .cull(DISABLE_CULLING)
         .build(false)
   );

   private HomelanderRenderTypes(
      String name, VertexFormat format, DrawMode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState
   ) {
      super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
      throw new IllegalStateException("Not meant for instantiation");
   }
}
