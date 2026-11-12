package dev.nightshift.skate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkateTrick;
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
    private static final int TRICK_BANNER_TICKS = 32;
    private static final int PANEL_WIDTH = 192;
    private static final int PANEL_HEIGHT = 40;
    private static final int AWARD_TOP = 52;
    private static final int TIMER_BAR_GAP = 9;
    private static final int TIMER_BAR_HALF_WIDTH = 60;
    private static final int AWARD_WIDTH = 144;
    private static final int AWARD_HEIGHT = 48;
    private static final Identifier SCORE_PANEL = NightShiftSkate.id("textures/gui/score_panel.png");
    private static final Style LETTERING = Style.EMPTY.withFont(NightShiftSkate.id("score_serif"));
    private static final Style POPUP_LETTERING = Style.EMPTY.withFont(NightShiftSkate.id("popup_gothic"));
    private static final int IVORY = 0xFFF2DFBE;
    private static final int ANTIQUE_GOLD = 0xFFC5AD8C;
    private static int totalScore;
    private static int comboScore;
    private static int remainingTicks;
    private static int awardScore;
    private static int awardRank;
    private static int awardTicks;
    private static SkateTrick latestTrick = SkateTrick.NONE;
    private static int trickPoints;
    private static int trickTicks;

    private SkateHud() {
    }

    static void initialize(SkateGuide guide) {
        ClientPlayNetworking.registerGlobalReceiver(NightShiftSkate.SCORE,
                (client, handler, buffer, responseSender) -> {
                    int total = buffer.readVarInt();
                    int combo = buffer.readVarInt();
                    int remaining = buffer.readVarInt();
                    int banked = buffer.readVarInt();
                    int rank = buffer.readVarInt();
                    client.execute(() -> acceptScore(total, combo, remaining, banked, rank));
                });
        ClientPlayNetworking.registerGlobalReceiver(NightShiftSkate.TRICK_AWARD,
                (client, handler, buffer, responseSender) -> {
                    SkateTrick trick = SkateTrick.fromId(buffer.readVarInt());
                    int earned = buffer.readVarInt();
                    if (trick != SkateTrick.NONE && earned > 0) {
                        client.execute(() -> acceptTrick(trick, earned));
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            guide.tick(client.player != null, client.player != null && client.currentScreen == null);
            if (client.player == null) {
                totalScore = 0;
                comboScore = 0;
                remainingTicks = 0;
                awardScore = 0;
                awardTicks = 0;
                trickTicks = 0;
                return;
            }
            if (!client.isPaused() && remainingTicks > 0) {
                remainingTicks--;
            }
            if (!client.isPaused() && awardTicks > 0) {
                awardTicks--;
            }
            if (!client.isPaused() && trickTicks > 0) {
                trickTicks--;
            }
        });
        HudRenderCallback.EVENT.register((context, tickDelta) -> render(context, tickDelta, guide));
    }

    private static void acceptTrick(SkateTrick trick, int earned) {
        latestTrick = trick;
        trickPoints = earned;
        trickTicks = TRICK_BANNER_TICKS;
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

    private static void render(DrawContext context, float tickDelta, SkateGuide guide) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        TextRenderer text = client.textRenderer;
        boolean riding = client.player.getVehicle() instanceof SkateboardEntity;
        boolean showAward = awardTicks > 0 && awardScore > 0;
        if (riding || showAward) {
            renderTopPanel(context, text, tickDelta, riding, showAward);
        }
        int sidebarWidth = guide.render(context, text, client.options);
        if (trickTicks > 0) {
            renderTrick(context, text, tickDelta, sidebarWidth);
        }
    }

    private static void renderTopPanel(DrawContext context, TextRenderer text, float tickDelta,
                                       boolean riding, boolean showAward) {
        float screenScale = Math.min(1.0f, Math.min((context.getScaledWindowWidth() - 10) / (float) PANEL_WIDTH,
                (context.getScaledWindowHeight() * 0.5f - 12) / (AWARD_TOP + AWARD_HEIGHT + 5)));
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
            renderAward(context, text, riding ? AWARD_TOP : 0, tickDelta);
        }
        context.getMatrices().pop();
    }

    private static void renderTrick(DrawContext context, TextRenderer font, float tickDelta, int sidebarWidth) {
        float life = trickTicks - tickDelta;
        float elapsed = TRICK_BANNER_TICKS - life;
        float opacity = MathHelper.clamp(Math.min(elapsed / 2f, life / 7f), 0.0f, 1.0f);
        if (opacity < 0.07f) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int maxWidth = Math.max(24, Math.min(width * 3 / 4, width - 2 * sidebarWidth - 16));
        Text name = popupText(latestTrick.englishName());
        int nameWidth = font.getWidth(name);
        float pop = 1.0f + 0.16f * (1.0f - MathHelper.clamp(elapsed / 6.0f, 0, 1));
        float nameScale = Math.min(MathHelper.clamp(height / 85f, 2.2f, 4.3f) * pop,
                maxWidth / (float) Math.max(1, nameWidth));
        float pointsScale = Math.min(2.1f, nameScale * 0.82f);
        int nameHeight = Math.round(font.fontHeight * nameScale);
        int pointsHeight = Math.round(font.fontHeight * pointsScale);
        int top = -(nameHeight + pointsHeight + 9) / 2;
        context.getMatrices().push();
        context.getMatrices().translate(width * 0.5f, height * 0.5f, 0);
        drawCentered(context, font, name, 0, top, nameScale, maxWidth, fade(IVORY, opacity));
        drawCentered(context, font, popupText("+" + formatScore(trickPoints)), 0, top + nameHeight + 6,
                pointsScale, maxWidth, fade(0xFFE7BF83, opacity));
        context.getMatrices().pop();
    }

    private static int fade(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0x00FFFFFF);
    }

    private static void renderScore(DrawContext context, TextRenderer font, float tickDelta) {
        drawPlate(context, SCORE_PANEL, PANEL_WIDTH, PANEL_HEIGHT);
        center(context, font, Text.translatable("night_shift_skate.hud.total"), -40, 13, 1, 65, ANTIQUE_GOLD);
        center(context, font, Text.translatable("night_shift_skate.hud.combo"), 40, 13, 1, 65, ANTIQUE_GOLD);
        center(context, font, Text.literal(formatScore(totalScore)), -40, 18, 1.4f, 66, IVORY);

        boolean active = comboScore > 0 && remainingTicks > 0;
        Text combo = Text.literal(active ? "+" + formatScore(comboScore) : "—");
        center(context, font, combo, 40, 18, 1.4f, 66, active ? IVORY : 0xFF978378);

        if (active) {
            float time = Math.max(0, remainingTicks - tickDelta);
            int filled = Math.round(TIMER_BAR_HALF_WIDTH * time / COMBO_WINDOW_TICKS);
            if (filled > 0) {
                context.fillGradient(-TIMER_BAR_GAP - filled, 30, -TIMER_BAR_GAP, 32, 0xFFE18B7D, 0xFFA43752);
                context.fillGradient(TIMER_BAR_GAP, 30, TIMER_BAR_GAP + filled, 32, 0xFFE18B7D, 0xFFA43752);
            }
            Text countdown = Text.translatable("night_shift_skate.hud.countdown",
                    String.format(Locale.ROOT, "%.1f", remainingTicks / 20.0f));
            center(context, font, countdown, 0, PANEL_HEIGHT, 1, 64, 0xFFF0D4BB);
        }
    }

    private static void renderAward(DrawContext context, TextRenderer font, int top, float tickDelta) {
        float life = awardTicks - tickDelta;
        float intro = MathHelper.clamp((40.0f - life) / 5.0f, 0, 1);
        float outro = MathHelper.clamp(life / 8.0f, 0, 1);
        float opacity = Math.min(intro, outro);
        if (opacity < 0.07f) {
            return;
        }
        float scale = (1.0f + (1.0f - intro) * 0.35f) * opacity;
        context.getMatrices().push();
        context.getMatrices().translate(0, top + AWARD_HEIGHT * 0.5f * (1 - scale), 0);
        context.getMatrices().scale(scale, scale, 1);
        Text rank = Text.translatable("night_shift_skate.hud.rank." + awardRank).setStyle(POPUP_LETTERING);
        drawCentered(context, font, rank, 0, 0, 3.4f, AWARD_WIDTH, fade(rankColor(awardRank), opacity));
        drawCentered(context, font, popupText(formatScore(awardScore)), 0, 34, 1.65f, AWARD_WIDTH, fade(IVORY, opacity));
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
        drawCentered(context, font, value.copy().setStyle(LETTERING), x, y, scale, maxWidth, color);
    }

    private static Text popupText(String value) {
        return Text.literal(value).setStyle(POPUP_LETTERING);
    }

    private static void drawCentered(DrawContext context, TextRenderer font, Text value, int x, int y, float scale,
                                     int maxWidth, int color) {
        float fittingScale = Math.min(scale, maxWidth / (float) Math.max(1, font.getWidth(value)));
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(fittingScale, fittingScale, 1);
        context.drawCenteredTextWithShadow(font, value, 0, 0, color);
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
