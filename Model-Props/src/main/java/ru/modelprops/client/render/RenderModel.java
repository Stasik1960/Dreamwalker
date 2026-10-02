package ru.modelprops.client.render;

import net.minecraft.client.render.model.json.ModelTransformationMode;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record RenderModel(
        String id,
        String displayName,
        List<Element> elements,
        String flatTexture,
        Map<ModelTransformationMode, DisplayTransform> displayTransforms
) {
    public Set<String> animationTargets() {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        targets.add("root");
        for (Element element : elements) {
            targets.add(element.id());
        }
        return Set.copyOf(targets);
    }

    public record Element(String id, float[] from, float[] to, ElementRotation rotation, Map<String, Face> faces) {
    }

    public record ElementRotation(float[] origin, String axis, float angle) {
        public static final ElementRotation NONE = new ElementRotation(new float[]{8.0F, 8.0F, 8.0F}, "y", 0.0F);
    }

    public record Face(float[] uv, String texture, int rotation) {
    }

    public record DisplayTransform(float[] translation, float[] rotation, float[] scale) {
        public static final DisplayTransform IDENTITY = new DisplayTransform(
                new float[]{0.0F, 0.0F, 0.0F},
                new float[]{0.0F, 0.0F, 0.0F},
                new float[]{1.0F, 1.0F, 1.0F}
        );
    }
}
