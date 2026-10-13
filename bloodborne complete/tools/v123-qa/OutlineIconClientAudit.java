package dev.dreamwalker.bloodbornedw.v123qa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.debug.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;

/** Native framebuffer QA, never edits screenshots and never ships in the production mod. */
public final class OutlineIconClientAudit implements ClientModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject result=new JsonObject();private final JsonArray frames=new JsonArray(),captured=new JsonArray();
    private List<ItemStack> icons;private Path gameDir;private int index,age,settled,stopAge;private boolean initialized,sent,waiting,done;private volatile boolean saved;private JsonObject row;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient client){
        if(done){if(result.has("status")&&((result.has("captureComplete")&&result.get("captureComplete").getAsBoolean())||result.get("status").getAsString().equals("FAIL"))&&++stopAge==20)client.scheduleStop();return;}
        if(client.player==null||client.world==null||client.getServer()==null)return;
        try{
            if(!initialized){
                Path manifest=client.getServer().getSavePath(WorldSavePath.ROOT).resolve("v123-fixture.json");if(!Files.isRegularFile(manifest))return;
                initialized=true;gameDir=client.runDirectory.toPath();require(!Files.exists(gameDir.resolve("v123-client-output.json")),"Fresh evidence required");
                String digest=OutlineQaEvidence.productionSha();JsonObject fixture=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();require(digest.equals(fixture.get("productionJarSha256").getAsString()),"Client fixture production differs");
                icons=UnifiedCreativeCatalogue.canonicalItems().stream().filter(s->ObjectRegistry.isPlacementItem(s)).toList();require(icons.size()==69,"69 canonical RP icons expected");
                for(int page=0;page<2;page++){JsonObject f=new JsonObject();f.addProperty("type","icons");f.addProperty("page",page);f.addProperty("fileName","v123-rp-icons-page-"+(page+1)+".png");frames.add(f);}
                for(JsonElement e:fixture.getAsJsonArray("fixtures")){JsonObject f=e.getAsJsonObject();if(f.get("screenshot").getAsBoolean()){f=f.deepCopy();
                        JsonArray original=f.getAsJsonArray("camera").deepCopy();f.add("originalFixtureCamera",original);JsonArray camera=original.deepCopy();
                        double targetY=original.get(1).getAsDouble()+1.62-Math.tan(Math.toRadians(original.get(4).getAsDouble()))*3;
                        double feet=Math.max(original.get(1).getAsDouble(),f.getAsJsonArray("root").get(1).getAsDouble()+.25),dy=feet+client.player.getStandingEyeHeight()-targetY;
                        require(Math.sqrt(9+dy*dy)<4.75,"Clear-floor native QA camera exceeds creative reach");camera.set(1,new JsonPrimitive(feet));camera.set(4,new JsonPrimitive(Math.toDegrees(Math.atan2(dy,3))));f.add("camera",camera);f.addProperty("cameraPolicy","QA camera feet above platform surface; aimed down at the same independently tested simple-shape point within native creative reach");
                        f.addProperty("type","outline");f.addProperty("fileName","v123-outline-"+f.get("role").getAsString()+".png");frames.add(f);}}
                result.addProperty("schema","dreamwalker-v123-native-client-v1");result.addProperty("productionJarSha256",digest);result.addProperty("nativeGuiIconCount",icons.size());result.addProperty("physicalMouseKeyboard","NOT_RUN");result.addProperty("captureMethod","Minecraft native DrawContext.drawItem and ScreenshotRecorder; actual world crosshair/outline; no PNG modification");result.add("frames",captured);write("RUNNING",false);return;
            }
            if(waiting){require(++age<200,"Native PNG save timed out");if(!saved)return;Path png=gameDir.resolve("screenshots").resolve(row.get("fileName").getAsString());var image=ImageIO.read(png.toFile());require(image!=null,"Native screenshot cannot decode");row.addProperty("sha256",OutlineQaEvidence.sha(png));row.addProperty("bytes",Files.size(png));row.addProperty("width",image.getWidth());row.addProperty("height",image.getHeight());row.addProperty("status","PASS");captured.add(row);index++;waiting=false;sent=false;age=settled=0;done=index==frames.size();write(done?"CAPTURE_COMPLETE":"RUNNING",done);return;}
            JsonObject frame=frames.get(index).getAsJsonObject();boolean gui=frame.get("type").getAsString().equals("icons");
            if(!sent){sent=true;age=settled=0;if(gui){client.setScreen(new IconSheet(frame.get("page").getAsInt(),icons));}else{
                    client.setScreen(null);JsonArray p=frame.getAsJsonArray("camera");String name=client.player.getGameProfile().getName();client.getServer().execute(()->{var player=client.getServer().getPlayerManager().getPlayer(name);player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(client.getServer().getOverworld(),p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble(),p.get(3).getAsFloat(),p.get(4).getAsFloat());for(int n=0;n<9;n++)player.getInventory().setStack(n,icons.get(n).copy());});
                }return;}
            if(++age>=900){JsonObject debug=new JsonObject();debug.addProperty("fileName",frame.get("fileName").getAsString());if(frame.has("camera"))debug.add("expectedCamera",frame.getAsJsonArray("camera").deepCopy());debug.addProperty("actualCamera",List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch()).toString());debug.addProperty("eye",client.player.getEyePos().toString());debug.addProperty("standingEyeHeight",client.player.getStandingEyeHeight());debug.addProperty("boundingBox",client.player.getBoundingBox().toString());debug.addProperty("feetCell",client.world.getBlockState(client.player.getBlockPos()).toString());debug.addProperty("screen",String.valueOf(client.currentScreen));debug.addProperty("atExpectedCamera",frame.has("camera")&&atCamera(client,frame.getAsJsonArray("camera")));result.add("cameraTimeoutDiagnostic",debug);throw new IllegalStateException("Native frame never settled "+frame.get("fileName"));}
            boolean ready=gui?client.currentScreen instanceof IconSheet sheet&&sheet.rendered:client.currentScreen==null&&atCamera(client,frame.getAsJsonArray("camera"));settled=ready?settled+1:0;if(settled<50)return;
            row=frame.deepCopy();if(gui){IconSheet sheet=(IconSheet)client.currentScreen;require(sheet.failure==null,"Icon render failed "+sheet.failure);row.add("icons",sheet.renderEvidence.deepCopy());row.addProperty("sourceBakedBoneTransformsUnchanged","PASS_BEFORE_AFTER_EACH_DRAW_ITEM");}
            else{require(client.crosshairTarget instanceof BlockHitResult,"Native crosshair misses fixture");BlockHitResult hit=(BlockHitResult)client.crosshairTarget;BlockPos p=hit.getBlockPos();var owner=CompositeRuntime.target(client.world,p,client.player);
                JsonArray r=frame.getAsJsonArray("root");BlockPos root=new BlockPos(r.get(0).getAsInt(),r.get(1).getAsInt(),r.get(2).getAsInt());var resident=VerticalMount.owner(client.world,root);
                if(owner==null||!owner.instanceId().toString().equals(frame.get("uuid").getAsString())){JsonObject debug=new JsonObject();debug.addProperty("role",frame.get("role").getAsString());debug.addProperty("hitCell",p.toShortString());debug.addProperty("hitPosition",hit.getPos().toString());debug.addProperty("actualOwner",String.valueOf(owner));debug.addProperty("rootResident",String.valueOf(resident));debug.addProperty("eye",client.player.getEyePos().toString());debug.addProperty("direction",client.player.getRotationVec(1).toString());debug.addProperty("nativeOutline",client.world.getBlockState(p).getOutlineShape(client.world,p).getBoundingBoxes().toString());debug.addProperty("contributions",CompositeRuntime.contributions(client.world,p).toString());result.add("aimDiagnostic",debug);}
                require(owner!=null&&owner.instanceId().toString().equals(frame.get("uuid").getAsString()),"Native crosshair selected another owner at "+p);require(resident!=null&&owner.equals(resident),"Selected helper owner differs from actual saved root resident");
                var state=client.world.getBlockState(p);row.addProperty("actualHitCell",p.toShortString());row.addProperty("actualUuid",owner.instanceId().toString());row.addProperty("outlineBoxes",state.getOutlineShape(client.world,p).getBoundingBoxes().size());row.addProperty("physicalBoxes",state.getCollisionShape(client.world,p).getBoundingBoxes().size());row.addProperty("actualCamera",List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch()).toString());row.addProperty("ownerMethod","Actual production CompositeRuntime.target ray on root or helper cell; saved root resident UUID also verified");}
            saved=false;waiting=true;age=0;ScreenshotRecorder.saveScreenshot(gameDir.toFile(),row.get("fileName").getAsString(),client.getFramebuffer(),message->client.execute(()->saved=true));
        }catch(Throwable error){done=true;result.addProperty("failure",error.toString());error.printStackTrace();try{write("FAIL",false);}catch(Exception ignored){}}
    }
    private static boolean atCamera(MinecraftClient client,JsonArray p){return client.player.getPos().squaredDistanceTo(new Vec3d(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble()))<.0025&&Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-p.get(3).getAsDouble()))<.5&&Math.abs(client.player.getPitch()-p.get(4).getAsDouble())<.5;}
    private void write(String status,boolean complete)throws Exception{result.addProperty("status",status);result.addProperty("captureComplete",complete);result.addProperty("capturedFrames",captured.size());Files.writeString(gameDir.resolve("v123-client-output.json"),JSON.toJson(result)+"\n");}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static final class IconSheet extends Screen {
        final int page;final List<ItemStack> items;final JsonArray renderEvidence=new JsonArray();boolean rendered;String failure;
        IconSheet(int page,List<ItemStack> items){super(Text.literal("V12.3 native RP icon audit"));this.page=page;this.items=items;}
        @Override public boolean shouldPause(){return false;}
        @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
            draw.fill(0,0,width,height,0xff20242b);draw.drawText(textRenderer,"V12.3 / RP model icons / "+(page+1)+" of 2",8,8,0xffffffff,false);
            try{
                int columns=9,slotW=Math.min(46,(width-12)/columns),slotH=Math.min(50,(height-28)/4),start=page*36,end=Math.min(items.size(),start+36);
                for(int i=start;i<end;i++){ItemStack item=items.get(i);int local=i-start,x=6+(local%columns)*slotW,y=24+(local/columns)*slotH;draw.fill(x,y,x+slotW-2,y+slotH-2,0xff313843);
                    String itemId=Registries.ITEM.getId(item.getItem()).toString();String asset=ObjectRegistry.canonicalId(Registries.ITEM.getId(item.getItem()).getPath().replace("_placer",""));
                    GeoModel<GeoAnimatable> model=new GeoModel<>(){public net.minecraft.util.Identifier getModelResource(GeoAnimatable v){return BloodborneRp.id(AssetCatalog.get(asset).model());}public net.minecraft.util.Identifier getTextureResource(GeoAnimatable v){return BloodborneRp.id(AssetCatalog.get(asset).texture());}public net.minecraft.util.Identifier getAnimationResource(GeoAnimatable v){return BloodborneRp.id(AssetCatalog.get(asset).animation());}};
                    var baked=model.getBakedModel(model.getModelResource(null));String before=boneState(baked.topLevelBones());
                    draw.getMatrices().push();draw.getMatrices().translate(x+7,y+2,0);draw.getMatrices().scale(1.65f,1.65f,1);draw.drawItem(item,0,0);draw.getMatrices().pop();
                    require(before.equals(boneState(baked.topLevelBones())),"Inventory icon altered source baked pose "+itemId);
                    var catalogue=DebugCatalogue.entry(item);String label=catalogue==null?"RP":catalogue.temporaryId();draw.drawText(textRenderer,label,x+7,y+slotH-12,0xffe4e9ed,false);
                    if(!rendered){JsonObject row=new JsonObject();row.addProperty("item",itemId);row.addProperty("temporaryId",label);row.addProperty("builtin3dModel",MinecraftClient.getInstance().getItemRenderer().getModel(item,null,null,0).isBuiltin());require(row.get("builtin3dModel").getAsBoolean(),"RP icon is not native custom3D model "+itemId);row.addProperty("sourceBoneStateSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(before.getBytes(java.nio.charset.StandardCharsets.UTF_8))));row.addProperty("sourceBakedPoseUnchanged",true);renderEvidence.add(row);}
                }rendered=true;
            }catch(Throwable error){failure=error.toString();throw new IllegalStateException(error);}
        }
        private static String boneState(List<GeoBone> roots){StringBuilder out=new StringBuilder();for(GeoBone bone:roots){out.append(bone.getName()).append(':').append(List.of(bone.getRotX(),bone.getRotY(),bone.getRotZ(),bone.getPosX(),bone.getPosY(),bone.getPosZ(),bone.getScaleX(),bone.getScaleY(),bone.getScaleZ(),bone.isHidden(),bone.isHidingChildren()));out.append(boneState(bone.getChildBones()));}return out.toString();}
    }
}
