package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class MagicConnectScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 220;
    private static final int FIELD_WIDTH = 92;
    private static final int WIDGET_HEIGHT = 20;

    private final UUID token;
    private final int handOrdinal;
    private final boolean initiallyConfigured;
    private final int initialFrequencyA;
    private final int initialFrequencyB;

    private boolean enabled;
    private boolean settingsSubmitted;
    private int panelX;
    private int panelY;
    private TextFieldWidget frequencyAField;
    private TextFieldWidget frequencyBField;
    private ButtonWidget stateButton;
    private Text validationError;

    public MagicConnectScreen(UUID token, int handOrdinal, boolean configured, boolean enabled,
                              int frequencyA, int frequencyB) {
        super(Text.translatable("screen.dw_magic_connect.title"));
        this.token = token;
        this.handOrdinal = handOrdinal;
        this.initiallyConfigured = configured;
        this.enabled = enabled;
        this.initialFrequencyA = frequencyA;
        this.initialFrequencyB = frequencyB;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        int fieldY = panelY + 76;
        int firstFieldX = panelX + 50;
        int secondFieldX = panelX + 178;
        int maxLength = Math.max(
                Integer.toString(MagicConnectData.MIN_FREQUENCY).length(),
                Integer.toString(MagicConnectData.MAX_FREQUENCY).length()
        );

        frequencyAField = createFrequencyField(firstFieldX, fieldY, maxLength);
        frequencyBField = createFrequencyField(secondFieldX, fieldY, maxLength);
        frequencyAField.setText(initiallyConfigured ? Integer.toString(initialFrequencyA) : "");
        frequencyBField.setText(initiallyConfigured ? Integer.toString(initialFrequencyB) : "");

        stateButton = addDrawableChild(ButtonWidget.builder(stateText(), button -> {
                    enabled = !enabled;
                    button.setMessage(stateText());
                })
                .dimensions(panelX + 88, panelY + 128, 144, WIDGET_HEIGHT)
                .build());

        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.dw_magic_connect.save"),
                        button -> saveSettings()
                )
                .dimensions(panelX + 88, panelY + 174, 144, WIDGET_HEIGHT)
                .build());

        setInitialFocus(frequencyAField);
    }

    private TextFieldWidget createFrequencyField(int x, int y, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(
                textRenderer,
                x,
                y,
                FIELD_WIDTH,
                WIDGET_HEIGHT,
                Text.translatable("screen.dw_magic_connect.frequency")
        );
        field.setMaxLength(maxLength);
        field.setTextPredicate(MagicConnectScreen::isUnsignedIntegerText);
        field.setChangedListener(ignored -> validationError = null);
        return addDrawableChild(field);
    }

    private static boolean isUnsignedIntegerText(String value) {
        if (value.isEmpty()) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private Text stateText() {
        String key = enabled
                ? "screen.dw_magic_connect.enabled"
                : "screen.dw_magic_connect.disabled";
        return Text.translatable(key);
    }

    private void saveSettings() {
        Integer frequencyA = parseFrequency(frequencyAField.getText());
        Integer frequencyB = parseFrequency(frequencyBField.getText());
        if (frequencyA == null || frequencyB == null) {
            validationError = Text.translatable(
                    "screen.dw_magic_connect.invalid_frequency",
                    MagicConnectData.MIN_FREQUENCY,
                    MagicConnectData.MAX_FREQUENCY
            ).formatted(Formatting.RED);
            return;
        }

        settingsSubmitted = true;
        DwMagicConnectClient.sendSettings(token, handOrdinal, enabled, frequencyA, frequencyB);
        close();
    }

    private static Integer parseFrequency(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < MagicConnectData.MIN_FREQUENCY || parsed > MagicConnectData.MAX_FREQUENCY) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public void tick() {
        frequencyAField.tick();
        frequencyBField.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEE15191D);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 34, 0xFF243039);
        context.fill(panelX, panelY + 34, panelX + PANEL_WIDTH, panelY + 35, 0xFF4E747D);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 12, 0xE8FBFF);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.dw_magic_connect.frequency"),
                width / 2,
                panelY + 48,
                0xBFEAF0
        );

        context.drawTextWithShadow(
                textRenderer,
                Text.translatable("screen.dw_magic_connect.frequency_a"),
                panelX + 32,
                panelY + 82,
                0xFFFFFF
        );
        context.drawTextWithShadow(
                textRenderer,
                Text.translatable("screen.dw_magic_connect.frequency_b"),
                panelX + 160,
                panelY + 82,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                textRenderer,
                frequencyPreview(),
                width / 2,
                panelY + 106,
                0xA9C8CE
        );
        context.drawTextWithShadow(
                textRenderer,
                Text.translatable("screen.dw_magic_connect.state"),
                panelX + 24,
                panelY + 134,
                0xFFFFFF
        );

        if (validationError != null) {
            context.drawCenteredTextWithShadow(textRenderer, validationError, width / 2, panelY + 155, 0xFF6B6B);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private Text frequencyPreview() {
        String frequencyA = frequencyAField == null || frequencyAField.getText().isEmpty()
                ? Text.translatable("screen.dw_magic_connect.not_configured").getString()
                : frequencyAField.getText();
        String frequencyB = frequencyBField == null || frequencyBField.getText().isEmpty()
                ? Text.translatable("screen.dw_magic_connect.not_configured").getString()
                : frequencyBField.getText();
        return Text.translatable("screen.dw_magic_connect.current_frequency", frequencyA, frequencyB);
    }

    @Override
    public void removed() {
        if (!settingsSubmitted) {
            DwMagicConnectClient.sendClose(token);
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
