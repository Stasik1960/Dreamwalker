package dev.dreamwalker.pickup;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public final class PickupService {
    private record Permission(ItemEntity item, PlayerEntity player) {}
    private static final ThreadLocal<Permission> PERMISSION = new ThreadLocal<>();
    private static final Map<PlayerEntity, RequestWindow> REQUESTS = new WeakHashMap<>();
    private static final class RequestWindow { long tick; int count; }

    private PickupService() {}

    /** Server-thread-only burst limit. Allows fast legitimate taps, bounds packet spam. */
    public static boolean acceptRequest(PlayerEntity player) {
        long tick = player.getWorld().getServer().getTicks();
        RequestWindow window = REQUESTS.computeIfAbsent(player, ignored -> new RequestWindow());
        if (window.tick != tick) { window.tick = tick; window.count = 0; }
        return ++window.count <= 4;
    }

    public static boolean isPermitted(ItemEntity item, PlayerEntity player) {
        Permission permission = PERMISSION.get();
        return permission != null && permission.item == item && permission.player == player;
    }

    /** Matches the search box in vanilla PlayerEntity.tickMovement, including riding. */
    public static Box pickupBox(PlayerEntity player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null && !vehicle.isRemoved()
                ? player.getBoundingBox().union(vehicle.getBoundingBox()).expand(1.0, 0.0, 1.0)
                : player.getBoundingBox().expand(1.0, 0.5, 1.0);
    }

    public static boolean pickUpNearest(PlayerEntity player) {
        if (player.getWorld().isClient || !player.isAlive() || player.isSpectator()) return false;
        var items = player.getWorld().getEntitiesByClass(ItemEntity.class, pickupBox(player),
                item -> item.isAlive() && !item.cannotPickup() && !item.getStack().isEmpty());
        items.sort(Comparator.comparingDouble((ItemEntity item) -> item.squaredDistanceTo(player))
                .thenComparingInt(Entity::getId));
        for (ItemEntity item : items) {
            int before = item.getStack().getCount();
            Permission previous = PERMISSION.get();
            PERMISSION.set(new Permission(item, player));
            try {
                // Vanilla handles ownership, inventory space, partial stacks, stats and animations.
                item.onPlayerCollision(player);
            } finally {
                if (previous == null) PERMISSION.remove(); else PERMISSION.set(previous);
            }
            if (item.isRemoved() || item.getStack().getCount() < before) return true;
        }
        return false;
    }
}
