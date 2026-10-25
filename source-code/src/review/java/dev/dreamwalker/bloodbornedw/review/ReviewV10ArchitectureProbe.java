package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import java.util.*;
import java.lang.reflect.Proxy;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.*;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import java.util.function.Consumer;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;

/** Marker-owned read-only QA. Captures actual loaded renderer output on the
 * client thread; it does not claim a screenshot, GPU timing or visual acceptance. */
public final class ReviewV10ArchitectureProbe {
    private ReviewV10ArchitectureProbe(){}
    public static JsonObject verify(MinecraftClient client,JsonObject options){
        require(client.world!=null&&client.player!=null,"Actual client world/player must be loaded");
        JsonObject report=new JsonObject();report.addProperty("scope","actual client baked resources and BlockEntityRenderer invocation; no world edits; no GPU/manual visual claim");
        JsonArray icons=new JsonArray();report.add("builderIcons",icons);Set<Identifier> iconSprites=new HashSet<>();
        for(String path:List.of("builder_tool","composite_builder")){
            var item=net.minecraft.registry.Registries.ITEM.get(new Identifier("bloodborne_dw",path));BakedModel icon=client.getItemRenderer().getModel(new net.minecraft.item.ItemStack(item),client.world,client.player,0);List<BakedQuad> iconQuads=quads(icon,null);require(icon!=client.getBakedModelManager().getMissingModel()&&!iconQuads.isEmpty(),"Actual held/Creative builder icon has a nonmissing model");Set<String> sprites=new TreeSet<>();for(var quad:iconQuads){var sprite=quad.getSprite().getContents().getId();require(!sprite.toString().contains("missingno"),"Actual builder sprite missing");sprites.add(sprite.toString());iconSprites.add(sprite);}require(sprites.equals(Set.of("bloodborne_dw:item/"+path)),"Two manual icons resolve their own pixel-art sprite");JsonObject row=new JsonObject();row.addProperty("item","bloodborne_dw:"+path);row.addProperty("quadCount",iconQuads.size());row.add("sprites",new Gson().toJsonTree(sprites));icons.add(row);
        }require(iconSprites.size()==2,"The two actual baked builder sprites stay distinct");
        JsonArray faces=new JsonArray();report.add("frontBackUv",faces);
        for(String path:List.of(GlazingTypes.WINDOW02,GlazingTypes.WINDOW03)){
            var block=CompositeArchitecture.kindBlock(path);var part=block.spec.pose(block.getDefaultState()).parts().get(0);
            BakedModel model=client.getBakedModelManager().getModel(part.model());require(model!=client.getBakedModelManager().getMissingModel(),"Glazing model missing: "+part.model());
            List<BakedQuad> quads=quads(model,null);require(quads.size()==2,"Exactly two uncullable source faces expected for "+path);
            Set<Direction> sides=new HashSet<>();Map<Direction,Map<String,float[]>> uvAtLocalXy=new EnumMap<>(Direction.class);
            for(BakedQuad quad:quads){
                require(!quad.getSprite().getContents().getId().toString().contains("missingno"),"Actual glazing sprite missing");sides.add(quad.getFace());int[] data=quad.getVertexData();int stride=data.length/4;
                float minU=Float.POSITIVE_INFINITY,minV=minU,maxU=Float.NEGATIVE_INFINITY,maxV=maxU;
                Map<String,float[]> faceUv=new TreeMap<>();
                for(int i=0;i<4;i++){minU=Math.min(minU,Float.intBitsToFloat(data[i*stride+4]));maxU=Math.max(maxU,Float.intBitsToFloat(data[i*stride+4]));minV=Math.min(minV,Float.intBitsToFloat(data[i*stride+5]));maxV=Math.max(maxV,Float.intBitsToFloat(data[i*stride+5]));String xy=data[i*stride]+":"+data[i*stride+1];require(faceUv.put(xy,new float[]{Float.intBitsToFloat(data[i*stride+4]),Float.intBitsToFloat(data[i*stride+5])})==null,"Actual face has four distinct planar X/Y vertices");}
                uvAtLocalXy.put(quad.getFace(),faceUv);
                // Baked UVs are contracted toward the center by vanilla's
                // texture bleed correction; compare both faces against each
                // other and the source rectangle with that known shrink.
                float centerU=(quad.getSprite().getFrameU(.125)+quad.getSprite().getFrameU(5.625))/2,centerV=(quad.getSprite().getFrameV(10.25)+quad.getSprite().getFrameV(15.75))/2;
                require(Math.abs((minU+maxU)/2-centerU)<1e-6&&Math.abs((minV+maxV)/2-centerV)<1e-6,"Both faces retain the exact source front rectangle center, not another window UV");
                float shrink=quad.getSprite().getAnimationFrameDelta();
                require(Math.abs(minU-MathHelper.lerp(shrink,quad.getSprite().getFrameU(.125),centerU))<1e-6&&Math.abs(maxU-MathHelper.lerp(shrink,quad.getSprite().getFrameU(5.625),centerU))<1e-6&&Math.abs(minV-MathHelper.lerp(shrink,quad.getSprite().getFrameV(10.25),centerV))<1e-6&&Math.abs(maxV-MathHelper.lerp(shrink,quad.getSprite().getFrameV(15.75),centerV))<1e-6,"Actual baked front/back extents match the exact source rectangle after vanilla bleed correction");
                JsonObject row=new JsonObject();row.addProperty("type",DebugCatalogue.entry(block.getDefaultState()).temporaryId());row.addProperty("model",part.model().toString());row.addProperty("face",quad.getFace().asString());row.addProperty("sprite",quad.getSprite().getContents().getId().toString());row.add("actualAtlasUvBounds",new Gson().toJsonTree(new float[]{minU,minV,maxU,maxV}));row.add("actualUvAtLocalXyVertexBits",new Gson().toJsonTree(faceUv));row.addProperty("vertexHash",Arrays.hashCode(data));faces.add(row);
            }
            require(sides.equals(Set.of(Direction.NORTH,Direction.SOUTH)),"Both planar faces are present after 03 intrinsic normalization");
            Map<String,float[]> north=uvAtLocalXy.get(Direction.NORTH),south=uvAtLocalXy.get(Direction.SOUTH);require(north.keySet().equals(south.keySet()),"Front/back actual vertices share the same source X/Y corners");
            for(String corner:north.keySet())for(int axis=0;axis<2;axis++)require(Math.abs(north.get(corner)[axis]-south.get(corner)[axis])<1e-6,"Reverse-face U correction preserves matching front/back atlasUV at each physical X/Y corner");
            for(int i=faces.size()-2;i<faces.size();i++)faces.get(i).getAsJsonObject().addProperty("frontBackAtlasUvEqualAtSameLocalXy",true);
        }
        JsonArray samples=new JsonArray();report.add("shiftedInstances",samples);
        if(options!=null&&options.has("samples"))for(JsonElement raw:options.getAsJsonArray("samples")){
            JsonObject requested=raw.getAsJsonObject();JsonArray xyz=requested.getAsJsonArray("root");BlockPos root=new BlockPos(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt());
            require(client.world.isChunkLoaded(root),"Probe root chunk actually loaded: "+root);BlockState state=client.world.getBlockState(root);
            require(client.world.getBlockEntity(root) instanceof CompositeBlockEntity,"Native/composite owner BE actually present: "+root);CompositeBlockEntity be=(CompositeBlockEntity)client.world.getBlockEntity(root);
            var owner=be.resident();require(owner!=null,"Actual synchronized owner UUID missing");require(owner.instanceId().toString().equals(requested.get("uuid").getAsString())&&owner.registryId().equals(requested.get("registry").getAsString()),"Actually loaded root preserves the exact authored UUID and registry");double offset=VerticalMount.offset(client.world,root);
            require(Math.abs(offset-requested.get("expectedOffset").getAsDouble())<1e-9,"Actual server/client height mismatch");String type=DebugCatalogue.entry(state).temporaryId();require(type.equals(requested.get("expectedType").getAsString()),"Actual held/server art type differs from sample");
            @SuppressWarnings("unchecked") BlockEntityRenderer<CompositeBlockEntity> renderer=(BlockEntityRenderer<CompositeBlockEntity>)(Object)client.getBlockEntityRenderDispatcher().get(be);require(renderer!=null,"Actual root renderer missing");
            Capture actual=new Capture();renderer.render(be,0,new MatrixStack(),layer->actual,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
            require(actual.vertices>0,"Shifted BER emits actual vertices: "+type);
            // A detached typed copy runs the same loaded renderer without
            // mutating world/BE/UUID. Native offset 0 intentionally renders
            // through the chunk, so use 1 for its comparison invocation.
            CompositeBlockEntity baseline=be instanceof SourceLadderBlockEntity?new SourceLadderBlockEntity(root,state):new CompositeBlockEntity(root,state);baseline.readNbt(be.createNbt());NbtCompound payload=be.payload();double comparisonOffset=VerticalMount.isNative(state)?1:0;payload.putDouble(VerticalMount.KEY,comparisonOffset);baseline.set(owner,be.contributions(),payload);
            Capture prior=new Capture();renderer.render(baseline,0,new MatrixStack(),layer->prior,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
            require(prior.vertices==actual.vertices,"Height does not drop/add source quad vertices");
            for(int i=0;i<6;i++){double expected=prior.bounds[i]+(i==1||i==4?offset-comparisonOffset:0);require(Math.abs(expected-actual.bounds[i])<1e-5,"Height renderer moves Y alone without changing source art extents");}
            OldAnchorCapture oldAnchor=new OldAnchorCapture(state);if(VerticalMount.isNative(state)&&offset!=0){((FabricBakedModel)client.getBlockRenderManager().getModel(state)).emitBlockQuads(client.world,state,root,()->Random.create(0),oldAnchor);oldAnchor.requireNoGeometry();}
            var object=CompositeRuntime.instance(client.world,root,state,owner.instanceId(),be.payload());int ownedCells=0;
            for(var cell:object.cells().entrySet()){
                BlockPos pos=CompositeData.pos(owner.root().add(cell.getKey()));require(CompositeRuntime.contributions(client.world,pos).stream().anyMatch(c->c.owner().equals(owner)),"Actual shifted helper/client ledger points to same UUID");ownedCells++;
                var expected=CompositeRuntime.shape(cell.getValue().collision());var current=client.world.getBlockState(pos).getCollisionShape(client.world,pos);
                require(!VoxelShapes.matchesAnywhere(expected,current,BooleanBiFunction.ONLY_FIRST),"Actual client native+overlay collision contains owner physical contribution");
            }
            var picked=CompositeRuntime.pick(client.world,owner);require(DebugCatalogue.entry(picked).temporaryId().equals(type)&&picked.getSubNbt("CompositePayload")==null&&picked.getSubNbt("BlockEntityTag")==null,"Client pick preserves art type and excludes owner links/height privilege");
            JsonObject row=new JsonObject();row.add("root",xyz.deepCopy());row.addProperty("type",type);row.addProperty("registry",owner.registryId());row.addProperty("uuid",owner.instanceId().toString());row.addProperty("actualOffset",offset);row.addProperty("rendererClass",renderer.getClass().getName());row.addProperty("actualVertices",actual.vertices);row.add("actualLocalRendererBounds",new Gson().toJsonTree(actual.bounds));row.addProperty("staticEmitterCallsAtOldAnchor",oldAnchor.geometrySinkCalls);row.add("staticOldAnchorCapture",oldAnchor.report());row.addProperty("shiftedOwnerCells",ownedCells);row.addProperty("clientPick",DebugCatalogue.entry(picked).temporaryId());
            if(state.getBlock() instanceof PrototypeLadderBlock ladder&&offset!=0){var strip=ladder.climbingShape(state).getBoundingBoxes().get(0).offset(root).offset(0,offset,0).contract(.001);require(VerticalMount.climbing(client.world,strip).orElseThrow().equals(root),"Actual client climbing region resolves translated ladder to same root");row.addProperty("shiftedClimbingOwnerResolved",true);}
            samples.add(row);
        }
        report.addProperty("sampleCount",samples.size());report.addProperty("status","PASS_ACTUAL_LOADED_CLIENT_BAKES_UV_AND_REQUESTED_RENDERER_HEIGHT_SAMPLES");report.addProperty("manualVisual","NOT_RUN");return report;
    }
    private static List<BakedQuad> quads(BakedModel model,BlockState state){List<BakedQuad> out=new ArrayList<>();out.addAll(model.getQuads(state,null,Random.create(0)));for(Direction side:Direction.values())out.addAll(model.getQuads(state,side,Random.create(0)));return out;}
    private static void require(boolean truth,String message){if(!truth)throw new IllegalStateException(message);}
    /** Captures every actual geometry sink, while retaining wrapper transform
     * bookkeeping. A context request or balanced push/pop is not emitted art. */
    @SuppressWarnings("removal")
    private static final class OldAnchorCapture implements RenderContext {
        private final BlockState state;
        private final Deque<QuadTransform> transforms=new ArrayDeque<>();
        int contextCalls,pushCalls,popCalls,maxTransformDepth,emitterRequests,emitterPreparations,meshSubmissions,fallbackSubmissions,geometrySinkCalls,actualQuads,actualMeshQuads;
        OldAnchorCapture(BlockState state){this.state=state;}
        @Override public void pushTransform(QuadTransform transform){contextCalls++;pushCalls++;require(transform!=null&&transforms.size()<64,"Old-anchor wrapper transform is null or exceeds bounded depth");transforms.push(transform);maxTransformDepth=Math.max(maxTransformDepth,transforms.size());}
        @Override public void popTransform(){contextCalls++;popCalls++;require(!transforms.isEmpty(),"Old-anchor wrapper transform stack underflow");transforms.pop();}
        @Override public boolean hasTransform(){contextCalls++;return !transforms.isEmpty();}
        @Override public boolean isFaceCulled(Direction face){contextCalls++;return false;}
        @Override public QuadEmitter getEmitter(){contextCalls++;emitterRequests++;return (QuadEmitter)Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(),new Class<?>[]{QuadEmitter.class},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"QA old-anchor geometry capture";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->throw new IllegalStateException("Unknown QA emitter object method");};
            if(method.getName().equals("emit")){geometrySinkCalls++;actualQuads++;throw new IllegalStateException("Old static root emitted a quad while BER offset active");}
            if(method.getReturnType().isInstance(proxy)){emitterPreparations++;return proxy;}
            throw new IllegalStateException("Unsupported QA emitter query: "+method.getName());
        });}
        @Override public Consumer<Mesh> meshConsumer(){contextCalls++;return mesh->{meshSubmissions++;require(mesh!=null,"Old-anchor mesh submission is null");mesh.forEach(quad->{geometrySinkCalls++;actualMeshQuads++;actualQuads++;throw new IllegalStateException("Old static root emitted mesh geometry while BER offset active");});};}
        @Override public BakedModelConsumer bakedModelConsumer(){contextCalls++;return new BakedModelConsumer(){
            @Override public void accept(BakedModel model){accept(model,state);}
            @Override public void accept(BakedModel model,BlockState explicitState){fallbackSubmissions++;require(model!=null,"Old-anchor fallback model is null");int count=quads(model,explicitState).size();if(count>0){geometrySinkCalls++;actualQuads+=count;throw new IllegalStateException("Old static root emitted fallback model geometry while BER offset active");}}
        };}
        void requireNoGeometry(){require(transforms.isEmpty()&&pushCalls==popCalls,"Old-anchor wrapper must balance every transform");require(geometrySinkCalls==0&&actualQuads==0&&actualMeshQuads==0,"Native chunk mesh must emit zero actual old-anchor quads/mesh vertices");}
        JsonObject report(){JsonObject out=new JsonObject();out.addProperty("scope","Entire loaded FRAPI model including wrappers; uncullable capture rejects actual emitter.emit, mesh quads and nonempty fallback model geometry. Balanced transform bookkeeping is observed separately.");out.addProperty("contextMethodCalls",contextCalls);out.addProperty("wrapperPushTransforms",pushCalls);out.addProperty("wrapperPopTransforms",popCalls);out.addProperty("wrapperTransformDepthAfter",transforms.size());out.addProperty("maxWrapperTransformDepth",maxTransformDepth);out.addProperty("emitterRequests",emitterRequests);out.addProperty("emitterPreparations",emitterPreparations);out.addProperty("meshSubmissions",meshSubmissions);out.addProperty("fallbackModelSubmissions",fallbackSubmissions);out.addProperty("geometrySinkCalls",geometrySinkCalls);out.addProperty("actualQuads",actualQuads);out.addProperty("actualMeshQuads",actualMeshQuads);return out;}
    }
    private static final class Capture implements VertexConsumer {
        int vertices;double x,y,z;double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void next(){vertices++;bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.min(bounds[2],z);bounds[3]=Math.max(bounds[3],x);bounds[4]=Math.max(bounds[4],y);bounds[5]=Math.max(bounds[5],z);}
        @Override public void fixedColor(int r,int g,int b,int a){}
        @Override public void unfixColor(){}
    }
}

