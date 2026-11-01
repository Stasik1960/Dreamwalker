package dev.dreamwalker.bloodbornedw.visual;

import java.util.function.Function;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/** Applied once when the native shared mesh is baked in the pending resource epoch. */
public final class NativeWallVisuals {
    /** Read once per native session from the resources actually being loaded.
     * A queued server switch must not alter an already-running F3+T reload. */
    public static boolean loadingProfileIsAlt() {
        Identifier marker=new Identifier("bloodborne_dw","visuals/active-profile.json");
        try (var reader=MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(marker).getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject().get("alt").getAsBoolean();
        } catch (Exception ex) { throw new IllegalStateException("Missing city visual resource profile",ex); }
    }
    public static BakedQuad bake(int[] data,int tint,Direction face,Sprite source,boolean shade,Function<SpriteIdentifier,Sprite> textures,boolean alt) {
        FacadeMaterials.correct(data,source,alt);
        return new BakedQuad(data,tint,face,source,shade);
    }
}
