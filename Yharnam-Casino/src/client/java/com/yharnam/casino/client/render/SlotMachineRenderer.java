package com.yharnam.casino.client.render;

import com.yharnam.casino.SlotMachineBlock;
import com.yharnam.casino.SlotMachineEntity;
import com.yharnam.casino.SlotStyle;
import com.yharnam.casino.YharnamCasino;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

public final class SlotMachineRenderer implements BlockEntityRenderer<SlotMachineEntity> {
    private final Map<SlotStyle, MachineMesh> models = new EnumMap<>(SlotStyle.class);
    private final Map<SlotStyle, RenderLayer> layers = new EnumMap<>(SlotStyle.class);

    public SlotMachineRenderer(BlockEntityRendererFactory.Context context) {
        for (SlotStyle style : SlotStyle.values()) {
            models.put(style, MachineMesh.load(style.id()));
            Identifier texture = new Identifier(
                    YharnamCasino.ID,
                    "textures/machine/" + style.id() + ".png"
            );
            layers.put(style, RenderLayer.getEntityCutout(texture));
        }
    }

    @Override
    public void render(
            SlotMachineEntity entity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay
    ) {
        SlotStyle style = entity.style();
        MachineMesh mesh = models.get(style);
        VertexConsumer vertices = vertexConsumers.getBuffer(layers.get(style));

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        float facingDegrees = entity.getCachedState().get(SlotMachineBlock.FACING).asRotation();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facingDegrees));

        float elapsedTicks = entity.spinStarted() >= 0 && entity.getWorld() != null
                ? entity.getWorld().getTime() - entity.spinStarted() + tickDelta
                : -1.0f;
        renderPart(
                mesh.part(MachineMesh.LEVER),
                entity.leverAngle(elapsedTicks),
                matrices,
                vertices,
                light
        );
        for (int reel = 0; reel < MachineMesh.REELS.length; reel++) {
            renderPart(
                    mesh.part(MachineMesh.REELS[reel]),
                    entity.reelAngle(reel, elapsedTicks),
                    matrices,
                    vertices,
                    light
            );
        }
        matrices.pop();
    }

    private static void renderPart(
            MachineMesh.Part part,
            float xRotationDegrees,
            MatrixStack matrices,
            VertexConsumer consumer,
            int light
    ) {
        matrices.push();
        matrices.translate(part.pivot().x, part.pivot().y, part.pivot().z);
        if (xRotationDegrees != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(xRotationDegrees));
        }

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        float[] data = part.vertices();
        // Entity cutout batches use QUADS. Repeat the final point so each source triangle
        // forms one degenerate quad without introducing another diagonal or changing UVs.
        for (int offset = 0; offset < data.length; offset += 24) {
            vertex(consumer, positionMatrix, normalMatrix, data, offset, light);
            vertex(consumer, positionMatrix, normalMatrix, data, offset + 8, light);
            vertex(consumer, positionMatrix, normalMatrix, data, offset + 16, light);
            vertex(consumer, positionMatrix, normalMatrix, data, offset + 16, light);
        }
        matrices.pop();
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f positionMatrix,
            Matrix3f normalMatrix,
            float[] data,
            int offset,
            int light
    ) {
        consumer.vertex(positionMatrix, data[offset], data[offset + 1], data[offset + 2])
                .color(255, 255, 255, 255)
                .texture(data[offset + 3], data[offset + 4])
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normalMatrix, data[offset + 5], data[offset + 6], data[offset + 7])
                .next();
    }

    @Override
    public int getRenderDistance() {
        return 48;
    }
}
