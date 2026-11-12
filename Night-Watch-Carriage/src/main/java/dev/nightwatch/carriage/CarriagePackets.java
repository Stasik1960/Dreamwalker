package dev.nightwatch.carriage;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public final class CarriagePackets {
    public static final Identifier DRIVE = NightWatchCarriage.id("drive");
    public static final Identifier ACTION = NightWatchCarriage.id("action");

    public enum Action {
        SEAT_LEFT(CarriageEntity.Seat.LEFT),
        SEAT_RIGHT(CarriageEntity.Seat.RIGHT),
        SEAT_PULLER(CarriageEntity.Seat.PULLER),
        TOGGLE_DRIVER(null),
        TOGGLE_HOOD(null),
        TOGGLE_LAMPS(null);

        final CarriageEntity.Seat seat;

        Action(CarriageEntity.Seat seat) { this.seat = seat; }
    }

    private CarriagePackets() { }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DRIVE, (server, player, handler, buf, reply) -> {
            if (buf.readableBytes() != 1) return;
            int keys = buf.readUnsignedByte();
            if (keys > 15) return;
            server.execute(() -> {
                if (player.getVehicle() instanceof CarriageEntity carriage) carriage.acceptInput(player, keys);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, reply) -> {
            if (buf.readableBytes() != 5) return;
            int entityId = buf.readInt();
            int opcode = buf.readUnsignedByte();
            if (opcode >= Action.values().length) return;
            Action action = Action.values()[opcode];
            server.execute(() -> {
                Entity entity = player.getWorld().getEntityById(entityId);
                if (entity instanceof CarriageEntity carriage) carriage.acceptAction(player, action);
            });
        });
    }
}
