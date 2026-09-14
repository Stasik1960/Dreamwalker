package com.coraxberg.poolbilliards.game;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import java.util.UUID;

public final class PoolBallStyleCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var game = new PoolGameState();
        UUID player = UUID.randomUUID(), observer = UUID.randomUUID();
        game.join(player, "Player");
        require(!game.setBallStyle(observer, true), "Spectator cannot change the style");
        require(game.setBallStyle(player, true), "Joined player can select white balls before shooting");
        require(game.setBallStyle(player, false) && !game.monochromeBalls, "Can switch back");
        game.setBallStyle(player, true);
        var saved = new PoolGameState();
        saved.fromNbt(game.toClientNbt());
        require(saved.monochromeBalls && saved.canChangeBallStyle(player), "NBT preserves editable style");
        require(!game.shoot(player, Double.NaN, 1) && !game.appearanceLocked, "Invalid shot must not lock style");
        require(game.shoot(player, 0, 1), "First shot accepted");
        require(!game.setBallStyle(player, false) && game.monochromeBalls, "First shot locks style on server");
        for (var ball : game.balls) { ball.vx = 0; ball.vy = 0; }
        game.tickPhysics();
        require(!game.setBallStyle(player, false), "Settling does not unlock style");
        saved.fromNbt(game.toClientNbt());
        require(saved.appearanceLocked && saved.monochromeBalls, "NBT preserves lock");
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            game.write(buffer);
            var received = PoolGameState.read(buffer);
            require(received.monochromeBalls && received.appearanceLocked, "Packet preserves style and lock");
            require(received.activePlayers.contains(player), "Packet retains participant state");
        } finally { buffer.release(); }
        game.voteReset(player, java.util.List.of(player));
        require(game.monochromeBalls && game.canChangeBallStyle(player), "New game keeps preference and unlocks style");
        require(game.balls.stream().map(b -> b.id).distinct().count() == 16, "Style must not change ball IDs");
        game.leaveUi(player);
        require(!game.setBallStyle(player, false), "Closed UI cannot change style");
        System.out.println("Ball style checks passed: access, switch, shot lock, reset, NBT and network round trips.");
    }
}
