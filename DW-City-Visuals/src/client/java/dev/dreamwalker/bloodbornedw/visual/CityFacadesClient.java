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
import net.minecraft.world.BlockRenderView;
import dev.dreamwalker.bloodbornedw.composite.NativeGridBlock;
import dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock;
import dev.dreamwalker.bloodbornedw.editor.EditorRenderScope;
import java.util.IdentityHashMap;
import java.util.Map;

public final class CityFacadesClient implements ClientModInitializer {
    private static final class ModelCaches {
        final Map<BakedModel,BakedModel[]> regional=new IdentityHashMap<>();
        final Map<BakedModel,BakedModel[]> manual=new IdentityHashMap<>();
        final Map<BakedModel,BakedModel> base=new IdentityHashMap<>();
    }
    private static volatile ModelCaches caches=new ModelCaches();
    static void clearCache() {
        caches=new ModelCaches();
        ShutterSubstrate.reload();
    }
    private static BakedModel fixed(BakedModel model,int role) {
        return fixed(model,role,false);
    }
    private static BakedModel fixed(BakedModel model,int role,boolean manual) {
        ModelCaches generation=caches;
        if(model instanceof FacadeModel facade) {
            if(facade.resolved()&&facade.role()==role&&facade.manualStyle()==manual)return model;
            model=facade.delegate();
        }
        Map<BakedModel,BakedModel[]> cache=manual?generation.manual:generation.regional;
        synchronized (generation) {
            BakedModel[] variants=cache.computeIfAbsent(model,ignored->new BakedModel[5]);
            if(variants[role]==null)variants[role]=new FacadeModel(model,role,false,manual,true);
            return variants[role];
        }
    }
    private static BakedModel fixedBase(BakedModel model) {
        ModelCaches generation=caches;
        if(model instanceof FacadeModel facade) model=facade.delegate();
        BakedModel key=model;
        synchronized (generation) {
            return generation.base.computeIfAbsent(key,ignored->new FacadeModel(key,0,true,false,true));
        }
    }
    private static boolean brick(String path) { return path.equals("source_assembly_brick_wall") || path.equals("source_bricks") || ShutterSubstrate.sourceId(new Identifier("bloodborne_dw",path)); }
    public static boolean contextualPart(Identifier id){return ShutterSubstrate.modelId(id);}
    @Override public void onInitializeClient() {
        dev.dreamwalker.bloodbornedw.composite.SodiumCompatibility.checkTranslucentTargets();
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
    public static BakedModel staticModel(BakedModel model, BlockPos root, BlockState state,BlockRenderView world) {
        if(!CityVisualClient.isAlt())return model;
        Identifier id=Registries.BLOCK.getId(state.getBlock());
        if(!id.getNamespace().equals("bloodborne_dw"))return model;
        int painted=EditorRenderScope.style(world,root);
        int role=painted>=0?painted:(ShutterSubstrate.source(state)||ShutterSubstrate.model(model,state)?ShutterSubstrate.role(world,root):FacadeMaterials.role(root));
        if(painted==0)return fixedBase(model);
        return role==0?model:fixed(model,role,painted>0);
    }
    /** visible() returns a fresh, world-bound occlusion view. Never retain or probe it. */
    public static BakedModel visibleModel(BakedModel model, BlockPos root, BlockState state,BlockRenderView world,boolean shutterPart) {
        if(!CityVisualClient.isAlt())return model;
        Identifier id=Registries.BLOCK.getId(state.getBlock());
        if(!id.getNamespace().equals("bloodborne_dw"))return model;
        int painted=EditorRenderScope.style(world,root);
        int role=painted>=0?painted:(ShutterSubstrate.source(state)||shutterPart?ShutterSubstrate.role(world,root):FacadeMaterials.role(root));
        if(painted==0)return new FacadeModel(model,0,true,false,true);
        return role==0?model:new FacadeModel(model,role,false,painted>0,true);
    }
    public static BakedModel terrainModel(BakedModel model,BlockPos pos,BlockState state,BlockRenderView world) {
        if(!CityVisualClient.isAlt())return model;
        // A native terrain snapshot can contain ordinary compact imported
        // blocks as well as city carrier blocks. Resolve an explicit editor
        // style before the native-only facade fallback below.
        Identifier id=Registries.BLOCK.getId(state.getBlock());
        if(!id.getNamespace().equals("bloodborne_dw"))return model;
        int painted=EditorRenderScope.style(world,pos);
        if(painted>=0) {
            if(model instanceof FacadeModel facade) {
                if(facade.resolved()&&facade.role()==painted&&facade.manualStyle()==(painted>0))return model;
                // Resolved models may already be bound to position-specific
                // occlusion. Never place them in a shared cache or unwrap one
                // for reuse by a different world position.
                return new FacadeModel(facade.delegate(),painted,painted==0,painted>0,true);
            }
            return new FacadeModel(model,painted,painted==0,painted>0,true);
        }
        if(!(state.getBlock() instanceof NativeGridBlock) && !(state.getBlock() instanceof NativePhysicsBlock))return model;
        if(model instanceof FacadeModel)return model;
        int role=ShutterSubstrate.model(model,state)?ShutterSubstrate.role(world,pos):FacadeMaterials.role(pos);
        return role==0?model:fixed(model,role);
    }
    public static BakedModel legacyTerrainModel(BakedModel model,BlockPos pos,BlockState state) {
        var world=net.minecraft.client.MinecraftClient.getInstance().world;
        return state.getBlock() instanceof NativeGridBlock && world!=null ? terrainModel(model,pos,state,world) : model;
    }
}



