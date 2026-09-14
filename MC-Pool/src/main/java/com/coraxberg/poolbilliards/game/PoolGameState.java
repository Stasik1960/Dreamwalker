package com.coraxberg.poolbilliards.game;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;

import java.util.*;

public class PoolGameState {
    public static final double TABLE_W = 900.0;
    public static final double TABLE_H = 490.0;
    public static final double BALL_R = 12.0;
    public static final double POCKET_R = 38.0;
    private static final double BALL_RESTITUTION = 0.96;
    private static final double CUSHION_RESTITUTION = 0.84;
    private static final double CUSHION_TANGENT_RETENTION = 0.985;

    public enum SoundType { BALL, CUSHION, POCKET }
    public record PhysicsSound(SoundType type, double x, double y, double strength) {}
    // The loudest event of each kind is enough during a busy break. Sounds are
    // transient and deliberately never saved or sent with the game state.
    private final EnumMap<SoundType, PhysicsSound> physicsSounds = new EnumMap<>(SoundType.class);

    public List<PhysicsSound> drainPhysicsSounds() {
        List<PhysicsSound> sounds = List.copyOf(physicsSounds.values());
        physicsSounds.clear();
        return sounds;
    }

    private void sound(SoundType type, double x, double y, double strength) {
        PhysicsSound previous = physicsSounds.get(type);
        if (previous == null || strength > previous.strength()) {
            physicsSounds.put(type, new PhysicsSound(type, x, y, strength));
        }
    }

    public final ArrayList<PoolBall> balls = new ArrayList<>();
    /** Все игроки, которые участвовали в текущей партии. Счёт этих игроков хранится до сброса стола. */
    public final ArrayList<UUID> players = new ArrayList<>();
    /** Только игроки, у которых прямо сейчас открыт интерфейс этого стола. */
    public final LinkedHashSet<UUID> activePlayers = new LinkedHashSet<>();
    public final LinkedHashMap<UUID, String> playerNames = new LinkedHashMap<>();
    public final LinkedHashMap<UUID, Integer> scores = new LinkedHashMap<>();
    public final HashSet<UUID> resetVotes = new HashSet<>();

    public int currentTurn = 0;
    public boolean started = false;
    public boolean gameOver = false;
    public boolean monochromeBalls = false;
    public boolean appearanceLocked = false;
    public String status = "Ожидание игроков";
    public UUID winner = null;
    private boolean shotInProgress = false;
    private final ArrayList<Integer> pocketedThisShot = new ArrayList<>();

    public PoolGameState() {
        resetTable();
    }

    public void join(UUID uuid, String name) {
        playerNames.put(uuid, name);
        if (gameOver) {
            // После завершения партии можно смотреть стол, но новый участник не добавляется до сброса.
            return;
        }
        if (!players.contains(uuid)) {
            if (players.size() >= 3) {
                status = "Мест за столом нет. Наблюдение";
                return;
            }
            players.add(uuid);
            scores.put(uuid, 0);
        }
        activePlayers.add(uuid);
        if (!started) started = true;
        normalizeTurn();
        status = "Ходит: " + getCurrentPlayerName();
    }

    public void leaveUi(UUID uuid) {
        activePlayers.remove(uuid);
        resetVotes.remove(uuid);
        if (!gameOver) {
            normalizeTurn();
            if (activePlayers.isEmpty()) {
                status = "Все вышли из интерфейса";
            } else {
                status = "Ходит: " + getCurrentPlayerName();
            }
        }
    }

    public void syncActivePlayers(Collection<UUID> active) {
        LinkedHashSet<UUID> next = new LinkedHashSet<>();
        for (UUID id : players) {
            if (active.contains(id)) next.add(id);
        }
        activePlayers.clear();
        activePlayers.addAll(next);
        resetVotes.retainAll(activePlayers);
        if (!gameOver) normalizeTurn();
    }

    public boolean isParticipant(UUID uuid) {
        return players.contains(uuid);
    }

    private ArrayList<UUID> getTurnPlayers() {
        ArrayList<UUID> list = new ArrayList<>();
        for (UUID id : players) {
            if (activePlayers.contains(id)) list.add(id);
        }
        return list;
    }

    private void normalizeTurn() {
        ArrayList<UUID> list = getTurnPlayers();
        if (list.isEmpty()) {
            currentTurn = 0;
        } else {
            currentTurn = Math.floorMod(currentTurn, list.size());
        }
    }

    public String getCurrentPlayerName() {
        UUID id = getCurrentPlayerId();
        if (id == null) return "никто";
        return playerNames.getOrDefault(id, "Игрок");
    }

    public UUID getCurrentPlayerId() {
        ArrayList<UUID> list = getTurnPlayers();
        if (list.isEmpty()) return null;
        return list.get(Math.floorMod(currentTurn, list.size()));
    }

    public boolean canShoot(UUID uuid) {
        return started && !gameOver && activePlayers.contains(uuid) && Objects.equals(getCurrentPlayerId(), uuid) && !areBallsMoving();
    }

    public boolean shoot(UUID uuid, double angle, double power) {
        if (!canShoot(uuid) || !Double.isFinite(angle) || !Double.isFinite(power)) return false;
        PoolBall cue = getBall(0);
        if (cue == null) return false;
        if (cue.pocketed) {
            cue.pocketed = false;
            cue.x = 230;
            cue.y = TABLE_H / 2.0;
        }
        double p = Math.max(0.0, Math.min(1.0, power));
        // Full power is about 10% gentler than the previous version.
        // soft shots still remain useful for close positional play.
        double speed = 8.0 + p * 50.5;
        cue.vx = Math.cos(angle) * speed;
        cue.vy = Math.sin(angle) * speed;
        shotInProgress = true;
        appearanceLocked = true;
        pocketedThisShot.clear();
        resetVotes.clear();
        status = "Удар: " + getCurrentPlayerName();
        return true;
    }

    public void removeResetVote(UUID uuid) {
        resetVotes.remove(uuid);
    }

    public boolean canChangeBallStyle(UUID uuid) {
        return !appearanceLocked && !gameOver && !areBallsMoving()
                && players.contains(uuid) && activePlayers.contains(uuid);
    }

    public boolean setBallStyle(UUID uuid, boolean monochrome) {
        if (!canChangeBallStyle(uuid)) return false;
        monochromeBalls = monochrome;
        return true;
    }

    public void voteReset(UUID uuid, Collection<UUID> activeUiPlayers) {
        if (!players.contains(uuid) || !activePlayers.contains(uuid) || !activeUiPlayers.contains(uuid)) return;

        LinkedHashSet<UUID> required = new LinkedHashSet<>();
        for (UUID p : players) {
            if (activePlayers.contains(p) && activeUiPlayers.contains(p)) required.add(p);
        }
        resetVotes.retainAll(required);
        resetVotes.add(uuid);

        status = "Сброс: " + resetVotes.size() + "/" + required.size();
        if (!required.isEmpty() && resetVotes.containsAll(required)) {
            LinkedHashMap<UUID, String> oldNames = new LinkedHashMap<>(playerNames);
            ArrayList<UUID> activeNow = new ArrayList<>(required);
            resetTable();
            playerNames.putAll(oldNames);
            for (UUID p : activeNow) {
                if (players.size() < 3) {
                    players.add(p);
                    activePlayers.add(p);
                    scores.put(p, 0);
                }
            }
            started = !activePlayers.isEmpty();
            status = started ? "Новая партия. Ходит: " + getCurrentPlayerName() : "Ожидание игроков";
        }
    }

    public void resetTable() {
        balls.clear();
        players.clear();
        activePlayers.clear();
        scores.clear();
        resetVotes.clear();
        currentTurn = 0;
        started = false;
        gameOver = false;
        winner = null;
        shotInProgress = false;
        appearanceLocked = false;
        pocketedThisShot.clear();
        physicsSounds.clear();
        status = "Ожидание игроков";
        rackBalls();
    }

    private void rackBalls() {
        balls.add(new PoolBall(0, 230, TABLE_H / 2.0));
        double startX = 635;
        double startY = TABLE_H / 2.0;
        int id = 1;
        double dx = BALL_R * 1.82;
        double dy = BALL_R * 2.08;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= row; col++) {
                if (id > 15) return;
                double x = startX + row * dx;
                double y = startY + (col - row / 2.0) * dy;
                int ballId = id;
                // Swap 5 and 8 so the black eight sits in the centre without
                // duplicating a numbered ball (the old swap produced two 9s).
                if (id == 5) ballId = 8;
                else if (id == 8) ballId = 5;
                balls.add(new PoolBall(ballId, x, y));
                id++;
            }
        }
    }

    public PoolBall getBall(int id) {
        for (PoolBall b : balls) if (b.id == id) return b;
        return null;
    }

    public boolean areBallsMoving() {
        for (PoolBall b : balls) if (!b.pocketed && b.vx * b.vx + b.vy * b.vy > 0.015) return true;
        return false;
    }

    public boolean tickPhysics() {
        if (!areBallsMoving() && !shotInProgress) return false;

        physicsSounds.clear();

        double fastest = 0;
        for (PoolBall ball : balls) {
            if (!ball.pocketed) fastest = Math.max(fastest, Math.hypot(ball.vx, ball.vy));
        }
        // Keep each movement below a ball radius at high shot powers, so balls
        // do not pass through one another between collision checks.
        int steps = Math.max(3, Math.min(12, (int) Math.ceil(fastest / (BALL_R * 0.85))));
        boolean changed = false;
        for (int step = 0; step < steps; step++) {
            moveBalls(1.0 / steps);
            // Two passes in opposite order let an impact travel through a
            // tightly packed rack without favouring low-numbered balls.
            resolveCollisions(false);
            resolveCollisions(true);
            changed = true;
        }

        for (PoolBall b : balls) {
            if (!b.pocketed) {
                // Multiplicative damping preserves the momentum of balls that
                // leave a dense collision with a modest speed. Constant drag
                // stopped those balls almost immediately and made only the
                // outside balls of a rack disperse.
                b.vx *= 0.97;
                b.vy *= 0.97;
                if (Math.hypot(b.vx, b.vy) < 1.5) {
                    b.vx *= 0.85;
                    b.vy *= 0.85;
                }
                if (Math.hypot(b.vx, b.vy) < 0.15) {
                    b.vx = 0;
                    b.vy = 0;
                }
            }
        }

        if (shotInProgress && !areBallsMoving()) {
            finishShot();
            changed = true;
        }
        return changed;
    }

    private void moveBalls(double dt) {
        for (PoolBall b : balls) {
            if (b.pocketed) continue;
            b.x += b.vx * dt;
            b.y += b.vy * dt;

            if (isInPocket(b.x, b.y)) {
                sound(SoundType.POCKET, b.x, b.y, Math.hypot(b.vx, b.vy));
                b.pocketed = true;
                b.vx = 0;
                b.vy = 0;
                pocketedThisShot.add(b.id);
                continue;
            }

            collideRails(b);

        }
    }

    private void soundCushion(PoolBall ball, double normalSpeed) {
        if (normalSpeed > 2.0) sound(SoundType.CUSHION, ball.x, ball.y, normalSpeed);
    }

    private boolean isInPocket(double x, double y) {
        for (PoolTableGeometry.Pocket pocket : PoolTableGeometry.POCKETS) {
            if (pocket.captures(x, y)) return true;
        }
        return false;
    }

    private void collideRails(PoolBall ball) {
        // Circle versus the exact baked rail/jaw rectangles. Selecting the
        // deepest contact avoids pushing against internal strip boundaries.
        for (int pass = 0; pass < 3; pass++) {
            double deepest = 0, normalX = 0, normalY = 0;
            for (PoolTableGeometry.Rail rail : PoolTableGeometry.RAILS) {
                if (ball.x < rail.x0() - BALL_R || ball.x > rail.x1() + BALL_R
                        || ball.y < rail.y0() - BALL_R || ball.y > rail.y1() + BALL_R) continue;
                double nearX = Math.max(rail.x0(), Math.min(rail.x1(), ball.x));
                double nearY = Math.max(rail.y0(), Math.min(rail.y1(), ball.y));
                double dx = ball.x - nearX, dy = ball.y - nearY;
                double distance = Math.hypot(dx, dy);
                double depth, nx, ny;
                if (distance > 1e-9) {
                    depth = BALL_R - distance;
                    nx = dx / distance;
                    ny = dy / distance;
                } else {
                    double left = ball.x - rail.x0(), right = rail.x1() - ball.x;
                    double top = ball.y - rail.y0(), bottom = rail.y1() - ball.y;
                    double nearest = Math.min(Math.min(left, right), Math.min(top, bottom));
                    depth = BALL_R + nearest;
                    nx = nearest == left ? -1 : nearest == right ? 1 : 0;
                    ny = nx != 0 ? 0 : nearest == top ? -1 : 1;
                }
                if (depth > deepest) { deepest = depth; normalX = nx; normalY = ny; }
            }
            if (deepest <= 0) break;
            ball.x += normalX * (deepest + 1e-6);
            ball.y += normalY * (deepest + 1e-6);
            double normalSpeed = ball.vx * normalX + ball.vy * normalY;
            if (normalSpeed < 0) {
                soundCushion(ball, -normalSpeed);
                double tangentX = ball.vx - normalSpeed * normalX;
                double tangentY = ball.vy - normalSpeed * normalY;
                ball.vx = tangentX * CUSHION_TANGENT_RETENTION - normalSpeed * CUSHION_RESTITUTION * normalX;
                ball.vy = tangentY * CUSHION_TANGENT_RETENTION - normalSpeed * CUSHION_RESTITUTION * normalY;
            }
        }
    }

    private void resolveCollisions(boolean reverse) {
        for (int index = 0; index < balls.size(); index++) {
            int i = reverse ? balls.size() - 1 - index : index;
            PoolBall a = balls.get(i);
            if (a.pocketed) continue;
            for (int other = 0; other < balls.size(); other++) {
                int j = reverse ? balls.size() - 1 - other : other;
                if (j <= i) continue;
                PoolBall b = balls.get(j);
                if (b.pocketed) continue;
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double dist2 = dx * dx + dy * dy;
                double min = BALL_R * 2.0;
                if (dist2 < min * min) {
                    double dist = Math.sqrt(dist2);
                    // Coincident centres can occur in edited/legacy saves.
                    double nx = dist > 1e-9 ? dx / dist : 1.0;
                    double ny = dist > 1e-9 ? dy / dist : 0.0;
                    double overlap = min - dist;
                    double correction = Math.max(0.0, overlap - 0.01) * 0.5;
                    a.x -= nx * correction;
                    a.y -= ny * correction;
                    b.x += nx * correction;
                    b.y += ny * correction;

                    double dvx = b.vx - a.vx;
                    double dvy = b.vy - a.vy;
                    double closingSpeed = -(dvx * nx + dvy * ny);
                    if (closingSpeed > 0) {
                        // Equal-mass impulse conserves momentum. Low-speed
                        // contacts lose a little more energy to prevent chatter.
                        double restitution = closingSpeed < 0.75 ? 0.55 : BALL_RESTITUTION;
                        double impulse = (1.0 + restitution) * closingSpeed * 0.5;
                        a.vx -= impulse * nx;
                        a.vy -= impulse * ny;
                        b.vx += impulse * nx;
                        b.vy += impulse * ny;
                        if (closingSpeed > 1.5) {
                            sound(SoundType.BALL, (a.x + b.x) * 0.5, (a.y + b.y) * 0.5, closingSpeed);
                        }
                    }
                }
            }
        }
    }

    private void finishShot() {
        shotInProgress = false;
        UUID current = getCurrentPlayerId();
        if (current == null) return;
        boolean scored = false;
        boolean cuePocketed = false;
        boolean eightPocketed = false;
        for (int id : pocketedThisShot) {
            if (id == 0) cuePocketed = true;
            else if (id == 8) eightPocketed = true;
            else scored = true;
        }

        if (scored) scores.put(current, scores.getOrDefault(current, 0) + countObjectBalls(pocketedThisShot));

        if (eightPocketed) {
            int activeCount = Math.max(1, activePlayers.size());
            int needed = activeCount >= 3 ? 5 : 7;
            if (scores.getOrDefault(current, 0) >= needed) {
                gameOver = true;
                winner = current;
                status = "Победа: " + playerNames.getOrDefault(current, "Игрок");
            } else {
                gameOver = true;
                status = playerNames.getOrDefault(current, "Игрок") + " забил восьмёрку слишком рано";
            }
            return;
        }

        if (cuePocketed) {
            PoolBall cue = getBall(0);
            if (cue != null) {
                cue.pocketed = false;
                cue.x = 230;
                cue.y = TABLE_H / 2.0;
            }
            advanceTurn();
            status = "Биток в лузе. Ходит: " + getCurrentPlayerName();
        } else if (scored) {
            status = "Ход продолжается: " + getCurrentPlayerName();
        } else {
            advanceTurn();
            status = "Ходит: " + getCurrentPlayerName();
        }
        pocketedThisShot.clear();
    }

    private int countObjectBalls(List<Integer> ids) {
        int n = 0;
        for (int id : ids) if (id >= 1 && id <= 15 && id != 8) n++;
        return n;
    }

    private void advanceTurn() {
        ArrayList<UUID> list = getTurnPlayers();
        if (!list.isEmpty()) currentTurn = (currentTurn + 1) % list.size();
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        NbtList ballList = new NbtList();
        for (PoolBall b : balls) ballList.add(b.toNbt());
        n.put("balls", ballList);
        NbtList playerList = new NbtList();
        for (UUID id : players) playerList.add(NbtString.of(id.toString()));
        n.put("players", playerList);
        NbtCompound names = new NbtCompound();
        for (Map.Entry<UUID, String> e : playerNames.entrySet()) names.putString(e.getKey().toString(), e.getValue());
        n.put("names", names);
        NbtCompound scoreNbt = new NbtCompound();
        for (Map.Entry<UUID, Integer> e : scores.entrySet()) scoreNbt.putInt(e.getKey().toString(), e.getValue());
        n.put("scores", scoreNbt);
        NbtList votes = new NbtList();
        for (UUID id : resetVotes) votes.add(NbtString.of(id.toString()));
        n.put("votes", votes);
        n.putInt("turn", currentTurn);
        n.putBoolean("started", started);
        n.putBoolean("over", gameOver);
        n.putBoolean("monochromeBalls", monochromeBalls);
        n.putBoolean("appearanceLocked", appearanceLocked);
        n.putString("status", status);
        if (winner != null) n.putUuid("winner", winner);
        return n;
    }

    public NbtCompound toClientNbt() {
        NbtCompound n = toNbt();
        NbtList active = new NbtList();
        for (UUID id : activePlayers) active.add(NbtString.of(id.toString()));
        n.put("active", active);
        return n;
    }

    public void fromNbt(NbtCompound n) {
        physicsSounds.clear();
        balls.clear();
        NbtList ballList = n.getList("balls", 10);
        for (int i = 0; i < ballList.size(); i++) {
            PoolBall b = new PoolBall(0,0,0);
            b.fromNbt(ballList.getCompound(i));
            balls.add(b);
        }
        repairLegacyBallIds();
        players.clear();
        NbtList playerList = n.getList("players", 8);
        for (int i = 0; i < playerList.size(); i++) players.add(UUID.fromString(playerList.getString(i)));
        activePlayers.clear();
        if (n.contains("active")) {
            NbtList activeList = n.getList("active", 8);
            for (int i = 0; i < activeList.size(); i++) activePlayers.add(UUID.fromString(activeList.getString(i)));
        }
        playerNames.clear();
        NbtCompound names = n.getCompound("names");
        for (String k : names.getKeys()) playerNames.put(UUID.fromString(k), names.getString(k));
        scores.clear();
        NbtCompound scoreNbt = n.getCompound("scores");
        for (String k : scoreNbt.getKeys()) scores.put(UUID.fromString(k), scoreNbt.getInt(k));
        resetVotes.clear();
        NbtList votes = n.getList("votes", 8);
        for (int i = 0; i < votes.size(); i++) resetVotes.add(UUID.fromString(votes.getString(i)));
        currentTurn = n.getInt("turn");
        started = n.getBoolean("started");
        gameOver = n.getBoolean("over");
        monochromeBalls = n.getBoolean("monochromeBalls");
        // Older saves cannot prove that no shot has been played. A new game
        // unlocks the appearance selector for those tables as well.
        appearanceLocked = n.contains("appearanceLocked") ? n.getBoolean("appearanceLocked") : started;
        status = n.getString("status");
        winner = n.containsUuid("winner") ? n.getUuid("winner") : null;
        if (balls.isEmpty()) rackBalls();
        normalizeTurn();
    }

    private void repairLegacyBallIds() {
        if (getBall(5) != null) return;
        int nines = 0;
        for (PoolBall ball : balls) if (ball.id == 9) nines++;
        if (nines == 2) {
            // Version 0.1.9 saved two nines and no five. The first nine was
            // the former eight ball, so restore its intended unique number.
            getBall(9).id = 5;
        }
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(balls.size());
        for (PoolBall b : balls) {
            buf.writeInt(b.id);
            buf.writeDouble(b.x);
            buf.writeDouble(b.y);
            buf.writeDouble(b.vx);
            buf.writeDouble(b.vy);
            buf.writeBoolean(b.pocketed);
        }
        buf.writeInt(players.size());
        for (UUID id : players) {
            buf.writeUuid(id);
            buf.writeString(playerNames.getOrDefault(id, "Игрок"));
            buf.writeInt(scores.getOrDefault(id, 0));
            buf.writeBoolean(resetVotes.contains(id));
        }
        buf.writeInt(activePlayers.size());
        for (UUID id : activePlayers) buf.writeUuid(id);
        buf.writeInt(currentTurn);
        buf.writeBoolean(started);
        buf.writeBoolean(gameOver);
        buf.writeString(status);
        buf.writeBoolean(winner != null);
        if (winner != null) buf.writeUuid(winner);
        buf.writeBoolean(monochromeBalls);
        buf.writeBoolean(appearanceLocked);
    }

    public static PoolGameState read(PacketByteBuf buf) {
        PoolGameState g = new PoolGameState();
        g.balls.clear();
        int bc = buf.readInt();
        for (int i = 0; i < bc; i++) {
            PoolBall b = new PoolBall(buf.readInt(), buf.readDouble(), buf.readDouble());
            b.vx = buf.readDouble();
            b.vy = buf.readDouble();
            b.pocketed = buf.readBoolean();
            g.balls.add(b);
        }
        g.players.clear();
        g.playerNames.clear();
        g.scores.clear();
        g.resetVotes.clear();
        int pc = buf.readInt();
        for (int i = 0; i < pc; i++) {
            UUID id = buf.readUuid();
            g.players.add(id);
            g.playerNames.put(id, buf.readString(32767));
            g.scores.put(id, buf.readInt());
            if (buf.readBoolean()) g.resetVotes.add(id);
        }
        g.activePlayers.clear();
        int ac = buf.readInt();
        for (int i = 0; i < ac; i++) g.activePlayers.add(buf.readUuid());
        g.currentTurn = buf.readInt();
        g.started = buf.readBoolean();
        g.gameOver = buf.readBoolean();
        g.status = buf.readString(32767);
        g.winner = buf.readBoolean() ? buf.readUuid() : null;
        g.monochromeBalls = buf.readableBytes() >= 2 && buf.readBoolean();
        g.appearanceLocked = buf.isReadable() ? buf.readBoolean() : g.started;
        g.normalizeTurn();
        return g;
    }
}
