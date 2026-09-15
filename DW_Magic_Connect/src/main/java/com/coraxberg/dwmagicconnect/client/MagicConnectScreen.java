package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.DwMagicConnectMod;
import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MagicConnectScreen extends Screen {
    private static final Identifier DIAL = DwMagicConnectMod.id("textures/gui/arcane_dial.png");
    private static final int GOLD = 0xFFC8A46C, INK = 0xF0091423, CYAN = 0xFF91ECED;
    private final UUID token;
    private final int handOrdinal;
    private final String[][] channels = new String[3][2];
    private boolean enabled, settingsSubmitted, loading;
    private int selected, transmitIndex;
    private RadioScreenLayout layout;
    private TextFieldWidget fieldA, fieldB;
    private ButtonWidget transmitButton, stateButton;
    private final ButtonWidget[] tabs = new ButtonWidget[3];
    private Text error;

    public MagicConnectScreen(UUID token, int handOrdinal, boolean enabled, List<Frequency> frequencies, int transmitIndex) {
        super(label("title"));
        this.token = token;
        this.handOrdinal = handOrdinal;
        this.enabled = enabled;
        this.transmitIndex = Math.max(0, Math.min(2, transmitIndex));
        selected = this.transmitIndex;
        for (int i = 0; i < 3; i++) {
            channels[i][0] = i < frequencies.size() ? frequencies.get(i).a() : "";
            channels[i][1] = i < frequencies.size() ? frequencies.get(i).b() : "";
        }
    }

    private static Text label(String key, Object... args) {
        return Text.translatable("screen.dw_magic_connect." + key, args);
    }

    @Override protected void init() {
        layout = RadioScreenLayout.fit(width, height);
        int cx = width / 2, top = layout.top(), controls = layout.controlsWidth();
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            tabs[i] = button(cx - controls / 2 + i * (controls / 3), top, controls / 3 - 4, 18,
                    label("channel", i + 1), b -> switchSlot(slot));
        }
        fieldA = field(cx - controls / 4 - 29, top + 54, "A");
        fieldB = field(cx + controls / 4 - 29, top + 54, "B");
        transmitButton = button(cx - 82, top + 95, 164, 18, label("make_transmit"), b -> {
            storeFields(); transmitIndex = selected; updateButtons();
        });
        stateButton = button(cx - 72, top + 118, 144, 18, label(enabled ? "enabled" : "disabled"), b -> {
            enabled = !enabled; updateButtons();
        });
        button(cx - 72, top + 141, 144, 20, label("save"), b -> save());
        loadFields();
        setInitialFocus(fieldA);
    }

    private TextFieldWidget field(int x, int y, String name) {
        var field = new TextFieldWidget(textRenderer, x, y, 58, 18, label("frequency").copy().append(" " + name));
        field.setDrawsBackground(false);
        field.setMaxLength(3);
        field.setEditableColor(0xFFF0DFAD);
        field.setTextPredicate(s -> s.matches("[A-Za-zА-Яа-яЁё0-9]{0,3}"));
        field.setChangedListener(s -> { if (!loading) { storeFields(); error = null; } });
        return addDrawableChild(field);
    }

    private ButtonWidget button(int x, int y, int w, int h, Text text, ButtonWidget.PressAction action) {
        return addDrawableChild(new ArcaneButton(x, y, w, h, text, action));
    }
    private void storeFields() {
        if (fieldA == null || fieldB == null) return;
        channels[selected][0] = fieldA.getText();
        channels[selected][1] = fieldB.getText();
    }
    private void loadFields() {
        loading = true;
        fieldA.setText(channels[selected][0]);
        fieldB.setText(channels[selected][1]);
        loading = false;
        updateButtons();
    }
    private void switchSlot(int slot) {
        storeFields(); selected = slot; error = null; loadFields();
    }
    private void updateButtons() {
        for (int i = 0; i < 3; i++) {
            tabs[i].setMessage(Text.literal(i == selected ? "◆ " : "").append(label("channel", i + 1)));
        }
        transmitButton.setMessage(label(selected == transmitIndex ? "transmit_selected" : "make_transmit"));
        transmitButton.active = selected != transmitIndex;
        stateButton.setMessage(label(enabled ? "enabled" : "disabled"));
    }
    private void save() {
        storeFields();
        var frequencies = new ArrayList<Frequency>();
        for (int i = 0; i < 3; i++) {
            String a = channels[i][0].toUpperCase(Locale.ROOT), b = channels[i][1].toUpperCase(Locale.ROOT);
            if (a.isEmpty() != b.isEmpty() || (enabled && i == transmitIndex && a.isEmpty())) {
                switchSlot(i); error = label("invalid_frequency"); return;
            }
            frequencies.add(new Frequency(a, b));
        }
        settingsSubmitted = true;
        DwMagicConnectClient.sendSettings(token, handOrdinal, enabled, frequencies, transmitIndex);
        close();
    }
    @Override public void tick() { fieldA.tick(); fieldB.tick(); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int size = layout.diameter(), x = (width - size) / 2, y = (height - size) / 2;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        context.drawTexture(DIAL, x, y, size, size, 0, 0, 1254, 1254, 1254, 1254);
        RenderSystem.disableBlend();
        int cx = width / 2, top = layout.top(), spread = layout.controlsWidth() / 4;
        centered(context, title, cx, top - 17, 0xF3D49C);
        centered(context, label(selected == transmitIndex ? "talk_listen" : "listen_only"), cx, top + 24, 0xA2DFE0);
        centered(context, Text.literal("A"), cx - spread, top + 39, 0xF3D49C);
        centered(context, Text.literal("B"), cx + spread, top + 39, 0xF3D49C);
        frame(context, cx - spread - 34, top + 50, 68, 24, fieldA.isFocused() ? CYAN : GOLD, INK);
        frame(context, cx + spread - 34, top + 50, 68, 24, fieldB.isFocused() ? CYAN : GOLD, INK);
        centered(context, Text.literal(visibleCode(fieldA.getText()) + " : " + visibleCode(fieldB.getText())), cx, top + 80, 0xB4F7F0);
        if (error != null) {
            context.fill(cx - layout.controlsWidth() / 2, top + 21, cx + layout.controlsWidth() / 2, top + 36, 0xFF17101A);
            centered(context, error, cx, top + 24, 0xFFAE9E);
        }
        super.render(context, mouseX, mouseY, delta);
    }
    private static String visibleCode(String s) { return s.isEmpty() ? "—" : s.toUpperCase(Locale.ROOT); }
    private void centered(DrawContext c, Text text, int cx, int y, int color) {
        c.drawCenteredTextWithShadow(textRenderer, text, cx, y, color);
    }
    private static void frame(DrawContext c, int x, int y, int w, int h, int border, int fill) {
        c.fill(x + 4, y, x + w - 4, y + h, border);
        c.fill(x, y + 4, x + w, y + h - 4, border);
        c.fill(x + 5, y + 1, x + w - 5, y + h - 1, fill);
        c.fill(x + 1, y + 5, x + w - 1, y + h - 5, fill);
    }
    private final class ArcaneButton extends ButtonWidget {
        ArcaneButton(int x, int y, int w, int h, Text text, PressAction action) {
            super(x, y, w, h, text, action, DEFAULT_NARRATION_SUPPLIER);
        }
        @Override public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
            boolean hot = active && (isHovered() || isFocused());
            int border = this == stateButton && enabled ? 0xFF69DEB4 : hot ? CYAN : GOLD;
            frame(c, getX(), getY(), getWidth(), getHeight(), border, hot ? 0xFF153D4C : 0xF0102432);
            centered(c, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                    active ? (hot ? 0xC4FFFF : 0xF4DDB5) : 0x77B6B7);
        }
    }
    @Override public void removed() {
        if (!settingsSubmitted) DwMagicConnectClient.sendClose(token);
        super.removed();
    }
    @Override public boolean shouldPause() { return false; }
}
