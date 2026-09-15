package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.DwMagicConnectMod;
import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MagicConnectScreen extends Screen {
    private static final Identifier DIAL = DwMagicConnectMod.id("textures/gui/arcane_dial.png");
    private final UUID token;
    private final int handOrdinal;
    private final List<Frequency> channels;
    private boolean enabled, settingsSubmitted;
    private int selected, transmitIndex;
    private RadioScreenLayout layout;
    private ButtonWidget transmitButton, stateButton;
    private final ButtonWidget[] tabs = new ButtonWidget[3];
    private final ClockDialWidget[] clocks = new ClockDialWidget[2];
    private Text error;
    public MagicConnectScreen(UUID token, int handOrdinal, boolean enabled, List<Frequency> frequencies, int tx) {
        super(label("title"));
        this.token = token; this.handOrdinal = handOrdinal; this.enabled = enabled;
        channels = new ArrayList<>(frequencies);
        transmitIndex = Math.max(0, Math.min(2, tx)); selected = transmitIndex;
    }
    private static Text label(String key, Object... args) {
        return Text.translatable("screen.dw_magic_connect." + key, args);
    }
    @Override protected void init() {
        layout = RadioScreenLayout.fit(width, height);
        int cx = width / 2, top = layout.top(), w = layout.controlsWidth();
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            tabs[i] = button(cx - w / 2 + i * (w / 3), top, w / 3 - 3, 18,
                    label("channel", i + 1), b -> { selected = slot; error = null; updateButtons(); });
        }
        for (int i = 0; i < 2; i++) {
            clocks[i] = addDrawableChild(new ClockDialWidget(cx + (i == 0 ? -1 : 1) * layout.offset(),
                    layout.centerY(), layout.radius(), i == 1, () -> channels.get(selected),
                    (hand, value) -> { channels.set(selected, channels.get(selected).withHand(hand, value)); error = null; updateButtons(); }));
        }
        int footer = layout.footer();
        transmitButton = button(cx - w / 2, footer, w * 2 / 3 - 3, 18, label("make_transmit"), b -> {
            transmitIndex = selected; error = null; updateButtons();
        });
        button(cx - w / 2 + w * 2 / 3, footer, w / 3, 18, label("clear"), b -> {
            channels.set(selected, Frequency.empty()); error = null; updateButtons();
        });
        stateButton = button(cx - 70, footer + 22, 140, 18, label("disabled"), b -> {
            enabled = !enabled; error = null; updateButtons();
        });
        button(cx - 70, footer + 44, 140, 20, label("save"), b -> save());
        updateButtons();
    }
    private ButtonWidget button(int x, int y, int w, int h, Text text, ButtonWidget.PressAction action) {
        return addDrawableChild(new ArcaneButton(x, y, w, h, text, action));
    }
    private void updateButtons() {
        for (int i = 0; i < 3; i++) tabs[i].setMessage(Text.literal(i == selected ? "◆ " : "").append(label("channel", i + 1)));
        transmitButton.setMessage(label(selected == transmitIndex ? "transmit_selected" : "make_transmit"));
        transmitButton.active = selected != transmitIndex && channels.get(selected).configured();
        stateButton.setMessage(label(enabled ? "enabled" : "disabled"));
    }
    private void save() {
        if (enabled && !channels.get(transmitIndex).configured()) {
            selected = transmitIndex; error = label("invalid_frequency"); updateButtons(); return;
        }
        settingsSubmitted = true;
        DwMagicConnectClient.sendSettings(token, handOrdinal, enabled, channels, transmitIndex);
        close();
    }
    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c);
        int size = layout.diameter(), cx = width / 2;
        RenderSystem.enableBlend();
        // Softer luminance and a slightly muted blue/gold palette, without changing the source asset.
        RenderSystem.setShaderColor(.76f, .79f, .84f, 1);
        c.drawTexture(DIAL, (width - size) / 2, (height - size) / 2, size, size, 0, 0, 1254, 1254, 1254, 1254);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        int statusY = layout.top() + 22;
        Text status = error != null ? error : !channels.get(selected).configured() ? label("not_configured")
                : label(selected == transmitIndex ? "talk_listen" : "listen_only");
        c.fill(cx - layout.controlsWidth() / 2 - 6, statusY - 2,
                cx + layout.controlsWidth() / 2 + 6, statusY + 10, 0xD0101B29);
        centered(c, status, cx, statusY, error != null ? 0xD19A87 : 0x9FB4BE);
        centered(c, label("moon"), cx - layout.offset(), layout.centerY() - layout.radius() - 12, 0xA1BECC);
        centered(c, label("sun"), cx + layout.offset(), layout.centerY() - layout.radius() - 12, 0xC6AC7C);
        super.render(c, mouseX, mouseY, delta);
        for (ClockDialWidget clock : clocks) if (clock.isMouseOver(mouseX, mouseY) && !clock.isDraggingHand()) {
            c.drawTooltip(textRenderer, label("drag_hint"), mouseX, mouseY); break;
        }
    }
    private void centered(DrawContext c, Text text, int cx, int y, int color) {
        c.drawCenteredTextWithShadow(textRenderer, text, cx, y, color);
    }
    private static void frame(DrawContext c, int x, int y, int w, int h, int border, int fill) {
        c.fill(x + 3, y, x + w - 3, y + h, border);
        c.fill(x, y + 3, x + w, y + h - 3, border);
        c.fill(x + 4, y + 1, x + w - 4, y + h - 1, fill);
        c.fill(x + 1, y + 4, x + w - 1, y + h - 4, fill);
    }
    private final class ArcaneButton extends ButtonWidget {
        ArcaneButton(int x, int y, int w, int h, Text text, PressAction action) {
            super(x, y, w, h, text, action, DEFAULT_NARRATION_SUPPLIER);
        }
        @Override public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
            boolean hot = active && (isHovered() || isFocused());
            int border = this == stateButton && enabled ? 0xFF789F8C : hot ? 0xFF9DBCC4 : 0xFF927B58;
            frame(c, getX(), getY(), getWidth(), getHeight(), border, hot ? 0xFF243743 : 0xF014232F);
            // Fit translated button labels on small GUI sizes without truncating them.
            float scale = Math.min(1f, (getWidth() - 8f) / Math.max(1, textRenderer.getWidth(getMessage())));
            c.getMatrices().push();
            c.getMatrices().translate(getX() + getWidth() / 2f, getY() + (getHeight() - 8 * scale) / 2f, 0);
            c.getMatrices().scale(scale, scale, 1);
            centered(c, getMessage(), 0, 0, active ? 0xC8B897 : 0x798C91);
            c.getMatrices().pop();
        }
    }
    @Override public void removed() {
        if (!settingsSubmitted) DwMagicConnectClient.sendClose(token);
        super.removed();
    }
    @Override public boolean shouldPause() { return false; }
}
