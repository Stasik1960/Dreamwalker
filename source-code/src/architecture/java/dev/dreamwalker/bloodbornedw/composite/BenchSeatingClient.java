package dev.dreamwalker.bloodbornedw.composite;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
public final class BenchSeatingClient {
    private static net.minecraft.entity.Entity previous;
    private BenchSeatingClient(){}
    public static void initialize(){EntityRendererRegistry.register(BenchSeating.ENTITY,EmptyEntityRenderer::new);net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{var vehicle=client.player==null?null:client.player.getVehicle();if(vehicle instanceof BenchSeatEntity&&vehicle!=previous){float yaw=vehicle.getYaw();client.player.setYaw(yaw);client.player.setHeadYaw(yaw);client.player.setBodyYaw(yaw);}previous=vehicle;});}
}
