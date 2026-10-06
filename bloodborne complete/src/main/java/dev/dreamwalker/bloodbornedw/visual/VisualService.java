package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import dev.dreamwalker.bloodbornedw.block.Visual;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

/** World-local, ordered cosmetic overrides.  Rules intentionally never mutate blocks. */
public final class VisualService {
    private static final String STATE_ID = "bloodborne_dw_visual_rules";
    private static final int MAX_RULES = 4096;
    public static final Identifier SNAPSHOT_PACKET = new Identifier("bloodborne_dw", "visual_rules");
    private static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;
    private static boolean initialized;

    private VisualService() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSnapshot(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sendSnapshot(player));
    }
    public static boolean initialized() { return initialized; }

    public static Visual effective(ServerWorld world, BlockPos pos, BlockState state) {
        Visual result = state.contains(DwBlocks.VISUAL) ? state.get(DwBlocks.VISUAL) : Visual.BASE;
        String id = id(state);
        if (id == null) return result;
        return state(world).compiled.effective(pos, id, result);
    }

    /** Safe, bounded copy suitable for a client packet or test persistence round-trip. */
    public static NbtCompound snapshot(ServerWorld world) { return writeRules(state(world).rules); }

    public static Visual effective(NbtCompound snapshot, BlockPos pos, String id, Visual stored) {
        return compile(snapshot).effective(pos, id, stored);
    }

    /** Decode untrusted snapshot data once, before it reaches the block-render hot path. */
    public static RuleSet compile(NbtCompound snapshot) {
        if (snapshot == null) return RuleSet.EMPTY;
        ArrayList<Rule> parsed = new ArrayList<>(); NbtList list = snapshot.getList("rules", 10);
        for (int i = 0; i < list.size() && parsed.size() < MAX_RULES; i++) Rule.read(list.getCompound(i)).ifPresent(parsed::add);
        return new RuleSet(parsed);
    }

    public static void setPoint(ServerWorld world, BlockPos pos, String id, Visual visual) {
        add(world, Rule.point(pos, id, visual));
    }

    public static void addAreaRule(ServerWorld world, BlockPos first, BlockPos second, Collection<String> ids, Visual visual) {
        add(world, Rule.area(first, second, ids, visual));
    }

    public static void addAllRule(ServerWorld world, Collection<String> ids, Visual visual) {
        add(world, Rule.all(ids, visual));
    }

    /** Replaces every prior rule; existing native state remains intact. */
    public static void reset(ServerWorld world, Collection<String> ids, Visual visual) {
        RuleState state = state(world);
        List<Rule> proposed = List.of(Rule.all(ids, visual));
        validate(proposed);
        state.rules.clear();
        state.rules.addAll(proposed); state.rebuild();
        state.markDirty();
        broadcast(world);
    }

    public static List<String> describe(ServerWorld world, BlockPos pos, BlockState blockState) {
        ArrayList<String> result = new ArrayList<>();
        result.add("stored=" + (blockState.contains(DwBlocks.VISUAL) ? blockState.get(DwBlocks.VISUAL).asString() : "base"));
        result.add("effective=" + effective(world, pos, blockState).asString());
        result.add("rules=" + state(world).rules.size());
        return result;
    }

    public static String id(BlockState state) {
        DwBlocks.Entry entry = DwBlocks.byBlock(state.getBlock()); return entry == null ? null : entry.id();
    }

    private static void add(ServerWorld world, Rule rule) {
        RuleState state = state(world);
        if (state.rules.size() >= MAX_RULES) throw new IllegalStateException("Too many visual rules in this world");
        ArrayList<Rule> proposed = new ArrayList<>(state.rules); proposed.add(rule); validate(proposed);
        state.rules.add(rule); state.rebuild();
        state.markDirty();
        broadcast(world);
    }

    private static RuleState state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(RuleState::fromNbt, RuleState::new, STATE_ID);
    }

    private static NbtCompound writeRules(List<Rule> rules) {
        NbtCompound nbt = new NbtCompound(); NbtList list = new NbtList();
        for (Rule rule : rules) list.add(rule.write());
        nbt.put("rules", list); return nbt;
    }
    private static void validate(List<Rule> rules) {
        if (rules.size() > MAX_RULES) throw new IllegalArgumentException("Too many visual rules");
        for (Rule rule : rules) for (String id : rule.ids) if (!id.matches("\\d{5}") || DwBlocks.byId(id) == null) throw new IllegalArgumentException("Unknown DW id");
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer()); buffer.writeNbt(writeRules(rules));
        if (buffer.writerIndex() > MAX_SNAPSHOT_BYTES - 256) throw new IllegalArgumentException("Visual rule snapshot exceeds 1 MiB");
    }
    private static void sendSnapshot(net.minecraft.server.network.ServerPlayerEntity player) {
        NbtCompound nbt = snapshot(player.getServerWorld()); PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        buffer.writeString(player.getServerWorld().getRegistryKey().getValue().toString(), 128); buffer.writeNbt(nbt);
        if (buffer.writerIndex() <= MAX_SNAPSHOT_BYTES) ServerPlayNetworking.send(player, SNAPSHOT_PACKET, buffer);
    }
    private static void broadcast(ServerWorld world) { for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) sendSnapshot(player); }

    private static final class RuleState extends PersistentState {
        final List<Rule> rules = new ArrayList<>();
        volatile RuleSet compiled = RuleSet.EMPTY;
        static RuleState fromNbt(NbtCompound nbt) {
            RuleState value = new RuleState();
            NbtList list = nbt.getList("rules", 10);
            for (int i = 0; i < list.size() && value.rules.size() < MAX_RULES; i++) Rule.read(list.getCompound(i)).filter(rule -> rule.valid()).ifPresent(value.rules::add);
            value.rebuild(); return value;
        }
        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            return writeRules(rules);
        }
        void rebuild() { compiled = new RuleSet(rules); }
    }

    private enum Kind { ALL, POINT, AREA }
    public static final class RuleSet {
        static final RuleSet EMPTY = new RuleSet(List.of());
        private final Rule[] newestFirst;
        private RuleSet(Collection<Rule> rules) { Rule[] ordered=rules.toArray(Rule[]::new); newestFirst=new Rule[ordered.length]; for(int i=0;i<ordered.length;i++) newestFirst[i]=ordered[ordered.length-1-i]; }
        public Visual effective(BlockPos pos, String id, Visual stored) { for(Rule rule:newestFirst) if(rule.matches(pos,id)) return rule.visual; return stored; }
        public int size() { return newestFirst.length; }
    }
    private static final class Rule {
        final Kind kind; final int x1, y1, z1, x2, y2, z2; final Set<String> ids; final Visual visual;
        private Rule(Kind kind, BlockPos first, BlockPos second, Collection<String> ids, Visual visual) {
            this.kind = kind; x1 = Math.min(first.getX(), second.getX()); y1 = Math.min(first.getY(), second.getY()); z1 = Math.min(first.getZ(), second.getZ());
            x2 = Math.max(first.getX(), second.getX()); y2 = Math.max(first.getY(), second.getY()); z2 = Math.max(first.getZ(), second.getZ());
            this.ids = Set.copyOf(new LinkedHashSet<>(ids)); this.visual = visual;
        }
        static Rule all(Collection<String> ids, Visual visual) { return new Rule(Kind.ALL, BlockPos.ORIGIN, BlockPos.ORIGIN, ids, visual); }
        static Rule point(BlockPos pos, String id, Visual visual) { return new Rule(Kind.POINT, pos, pos, List.of(id), visual); }
        static Rule area(BlockPos a, BlockPos b, Collection<String> ids, Visual visual) { return new Rule(Kind.AREA, a, b, ids, visual); }
        boolean matches(BlockPos pos, String id) {
            if (!ids.isEmpty() && !ids.contains(id)) return false;
            return kind == Kind.ALL || (pos.getX() >= x1 && pos.getX() <= x2 && pos.getY() >= y1 && pos.getY() <= y2 && pos.getZ() >= z1 && pos.getZ() <= z2);
        }
        boolean valid() { return ids.stream().allMatch(id -> id.matches("\\d{5}") && DwBlocks.byId(id) != null); }
        NbtCompound write() { NbtCompound n = new NbtCompound(); n.putString("kind", kind.name()); n.putInt("x1",x1); n.putInt("y1",y1); n.putInt("z1",z1); n.putInt("x2",x2); n.putInt("y2",y2); n.putInt("z2",z2); n.putString("visual",visual.asString()); n.putString("ids",String.join(",",ids)); return n; }
        static java.util.Optional<Rule> read(NbtCompound n) { try { Kind k=Kind.valueOf(n.getString("kind")); Visual v="alt".equals(n.getString("visual"))?Visual.ALT:Visual.BASE; List<String> ids=n.getString("ids").isEmpty()?List.of():List.of(n.getString("ids").split(",")); return java.util.Optional.of(new Rule(k,new BlockPos(n.getInt("x1"),n.getInt("y1"),n.getInt("z1")),new BlockPos(n.getInt("x2"),n.getInt("y2"),n.getInt("z2")),ids,v)); } catch (RuntimeException ignored) { return java.util.Optional.empty(); } }
    }
}
