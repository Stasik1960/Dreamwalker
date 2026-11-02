package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Reads the resource profile currently loaded by Minecraft. */
public final class NativeWallVisuals {
    /** Read once per native session from the resources actually being loaded.
     * A queued server switch must not alter an already-running F3+T reload. */
    public static boolean loadingProfileIsAlt() {
        Identifier marker=new Identifier("bloodborne_dw","visuals/active-profile.json");
        try (var reader=MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(marker).getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject().get("alt").getAsBoolean();
        } catch (Exception ex) { throw new IllegalStateException("Missing city visual resource profile",ex); }
    }
}
