"""Build the isolated V12.2 author and marker-gated native capture QA only.

Reuses the established opt-in build implementation by executing a transformed
copy held in memory. No legacy file or production build task is modified.
"""
from pathlib import Path


def capture_java(root):
    """Instrument a fresh QA class while leaving the previous source untouched."""
    path=root/'src/gallery/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryClientCapture.java'
    java=path.read_text(encoding='utf8')
    def replace(before,after):
        nonlocal java
        assert java.count(before)==1, 'Legacy capture anchor changed: '+before[:80]
        java=java.replace(before,after)
    replace('public final class FlatGalleryClientCapture implements ClientModInitializer',
            'public final class FlatGalleryV122ClientCapture implements ClientModInitializer')
    replace('private int index,age,settled,quitAge;',
            '''private int index,age,settled,quitAge,frameRenderEvents,maxSettled,poseCorrections;
    private static FlatGalleryV122ClientCapture activeCapture;
    private JsonArray renderedCamera;
    private boolean renderedCameraVerified;''')
    replace('ClientTickEvents.END_CLIENT_TICK.register(this::tick);',
            '''activeCapture=this;
            report.addProperty("cameraController","marker-gated planned pose at client ticks and GameRenderer.renderWorld HEAD, before native Camera.update");
            report.addProperty("criticalCameraToleranceUnchanged",true);
            report.addProperty("captureHudHidden",true);
            ClientTickEvents.START_CLIENT_TICK.register(FlatGalleryV122ClientCapture::holdPlannedPose);
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);''')
    replace('WorldRenderEvents.END.register(context->{if(!stopped&&sent&&!waiting&&client.player!=null&&client.world!=null&&atCamera(client,frames.get(index).getAsJsonObject().getAsJsonArray("camera")))renderReady=true;});',
            '''WorldRenderEvents.END.register(context->{
                if(!stopped&&sent&&!waiting&&client.player!=null&&client.world!=null){
                    frameRenderEvents++;
                    var actual=context.camera();var eye=client.player.getCameraPosVec(context.tickDelta());
                    renderedCamera=JSON.toJsonTree(List.of(actual.getPos().x,actual.getPos().y,actual.getPos().z,actual.getYaw(),actual.getPitch())).getAsJsonArray();
                    renderedCameraVerified=actual.getFocusedEntity()==client.player&&!actual.isThirdPerson()
                        &&actual.getPos().squaredDistanceTo(eye)<0.0025
                        &&Math.abs(MathHelper.wrapDegrees(actual.getYaw()-client.player.getYaw()))<0.5
                        &&Math.abs(actual.getPitch()-client.player.getPitch())<0.5;
                    renderReady=renderedCameraVerified&&atCamera(client,frames.get(index).getAsJsonObject().getAsJsonArray("camera"));
                }
            });''')
    replace('if(quitAfterCapture&&report.has("captureComplete")&&report.get("captureComplete").getAsBoolean()&&++quitAge==20)client.scheduleStop();',
            'if(quitAfterCapture&&++quitAge==20)client.scheduleStop();')
    replace('if(!sent){client.player.networkHandler.sendChatCommand(frame.get("command").getAsString());sent=true;renderReady=false;age=0;return;}',
            'if(!sent){client.player.networkHandler.sendChatCommand(frame.get("command").getAsString());sent=true;renderReady=false;renderedCamera=null;renderedCameraVerified=false;age=0;frameRenderEvents=0;maxSettled=0;poseCorrections=0;return;}')
    replace('require(++age<=900,"Camera did not settle: "+frame.get("fileName").getAsString());',
            'holdPlannedPose(client);require(++age<=900,"Camera did not settle: "+frame.get("fileName").getAsString());')
    replace('int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():60;if(settled<wait||!renderReady)return;',
            'maxSettled=Math.max(maxSettled,settled);int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():60;if(settled<wait||!renderReady)return;')
    replace('captured.addProperty("world",client.world.getRegistryKey().getValue().toString());captured.addProperty("worldTick",client.world.getTime());',
            '''captured.addProperty("world",client.world.getRegistryKey().getValue().toString());captured.addProperty("worldTick",client.world.getTime());
            captured.add("actualRenderedCamera",renderedCamera.deepCopy());captured.addProperty("renderedCameraVerified",renderedCameraVerified);
            captured.addProperty("frameRenderEvents",frameRenderEvents);captured.addProperty("poseCorrections",poseCorrections);''')
    replace('private static JsonObject checkRoot(MinecraftClient client,JsonObject expected) {',
            '''/** Only an explicit fresh capture marker creates activeCapture. Never alters world blocks. */
    public static void holdPlannedPose(MinecraftClient client){
        var capture=activeCapture;
        if(capture==null||capture.stopped||!capture.sent||capture.frames==null||capture.index>=capture.frames.size()
            ||client.player==null||client.world==null||client.currentScreen!=null||client.isPaused())return;
        JsonArray pose=capture.frames.get(capture.index).getAsJsonObject().getAsJsonArray("camera");
        if(!atCamera(client,pose))capture.poseCorrections++;
        net.minecraft.client.option.KeyBinding.unpressAll();
        client.player.setVelocity(Vec3d.ZERO);
        client.player.refreshPositionAndAngles(pose.get(0).getAsDouble(),pose.get(1).getAsDouble(),pose.get(2).getAsDouble(),pose.get(3).getAsFloat(),pose.get(4).getAsFloat());
        if(client.getCameraEntity()!=client.player)client.setCameraEntity(client.player);
        client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
        client.options.hudHidden=true;
    }
    private static JsonObject checkRoot(MinecraftClient client,JsonObject expected) {''')
    replace('private void fail(Throwable failure){stopped=true;report.addProperty("failure",failure.toString());try{write("FAIL_CAPTURE",false);}catch(Exception ignored){}failure.printStackTrace();}',
            '''private void fail(Throwable failure){
        stopped=true;report.addProperty("failure",failure.toString());
        try{report.add("failureDiagnostics",cameraDiagnostics(MinecraftClient.getInstance()));}
        catch(Throwable diagnosticFailure){report.addProperty("diagnosticFailure",diagnosticFailure.toString());}
        report.addProperty("normalStopAfterFailureRequested",quitAfterCapture);
        try{write("FAIL_CAPTURE",false);}catch(Exception ignored){}
        failure.printStackTrace();
    }
    private JsonObject cameraDiagnostics(MinecraftClient client){
        JsonObject result=new JsonObject();
        result.addProperty("frameIndexOneBased",index+1);result.addProperty("age",age);
        result.addProperty("settled",settled);result.addProperty("maxSettled",maxSettled);
        result.addProperty("renderReady",renderReady);result.addProperty("frameRenderEvents",frameRenderEvents);
        result.addProperty("poseCorrections",poseCorrections);result.addProperty("renderedCameraVerified",renderedCameraVerified);
        if(renderedCamera!=null)result.add("lastRenderedCamera",renderedCamera.deepCopy());
        result.addProperty("sent",sent);result.addProperty("waiting",waiting);
        result.addProperty("screen",client.currentScreen==null?null:client.currentScreen.getClass().getName());
        result.addProperty("paused",client.isPaused());result.addProperty("windowFocused",client.isWindowFocused());
        result.addProperty("skipGameRender",client.skipGameRender);
        result.addProperty("perspective",client.options.getPerspective().toString());
        result.addProperty("windowWidth",client.getWindow().getWidth());result.addProperty("windowHeight",client.getWindow().getHeight());
        JsonArray expected=null;
        if(frames!=null&&index>=0&&index<frames.size()){
            JsonObject frame=frames.get(index).getAsJsonObject();result.add("failedFrame",frame.deepCopy());expected=frame.getAsJsonArray("camera");
        }
        if(client.player!=null){
            result.add("actualPlayerPose",JSON.toJsonTree(List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch())));
            result.addProperty("spectator",client.player.isSpectator());
            result.addProperty("flying",client.player.getAbilities().flying);
            if(expected!=null){
                double dx=client.player.getX()-expected.get(0).getAsDouble(),dy=client.player.getY()-expected.get(1).getAsDouble(),dz=client.player.getZ()-expected.get(2).getAsDouble();
                double yaw=MathHelper.wrapDegrees(client.player.getYaw()-expected.get(3).getAsDouble()),pitch=client.player.getPitch()-expected.get(4).getAsDouble();
                result.add("poseErrors",JSON.toJsonTree(List.of(dx,dy,dz,yaw,pitch)));
                result.addProperty("positionWithinTolerance",dx*dx+dy*dy+dz*dz<0.0025);
                result.addProperty("yawWithinTolerance",Math.abs(yaw)<0.5);result.addProperty("pitchWithinTolerance",Math.abs(pitch)<0.5);
            }
            if(client.world!=null){
                result.addProperty("legacyPlayerChunkGate",client.world.isChunkLoaded(client.player.getBlockPos()));
                result.addProperty("actualPlayerChunkLoaded",client.world.getChunkManager().isChunkLoaded(client.player.getBlockX()>>4,client.player.getBlockZ()>>4));
            }
        }
        var cameraEntity=client.getCameraEntity();
        if(cameraEntity!=null){
            result.addProperty("cameraEntityIsPlayer",cameraEntity==client.player);
            result.addProperty("cameraEntityUuid",cameraEntity.getUuidAsString());
            result.add("cameraEntityPose",JSON.toJsonTree(List.of(cameraEntity.getX(),cameraEntity.getY(),cameraEntity.getZ(),cameraEntity.getYaw(),cameraEntity.getPitch())));
        }
        if(client.world!=null)result.addProperty("worldTick",client.world.getTime());
        return result;
    }''')
    return java


CAMERA_MIXIN='''package dev.dreamwalker.bloodbornedw.gallery.capture.mixin;
import dev.dreamwalker.bloodbornedw.gallery.FlatGalleryV122ClientCapture;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Minecraft 1.20.1 intermediary selector; opt-in QA class, no production mixins. */
@Mixin(targets="net.minecraft.class_757",remap=false)
public abstract class FlatGalleryV122CameraMixin {
    @Inject(method="method_3188",at=@At("HEAD"),remap=false,require=1)
    private void dwGalleryHoldPose(CallbackInfo ci){
        FlatGalleryV122ClientCapture.holdPlannedPose(MinecraftClient.getInstance());
    }
}
'''

source=Path(__file__).with_name('build_flat_gallery_addon.py').read_text(encoding='utf8')
source=source.replace('FlatGalleryAuthor','FlatGalleryV122Author')
source=source.replace('FlatGalleryClientCapture','FlatGalleryV122ClientCapture')
source=source.replace('dreamwalker-flat-gallery-author','dreamwalker-flat-gallery-v122-author')
source=source.replace("'version': '1.0.0'", "'version': '1.2.4'")
source=source.replace("archiveVersion = '1.0.0'", "archiveVersion = '1.2.4'")
source=source.replace('v122-author-1.0.0.jar','v122-author-1.2.4.jar')
anchor='    output.mkdir(parents=True)\n'
assert source.count(anchor)==1
source=source.replace(anchor,anchor+'''    generated_java=output/'generated-java'
    generated_arg=str(generated_java).replace('\\\\', '/').replace("'", "\\\\'")
    generated_capture=generated_java/'dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV122ClientCapture.java'
    generated_capture.parent.mkdir(parents=True,exist_ok=True)
    generated_capture.write_text(capture_java(ROOT),encoding='utf8')
    generated_mixin=generated_capture.parent/'capture/mixin/FlatGalleryV122CameraMixin.java'
    generated_mixin.parent.mkdir(parents=True,exist_ok=True)
    generated_mixin.write_text(CAMERA_MIXIN,encoding='utf8')
''')
source=source.replace("java.srcDirs = ['src/gallery/java']", "java.srcDirs = ['src/gallery/java', 'GENERATED_JAVA_ARG']")
source=source.replace("'**/FlatGalleryV122ClientCapture.java'", "'**/FlatGalleryV122ClientCapture.java', '**/FlatGalleryV122CameraMixin.java'")
source=source.replace(".replace('RESOURCE_ARG', resource_arg)", ".replace('RESOURCE_ARG', resource_arg).replace('GENERATED_JAVA_ARG', generated_arg)")
anchor="    write(resources / 'fabric.mod.json', metadata)"
assert source.count(anchor)==1
source=source.replace(anchor,"""    metadata['mixins']=[{'config':'dreamwalker-gallery-v122-capture.mixins.json','environment':'client'}]
    write(resources/'dreamwalker-gallery-v122-capture.mixins.json',{
        'required':True,'minVersion':'0.8','package':'dev.dreamwalker.bloodbornedw.gallery.capture.mixin',
        'compatibilityLevel':'JAVA_17','client':['FlatGalleryV122CameraMixin'],
        'injectors':{'defaultRequire':1}})
"""+anchor)
exec(compile(source,str(Path(__file__).with_name('build_flat_gallery_addon.py')),'exec'),globals())
