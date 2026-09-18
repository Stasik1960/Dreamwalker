package daot.compat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public final class BackportParticles {
    public static void initialize() {
        ParticleFactoryRegistry.getInstance().register(BackportEffects.WHITE_SMOKE,
            sprites -> (type,w,x,y,z,vx,vy,vz) -> new Smoke(w,x,y,z,vx,vy,vz,sprites,false));
        ParticleFactoryRegistry.getInstance().register(BackportEffects.DUST_PLUME,
            sprites -> (type,w,x,y,z,vx,vy,vz) -> new Smoke(w,x,y,z,vx,vy,vz,sprites,true));
        ParticleFactoryRegistry.getInstance().register(BackportEffects.SMALL_GUST,
            sprites -> (type,w,x,y,z,vx,vy,vz) -> new Gust(w,x,y,z,sprites));
    }
    private static final class Smoke extends AscendingParticle {
        private final boolean plume;
        Smoke(ClientWorld w,double x,double y,double z,double vx,double vy,double vz,SpriteProvider sprites,boolean plume) {
            super(w,x,y,z,plume?0.7F:0.1F,plume?0.6F:0.1F,plume?0.7F:0.1F,
                vx,vy+(plume?0.15F:0),vz,1F,sprites,plume?0.5F:0.3F,plume?7:8,plume?0.5F:-0.1F,!plume);
            this.plume=plume;
            float shade=plume?random.nextFloat()*0.2F:0;
            red=0.7294118F-shade;green=0.69411767F-shade;blue=0.7607843F-shade;
        }
        @Override public void tick() {
            if(plume) { gravityStrength*=0.88F;velocityMultiplier*=0.92F; }
            super.tick();
        }
    }
    private static final class Gust extends SpriteBillboardParticle {
        private final SpriteProvider sprites;
        Gust(ClientWorld w,double x,double y,double z,SpriteProvider sprites) {
            super(w,x,y,z);this.sprites=sprites;maxAge=12+random.nextInt(4);
            scale=0.15F;setBoundingBoxSpacing(0.15F,0.15F);setSpriteForAge(sprites);
        }
        @Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_LIT; }
        @Override public int getBrightness(float tickDelta) { return 15728880; }
        @Override public void tick() { if(age++ >= maxAge)markDead(); else setSpriteForAge(sprites); }
    }
}
