package com.yharnam.casino;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

final class CasinoStaffAccess {
    // The same server-controlled tags used by Dreamwalker RP-Chat and Profiles.
    private static final Set<String> STAFF_TAGS = Set.of("rpchat_admin", "rpchat_gm", "profile_admin", "profile_gm");

    private CasinoStaffAccess() {}

    static boolean isStaff(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || player.getCommandTags().stream().anyMatch(STAFF_TAGS::contains);
    }
}
