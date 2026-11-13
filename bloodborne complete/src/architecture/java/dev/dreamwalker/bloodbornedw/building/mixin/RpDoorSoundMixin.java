package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.DoorSounds;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.link.ObjectPolicies;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ObjectPolicies.class, remap = false)
abstract class RpDoorSoundMixin {
    @Inject(method = "changed", at = @At("HEAD"))
    private static void dreamwalker$playCommittedRpDoorSound(ServerWorld world, MechanismLinks.TargetRef ref,
            boolean before, boolean after, CallbackInfo ci) {
        if (before != after && ref.kind() == MechanismLinks.Kind.RP
                && world.getEntity(ref.instance()) instanceof RpObjectEntity door) {
            DoorSounds.rp(world, door, after);
        }
    }
}
