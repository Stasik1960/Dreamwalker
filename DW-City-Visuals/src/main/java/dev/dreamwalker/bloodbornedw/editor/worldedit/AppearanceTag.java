package dev.dreamwalker.bloodbornedw.editor.worldedit;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.LongTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import dev.dreamwalker.bloodbornedw.editor.EditorCells;
import dev.dreamwalker.bloodbornedw.editor.EditorData;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class AppearanceTag {
    private static final String KEY = "DreamwalkerCityEditorRGB";
    private static final String LEGACY_KEY = "DreamwalkerCityEditor";
    private static final int FORMAT = 1;
    private AppearanceTag() {}

    public static BaseBlock capture(ServerWorld world, BlockPos pos, BaseBlock block) {
        if (world == null || !world.getServer().isOnThread() || !CityEditorServer.supported(world.getBlockState(pos))) return block;
        Map<String, Tag> values = new LinkedHashMap<>();
        if (block.getNbtData() != null) values.putAll(block.getNbtData().getValue());
        values.remove(LEGACY_KEY);
        int stored = EditorData.style(world,pos);
        int applied = EditorCells.isRgb(stored) ? stored : CityEditorServer.effectiveStyle(world,pos);
        values.put(KEY, new CompoundTag(Map.of("Format", new IntTag(FORMAT), "Value", new IntTag(stored),
                "Applied", new IntTag(applied), "Source", new LongTag(pos.asLong()),
                "Dimension", new StringTag(world.getRegistryKey().getValue().toString()))));
        return block.toBaseBlock(new CompoundTag(values));
    }

    /** Remove only our transport field on a copy at the native world boundary. */
    public static BaseBlock forNativeWrite(BaseBlock block) {
        CompoundTag nbt = block.getNbtData();
        if (nbt == null || (!nbt.containsKey(KEY) && !nbt.containsKey(LEGACY_KEY))) return block;
        Map<String, Tag> values = new LinkedHashMap<>(nbt.getValue());
        values.remove(KEY);
        values.remove(LEGACY_KEY);
        return block.toBaseBlock(values.isEmpty() ? null : new CompoundTag(values));
    }

    public static boolean valid(BaseBlock block) {
        CompoundTag nbt = block.getNbtData();
        if (nbt == null || (!nbt.containsKey(KEY) && !nbt.containsKey(LEGACY_KEY))) return true;
        if (nbt.containsKey(KEY) && nbt.containsKey(LEGACY_KEY)) return false;
        boolean current = nbt.containsKey(KEY);
        String key = current ? KEY : LEGACY_KEY;
        if (!(nbt.getValue().get(key) instanceof CompoundTag appearance)) return false;
        var tags = appearance.getValue();
        String valueKey = current ? "Value" : "Style";
        if (current && (!(tags.get("Format") instanceof IntTag format) || format.getValue() != FORMAT)) return false;
        if (!(tags.get(valueKey) instanceof IntTag style) || !(tags.get("Applied") instanceof IntTag applied)
                || !(tags.get("Source") instanceof LongTag) || !(tags.get("Dimension") instanceof StringTag)) return false;
        if (current) {
            return (style.getValue() == -1 || EditorCells.isStored(style.getValue()))
                    && EditorCells.isStored(applied.getValue());
        }
        return style.getValue() >= -1 && style.getValue() <= 4
                && applied.getValue() >= 0 && applied.getValue() <= 4;
    }

    public static int style(BaseBlock block, ServerWorld world, BlockPos target) {
        CompoundTag nbt = block.getNbtData();
        if (nbt == null || (!nbt.containsKey(KEY) && !nbt.containsKey(LEGACY_KEY))) return 0;
        boolean current = nbt.containsKey(KEY);
        CompoundTag appearance = (CompoundTag) nbt.getValue().get(current ? KEY : LEGACY_KEY);
        String valueKey = current ? "Value" : "Style";
        int stored = appearance.getInt(valueKey);
        if (current && EditorCells.isRgb(stored)) return stored;
        return appearance.getLong("Source") == target.asLong() && appearance.getString("Dimension").equals(world.getRegistryKey().getValue().toString()) ? appearance.getInt(valueKey) : appearance.getInt("Applied");
    }
}


