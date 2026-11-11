package dev.dreamwalker.gmwarps;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

record WarpLocation(Identifier dimension, double x, double y, double z, float yaw, float pitch) {
    static WarpLocation capture(ServerPlayerEntity player) {
        return new WarpLocation(player.getWorld().getRegistryKey().getValue(), player.getX(), player.getY(),
                player.getZ(), player.getYaw(), player.getPitch());
    }

    boolean valid() {
        return dimension != null && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch) && World.isValid(BlockPos.ofFloored(x, y, z));
    }

    NbtCompound writeNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("Dimension", dimension.toString());
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        return tag;
    }

    static WarpLocation readNbt(NbtCompound tag) {
        if (!tag.contains("Dimension", NbtElement.STRING_TYPE) || !tag.contains("X", NbtElement.NUMBER_TYPE)
                || !tag.contains("Y", NbtElement.NUMBER_TYPE) || !tag.contains("Z", NbtElement.NUMBER_TYPE)
                || !tag.contains("Yaw", NbtElement.NUMBER_TYPE) || !tag.contains("Pitch", NbtElement.NUMBER_TYPE)) {
            return null;
        }
        WarpLocation location = new WarpLocation(Identifier.tryParse(tag.getString("Dimension")),
                tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"), tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        return location.valid() ? location : null;
    }
}
