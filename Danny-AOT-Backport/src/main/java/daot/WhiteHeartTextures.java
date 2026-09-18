package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage.Format;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class WhiteHeartTextures {
   public static final Identifier HEART_FULL = new Identifier("dannys-aot", "homelander_heart_full");
   public static final Identifier HEART_HALF = new Identifier("dannys-aot", "homelander_heart_half");
   private static final int[][] FULL_PATTERN = new int[][]{
      {0, 1, 1, 0, 0, 1, 1, 0, 0},
      {1, 1, 1, 1, 1, 1, 1, 1, 0},
      {1, 1, 1, 1, 1, 1, 1, 1, 0},
      {1, 1, 1, 1, 1, 1, 1, 1, 0},
      {0, 1, 1, 1, 1, 1, 1, 0, 0},
      {0, 0, 1, 1, 1, 1, 0, 0, 0},
      {0, 0, 0, 1, 1, 0, 0, 0, 0},
      {0, 0, 0, 0, 0, 0, 0, 0, 0},
      {0, 0, 0, 0, 0, 0, 0, 0, 0}
   };
   private static final int[][] HALF_PATTERN = new int[][]{
      {0, 1, 1, 0, 0, 0, 0, 0, 0},
      {1, 1, 1, 1, 1, 0, 0, 0, 0},
      {1, 1, 1, 1, 1, 0, 0, 0, 0},
      {1, 1, 1, 1, 1, 0, 0, 0, 0},
      {0, 1, 1, 1, 1, 0, 0, 0, 0},
      {0, 0, 1, 1, 1, 0, 0, 0, 0},
      {0, 0, 0, 1, 1, 0, 0, 0, 0},
      {0, 0, 0, 0, 0, 0, 0, 0, 0},
      {0, 0, 0, 0, 0, 0, 0, 0, 0}
   };
   private static boolean registered = false;

   private WhiteHeartTextures() {
   }

   public static void register() {
      if (!registered) {
         registered = true;
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null) {
            mc.getTextureManager().registerTexture(HEART_FULL, new NativeImageBackedTexture(buildHeart(FULL_PATTERN)));
            mc.getTextureManager().registerTexture(HEART_HALF, new NativeImageBackedTexture(buildHeart(HALF_PATTERN)));
         }
      }
   }

   private static NativeImage buildHeart(int[][] pattern) {
      NativeImage img = new NativeImage(Format.RGBA, 9, 9, true);

      for (int y = 0; y < 9; y++) {
         for (int x = 0; x < 9; x++) {
            img.setColor(x, y, pattern[y][x] == 1 ? -1 : 0);
         }
      }

      return img;
   }
}
