package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallModels;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** QA-only, marker-gated validation of an ordinary remapped integrated client.
 * Optional diagnostics use ordinary temporary placement/removal and restore all
 * native host cells before save. No screenshots or inferred manual/visual acceptance.
 * MinecraftClient.stop() returns through normal integrated-server save/shutdown.
 */
public final class ReviewClientBootstrap implements ClientModInitializer {
    private static final String GUARD="ISOLATED_SAVED_REVIEW_CLIENT_ONLY";
    private static final Set<String> EXPECTED=Set.of("bloodborne_dw:prototype_double_door","bloodborne_dw:prototype_wood_window",
        "bloodborne_dw:prototype_thin_window","bloodborne_dw:prototype_tree","bloodborne_dw:prototype_roof",
        "bloodborne_dw:prototype_wall","bloodborne_dw:prototype_ladder","bloodborne_dw:prototype_glass_window_02",
        "bloodborne_dw:prototype_wall_skin_0","bloodborne_dw:prototype_wall_skin_1","bloodborne_dw:prototype_wall_skin_2",
        "bloodborne_dw:prototype_wall_skin_3","bloodborne_dw:prototype_wall_skin_4","bloodborne_dw:prototype_wall_skin_5",
        "bloodborne_dw:prototype_wall_skin_7","bloodborne_dw:prototype_ladder_art_1","bloodborne_dw:prototype_ladder_art_2");
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private JsonObject marker,report;
    private Path input,output;
    private boolean done;
    private int ticks,readyTicks;
    private long initialWorldTime=Long.MIN_VALUE;
    private Integer initialShaderFrameCounter;
    private DiagnosticsClientReviewProbe diagnosticsProbe;

    @Override public void onInitializeClient(){
        Path game=FabricLoader.getInstance().getGameDir();input=game.resolve("bloodborne-review-client-input.json");output=game.resolve("client-review-output.json");
        if(!Files.isRegularFile(input))return;
        try{
            byte[] bytes=Files.readAllBytes(input);marker=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!marker.has("guard")||!GUARD.equals(marker.get("guard").getAsString()))return;
            report=new JsonObject();report.addProperty("schema","dreamwalker-isolated-client-resource-check-v1");
            report.addProperty("markerSha256",sha(bytes));report.addProperty("guard",GUARD);
            report.addProperty("productionQaIncluded",false);report.addProperty("visualAcceptance","NOT_RUN");
            report.addProperty("manualGameplay","NOT_RUN");report.addProperty("screenshot","NOT_CAPTURED");
            if(Files.exists(output))throw new IllegalArgumentException("Existing client output; fresh isolated profile required");
            if(marker.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported client marker schema");
            if(!marker.get("worldName").getAsString().equals("prototype-fixture"))throw new IllegalArgumentException("Only copied prototype-fixture is authorized");
            if(!marker.get("productionJarSha256").getAsString().matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact production JAR SHA256 required");
            Set<String> requested=new TreeSet<>();for(JsonElement id:marker.getAsJsonArray("expectedRegistryIds"))requested.add(id.getAsString());
            if(!requested.equals(EXPECTED))throw new IllegalArgumentException("Expected exactly the17 append-only V9 architecture registry types");
            if(marker.has("expectedShaderPack")){
                String name=marker.get("expectedShaderPack").getAsString();
                if(name.length()>128||!name.endsWith(".zip")||name.contains("/")||name.contains("\\"))throw new IllegalArgumentException("Expected shader pack must be a local ZIP filename");
                JsonObject shader=new JsonObject();shader.addProperty("expectedShaderPack",name);shader.addProperty("manualVisualAcceptance","NOT_RUN");report.add("irisRuntime",shader);
                if(marker.has("expectedShaderOptions")&&marker.getAsJsonObject("expectedShaderOptions").size()>128)throw new IllegalArgumentException("Shader option QA budget exceeded");
            }
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        }catch(Exception failure){
            if(report!=null){report.addProperty("status","FAIL_CLIENT_MARKER");report.addProperty("error",failure.toString());write();}
            // Invalid input must never shut down an unrelated client.
        }
    }
    private void tick(MinecraftClient client){
        if(done)return;ticks++;
        int timeout=marker.has("maxClientTicks")?marker.get("maxClientTicks").getAsInt():3600;
        try{
            if(timeout<100||timeout>12000)throw new IllegalArgumentException("Bounded maxClientTicks must be100..12000");
            if(client.world==null||client.player==null||client.getNetworkHandler()==null||client.getServer()==null||client.getOverlay()!=null){
                readyTicks=0;if(ticks>=timeout)throw new IllegalStateException("Timeout awaiting integrated world/player and completed resource overlay");return;
            }
            Path save=client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            if(!save.getFileName().toString().equals(marker.get("worldName").getAsString()))throw new IllegalStateException("Unexpected integrated world directory:"+save);
            if(initialWorldTime==Long.MIN_VALUE){
                initialWorldTime=client.world.getTime();
                if(marker.has("expectedShaderPack"))initialShaderFrameCounter=observeIris("initial",false).get("frameCounter").getAsInt();
            }
            readyTicks++;
            if(readyTicks<40||client.world.getTime()-initialWorldTime<20){if(ticks>=timeout)throw new IllegalStateException("World time did not advance sufficiently");return;}
            if(marker.has("diagnosticsReview")){if(diagnosticsProbe==null){diagnosticsProbe=new DiagnosticsClientReviewProbe(marker.getAsJsonObject("diagnosticsReview"));report.add("diagnosticsActualClient",diagnosticsProbe.report());}if(!diagnosticsProbe.tick(client))return;}
            report.addProperty("clientTicks",ticks);report.addProperty("readyClientTicks",readyTicks);report.addProperty("initialWorldTime",initialWorldTime);report.addProperty("actualWorldTime",client.world.getTime());
            report.addProperty("actualWorldDirectory",save.toString());report.addProperty("actualLevelName",client.getServer().getSaveProperties().getLevelName());
            report.addProperty("dimension",client.world.getRegistryKey().getValue().toString());
            JsonArray player=new JsonArray();player.add(client.player.getX());player.add(client.player.getY());player.add(client.player.getZ());report.add("actualPlayerPos",player);
            verifyJar();
            JsonArray packs=new JsonArray();Set<String> packNames=new TreeSet<>();client.getResourceManager().streamResourcePacks().forEach(pack->{packs.add(pack.getName());packNames.add(pack.getName());});report.add("loadedResourcePacks",packs);
            if(marker.has("expectedResourcePackNames"))for(JsonElement name:marker.getAsJsonArray("expectedResourcePackNames"))
                if(!packNames.contains(name.getAsString()))throw new IllegalStateException("Requested review resource pack is not loaded:"+name.getAsString());
            JsonArray registry=new JsonArray();for(String name:new TreeSet<>(EXPECTED)){
                Identifier id=new Identifier(name);JsonObject row=new JsonObject();row.addProperty("id",name);
                boolean block=Registries.BLOCK.containsId(id),item=Registries.ITEM.containsId(id);row.addProperty("blockRegistered",block);row.addProperty("itemRegistered",item);registry.add(row);
                if(!block||!item)throw new IllegalStateException("Missing staged block/item registry:"+name);
                row.addProperty("stateCount",Registries.BLOCK.get(id).getStateManager().getStates().size());
            }report.add("registryFamilies",registry);
            JsonArray models=new JsonArray();report.add("actualBakedModels",models);Set<Identifier> source=new TreeSet<>(Comparator.comparing(Identifier::toString));
            checkModel(client,"vanilla-stone",Blocks.STONE.getDefaultState(),client.getBlockRenderManager().getModel(Blocks.STONE.getDefaultState()),models,true);
            JsonArray inventory=new JsonArray();report.add("actualInventoryModels",inventory);
            for(String path:List.of("builder_tool","composite_builder","wall_builder")){
                Identifier id=new Identifier("bloodborne_dw",path);
                if(!Registries.ITEM.containsId(id))throw new IllegalStateException("Missing builder item:"+id);
                ItemStack stack=new ItemStack(Registries.ITEM.get(id));
                checkModel(client,"inventory:"+id,null,client.getItemRenderer().getModel(stack,client.world,client.player,0),inventory,true);
            }
            for(String name:new TreeSet<>(EXPECTED)){
                ItemStack stack=new ItemStack(Registries.ITEM.get(new Identifier(name)));BakedModel model=client.getItemRenderer().getModel(stack,client.world,client.player,0);
                // Composite root items are rendered by the registered builtin renderer.
                checkModel(client,"inventory:"+name,null,model,inventory,!model.isBuiltin());
            }
            JsonArray ladderItems=new JsonArray();report.add("ladderInventoryVariantsAndProfiles",ladderItems);
            for(int variant=0;variant<3;variant++)for(var profile:PrototypeLadderBlock.Profile.values()){
                BlockState state=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.VARIANT,variant).with(PrototypeLadderBlock.PROFILE,profile);
                ItemStack stack=PrototypeArchitecture.LADDER.artisticStack(state);
                String expected=variant==0?"bloodborne_dw:prototype_ladder":"bloodborne_dw:prototype_ladder_art_"+variant;
                if(!Registries.ITEM.getId(stack.getItem()).toString().equals(expected))throw new IllegalStateException("Ladder pick did not canonicalize artistic type:"+state);
                BakedModel model=client.getItemRenderer().getModel(stack,client.world,client.player,0);
                checkModel(client,"ladder-inventory-type:"+expected+"/"+profile.asString(),null,model,ladderItems,true);
            }
            for(var block:CompositeArchitecture.blocks())for(var variant:block.spec.variants)for(var pose:List.of(variant.closed(),variant.open()))for(var part:pose.parts()){source.add(part.model());source.add(part.altModel());}
            if(marker.has("expectedModels"))for(JsonElement raw:marker.getAsJsonArray("expectedModels"))source.add(new Identifier(raw.getAsString()));
            if(source.size()>1000)throw new IllegalArgumentException("QA model budget exceeded");
            for(Identifier id:source)checkModel(client,id.toString(),null,client.getBakedModelManager().getModel(id),models,true);
            // The actual registered state models include the source-clone and45-degree wrappers.
            int ladderStates=0;for(var ladder:ladderBlocks())for(BlockState state:ladder.getStateManager().getStates()){
                checkModel(client,"ladder-state:"+state,state,client.getBlockRenderManager().getModel(state),models,true);ladderStates++;
            }
            report.add("wallModelProvider",GSON.toJsonTree(PrototypeWallModels.snapshot()));
            // Uniform and mixed recipes cover every source wall part/profile/yaw.
            int wallModels=0,wallMixedModels=0;
            for(int yaw=0;yaw<8;yaw++)for(int art=0;art<8;art++)for(var course:PrototypeWallBlock.Course.values())for(var profile:PrototypeWallBlock.Profile.values())for(boolean post:List.of(false,true)){
                net.minecraft.block.enums.WallShape shape=course==PrototypeWallBlock.Course.TALL?net.minecraft.block.enums.WallShape.TALL:net.minecraft.block.enums.WallShape.LOW;
                BlockState state=PrototypeWallArchitecture.materialBlock(art).getDefaultState().with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL)
                    .with(PrototypeWallBlock.ROTATION,yaw).with(PrototypeWallBlock.PROFILE,profile)
                    .with(PrototypeWallBlock.POST,post).with(PrototypeWallBlock.NORTH,shape).with(PrototypeWallBlock.EAST,shape).with(PrototypeWallBlock.SOUTH,shape).with(PrototypeWallBlock.WEST,shape);
                checkModel(client,"wall-state:"+state,state,client.getBlockRenderManager().getModel(state),models,true);wallModels++;
                if(course==PrototypeWallBlock.Course.LOW){
                    BlockState mixed=state.with(PrototypeWallBlock.EAST,net.minecraft.block.enums.WallShape.TALL).with(PrototypeWallBlock.SOUTH,net.minecraft.block.enums.WallShape.NONE);
                    checkModel(client,"wall-state-mixed:"+mixed,mixed,client.getBlockRenderManager().getModel(mixed),models,true);wallModels++;wallMixedModels++;
                }
            }
            report.addProperty("compositeSourceModelsChecked",source.size());report.addProperty("ladderStateModelsChecked",ladderStates);
            report.addProperty("wallRepresentativeStateModelsChecked",wallModels);report.addProperty("wallMixedHeightStateModelsChecked",wallMixedModels);report.addProperty("allWallConnectionsEnumerated",false);
            verifyWallProvider(client);
            verifyMountedGlazing(client);
            verifyOrdinaryLadderMount(client);
            verifyCanonicalWallTypes(client);
            verifyLegacyGlazingItems(client);
            if(marker.has("expectedBlocks")){
                JsonArray blocks=new JsonArray();report.add("observedSceneCells",blocks);
                for(JsonElement raw:marker.getAsJsonArray("expectedBlocks")){
                    JsonObject request=raw.getAsJsonObject();JsonArray p=request.getAsJsonArray("pos");BlockPos pos=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                    if(!client.world.isChunkLoaded(pos))throw new IllegalStateException("Requested scene chunk not loaded:"+pos);
                    BlockState state=client.world.getBlockState(pos);JsonObject row=request.deepCopy();row.addProperty("actualState",state.toString());blocks.add(row);
                    if(!Registries.BLOCK.getId(state.getBlock()).toString().equals(request.get("id").getAsString()))throw new IllegalStateException("Requested scene cell mismatch:"+pos+" "+state);
                    if(state.getBlock() instanceof ThinWindowRootBlock)verifyObservedGlazing(client,pos,state,row);
                }
            }
            report.add("rpV9ActualClient",RpV9ClientReviewProbe.verify(client));
            if(marker.has("expectedShaderPack"))observeIris("final",true);
            finish(client,"PASS_CLIENT_WORLD_RESOURCES_NORMAL_STOP_REQUESTED",null);
        }catch(Exception failure){finish(client,"FAIL_CLIENT_WORLD_RESOURCES",failure);}
    }
    /** Optional public-API reflection keeps Iris absent from QA compile/runtime dependencies. */
    private JsonObject observeIris(String phase,boolean verify)throws Exception{
        JsonObject shader=report.getAsJsonObject("irisRuntime"),row=new JsonObject();shader.add(phase,row);
        Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
        Object config=iris.getMethod("getIrisConfig").invoke(null);
        Optional<?> pack=(Optional<?>)iris.getMethod("getCurrentPack").invoke(null);
        String name=(String)iris.getMethod("getCurrentPackName").invoke(null);
        boolean enabled=(boolean)config.getClass().getMethod("areShadersEnabled").invoke(config);
        boolean fallback=(boolean)iris.getMethod("isFallback").invoke(null);
        Object manager=iris.getMethod("getPipelineManager").invoke(null);
        Object pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
        String pipelineClass=pipeline==null?"null":pipeline.getClass().getName();
        Object counter=Class.forName("net.irisshaders.iris.uniforms.SystemTimeUniforms").getField("COUNTER").get(null);
        int frames=((java.util.function.IntSupplier)counter).getAsInt();
        row.addProperty("currentPackName",name);row.addProperty("packPresent",pack.isPresent());row.addProperty("shadersEnabled",enabled);
        row.addProperty("fallback",fallback);row.addProperty("pipelineClass",pipelineClass);row.addProperty("frameCounter",frames);
        boolean active=pipelineClass.equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
        if(active){
            row.addProperty("shaderMapPresent",pipeline.getClass().getMethod("getShaderMap").invoke(pipeline)!=null);
            // This flag is render-phase dependent; at END_CLIENT_TICK false is valid.
            row.addProperty("shouldOverrideShadersAtTick",(boolean)pipeline.getClass().getMethod("shouldOverrideShaders").invoke(pipeline));
            row.addProperty("skipAllRendering",(boolean)pipeline.getClass().getMethod("skipAllRendering").invoke(pipeline));
        }
        if(!verify)return row;
        shader.addProperty("status","FAIL_ACTIVE_IRIS_RENDERING_PIPELINE");
        if(!marker.get("expectedShaderPack").getAsString().equals(name)||!pack.isPresent()||!enabled||fallback||!active
            ||!row.get("shaderMapPresent").getAsBoolean()||row.get("skipAllRendering").getAsBoolean())
            throw new IllegalStateException("Requested shader did not produce an active Iris rendering pipeline:"+row);
        if(initialShaderFrameCounter==null||frames<=initialShaderFrameCounter)throw new IllegalStateException("Iris frame counter did not advance:"+initialShaderFrameCounter+" -> "+frames);
        shader.addProperty("frameCounterAdvanced",true);
        if(marker.has("expectedShaderOptions")){
            Object options=pack.orElseThrow().getClass().getMethod("getShaderPackOptions").invoke(pack.orElseThrow());
            Object values=options.getClass().getMethod("getOptionValues").invoke(options);
            Class<?> api=Class.forName("net.irisshaders.iris.shaderpack.option.values.OptionValues");
            Object set=api.getMethod("getOptionSet").invoke(values);
            Map<?,?> bools=(Map<?,?>)set.getClass().getMethod("getBooleanOptions").invoke(set);
            Map<?,?> strings=(Map<?,?>)set.getClass().getMethod("getStringOptions").invoke(set);
            JsonArray checks=new JsonArray();shader.add("effectiveOptions",checks);
            for(var option:marker.getAsJsonObject("expectedShaderOptions").entrySet()){
                String key=option.getKey(),expected=option.getValue().getAsString(),actual=null;
                if(!key.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("Invalid expected shader option key:"+key);
                if(bools.containsKey(key))actual=String.valueOf(api.getMethod("getBooleanValueOrDefault",String.class).invoke(values,key));
                else if(strings.containsKey(key))actual=(String)api.getMethod("getStringValueOrDefault",String.class).invoke(values,key);
                JsonObject check=new JsonObject();check.addProperty("key",key);check.addProperty("expected",expected);check.addProperty("actual",actual);
                boolean match=sameShaderOption(expected,actual);check.addProperty("matches",match);checks.add(check);
                if(!match)throw new IllegalStateException("Shader option not applied:"+key+" expected="+expected+" actual="+actual);
            }
        }
        shader.addProperty("status","PASS_ACTIVE_IRIS_RENDERING_PIPELINE");
        return row;
    }
    private static boolean sameShaderOption(String expected,String actual){
        if(Objects.equals(expected,actual))return true;if(actual==null)return false;
        try{return new BigDecimal(expected).compareTo(new BigDecimal(actual))==0;}catch(NumberFormatException ignored){return false;}
    }
    private void verifyJar()throws Exception{
        JsonArray origins=new JsonArray();boolean match=false;
        for(Path path:FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths()){
            JsonObject row=new JsonObject();row.addProperty("path",path.toString());if(Files.isRegularFile(path)){String hash=sha(Files.readAllBytes(path));row.addProperty("sha256",hash);match|=hash.equals(marker.get("productionJarSha256").getAsString());}origins.add(row);
        }report.add("actualProductionOrigins",origins);if(!match)throw new IllegalStateException("Loaded production JAR does not match exact marker hash");
    }
    private void verifyWallProvider(MinecraftClient client){
        Map<String,Integer> counts=PrototypeWallModels.snapshot();report.add("wallModelProvider",GSON.toJsonTree(counts));
        if(counts.getOrDefault("stateBindings",0)!=155520||counts.getOrDefault("visualKeys",0)!=17152
            ||counts.getOrDefault("primitiveResourceIds",0)!=40||counts.getOrDefault("primitiveBakedModels",0)!=160
            ||counts.getOrDefault("appearanceBakedModels",0)!=17152||counts.getOrDefault("clonedBakedQuads",-1)!=0
            ||counts.getOrDefault("parentsLinked",0)!=1||counts.getOrDefault("primitiveModelsWithGeometry",0)!=160)
            throw new IllegalStateException("Unexpected bounded wall provider counts:"+counts);
        BlockState base=PrototypeWallArchitecture.WALL.getDefaultState().with(PrototypeWallBlock.NORTH,net.minecraft.block.enums.WallShape.TALL).with(PrototypeWallBlock.EAST,net.minecraft.block.enums.WallShape.LOW);
        Map<String,BlockState> changes=new LinkedHashMap<>();changes.put("independent_material_type",PrototypeWallArchitecture.changeMaterial(base,1));
        changes.put("profile",base.with(PrototypeWallBlock.PROFILE,PrototypeWallBlock.Profile.ALT));
        changes.put("yaw",base.with(PrototypeWallBlock.ROTATION,1));changes.put("north_height",base.with(PrototypeWallBlock.NORTH,net.minecraft.block.enums.WallShape.LOW));
        changes.put("east_height",base.with(PrototypeWallBlock.EAST,net.minecraft.block.enums.WallShape.TALL));
        changes.put("arms",base.with(PrototypeWallBlock.NORTH,net.minecraft.block.enums.WallShape.NONE).with(PrototypeWallBlock.WEST,net.minecraft.block.enums.WallShape.LOW));changes.put("post",base.cycle(PrototypeWallBlock.POST));
        JsonArray rows=new JsonArray();report.add("wallNativeRerenderChecks",rows);
        for(var change:changes.entrySet()){
            BakedModel before=client.getBlockRenderManager().getModel(base),after=client.getBlockRenderManager().getModel(change.getValue());
            boolean rerender=client.getBakedModelManager().shouldRerender(base,change.getValue());JsonObject row=new JsonObject();
            row.addProperty("change",change.getKey());row.addProperty("nativeShouldRerender",rerender);row.addProperty("distinctAppearanceWrapper",before!=after);
            row.addProperty("particleBefore",before.getParticleSprite().getContents().getId().toString());row.addProperty("particleAfter",after.getParticleSprite().getContents().getId().toString());rows.add(row);
            if(!rerender||before==after)throw new IllegalStateException("Wall appearance change would not update:"+change.getKey());
        }
    }
    private static void checkModel(MinecraftClient client,String name,BlockState state,BakedModel model,JsonArray rows,boolean geometry)throws Exception{
        JsonObject row=new JsonObject();row.addProperty("model",name);row.addProperty("class",model.getClass().getName());
        boolean missing=model==client.getBakedModelManager().getMissingModel();row.addProperty("missingModel",missing);
        Set<String> textures=new TreeSet<>();int quads=0;MessageDigest vertexHash=MessageDigest.getInstance("SHA-256");
        // Native face buckets0..5 then general(-1); each bucket starts with its
        // index/count. Quad metadata and raw vertex ints use big-endian int32,
        // sprite ID uses length-prefixed UTF8. Returned native order is retained.
        // State labels, class names and Java identity never enter the fingerprint.
        for(Direction face:Arrays.copyOf(Direction.values(),7)){
            List<BakedQuad> bucket=model.getQuads(state,face,Random.create(0));hashInt(vertexHash,face==null?-1:face.ordinal());hashInt(vertexHash,bucket.size());
            for(BakedQuad quad:bucket){
                quads++;String texture=quad.getSprite().getContents().getId().toString();textures.add(texture);
                hashInt(vertexHash,quad.getFace().ordinal());hashInt(vertexHash,quad.getColorIndex());hashInt(vertexHash,quad.hasShade()?1:0);
                byte[] sprite=texture.getBytes(StandardCharsets.UTF_8);hashInt(vertexHash,sprite.length);vertexHash.update(sprite);
                int[] vertices=quad.getVertexData();hashInt(vertexHash,vertices.length);for(int value:vertices)hashInt(vertexHash,value);
            }
        }
        JsonArray atlas=new JsonArray();textures.forEach(atlas::add);row.add("quadSpriteIds",atlas);row.addProperty("quadCount",quads);
        row.addProperty("quadVertexSha256",HexFormat.of().formatHex(vertexHash.digest()));rows.add(row);
        if(missing||textures.contains(MissingSprite.getMissingSpriteId().toString())||geometry&&quads==0)throw new IllegalStateException("Unresolved or empty authored model:"+name+" quads="+quads+" textures="+textures);
        if(name.equals("vanilla-stone")&&!textures.contains("minecraft:block/stone"))throw new IllegalStateException("Vanilla block atlas stone sprite was not retained:"+textures);
    }
    private void verifyMountedGlazing(MinecraftClient client)throws Exception{
        JsonArray rows=new JsonArray();report.add("glazingActualBakedMountBounds",rows);
        for(String path:List.of(GlazingTypes.WINDOW01,GlazingTypes.WINDOW02)){
        var block=CompositeArchitecture.kindBlock(path);
        for(var profile:CompositeRootBlock.Profile.values())for(int yaw:new int[]{0,2,4,6})for(var mode:ThinWindowRootBlock.Mount.values()){
            BlockState state=block.getDefaultState().with(CompositeRootBlock.VARIANT,0).with(CompositeRootBlock.ROTATION,yaw).with(CompositeRootBlock.PROFILE,profile).with(ThinWindowRootBlock.MOUNT,mode);
            var part=block.spec.pose(state).parts().get(0);Identifier selected=profile==CompositeRootBlock.Profile.ALT?part.altModel():part.model();BakedModel model=client.getBakedModelManager().getModel(selected);
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};int vertices=0;
            for(Direction face:Arrays.copyOf(Direction.values(),7))for(BakedQuad quad:model.getQuads(null,face,Random.create(0))){int[] data=quad.getVertexData();int stride=data.length/4;
                for(int v=0;v<4;v++){int n=v*stride;double[] p={Float.intBitsToFloat(data[n]),Float.intBitsToFloat(data[n+1]),Float.intBitsToFloat(data[n+2])};
                    p=rotatePoint(p,-part.yaw(),false);for(int i=0;i<3;i++)p[i]+=i==0?part.offset().x()/16:i==1?part.offset().y()/16:part.offset().z()/16;
                    if(mode!=ThinWindowRootBlock.Mount.VERTICAL){p=rotatePoint(p,block.spec.variant(state).mountAlignmentYaw(),false);p=rotatePoint(p,mode==ThinWindowRootBlock.Mount.FLOOR?90:-90,true);}
                    for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],p[i]);hi[i]=Math.max(hi[i],p[i]);}vertices++;
                }
            }
            var b=block.spec.mountedBounds(state);double[] expectedLo={b.from().x()/16,b.from().y()/16,b.from().z()/16},expectedHi={b.to().x()/16,b.to().y()/16,b.to().z()/16};
            for(int i=0;i<3;i++)if(Math.abs(lo[i]-expectedLo[i])>1e-5||Math.abs(hi[i]-expectedHi[i])>1e-5)throw new IllegalStateException("Actual baked glazing mount bounds mismatch type="+path+" mode="+mode+" actual="+Arrays.toString(lo)+"/"+Arrays.toString(hi));
            JsonObject row=new JsonObject();row.addProperty("type",path);row.addProperty("variant",0);row.addProperty("rotation",yaw);row.addProperty("profile",profile.asString());row.addProperty("mode",mode.asString());row.addProperty("actualBakedVertices",vertices);row.add("actualMinBeforeGlobalYaw",GSON.toJsonTree(lo));row.add("actualMaxBeforeGlobalYaw",GSON.toJsonTree(hi));row.addProperty("matchesExactMountMetadata",true);
            checkGlazingFaces(client,state,model,row);rows.add(row);
        }}
        report.addProperty("ordinaryGlazingTypeCount",2);report.addProperty("ordinaryGlazingGlobalYawCount",4);
        report.addProperty("ordinaryGlazingMountCount",3);report.addProperty("ordinaryGlazingModelMountPoseChecks",rows.size());
    }
    private static double[] rotatePoint(double[] point,double degrees,boolean pitch){double c=Math.cos(Math.toRadians(degrees)),s=Math.sin(Math.toRadians(degrees)),x=point[0]-.5,y=point[1]-.5,z=point[2]-.5;return pitch?new double[]{x+.5,y*c-z*s+.5,y*s+z*c+.5}:new double[]{x*c+z*s+.5,y+.5,z*c-x*s+.5};}
    private void verifyOrdinaryLadderMount(MinecraftClient client)throws Exception{
        JsonArray pairs=new JsonArray();report.add("ordinaryAndSourceLadderBakedEquality",pairs);
        for(var ladder:ladderBlocks())for(BlockState state:ladder.getStateManager().getStates())if(!state.get(PrototypeLadderBlock.SOURCE_CLONE)){
            BlockState clone=state.with(PrototypeLadderBlock.SOURCE_CLONE,true);JsonArray rows=new JsonArray();
            checkModel(client,"ordinary",state,client.getBlockRenderManager().getModel(state),rows,true);checkModel(client,"source",clone,client.getBlockRenderManager().getModel(clone),rows,true);
            String ordinary=rows.get(0).getAsJsonObject().get("quadVertexSha256").getAsString(),source=rows.get(1).getAsJsonObject().get("quadVertexSha256").getAsString();
            if(!ordinary.equals(source))throw new IllegalStateException("Ordinary ladder does not use proven source-pair mount:"+state);
            JsonObject row=new JsonObject();row.addProperty("state",state.toString());row.addProperty("sameOriginalArtworkAndMount",true);row.addProperty("quadVertexSha256",ordinary);pairs.add(row);
        }
    }
    private static List<PrototypeLadderBlock> ladderBlocks(){return List.copyOf(PrototypeArchitecture.LADDERS);}
    private void verifyCanonicalWallTypes(MinecraftClient client)throws Exception{
        JsonArray rows=new JsonArray();report.add("wallLegacyToIndependentTypeBakedEquality",rows);
        for(int art=0;art<8;art++)for(var profile:PrototypeWallBlock.Profile.values())for(int yaw=0;yaw<8; yaw++){
            BlockState legacy=PrototypeWallArchitecture.WALL.getDefaultState().with(PrototypeWallBlock.MATERIAL,art).with(PrototypeWallBlock.PROFILE,profile).with(PrototypeWallBlock.ROTATION,yaw).with(PrototypeWallBlock.NORTH,net.minecraft.block.enums.WallShape.TALL).with(PrototypeWallBlock.EAST,net.minecraft.block.enums.WallShape.LOW);
            BlockState canonical=PrototypeWallArchitecture.changeMaterial(legacy,art);JsonArray pair=new JsonArray();
            checkModel(client,"legacy",legacy,client.getBlockRenderManager().getModel(legacy),pair,true);checkModel(client,"canonical",canonical,client.getBlockRenderManager().getModel(canonical),pair,true);
            String before=pair.get(0).getAsJsonObject().get("quadVertexSha256").getAsString(),after=pair.get(1).getAsJsonObject().get("quadVertexSha256").getAsString();
            if(!before.equals(after))throw new IllegalStateException("Wall independent type altered legacy art:"+art+"/"+profile+"/"+yaw);
            ItemStack picked=((PrototypeWallBlock)legacy.getBlock()).artisticStack(legacy);String id=Registries.ITEM.getId(picked.getItem()).toString();
            if(!id.equals(Registries.BLOCK.getId(canonical.getBlock()).toString()))throw new IllegalStateException("Legacy wall pick returned old variant identity:"+legacy);
            var debug=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(legacy);var newDebug=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(canonical);
            if(debug==null||newDebug==null||!debug.temporaryId().equals(newDebug.temporaryId()))throw new IllegalStateException("Wall logical TEMP alias differs:"+legacy);
            JsonObject row=new JsonObject();row.addProperty("sourceMaterial",art);row.addProperty("canonicalType",id);row.addProperty("tempNumber",debug.temporaryId());row.addProperty("profile",profile.asString());row.addProperty("rotation",yaw);row.addProperty("sameBakedVertexHash",true);row.addProperty("quadVertexSha256",after);rows.add(row);
        }
    }
    private void verifyLegacyGlazingItems(MinecraftClient client)throws Exception{
        JsonArray rows=new JsonArray();report.add("legacyGlazingCanonicalInventoryArt",rows);
        var old=CompositeArchitecture.kindBlock(GlazingTypes.WINDOW01);
        for(int variant=0;variant<3;variant++)for(var profile:CompositeRootBlock.Profile.values()){
            BlockState legacy=old.getDefaultState().with(CompositeRootBlock.VARIANT,variant).with(CompositeRootBlock.ROTATION,1).with(CompositeRootBlock.PROFILE,profile);
            ItemStack picked=old.art(legacy,new net.minecraft.nbt.NbtCompound());CompositeRootBlock target=GlazingTypes.itemTarget(legacy);BlockState inventoryState=GlazingTypes.itemState(target,picked);
            if(inventoryState.get(CompositeRootBlock.VARIANT)!=0||inventoryState.get(CompositeRootBlock.ROTATION)!=0||picked.getSubNbt("CompositePayload")!=null||picked.getItem()!=target.asItem())throw new IllegalStateException("Legacy glazing pick retained angular or instance payload:"+legacy);
            var part=target.spec.pose(inventoryState).parts().get(0);Identifier selected=profile==CompositeRootBlock.Profile.ALT?part.altModel():part.model();JsonObject row=new JsonObject();
            row.addProperty("oldVariant",variant);row.addProperty("canonicalType",target.spec.id.toString());row.addProperty("tempNumber",dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(legacy).temporaryId());row.addProperty("profile",profile.asString());row.addProperty("ordinaryVariant",0);row.addProperty("ordinaryInventoryYaw",0);row.addProperty("legacyAngularPoseDiscarded",true);
            checkGlazingFaces(client,inventoryState,client.getBakedModelManager().getModel(selected),row);
            var legacyPart=old.spec.pose(legacy).parts().get(0);Identifier legacySelected=profile==CompositeRootBlock.Profile.ALT?legacyPart.altModel():legacyPart.model();JsonArray original=new JsonArray();checkModel(client,legacySelected.toString(),null,client.getBakedModelManager().getModel(legacySelected),original,true);
            row.add("installedLegacySourceModel",original.get(0));row.addProperty("installedLegacyStateUnmodified",legacy.get(CompositeRootBlock.VARIANT)==variant&&legacy.get(CompositeRootBlock.ROTATION)==1);rows.add(row);
        }
    }
    private void verifyObservedGlazing(MinecraftClient client,BlockPos pos,BlockState state,JsonObject row)throws Exception{
        if(state.get(CompositeRootBlock.VARIANT)!=0||(state.get(CompositeRootBlock.ROTATION)&1)!=0)throw new IllegalStateException("Ordinary saved scene contains legacy-only glazing pose:"+pos+" "+state);
        CompositeRootBlock block=(CompositeRootBlock)state.getBlock();ItemStack picked=block.art(state,new net.minecraft.nbt.NbtCompound());BlockState itemState=GlazingTypes.itemState(block,picked);
        if(picked.getItem()!=block.asItem()||itemState.get(CompositeRootBlock.VARIANT)!=0||itemState.get(CompositeRootBlock.PROFILE)!=state.get(CompositeRootBlock.PROFILE))throw new IllegalStateException("Observed server glazing and canonical inventory disagree:"+pos);
        var part=block.spec.pose(state).parts().get(0);Identifier selected=state.get(CompositeRootBlock.PROFILE)==CompositeRootBlock.Profile.ALT?part.altModel():part.model();
        row.addProperty("canonicalPickedItem",Registries.ITEM.getId(picked.getItem()).toString());row.addProperty("tempNumber",dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(state).temporaryId());row.addProperty("mount",ThinWindowRootBlock.mount(state).asString());row.addProperty("cardinalOrdinaryPose",true);row.addProperty("actualSelectedSourceModel",selected.toString());
        checkGlazingFaces(client,state,client.getBakedModelManager().getModel(selected),row);
    }
    private static void checkGlazingFaces(MinecraftClient client,BlockState state,BakedModel model,JsonObject row)throws Exception{
        Set<Direction> directions=new HashSet<>();for(Direction face:Arrays.copyOf(Direction.values(),7))for(BakedQuad quad:model.getQuads(null,face,Random.create(0)))directions.add(quad.getFace());
        boolean opposite=directions.stream().anyMatch(face->directions.contains(face.getOpposite()));if(!opposite)throw new IllegalStateException("Glazing artwork lacks two opposing authored faces:"+state);
        JsonArray nativeModel=new JsonArray();checkModel(client,"glazing-art:"+state,null,model,nativeModel,true);row.add("actualSourceModel",nativeModel.get(0));row.addProperty("twoOpposingAuthoredFaces",true);
    }
    private static void hashInt(MessageDigest hash,int value){hash.update((byte)(value>>>24));hash.update((byte)(value>>>16));hash.update((byte)(value>>>8));hash.update((byte)value);}
    private void finish(MinecraftClient client,String status,Exception failure){
        done=true;report.addProperty("status",status);if(failure!=null)report.addProperty("error",failure.toString());
        report.addProperty("normalStopRequested",true);report.addProperty("saveExitCompletion","EXTERNAL_PROCESS_EXIT_AND_SAVED_WORLD_CHECK_REQUIRED");write();client.stop();
    }
    private void write(){try{Files.writeString(output,GSON.toJson(report)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(Exception failure){throw new IllegalStateException("Cannot persist isolated client QA report",failure);}}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
