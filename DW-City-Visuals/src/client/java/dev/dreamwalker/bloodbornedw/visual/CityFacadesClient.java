package dev.dreamwalker.bloodbornedw.visual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import dev.dreamwalker.bloodbornedw.composite.NativeGridBlock;
import dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock;
import java.util.IdentityHashMap;
import java.util.Map;

public final class CityFacadesClient implements ClientModInitializer {
    private static final Map<BakedModel,BakedModel[]> MODELS=new IdentityHashMap<>();
    static synchronized void clearCache() { MODELS.clear(); }
    private static synchronized BakedModel fixed(BakedModel model,int role) {
        if(model instanceof FacadeModel facade) {
            if(facade.role()==role)return model;
            model=facade.delegate();
        }
        BakedModel[] variants=MODELS.computeIfAbsent(model,ignored->new BakedModel[4]);
        if(variants[role]==null)variants[role]=new FacadeModel(model,role);
        return variants[role];
    }
    private static boolean brick(String path) { return path.equals("source_assembly_brick_wall") || path.equals("source_bricks"); }
    @Override public void onInitializeClient() {
        CityVisualClient.registerReloadListener(FacadeMaterials::reload);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return new Identifier("bloodborne_dw","city_facades"); }
            public void reload(ResourceManager manager) { FacadeMaterials.reload(); }
        });
        ModelLoadingPlugin.register(plugin->plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)-> {
            if(context.id() instanceof ModelIdentifier id && id.getNamespace().equals("bloodborne_dw") && id.getVariant().contains("compact=true") && brick(id.getPath())) return new FacadeModel(model,0);
            return model;
        }));
    }
    public static BakedModel staticModel(BakedModel model, BlockPos root, BlockState state) {
        Identifier id=Registries.BLOCK.getId(state.getBlock());
        if(!id.getNamespace().equals("bloodborne_dw"))return model;
        int role=FacadeMaterials.role(root);
        return role==0?model:fixed(model,role);
    }
    /** visible() returns a fresh, world-bound occlusion view. Never retain or probe it. */
    public static BakedModel visibleModel(BakedModel model, BlockPos root, BlockState state) {
        Identifier id=Registries.BLOCK.getId(state.getBlock());
        if(!id.getNamespace().equals("bloodborne_dw"))return model;
        int role=FacadeMaterials.role(root);
        return role==0?model:new FacadeModel(model,role);
    }
    public static BakedModel terrainModel(BakedModel model,BlockPos pos,BlockState state) {
        if(!CityVisualClient.isAlt())return model;
        if(!(state.getBlock() instanceof NativeGridBlock) && !(state.getBlock() instanceof NativePhysicsBlock))return model;
        if(model instanceof FacadeModel)return model;
        int role=FacadeMaterials.role(pos); return role==0?model:fixed(model,role);
    }
    public static BakedModel legacyTerrainModel(BakedModel model,BlockPos pos,BlockState state) {
        return state.getBlock() instanceof NativeGridBlock ? terrainModel(model,pos,state) : model;
    }
}



