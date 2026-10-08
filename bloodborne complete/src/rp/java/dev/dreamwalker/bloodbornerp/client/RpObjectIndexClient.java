package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.object.RpObjectIndex;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Client-only hooks keep common RP index classes safe on a dedicated server. */
public final class RpObjectIndexClient {
    private static boolean initialized;
    private RpObjectIndexClient() {}
    public static void initialize(){if(initialized)return;initialized=true;ClientEntityEvents.ENTITY_LOAD.register(RpObjectIndex::loaded);ClientEntityEvents.ENTITY_UNLOAD.register(RpObjectIndex::unloaded);ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{if(client.world!=null)RpObjectIndex.clear(client.world);});}
}
