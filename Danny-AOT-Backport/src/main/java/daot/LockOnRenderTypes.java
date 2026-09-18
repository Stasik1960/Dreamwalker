package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;

@Environment(EnvType.CLIENT)
public final class LockOnRenderTypes extends RenderLayer {
   public static final RenderLayer RETICLE = of(
      "dannys-aot:jaw_lock_on",
      VertexFormats.POSITION_COLOR,
      DrawMode.QUADS,
      256,
      false,
      true,
      MultiPhaseParameters.builder()
         .program(COLOR_PROGRAM)
         .writeMaskState(COLOR_MASK)
         .transparency(TRANSLUCENT_TRANSPARENCY)
         .depthTest(ALWAYS_DEPTH_TEST)
         .cull(DISABLE_CULLING)
         .build(false)
   );

   private LockOnRenderTypes(
      String name, VertexFormat format, DrawMode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState
   ) {
      super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
      throw new IllegalStateException("Not meant for instantiation");
   }
}
