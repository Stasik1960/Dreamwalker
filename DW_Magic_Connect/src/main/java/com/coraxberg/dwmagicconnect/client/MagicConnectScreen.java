package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.DwMagicConnectMod;
import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

/** The instrument itself is the control surface: crystal, side rocker, and clock hands. */
public final class MagicConnectScreen extends Screen {
    private static final Identifier DIAL = DwMagicConnectMod.id("textures/gui/arcane_dial.png");
    private final UUID token;
    private final int handOrdinal;
    private Frequency frequency;
    private boolean enabled, speaker, dirty;
    private int radius, saveTicks;
    private RadioScreenLayout layout;
    private final ClockDialWidget[] clocks = new ClockDialWidget[2];
    private final InstrumentControl[] controls = new InstrumentControl[4];
    private ClockDialWidget draggedClock;

    public MagicConnectScreen(UUID token, int handOrdinal, boolean enabled, Frequency frequency, boolean speaker, int radius) {
        super(label("title"));
        this.token = token; this.handOrdinal = handOrdinal; this.enabled = enabled;
        this.frequency = frequency; this.speaker = speaker; this.radius = radius;
    }
    private static Text label(String key, Object... args) {
        return Text.translatable("screen.dw_magic_connect." + key, args);
    }
    @Override protected void init() {
        draggedClock = null;
        layout = RadioScreenLayout.fit(width, height);
        for (int i = 0; i < 2; i++) {
            clocks[i] = addDrawableChild(new ClockDialWidget(width / 2 + (i == 0 ? -1 : 1) * layout.offset(),
                    layout.centerY(), layout.radius(), i == 1, () -> frequency,
                    (hand, value) -> {
                        Frequency next = frequency.withHand(hand, value);
                        if (!next.equals(frequency)) { frequency = next; changed(); }
                    }));
        }
        // Coordinates correspond to the existing lower jewel and side metalwork.
        controls[0] = control(0, .500, .867, .078, .095);
        controls[1] = control(1, .954, .570, .029, .090);
        controls[2] = control(2, .046, .545, .029, .051);
        controls[3] = control(3, .046, .610, .029, .051);
    }
    private InstrumentControl control(int kind, double u, double v, double w, double h) {
        int bw = Math.max(7, (int) (w * layout.diameter())), bh = Math.max(10, (int) (h * layout.diameter()));
        return addDrawableChild(new InstrumentControl(kind, layout.x(width, u) - bw / 2,
                layout.y(height, v) - bh / 2, bw, bh));
    }
    private void changed() { dirty = true; }
    private void flushSettings() {
        if (!dirty) return;
        DwMagicConnectClient.sendSettings(token, handOrdinal, enabled, frequency, speaker, radius);
        dirty = false; saveTicks = 0;
    }
    @Override public void tick() {
        super.tick();
        if (dirty && ++saveTicks >= 4) flushSettings();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        // Screen normally re-focuses a clicked widget AFTER mouseClicked. That clears its new
        // dragging flag on a second click of the same clock. Focus first, then start dragging.
        for (ClockDialWidget clock : clocks) {
            if (button == 0 && clock.isMouseOver(x, y)) {
                setFocused(clock);
                if (clock.mouseClicked(x, y, button)) { draggedClock = clock; return true; }
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return draggedClock != null ? draggedClock.mouseDragged(x, y, button, dx, dy)
                : super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggedClock != null) {
            draggedClock.mouseReleased(x, y, button); draggedClock = null; flushSettings(); return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c);
        int size = layout.diameter();
        RenderSystem.enableBlend();
        // Use the original saturated artwork instead of the old gray-blue dimming multiplier.
        RenderSystem.setShaderColor(1, 1, 1, 1);
        c.drawTexture(DIAL, (width - size) / 2, (height - size) / 2, size, size, 0, 0, 1254, 1254, 1254, 1254);
        if (enabled) {
            double phase = System.nanoTime() / 1_000_000_000.0;
            int alpha = (int) (23 + 9 * Math.sin(phase * 1.6));
            glow(c, .500, .065, .033, alpha);
            glow(c, .500, .867, .040, alpha + 8);
            glow(c, .087, .615, .022, alpha);
            glow(c, .910, .615, .022, alpha);
            sparkle(c, .384, .350, phase, 0);
            sparkle(c, .634, .334, phase, 1.8);
        }
        RenderSystem.disableBlend();
        centered(c, label(enabled ? "enabled" : "disabled"), width / 2, layout.y(height, .745), enabled ? 0x9FFFE8 : 0xA1A9B6);
        centered(c, label("range", radius), width / 2, layout.y(height, .778), 0xE4CFA0);
        for (int i = 0; i < 2; i++) {
            int x = width / 2 + (i == 0 ? -1 : 1) * layout.offset();
            centered(c, label(i == 0 ? "moon" : "sun"), x, layout.top(), i == 0 ? 0x95E8FF : 0xFFE09A);
        }
        super.render(c, mouseX, mouseY, delta);
        for (int i = 0; i < 2; i++) {
            int x = width / 2 + (i == 0 ? -1 : 1) * layout.offset();
            String time = frequency.configured() ? String.format(java.util.Locale.ROOT, "%02d:%02d",
                    frequency.hand(i * 2) == 0 ? 12 : frequency.hand(i * 2), frequency.hand(i * 2 + 1)) : "--:--";
            centered(c, Text.literal(time), x, layout.footer(), i == 0 ? 0x95E8FF : 0xFFE09A);
        }
        if (draggedClock == null) {
            for (InstrumentControl control : controls) if (control.isMouseOver(mouseX, mouseY)) {
                c.drawTooltip(textRenderer, control.description(), mouseX, mouseY); return;
            }
            for (ClockDialWidget clock : clocks) if (clock.isMouseOver(mouseX, mouseY)) {
                c.drawTooltip(textRenderer, label("drag_hint"), mouseX, mouseY); break;
            }
        }
    }
    private void centered(DrawContext c, Text text, int x, int y, int color) {
        float scale = Math.min(1f, layout.diameter() / 300f);
        c.getMatrices().push(); c.getMatrices().translate(x, y, 0); c.getMatrices().scale(scale, scale, 1);
        c.drawCenteredTextWithShadow(textRenderer, text, 0, 0, color); c.getMatrices().pop();
    }
    private void glow(DrawContext c, double u, double v, double extent, int alpha) {
        int x = layout.x(width, u), y = layout.y(height, v), r = Math.max(3, (int) (layout.diameter() * extent));
        for (int layer = 3; layer >= 1; layer--)
            ClockDialWidget.disk(c, x, y, r * layer / 2, ((alpha / (layer + 1)) << 24) | 0x42DFFF);
    }
    private void sparkle(DrawContext c, double u, double v, double phase, double offset) {
        int alpha = (int) (25 + 35 * (1 + Math.sin(phase + offset)) / 2);
        int x = layout.x(width, u), y = layout.y(height, v);
        c.fill(x - 2, y, x + 3, y + 1, alpha << 24 | 0xB6EFFF);
        c.fill(x, y - 2, x + 1, y + 3, alpha << 24 | 0xB6EFFF);
    }
    private final class InstrumentControl extends ClickableWidget {
        private final int kind;
        private long pressedUntil;
        InstrumentControl(int kind, int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty()); this.kind = kind; setMessage(description());
        }
        Text description() {
            return switch (kind) {
                case 0 -> label("power_hint", label(enabled ? "enabled" : "disabled"));
                case 1 -> label("speaker_hint", label(speaker ? "enabled" : "disabled"));
                case 2 -> label("volume_up", radius);
                default -> label("volume_down", radius);
            };
        }
        private void activate() {
            switch (kind) {
                case 0 -> { if (!frequency.configured()) frequency = new Frequency(0, 0, 0, 0); enabled = !enabled; }
                case 1 -> speaker = !speaker;
                case 2 -> radius = Math.min(10, radius + 1);
                case 3 -> radius = Math.max(1, radius - 1);
            }
            pressedUntil = System.nanoTime() + 140_000_000L;
            setMessage(description()); changed(); flushSettings();
            playDownSound(client.getSoundManager());
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0 || !isMouseOver(x, y)) return false;
            activate(); return true;
        }
        @Override public boolean isMouseOver(double x, double y) {
            if (!super.isMouseOver(x, y)) return false;
            if (kind != 0) return true;
            return Math.abs((x - getX() - getWidth() / 2.0) / (getWidth() / 2.0))
                    + Math.abs((y - getY() - getHeight() / 2.0) / (getHeight() / 2.0)) <= 1;
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
                activate(); return true;
            }
            return false;
        }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            builder.put(NarrationPart.TITLE, description());
        }
        @Override public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
            boolean hot = isMouseOver(mouseX, mouseY) || isFocused();
            if (kind == 0) {
                if (hot) { RenderSystem.enableBlend(); glow(c, .500, .867, .028, 48); RenderSystem.disableBlend(); }
                return;
            }
            boolean pressed = kind == 1 ? speaker : System.nanoTime() < pressedUntil;
            int x = getX() + (pressed ? (kind == 1 ? -2 : 2) : 0), y = getY(), w = getWidth(), h = getHeight();
            c.fill(getX() - 2, y - 2, getX() + w + 2, y + h + 2, 0xFF16121B);
            c.fill(x, y, x + w, y + h, hot ? 0xFFF0CB81 : 0xFF9D7943);
            c.fill(x + 1, y + 2, x + w - 1, y + h - 2, pressed ? 0xFF15394B : 0xFF332A27);
            c.fill(x + 1, y + 2, x + 2, y + h - 2, 0xFFDEC083);
            int cx = x + w / 2, cy = y + h / 2, ink = pressed ? 0xFF88EDFF : 0xFFE3CD9C;
            if (kind == 1) {
                for (int row = -1; row <= 1; row++) c.fill(cx - 1, cy + row * 3, cx + 2, cy + row * 3 + 1, ink);
            } else {
                c.fill(cx - 2, cy, cx + 3, cy + 1, ink);
                if (kind == 2) c.fill(cx, cy - 2, cx + 1, cy + 3, ink);
            }
        }
    }
    @Override public void removed() {
        flushSettings(); DwMagicConnectClient.sendClose(token); super.removed();
    }
    @Override public boolean shouldPause() { return false; }
}
