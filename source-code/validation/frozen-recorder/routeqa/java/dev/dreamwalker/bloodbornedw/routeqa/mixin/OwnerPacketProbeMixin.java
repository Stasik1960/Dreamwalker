package dev.dreamwalker.bloodbornedw.routeqa.mixin;
import dev.dreamwalker.bloodbornedw.routeqa.RouteClient;
import dev.dreamwalker.bloodbornedw.composite.CompositeClient;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Read scalar packet metadata before Fabric dispatch; never read/retain data. */
@Mixin(value=CompositeClient.class,remap=false)
public abstract class OwnerPacketProbeMixin {
    // Fabric can bypass vanilla onCustomPayload entirely. Observe the actual
    // existing decoder once, without parsing, copying or retaining its buffer.
    @ModifyVariable(method="decodeOwners",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private static PacketByteBuf count(PacketByteBuf buffer){RouteClient.ownerPacket(buffer.readableBytes());return buffer;}
}
