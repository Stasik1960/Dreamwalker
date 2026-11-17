package com.yharnam.casino.client.render;

import com.yharnam.casino.FortuneWheelEntity;
import com.yharnam.casino.GamingFurnitureBlock;
import com.yharnam.casino.YharnamCasino;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class FortuneWheelRenderer implements BlockEntityRenderer<FortuneWheelEntity> {
    private static final RenderLayer WHEEL =
        RenderLayer.getEntityCutout(YharnamCasino.id("textures/machine/fortune_wheel.png"));
    private final MachineMesh.Part rotor = MachineMesh.load("fortune_rotor").part("rotor");
    private final TextRenderer font;

    public FortuneWheelRenderer(BlockEntityRendererFactory.Context context) {
        font = context.getTextRenderer();
    }

    @Override
    public void render(FortuneWheelEntity wheel, float tickDelta, MatrixStack matrices,
        VertexConsumerProvider consumers, int light, int overlay) {
        matrices.push();
        matrices.translate(.5, 0, .5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
            -wheel.getCachedState().get(GamingFurnitureBlock.FACING).asRotation()));
        matrices.push();
        matrices.translate(0, 1.43, -.06);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(wheel.angle(tickDelta)));
        SlotMachineRenderer.renderPart(rotor, 0, matrices, consumers.getBuffer(WHEEL), light);
        matrices.pop();
        float power = wheel.strength(tickDelta);
        String label = "";
        if (power >= 0) {
            int filled = Math.round(power * 10);
            label = "["
                + "|".repeat(filled) + "·".repeat(10 - filled) + "] " + Math.round(power * 100) + "%";
        } else if (!wheel.result().isEmpty())
            label = "Сектор " + wheel.result();
        if (!label.isEmpty()) {
            matrices.translate(0, .44, .17);
            matrices.scale(.008f, -.008f, .008f);
            font.draw(label, -font.getWidth(label) / 2f, 0, 0xfff4d9a0, false, matrices.peek().getPositionMatrix(),
                consumers, TextRenderer.TextLayerType.NORMAL, 0xa0201716, light);
        }
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(FortuneWheelEntity wheel) {
        return true;
    }
    @Override
    public int getRenderDistance() {
        return 40;
    }
}
