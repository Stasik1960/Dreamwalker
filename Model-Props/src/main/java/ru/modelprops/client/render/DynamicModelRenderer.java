package ru.modelprops.client.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.modelprops.ModelPropItem;
import ru.modelprops.animation.AnimationPose;
import ru.modelprops.animation.AnimationRuntime;
import ru.modelprops.animation.AnimationTransform;
import ru.modelprops.animation.AnimationVector;
import ru.modelprops.client.ClientModelCatalog;
import ru.modelprops.client.ClientModelEntry;
import ru.modelprops.model.ModelTransform;

import java.util.Map;

public final class DynamicModelRenderer {
    public static final DynamicModelRenderer INSTANCE = new DynamicModelRenderer();

    private DynamicModelRenderer() {
    }

    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int overlay) {
        renderInternal(stack, mode, matrices, consumers, light, overlay, false);
    }

    public void renderPreview(ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers) {
        renderInternal(stack, ModelTransformationMode.GUI, matrices, consumers,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, true);
    }

    private void renderInternal(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                VertexConsumerProvider consumers, int light, int overlay, boolean forceHandTransform) {
        String id = ModelPropItem.getModelId(stack);
        ClientModelEntry entry = ClientModelCatalog.INSTANCE.get(id);
        matrices.push();
        if (entry == null) {
            drawFlat(id.isBlank() ? "minecraft:item/armor_stand" : "minecraft:item/barrier",
                    matrices, consumers, light, overlay);
            matrices.pop();
            return;
        }

        RenderModel model = entry.model();
        applyDisplayTransform(matrices, model.displayTransforms().get(mode));
        if (forceHandTransform || isHand(mode)) {
            applyServerTransform(matrices, ClientModelCatalog.INSTANCE.transform(id));
        }
        AnimationPose animationPose = isHand(mode)
                ? AnimationRuntime.pose(stack, id, System.nanoTime())
                : AnimationPose.EMPTY;
        applyAnimationTransform(matrices, animationPose.transform("root"), new float[]{8.0F, 8.0F, 8.0F});

        if (model.elements().isEmpty()) {
            drawFlat(model.flatTexture(), matrices, consumers, light, overlay);
        } else {
            for (RenderModel.Element element : model.elements()) {
                renderElement(element, animationPose.transform(element.id()), matrices, consumers, light, overlay);
            }
        }
        matrices.pop();
    }

    private static boolean isHand(ModelTransformationMode mode) {
        return mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
    }

    private static void applyDisplayTransform(MatrixStack matrices, RenderModel.DisplayTransform transform) {
        if (transform == null) {
            return;
        }
        float[] translation = transform.translation();
        float[] rotation = transform.rotation();
        float[] scale = transform.scale();
        matrices.translate(translation[0] / 16.0F, translation[1] / 16.0F, translation[2] / 16.0F);
        matrices.translate(0.5F, 0.5F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));
        matrices.scale(scale[0], scale[1], scale[2]);
        matrices.translate(-0.5F, -0.5F, -0.5F);
    }

    private static void applyServerTransform(MatrixStack matrices, ModelTransform transform) {
        matrices.translate(transform.offsetX(), transform.offsetY(), transform.offsetZ());
        matrices.translate(0.5F, 0.5F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(transform.rotationX()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(transform.rotationY()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(transform.rotationZ()));
        matrices.scale(transform.scale(), transform.scale(), transform.scale());
        matrices.translate(-0.5F, -0.5F, -0.5F);
    }

    private static void renderElement(RenderModel.Element element, AnimationTransform animation,
                                      MatrixStack matrices,
                                      VertexConsumerProvider consumers, int light, int overlay) {
        matrices.push();
        RenderModel.ElementRotation rotation = element.rotation();
        float[] pivot = rotation == null ? new float[]{8.0F, 8.0F, 8.0F} : rotation.origin();
        applyAnimationTransform(matrices, animation, pivot);
        if (rotation != null && rotation.angle() != 0.0F) {
            float[] origin = rotation.origin();
            matrices.translate(origin[0] / 16.0F, origin[1] / 16.0F, origin[2] / 16.0F);
            switch (rotation.axis()) {
                case "x" -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation.angle()));
                case "z" -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation.angle()));
                default -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation.angle()));
            }
            matrices.translate(-origin[0] / 16.0F, -origin[1] / 16.0F, -origin[2] / 16.0F);
        }

        float x0 = element.from()[0] / 16.0F;
        float y0 = element.from()[1] / 16.0F;
        float z0 = element.from()[2] / 16.0F;
        float x1 = element.to()[0] / 16.0F;
        float y1 = element.to()[1] / 16.0F;
        float z1 = element.to()[2] / 16.0F;

        for (Map.Entry<String, RenderModel.Face> face : element.faces().entrySet()) {
            float[][] vertices;
            float nx;
            float ny;
            float nz;
            switch (face.getKey()) {
                case "down" -> {
                    vertices = new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
                    nx = 0; ny = -1; nz = 0;
                }
                case "up" -> {
                    vertices = new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
                    nx = 0; ny = 1; nz = 0;
                }
                case "north" -> {
                    vertices = new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
                    nx = 0; ny = 0; nz = -1;
                }
                case "south" -> {
                    vertices = new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
                    nx = 0; ny = 0; nz = 1;
                }
                case "west" -> {
                    vertices = new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
                    nx = -1; ny = 0; nz = 0;
                }
                case "east" -> {
                    vertices = new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
                    nx = 1; ny = 0; nz = 0;
                }
                default -> throw new IllegalStateException("Unexpected face " + face.getKey());
            }
            drawFace(face.getValue(), vertices, nx, ny, nz, matrices, consumers, light, overlay);
        }
        matrices.pop();
    }

    private static void applyAnimationTransform(MatrixStack matrices, AnimationTransform transform, float[] pivot) {
        if (transform == null || transform.equals(AnimationTransform.IDENTITY)) {
            return;
        }
        AnimationVector position = transform.position();
        AnimationVector rotation = transform.rotation();
        AnimationVector scale = transform.scale();
        matrices.translate(position.x() / 16.0F, position.y() / 16.0F, position.z() / 16.0F);
        matrices.translate(pivot[0] / 16.0F, pivot[1] / 16.0F, pivot[2] / 16.0F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation.x()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation.y()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation.z()));
        matrices.scale(scale.x(), scale.y(), scale.z());
        matrices.translate(-pivot[0] / 16.0F, -pivot[1] / 16.0F, -pivot[2] / 16.0F);
    }

    private static void drawFace(RenderModel.Face face, float[][] vertices, float nx, float ny, float nz,
                                 MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        Identifier texture = ClientModelCatalog.INSTANCE.textures().resolve(face.texture());
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        float[] uv = face.uv();
        float[][] coordinates = {
                {uv[0] / 16.0F, uv[3] / 16.0F},
                {uv[2] / 16.0F, uv[3] / 16.0F},
                {uv[2] / 16.0F, uv[1] / 16.0F},
                {uv[0] / 16.0F, uv[1] / 16.0F}
        };
        int rotation = Math.floorMod(face.rotation() / 90, 4);
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        for (int i = 0; i < 4; i++) {
            float[] vertex = vertices[i];
            float[] tex = coordinates[Math.floorMod(i + rotation, 4)];
            consumer.vertex(position, vertex[0], vertex[1], vertex[2])
                    .color(255, 255, 255, 255)
                    .texture(tex[0], tex[1])
                    .overlay(overlay)
                    .light(light)
                    .normal(normal, nx, ny, nz)
                    .next();
        }
    }

    private static void drawFlat(String textureRef, MatrixStack matrices, VertexConsumerProvider consumers,
                                 int light, int overlay) {
        Identifier texture = ClientModelCatalog.INSTANCE.textures().resolve(textureRef);
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        float[][] front = {{0, 0, 0.5F}, {1, 0, 0.5F}, {1, 1, 0.5F}, {0, 1, 0.5F}};
        RenderModel.Face face = new RenderModel.Face(new float[]{0, 0, 16, 16}, textureRef, 0);
        drawFaceWithConsumer(face, front, 0, 0, 1, matrices, consumer, light, overlay);
        float[][] back = {{1, 0, 0.499F}, {0, 0, 0.499F}, {0, 1, 0.499F}, {1, 1, 0.499F}};
        drawFaceWithConsumer(face, back, 0, 0, -1, matrices, consumer, light, overlay);
    }

    private static void drawFaceWithConsumer(RenderModel.Face face, float[][] vertices, float nx, float ny, float nz,
                                             MatrixStack matrices, VertexConsumer consumer, int light, int overlay) {
        float[] uv = face.uv();
        float[][] coordinates = {
                {uv[0] / 16.0F, uv[3] / 16.0F}, {uv[2] / 16.0F, uv[3] / 16.0F},
                {uv[2] / 16.0F, uv[1] / 16.0F}, {uv[0] / 16.0F, uv[1] / 16.0F}
        };
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        for (int i = 0; i < 4; i++) {
            consumer.vertex(position, vertices[i][0], vertices[i][1], vertices[i][2])
                    .color(255, 255, 255, 255)
                    .texture(coordinates[i][0], coordinates[i][1])
                    .overlay(overlay).light(light).normal(normal, nx, ny, nz).next();
        }
    }
}
