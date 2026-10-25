package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class FauxWaterSoundsMixin {
    @Unique private boolean dw$wasInFaux;
    @Inject(method="baseTick",at=@At("RETURN"))
    private void dw$fauxSounds(CallbackInfo ci){
        var entity=(Entity)(Object)this;if(entity.getWorld().isClient||entity.age%4!=0||!(entity instanceof PlayerEntity||entity instanceof ItemEntity))return;
        var box=entity.getBoundingBox();boolean inside=false;
        for(int x=(int)Math.floor(box.minX);x<Math.ceil(box.maxX)&&!inside;x++)for(int y=(int)Math.floor(box.minY);y<Math.ceil(box.maxY)&&!inside;y++)for(int z=(int)Math.floor(box.minZ);z<Math.ceil(box.maxZ)&&!inside;z++)inside=FauxWaterlogging.contains(entity.getWorld(),new BlockPos(x,y,z));
        if(inside&&!dw$wasInFaux)entity.playSound(SoundEvents.ENTITY_GENERIC_SPLASH,.25f,1);
        else if(inside&&entity.age%12==0&&entity.getVelocity().horizontalLengthSquared()>.0001)entity.playSound(SoundEvents.ENTITY_GENERIC_SWIM,.15f,1);
        dw$wasInFaux=inside;
    }
}
