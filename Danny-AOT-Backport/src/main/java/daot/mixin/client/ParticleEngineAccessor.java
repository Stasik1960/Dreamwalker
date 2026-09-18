package daot.mixin.client;

import java.util.Map;
import java.util.Queue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ParticleManager.class)
public interface ParticleEngineAccessor {
   @Accessor("particles")
   Map<ParticleTextureSheet, Queue<Particle>> getParticles();
}
