package noppes.mpm.packets;

import net.minecraft.class_2960;

/** Wire format for oversized C2S profile packets on Fabric 1.20.1. */
public final class ProfileChunks {
    public static final class_2960 CHANNEL = new class_2960("moreplayermodels", "profile_chunks_v1");

    public static final int PING = 1;
    public static final int UPDATE = 2;
    public static final int HEADER_BYTES = 1 + Integer.BYTES * 2;
    public static final int CHUNK_BYTES = 30_000;
    public static final int LEGACY_LIMIT = 32_767;
    // Leave headroom under the existing 256 KiB client receive limit for the
    // server's normalized profile and its 16-byte player UUID.
    public static final int PROFILE_LIMIT = 240 * 1024;

    private ProfileChunks() {
    }
}
