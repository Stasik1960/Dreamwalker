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
        return load(MinecraftClient.getInstance().getResourceManager(), id);
    }

    static MachineMesh load(ResourceManager resources, Identifier id) {
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
                    parts.put(name, new Part(pivot, vertices));
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

    record Part(Vector3f pivot, float[] vertices) {
    }
}
