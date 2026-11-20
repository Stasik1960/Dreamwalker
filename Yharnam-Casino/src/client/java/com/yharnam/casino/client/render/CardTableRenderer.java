package com.yharnam.casino.client.render;

import com.yharnam.casino.CardTableEntity;
import com.yharnam.casino.GamingFurnitureBlock;
import com.yharnam.casino.YharnamCasino;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

public final class CardTableRenderer implements BlockEntityRenderer<CardTableEntity> {
    private static final RenderLayer FACES = layer("faces");
    private static final RenderLayer CLASSIC = layer("classic");
    private static final RenderLayer CHIP = layer("counter");
    private static final float FELT = .854f;
    private static final int CREAM = 0xfff3deb2, GOLD = 0xffffcd65;
    private final TextRenderer font;
    private static Peek peek;

    private record Peek(ClientWorld world, BlockPos pos, int seat, int round, int[] cards, long expires) {}

    public CardTableRenderer(BlockEntityRendererFactory.Context context) {
        font = context.getTextRenderer();
    }

    private static RenderLayer layer(String name) {
        return RenderLayer.getEntityCutout(YharnamCasino.id("textures/entity/cards/" + name + ".png"));
    }

    public static void registerPrivateHands() {
        ClientPlayNetworking.registerGlobalReceiver(CardTableEntity.PEEK, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int seat = buf.readVarInt();
            int round = buf.readVarInt();
            int[] cards = buf.readIntArray(2);
            client.execute(() -> {
                if (client.world != null)
                    peek = new Peek(client.world, pos, seat, round, cards, client.world.getTime() + 80);
            });
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> peek = null);
    }

    @Override
    public void render(
        CardTableEntity table, float delta, MatrixStack m, VertexConsumerProvider out, int light, int overlay) {
        NbtCompound state = table.display();
        boolean jack = table.blackjack();
        m.push();
        m.translate(.5, 0, .5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
            -table.getCachedState().get(GamingFurnitureBlock.FACING).asRotation()));
        m.translate(0, 0, .5);
        String texture = state.getString("Back");
        Identifier backId = Identifier.tryParse(texture);
        var back = texture.isEmpty() || backId == null ? CLASSIC : RenderLayer.getEntityCutout(backId);
        if (!texture.isEmpty()) {
            float x = jack ? .77f : 0f;
            float z = jack ? -.26f : -.36f;
            float base = jack ? .916f : FELT;
            for (int i = 0; i < 4; i++)
                card(-1, x + i * .001f, base + i * .006f, z, .12f, .166f, back, m, out, light);
        }
        if (peek != null && (peek.world != table.getWorld() || peek.expires < peek.world.getTime()))
            peek = null;
        int active = state.contains("Turn") ? state.getInt("Turn") : -1;
        for (int i = 0; i < table.seatCount(); i++) {
            float x = (float) GamingFurnitureBlock.seatX(jack, i), z = (float) GamingFurnitureBlock.seatZ(jack, i);
            String player = state.getString("Seat" + i);
            int[] cards = state.getIntArray("Cards" + i);
            if (cards.length == 2 && cards[0] < 0 && peek != null && peek.seat == i && peek.pos.equals(table.getPos())
                && peek.round == state.getInt("RoundNumber") && !player.isEmpty())
                cards = peek.cards;
            int[] split = state.getIntArray("Split" + i);
            hand(cards, x, z, FELT + .003f, split.length > 0 ? .101f : .12f, back, m, out, light);
            if (split.length > 0)
                hand(split, x, z - .112f, FELT + .016f, .101f, back, m, out, light);
            long bet = state.getLong("Bet" + i), stack = state.getLong("Stack" + i);
            if (bet > 0)
                counters(bet, x + (x > 0 ? -.115f : .115f), FELT, z + .058f, m, out, light);
            String label = player.isEmpty() ? (jack ? "" : "МЕСТО " + (i + 1)) : (active == i ? "› " : "") + player;
            if (state.contains("Button") && state.getInt("Button") == i)
                label = "D · " + label;
            float titleZ = !jack && z > .45f ? z - .15f : z + .101f;
            label(label, x, titleZ, FELT + .005f, .0060f, .32f, active == i ? GOLD : CREAM, m, out, light);
            String combo = state.getString("Combo"+i);
            if (!combo.isEmpty()) label(combo,x,titleZ-.043f,FELT+.005f,.0045f,.31f,GOLD,m,out,light);
            if (bet > 0 || stack > 0) {
                String amount = (jack ? "" : "● ") + bet + (jack || stack == 0 ? "" : " / " + stack);
                if (jack && split.length > 0 && active == i)
                    amount += " · " + (state.getInt("SplitTurn") + 1);
                label(amount, x, titleZ + .055f, FELT + .005f, .0050f, .28f, CREAM, m, out, light);
            }
        }
        int[] community = state.getIntArray(jack ? "DealerCards" : "Board");
        hand(community, jack ? -.46f : 0, jack ? -.23f : .006f, FELT + .005f, .143f, back, m, out, light);
        String dealer = state.getString("Dealer");
        label(dealer.isEmpty() ? "КРУПЬЕ" : "КРУПЬЕ · " + dealer, jack ? -.74f : 0, jack ? -.37f : -.515f, FELT + .004f,
            .006f, .70f, GOLD, m, out, light);
        long bank = state.getLong("Bank");
        if (jack && bank > 0) {
            counters(bank, -.20f, .900f, -.35f, m, out, light);
            label("БАНК " + bank, .02f, -.35f, .903f, .0046f, .38f, CREAM, m, out, light);
        }
        long draft = state.getLong("DraftRaise");
        if (draft > 0)
            counters(draft, -.32f, FELT, .19f, m, out, light);
        String result = state.getString("Result");
        String phase = switch (state.getString("Phase")) {
            case "preflop" -> "ПРЕФЛОП";
            case "flop" -> "ФЛОП";
            case "turn" -> "ТЁРН";
            case "river" -> "РИВЕР";
            case "players" -> "ХОД ИГРОКА";
            case "dealer", "deal" -> "РАЗДАЁТ КРУПЬЕ";
            case "showdown" -> "ВСКРЫТИЕ";
            default -> "";
        };
        if (draft > 0)
            phase = "ПОВЫСИТЬ НА " + draft;
        if (!result.isEmpty() || !phase.isEmpty())
            label(result.isEmpty() ? phase : result, 0, jack ? .025f : .16f, FELT + .006f, .0048f, jack ? .53f : .87f,
                GOLD, m, out, light);
        m.pop();
    }

    private void label(String text, float x, float z, float y, float size, float maxWidth, int color, MatrixStack m,
        VertexConsumerProvider out, int light) {
        m.push();
        m.translate(x, y, z);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        float scale = Math.min(size, maxWidth / Math.max(1, font.getWidth(text)));
        m.scale(scale, scale, scale);
        font.draw(text, -font.getWidth(text) / 2f, -4, color, false, m.peek().getPositionMatrix(), out,
            TextRenderer.TextLayerType.NORMAL, 0, light);
        m.pop();
    }

    private static void hand(int[] cards, float x, float z, float y, float width, RenderLayer back, MatrixStack m,
        VertexConsumerProvider out, int light) {
        float step = Math.min(width * 1.06f, (width > .14f ? .71f : .145f) / Math.max(1, cards.length - 1));
        for (int i = 0; i < cards.length; i++)
            card(cards[i], x + step * (i - (cards.length - 1) / 2f), y + i * .0014f, z, width, width * 1.42f, back, m,
                out, light);
    }

    private static void card(int id, float x, float y, float z, float w, float h, RenderLayer back, MatrixStack m,
        VertexConsumerProvider out, int light) {
        int rank = id < 0 ? 0 : id % 13, suit = id < 0 ? 0 : id / 13;
        float u = id < 0 ? 0 : (rank * 32 + .45f) / 512f, v = id < 0 ? 0 : (suit * 48 + .45f) / 256f;
        float u2 = id < 0 ? 1 : ((rank + 1) * 32 - .45f) / 512f, v2 = id < 0 ? 1 : ((suit + 1) * 48 - .45f) / 256f;
        top(out.getBuffer(id < 0 ? back : FACES), m, x, y, z, w, h, u, v, u2, v2, 0xffffff, light);
    }

    private static void counters(
        long value, float x, float y, float z, MatrixStack m, VertexConsumerProvider out, int light) {
        int levels = Math.min(5, 1 + (Long.toString(value).length() - 1));
        int rgb = value >= 1000 ? 0x75648b : value >= 100 ? 0x54718d : value >= 10 ? 0x52836b : 0xaf6c58;
        VertexConsumer buffer = out.getBuffer(CHIP);
        for (int i = 0; i < levels; i++)
            top(buffer, m, x, y + .002f + i * .008f, z - i * .002f, .086f, .086f, 0, 0, 1, 1, rgb, light);
    }

    static void top(VertexConsumer buffer, MatrixStack m, float x, float y, float z, float w, float h, float u,
        float v, float u2, float v2, int rgb, int light) {
        point(buffer, m, x - w / 2, y, z - h / 2, u, v, rgb, light);
        point(buffer, m, x - w / 2, y, z + h / 2, u, v2, rgb, light);
        point(buffer, m, x + w / 2, y, z + h / 2, u2, v2, rgb, light);
        point(buffer, m, x + w / 2, y, z - h / 2, u2, v, rgb, light);
    }

    private static void point(
        VertexConsumer b, MatrixStack m, float x, float y, float z, float u, float v, int rgb, int light) {
        b.vertex(m.peek().getPositionMatrix(), x, y, z)
            .color(rgb >> 16, (rgb >> 8) & 255, rgb & 255, 255)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(m.peek().getNormalMatrix(), 0, 1, 0)
            .next();
    }

    @Override
    public boolean rendersOutsideBoundingBox(CardTableEntity table) {
        return true;
    }
    @Override
    public int getRenderDistance() {
        return 24;
    }
}
