package com.yharnam.casino.client;

import com.yharnam.casino.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;

/** A passive crosshair readout. All input, including the player's chat bindings, stays with Minecraft. */
final class AttractionHints {
    static void draw(DrawContext ctx,float delta) {
        var c=MinecraftClient.getInstance();
        if (c.currentScreen!=null || c.options.hudHidden || c.world==null || c.player==null ||
                !(c.crosshairTarget instanceof BlockHitResult hit)) return;
        var state=c.world.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof GamingFurnitureBlock block)) return;
        var base=block.anchor(hit.getBlockPos(),state);
        var furniture=c.world.getBlockEntity(base);
        var facing=state.get(GamingFurnitureBlock.FACING);
        String line=null;
        if(furniture instanceof AttractionEntity game) {
            var at=GamingFurnitureBlock.attractionHit(base,facing,hit.getPos());
            line=AttractionEntity.resetButton(game.derby(),at.x,at.z)?"ПКМ · сбросить "+(game.derby()?"заезд":"рулетку"):hint(c,game,at.x,at.z);
        } else if(furniture instanceof CardTableEntity table) {
            var at=ChessLayout.local(base,facing,hit.getPos());
            if(GamingFurnitureBlock.target(table.blackjack(),at.x,at.z)==-3) line="ПКМ · сбросить партию";
            else if(table.blackjack()) {
                var display=table.display();
                for(int seat=0;seat<table.seatCount();seat++) if(display.containsUuid("SeatId"+seat) ? c.player.getUuid().equals(display.getUuid("SeatId"+seat))
                        : c.player.getGameProfile().getName().equals(display.getString("Seat"+seat))) {
                    int[] cards=display.getIntArray("Cards"+seat),split=display.getIntArray("Split"+seat);
                    if(cards.length>0) line=split.length==0?"Ваши очки · "+CardRules.blackjackTotal(cards):
                        "Ваши очки · ① "+CardRules.blackjackTotal(cards)+"   ② "+CardRules.blackjackTotal(split);
                    break;
                }
            }
        } else if(furniture instanceof ChessTableEntity) {
            var at=ChessLayout.local(base,facing,hit.getPos());
            int end=ChessLayout.buttonEnd(at.x,at.z);
            if(end!=0) line=(at.x*end>0?"↻  Новая партия":"½  Ничья")+" · нажмите такую же кнопку с обеих сторон";
        } else if(furniture instanceof FortuneWheelEntity) {
            var at=ChessLayout.local(base,facing,hit.getPos());
            if(Math.abs(at.x-.38)<.105 && Math.abs(at.y-.1875)<.105 && at.z>-.43) line="ПКМ · сбросить колесо";
        }
        if (line==null) return;
        if(c.options.debugEnabled || c.options.playerListKey.isPressed()) return;
        int maxWidth=Math.max(80,Math.min(255,ctx.getScaledWindowWidth()/2-12));
        var lines=c.textRenderer.wrapLines(net.minecraft.text.Text.literal(line),maxWidth);
        int width=lines.stream().mapToInt(c.textRenderer::getWidth).max().orElse(0);
        ctx.fill(5,5,13+width,10+lines.size()*11,0xb2191519);
        int y=8;
        for(var row:lines) {
            ctx.drawTextWithShadow(c.textRenderer,row,9,y,0xffe8d5a8);
            y+=11;
        }
    }
    private static String hint(MinecraftClient c,AttractionEntity game,double x,double z) {
        if (game.derby()) {
            if (game.moving()) return "Заезд идёт";
            if (AttractionEntity.starter(x,z)) return "ПКМ · старт четырёх дорожек";
            return game.lastLane()>=0 ? "Победитель · дорожка №"+(game.lastLane()+1) : "Кнопка старта · справа";
        }
        int role=game.role(c.player.getUuid()), mark=AttractionEntity.marker(x,z);
        var target=RouletteRound.targetAt(x,z);
        long total=0,own=0;
        if(target!=null) for(var pile:game.piles()) if(pile.target().mask()==target.mask()) {
            total+=pile.amount();
            if(pile.seat()==role) own=pile.amount();
        }
        String placed=total>0?" · на поле "+total+(own>0?" · ваши "+own:""):"";
        if (game.moving()) return "Ставки закрыты · шарик в игре"+placed;
        if (mark!=AttractionEntity.NONE) {
            String place=mark==AttractionEntity.DEALER ? "Крупье" : "Место "+(mark+1);
            if (game.name(mark).isEmpty()) return place+" · ПКМ — занять";
            if (mark!=role) return place+" · "+game.name(mark);
            long cash=role==AttractionEntity.DEALER ? game.ledger().house() : game.ledger().bankroll(role);
            if (c.player.getMainHandStack().getItem() instanceof ChipItem chip)
                return "Остаток "+cash+" · ПКМ — одна фишка · Shift+ПКМ — стак ("+chip.value()+")";
            return "Остаток "+cash+(role==AttractionEntity.DEALER?"":" · ПКМ — сменить номинал ("+game.stake(role)+")")
                +" · Shift+ПКМ пустой рукой — выйти";
        }
        if (role==AttractionEntity.DEALER && Math.hypot(x-RouletteSpin.CX,z-RouletteSpin.CZ)<.68)
            return "ПКМ · закрыть ставки и запустить шарик";
        if (target==null) return role==AttractionEntity.NONE ? "ПКМ по метке · занять место" : null;
        String field=target.label()+" · "+target.payout()+":1"+placed;
        if (role<0 || role>=AttractionEntity.DEALER) return field;
        int v=c.player.getMainHandStack().getItem() instanceof ChipItem chip ? chip.value() : game.stake(role);
        return field+"   ПКМ — "+v+"   Shift+ПКМ — отмена   Остаток "+game.ledger().bankroll(role);
    }
}
