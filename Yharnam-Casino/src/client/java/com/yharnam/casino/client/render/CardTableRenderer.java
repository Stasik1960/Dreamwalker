package com.yharnam.casino.client.render;

import com.yharnam.casino.CardTableEntity;
import com.yharnam.casino.CardRules;
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
            m.push();
            m.translate(x,0,z);
            if(jack) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((i-3)*19f));
            float width=jack ? .088f : .107f;
            hand(cards,0,0,FELT+.003f,width,back,m,out,light);
            if(split.length>0) hand(split,0,-.133f,FELT+.005f,.079f,back,m,out,light);
            long bet=state.getLong("Bet"+i), stack=state.getLong("Stack"+i);
            if(bet>0) counters(bet,-.173f,FELT,-.013f,.032f,m,out,light);
            if(stack>0) counters(stack,.173f,FELT,-.013f,.032f,m,out,light);
            String name=player.isEmpty() ? (jack?"":"МЕСТО "+(i+1)) : (active==i?"› ":"")+player;
            if(state.getInt("Button")==i && state.contains("Button")) name="D · "+name;
            float direction=!jack && z>.45f?-1:1;
            label(name,0,direction*.099f,FELT+.004f,.0048f,.255f,active==i?GOLD:CREAM,m,out,light);
            String detail=jack && cards.length>0 ? "Очки "+CardRules.blackjackTotal(cards)
                    +(split.length>0?" / "+CardRules.blackjackTotal(split):"") : state.getString("Combo"+i);
            label(detail,0,direction*.143f,FELT+.004f,.0044f,.250f,GOLD,m,out,light);
            if(bet>0 || stack>0) {
                String amount=Long.toString(bet)+(stack>0?" / "+stack:"");
                label(amount,0,direction*(detail.isEmpty()?.144f:.185f),FELT+.004f,.0043f,.25f,CREAM,m,out,light);
            }
            m.pop();
        }
        int[] community=state.getIntArray(jack?"DealerCards":"Board");
        if(jack) {
            float dealerWidth=.105f;
            float step=Math.min(.113f,.74f/Math.max(1,community.length-1));
            for(int i=0;i<community.length;i++)
                card(community[i],-.57f+step*(i-(community.length-1)*.5f),FELT+.006f+i*.001f,
                        -.281f,dealerWidth,.147f,back,m,out,light);
        } else hand(community,0,.006f,FELT+.005f,.143f,back,m,out,light);
        String dealer=state.getString("Dealer");
        String dealerLabel=dealer.isEmpty()?"КРУПЬЕ":"КРУПЬЕ · "+dealer;
        if(jack && community.length>0) {
            boolean hidden=community[community.length-1]<0;
            int points=hidden?CardRules.blackjackTotal(new int[]{community[0]}):CardRules.blackjackTotal(community);
            dealerLabel+=" · "+points+(hidden?" + ?":"");
        }
        label(dealerLabel,jack?-.88f:0,jack?-.413f:-.515f,FELT+.004f,.0052f,jack?.46f:.70f,GOLD,m,out,light);
        long bank=state.getLong("Bank");
        if(jack && bank>0) {
            counters(bank,.147f,.900f,-.370f,.041f,m,out,light);
            label("БАНК "+bank,.366f,-.345f,.902f,.0045f,.250f,CREAM,m,out,light);
        }
        long draft = state.getLong("DraftRaise");
        if (draft > 0)
            counters(draft, -.44f, FELT, .155f,.040f, m, out, light);
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
            label(result.isEmpty() ? phase : result, 0, jack ? .063f : .16f, FELT + .006f, .0048f, jack ? .53f : .87f,
                GOLD, m, out, light);
        m.pop();
    }

    private void label(String text, float x, float z, float y, float size, float maxWidth, int color, MatrixStack m,
        VertexConsumerProvider out, int light) {
        if(text.isEmpty()) return;
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
        long value, float x, float y, float z,float diameter, MatrixStack m, VertexConsumerProvider out, int light) {
        ChipTowers.draw(value,0,x,y,z,diameter,ChipTowers.color(value),m,out,light);
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
