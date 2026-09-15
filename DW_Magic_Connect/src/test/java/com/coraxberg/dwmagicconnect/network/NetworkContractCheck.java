package com.coraxberg.dwmagicconnect.network;

import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

public final class NetworkContractCheck {
    public static void main(String[] args) {
        UUID token = UUID.randomUUID();
        var frequency = new MagicConnectData.Frequency(8, 10, 0, 40);
        var expected = new DwMagicConnectNetworking.SettingsPayload(token, 1, true, frequency, true, 7);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        DwMagicConnectNetworking.writePayload(buffer, expected);
        var decoded = DwMagicConnectNetworking.readPayload(buffer);
        check(decoded.equals(expected), "v4 payload roundtrip");
        check(buffer.readableBytes() == 0, "exact payload length");
        check(DwMagicConnectNetworking.OPEN_SCREEN.getPath().equals("open_screen_v4"), "open id");
        check(DwMagicConnectNetworking.SAVE_SETTINGS.getPath().equals("save_settings_v4"), "save id");
        check(DwMagicConnectNetworking.CLOSE_SCREEN.getPath().equals("close_screen_v4"), "close id");
        System.out.println("Network checks passed: v4 IDs and exact settings payload.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
