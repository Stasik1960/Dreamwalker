package dev.dreamwalker.bloodbornedw.compat.worldedit;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import java.util.Map;
import net.minecraft.util.math.BlockPos;

public final class WorldEditNativeWrites {
    public static final ThreadLocal<Map<BlockPos,BlockData>> EXPLICIT=new ThreadLocal<>();
    public static final String BRIDGE_VISUAL = "DreamwalkerNativeBridgeVisual";
    public static final String RETAINED_FRAME = "DreamwalkerRetainedFrame";
    private WorldEditNativeWrites() {}
}
