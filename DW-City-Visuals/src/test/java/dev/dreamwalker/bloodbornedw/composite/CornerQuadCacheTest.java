package dev.dreamwalker.bloodbornedw.composite;

import java.util.Map;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** Runs only inside the capture client: verifies transformed cache guards, not mocks. */
public final class CornerQuadCacheTest {
    private static Map<?, ?> cache(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>)field.get(null);
    }
    public static void main(String[] args) throws Exception {
        var state = Blocks.STONE.getDefaultState();
        var model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        var originals = model.getQuads(state, Direction.NORTH, Random.create(7));
        if (originals.isEmpty()) throw new AssertionError("Stone model has no NORTH quad");
        BakedQuad original = originals.get(0);
        BakedQuad parent = new BakedQuad(original.getVertexData().clone(), original.getColorIndex(),
            original.getFace(), original.getSprite(), original.hasShade());
        var opaque = cache(StaticArchitectureGeometry.class, "OPAQUE_QUADS");
        var binary = cache(BinaryQuadAlpha.class, "CACHE");
        boolean expectedOpaque = StaticArchitectureGeometry.opaqueQuad(parent);
        boolean expectedBinary = BinaryQuadAlpha.binary(parent);
        for (int attempt = 0; attempt < 32; attempt++) {
            BakedQuad child = new BakedQuad(parent.getVertexData().clone(), parent.getColorIndex(),
                parent.getFace(), parent.getSprite(), parent.hasShade());
            CornerQuadParentScope.emit(child, parent, () -> {
                if (StaticArchitectureGeometry.opaqueQuad(child) != expectedOpaque ||
                    BinaryQuadAlpha.binary(child) != expectedBinary)
                    throw new AssertionError("Child layer differs from parent");
                BakedQuad nested = new BakedQuad(child.getVertexData().clone(), child.getColorIndex(),
                    child.getFace(), child.getSprite(), child.hasShade());
                CornerQuadParentScope.emit(nested, child, () -> {
                    if (StaticArchitectureGeometry.opaqueQuad(nested) != expectedOpaque ||
                        BinaryQuadAlpha.binary(nested) != expectedBinary)
                        throw new AssertionError("Nested child layer differs from original parent");
                });
                if (opaque.containsKey(nested) || binary.containsKey(nested) ||
                    CornerQuadParentScope.parentFor(child) != parent)
                    throw new AssertionError("Nested scope retained child or failed to restore outer scope");
            });
            if (opaque.containsKey(child) || binary.containsKey(child))
                throw new AssertionError("Transient child retained in an identity cache");
            if (CornerQuadParentScope.parentFor(child) != null)
                throw new AssertionError("Child scope leaked after emission");
        }
        if (!opaque.containsKey(parent) || !binary.containsKey(parent))
            throw new AssertionError("Parent classification was not cached");
        System.out.println("PASS: native Mixin layer inheritance; 64 transient children, including nested, absent from both identity caches");
    }
}
