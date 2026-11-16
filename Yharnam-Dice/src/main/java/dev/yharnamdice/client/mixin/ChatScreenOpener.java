package dev.yharnamdice.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface ChatScreenOpener {
    @Invoker("openChatScreen")
    void yharnamDice$openChat(String initialText);
}
