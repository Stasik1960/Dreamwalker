package dev.nightshift.skate;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

final class SkateCombo {
    private static final int WINDOW_TICKS = 100;
    private static final String TOTAL_OBJECTIVE = "night_shift_pts";

    private int points;
    private long expiresAt;

    void sync(ServerPlayerEntity rider) {
        send(rider, totalPoints(rider), 0);
    }

    void add(ServerPlayerEntity rider, int award) {
        tick(rider, false);
        points = (int) Math.min(Integer.MAX_VALUE, (long) points + award);
        expiresAt = rider.getWorld().getTime() + WINDOW_TICKS;
        sync(rider);
    }

    void add(ServerPlayerEntity rider, SkateTrick trick) {
        add(rider, trick.points());
        announce(rider, trick, trick.points());
    }

    static void announce(PlayerEntity rider, SkateTrick trick, int earned) {
        if (earned <= 0 || !(rider instanceof ServerPlayerEntity serverRider)
                || serverRider.networkHandler == null
                || !ServerPlayNetworking.canSend(serverRider, NightShiftSkate.TRICK_AWARD)) {
            return;
        }
        var buf = PacketByteBufs.create();
        buf.writeVarInt(trick.ordinal());
        buf.writeVarInt(earned);
        ServerPlayNetworking.send(serverRider, NightShiftSkate.TRICK_AWARD, buf);
    }

    void tick(ServerPlayerEntity rider, boolean stopped) {
        if (points > 0 && (stopped || rider.getWorld().getTime() >= expiresAt)) {
            bank(rider);
        }
    }

    void bank(ServerPlayerEntity rider) {
        if (points == 0) {
            return;
        }
        int earned = points;
        points = 0;
        Scoreboard scoreboard = rider.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(TOTAL_OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(TOTAL_OBJECTIVE, ScoreboardCriterion.DUMMY,
                    Text.literal("Night Shift"), ScoreboardCriterion.RenderType.INTEGER);
        }
        ScoreboardPlayerScore score = scoreboard.getPlayerScore(rider.getUuidAsString(), objective);
        int total = (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, score.getScore()) + earned);
        score.setScore(total);
        send(rider, total, earned);
    }

    static int totalPoints(ServerPlayerEntity rider) {
        Scoreboard scoreboard = rider.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(TOTAL_OBJECTIVE);
        String playerKey = rider.getUuidAsString();
        return objective != null && scoreboard.playerHasObjective(playerKey, objective)
                ? scoreboard.getPlayerScore(playerKey, objective).getScore() : 0;
    }

    private void send(ServerPlayerEntity rider, int total, int banked) {
        if (rider.networkHandler == null || !ServerPlayNetworking.canSend(rider, NightShiftSkate.SCORE)) {
            return;
        }
        int remaining = points > 0 ? (int) Math.max(0, expiresAt - rider.getWorld().getTime()) : 0;
        var buf = PacketByteBufs.create();
        buf.writeVarInt(total);
        buf.writeVarInt(points);
        buf.writeVarInt(remaining);
        buf.writeVarInt(banked);
        buf.writeVarInt(rank(banked));
        ServerPlayNetworking.send(rider, NightShiftSkate.SCORE, buf);
    }

    private static int rank(int points) {
        if (points >= 1700) return 5;
        if (points >= 1000) return 4;
        if (points >= 600) return 3;
        if (points >= 300) return 2;
        return points > 0 ? 1 : 0;
    }
}
