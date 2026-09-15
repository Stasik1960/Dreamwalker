package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import net.minecraft.client.MinecraftClient;
import com.coraxberg.dwmagicconnect.DwMagicConnectMod;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

/** Independently draggable, discrete clock hands. No texture contains interactive marks or hands. */
public final class ClockDialWidget extends ClickableWidget {
    private static final Identifier CONTROLS = DwMagicConnectMod.id("textures/gui/arcane_controls.png");
    private final boolean sun;
    private final int radius;
    private final Supplier<Frequency> frequency;
    private final BiConsumer<Integer, Integer> change;
    private int selectedHand = 1;
    private boolean dragging;
    public ClockDialWidget(int cx, int cy, int radius, boolean sun, Supplier<Frequency> frequency,
                           BiConsumer<Integer, Integer> change) {
        super(cx - radius - 5, cy - radius - 5, 2 * radius + 10, 2 * radius + 10,
                Text.translatable("screen.dw_magic_connect." + (sun ? "sun" : "moon")));
        this.radius = radius; this.sun = sun; this.frequency = frequency; this.change = change;
    }
    public boolean isDraggingHand() { return dragging; }
    private int cx() { return getX() + radius + 5; }
    private int cy() { return getY() + radius + 5; }
    private int position(int hand) { return Math.max(0, frequency.get().hand((sun ? 2 : 0) + hand)); }
    private int pick(double x, double y) {
        return ClockMath.pick(x - cx(), y - cy(), position(0), position(1), radius);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !active || !visible) return false;
        int hand = pick(x, y);
        if (hand < 0) return false;
        selectedHand = hand; dragging = true;
        // Activate an unset frequency without shifting a hand on the initial click.
        change.accept((sun ? 2 : 0) + selectedHand, position(selectedHand));
        playDownSound(MinecraftClient.getInstance().getSoundManager());
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!dragging || button != 0) return false;
        if (Math.hypot(x - cx(), y - cy()) >= 5) {
            change.accept((sun ? 2 : 0) + selectedHand,
                    ClockMath.snap(x - cx(), y - cy(), selectedHand == 0 ? 12 : 60));
        }
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !dragging) return false;
        dragging = false; return true;
    }
    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) dragging = false;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused()) return false;
        if (key == GLFW.GLFW_KEY_SPACE) { selectedHand = 1 - selectedHand; return true; }
        int step = switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> 1;
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> -1;
            default -> 0;
        };
        if (step == 0) return false;
        change.accept((sun ? 2 : 0) + selectedHand,
                Math.floorMod(position(selectedHand) + step, selectedHand == 0 ? 12 : 60));
        return true;
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
        builder.put(NarrationPart.USAGE, Text.translatable("screen.dw_magic_connect.keyboard"));
        builder.put(NarrationPart.HINT, Text.translatable("screen.dw_magic_connect.hand_position",
                Text.translatable("screen.dw_magic_connect." + (selectedHand == 0 ? "hour" : "minute")),
                position(selectedHand)));
    }
    @Override public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
        int x = cx(), y = cy();
        int brass = 0xFFCBA458, accent = sun ? 0xFFFFD584 : 0xFF83E2FF;
        disk(c, x + 2, y + 3, radius + 4, 0x60000000);
        disk(c, x, y, radius + 3, 0xFF3A3029);
        disk(c, x, y, radius + 1, brass);
        disk(c, x, y, radius - 1, 0xFF171E2A);
        disk(c, x, y, radius - 4, sun ? 0xFF171322 : 0xFF051B2C);
        ring(c, x, y, radius - 2, 0xFFFFDF9C);
        ring(c, x, y, radius - 5, 0xFF9D642B);
        for (int cardinal = 0; cardinal < 4; cardinal++) {
            double angle = cardinal * Math.PI / 2;
            double px = x + Math.sin(angle) * (radius + 1), py = y - Math.cos(angle) * (radius + 1);
            line(c, px - 3, py, px, py - 4, 1.5f, 0xFFFFDEA0);
            line(c, px, py - 4, px + 3, py, 1.5f, 0xFFD49A42);
            line(c, px + 3, py, px, py + 4, 1.5f, 0xFF996026);
            line(c, px, py + 4, px - 3, py, 1.5f, 0xFFFFDEA0);
        }
        ring(c, x, y, radius * .64, sun ? 0xFF65513C : 0xFF3B5668);
        line(c, x - radius * .62, y, x + radius * .62, y, 1, 0x403F6575);
        line(c, x, y - radius * .62, x, y + radius * .62, 1, 0x403F6575);
        for (int i = 0; i < 60; i++) {
            double outer = radius - 7, inner = outer - (i % 5 == 0 ? 5 : 2);
            line(c, x + ClockMath.x(i, 60, inner), y + ClockMath.y(i, 60, inner),
                    x + ClockMath.x(i, 60, outer), y + ClockMath.y(i, 60, outer), 1,
                    i % 5 == 0 ? accent : 0xFF535865);
        }
        int hot = dragging || isFocused() ? selectedHand : pick(mouseX, mouseY);
        drawHand(c, 0, hot == 0, frequency.get().configured());
        drawHand(c, 1, hot == 1, frequency.get().configured());

        if (isFocused()) ring(c, x, y, radius + 4, accent);

    }
    private void drawHand(DrawContext c, int hand, boolean hot, boolean configured) {
        int steps = hand == 0 ? 12 : 60;
        // Atlas pivots and gemstone centres preserve the existing grab points.
        float scale = (float) (radius * (hand == 0 ? .48 : .78) / (hand == 0 ? 274 : 428));
        c.getMatrices().push();
        c.getMatrices().translate(cx(), cy(), 0);
        c.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(position(hand) * 360f / steps));
        c.getMatrices().scale(scale, scale, 1);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        c.drawTexture(CONTROLS, -314, -542, 627, 627, hand == 0 ? 627 : 0, 0, 627, 627, 1254, 1254);
        RenderSystem.disableBlend();
        c.getMatrices().pop();
    }
    static void disk(DrawContext c, int x, int y, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            c.fill(x - dx, y + dy, x + dx + 1, y + dy + 1, color);
        }
    }
    static void ring(DrawContext c, int x, int y, double radius, int color) {
        for (int i = 0; i < 96; i++) line(c,
                x + ClockMath.x(i, 96, radius), y + ClockMath.y(i, 96, radius),
                x + ClockMath.x(i + 1, 96, radius), y + ClockMath.y(i + 1, 96, radius), 1, color);
    }
    static void line(DrawContext c, double x1, double y1, double x2, double y2, float thickness, int color) {
        c.getMatrices().push();
        c.getMatrices().translate(x1, y1, 0);
        c.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
        c.getMatrices().scale(1, thickness, 1);
        c.fill(0, 0, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1)), 1, color);
        c.getMatrices().pop();
    }
}
