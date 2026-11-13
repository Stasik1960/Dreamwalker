package dev.dreamwalker.bloodbornedw.building;

import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSourceShift;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class DoorSounds {
    private static final Set<String> ARCHITECTURE_DOORS = Set.of(
            "prototype_double_door", "source_acacia_pressure_plate", "source_allium", "source_birch_grille_door",
            "source_crimson_top", "source_dark_arch_door", "source_oak_prison_door", "source_smooth_quartz_top");

    private DoorSounds() {}

    public static void architecture(ServerWorld world, BlockPos root, BlockState state, boolean open) {
        if (!(state.getBlock() instanceof CompositeRootBlock block) || !block.spec.openable) return;
        String path = block.spec.id.getPath();
        if (!block.spec.functionalDoorPhysics && !ARCHITECTURE_DOORS.contains(path)) return;

        Vec3d origin = Vec3d.ofCenter(root);
        if (world.getBlockEntity(root) instanceof CompositeBlockEntity entity) {
            var payload = entity.payload();
            var shift = CompositeSourceShift.world(state, payload);
            origin = origin.add(shift.x(), shift.y() + payload.getDouble("MountY") + payload.getDouble(VerticalMount.KEY), shift.z());
        }
        play(world, origin, open, path.equals("source_allium"));
    }

    public static void rp(ServerWorld world, RpObjectEntity door, boolean open) {
        switch (door.assetId()) {
            case "door_1", "door_2" -> play(world, door.getPos(), open, false);
            case "main_gate", "small_gate" -> play(world, door.getPos(), open, true);
            default -> { }
        }
    }

    private static void play(ServerWorld world, Vec3d origin, boolean open, boolean metal) {
        var sound = metal
                ? (open ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_CLOSE)
                : (open ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE);
        world.playSound(null, origin.x, origin.y, origin.z, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }
}
