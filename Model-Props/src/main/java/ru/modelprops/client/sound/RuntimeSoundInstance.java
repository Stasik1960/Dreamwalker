package ru.modelprops.client.sound;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

final class RuntimeSoundInstance extends AbstractSoundInstance {
    private final WeightedSoundSet soundSet;

    RuntimeSoundInstance(Identifier id, Sound sound, WeightedSoundSet soundSet,
                         double x, double y, double z, float volume, float pitch) {
        super(id, SoundCategory.PLAYERS, SoundInstance.createRandom());
        this.sound = sound;
        this.soundSet = soundSet;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.pitch = pitch;
        this.repeat = false;
        this.repeatDelay = 0;
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
        this.relative = false;
    }

    @Override
    public WeightedSoundSet getSoundSet(SoundManager soundManager) {
        return soundSet;
    }
}
