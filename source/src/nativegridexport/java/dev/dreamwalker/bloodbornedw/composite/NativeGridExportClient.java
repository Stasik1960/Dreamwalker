package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;
import org.joml.Vector3f;

/** Asset-only offline export. The authoring tool is never part of the delivered mod. */
public final class NativeGridExportClient implements ClientModInitializer {
    private static final Gson JSON=new Gson();
    private boolean done; private int readyTicks;
    private final Map<Identifier,Boolean> opaqueTextures=new HashMap<>();
    @Override public void onInitializeClient(){
        if(System.getProperty("dw.nativegrid.export.request")==null)return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(done||client.getOverlay()!=null||client.currentScreen==null)return;
            if(++readyTicks<30)return;done=true;
            Path out=Path.of(System.getProperty("dw.nativegrid.export.output"));
            try{Files.createDirectories(out);run(client,out);write(out.resolve("export-result.json"),Map.of("status","PASS","worldOpened",false));}
            catch(Throwable failure){failure.printStackTrace();try{Files.createDirectories(out);write(out.resolve("export-result.json"),Map.of("status","FAIL","error",failure.toString()));}catch(IOException ignored){}}
            client.scheduleStop();
        });
    }
    private static void write(Path path,Object data)throws IOException{Files.writeString(path,JSON.toJson(data)+"\n",StandardCharsets.UTF_8);}
    private void run(MinecraftClient client,Path out)throws Exception{
        JsonObject request=JsonParser.parseString(Files.readString(Path.of(System.getProperty("dw.nativegrid.export.request")))).getAsJsonObject();
        long poses=0,quads=0;
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("source-geometry.jsonl"),StandardCharsets.UTF_8)){
            for(JsonElement raw:request.getAsJsonArray("poses")){
                JsonObject row=raw.getAsJsonObject(),exported=exportPose(client,row);
                writer.write(JSON.toJson(exported));writer.newLine();poses++;quads+=exported.getAsJsonArray("quads").size();
            }
        }
        try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("native-geometry.jsonl"),StandardCharsets.UTF_8)){
            JsonArray nativeStates=request.has("nativeStates")?request.getAsJsonArray("nativeStates"):new JsonArray();
            for(JsonElement raw:nativeStates){JsonObject row=raw.getAsJsonObject(),exported=exportNative(client,row);writer.write(JSON.toJson(exported));writer.newLine();}
        }
        if(request.has("doorFootprints"))exportDoorFootprints(request.getAsJsonObject("doorFootprints"),out.resolve("door-footprints.json"));
        write(out.resolve("geometry-summary.json"),Map.of("schemaVersion",1,"poses",poses,"quads",quads,"runtimeVersion",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getMetadata().getVersion().getFriendlyString(),"worldOpened",false));
    }
    private JsonObject exportPose(MinecraftClient client,JsonObject row)throws Exception{
        BlockState state=state(row);
        if(!(state.getBlock() instanceof CompositeRootBlock block))throw new IllegalArgumentException("Not a source composite: "+row);
        NbtCompound payload=payload(row);
        JsonObject result=new JsonObject();result.addProperty("key",row.get("key").getAsString());result.add("sourcePose",row.deepCopy());
        result.addProperty("openable",block.spec.openable);result.addProperty("functionalDoor",block.spec.functionalDoorPhysics);result.addProperty("displayName",block.spec.displayName);
        JsonArray quads=new JsonArray(),models=new JsonArray();MatrixStack matrix=new MatrixStack();
        var worldShift=CompositeSourceShift.world(state,payload);matrix.translate(worldShift.x(),worldShift.y()+payload.getDouble("MountY"),worldShift.z());
        rotate(matrix,.5,.5,.5,-state.get(CompositeRootBlock.ROTATION)*45,false);
        if(ThinWindowRootBlock.mount(state)!=ThinWindowRootBlock.Mount.VERTICAL){
            rotate(matrix,.5,.5,.5,ThinWindowRootBlock.mount(state)==ThinWindowRootBlock.Mount.FLOOR?90:-90,true);
            rotate(matrix,.5,.5,.5,block.spec.variant(state).mountAlignmentYaw(),false);
        }
        for(var part:ImportedStairLegacyArt.selectedParts(block,state,payload)){
            // Match the installed blocks.2 renderer exactly. Profile-specific
            // baked wrappers receive the real state; no hypothetical alt model
            // is invented merely because a descriptor also has altModel.
            Identifier modelId=part.model();models.add(modelId.toString());
            matrix.push();matrix.translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);
            rotate(matrix,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);
            rotate(matrix,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);
            rotate(matrix,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
            BakedModel model=client.getBakedModelManager().getModel(modelId);
            if(model==client.getBakedModelManager().getMissingModel())throw new IllegalStateException("Missing baked source model: "+modelId);
            boolean emissive=modelId.getPath().startsWith("remaining/source_emissive/");
            collect(client,model,state,matrix,quads,block.spec.translucent?"translucent":null,emissive,block.spec.id.toString(),RemainingSourceTint.biomeTinted(block),false);
            matrix.pop();
        }
        result.add("models",models);result.add("quads",quads);
        Map<Cell,Footprint> footprint=CompositeSourceShift.footprint(block.spec,state,payload);
        Map<Cell,List<Box>> selection=block.spec.simplePhysicalCollision?ImportedSelectionShapes.footprint(block,state,payload):new TreeMap<>();
        if(!block.spec.simplePhysicalCollision)for(var e:footprint.entrySet())selection.put(e.getKey(),e.getValue().selection());
        Map<Cell,List<Box>> climb=climbing(block,state,payload);
        Map<Cell,Integer> carriers=ImportedWholeSourceOpticsV127.carriers(block.spec,state,payload);
        SortedSet<Cell> all=new TreeSet<>(footprint.keySet());all.addAll(selection.keySet());all.addAll(climb.keySet());all.addAll(carriers.keySet());all.add(Cell.ORIGIN);
        JsonArray cells=new JsonArray();
        for(Cell c:all){JsonObject cell=new JsonObject();cell.add("cell",JSON.toJsonTree(new int[]{c.x(),c.y(),c.z()}));
            cell.add("collision",boxes(footprint.getOrDefault(c,Footprint.EMPTY).collision()));cell.add("outline",boxes(selection.getOrDefault(c,List.of())));cell.add("climbing",boxes(climb.getOrDefault(c,List.of())));
            JsonObject optics=new JsonObject();optics.addProperty("opacity",Math.max(c.equals(Cell.ORIGIN)?ImportedSourceLightingV127.opacity(block.spec,state,0):0,carriers.getOrDefault(c,0)));optics.addProperty("luminance",c.equals(Cell.ORIGIN)?state.getLuminance():0);
            optics.addProperty("ambientOcclusion",1);optics.addProperty("transparent",true);optics.addProperty("sidedTransparency",true);optics.addProperty("opaque",false);optics.addProperty("opaqueFullCube",false);optics.addProperty("solidBlock",false);optics.addProperty("suffocates",false);optics.addProperty("blockVision",false);cell.add("optics",optics);
            cell.add("fluid",c.equals(Cell.ORIGIN)?fluid(state):JsonNull.INSTANCE);cells.add(cell);
        }
        result.add("cells",cells);return result;
    }
    private JsonObject exportNative(MinecraftClient client,JsonObject request)throws Exception{
        BlockState state=state(request);if(state.hasBlockEntity())throw new IllegalArgumentException("Native base has a BlockEntity: "+request);
        boolean physicsOnly=request.has("physicsOnly")&&request.get("physicsOnly").getAsBoolean();
        if(!physicsOnly&&(state.getBlock() instanceof StaticArchitectureBlock||CompositeTerrain.eligible(state)))throw new IllegalArgumentException("Source terrain is not a self-contained native substrate; decompose its actual fragments first: "+request);
        if(!physicsOnly&&MaterialSlabs.hasOverlay(state)&&StaticArchitectureCatalog.recipe(state).resident()>=0)throw new IllegalArgumentException("Slab overlay has a visible resident beyond its baked model; explicit source export required: "+request);
        JsonObject result=new JsonObject();result.add("state",request.deepCopy());JsonArray quads=new JsonArray();
        result.addProperty("renderType",state.getRenderType().name());
        if(!physicsOnly&&state.getRenderType()==BlockRenderType.MODEL){
            var model=client.getBlockRenderManager().getModel(state);result.addProperty("modelClass",model.getClass().getName());
            boolean vanilla=((net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel)model).isVanillaAdapter();
            result.addProperty("vanillaAdapter",vanilla);
            if(!vanilla)throw new IllegalArgumentException("Native substrate uses custom FRAPI emission; explicit finite exporter is required: "+request+" model="+model.getClass().getName());
            collect(client,model,state,new MatrixStack(),quads,nativeLayer(state),false,request.get("registryId").getAsString(),false,true);
            if(quads.isEmpty())throw new IllegalStateException("MODEL substrate emitted no baked quads; never silently discard its visual: "+request+" model="+model.getClass().getName());
        }
        result.addProperty("nativeVisualComplete",!physicsOnly);
        result.add("quads",quads);var view=EmptyBlockView.INSTANCE;var pos=BlockPos.ORIGIN;
        result.add("collision",mcBoxes(state.getCollisionShape(view,pos,ShapeContext.absent()).getBoundingBoxes()));result.add("outline",mcBoxes(state.getOutlineShape(view,pos,ShapeContext.absent()).getBoundingBoxes()));result.add("culling",mcBoxes(state.getCullingShape(view,pos).getBoundingBoxes()));result.add("climbing",new JsonArray());
        JsonObject optics=new JsonObject();optics.addProperty("opacity",state.getOpacity(view,pos));optics.addProperty("luminance",state.getLuminance());optics.addProperty("ambientOcclusion",state.getAmbientOcclusionLightLevel(view,pos));optics.addProperty("transparent",state.isTransparent(view,pos));optics.addProperty("sidedTransparency",state.hasSidedTransparency());optics.addProperty("opaque",state.isOpaque());optics.addProperty("opaqueFullCube",state.isOpaqueFullCube(view,pos));optics.addProperty("solidBlock",state.isSolidBlock(view,pos));optics.addProperty("suffocates",state.shouldSuffocate(view,pos));optics.addProperty("blockVision",state.shouldBlockVision(view,pos));result.add("optics",optics);result.add("fluid",fluid(state));return result;
    }
    private void collect(MinecraftClient client,BakedModel model,BlockState state,MatrixStack matrix,JsonArray target,String layer,boolean emissive,String tintSource,boolean biomeTint,boolean nativeBase)throws Exception{
        for(int bucket=-1;bucket<6;bucket++)for(BakedQuad quad:model.getQuads(state,bucket<0?null:Direction.values()[bucket],Random.create(42))){
            Sprite sprite=quad.getSprite();if(sprite.getContents().getId().getPath().equals("missingno"))throw new IllegalStateException("Missing source sprite");
            String actualLayer=layer!=null?layer:opaqueTexture(client,sprite.getContents().getId())?"solid":"cutout";
            JsonObject q=new JsonObject(),material=new JsonObject();material.addProperty("sprite",sprite.getContents().getId().toString());material.addProperty("layer",actualLayer);
            if(quad.hasColor()&&(biomeTint||nativeBase)){material.addProperty("tintSource",tintSource);material.addProperty("tintMode",biomeTint?"remaining_grass":"native");}
            q.add("material",material);q.addProperty("tint",quad.hasColor()&&(biomeTint||nativeBase)?quad.getColorIndex():-1);
            q.addProperty("shade",quad.hasShade());q.addProperty("ao",model.useAmbientOcclusion());q.addProperty("emissive",emissive);q.addProperty("opaque",actualLayer.equals("solid"));
            JsonArray vertices=new JsonArray();int[] data=quad.getVertexData();int stride=data.length/4;Vector3f[] points=new Vector3f[4];
            for(int i=0;i<4;i++){int at=i*stride;Vector3f p=new Vector3f(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));matrix.peek().getPositionMatrix().transformPosition(p);points[i]=p;}
            Vector3f normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));if(normal.lengthSquared()<1e-15f)normal.set(quad.getFace().getOffsetX(),quad.getFace().getOffsetY(),quad.getFace().getOffsetZ());else normal.normalize();
            q.add("normal",JSON.toJsonTree(new float[]{normal.x,normal.y,normal.z}));q.addProperty("face",Direction.getFacing(normal.x,normal.y,normal.z).ordinal());q.addProperty("cull",nativeBase?bucket:-1);
            int packed=packNormal(normal);
            for(int i=0;i<4;i++){int at=i*stride;JsonArray v=new JsonArray();v.add(points[i].x);v.add(points[i].y);v.add(points[i].z);v.add((Float.intBitsToFloat(data[at+4])-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU())*16);v.add((Float.intBitsToFloat(data[at+5])-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV())*16);v.add(data[at+3]);v.add(emissive?0x00F000F0:data[at+6]);v.add(packed);vertices.add(v);}
            q.add("vertices",vertices);target.add(q);
        }
    }
    private boolean opaqueTexture(MinecraftClient client,Identifier sprite)throws IOException{
        Boolean known=opaqueTextures.get(sprite);if(known!=null)return known;
        Identifier texture=new Identifier(sprite.getNamespace(),"textures/"+sprite.getPath()+".png");boolean opaque=true;
        try(var stream=client.getResourceManager().getResourceOrThrow(texture).getInputStream();var image=net.minecraft.client.texture.NativeImage.read(stream)){
            for(int y=0;y<image.getHeight()&&opaque;y++)for(int x=0;x<image.getWidth();x++)if((image.getColor(x,y)>>>24)!=255){opaque=false;break;}
        }
        opaqueTextures.put(sprite,opaque);return opaque;
    }
    private static String nativeLayer(BlockState state){RenderLayer layer=RenderLayers.getBlockLayer(state);return layer==RenderLayer.getTranslucent()?"translucent":layer==RenderLayer.getCutout()?"cutout":layer==RenderLayer.getCutoutMipped()?"cutout_mipped":"solid";}
    private static JsonElement fluid(BlockState state){var fluid=state.getFluidState();if(fluid.isEmpty())return JsonNull.INSTANCE;JsonObject out=new JsonObject();String id=Registries.FLUID.getId(fluid.getFluid()).toString();if(id.endsWith("flowing_water"))id="minecraft:water";if(id.endsWith("flowing_lava"))id="minecraft:lava";out.addProperty("id",id);out.addProperty("level",fluid.getLevel());out.addProperty("falling",fluid.contains(net.minecraft.fluid.FlowableFluid.FALLING)&&fluid.get(net.minecraft.fluid.FlowableFluid.FALLING));return out;}
    private static NbtCompound payload(JsonObject row){
        NbtCompound payload=ImportedTypeAliases.markPayload(null);
        JsonArray shift=row.has("shift")?row.getAsJsonArray("shift"):JSON.toJsonTree(new double[]{0,0,0}).getAsJsonArray();
        NbtList values=new NbtList();for(var n:shift)values.add(NbtDouble.of(n.getAsDouble()));payload.put("SourceShift",values);
        payload.putDouble("MountY",row.has("mountY")?row.get("mountY").getAsDouble():0);
        if(row.has("legacyStairArt"))payload.putString(ImportedStairLegacyArt.KEY,row.get("legacyStairArt").getAsString());
        if(row.has("sourceCarrierContextVersion")){int version=row.get("sourceCarrierContextVersion").getAsInt();if(version!=1)throw new IllegalArgumentException("Unknown source carrier marker version");payload.putInt(ImportedWholeSourceOpticsV127.CONTEXT,version);}
        return payload;
    }
    private static void exportDoorFootprints(JsonObject request,Path output)throws Exception{
        Path source=Path.of(request.get("requestPath").getAsString());String sourceHash=sha(source);
        if(!sourceHash.equals(request.get("sha256").getAsString()))throw new IllegalArgumentException("Door footprint request changed after freeze");
        JsonArray rows=JsonParser.parseString(Files.readString(source)).getAsJsonObject().getAsJsonArray("poses");
        if(!rows.equals(request.getAsJsonArray("poses")))throw new IllegalArgumentException("Door footprint list differs from its frozen source request");
        JsonArray results=new JsonArray();
        for(var raw:rows){JsonObject row=raw.getAsJsonObject();BlockState state=state(row);
            if(!(state.getBlock() instanceof CompositeRootBlock block)||!block.spec.openable)throw new IllegalArgumentException("Requested footprint is not an openable source: "+row);
            NbtCompound payload=payload(row);SortedSet<Cell> union=new TreeSet<>();JsonArray states=new JsonArray();
            for(boolean open:new boolean[]{false,true}){BlockState target=state.with(CompositeRootBlock.OPEN,open);var cells=new TreeSet<>(CompositeSourceShift.footprint(block.spec,target,payload).keySet());union.addAll(cells);JsonArray encoded=new JsonArray();for(var cell:cells)encoded.add(JSON.toJsonTree(new int[]{cell.x(),cell.y(),cell.z()}));JsonObject variant=new JsonObject();variant.addProperty("open",open);variant.add("cells",encoded);states.add(variant);}
            JsonArray cells=new JsonArray();for(var cell:union)cells.add(JSON.toJsonTree(new int[]{cell.x(),cell.y(),cell.z()}));JsonObject result=new JsonObject();result.addProperty("key",row.get("key").getAsString());result.add("cells",cells);result.add("states",states);results.add(result);
        }
        Path production=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths().get(0);
        JsonObject report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("status","PASS");report.addProperty("nativeDescriptor",true);report.addProperty("worldOpened",false);report.addProperty("sourceRequestSha256",sourceHash);report.addProperty("baselineModJarSha256",sha(production));report.add("poses",results);write(output,report);
    }
    private static String sha(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var stream=Files.newInputStream(path)){byte[] chunk=new byte[1<<20];int count;while((count=stream.read(chunk))!=-1)digest.update(chunk,0,count);}return HexFormat.of().formatHex(digest.digest());}
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState state(JsonObject row){String id=row.get("registryId").getAsString();Identifier identifier=new Identifier(id);if(!Registries.BLOCK.containsId(identifier))throw new IllegalArgumentException("Unknown registry "+id);BlockState state=Registries.BLOCK.get(identifier).getDefaultState();if(row.has("properties"))for(var e:row.getAsJsonObject("properties").entrySet()){Property property=state.getBlock().getStateManager().getProperty(e.getKey());if(property==null)throw new IllegalArgumentException("Unknown property "+e);Comparable value=(Comparable)property.parse(e.getValue().getAsString()).orElseThrow();state=state.with(property,value);}return state;}
    private static JsonArray boxes(List<Box> boxes){JsonArray result=new JsonArray();for(var b:boxes)result.add(JSON.toJsonTree(new double[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()}));return result;}
    private static JsonArray mcBoxes(List<net.minecraft.util.math.Box> boxes){JsonArray result=new JsonArray();for(var b:boxes)result.add(JSON.toJsonTree(new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ}));return result;}
    private static Map<Cell,List<Box>> climbing(CompositeRootBlock block,BlockState state,NbtCompound payload){
        if(block.spec.importedClimbingBounds==null)return Map.of();var b=block instanceof ImportedLadderBlock?ImportedLadderBlock.bounds(state):block.spec.importedClimbingBounds;
        var shift=CompositeSourceShift.world(state,payload);var input=new ImportedCollisionMath.Box(b.from().x()/16,b.from().y()/16,b.from().z()/16,b.to().x()/16,b.to().y()/16,b.to().z()/16);Map<Cell,List<Box>> result=new TreeMap<>();
        for(var box:ImportedCollisionMath.transform(List.of(input),state.get(CompositeRootBlock.ROTATION),payload.getDouble("MountY"),false,shift.x(),shift.y(),shift.z()))for(int x=(int)Math.floor(box.x0());x<(int)Math.ceil(box.x1());x++)for(int y=(int)Math.floor(box.y0());y<(int)Math.ceil(box.y1());y++)for(int z=(int)Math.floor(box.z0());z<(int)Math.ceil(box.z1());z++)result.computeIfAbsent(new Cell(x,y,z),k->new ArrayList<>()).add(new Box(Math.max(0,box.x0()-x),Math.max(0,box.y0()-y),Math.max(0,box.z0()-z),Math.min(1,box.x1()-x),Math.min(1,box.y1()-y),Math.min(1,box.z1()-z)));
        return result;
    }
    private static void rotate(MatrixStack matrix,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrix.translate(x,y,z);matrix.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrix.translate(-x,-y,-z);}
    private static int packNormal(Vector3f n){return ((byte)Math.round(n.x*127)&255)|(((byte)Math.round(n.y*127)&255)<<8)|(((byte)Math.round(n.z*127)&255)<<16);}
}
