package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.DwMagicConnectMod;
import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
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
        controls[1] = control(1, .910, .566, .043, .086);
        controls[2] = control(2, .087, .542, .043, .043);
        controls[3] = control(3, .087, .585, .043, .043);
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
        RenderSystem.setShaderColor(enabled ? 1f : .60f, enabled ? .96f : .63f, enabled ? 1f : .70f, 1);
        c.drawTexture(DIAL, (width - size) / 2, (height - size) / 2, size, size, 0, 0, 1254, 1254, 1254, 1254);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (enabled) {
            double phase = System.nanoTime() / 1_000_000_000.0;
            int alpha = (int) (58 + 13 * Math.sin(phase * 1.6));
            // Every gemstone in the static artwork has its own softly breathing aura.
            double[][] jewels = {{.500,.065,.038},{.500,.867,.048},{.087,.615,.030},
                    {.910,.615,.030},{.107,.360,.021},{.890,.360,.021},
                    {.210,.805,.023},{.790,.805,.023},{.392,.203,.014},{.606,.203,.014}};
            for (int i = 0; i < jewels.length; i++) {
                double[] jewel = jewels[i];
                mist(c, jewel[0], jewel[1], jewel[2], alpha, phase + i * .73);
            }
            halo(c, layout.x(width,.090), layout.y(height,.463), size * .070, 0x80DFFF, 48);
            halo(c, layout.x(width,.909), layout.y(height,.463), size * .070, 0xFFD580, 46);
            halo(c, layout.x(width,.500), layout.y(height,.210), size * .085, 0xFFE0A0, 25);
            sparkle(c, .384, .350, phase, 0);
            sparkle(c, .634, .334, phase, 1.8);
        }
        RenderSystem.disableBlend();
        for (int i = 0; i < 2; i++) {
            int x = width / 2 + (i == 0 ? -1 : 1) * layout.offset();
            centered(c, label(i == 0 ? "moon" : "sun"), x, layout.top(), i == 0 ? 0x95E8FF : 0xFFE09A);
        }
        super.render(c, mouseX, mouseY, delta);
        for (int clock = 0; clock < 2; clock++) {
            int x = width / 2 + (clock == 0 ? -1 : 1) * layout.offset();
            if (!enabled) {
                ClockDialWidget.disk(c, x, layout.centerY(), layout.radius(), 0x55000000);
            } else {
                halo(c, x, layout.centerY(), layout.radius() * .12, 0x7BDCFF, 55);
                for (int hand = 0; hand < 2; hand++) {
                    int position = Math.max(0, frequency.hand(clock * 2 + hand));
                    int steps = hand == 0 ? 12 : 60;
                    double length = layout.radius() * (hand == 0 ? .48 : .78);
                    halo(c, x + ClockMath.x(position, steps, length),
                            layout.centerY() + ClockMath.y(position, steps, length), layout.radius() * .12, 0x74DFFF, 45);
                }
                RenderSystem.disableBlend();
            }
        }

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
    private void mist(DrawContext c, double u, double v, double extent, int alpha, double phase) {
        double x = layout.x(width, u), y = layout.y(height, v), r = layout.diameter() * extent;
        halo(c, x, y, r * 1.8, 0x409DFF, alpha / 2);
        halo(c, x, y, r, 0xA2EFFF, alpha);
        // Three broad, faint wisps drift without spawning particles or allocating textures.
        for (int i = 0; i < 3; i++) {
            double a = phase * .35 + i * 2.094;
            halo(c, x + Math.cos(a) * r * .7, y + Math.sin(a * .8) * r * .45,
                    r * 1.05, 0x74CFFF, alpha / 5);
        }
    }
    private static void halo(DrawContext c, double x, double y, double radius, int rgb, int alpha) {
        c.draw();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var matrix = c.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        buffer.vertex(matrix, (float)x, (float)y, 0).color(red, green, blue, alpha).next();
        for (int i = 0; i <= 32; i++) {
            double angle = Math.PI * 2 * i / 32;
            buffer.vertex(matrix, (float)(x + Math.cos(angle) * radius), (float)(y + Math.sin(angle) * radius), 0)
                    .color(red, green, blue, 0).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
    private void sparkle(DrawContext c, double u, double v, double phase, double offset) {
        int alpha = (int) (25 + 35 * (1 + Math.sin(phase + offset)) / 2);
        int x = layout.x(width, u), y = layout.y(height, v);
        c.fill(x - 2, y, x + 3, y + 1, alpha << 24 | 0xB6EFFF);
        c.fill(x, y - 2, x + 1, y + 3, alpha << 24 | 0xB6EFFF);
    }
    private final class InstrumentControl extends ClickableWidget {
        private final int kind;
        private long pressedUntil, lastFrame;
        private float depression;
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
                case 2 -> radius = Math.min(com.coraxberg.dwmagicconnect.item.MagicConnectData.MAX_RADIUS, radius + 1);
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
            long now = System.nanoTime();
            boolean pressed = kind == 1 ? speaker : now < pressedUntil;
            float dt = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1_000_000_000f);
            lastFrame = now;
            depression += ((pressed ? 1f : 0f) - depression) * (1f - (float)Math.exp(-dt * 24));
            if (kind == 0) {
                // Hover must never light an unpowered instrument.
                if (hot && enabled) {
                    halo(c, getX() + getWidth()/2.0, getY() + getHeight()/2.0, getWidth()*.65, 0x8AEAFF, 30);
                    RenderSystem.disableBlend();
                }
                return;
            }
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int gold = hot ? 0xFFFFDA84 : 0xFFD1A14F;
            // A pointed brass bezel nests directly in the pendant beneath each celestial seal.
            c.fill(x - 2, y + 2, x + w + 2, y + h - 2, 0xFF4C3018);
            c.fill(x - 1, y + 1, x + w + 1, y + h - 1, gold);
            c.fill(x + 1, y + 2, x + w - 1, y + h - 2, 0xFF071320);
            ClockDialWidget.line(c, x - 2, y + 4, x + w/2.0, y - 3, 1, gold);
            ClockDialWidget.line(c, x + w/2.0, y - 3, x + w + 2, y + 4, 1, gold);
            ClockDialWidget.line(c, x - 2, y + h - 4, x + w/2.0, y + h + 3, 1, gold);
            ClockDialWidget.line(c, x + w/2.0, y + h + 3, x + w + 2, y + h - 4, 1, gold);
            c.getMatrices().push();
            c.getMatrices().translate((kind == 1 ? -1 : 1) * depression * 1.5, depression, 0);
            int cx = x + w/2, cy = y + h/2, ink = enabled && pressed ? 0xFF90EAFF : 0xFFEBCB80;
            if (kind == 1) {
                // A resonating sigil: central star inside two open magical arcs, not a loudspeaker.
                int r = Math.max(3, w/3);
                ClockDialWidget.line(c, cx, cy-r, cx+r/2.0, cy, 1, ink);
                ClockDialWidget.line(c, cx+r/2.0, cy, cx, cy+r, 1, ink);
                ClockDialWidget.line(c, cx, cy+r, cx-r/2.0, cy, 1, ink);
                ClockDialWidget.line(c, cx-r/2.0, cy, cx, cy-r, 1, ink);
                for (int side : new int[]{-1,1}) for (int i=0;i<8;i++) {
                    double a=-1.1+i*.275, b=a+.275;
                    ClockDialWidget.line(c, cx+side*Math.cos(a)*r, cy+Math.sin(a)*r*1.5,
                            cx+side*Math.cos(b)*r, cy+Math.sin(b)*r*1.5, 1, ink);
                }
                if (enabled && speaker) {
                    halo(c, cx, cy, w*.85, 0x68DFFF, 30+(int)(8*Math.sin(now/1e9*2)));
                    RenderSystem.disableBlend();
                }
            } else {
                int r = Math.max(2,w/4);
                c.fill(cx-r, cy, cx+r+1, cy+1, ink);
                if (kind == 2) c.fill(cx, cy-r, cx+1, cy+r+1, ink);
            }
            c.getMatrices().pop();

        }
    }
    @Override public void removed() {
        flushSettings(); DwMagicConnectClient.sendClose(token); super.removed();
    }
    @Override public boolean shouldPause() { return false; }
}
