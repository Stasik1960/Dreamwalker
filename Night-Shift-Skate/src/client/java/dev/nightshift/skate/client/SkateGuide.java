package dev.nightshift.skate.client;

import dev.nightshift.skate.SkateTrick;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

final class SkateGuide {
    private static final int MARGIN = 8;
    private static final int INSET = 9;
    private static final int HEADER_HEIGHT = 23;
    private static final int ROW_HEIGHT = 12;
    private static final int SECTION_HEIGHT = 17;
    private static final int GOLD = 0xFFD6B993;
    private static final int IVORY = 0xFFF1E5D4;

    private final KeyBinding help;
    private final KeyBinding flip;
    private final KeyBinding grab;
    private boolean visible;

    SkateGuide(KeyBinding help, KeyBinding flip, KeyBinding grab) {
        this.help = help;
        this.flip = flip;
        this.grab = grab;
    }

    void tick(boolean inGame, boolean canToggle) {
        while (help.wasPressed()) {
            if (canToggle) visible = !visible;
        }
        if (!inGame) visible = false;
    }

    int render(DrawContext context, TextRenderer font, GameOptions options) {
        if (!visible) return 0;

        List<Row> rows = rows(options);
        Text heading = helpText("title");
        Text closeKey = keyName(help);
        int widest = font.getWidth(heading) + font.getWidth(closeKey) + 16;
        int height = HEADER_HEIGHT + INSET - 4;
        for (Row row : rows) {
            if (row.keys() == null) {
                height += SECTION_HEIGHT;
            } else {
                widest = Math.max(widest, font.getWidth(row.keys()) + font.getWidth(row.description()) + 13);
                height += ROW_HEIGHT;
            }
        }
        int width = Math.max(154, widest + INSET * 2);
        int windowWidth = context.getScaledWindowWidth();
        int windowHeight = context.getScaledWindowHeight();
        float scale = Math.min(1.0f, Math.min((windowWidth * 0.36f) / width, (windowHeight - MARGIN * 2f) / height));
        if (scale <= 0) return 0;

        context.getMatrices().push();
        context.getMatrices().translate(windowWidth - MARGIN - width * scale, (windowHeight - height * scale) * 0.5f, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.fill(0, 0, width, height, 0xD8896552);
        context.fillGradient(1, 1, width - 1, height - 1, 0xF323171C, 0xEE110D13);
        context.drawTextWithShadow(font, heading, INSET, 8, IVORY);
        int closeWidth = font.getWidth(closeKey);
        context.fill(width - INSET - closeWidth - 4, 5, width - INSET + 3, 18, 0xFF51352D);
        context.drawTextWithShadow(font, closeKey, width - INSET - closeWidth, 8, GOLD);

        int y = HEADER_HEIGHT;
        for (Row row : rows) {
            if (row.keys() == null) {
                context.fill(INSET, y - 1, width - INSET, y, 0x8E775346);
                context.drawTextWithShadow(font, row.description(), INSET, y + 3, GOLD);
                y += SECTION_HEIGHT;
            } else {
                context.drawTextWithShadow(font, row.keys(), INSET, y, GOLD);
                context.drawTextWithShadow(font, row.description(),
                        width - INSET - font.getWidth(row.description()), y, IVORY);
                y += ROW_HEIGHT;
            }
        }
        context.getMatrices().pop();
        return (int) Math.ceil(width * scale) + MARGIN;
    }

    private List<Row> rows(GameOptions options) {
        Text use = keyName(options.useKey);
        Text sneak = keyName(options.sneakKey);
        Text forward = keyName(options.forwardKey);
        Text brake = keyName(options.backKey);
        Text left = keyName(options.leftKey);
        Text right = keyName(options.rightKey);
        Text jump = keyName(options.jumpKey);
        Text flipKey = keyName(flip);
        Text grabKey = keyName(grab);
        Text steer = pair(left, right);
        return List.of(
                section("ride"),
                row(use, "place"),
                row(chord(sneak, use), "dismount"),
                row(pair(forward, brake), "speed"),
                row(steer, "steer"),
                row(jump, "ollie"),
                row(jump, "rail"),
                section("ground"),
                trick(grabKey, SkateTrick.MANUAL),
                trick(chord(forward, grabKey), SkateTrick.NOSE_MANUAL),
                section("air"),
                trick(flipKey, SkateTrick.POP_SHOVE_IT),
                trick(chord(left, flipKey), SkateTrick.KICKFLIP),
                trick(chord(right, flipKey), SkateTrick.HEELFLIP),
                trick(chord(brake, flipKey), SkateTrick.SHOVE_IT_360),
                trick(chord(sneak, flipKey), SkateTrick.VARIAL_KICKFLIP),
                trick(chord(forward, flipKey), SkateTrick.FLIP_360),
                trick(grabKey, SkateTrick.INDY),
                trick(chord(left, grabKey), SkateTrick.MELON),
                trick(chord(forward, grabKey), SkateTrick.NOSEGRAB),
                trick(chord(brake, grabKey), SkateTrick.TAILGRAB),
                row(chord(steer, grabKey), "wall"));
    }

    private static Text keyName(KeyBinding binding) {
        String translationKey = binding.getBoundKeyTranslationKey();
        return switch (translationKey) {
            case "key.mouse.left", "key.mouse.right", "key.mouse.middle" -> helpText(translationKey);
            case "key.keyboard.left.shift", "key.keyboard.right.shift" -> Text.literal("Shift");
            default -> binding.getBoundKeyLocalizedText();
        };
    }

    private static Text pair(Text first, Text second) {
        return first.copy().append(" / ").append(second);
    }

    private static Text chord(Text first, Text second) {
        return first.copy().append(" + ").append(second);
    }

    private static Text helpText(String name) {
        return Text.translatable("night_shift_skate.guide." + name);
    }

    private static Row section(String name) {
        return new Row(null, helpText(name));
    }

    private static Row row(Text keys, String name) {
        return new Row(keys, helpText(name));
    }

    private static Row trick(Text keys, SkateTrick trick) {
        return new Row(keys, Text.translatable(trick.translationKey()));
    }

    private record Row(Text keys, Text description) {}
}
