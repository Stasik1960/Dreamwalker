package dev.dreamwalker.bloodbornedw.architecture;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.util.ModelIdentifier;

/** Client-only entry point; predicate values match the separately generated item overrides. */
@Environment(EnvType.CLIENT)
public final class PrototypeArchitectureClient implements ClientModInitializer {
    @Override public void onInitializeClient() { initialize(); }
    public static void initialize() {
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            if (model != null && context.id() instanceof ModelIdentifier id && id.getNamespace().equals("bloodborne_dw") && (id.getPath().equals("prototype_ladder")||id.getPath().startsWith("prototype_ladder_art_"))) {
                model=dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderClient.wrap(model,id);
                if(java.util.Arrays.asList(id.getVariant().split(",")).contains("diagonal=true")) return new DiagonalBakedModel(model);
            }
            return model;
        }));
        for(var block:PrototypeArchitecture.LADDERS)BlockRenderLayerMap.INSTANCE.putBlock(block,RenderLayer.getCutout());
        for(var item:PrototypeArchitecture.LADDER_ITEMS){
        FabricModelPredicateProviderRegistry.register(item, PrototypeArchitecture.id("variant"),
                (stack, world, entity, seed) -> PrototypeLadderItem.variant(stack) / 2.0F);
        FabricModelPredicateProviderRegistry.register(item, PrototypeArchitecture.id("profile"),
                (stack, world, entity, seed) -> PrototypeLadderItem.profile(stack) == PrototypeLadderBlock.Profile.ALT ? 1.0F : 0.0F);
        }
        PrototypeWallArchitecture.initializeClient();
        CompositeArchitecture.initializeClient();
    }
}
