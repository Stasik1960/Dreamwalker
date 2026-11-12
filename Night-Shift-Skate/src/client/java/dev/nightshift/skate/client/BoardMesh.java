package dev.nightshift.skate.client;

import dev.nightshift.skate.BoardStyle;
import dev.nightshift.skate.NightShiftSkate;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Immutable, evaluated Blender geometry loaded from the client resource pack. */
final class BoardMesh {
    private static final int MAGIC = 0x4E534D31; // NSM1
    private static final int VERSION = 1;
    private static final int MAX_VERTICES = 500_000;
    private static final int MAX_INDICES = 1_500_000;

    private final float[] vertices;
    private final int[] colors;
    private final int[] indices;

    private BoardMesh(float[] vertices, int[] colors, int[] indices) {
        this.vertices = vertices;
        this.colors = colors;
        this.indices = indices;
    }

    static BoardMesh read(ResourceManager manager, Identifier id) throws IOException {
        try (InputStream resource = manager.getResource(id).orElseThrow(
                () -> new IOException("Missing board mesh " + id)).getInputStream();
             DataInputStream input = new DataInputStream(new BufferedInputStream(resource))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported board mesh header in " + id);
            }
            int vertexCount = input.readInt();
            int indexCount = input.readInt();
            if (vertexCount <= 0 || vertexCount > MAX_VERTICES
                    || indexCount <= 0 || indexCount > MAX_INDICES || indexCount % 3 != 0) {
                throw new IOException("Invalid board mesh counts in " + id + ": "
                        + vertexCount + " vertices, " + indexCount + " indices");
            }
            for (int i = 0; i < 6; i++) {
                if (!Float.isFinite(input.readFloat())) {
                    throw new IOException("Non-finite board mesh bounds in " + id);
                }
            }
            float[] vertices = new float[vertexCount * 6];
            int[] colors = new int[vertexCount];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                int offset = vertex * 6;
                for (int component = 0; component < 6; component++) {
                    float value = input.readFloat();
                    if (!Float.isFinite(value)) {
                        throw new IOException("Non-finite vertex in " + id);
                    }
                    vertices[offset + component] = value;
                }
                colors[vertex] = input.readInt();
            }
            int[] indices = new int[indexCount];
            for (int index = 0; index < indexCount; index++) {
                indices[index] = input.readInt();
                if (indices[index] < 0 || indices[index] >= vertexCount) {
                    throw new IOException("Out-of-range index in " + id);
                }
            }
            return new BoardMesh(vertices, colors, indices);
        }
    }

    void render(MatrixStack matrices, VertexConsumer consumer, int light) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();
        for (int index = 0; index < indices.length; index += 3) {
            emit(consumer, position, normal, light, indices[index]);
            emit(consumer, position, normal, light, indices[index + 1]);
            emit(consumer, position, normal, light, indices[index + 2]);
            // Entity cutout is a quad layer; repeating the final corner emits a safe triangle.
            emit(consumer, position, normal, light, indices[index + 2]);
        }
    }

    private void emit(VertexConsumer consumer, Matrix4f position, Matrix3f normal, int light, int vertex) {
        int offset = vertex * 6;
        int color = colors[vertex];
        consumer.vertex(position, vertices[offset], vertices[offset + 1], vertices[offset + 2])
                .color((color >>> 24) & 255, (color >>> 16) & 255, (color >>> 8) & 255, color & 255)
                .texture(0.5F, 0.5F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal, vertices[offset + 3], vertices[offset + 4], vertices[offset + 5])
                .next();
    }

    static final class Library implements SimpleSynchronousResourceReloadListener {
        private static final Identifier LISTENER_ID = NightShiftSkate.id("board_meshes");
        private static final Identifier IRON = NightShiftSkate.id("meshes/board/iron_vespers.nsmesh");
        private static final Identifier GRAVE = NightShiftSkate.id("meshes/board/grave_procession.nsmesh");
        private static volatile Map<BoardStyle, BoardMesh> loaded = Map.of();

        private Library() {
        }

        static void initialize() {
            ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new Library());
        }

        static BoardMesh get(BoardStyle style) {
            BoardMesh mesh = loaded.get(style);
            if (mesh == null) {
                throw new IllegalStateException("Board mesh resources have not loaded for " + style);
            }
            return mesh;
        }

        @Override
        public Identifier getFabricId() {
            return LISTENER_ID;
        }

        @Override
        public void reload(ResourceManager manager) {
            EnumMap<BoardStyle, BoardMesh> next = new EnumMap<>(BoardStyle.class);
            try {
                next.put(BoardStyle.IRON_VESPERS, BoardMesh.read(manager, IRON));
                next.put(BoardStyle.GRAVE_PROCESSION, BoardMesh.read(manager, GRAVE));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load Night Shift board meshes", exception);
            }
            loaded = Map.copyOf(next);
        }
    }
}
