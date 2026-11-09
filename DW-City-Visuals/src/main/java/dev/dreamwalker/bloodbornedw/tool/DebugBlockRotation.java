package dev.dreamwalker.bloodbornedw.tool;

import net.minecraft.server.network.ServerPlayerEntity;

/** Direction of the current synchronous BB rotation transaction. */
public final class DebugBlockRotation {
    private static final ThreadLocal<Integer> STEP = new ThreadLocal<>();

    private DebugBlockRotation() {}

    public static boolean apply(ServerPlayerEntity player, ToolTarget target, boolean clockwise) {
        Integer previous = STEP.get();
        STEP.set(clockwise ? 1 : -1);
        try {
            return BbToolRotations.rotate(player, target);
        } finally {
            if (previous == null) STEP.remove();
            else STEP.set(previous);
        }
    }

    public static int direction() {
        Integer step = STEP.get();
        return step == null ? 1 : step;
    }
}
