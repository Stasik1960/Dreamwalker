package dev.nightshift.skate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkateboardEntity;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Client-owned display state. Scores arrive from the authoritative server. */
final class SkateHud {
    private static final int COMBO_WINDOW_TICKS = 100;
    private static final int PANEL_WIDTH = 208;
    private static final int PANEL_HEIGHT = 62;
    private static final int AWARD_WIDTH = 144;
    private static final int AWARD_HEIGHT = 48;
    private static final Identifier SCORE_PANEL = NightShiftSkate.id("textures/gui/score_panel.png");
    private static final Identifier RANK_PLATE = NightShiftSkate.id("textures/gui/rank_plate.png");
    private static final Style LETTERING = Style.EMPTY.withFont(NightShiftSkate.id("score_serif"));
    private static final int IVORY = 0xFFF2DFBE;
    private static final int ANTIQUE_GOLD = 0xFFC5AD8C;
    private static int totalScore;
    private static int comboScore;
    private static int remainingTicks;
    private static int awardScore;
    private static int awardRank;
    private static int awardTicks;

    private SkateHud() {
    }

    static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(NightShiftSkate.SCORE,
                (client, handler, buffer, responseSender) -> {
                    int total = buffer.readVarInt();
                    int combo = buffer.readVarInt();
                    int remaining = buffer.readVarInt();
                    int banked = buffer.readVarInt();
                    int rank = buffer.readVarInt();
                    client.execute(() -> acceptScore(total, combo, remaining, banked, rank));
                });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                totalScore = 0;
                comboScore = 0;
                remainingTicks = 0;
                awardScore = 0;
                awardTicks = 0;
                return;
            }
            if (!client.isPaused() && remainingTicks > 0) {
                remainingTicks--;
            }
            if (!client.isPaused() && awardTicks > 0) {
                awardTicks--;
            }
        });
        HudRenderCallback.EVENT.register(SkateHud::render);
    }

    private static void acceptScore(int total, int combo, int remaining, int banked, int rank) {
        totalScore = Math.max(0, total);
        comboScore = Math.max(0, combo);
        remainingTicks = MathHelper.clamp(remaining, 0, COMBO_WINDOW_TICKS);
        if (banked > 0) {
            awardScore = banked;
            awardRank = MathHelper.clamp(rank, 0, 5);
            awardTicks = 40;
        }
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        TextRenderer text = client.textRenderer;
        boolean riding = client.player.getVehicle() instanceof SkateboardEntity;
        boolean showAward = awardTicks > 0 && awardScore > 0;
        if (!riding && !showAward) {
            return;
        }

        float screenScale = Math.min(1.0f, Math.min((context.getScaledWindowWidth() - 10) / (float) PANEL_WIDTH,
                (context.getScaledWindowHeight() * 0.5f - 12) / (PANEL_HEIGHT + AWARD_HEIGHT + 9)));
        if (screenScale <= 0) {
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() * 0.5f, 7, 0);
        context.getMatrices().scale(screenScale, screenScale, 1);
        if (riding) {
            renderScore(context, text, tickDelta);
        }
        if (showAward) {
            renderAward(context, text, riding ? PANEL_HEIGHT + 4 : 0, tickDelta);
        }
        context.getMatrices().pop();
    }

    private static void renderScore(DrawContext context, TextRenderer font, float tickDelta) {
        drawPlate(context, SCORE_PANEL, PANEL_WIDTH, PANEL_HEIGHT);
        center(context, font, Text.translatable("night_shift_skate.hud.total"), -47, 11, 1, 65, ANTIQUE_GOLD);
        center(context, font, Text.translatable("night_shift_skate.hud.combo"), 47, 11, 1, 65, ANTIQUE_GOLD);
        center(context, font, Text.literal(formatScore(totalScore)), -47, 21, 1.65f, 72, IVORY);

        boolean active = comboScore > 0 && remainingTicks > 0;
        Text combo = Text.literal(active ? "+" + formatScore(comboScore) : "—");
        center(context, font, combo, 47, 21, 1.65f, 72, active ? IVORY : 0xFF978378);

        context.fill(-50, 50, 50, 55, 0xFF795346);
        context.fill(-49, 51, 49, 54, 0xFF1C1118);
        if (active) {
            float time = Math.max(0, remainingTicks - tickDelta);
            int filled = Math.round(98 * time / COMBO_WINDOW_TICKS);
            if (filled > 0) {
                context.fillGradient(-49, 51, -49 + filled, 54, 0xFFD36766, 0xFF80293F);
            }
            Text countdown = Text.translatable("night_shift_skate.hud.countdown",
                    String.format(Locale.ROOT, "%.1f", remainingTicks / 20.0f));
            center(context, font, countdown, 0, 41, 1, 64, 0xFFD5BAA6);
        }
    }

    private static void renderAward(DrawContext context, TextRenderer font, int top, float tickDelta) {
        float life = awardTicks - tickDelta;
        float intro = MathHelper.clamp((40.0f - life) / 5.0f, 0, 1);
        float outro = MathHelper.clamp(life / 8.0f, 0, 1);
        float scale = (1.0f + (1.0f - intro) * 0.35f) * Math.min(intro, outro);
        if (scale <= 0.01f) {
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(0, top + AWARD_HEIGHT * 0.5f * (1 - scale), 0);
        context.getMatrices().scale(scale, scale, 1);
        drawPlate(context, RANK_PLATE, AWARD_WIDTH, AWARD_HEIGHT);
        center(context, font, Text.translatable("night_shift_skate.hud.rank." + awardRank), -34, 8, 2.3f, 39, rankColor(awardRank));
        center(context, font, Text.literal("+" + formatScore(awardScore)), 24, 10, 1.65f, 63, IVORY);
        center(context, font, Text.translatable("night_shift_skate.hud.banked"), 24, 28, 1, 60, ANTIQUE_GOLD);
        context.getMatrices().pop();
    }

    private static void drawPlate(DrawContext context, Identifier texture, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            context.drawTexture(texture, -width / 2, 0, width, height, 0, 0,
                    width * 4, height * 4, width * 4, height * 4);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    private static void center(DrawContext context, TextRenderer font, Text value, int x, int y, float scale,
                               int maxWidth, int color) {
        Text lettering = value.copy().setStyle(LETTERING);
        float fittingScale = Math.min(scale, maxWidth / (float) Math.max(1, font.getWidth(lettering)));
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(fittingScale, fittingScale, 1);
        context.drawCenteredTextWithShadow(font, lettering, 0, 0, color);
        context.getMatrices().pop();
    }

    private static int rankColor(int rank) {
        return switch (rank) {
            case 2 -> 0xFFBBC3C5;
            case 3 -> 0xFFE5BF85;
            case 4 -> 0xFFE7A477;
            case 5 -> 0xFFFFA49F;
            default -> 0xFFC9B6A0;
        };
    }

    private static String formatScore(int score) {
        return String.format(Locale.ROOT, "%,d", score).replace(',', ' ');
    }
}
