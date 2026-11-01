package dev.dreamwalker.bloodbornedw.visual.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.integrated.IntegratedServerLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(IntegratedServerLoader.class)
public interface IntegratedVisualReloadAccessor {
    @Invoker("start") void dw$reopenAcceptedSave(Screen parent, String save, boolean safeMode, boolean showBackupPrompt);
}
