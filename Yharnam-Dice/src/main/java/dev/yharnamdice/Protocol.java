package dev.yharnamdice;

import com.google.gson.Gson;
import dev.yharnamdice.game.TableSessions;
import dev.yharnamdice.item.RuleDiskItem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Protocol {
    public static final Identifier ACT = DiceMod.id("table_action"), VIEW = DiceMod.id("table_view"),
            DICE = DiceMod.id("table_dice"), DISK_OPEN = DiceMod.id("disk_open"), DISK_WRITE = DiceMod.id("disk_write");
    public static final int LEAVE=0, BAG=1, SELECT=2, THROW=3, START=4, MARK=5, KEEP=6, BANK=7, STAND=8;
    public static final int MAX_JSON = 16384;
    public static final Gson GSON = new Gson();
    private static final Map<UUID, DiskEdit> EDITS = new HashMap<>();
    private record DiskEdit(int slot, long token, ItemStack item) {}
    private Protocol() {}

    public record Bag(int slot, String name) {}
    public record View(long table, String title, String note, String turn, List<Bag> bags,
                       int bagSlot, int[] skins, int selected, int required, int playerCount, int minPlayers,
                       boolean host, boolean started, boolean busy, boolean canRoll, boolean canKeep,
                       boolean canBank, boolean canStand, boolean canMark, String game, boolean canLeave,
                       int[] values, int[] active, int marked, List<String> board) {}
    public record Visual(int seat, int index, int skin, float x, float y, float z,
                         float qx, float qy, float qz, float qw, float scale) {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ACT, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != 13) return;
            long table = buf.readLong();
            int action = buf.readUnsignedByte();
            int arg = buf.readInt();
            server.execute(() -> TableSessions.action(player, BlockPos.fromLong(table), action, arg));
        });
        ServerPlayNetworking.registerGlobalReceiver(DISK_WRITE, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() > MAX_JSON * 3 + 20 || buf.readableBytes() < 9) return;
            long token = buf.readLong();
            String input;
            try { input = buf.readString(MAX_JSON); }
            catch (RuntimeException invalid) { return; }
            if (buf.isReadable()) return;
            server.execute(() -> {
                DiskEdit edit = EDITS.get(player.getUuid());
                if (edit == null || edit.token != token || TableSessions.isPlaying(player)) return;
                ItemStack held = player.getInventory().getStack(edit.slot);
                if (held != edit.item || !held.isOf(DiceMod.DISK)) return;
                try {
                    RuleDiskItem.write(held, input);
                    player.getInventory().markDirty();
                    EDITS.remove(player.getUuid());
                    diskResponse(player, token, true, "");
                } catch (IllegalArgumentException invalid) {
                    diskResponse(player, token, false, invalid.getMessage());
                }
            });
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> EDITS.remove(handler.player.getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> EDITS.clear());
    }

    public static void openDisk(ServerPlayerEntity player, int slot, String json) {
        if (!ServerPlayNetworking.canSend(player, DISK_OPEN)) return;
        long token = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        EDITS.put(player.getUuid(), new DiskEdit(slot, token, player.getInventory().getStack(slot)));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(0).writeLong(token);
        buf.writeString(json, MAX_JSON);
        ServerPlayNetworking.send(player, DISK_OPEN, buf);
    }

    private static void diskResponse(ServerPlayerEntity player, long token, boolean success, String reason) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(success ? 1 : 2).writeLong(token);
        buf.writeString(reason == null ? "Invalid JSON" : reason, 300);
        ServerPlayNetworking.send(player, DISK_OPEN, buf);
    }

    public static void view(ServerPlayerEntity player, View value) {
        if (!ServerPlayNetworking.canSend(player, VIEW)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(value != null);
        if (value != null) buf.writeString(GSON.toJson(value), MAX_JSON);
        ServerPlayNetworking.send(player, VIEW, buf);
    }

    public static void visual(ServerPlayerEntity player, BlockPos pos, int rotation, List<Visual> dice) {
        if (!ServerPlayNetworking.canSend(player, DICE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(rotation);
        buf.writeVarInt(dice.size());
        for (Visual die : dice) {
            buf.writeByte(die.seat).writeByte(die.index).writeByte(die.skin)
                .writeFloat(die.x).writeFloat(die.y).writeFloat(die.z)
                .writeFloat(die.qx).writeFloat(die.qy).writeFloat(die.qz).writeFloat(die.qw).writeFloat(die.scale);
        }
        ServerPlayNetworking.send(player, DICE, buf);
    }
}
