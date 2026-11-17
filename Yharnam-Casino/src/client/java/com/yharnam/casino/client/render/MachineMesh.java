package com.yharnam.casino.client.render;

import com.yharnam.casino.YharnamCasino;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class MachineMesh {
    static final String LEVER = "lever";
    static final String[] REELS = {"reel_01", "reel_02", "reel_03"};

    private final Map<String, Part> parts;

    private MachineMesh(Map<String, Part> parts) {
        this.parts = parts;
    }

    static MachineMesh load(String style) {
        Identifier id = new Identifier(YharnamCasino.ID, "models/machine/" + style + ".mesh");
        return load(MinecraftClient.getInstance().getResourceManager(), id, true);
    }

    static MachineMesh load(ResourceManager resources, Identifier id) {
        return load(resources, id, false);
    }

    private static MachineMesh load(ResourceManager resources, Identifier id, boolean dynamic) {
        try {
            Resource resource = resources.getResource(id)
                    .orElseThrow(() -> new IOException("Missing machine mesh " + id));
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(resource.getInputStream()))) {
                if (input.readInt() != 0x5943534d) {
                    throw new IOException("Invalid Yharnam machine mesh header in " + id);
                }
                int version = input.readInt();
                if (version != 1) {
                    throw new IOException("Unsupported Yharnam machine mesh version " + version);
                }
                int partCount = input.readInt();
                Map<String, Part> parts = new HashMap<>(partCount);
                for (int partIndex = 0; partIndex < partCount; partIndex++) {
                    String name = input.readUTF();
                    Vector3f pivot = new Vector3f(input.readFloat(), input.readFloat(), input.readFloat());
                    int vertexCount = input.readInt();
                    float[] vertices = new float[Math.multiplyExact(vertexCount, 8)];
                    for (int index = 0; index < vertices.length; index++) {
                        vertices[index] = input.readFloat();
                    }
                    parts.put(name, new Part(pivot, dynamic ? toDrawQuads(vertices) : vertices));
                }
                return new MachineMesh(Map.copyOf(parts));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load slot machine model " + id, exception);
        }
    }

    Part part(String name) {
        return parts.get(name);
    }

    // Keep the exact triangle order and attributes. A native quad renders (A,B,C) + (A,C,D).
    private static float[] toDrawQuads(float[] source) {
        final int stride = 8;
        final int triangle = 3 * stride;
        float[] output = new float[source.length / triangle * 4 * stride];
        int written = 0;
        for (int offset = 0; offset < source.length; offset += triangle) {
            int next = offset + triangle;
            int rotation = next < source.length ? sharedDiagonal(source, offset, next) : -1;
            if (rotation < 0) {
                System.arraycopy(source, offset, output, written, triangle);
                System.arraycopy(source, offset + 2 * stride, output, written + triangle, stride);
            } else {
                int first = rotation / 3;
                int second = rotation % 3;
                for (int corner = 0; corner < 3; corner++) {
                    int from = offset + (first + corner) % 3 * stride;
                    System.arraycopy(source, from, output, written + corner * stride, stride);
                }
                System.arraycopy(source, next + (second + 2) % 3 * stride, output, written + triangle, stride);
                offset = next;
            }
            written += 4 * stride;
        }
        return written == output.length ? output : Arrays.copyOf(output, written);
    }

    private static int sharedDiagonal(float[] source, int first, int second) {
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                if (sameVertex(source, first + a * 8, second + b * 8)
                        && sameVertex(source, first + (a + 2) % 3 * 8, second + (b + 1) % 3 * 8)) {
                    return 3 * a + b;
                }
            }
        }
        return -1;
    }

    private static boolean sameVertex(float[] source, int a, int b) {
        return Arrays.equals(source, a, a + 8, source, b, b + 8);
    }

    record Part(Vector3f pivot, float[] vertices) {
    }
}
