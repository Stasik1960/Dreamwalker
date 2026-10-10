package ru.modelprops.client;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.modelprops.ModelPropItem;
import ru.modelprops.client.render.DynamicModelRenderer;
import ru.modelprops.model.ModelTransform;
import ru.modelprops.net.ModelPropsNetworking;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class DebugTransformScreen extends Screen {
    private final ItemStack stack;
    private final String modelId;
    private final ModelTransform original;
    private final double[] values;
    private final ValueSlider[] sliders = new ValueSlider[7];
    private boolean keepPreview;

    public DebugTransformScreen(ItemStack stack) {
        super(Text.translatable("screen.modelprops.debug", ModelPropItem.getModelId(stack)));
        this.stack = stack.copy();
        this.modelId = ModelPropItem.getModelId(stack);
        this.original = ClientModelCatalog.INSTANCE.savedTransform(modelId);
        this.values = new double[]{
                original.scale(), original.offsetX(), original.offsetY(), original.offsetZ(),
                original.rotationX(), original.rotationY(), original.rotationZ()
        };
        updatePreview();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(300, Math.max(210, width / 2 - 20));
        int x = width - panelWidth - 12;
        int y = 34;
        int gap = 22;

        sliders[0] = addDrawableChild(new ValueSlider(x, y, panelWidth, 0.05, 8.0, values[0],
                value -> Text.translatable("screen.modelprops.scale", value), value -> setValue(0, value)));
        sliders[1] = addDrawableChild(new ValueSlider(x, y + gap, panelWidth, -2.0, 2.0, values[1],
                value -> Text.translatable("screen.modelprops.offset_x", value), value -> setValue(1, value)));
        sliders[2] = addDrawableChild(new ValueSlider(x, y + gap * 2, panelWidth, -2.0, 2.0, values[2],
                value -> Text.translatable("screen.modelprops.offset_y", value), value -> setValue(2, value)));
        sliders[3] = addDrawableChild(new ValueSlider(x, y + gap * 3, panelWidth, -2.0, 2.0, values[3],
                value -> Text.translatable("screen.modelprops.offset_z", value), value -> setValue(3, value)));
        sliders[4] = addDrawableChild(new ValueSlider(x, y + gap * 4, panelWidth, -180.0, 180.0, values[4],
                value -> Text.translatable("screen.modelprops.rotation_x", value), value -> setValue(4, value)));
        sliders[5] = addDrawableChild(new ValueSlider(x, y + gap * 5, panelWidth, -180.0, 180.0, values[5],
                value -> Text.translatable("screen.modelprops.rotation_y", value), value -> setValue(5, value)));
        sliders[6] = addDrawableChild(new ValueSlider(x, y + gap * 6, panelWidth, -180.0, 180.0, values[6],
                value -> Text.translatable("screen.modelprops.rotation_z", value), value -> setValue(6, value)));

        int buttonY = Math.min(height - 28, y + gap * 7 + 8);
        int third = (panelWidth - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.modelprops.save"), button -> save())
                .dimensions(x, buttonY, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.modelprops.reset"), button -> reset())
                .dimensions(x + third + 4, buttonY, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.modelprops.cancel"), button -> close())
                .dimensions(x + (third + 4) * 2, buttonY, third, 20).build());
    }

    private void setValue(int index, double value) {
        values[index] = value;
        updatePreview();
    }

    private ModelTransform current() {
        return new ModelTransform(
                (float) values[0], (float) values[1], (float) values[2], (float) values[3],
                (float) values[4], (float) values[5], (float) values[6], original.revision()
        );
    }

    private void updatePreview() {
        ClientModelCatalog.INSTANCE.preview(modelId, current());
    }

    private void reset() {
        double[] identity = {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        System.arraycopy(identity, 0, values, 0, identity.length);
        for (int i = 0; i < sliders.length; i++) {
            if (sliders[i] != null) {
                sliders[i].setActual(values[i]);
            }
        }
        updatePreview();
    }

    private void save() {
        ModelTransform transform = current();
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeString(modelId, 128);
        buffer.writeVarInt(original.revision());
        buffer.writeFloat(transform.scale());
        buffer.writeFloat(transform.offsetX());
        buffer.writeFloat(transform.offsetY());
        buffer.writeFloat(transform.offsetZ());
        buffer.writeFloat(transform.rotationX());
        buffer.writeFloat(transform.rotationY());
        buffer.writeFloat(transform.rotationZ());
        ClientPlayNetworking.send(ModelPropsNetworking.SAVE_TRANSFORM, buffer);
        keepPreview = true;
        close();
    }

    @Override
    public void close() {
        if (!keepPreview) {
            ClientModelCatalog.INSTANCE.clearPreview(modelId);
        }
        if (client != null) {
            client.setScreen(null);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        renderPreview(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.modelprops.preview"), width / 4, 34, 0xA0A0A0);
    }

    private void renderPreview(DrawContext context) {
        if (client == null) {
            return;
        }
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(width / 4.0F, Math.min(height - 45.0F, height / 2.0F + 45.0F), 200.0F);
        float previewSize = Math.min(150.0F, Math.max(70.0F, width / 5.0F));
        matrices.scale(previewSize, -previewSize, previewSize);
        matrices.translate(-0.5F, -0.5F, -0.5F);
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        DynamicModelRenderer.INSTANCE.renderPreview(stack, matrices, consumers);
        consumers.draw();
        matrices.pop();
    }

    private static final class ValueSlider extends SliderWidget {
        private final double minimum;
        private final double maximum;
        private final DoubleFunction<Text> label;
        private final DoubleConsumer listener;

        private ValueSlider(int x, int y, int width, double minimum, double maximum, double actual,
                            DoubleFunction<Text> label, DoubleConsumer listener) {
            super(x, y, width, 20, Text.empty(), normalize(actual, minimum, maximum));
            this.minimum = minimum;
            this.maximum = maximum;
            this.label = label;
            this.listener = listener;
            updateMessage();
        }

        private static double normalize(double actual, double minimum, double maximum) {
            return Math.max(0.0, Math.min(1.0, (actual - minimum) / (maximum - minimum)));
        }

        private double actual() {
            return minimum + value * (maximum - minimum);
        }

        private void setActual(double actual) {
            value = normalize(actual, minimum, maximum);
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(actual()));
        }

        @Override
        protected void applyValue() {
            listener.accept(actual());
        }
    }
}
