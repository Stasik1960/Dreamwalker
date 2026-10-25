package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** A configurable shortcut to the existing authorized server command. */
public final class NoCollideKeyClient {
    public static KeyBinding KEY;
    private NoCollideKeyClient(){}
    public static void initialize(){
        KEY=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.bloodborne_dw.no_collide",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.dreamwalker_bb"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{while(KEY.wasPressed()){
            if(client.player==null||client.getNetworkHandler()==null||client.currentScreen!=null)continue;
            if(!NoCollidePlacement.isModPlacement(client.player.getMainHandStack()))client.player.sendMessage(Text.literal("Неподходящий блок"),true);
            else client.getNetworkHandler().sendChatCommand("bb no-collide");
        }});
    }
}
