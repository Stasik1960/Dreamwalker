package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.mixin.StaticArchitectureSpriteAccess;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.render.model.BakedQuad;

/** Resource metadata only; evaluated during section builds and cleared on reload. */
public final class BinaryQuadAlpha {
    private static final Map<BakedQuad, Boolean> CACHE = Collections.synchronizedMap(new IdentityHashMap<>());
    private BinaryQuadAlpha() {}
    public static void clear() { CACHE.clear(); }
    public static boolean binary(BakedQuad quad) { return CACHE.computeIfAbsent(quad, BinaryQuadAlpha::inspect); }
    private static boolean inspect(BakedQuad quad) {
        var sprite = quad.getSprite(); var contents = sprite.getContents();
        var levels = ((StaticArchitectureSpriteAccess)(Object)contents).staticArchitectureMipmapLevelsImages();
        if (levels == null || levels.length == 0 || levels[0] == null) return false;
        var image = levels[0];
        int width = contents.getWidth(), height = contents.getHeight();
        if (width <= 0 || height <= 0 || image.getWidth() % width != 0 || image.getHeight() % height != 0) return false;
        int[] vertices = quad.getVertexData();
        if (vertices.length % 4 != 0) return false;
        int stride = vertices.length / 4;
        if (stride < 6) return false;
        double spanU = sprite.getMaxU() - sprite.getMinU(), spanV = sprite.getMaxV() - sprite.getMinV();
        if (!(spanU > 0) || !(spanV > 0)) return false;
        double minU = 1, maxU = 0, minV = 1, maxV = 0;
        for (int vertex = 0; vertex < 4; vertex++) {
            if ((vertices[vertex * stride + 3] >>> 24) != 255) return false;
            double u = (Float.intBitsToFloat(vertices[vertex * stride + 4]) - sprite.getMinU()) / spanU;
            double v = (Float.intBitsToFloat(vertices[vertex * stride + 5]) - sprite.getMinV()) / spanV;
            if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || u >= 1 || v < 0 || v >= 1) return false;
            minU = Math.min(minU, u); maxU = Math.max(maxU, u);
            minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        int fromX = (int)Math.floor(minU * width), toX = (int)Math.floor(maxU * width);
        int fromY = (int)Math.floor(minV * height), toY = (int)Math.floor(maxV * height);
        // CUTOUT does not sample the generated mip chain: binary source alpha
        // can legitimately become fractional in lower mips without being glass.
        for (int tileY = 0; tileY < image.getHeight(); tileY += height)
            for (int tileX = 0; tileX < image.getWidth(); tileX += width)
                for (int y = fromY; y <= toY; y++) for (int x = fromX; x <= toX; x++) {
                    int alpha = image.getColor(tileX + x, tileY + y) >>> 24;
                    if (alpha != 0 && alpha != 255) return false;
                }
        return true;
    }
}
