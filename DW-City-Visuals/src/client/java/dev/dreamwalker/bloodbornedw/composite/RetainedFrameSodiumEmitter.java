package dev.dreamwalker.bloodbornedw.composite;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.BlockRenderView;

/** Reflection is initialized only for an occupied sparse section; it does not add optional runtime dependencies. */
public final class RetainedFrameSodiumEmitter {
    private RetainedFrameSodiumEmitter() {}
    private static final class Bridge {
        static final Field CACHE, BUFFERS;
        static final Method WORLD_SLICE, CONTEXT_UPDATE, INDIUM_GET, INDIUM_RENDER, BLOCK_RENDERER, SODIUM_RENDER;
        static final Constructor<?> CONTEXT;
        static {
            try {
                Class<?> build = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildContext");
                Class<?> cache = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache");
                Class<?> slice = Class.forName("me.jellysquid.mods.sodium.client.world.WorldSlice");
                Class<?> context = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext");
                Class<?> buffers = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers");
                Class<?> sodiumRenderer = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer");
                CACHE = build.getField("cache"); BUFFERS = build.getField("buffers");
                WORLD_SLICE = cache.getMethod("getWorldSlice"); BLOCK_RENDERER = cache.getMethod("getBlockRenderer");
                CONTEXT = context.getConstructor(slice);
                CONTEXT_UPDATE = context.getMethod("update", BlockPos.class, BlockPos.class, BlockState.class, BakedModel.class, long.class);
                SODIUM_RENDER = sodiumRenderer.getMethod("renderModel", context, buffers);
                if (FabricLoader.getInstance().isModLoaded("indium")) {
                    Class<?> indium = Class.forName("link.infra.indium.renderer.render.TerrainRenderContext");
                    INDIUM_GET = indium.getMethod("get", build); INDIUM_RENDER = indium.getMethod("tessellateBlock", context);
                } else { INDIUM_GET = null; INDIUM_RENDER = null; }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Unsupported Sodium/Indium frame meshing API", failure);
            }
        }
    }
    public static void render(Object build, ChunkSectionPos origin, Map<Integer, Integer> frames) {
        if (frames.isEmpty()) return;
        try {
            Object cache = Bridge.CACHE.get(build), slice = Bridge.WORLD_SLICE.invoke(cache);
            BlockRenderView view = (BlockRenderView) slice;
            Object renderContext = Bridge.CONTEXT.newInstance(slice);
            Object renderer = Bridge.INDIUM_GET != null ? Bridge.INDIUM_GET.invoke(null, build) : Bridge.BLOCK_RENDERER.invoke(cache);
            Object buffers = Bridge.INDIUM_GET == null ? Bridge.BUFFERS.get(build) : null;
            BlockRenderManager manager = MinecraftClient.getInstance().getBlockRenderManager();
            Map<Integer, BakedModel> models = new HashMap<>();
            for (var entry : frames.entrySet()) {
                int packed = entry.getKey();
                BlockPos local = new BlockPos(packed & 15, (packed >> 8) & 15, (packed >> 4) & 15);
                BlockPos pos = new BlockPos(origin.getMinX() + local.getX(), origin.getMinY() + local.getY(), origin.getMinZ() + local.getZ());
                if (RetainedFrameState.matches(view.getBlockState(pos))) continue;
                BlockState state = RetainedFrames.state(entry.getValue());
                BakedModel model = models.computeIfAbsent(entry.getValue(), ignored -> new RetainedFrameModel(manager.getModel(state)));
                Bridge.CONTEXT_UPDATE.invoke(renderContext, pos, local, state, model, state.getRenderingSeed(pos));
                if (Bridge.INDIUM_RENDER != null) Bridge.INDIUM_RENDER.invoke(renderer, renderContext);
                else Bridge.SODIUM_RENDER.invoke(renderer, renderContext, buffers);
            }
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Unable to render a retained frame", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to access Sodium frame meshing", failure);
        }
    }
}

