package dev.dreamwalker.bloodbornedw.v127qa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.tool.BbAlternateService;
import dev.dreamwalker.bloodbornedw.tool.ToolCommands;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;

/** SHA-bound real client interaction packets + framebuffer frames. Physical
 * keyboard/mouse input is explicitly NOT_RUN. Never included in production. */
public final class NativeV127ClientQa implements ClientModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject report=new JsonObject();private final JsonArray operations=new JsonArray(),frames=new JsonArray();
    private final List<Case> cases=new ArrayList<>();private final List<Frame> capture=new ArrayList<>();
    private record Case(String name,BlockPos root,ItemStack stack,boolean rp) {}
    private record Frame(String name,int x,String held,String profile,boolean night) {}
    private Path dir,output;private String phase="BOOT",next,bound;private int age,index,settled,frameIndex;private boolean done,saved,waiting,rendered,reopenOnly;private double[] camera;private JsonObject previous;
    private final AtomicReference<BbAlternateService.Status> serverStatus=new AtomicReference<>();
    private final AtomicReference<JsonObject> persisted=new AtomicReference<>();
    private final AtomicReference<Throwable> serverFailure=new AtomicReference<>();
    @Override public void onInitializeClient(){
        try{var client=MinecraftClient.getInstance();dir=client.runDirectory.toPath().toAbsolutePath().normalize();Path marker=dir.resolve("client-capture-input.json");if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))return;var input=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();if(!input.has("v127NativeFunctions")||!input.get("v127NativeFunctions").getAsBoolean())return;require(input.get("freshTestWorldOnly").getAsBoolean(),"Explicit isolated test marker required");
            var paths=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();require(paths.size()==1,"One production artifact required");bound=sha(paths.get(0));require(bound.equals(input.get("productionJarSha256").getAsString()),"Production marker SHA mismatch");output=dir.resolve("v127-native-client-qa.json");require(!Files.exists(output),"Fresh client evidence directory required");
            reopenOnly=input.has("v127ReopenOnly")&&input.get("v127ReopenOnly").getAsBoolean();if(reopenOnly){Path old=Path.of(input.get("previousEvidenceFile").getAsString()).toAbsolutePath().normalize();require(old.toString().replace('\\','/').contains("/build/"),"Reopen evidence is restricted to isolated project-build QA");previous=JsonParser.parseString(Files.readString(old)).getAsJsonObject();require(previous.has("status")&&Set.of("PASS_ENGINE_CAPTURE","PASS_ENGINE_REOPEN").contains(previous.get("status").getAsString())&&previous.get("productionJarSha256").getAsString().equals(bound)&&previous.has("persistedState"),"Reopen must use successful prior state evidence bound to this production JAR");require(sha(old).equals(input.get("previousEvidenceSha256").getAsString()),"Prior state evidence SHA mismatch");}
            report.addProperty("schema","dreamwalker-v127-native-functions-v1");report.addProperty("productionJarSha256",bound);report.addProperty("reopenOnly",reopenOnly);report.addProperty("physicalMouseKeyboardInput","NOT_RUN");report.addProperty("method","Actual client interaction manager/creative inventory packets, production server acknowledgement, actual ScreenshotRecorder framebuffer; isolated function fixture; no gallery");report.add("operations",operations);report.add("frames",frames);write("WAITING_FOR_WORLD");
            WorldRenderEvents.END.register(context->{if(!done&&phase.equals("FRAME_WAIT")&&atCamera(client))rendered=true;});ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        }catch(Throwable failure){fail(failure);}
    }
    private void tick(MinecraftClient client){if(done)return;
        try{
            if(phase.equals("QUIT")){if(++age==30)client.scheduleStop();return;}
            if(client.player==null||client.world==null||client.currentScreen!=null||client.isPaused())return;
            require(++age<16000,"Native QA phase timeout: "+phase);if(age%80==0)write("RUNNING");
            switch(phase){
                case "BOOT"->{if(age<40)return;require(client.getServer()!=null,"Integrated isolated server required for this native function fixture");memory(client,reopenOnly?"after_saved_world_reopen":"after_open_before_hand_placement");prepareCases(client);index=0;if(reopenOnly){prepareFrames(client);capture.removeIf(frame->frame.name.startsWith("v127-base-"));report.addProperty("expectedFrames",capture.size());frameIndex=0;stage("FRAME_START");}else navigate(client,cases.get(0).root,"EQUIP_PLACE");}
                case "NAVIGATE"->{settled=atCamera(client)?settled+1:0;if(settled>=12)stage(next);}
                case "EQUIP_PLACE"->{equip(client,cases.get(index).stack);stage("PLACE_PACKET");}
                case "PLACE_PACKET"->{if(age<8)return;var current=cases.get(index);var floor=new BlockHitResult(Vec3d.ofCenter(current.root.down()).add(0,.5,0),Direction.UP,current.root.down(),false);require(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,floor).isAccepted(),"Client hand placement accepted locally: "+current.name);record(client,"actual_interactBlock_place",current.name,current.root);stage("WAIT_PLACE_ACK");}
                case "WAIT_PLACE_ACK"->{var current=cases.get(index);if(!placed(client,current))return;record(client,"server_synced_hand_placement",current.name,current.root);if(++index<cases.size())navigate(client,cases.get(index).root,"EQUIP_PLACE");else{persisted.set(null);requestState(client,true);stage("WAIT_COMMAND_FIXTURE");}}
                case "WAIT_COMMAND_FIXTURE"->{checkServerFailure();if(persisted.get()==null)return;report.add("savedCommandFixtures",persisted.get());prepareFrames(client);frameIndex=0;stage("FRAME_START");}
                case "FRAME_START"->{var frame=capture.get(frameIndex);if(!reopenOnly&&frame.name.equals("v127-alt-wall")&&age==1){memory(client,"before_alt_all");client.player.networkHandler.sendChatCommand("bb alt all");record(client,"actual_bb_alt_all_command","all",new BlockPos(0,64,0));serverStatus.set(null);stage("WAIT_ALL");return;}startFrame(client,frame);}
                case "WAIT_ALL"->{if(age%20==0){var server=client.getServer();server.execute(()->serverStatus.set(BbAlternateService.status(server)));}var status=serverStatus.get();if(status==null||status.running()||status.changed()==0)return;require(status.failed()==0,"Actual all command has no rejected edits: "+status);report.add("actualAllJob",JSON.toJsonTree(status));memory(client,"after_alt_all_complete");startFrame(client,capture.get(frameIndex));}
                case "FRAME_EQUIP"->{var frame=capture.get(frameIndex);equip(client,held(frame.held));if(frame.night)client.player.networkHandler.sendChatCommand("time set midnight");stage("FRAME_WAIT");rendered=false;}
                case "FRAME_WAIT"->{if(age<50||!rendered||!atCamera(client))return;var frame=capture.get(frameIndex);requireProfile(client,frame);if(frame.x==50)verifyAuthoredGrassProviders(client);if(frame.name.contains("light"))require(client.world.getLightLevel(LightType.BLOCK,new BlockPos(38,64,0))==14,"Invisible DW light has actual native block light14");var row=new JsonObject();row.addProperty("fileName",frame.name+".png");row.addProperty("name",frame.name);row.add("actualCamera",JSON.toJsonTree(List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch())));row.addProperty("heldItem",Registries.ITEM.getId(client.player.getMainHandStack().getItem()).toString());row.addProperty("worldTick",client.world.getTime());row.addProperty("status","CAPTURE_PENDING");frames.add(row);saved=false;waiting=true;ScreenshotRecorder.saveScreenshot(dir.toFile(),frame.name+".png",client.getFramebuffer(),message->client.execute(()->saved=true));stage("WAIT_SCREENSHOT");}
                case "WAIT_SCREENSHOT"->{if(!saved)return;var row=frames.get(frames.size()-1).getAsJsonObject();var file=dir.resolve("screenshots").resolve(row.get("fileName").getAsString());require(Files.isRegularFile(file)&&Files.size(file)>1000,"Actual framebuffer PNG saved");row.addProperty("sha256",sha(file));row.addProperty("bytes",Files.size(file));row.addProperty("status","PASS_CAPTURE_REQUIRES_PIXEL_REVIEW");waiting=false;write("RUNNING");if(++frameIndex<capture.size()){stage("FRAME_START");}else{persisted.set(null);requestState(client,false);stage("WAIT_SAVED_STATE");}}
                case "WAIT_SAVED_STATE"->{checkServerFailure();var state=persisted.get();if(state==null)return;report.add("persistedState",state);if(reopenOnly)require(previous.getAsJsonObject("persistedState").equals(state),"Normal saved-world reopen preserves every native/RP UUID, registry/profile/state and exact typed command counters");memory(client,reopenOnly?"after_reopened_frames":"before_normal_save_shutdown");report.addProperty("clientOpenAndSyncedOperations","PASS");report.addProperty("nativeFramebufferCapture","PASS");report.addProperty("pixelReview","NOT_RUN");report.addProperty("normalSaveAndReopen",reopenOnly?"PASS_ACTUAL_ENGINE_STATE_REOPEN":"VERIFY_AFTER_CLIENT_SHUTDOWN");write(reopenOnly?"PASS_ENGINE_REOPEN":"PASS_ENGINE_CAPTURE");stage("QUIT");}
                default->throw new IllegalStateException("Unknown QA phase: "+phase);
            }
        }catch(Throwable failure){fail(failure);}
    }
    private void prepareCases(MinecraftClient client){
        var ladder=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.FREESTANDING,true);cases.add(new Case("V12-wall",new BlockPos(0,64,0),new ItemStack(Registries.ITEM.get(new Identifier("bloodborne_dw","prototype_wall"))),false));cases.add(new Case("V12-ladder",new BlockPos(6,64,0),PrototypeArchitecture.LADDER.artisticStack(ladder),false));
        var barrel=new ItemStack(Registries.ITEM.get(new Identifier("bloodborne_dw","source_addon_barrel_0")));require(!barrel.isEmpty()&&barrel.getItem() instanceof CompositeItem,"Existing source barrel installation item required");cases.add(new Case("NEW-barrel",new BlockPos(12,64,0),ImportedAssemblyV126.canonicalStack(ImportedTypeAliases.canonicalStack(barrel)),false));cases.add(new Case("RP-curtain",new BlockPos(20,64,0),new ItemStack(Registries.ITEM.get(new Identifier("bloodborne_rp","curtainsmall_placer"))),true));cases.add(new Case("DW-barrier",new BlockPos(30,64,0),DwUtilityBlocks.BARRIER_ITEM.getDefaultStack(),false));cases.add(new Case("DW-light",new BlockPos(38,64,0),DwUtilityBlocks.LIGHT_ITEM.getDefaultStack(),false));for(var current:cases)require(!current.stack.isEmpty(),"Every test item is actually registered: "+current.name);
    }
    private void prepareFrames(MinecraftClient client){
        for(var key:List.of("wall","ladder","barrel","curtain","compact","grass")){int x=switch(key){case "wall"->0;case "ladder"->6;case "barrel"->12;case "curtain"->20;case "grass"->50;default->44;};if(key.equals("compact")&&client.world.getBlockState(new BlockPos(x,64,0)).isAir())continue;capture.add(new Frame("v127-base-"+key,x,"empty","base",false));}
        for(var key:List.of("wall","ladder","barrel","curtain","compact","grass")){int x=switch(key){case "wall"->0;case "ladder"->6;case "barrel"->12;case "curtain"->20;case "grass"->50;default->44;};if(key.equals("compact")&&client.world.getBlockState(new BlockPos(x,64,0)).isAir())continue;capture.add(new Frame("v127-alt-"+key,x,"empty","alt",false));}
        capture.add(new Frame("v127-dw-barrier-yellow-held",30,"dw_barrier","alt",false));capture.add(new Frame("v127-vanilla-barrier-red-held",32,"barrier","",false));capture.add(new Frame("v127-dw-barrier-empty-hidden",30,"empty","alt",false));capture.add(new Frame("v127-dw-light-held-marker",38,"dw_light","alt",true));capture.add(new Frame("v127-dw-light-empty-hidden-light14",38,"empty","alt",true));report.addProperty("expectedFrames",capture.size());
        require(capture.size()==17,"All six BASE/ALT native/RP/banked compact/authored grass pairs and five utility marker frames are required");
    }
    private void startFrame(MinecraftClient client,Frame frame){navigate(client,new BlockPos(frame.x,64,0),"FRAME_EQUIP");}
    private void requireProfile(MinecraftClient client,Frame frame){if(frame.profile.isEmpty())return;if(frame.x==20){var rp=rp(client);require(rp!=null&&rp.alternateVisual()==frame.profile.equals("alt"),"Actual tracked RP profile is synchronized");report.addProperty("rpAlternateSynced",rp.alternateVisual());return;}var root=new BlockPos(frame.x,64,0);var state=client.world.getBlockState(root);require(frame.profile.equals(BbAlternateService.profile(state)),"Actual native profile is synchronized: "+frame.name);if(frame.x==0||frame.x==6||frame.x==44){int[] colors=allQuads(client,state).stream().flatMapToInt(quad->java.util.stream.IntStream.range(0,4).map(vertex->quad.getVertexData()[vertex*(quad.getVertexData().length/4)+3])).distinct().toArray();var row=new JsonObject();row.addProperty("name",frame.name);row.add("bakedVertexColorsAbgr",JSON.toJsonTree(colors));operations.add(row);}}
    private List<net.minecraft.client.render.model.BakedQuad> allQuads(MinecraftClient client,net.minecraft.block.BlockState state){
        var model=client.getBlockRenderManager().getModel(state);var quads=new ArrayList<>(model.getQuads(state,null,Random.create(0)));
        for(var face:Direction.values())quads.addAll(model.getQuads(state,face,Random.create(0)));return quads;
    }
    private void verifyAuthoredGrassProviders(MinecraftClient client){
        var pos=new BlockPos(50,64,0);var placed=client.world.getBlockState(pos);require(ImportedCompactBlock.compact(placed)&&client.world.getBlockEntity(pos)==null,"Authored grass station is the actual native no-BE palette representation");
        require(allQuads(client,placed).stream().anyMatch(quad->quad.getColorIndex()==0),"Native authored grass model retains tint-index0 quads");
        var provider=net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK;
        var items=net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM;
        for(String path:List.of("source_grass_block","source_bricks_grass_block_0","source_potted_fern")){
            var block=CompositeArchitecture.kindBlock(path);require(block!=null,"Exact authored source tint registry exists: "+path);var callback=provider.get(block);var itemCallback=items.get(block.asItem());require(callback!=null&&itemCallback!=null,"Actual native block/item color providers are registered: "+path);
            int expected=RemainingSourceTint.color(block,client,pos),actual=callback.getColor(block.getDefaultState(),client.world,pos,0);int expectedInventory=RemainingSourceTint.color(block,client,null),actualInventory=itemCallback.getColor(new ItemStack(block),0);
            require(actual==expected&&actual!=0xFFFFFF&&actualInventory==expectedInventory,"Actual native and inventory callbacks equal the authored owner-renderer palette: "+path);
            require(callback.getColor(block.getDefaultState(),client.world,pos,1)==0xFFFFFF&&itemCallback.getColor(new ItemStack(block),1)==0xFFFFFF,"Untinted/other-index faces preserve white modulation: "+path);
            var row=new JsonObject();row.addProperty("operation","native_authored_grass_color_provider_parity");row.addProperty("registry",block.spec.id.toString());row.addProperty("nativeBiomeRgb",actual);row.addProperty("ownerRendererBiomeRgb",expected);row.addProperty("nativeInventoryRgb",actualInventory);row.addProperty("ownerRendererInventoryRgb",expectedInventory);operations.add(row);
        }
        var vanilla=provider.get(net.minecraft.block.Blocks.GRASS_BLOCK);require(vanilla!=null&&vanilla.getColor(net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState(),client.world,pos,0)==net.minecraft.client.color.world.BiomeColors.getGrassColor(client.world,pos),"Original vanilla grass color provider remains standard");
        report.addProperty("authoredNativeGrassBiomeAndInventoryTint","PASS_ACTUAL_CLIENT_COLOR_PROVIDERS");
    }
    private boolean placed(MinecraftClient client,Case current){if(current.rp)return rp(client)!=null;return current.stack.getItem() instanceof BlockItem item&&client.world.getBlockState(current.root).isOf(item.getBlock());}
    private RpObjectEntity rp(MinecraftClient client){for(var entity:client.world.getEntities())if(entity instanceof RpObjectEntity rp&&rp.assetId().equals("curtainsmall"))return rp;return null;}
    private ItemStack held(String path){return path.equals("empty")?ItemStack.EMPTY:new ItemStack(Registries.ITEM.get(new Identifier(path.equals("barrier")?"minecraft":"bloodborne_dw",path)));}
    private void equip(MinecraftClient client,ItemStack stack){client.player.getInventory().selectedSlot=0;client.player.getInventory().setStack(0,stack.copy());client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(0));client.interactionManager.clickCreativeStack(stack.copy(),36);}
    private void navigate(MinecraftClient client,BlockPos root,String after){camera=new double[]{root.getX()+.5,64,root.getZ()-3.5,0,18};client.player.networkHandler.sendChatCommand("tp @s "+camera[0]+" "+camera[1]+" "+camera[2]+" "+camera[3]+" "+camera[4]);next=after;stage("NAVIGATE");}
    private boolean atCamera(MinecraftClient client){return client.player!=null&&camera!=null&&client.player.getPos().squaredDistanceTo(new Vec3d(camera[0],camera[1],camera[2]))<.0025&&Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-(float)camera[3]))<.5&&Math.abs(client.player.getPitch()-camera[4])<.5;}
    private void record(MinecraftClient client,String operation,String name,BlockPos pos){var row=new JsonObject();row.addProperty("operation",operation);row.addProperty("name",name);row.add("at",JSON.toJsonTree(List.of(pos.getX(),pos.getY(),pos.getZ())));row.addProperty("clientWorldTick",client.world.getTime());if(client.world.getBlockEntity(pos) instanceof CompositeBlockEntity own&&own.resident()!=null)row.addProperty("syncedOwnerUuid",own.resident().instanceId().toString());operations.add(row);}
    private void memory(MinecraftClient client,String name){var runtime=Runtime.getRuntime();var row=new JsonObject();row.addProperty("operation","native_jvm_memory_and_cache_snapshot");row.addProperty("name",name);row.addProperty("usedHeapBytes",runtime.totalMemory()-runtime.freeMemory());row.addProperty("committedHeapBytes",runtime.totalMemory());row.addProperty("maximumHeapBytes",runtime.maxMemory());row.add("alternateModelCache",JSON.toJsonTree(dev.dreamwalker.bloodbornedw.tool.NativeAlternateVisualClient.snapshot()));row.add("clientOwnerGeometryCache",JSON.toJsonTree(CompositeShapeSnapshots.metrics(client.world)));operations.add(row);report.addProperty("memoryInterpretation","Observed native JVM heap/cache snapshots; no forced GC, synthetic memory estimate or client-memory claim from server GameTests");}
    private void requestState(MinecraftClient client,boolean commandFixture){
        var server=client.getServer();UUID playerId=client.player.getUuid();
        server.execute(()->{try{
            var world=server.getOverworld();var player=server.getPlayerManager().getPlayer(playerId);require(player!=null,"Actual integrated player is present");
            if(commandFixture){
                var snapshot=ToolCommands.get(server).writeNbt(new NbtCompound());var entries=snapshot.getList("Entries",10);
                for(int x:new int[]{12,30,38}){
                    var target=ToolTarget.block(world,new BlockPos(x,64,0),player);require(target!=null&&target.valid(server),"Actual command fixture target exists: "+x);
                    NbtCompound binding=new NbtCompound();binding.put("Target",target.write());binding.putString("Commands","time query daytime");binding.putInt("PersonalLimit",7);binding.putInt("GlobalLimit",11);binding.putInt("GlobalUsed",2);
                    var people=new net.minecraft.nbt.NbtList();var person=new NbtCompound();person.putUuid("UUID",playerId);person.putInt("Used",1);people.add(person);binding.put("Players",people);entries.add(binding);
                }
                snapshot.put("Entries",entries);ToolCommands.restoreAssemblyMigration(server,snapshot);
            }
            var state=new JsonObject();var objects=new JsonArray();
            for(int x:new int[]{0,6,12,30,32,38,44,50}){
                var pos=new BlockPos(x,64,0);var block=world.getBlockState(pos);var row=new JsonObject();row.addProperty("x",x);row.addProperty("registry",Registries.BLOCK.getId(block.getBlock()).toString());row.addProperty("blockState",block.toString());
                var owner=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(world,pos);if(owner!=null)row.addProperty("ownerUuid",owner.instanceId().toString());
                var entity=world.getBlockEntity(pos);if(entity!=null)row.addProperty("typedBlockEntitySnbt",entity.createNbt().toString());objects.add(row);
            }
            for(var entity:world.iterateEntities())if(entity instanceof RpObjectEntity rp&&rp.assetId().equals("curtainsmall")){
                var row=new JsonObject();row.addProperty("x",20);row.addProperty("registry",Registries.ENTITY_TYPE.getId(rp.getType()).toString());row.addProperty("ownerUuid",rp.getUuid().toString());row.addProperty("BbAltVisual",rp.alternateVisual());
                NbtCompound nbt=new NbtCompound();rp.writeNbt(nbt);
                // Pos/Rotation, model settings, links and typed source payload
                // are compared. Transient vanilla movement/environment fields
                // are not part of this static object persistence assertion.
                for(String key:List.of("Motion","FallDistance","Fire","Air","OnGround","PortalCooldown"))nbt.remove(key);
                row.addProperty("typedStableEntitySnbt",nbt.toString());objects.add(row);
            }
            state.add("objects",objects);state.addProperty("typedBindingsSnbt",ToolCommands.get(server).writeNbt(new NbtCompound()).toString());state.addProperty("lightLevel14",world.getLightLevel(LightType.BLOCK,new BlockPos(38,64,0)));
            if(!commandFixture)require(state.get("lightLevel14").getAsInt()==14,"Actual saved DW light remains14");persisted.set(state);
        }catch(Throwable failure){serverFailure.set(failure);}});
    }
    private void checkServerFailure(){var failure=serverFailure.get();if(failure!=null)throw new IllegalStateException("Server state proof failed",failure);}
    private void stage(String next){phase=next;age=0;settled=0;}
    private void write(String status)throws Exception{if(output==null)return;report.addProperty("status",status);report.addProperty("phase",phase);report.addProperty("frameIndex",frameIndex);report.addProperty("updatedUtc",Instant.now().toString());Files.writeString(output,JSON.toJson(report));}
    private void fail(Throwable failure){done=true;report.addProperty("failure",failure.toString());try{write("FAIL");}catch(Exception ignored){}failure.printStackTrace();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());}
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
