package dev.yharnamdice.game;

import dev.yharnamdice.DiceMod;
import dev.yharnamdice.Protocol;
import dev.yharnamdice.TableSeat;
import dev.yharnamdice.block.DiceTableEntity;
import dev.yharnamdice.item.DicePouchItem;
import dev.yharnamdice.item.RuleDiskItem;
import dev.yharnamdice.physics.*;
import dev.yharnamdice.rules.DiceRules;
import dev.yharnamdice.rules.RulesConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.state.property.Properties;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public final class TableSessions {
    private static final Map<ServerWorld, Map<BlockPos, Table>> WORLDS = new IdentityHashMap<>();
    private static final Map<UUID, Table> SEATED = new HashMap<>();
    private static final double CLOTH_Y = 14.13 / 16.0;
    private static final float DIE_SCALE = 0.68f / 3f * 0.70f;

    private TableSessions() {}

    public static boolean isPlaying(ServerPlayerEntity player) { return SEATED.containsKey(player.getUuid()); }

    public static void interact(ServerPlayerEntity user, DiceTableEntity entity) {
        if (!ServerPlayNetworking.canSend(user, Protocol.VIEW)) {
            tell(user, "client_required");
            return;
        }
        ServerWorld world = user.getServerWorld();
        Table table = WORLDS.computeIfAbsent(world, w -> new HashMap<>()).computeIfAbsent(
                entity.getPos().toImmutable(), pos -> new Table(world, entity, pos));
        Table current = SEATED.get(user.getUuid());
        if (current != null) {
            if (current == table) table.sync();
            else tell(user, "already");
            return;
        }
        ItemStack inHand = user.getMainHandStack();
        if (inHand.isOf(DiceMod.DISK) || user.isSneaking() && inHand.isEmpty() && !entity.disk().isEmpty()) {
            table.disk(user, inHand);
            return;
        }
        table.join(user);
    }

    public static void action(ServerPlayerEntity user, BlockPos pos, int action, int arg) {
        Table table = SEATED.get(user.getUuid());
        if (table != null && table.world == user.getServerWorld() && table.pos.equals(pos)) table.action(user, action, arg);
    }

    public static void tick(ServerWorld world) {
        Map<BlockPos, Table> tables = WORLDS.get(world);
        if (tables == null) return;
        Iterator<Table> it = tables.values().iterator();
        while (it.hasNext()) {
            Table table = it.next();
            table.tick();
            if (table.players.isEmpty()) it.remove();
        }
        if (tables.isEmpty()) WORLDS.remove(world);
    }

    public static void destroy(ServerWorld world, BlockPos pos) {
        Map<BlockPos, Table> tables = WORLDS.get(world);
        if (tables == null) return;
        Table table = tables.get(pos);
        // Leave removal to tick, so a world callback can safely close a table during iteration.
        if (table != null) table.close();
    }

    public static void disconnect(ServerPlayerEntity player) {
        Table table = SEATED.get(player.getUuid());
        if (table != null) table.leave(table.player(player.getUuid()), true);
    }

    public static void clear(MinecraftServer server) {
        WORLDS.clear();
        SEATED.clear();
    }

    private static void tell(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable("yharnam_dice.table." + key), true);
    }

    private static final class Player {
        final UUID id;
        final String name;
        final int side;
        final TableSeat seat;
        int bagSlot = -1;
        UUID bagId;
        int selected;
        int mark;
        int[] values = new int[0];
        int[] active = new int[0];
        final Map<Integer, DiceSnapshot> landed = new HashMap<>();
        int score;
        int pending;
        int rolls;
        boolean parked;
        boolean finished;
        boolean bust;
        String hand = "";

        Player(ServerPlayerEntity user, int side, TableSeat seat) {
            id = user.getUuid();
            name = user.getGameProfile().getName();
            this.side = side;
            this.seat = seat;
        }

        void reset() {
            mark = score = pending = rolls = 0;
            finished = bust = false;
            parked = false;
            values = active = new int[0];
            hand = "";
            landed.clear();
        }

        int[] checkedSkins(ServerPlayerEntity user) {
            int[] skins = new int[20];
            Arrays.fill(skins, -1);
            if (bagSlot < 0) return skins;
            for (int i = 0; i <= 40; i++) {
                ItemStack check = user.getInventory().getStack(i);
                if (check.isOf(DiceMod.POUCH) && bagId.equals(DicePouchItem.key(check))) {
                    bagSlot = i;
                    ItemStack[] dice = DicePouchItem.contents(check);
                    for (int j = 0; j < dice.length; j++) skins[j] = DiceMod.skin(dice[j]);
                    return skins;
                }
            }
            return skins;
        }
    }

    private static final class Table {
        final ServerWorld world;
        final DiceTableEntity entity;
        final BlockPos pos;
        final Direction facing;
        final int rotation;
        final Vec3d center;
        final List<Player> players = new ArrayList<>();
        final RulesConfig rules;
        final boolean custom;
        Player turn;
        Player throwingPlayer;
        DicePhysicsSession rolling;
        int[] thrown = new int[0];
        boolean started;
        boolean pokerReroll;
        boolean destroyed;
        String note = "";
        int tick;
        long lastSound;
        final Set<UUID> watchers = new HashSet<>();

        Table(ServerWorld world, DiceTableEntity entity, BlockPos pos) {
            this.world = world;
            this.entity = entity;
            this.pos = pos;
            facing = entity.getCachedState().get(Properties.HORIZONTAL_FACING);
            rotation = (facing.getHorizontal() + 1) % 4;
            center = Vec3d.ofCenter(pos).add(.5 * facing.getOffsetX(), CLOTH_Y - .5, .5 * facing.getOffsetZ());
            RulesConfig disk = RuleDiskItem.rules(entity.disk());
            custom = disk != null;
            rules = custom ? disk : new RulesConfig();
        }

        boolean free() { return rules.game == RulesConfig.Game.FREE; }

        boolean hasMatchActions(Player p) {
            if (!started || p != turn || p.finished || rolling != null) return false;
            return rules.game == RulesConfig.Game.FARKLE || rules.game == RulesConfig.Game.DICE_POKER && pokerReroll;
        }

        Vec3d worldPoint(double x, double y, double z) {
            return center.add(switch (rotation) {
                case 0 -> new Vec3d(x,y,z);
                case 1 -> new Vec3d(-z,y,x);
                case 2 -> new Vec3d(-x,y,-z);
                default -> new Vec3d(z,y,-x);
            });
        }

        Player player(UUID id) {
            return players.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null);
        }

        ServerPlayerEntity live(Player p) {
            return world.getServer().getPlayerManager().getPlayer(p.id);
        }

        void disk(ServerPlayerEntity user, ItemStack hand) {
            if (!players.isEmpty()) {
                tell(user, "disk_busy");
                return;
            }
            if (hand.isEmpty()) {
                ItemStack ejected = entity.disk().copy();
                entity.disk(ItemStack.EMPTY);
                user.getInventory().offerOrDrop(ejected);
                tell(user, "disk_removed");
            } else if (!entity.disk().isEmpty()) {
                tell(user, "disk_full");
            } else if (RuleDiskItem.rules(hand) == null) {
                tell(user, "disk_invalid");
            } else {
                entity.disk(hand);
                if (!user.getAbilities().creativeMode) hand.decrement(1);
                tell(user, "disk_loaded");
            }
            WORLDS.get(world).remove(pos);
        }

        void join(ServerPlayerEntity user) {
            if (destroyed || started || players.size() >= rules.maxPlayers || players.size() >= 4) {
                tell(user, started ? "in_progress" : "full");
                return;
            }
            if (user.hasVehicle() || user.isSleeping() || user.isSpectator()) {
                tell(user, "seat_blocked");
                return;
            }
            int side = -1;
            double closest = Double.POSITIVE_INFINITY;
            for (int i=0; i<4; i++) {
                final int place = i;
                if (players.stream().anyMatch(p -> p.side == place)) continue;
                Vec3d spot = seatPoint(i);
                net.minecraft.util.math.Box body = user.getDimensions(user.getPose()).getBoxAt(spot.x, spot.y, spot.z);
                if (!world.isSpaceEmpty(user,body)) continue;
                double distance = user.getPos().squaredDistanceTo(spot);
                if (distance < closest) { side = i; closest = distance; }
            }
            if (side < 0) {
                tell(user, "seat_blocked");
                return;
            }
            Vec3d spot = seatPoint(side);
            Vec3d delta = center.subtract(spot);
            float yaw = (float)(MathHelper.atan2(delta.z, delta.x) * 180 / Math.PI) - 90;
            Vec3d exit = worldPoint(side == 1 ? 1.78 : side == 3 ? -1.78 : 0,
                    -CLOTH_Y + .04, side == 0 ? -1.28 : side == 2 ? 1.28 : 0);
            TableSeat seat = DiceMod.SEAT.create(world);
            if (seat == null) return;
            seat.place(spot.x, spot.y, spot.z, yaw, exit.x, exit.z);
            if (!world.spawnEntity(seat)) return;
            user.setYaw(yaw);
            user.setPitch(42);
            user.setHeadYaw(yaw);
            if (!user.startRiding(seat, true)) {
                seat.discard();
                return;
            }
            Player p = new Player(user, side, seat);
            players.add(p);
            SEATED.put(p.id, this);
            note = "choose";
            world.playSound(null, spot.x, spot.y, spot.z, DiceMod.SIT, SoundCategory.BLOCKS, .44f, .9f);
            sync();
            broadcast();
        }

        Vec3d seatPoint(int side) {
            return worldPoint(side == 1 ? 1.34 : side == 3 ? -1.34 : 0,
                    -CLOTH_Y + .36, side == 0 ? -.77 : side == 2 ? .77 : 0);
        }

        void action(ServerPlayerEntity user, int action, int arg) {
            Player p = player(user.getUuid());
            if (p == null || destroyed || user.getVehicle() != p.seat) return;
            switch (action) {
                case Protocol.LEAVE -> { leave(p, false); return; }
                case Protocol.BAG -> {
                    if (started || rolling != null || arg < 0 || arg > 40) return;
                    ItemStack bag = user.getInventory().getStack(arg);
                    if (!bag.isOf(DiceMod.POUCH)) return;
                    p.bagId = DicePouchItem.identify(bag);
                    p.bagSlot = arg;
                    p.selected = 0;
                    p.reset();
                    user.getInventory().markDirty();
                    note = "pick";
                }
                case Protocol.SELECT -> {
                    if (started || rolling != null || arg < 0 || arg >= 20) return;
                    int[] skins = p.checkedSkins(user);
                    if (skins[arg] < 0) return;
                    int bit = 1 << arg;
                    if ((p.selected & bit) == 0 && Integer.bitCount(p.selected) >= rules.dicePerPlayer) {
                        note = "limit";
                        break;
                    }
                    if ((p.selected & bit) == 0 && !rules.allowDuplicateDice) {
                        for (int i=0; i<20; i++) if ((p.selected & (1 << i)) != 0 && skins[i] == skins[arg]) {
                            note = "duplicates";
                            sync();
                            return;
                        }
                    }
                    p.selected ^= bit;
                    p.reset();
                    note = isReady(p) ? "ready" : "pick";
                }
                case Protocol.START -> {
                    if (free() || started || rolling != null || p != players.get(0)) return;
                    if (players.size() < rules.minPlayers || players.stream().anyMatch(other -> !isReady(other))) {
                        note = "not_ready";
                        break;
                    }
                    for (Player other : players) {
                        other.reset();
                        other.active = selectedIndices(other);
                        other.values = new int[20];
                    }
                    started = true;
                    pokerReroll = false;
                    turn = players.get(0);
                    note = "turn";
                }
                case Protocol.MARK -> {
                    if (rolling != null || p != turn || !started || arg < 0 || arg >= 20
                            || p.finished || p.values[arg] == 0 || !contains(p.active,arg)) return;
                    if (rules.game != RulesConfig.Game.DICE_POKER && rules.game != RulesConfig.Game.FARKLE) return;
                    if (rules.game == RulesConfig.Game.DICE_POKER && !pokerReroll) return;
                    p.mark ^= 1 << arg;
                }
                case Protocol.THROW -> {
                    if (!canRoll(p)) return;
                    if (free()) prepareFree(p);
                    if (rules.game == RulesConfig.Game.FARKLE && p.rolls > 0 && hasValues(p, p.active) && !keep(p, false)) {
                        sync();
                        return;
                    }
                    int[] targets = rules.game == RulesConfig.Game.DICE_POKER && p.rolls > 0
                            ? Arrays.stream(p.active).filter(i -> (p.mark & (1 << i)) != 0).toArray() : p.active.clone();
                    launch(p, targets);
                }
                case Protocol.KEEP -> {
                    if (!canKeep(p)) return;
                    if (rules.game == RulesConfig.Game.DICE_POKER) finishPoker(p);
                    else keep(p, true);
                }
                case Protocol.BANK -> {
                    if (!canBank(p)) return;
                    if (p.mark != 0 && !keep(p,false)) {
                        sync();
                        return;
                    }
                    if (p.pending > 0) {
                        p.score += p.pending;
                        p.pending = 0;
                        if (p.score >= rules.target) {
                            finish(List.of(p));
                        } else {
                            nextTurn(p, "turn");
                        }
                    }
                }
                case Protocol.STAND -> {
                    if (!canStand(p)) return;
                    p.finished = true;
                    advanceFinished(p);
                }
                default -> { return; }
            }
            sync();
            broadcast();
        }

        private static boolean contains(int[] list, int n) { return Arrays.stream(list).anyMatch(i -> i == n); }
        private static boolean hasValues(Player p, int[] list) { return Arrays.stream(list).anyMatch(i -> p.values[i] != 0); }
        private static int[] selectedIndices(Player p) { return IntStream.range(0,20).filter(i -> (p.selected & (1 << i)) != 0).toArray(); }
        private boolean isReady(Player p) {
            ServerPlayerEntity user = live(p);
            if (user == null) return false;
            int count = Integer.bitCount(p.selected);
            int[] skins = p.checkedSkins(user);
            return (free() ? count > 0 && count <= rules.dicePerPlayer : count == rules.dicePerPlayer)
                    && Arrays.stream(selectedIndices(p)).allMatch(i -> skins[i] >= 0);
        }

        private boolean canRoll(Player p) {
            if (rolling != null) return false;
            if (free()) return players.size() >= rules.minPlayers && isReady(p);
            if (p != turn || p.finished || !started || !isReady(p)) return false;
            return switch (rules.game) {
                case DICE_POKER -> pokerReroll ? p.rolls <= rules.maxRerolls && p.mark != 0 : p.rolls == 0;
                case FARKLE -> p.active.length > 0 && (!hasValues(p,p.active) || p.mark != 0);
                default -> true;
            };
        }

        private boolean canKeep(Player p) {
            if (rolling != null || p != turn || !started) return false;
            return switch (rules.game) {
                case DICE_POKER -> pokerReroll && !p.finished && p.rolls > 0;
                case FARKLE -> hasValues(p,p.active) && p.mark != 0;
                default -> false;
            };
        }

        private boolean canBank(Player p) {
            return rolling == null && p == turn && started && rules.game == RulesConfig.Game.FARKLE
                    && (p.mark != 0 || p.pending > 0 && !hasValues(p,p.active));
        }

        private boolean canStand(Player p) {
            return rolling == null && started && p == turn && rules.game == RulesConfig.Game.TWENTY_ONE && p.rolls > 0;
        }

        private void prepareFree(Player p) {
            p.active = selectedIndices(p);
            p.values = new int[20];
            p.rolls=0;
            p.landed.clear();
        }

        private void launch(Player p, int[] list) {
            if (list.length == 0) return;
            Vec3 direction = switch (p.side) {
                case 0 -> new Vec3(.42,0,1);
                case 1 -> new Vec3(-1,0,.13);
                case 2 -> new Vec3(-.42,0,-1);
                default -> new Vec3(1,0,-.13);
            };
            int[] own = selectedIndices(p);
            List<Vec3> starts = Arrays.stream(list).mapToObj(i -> parked(p.side,Arrays.binarySearch(own,i),own.length)).toList();
            rolling = DicePhysicsSession.throwDice(list.length, ThreadLocalRandom.current().nextLong(), direction, starts,
                    DIE_SCALE);
            for (Player seated : players) if (seated != p) seated.parked = true;
            p.parked = false;
            thrown = list;
            throwingPlayer = p;
            p.mark = 0;
            p.rolls++;
            note = "rolling";
            sound(DiceMod.ROLL, .66f, .87f + world.random.nextFloat()*.24f);
        }

        private boolean keep(Player p, boolean standIfNoHotDice) {
            List<Integer> selected = Arrays.stream(p.active).filter(i -> (p.mark & (1 << i)) != 0)
                    .map(i -> p.values[i]).boxed().toList();
            if (selected.stream().anyMatch(value -> value == 0)) return false;
            int score = DiceRules.farklePoints(selected);
            if (score <= 0) { note = "bad_score"; return false; }
            p.pending += score;
            p.active = Arrays.stream(p.active).filter(i -> (p.mark & (1 << i)) == 0).toArray();
            p.mark = 0;
            if (p.active.length == 0) {
                if (rules.hotDice) {
                    p.active = selectedIndices(p);
                    Arrays.fill(p.values,0);
                    p.landed.clear();
                    note = "hot";
                } else {
                    if (!standIfNoHotDice) note = "bank_only";
                    return true;
                }
            } else {
                for (int i : p.active) p.values[i]=0;
                note = "kept";
            }
            return true;
        }

        private void rolled() {
            Player p = throwingPlayer;
            for (DiceSnapshot snapshot : rolling.snapshots()) {
                int index = thrown[snapshot.id()];
                p.values[index] = snapshot.settledFace();
                p.landed.put(index, snapshot);
            }
            rolling = null;
            throwingPlayer = null;
            p.mark = 0;
            switch (rules.game) {
                case FREE -> note = "rolled";
                case DICE_POKER -> {
                    p.hand = DiceRules.pokerHand(current(p,p.active)).label();
                    if (!pokerReroll) nextPokerOpening();
                    else if (p.rolls > rules.maxRerolls) finishPoker(p);
                    else note = "poker_choose";
                }
                case FARKLE -> {
                    if (!DiceRules.canScore(current(p,p.active))) {
                        p.pending=0;
                        nextTurn(p,"farkle");
                    } else note = "farkle_choose";
                }
                case TWENTY_ONE -> {
                    p.score += Arrays.stream(p.active).map(i -> p.values[i]).sum();
                    if (p.score > rules.target) {
                        p.finished = p.bust = true;
                        advanceFinished(p);
                    } else if (p.score == rules.target) {
                        p.finished = true;
                        advanceFinished(p);
                    } else note = "twenty_one_choose";
                }
            }
            sync();
        }

        private static List<Integer> current(Player p, int[] list) {
            return Arrays.stream(list).map(i -> p.values[i]).boxed().toList();
        }

        private void finishPoker(Player p) {
            p.score = DiceRules.pokerRank(current(p,p.active));
            p.finished = true;
            advanceFinished(p);
        }

        private void nextPokerOpening() {
            for (Player p : players) {
                if (p.rolls == 0) {
                    turn = p;
                    note = "turn";
                    return;
                }
            }
            if (rules.maxRerolls == 0) {
                int best=players.stream().mapToInt(p -> DiceRules.pokerRank(current(p,p.active))).max().orElseThrow();
                finish(players.stream().filter(p -> DiceRules.pokerRank(current(p,p.active))==best).toList());
            } else {
                turn = players.get(0);
                pokerReroll = true;
                note = "poker_choose";
            }
        }

        private void advanceFinished(Player p) {
            List<Player> remaining = players.stream().filter(x -> !x.finished).toList();
            if (remaining.isEmpty()) {
                int max = players.stream().filter(x -> !x.bust).mapToInt(x -> x.score).max().orElse(-1);
                finish(players.stream().filter(x -> !x.bust && x.score == max).toList());
            } else {
                for (int i=1; i<=players.size(); i++) {
                    Player candidate = players.get((players.indexOf(p)+i) % players.size());
                    if (!candidate.finished) {
                        turn = candidate;
                        note = p.bust ? "bust" : "turn";
                        break;
                    }
                }
            }
        }

        private void nextTurn(Player old, String status) {
            turn = players.get((players.indexOf(old)+1)%players.size());
            turn.active = selectedIndices(turn);
            turn.values = new int[20];
            turn.pending = turn.rolls = turn.mark = 0;
            turn.landed.clear();
            note = status;
        }

        void finish(List<Player> winners) {
            started = false;
            turn = null;
            note = winners.isEmpty() ? "no_winner" : (winners.size() == 1 ? "winner:" : "tie:")
                    + String.join(", ", winners.stream().map(p -> p.name).toList());
            sound(DiceMod.SIT, .45f, 1.27f);
        }

        void leave(Player p, boolean forced) {
            if (p == null) return;
            boolean host = p == players.get(0);
            if (host && started && rules.hostExit == RulesConfig.HostExit.BETWEEN_GAMES && !forced) {
                note = "host_locked";
                sync();
                return;
            }
            int index = players.indexOf(p);
            players.remove(p);
            SEATED.remove(p.id);
            ServerPlayerEntity user = live(p);
            if (user != null) {
                if (user.getVehicle() == p.seat) user.stopRiding();
                Protocol.view(user,null);
            }
            p.seat.discard();
            if (throwingPlayer == p) {
                rolling = null;
                throwingPlayer = null;
            }
            if (started) {
                if (players.size() < rules.minPlayers || host && rules.hostExit != RulesConfig.HostExit.TRANSFER) {
                    started = false;
                    turn = null;
                    rolling = null;
                    throwingPlayer = null;
                    players.forEach(Player::reset);
                    note = "cancelled";
                } else if (p == turn) {
                    turn = players.get(index % players.size());
                    if (rules.game == RulesConfig.Game.DICE_POKER && !pokerReroll) {
                        nextPokerOpening();
                    } else if (rules.game == RulesConfig.Game.FARKLE) {
                        turn.active = selectedIndices(turn);
                        turn.values = new int[20];
                        turn.pending = turn.rolls = turn.mark = 0;
                        turn.landed.clear();
                    } else if (turn.finished) {
                        advanceFinished(turn);
                    }
                    if (started && !(rules.game == RulesConfig.Game.DICE_POKER && pokerReroll)) note = "turn";
                }
            }
            sync();
            broadcast();
        }

        void close() {
            destroyed = true;
            for (Player p : List.copyOf(players)) leave(p, true);
            broadcast();
        }

        void tick() {
            if (players.isEmpty()) return;
            if (destroyed || world.getBlockEntity(pos) != entity) { close(); return; }
            for (Player p : List.copyOf(players)) {
                ServerPlayerEntity user = live(p);
                if (user == null || user.getWorld() != world || !user.isAlive() || user.getVehicle() != p.seat) {
                    leave(p,true);
                } else if (p.bagSlot >= 0) {
                    int[] skins = p.checkedSkins(user);
                    if (Arrays.stream(selectedIndices(p)).anyMatch(i -> skins[i] < 0)) {
                        p.bagSlot = -1;
                        p.selected = 0;
                        if (started) leave(p,true);
                        else {
                            p.reset();
                            sync();
                        }
                    }
                }
            }
            if (players.isEmpty()) return;
            tick++;
            if (rolling != null) {
                rolling.tick(.05);
                ImpactEvent collision = rolling.drainImpacts().stream().max(Comparator.comparingDouble(ImpactEvent::intensity)).orElse(null);
                if (collision != null && tick-lastSound >= 2 && collision.intensity() > .11) {
                    float impact = Math.min(1, (float) collision.intensity());
                    sound(switch (collision.type()) {
                        case TABLE -> DiceMod.FELT;
                        case RIM -> DiceMod.RIM;
                        case DICE -> DiceMod.CLACK;
                    }, .24f + .45f * impact, .82f + world.random.nextFloat()*.4f);
                    lastSound = tick;
                }
                if (rolling.isComplete()) rolled();
                broadcast();
            } else if (tick % 10 == 0) {
                broadcast();
            }
        }

        private void sound(net.minecraft.sound.SoundEvent event, float volume, float pitch) {
             world.playSound(null, center.x, center.y, center.z, event, SoundCategory.BLOCKS, volume, pitch);
        }

        void sync() {
            for (Player p : players) {
                ServerPlayerEntity user = live(p);
                if (user == null) continue;
                List<Protocol.Bag> bags = new ArrayList<>();
                for (int i=0; i<=40; i++) {
                    ItemStack bag = user.getInventory().getStack(i);
                    if (bag.isOf(DiceMod.POUCH)) {
                        int count = (int)Arrays.stream(DicePouchItem.contents(bag)).filter(die -> !die.isEmpty()).count();
                        String name = bag.getName().getString();
                        name = name.substring(0,Math.min(name.length(),48));
                        bags.add(new Protocol.Bag(i, name + "  " + count + "/20"));
                    }
                }
                List<String> board = new ArrayList<>();
                for (Player seat : players) {
                    String label = seat.name;
                    if (seat == turn) label = "› " + label;
                    if (rules.game == RulesConfig.Game.DICE_POKER && !seat.hand.isEmpty()) label += " · " + seat.hand;
                    if (rules.game == RulesConfig.Game.TWENTY_ONE) label += " · " + seat.score + (seat.bust ? " ✕" : "");
                    if (rules.game == RulesConfig.Game.FARKLE) label += " · " + seat.score + (seat.pending > 0 ? " +" + seat.pending : "");
                    if (free() && seat == p && seat.values.length == 20 && seat.landed.size()>0)
                        label += " · " + String.join(" ", Arrays.stream(seat.values).filter(i -> i > 0).mapToObj(Integer::toString).toList());
                    board.add(label);
                }
                int required = rules.dicePerPlayer;
                String title = custom ? rules.name : "";
                Protocol.view(user,new Protocol.View(pos.asLong(),title,note,turn == null ? "" : turn.name,
                        bags,p.bagSlot,p.checkedSkins(user),p.selected,required,players.size(),rules.minPlayers,
                        p == players.get(0) && !free(),started,rolling != null,canRoll(p),canKeep(p),canBank(p),
                        canStand(p),hasMatchActions(p),rules.game.name(),
                        !(p == players.get(0) && started && rules.hostExit == RulesConfig.HostExit.BETWEEN_GAMES),
                        p.values, p.active, p.mark, board));
            }
        }

        private static Vec3 parked(int side, int nth, int count) {
            int columns = side % 2 == 0 ? 10 : 6;
            int row = nth / columns, column = nth % columns;
            int items = Math.min(columns,count-row*columns);
            double gap = count<=6 ? .074 : .060;
            double space = (column-(items-1)*.5)*gap;
            if (side%2==0) return new Vec3(space,0,(side==0?-1:1)*(.34-row*.068));
            return new Vec3((side==1?1:-1)*(.83-row*.068),0,space);
        }

        void broadcast() {
            List<Protocol.Visual> shown = new ArrayList<>();
            List<DiceSnapshot> inMotion = rolling == null ? List.of() : rolling.snapshots();
            for (Player p : players) {
                ServerPlayerEntity user = live(p);
                if (user == null) continue;
                int[] skins = p.checkedSkins(user);
                int[] selected = selectedIndices(p);
                int k = 0;
                for (int index : selected) {
                    int skin = skins[index];
                    if (skin < 0) continue;
                    DiceSnapshot pose = p.landed.get(index);
                    if (throwingPlayer == p) {
                        for (int j=0; j<thrown.length; j++) if (thrown[j]==index) {
                            pose = inMotion.get(j);
                            break;
                        }
                    }
                    float scale = DIE_SCALE;
                    if (pose == null || throwingPlayer != p && (p.parked || !contains(p.active,index) || turn != null && turn != p)) {
                        Vec3 slot = parked(p.side, k, selected.length);
                        Quaternion q = Quaternion.IDENTITY;
                        if (pose != null) q = pose.rotation();
                        shown.add(visual(p,index,skin,slot.x(),scale*.10,slot.z(),q,scale));
                    } else {
                        // Preserve spin and center of mass; make resting dice touch the fabric at the render scale.
                        double y = Math.max(scale*.1, pose.position().y());
                        shown.add(visual(p,index,skin,pose.position().x(),y,pose.position().z(),pose.rotation(),scale));
                    }
                    k++;
                }
            }
            Set<UUID> nearby = new HashSet<>();
            for (ServerPlayerEntity user : world.getPlayers()) {
                if (user.getPos().squaredDistanceTo(center) <= 32*32) {
                    nearby.add(user.getUuid());
                    if (!shown.isEmpty() || watchers.contains(user.getUuid())) Protocol.visual(user,pos,rotation,shown);
                }
            }
            for (UUID old : watchers) {
                if (!nearby.contains(old)) {
                    ServerPlayerEntity user = world.getServer().getPlayerManager().getPlayer(old);
                    if (user != null && user.getWorld() == world) Protocol.visual(user,pos,rotation,List.of());
                }
            }
            watchers.clear();
            if (!shown.isEmpty()) watchers.addAll(nearby);
        }

        private static Protocol.Visual visual(Player p, int index, int skin, double x, double y, double z, Quaternion q, float scale) {
            return new Protocol.Visual(p.side,index,skin,(float)x,(float)y,(float)z,
                    (float)q.x(),(float)q.y(),(float)q.z(),(float)q.w(),scale);
        }
    }
}
