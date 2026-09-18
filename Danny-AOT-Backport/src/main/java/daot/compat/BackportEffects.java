package daot.compat;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
public final class BackportEffects {
    public static final DefaultParticleType WHITE_SMOKE = particle("white_smoke");
    public static final DefaultParticleType DUST_PLUME = particle("dust_plume");
    public static final DefaultParticleType SMALL_GUST = particle("small_gust");
    public static final SoundEvent ARMADILLO_UNROLL = sound("entity.armadillo.unroll_finish");
    public static final SoundEvent WIND_BURST = sound("entity.wind_charge.wind_burst");
    public static final SoundEvent OMINOUS_ACTIVATE = sound("block.trial_spawner.ominous_activate");
    private static DefaultParticleType particle(String name) {
        return Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "backport/"+name), FabricParticleTypes.simple());
    }
    private static SoundEvent sound(String name) {
        Identifier id=new Identifier("dannys-aot","backport."+name);
        return Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id));
    }
    public static void initialize() {}
}
