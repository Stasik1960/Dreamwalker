package dev.dreamwalker.flightqa.mixin;

import net.minecraft.client.gui.screen.BackupPromptScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BackupPromptScreen.class)
public interface FlightBackupPromptAccessor {
    @Accessor("callback")
    BackupPromptScreen.Callback dw$backupCallback();
}
