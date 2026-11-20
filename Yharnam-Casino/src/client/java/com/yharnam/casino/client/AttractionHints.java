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
        if (!(state.getBlock() instanceof GamingFurnitureBlock block) || !block.attraction()) return;
        var base=block.anchor(hit.getBlockPos(),state);
        if (!(c.world.getBlockEntity(base) instanceof AttractionEntity game)) return;
        var at=GamingFurnitureBlock.attractionHit(base,state.get(GamingFurnitureBlock.FACING),hit.getPos());
        var line=hint(c,game,at.x,at.z);
        if (line==null) return;
        int width=c.textRenderer.getWidth(line);
        float scale=Math.min(1f,(ctx.getScaledWindowWidth()-14f)/Math.max(1,width));
        var m=ctx.getMatrices();
        m.push();
        m.translate(ctx.getScaledWindowWidth()/2f,ctx.getScaledWindowHeight()-73,0);
        m.scale(scale,scale,1);
        ctx.fill(-width/2-4,-3,width/2+4,11,0xaa191519);
        ctx.drawTextWithShadow(c.textRenderer,line,-width/2,0,0xffe8d5a8);
        m.pop();
    }
    private static String hint(MinecraftClient c,AttractionEntity game,double x,double z) {
        if (game.derby()) {
            if (game.moving()) return "Заезд идёт";
            if (AttractionEntity.starter(x,z)) return "ПКМ · старт четырёх дорожек";
            return game.lastLane()>=0 ? "Победитель · дорожка №"+(game.lastLane()+1) : "Кнопка старта · справа";
        }
        if (game.moving()) return "Ставки закрыты · шарик в игре";
        int role=game.role(c.player.getUuid()), mark=AttractionEntity.marker(x,z);
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
        var target=RouletteRound.targetAt(x,z);
        if (target==null) return role==AttractionEntity.NONE ? "ПКМ по метке · занять место" : null;
        String field=target.label()+" · "+target.payout()+":1";
        if (role<0 || role>=AttractionEntity.DEALER) return field;
        int v=c.player.getMainHandStack().getItem() instanceof ChipItem chip ? chip.value() : game.stake(role);
        return field+"   ПКМ — "+v+"   Shift+ПКМ — отмена   Остаток "+game.ledger().bankroll(role);
    }
}
