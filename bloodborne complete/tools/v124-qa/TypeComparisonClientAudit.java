package dev.dreamwalker.bloodbornedw.v124qa;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import org.joml.Vector3f;

/** Actual native baked-source-part comparisons; isolated QA helper, not production. */
public final class TypeComparisonClientAudit implements ClientModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int PAIRS_PER_PAGE=12;
    private static JsonObject baseline, comparison;
    private static Constructor<CompositeSpec> specConstructor;
    private static Field specField;
    private static Method renderParts;
    private static final Map<String,CompositeSpec> OLD_SPECS=new HashMap<>();
    private static CompositeRootBlock BEFORE_CARRIER;
    private final JsonObject result=new JsonObject();
    private final JsonArray captured=new JsonArray();
    private List<Pair> pairs;private Path gameDir;private int page,age,settled,stopAge;
    private boolean initialized,sent,waiting,done;private volatile boolean saved;
    private JsonObject row;

    /** Called by the QA main initializer on CLIENT, before registry freeze. */
    public static void prepareBaselineBlocks(){
        baseline=resource("/v124-baseline-content.json");comparison=resource("/v124-visual-comparison.json");
        try{specConstructor=CompositeSpec.class.getDeclaredConstructor(JsonObject.class);specConstructor.setAccessible(true);specField=CompositeRootBlock.class.getDeclaredField("spec");specField.setAccessible(true);
            renderParts=CompositeClient.class.getDeclaredMethod("renderParts",CompositeRootBlock.class,BlockState.class,MatrixStack.class,VertexConsumerProvider.class,int.class,int.class,String.class,BlockPos.class,NbtCompound.class);renderParts.setAccessible(true);
            Set<String> required=new HashSet<>();for(JsonElement value:comparison.getAsJsonArray("fixtures"))required.add(value.getAsJsonObject().get("oldRegistryId").getAsString());
            for(String oldId:required){if(OLD_SPECS.containsKey(oldId))continue;String path=new Identifier(oldId).getPath();JsonObject descriptor=baseline.getAsJsonObject("descriptors").getAsJsonObject(path);require(descriptor!=null,"Old descriptor absent "+oldId);OLD_SPECS.put(oldId,specConstructor.newInstance(descriptor));}
            // One QA-only carrier avoids ~473*512 unused block states. It is
            // never placed or accessed by another thread. Only its spec field
            // is rebound on the client render thread, then checked by identity.
            CompositeSpec initial=OLD_SPECS.get("bloodborne_dw:source__wall_dec_top_1");require(initial!=null,"Small canonical QA carrier descriptor absent");BEFORE_CARRIER=new CompositeRootBlock(initial);Registry.register(Registries.BLOCK,new Identifier("dreamwalker_v124_qa","before_carrier"),BEFORE_CARRIER);
        }catch(Exception failure){throw new IllegalStateException("Native comparison production reflection failed",failure);}
    }
    @Override public void onInitializeClient(){
        require(baseline!=null&&BEFORE_CARRIER!=null&&!OLD_SPECS.isEmpty(),"QA carrier must be registered by the main initializer before registry freeze");
        ModelLoadingPlugin.register(context->{Set<Identifier> ids=new HashSet<>();collectModels(baseline.getAsJsonObject("descriptors"),ids);context.addModels(ids);});
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private static JsonObject resource(String name){try(var stream=TypeComparisonClientAudit.class.getResourceAsStream(name)){
        require(stream!=null,"Missing native comparison QA resource "+name);
        return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
    }catch(IOException failure){throw new IllegalStateException(failure);}}
    private static void collectModels(JsonElement value,Set<Identifier> ids){
        // Provenance sourceClosedApplications/sourceOpenApplications contain
        // original RP-only minecraft:block/... IDs. Only copied pose parts are
        // native runtime model dependencies.
        for(var descriptor:value.getAsJsonObject().entrySet())for(JsonElement variant:descriptor.getValue().getAsJsonObject().getAsJsonArray("variants"))
            for(var pose:variant.getAsJsonObject().getAsJsonObject("poses").entrySet())
                for(JsonElement part:pose.getValue().getAsJsonObject().getAsJsonArray("parts"))
                    for(String key:List.of("model","altModel"))if(part.getAsJsonObject().has(key))ids.add(new Identifier(part.getAsJsonObject().get(key).getAsString()));
    }
    private void tick(MinecraftClient client){
        if(done){if(++stopAge==20)client.scheduleStop();return;}
        if(client.player==null||client.world==null||client.getServer()==null)return;
        try{
            if(!initialized){
                Path sentinel=client.getServer().getSavePath(WorldSavePath.ROOT).resolve("v124-fixture.json");
                if(!Files.isRegularFile(sentinel))return;
                gameDir=client.runDirectory.toPath();require(!Files.exists(gameDir.resolve("v124-client-comparison-output.json")),"Fresh client evidence required");
                String production=productionSha();JsonObject fixture=JsonParser.parseString(Files.readString(sentinel)).getAsJsonObject();
                if(fixture.has("productionJarSha256"))require(production.equals(fixture.get("productionJarSha256").getAsString()),"World and client production SHA differ");
                if(fixture.has("afterProductionJarSha256"))require(production.equals(fixture.get("afterProductionJarSha256").getAsString()),"Saved migration fixture and client production SHA differ");
                Map<String,JsonObject> savedRows=new HashMap<>();if(fixture.has("fixtures"))for(JsonElement e:fixture.getAsJsonArray("fixtures")){JsonObject saved=e.getAsJsonObject();if(saved.has("oldRegistry")&&saved.has("oldState")){JsonObject properties=saved.getAsJsonObject("oldState");String variant=properties.has("variant")?properties.get("variant").getAsString():"0";savedRows.putIfAbsent(saved.get("oldRegistry").getAsString()+"|"+variant,saved);}}
                pairs=new ArrayList<>();for(JsonElement e:comparison.getAsJsonArray("fixtures")){JsonObject source=e.getAsJsonObject().deepCopy();String key=source.get("oldRegistryId").getAsString()+"|"+source.get("oldVariant").getAsInt();boolean independent=source.has("kind")&&!source.get("kind").getAsString().equals("MIGRATION_ALT_VIEW");if(!independent&&savedRows.containsKey(key))source.add("savedNativeFixture",savedRows.get(key).deepCopy());pairs.add(pair(source));}require(!pairs.isEmpty(),"No type comparison fixtures");
                result.addProperty("schema","dreamwalker-v124-native-visual-comparison-v1");result.addProperty("productionJarSha256",production);
                result.addProperty("baselineCommit",baseline.get("baselineCommit").getAsString());result.addProperty("pairCount",pairs.size());
                result.addProperty("pages",(pairs.size()+PAIRS_PER_PAGE-1)/PAIRS_PER_PAGE);
                result.addProperty("captureMethod","Actual CompositeClient.renderParts using native BakedModelManager, model UVs/textures/cutout and ScreenshotRecorder. Before/after use one common centre, scale, camera and full-bright light. Images are never modified.");
                result.addProperty("physicalMouseKeyboard","NOT_RUN");result.add("frames",captured);initialized=true;write("RUNNING",false);return;
            }
            if(waiting){require(++age<240,"Native screenshot callback timed out");if(!saved)return;
                Path png=gameDir.resolve("screenshots").resolve(row.get("fileName").getAsString());var pixels=ImageIO.read(png.toFile());require(pixels!=null,"Cannot decode native comparison PNG");
                row.addProperty("sha256",sha(png));row.addProperty("bytes",Files.size(png));row.addProperty("width",pixels.getWidth());row.addProperty("height",pixels.getHeight());compareNativePixels(row,pixels);row.addProperty("status","CAPTURED_REQUIRES_VISUAL_REVIEW");captured.add(row);
                page++;waiting=false;sent=false;age=settled=0;done=page*PAIRS_PER_PAGE>=pairs.size();write(done?"CAPTURE_COMPLETE":"RUNNING",done);return;
            }
            if(!sent){client.setScreen(new ComparisonSheet(page,pairs));sent=true;age=settled=0;return;}
            require(++age<300,"Native comparison page never rendered");
            boolean ready=client.currentScreen instanceof ComparisonSheet screen&&screen.rendered;
            settled=ready?settled+1:0;if(settled<8)return;
            ComparisonSheet screen=(ComparisonSheet)client.currentScreen;require(screen.failure==null,"Native comparison renderer failure "+screen.failure);
            row=new JsonObject();row.addProperty("fileName",String.format(Locale.ROOT,"v124-type-comparison-%03d.png",page+1));row.addProperty("page",page+1);row.addProperty("guiWidth",screen.guiWidth());row.addProperty("guiHeight",screen.guiHeight());row.add("pairs",screen.evidence.deepCopy());
            saved=false;waiting=true;age=0;ScreenshotRecorder.saveScreenshot(gameDir.toFile(),row.get("fileName").getAsString(),client.getFramebuffer(),message->client.execute(()->saved=true));
        }catch(Throwable failure){done=true;result.addProperty("failure",failure.toString());failure.printStackTrace();try{if(gameDir!=null)write("FAIL",false);}catch(Exception ignored){}}
    }
    private static String productionSha()throws Exception{
        JsonObject input=resource("/v124-qa-input.json");String expected=input.get("productionJarSha256").getAsString();
        var paths=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
        require(paths.size()==1&&expected.equals(sha(paths.get(0))),"QA production artifact differs");return expected;
    }
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void compareNativePixels(JsonObject frame,java.awt.image.BufferedImage image){
        double sx=(double)image.getWidth()/frame.get("guiWidth").getAsInt(),sy=(double)image.getHeight()/frame.get("guiHeight").getAsInt();int flagged=0;
        for(JsonElement entry:frame.getAsJsonArray("pairs")){JsonObject row=entry.getAsJsonObject();JsonArray left=row.getAsJsonArray("beforeGuiRect"),right=row.getAsJsonArray("afterGuiRect");int x=(int)Math.round(left.get(0).getAsDouble()*sx),X=(int)Math.round(right.get(0).getAsDouble()*sx),y=(int)Math.round(left.get(1).getAsDouble()*sy),w=(int)Math.round(left.get(2).getAsDouble()*sx),h=(int)Math.round(left.get(3).getAsDouble()*sy);long union=0,intersection=0,changed=0;double difference=0;
            for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++){int a=image.getRGB(x+dx,y+dy)&0xffffff,b=image.getRGB(X+dx,y+dy)&0xffffff;boolean ma=a!=0x313a43,mb=b!=0x313a43;if(ma||mb){union++;if(ma&&mb)intersection++;if(a!=b)changed++;for(int channel:new int[]{0,8,16})difference+=Math.abs(((a>>channel)&255)-((b>>channel)&255));}}
            double silhouette=1-(double)intersection/Math.max(1,union),pixel=difference/Math.max(1,union)/3/255;boolean needs=union==0||silhouette>.04||pixel>.05;
            row.addProperty("nativeSilhouetteDifference",silhouette);row.addProperty("nativeRgbDifference",pixel);row.addProperty("nativeChangedVisiblePixelsFraction",(double)changed/Math.max(1,union));row.addProperty("nativeVisiblePixelUnion",union);row.addProperty("nativePixelComparison",needs?"REVIEW_REQUIRED":"WITHIN_GEOMETRY_AND_MATERIAL_TOLERANCE");if(needs)flagged++;
        }frame.addProperty("nativePixelReviewRequiredPairs",flagged);
    }
    private void write(String status,boolean complete)throws Exception{result.addProperty("status",status);result.addProperty("captureComplete",complete);result.addProperty("capturedFrames",captured.size());Files.writeString(gameDir.resolve("v124-client-comparison-output.json"),JSON.toJson(result)+"\n");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private record Rendered(CompositeRootBlock block,CompositeSpec spec,BlockState state,NbtCompound payload,double[] bounds){}
    private record Pair(JsonObject source,Rendered before,Rendered after,double[] bounds){}

    private static Pair pair(JsonObject source)throws Exception{
        String oldId=source.get("oldRegistryId").getAsString(),mainId=source.get("mainRegistryId").getAsString();int oldVariant=source.get("oldVariant").getAsInt();
        CompositeRootBlock beforeBlock=BEFORE_CARRIER;CompositeSpec beforeSpec=OLD_SPECS.get(oldId);
        require(beforeSpec!=null,"Before spec was not prepared "+oldId);
        require(Registries.BLOCK.get(new Identifier(mainId)) instanceof CompositeRootBlock,"Canonical rendered type missing "+mainId);
        CompositeRootBlock afterBlock=(CompositeRootBlock)Registries.BLOCK.get(new Identifier(mainId));
        int oldRotation=source.has("oldRotation")?source.get("oldRotation").getAsInt():0;
        int delta=source.has("yawDeltaSteps")?source.get("yawDeltaSteps").getAsInt():0;
        BlockState beforeState=beforeBlock.getDefaultState().with(CompositeRootBlock.VARIANT,oldVariant).with(CompositeRootBlock.ROTATION,oldRotation);
        BlockState afterState=afterBlock.getDefaultState();
        int logicalVariant=source.get("variant").getAsInt();afterState=afterState.with(CompositeRootBlock.VARIANT,logicalVariant&15).with(CompositeRootBlock.ROTATION,Math.floorMod(oldRotation+delta,8));
        if(logicalVariant>=16){Property<?> bank=afterBlock.getStateManager().getProperties().stream().filter(p->p.getName().contains("bank")).findFirst().orElseThrow(()->new IllegalStateException("Canonical high variant lacks bank "+mainId));afterState=with(afterState,bank,Integer.toString(logicalVariant/16));}
        if(source.has("properties"))for(var e:source.getAsJsonObject("properties").entrySet()){Property<?> p=afterBlock.getStateManager().getProperty(e.getKey());require(p!=null,"After-state property missing "+e.getKey());afterState=with(afterState,p,e.getValue().getAsString());}
        if(source.has("open")){beforeState=beforeState.with(CompositeRootBlock.OPEN,source.get("open").getAsBoolean());afterState=afterState.with(CompositeRootBlock.OPEN,source.get("open").getAsBoolean());}
        if(source.has("profile")){boolean alt=source.get("profile").getAsString().equals("alt");beforeState=beforeState.with(CompositeRootBlock.PROFILE,alt?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE);afterState=afterState.with(CompositeRootBlock.PROFILE,alt?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE);}
        NbtCompound beforePayload=new NbtCompound(),afterPayload=new NbtCompound();
        if(!source.has("kind")||source.get("kind").getAsString().equals("MIGRATION_ALT_VIEW")){
            String legacyArt=ImportedStairLegacyArt.keyFor(oldId,oldVariant);
            if(legacyArt!=null)afterPayload.putString(ImportedStairLegacyArt.KEY,legacyArt);
        }
        if(source.has("sourceShiftDelta")){NbtList shift=new NbtList();for(JsonElement v:source.getAsJsonArray("sourceShiftDelta"))shift.add(NbtDouble.of(v.getAsDouble()));afterPayload.put("SourceShift",shift);}
        if(source.has("savedNativeFixture")){JsonObject saved=source.getAsJsonObject("savedNativeFixture");for(var e:saved.getAsJsonObject("oldState").entrySet()){Property<?> p=beforeBlock.getStateManager().getProperty(e.getKey());if(p!=null)beforeState=with(beforeState,p,e.getValue().getAsString());}JsonObject actualState=saved.getAsJsonObject(saved.has("afterState")?"afterState":"expectedState");for(var e:actualState.entrySet()){Property<?> p=afterBlock.getStateManager().getProperty(e.getKey());require(p!=null,"Saved canonical property absent "+e.getKey());afterState=with(afterState,p,e.getValue().getAsString());}if(saved.has("oldPayloadNbt"))beforePayload=StringNbtReader.parse(saved.get("oldPayloadNbt").getAsString());String actualPayload=saved.has("afterPayloadNbt")?"afterPayloadNbt":"expectedPayloadNbt";if(saved.has(actualPayload))afterPayload=StringNbtReader.parse(saved.get(actualPayload).getAsString());}
        bind(beforeBlock,beforeSpec,beforeState);double[] a=visualBounds(beforeBlock,beforeState,beforePayload);bind(afterBlock,afterBlock.spec,afterState);double[] b=visualBounds(afterBlock,afterState,afterPayload),both=new double[6];
        for(int i=0;i<3;i++){both[i]=Math.min(a[i],b[i]);both[i+3]=Math.max(a[i+3],b[i+3]);}
        return new Pair(source,new Rendered(beforeBlock,beforeSpec,beforeState,beforePayload,a),new Rendered(afterBlock,afterBlock.spec,afterState,afterPayload,b),both);
    }
    private static void bind(CompositeRootBlock block,CompositeSpec spec,BlockState state)throws IllegalAccessException{
        if(block==BEFORE_CARRIER)specField.set(block,spec);else require(block.spec==spec,"Production spec must never be rebound");
        require(block.spec==spec&&block.spec.pose(state)==spec.pose(state),"QA before-carrier spec/pose identity differs");
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState with(BlockState state,Property property,String value){Optional<?> parsed=property.parse(value);require(parsed.isPresent(),"Invalid native QA property "+property.getName()+"="+value);return state.with(property,(Comparable)parsed.get());}
    private static double[] visualBounds(CompositeRootBlock block,BlockState state,NbtCompound payload){
        double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        MinecraftClient client=MinecraftClient.getInstance();var shift=CompositeSourceShift.world(state,payload);int vertexCount=0;
        MatrixStack matrix=new MatrixStack();matrix.translate(shift.x(),shift.y()+mount(payload),shift.z());rotate(matrix,.5,.5,.5,-state.get(CompositeRootBlock.ROTATION)*45,false);
        for(var part:ImportedStairLegacyArt.selectedParts(block,state,payload)){
            matrix.push();matrix.translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);rotate(matrix,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);rotate(matrix,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);rotate(matrix,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
            var model=client.getBakedModelManager().getModel(state.get(CompositeRootBlock.PROFILE)==CompositeRootBlock.Profile.ALT?part.altModel():part.model());require(model!=client.getBakedModelManager().getMissingModel(),"Missing native before/after visual model "+part.model());
            List<net.minecraft.client.render.model.BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,net.minecraft.util.math.random.Random.create(0)));for(Direction direction:Direction.values())quads.addAll(model.getQuads(state,direction,net.minecraft.util.math.random.Random.create(0)));
            for(var quad:quads){int[] data=quad.getVertexData();int stride=data.length/4;require(stride>=3,"Unsupported native baked vertex layout");for(int v=0;v<4;v++){int i=v*stride;Vector3f p=new Vector3f(Float.intBitsToFloat(data[i]),Float.intBitsToFloat(data[i+1]),Float.intBitsToFloat(data[i+2]));matrix.peek().getPositionMatrix().transformPosition(p);double[] coordinates={p.x(),p.y(),p.z()};for(int axis=0;axis<3;axis++){bounds[axis]=Math.min(bounds[axis],coordinates[axis]);bounds[axis+3]=Math.max(bounds[axis+3],coordinates[axis]);}vertexCount++;}}
            matrix.pop();
        }require(vertexCount>0,"Empty actual native before/after geometry "+block.spec.id);return bounds;
    }
    private static void rotate(MatrixStack matrices,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrices.translate(x,y,z);matrices.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrices.translate(-x,-y,-z);}
    private static double mount(NbtCompound payload){return payload.getDouble("MountY")+payload.getDouble("VerticalOffset");}
    private static final class ComparisonSheet extends Screen {
        final int page;final List<Pair> pairs;final JsonArray evidence=new JsonArray();boolean rendered;String failure;
        ComparisonSheet(int page,List<Pair> pairs){super(Text.literal("V12.4 actual native model comparison"));this.page=page;this.pairs=pairs;}
        int guiWidth(){return width;}int guiHeight(){return height;}
        @Override public boolean shouldPause(){return false;}
        @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
            draw.fill(0,0,width,height,0xff202830);draw.drawText(textRenderer,"V12.4 actual models / BEFORE | AFTER / page "+(page+1),8,6,0xffffffff,false);
            try{
                int columns=4,rows=3,slotW=2*((width-12)/columns/2),slotH=(height-28)/rows,start=page*PAIRS_PER_PAGE,end=Math.min(pairs.size(),start+PAIRS_PER_PAGE);
                for(int i=start;i<end;i++){
                    Pair pair=pairs.get(i);int local=i-start,x=6+(local%columns)*slotW,y=22+(local/columns)*slotH;draw.fill(x,y,x+slotW-3,y+slotH-3,0xff313a43);
                    float scale=(float)Math.min((slotW/2.-10)/Math.max(.1,Math.max(pair.bounds[3]-pair.bounds[0],pair.bounds[5]-pair.bounds[2])*1.45),(slotH-24)/Math.max(.1,(pair.bounds[4]-pair.bounds[1])+(pair.bounds[3]-pair.bounds[0]+pair.bounds[5]-pair.bounds[2])*.3));
                    draw.draw();DiffuseLighting.enableGuiDepthLighting();RenderSystem.enableDepthTest();
                    for(int side=0;side<2;side++){Rendered selected=side==0?pair.before:pair.after;bind(selected.block,selected.spec,selected.state);MatrixStack matrix=draw.getMatrices();matrix.push();matrix.translate(x+slotW*(side==0?.25:.75),y+(slotH-16)*.5,250);matrix.scale(scale,-scale,scale);matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25));matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pair.source.has("viewYawDegrees")?pair.source.get("viewYawDegrees").getAsFloat():35));matrix.translate(-(pair.bounds[0]+pair.bounds[3])/2,-(pair.bounds[1]+pair.bounds[4])/2,-(pair.bounds[2]+pair.bounds[5])/2);var shift=CompositeSourceShift.world(selected.state,selected.payload);matrix.translate(shift.x(),shift.y()+mount(selected.payload),shift.z());renderParts.invoke(null,selected.block,selected.state,matrix,draw.getVertexConsumers(),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null,null,selected.payload);require(selected.block.spec==selected.spec,"Spec changed during native render");matrix.pop();draw.draw();}
                    RenderSystem.disableDepthTest();DiffuseLighting.disableGuiDepthLighting();
                    String label=pair.source.get("oldId").getAsString()+":"+pair.source.get("oldVariant").getAsInt()+" > "+pair.source.get("mainId").getAsString()+":"+pair.source.get("variant").getAsInt();draw.drawText(textRenderer,label,x+3,y+slotH-13,0xfff1f2f3,false);
                    if(!rendered){JsonObject row=pair.source.deepCopy();row.addProperty("beforeState",selectedState(pair.before));row.addProperty("afterState",selectedState(pair.after));row.add("beforeNativeBounds",JSON.toJsonTree(pair.before.bounds));row.add("afterNativeBounds",JSON.toJsonTree(pair.after.bounds));row.addProperty("sameCentreScaleView",true);row.addProperty("nativeModelsPresent",true);row.add("beforeGuiRect",JSON.toJsonTree(new double[]{x+2,y+2,slotW/2.-4,slotH-20}));row.add("afterGuiRect",JSON.toJsonTree(new double[]{x+slotW/2.+2,y+2,slotW/2.-4,slotH-20}));evidence.add(row);}
                }rendered=true;
            }catch(Throwable error){failure=error.toString();rendered=true;}
        }
        private static String selectedState(Rendered object)throws IllegalAccessException{bind(object.block,object.spec,object.state);return object.spec.id+" "+object.state.getEntries()+" "+object.payload;}
    }
}
