package dev.dreamwalker.bestiary.client;

import dev.dreamwalker.bestiary.ActorSpec;
import dev.dreamwalker.bestiary.Bestiary;
import dev.dreamwalker.bloodbornerp.client.CatalogMobRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class BestiaryClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        for (ActorSpec actor : ActorSpec.ACTORS) {
            EntityRendererRegistry.register(Bestiary.type(actor),
                context -> new CatalogMobRenderer(context, actor.asset().scale()));
        }
    }
}
