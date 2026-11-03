package dev.dreamwalker.bloodbornedw.editor.worldedit;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.LongTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import dev.dreamwalker.bloodbornedw.editor.EditorData;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class AppearanceTag {
    private static final String KEY = "DreamwalkerCityEditor";
    private AppearanceTag() {}

    public static BaseBlock capture(ServerWorld world, BlockPos pos, BaseBlock block) {
        if (world == null || !world.getServer().isOnThread() || !CityEditorServer.supported(world.getBlockState(pos))) return block;
        Map<String, Tag> values = new LinkedHashMap<>();
        if (block.getNbtData() != null) values.putAll(block.getNbtData().getValue());
        values.put(KEY, new CompoundTag(Map.of("Style", new IntTag(EditorData.style(world, pos)),
                "Applied", new IntTag(CityEditorServer.effectiveStyle(world, pos)), "Source", new LongTag(pos.asLong()), "Dimension", new StringTag(world.getRegistryKey().getValue().toString()))));
        return block.toBaseBlock(new CompoundTag(values));
    }

    /** Remove only our transport field on a copy at the native world boundary. */
    public static BaseBlock forNativeWrite(BaseBlock block) {
        CompoundTag nbt = block.getNbtData();
        if (nbt == null || !nbt.containsKey(KEY)) return block;
        Map<String, Tag> values = new LinkedHashMap<>(nbt.getValue());
        values.remove(KEY);
        return block.toBaseBlock(values.isEmpty() ? null : new CompoundTag(values));
    }

    public static boolean valid(BaseBlock block) {
        CompoundTag nbt = block.getNbtData();
        if (nbt == null || !nbt.containsKey(KEY)) return true;
        if (!(nbt.getValue().get(KEY) instanceof CompoundTag appearance)) return false;
        var tags = appearance.getValue();
        if (!(tags.get("Style") instanceof IntTag style) || !(tags.get("Applied") instanceof IntTag applied)
                || !(tags.get("Source") instanceof LongTag) || !(tags.get("Dimension") instanceof StringTag)) return false;
        return style.getValue() >= -1 && style.getValue() <= 4 && applied.getValue() >= 0 && applied.getValue() <= 4;
    }

    public static int style(BaseBlock block, ServerWorld world, BlockPos target) {
        CompoundTag nbt = block.getNbtData();
        if (nbt == null || !nbt.containsKey(KEY)) return 0;
        CompoundTag appearance = (CompoundTag) nbt.getValue().get(KEY);
        return appearance.getLong("Source") == target.asLong() && appearance.getString("Dimension").equals(world.getRegistryKey().getValue().toString()) ? appearance.getInt("Style") : appearance.getInt("Applied");
    }
}


