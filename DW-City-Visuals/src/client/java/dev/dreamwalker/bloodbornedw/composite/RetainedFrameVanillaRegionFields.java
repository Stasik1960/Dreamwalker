package dev.dreamwalker.bloodbornedw.composite;

import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.chunk.ChunkRendererRegion;

/** The fields belong to the region's immutable RenderedChunk snapshot. */
public final class RetainedFrameVanillaRegionFields {
    public static final Field CHUNKS, X, Z;
    private static Field field(String field, String descriptor) throws ReflectiveOperationException {
        String name = FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_853", field, descriptor);
        Field result = ChunkRendererRegion.class.getDeclaredField(name); result.setAccessible(true); return result;
    }
    static {
        try {
            CHUNKS = field("field_4483", "[[Lnet/minecraft/class_6849;");
            X = field("field_4488", "I"); Z = field("field_4487", "I");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported vanilla region snapshot", failure); }
    }
    private RetainedFrameVanillaRegionFields() {}
}

