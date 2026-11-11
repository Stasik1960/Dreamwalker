package dev.dreamwalker.gmwarps;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

final class WarpData extends PersistentState {
    private static final String SAVE_ID = "dw_gm_warps";
    private static final Pattern WARP_NAME = Pattern.compile("[\\p{L}\\p{N}_-]{1,64}");

    private record NamedWarp(String name, WarpLocation location) {}

    private final Map<String, NamedWarp> warps = new TreeMap<>();
    private final Map<UUID, WarpLocation> backPoints = new HashMap<>();

    static WarpData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(WarpData::readNbt, WarpData::new, SAVE_ID);
    }

    static boolean validName(String name) {
        return WARP_NAME.matcher(name).matches();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    List<String> warpNames() {
        return warps.values().stream().map(NamedWarp::name).toList();
    }

    WarpLocation findWarp(String name) {
        NamedWarp warp = warps.get(key(name));
        return warp == null ? null : warp.location();
    }

    boolean setWarp(String name, WarpLocation location) {
        NamedWarp previous = warps.put(key(name), new NamedWarp(name, location));
        markDirty();
        return previous != null;
    }

    boolean removeWarp(String name) {
        if (warps.remove(key(name)) == null) return false;
        markDirty();
        return true;
    }

    WarpLocation back(UUID player) {
        return backPoints.get(player);
    }

    void rememberBack(UUID player, WarpLocation location) {
        if (!location.equals(backPoints.put(player, location))) markDirty();
    }

    void clearBack(UUID player) {
        if (backPoints.remove(player) != null) markDirty();
    }

    private static WarpData readNbt(NbtCompound root) {
        WarpData data = new WarpData();
        NbtList warps = root.getList("Warps", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < warps.size(); i++) {
            NbtCompound entry = warps.getCompound(i);
            String name = entry.getString("Name");
            WarpLocation location = WarpLocation.readNbt(entry);
            if (validName(name) && location != null) {
                data.warps.put(key(name), new NamedWarp(name, location));
            }
        }
        NbtList points = root.getList("Back", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < points.size(); i++) {
            NbtCompound entry = points.getCompound(i);
            WarpLocation location = WarpLocation.readNbt(entry);
            if (entry.containsUuid("Player") && location != null) data.backPoints.put(entry.getUuid("Player"), location);
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound root) {
        NbtList names = new NbtList();
        for (NamedWarp warp : warps.values()) {
            NbtCompound entry = warp.location().writeNbt();
            entry.putString("Name", warp.name());
            names.add(entry);
        }
        NbtList points = new NbtList();
        backPoints.forEach((player, location) -> {
            NbtCompound entry = location.writeNbt();
            entry.putUuid("Player", player);
            points.add(entry);
        });
        root.put("Warps", names);
        root.put("Back", points);
        return root;
    }
}
