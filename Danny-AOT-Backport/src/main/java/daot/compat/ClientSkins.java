package daot.compat;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class ClientSkins {
    public enum Model { WIDE, SLIM }
    public record SkinTextures(Identifier texture, Model model) {}
    private static final Map<UUID,SkinTextures> CACHE=new ConcurrentHashMap<>();
    public static SkinTextures get(AbstractClientPlayerEntity player) {
        return new SkinTextures(player.getSkinTexture(), "slim".equals(player.getModel()) ? Model.SLIM : Model.WIDE);
    }
    public static SkinTextures resolve(UUID uuid) {
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.world!=null && mc.world.getPlayerByUuid(uuid) instanceof AbstractClientPlayerEntity player) return get(player);
        return CACHE.computeIfAbsent(uuid, key -> {
            SkinTextures fallback=new SkinTextures(DefaultSkinHelper.getTexture(key), "slim".equals(DefaultSkinHelper.getModel(key)) ? Model.SLIM : Model.WIDE);
            // SkinProvider does profile/texture requests asynchronously; no network I/O on the render thread.
            mc.getSkinProvider().loadSkin(new GameProfile(key,""), (type, id, texture) -> {
                if(type==MinecraftProfileTexture.Type.SKIN)
                    CACHE.put(key,new SkinTextures(id,"slim".equals(texture.getMetadata("model")) ? Model.SLIM : Model.WIDE));
            }, true);
            return fallback;
        });
    }
}
