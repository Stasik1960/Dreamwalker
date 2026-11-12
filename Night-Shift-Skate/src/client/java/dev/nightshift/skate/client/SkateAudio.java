package dev.nightshift.skate.client;

import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkateboardEntity;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;

/** Starts one continuous positional wheel loop for every nearby moving board. */
public final class SkateAudio implements ClientModInitializer {
    private static final double HEARING_RANGE = 18.0;
    private final Map<SkateboardEntity, SkateboardRollingSound> rolling = new HashMap<>();
    private ClientWorld world;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        SoundManager sounds = client.getSoundManager();
        if (client.world != world || client.player == null) {
            rolling.values().forEach(SkateboardRollingSound::finish);
            rolling.clear();
            world = client.world;
        }
        if (world == null || client.player == null || client.isPaused()) {
            return;
        }
        rolling.values().removeIf(sound -> sound.isDone() || !sounds.isPlaying(sound));
        if (client.options.getSoundVolume(SoundCategory.PLAYERS) <= 0.0f
                || client.options.getSoundVolume(SoundCategory.MASTER) <= 0.0f) {
            return;
        }

        Box audible = client.player.getBoundingBox().expand(HEARING_RANGE);
        for (SkateboardEntity board : world.getEntitiesByType(
                NightShiftSkate.SKATEBOARD_ENTITY, audible, entity -> true)) {
            if (SkateboardRollingSound.shouldStart(board) && !rolling.containsKey(board)) {
                SkateboardRollingSound sound = new SkateboardRollingSound(board);
                rolling.put(board, sound);
                sounds.play(sound);
            }
        }
    }
}
