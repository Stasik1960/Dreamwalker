"""Build isolated V12.7 gallery tooling; the production JAR remains frozen."""
import argparse, hashlib, json, os, subprocess, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SHA='b98c752b797ef07c5acf1d4852bda83eec52717fed798d4ec76860b4f6972850'
PROD=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar'
parser=argparse.ArgumentParser();parser.add_argument('--attempt',type=int,default=1);args=parser.parse_args()
OUT=ROOT/('build/v127-new-gallery-addon'+str(args.attempt))
SOURCE=ROOT/'tools/gallery-v127/java'
JAVA='C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def replace(s,old,new):
    assert s.count(old)==1,old[:100]
    return s.replace(old,new)
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
assert sha(PROD)==SHA and not OUT.exists()
OUT.mkdir()
author=(ROOT/'tools/gallery-v126/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV126Author.java').read_text(encoding='utf8').replace('V126','V127').replace('v126','v127').replace('V12.6','V12.7')
author=replace(author,'equals("architecture")){','equals("architecture")||input.get("kind").getAsString().equals("utility")){')
author=replace(author,'case "items"->Blocks.BLUE_CONCRETE;','case "items"->Blocks.BLUE_CONCRETE;case "utility"->Blocks.ORANGE_CONCRETE;')
author=replace(author,'}else{\n                payload.putDouble(VerticalMount.KEY,station.origin.y-root.getY());','''}else if(kind.equals("utility")){
                world.setBlockState(root,e.state,Block.NOTIFY_ALL);placed=world.getBlockState(root);
                uuid=UUID.nameUUIDFromBytes(("utility:"+root).getBytes(StandardCharsets.UTF_8));representation="native_invisible_utility_block";
                BlockPos support=new BlockPos(station.x+station.width-1,63,station.z+station.depth-1);
                ItemFrameEntity icon=new ItemFrameEntity(world,support.up(),Direction.UP);icon.setHeldItemStack(e.item.copy(),false);icon.setInvulnerable(true);require(world.spawnEntity(icon),"Utility item frame");
            }else{
                payload.putDouble(VerticalMount.KEY,station.origin.y-root.getY());''')
author=replace(author,'case"items"->"Предметы V12 в рамках";','case"items"->"Предметы V12 в рамках";case"utility"->"Служебные V12.7";')
path=SOURCE/'dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV127Author.java';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(author,encoding='utf8')
observer=(ROOT/'build/v127-gallery-benchmark-qa/src/dev/dreamwalker/bloodbornedw/galleryqa/GalleryBenchmark.java').read_text(encoding='utf8')
observer=observer.replace('class GalleryBenchmark ', 'class GalleryBenchmarkV127 ').replace('existingGalleryCopyOnly','newGalleryOnly').replace('existing-gallery','new-gallery')
observer=replace(observer,'route.size()==6','route.size()>=6&&route.size()<=16')
observer=replace(observer,'frames.size()==6','frames.size()==route.size()')
observer=replace(observer,'6*input.get("warmSecondsPerStation")','route.size()*input.get("warmSecondsPerStation")')
observer=replace(observer,'report.addProperty("galleryCreated",false);','report.addProperty("galleryCreated",true);')
observer=replace(observer,'private Path directory,output;','private static GalleryBenchmarkV127 INSTANCE; private Path directory,output;')
observer=replace(observer,'active=true;write("WAITING_FOR_EXISTING_WORLD");','INSTANCE=this;active=true;write("WAITING_FOR_EXISTING_WORLD");')
observer=replace(observer,'if(phase.equals("WARM"))check(', 'lockCamera(client);if(phase.equals("WARM"))check(')
observer=replace(observer,'private void navigate(MinecraftClient client){','private void navigate(MinecraftClient client){GLFW.glfwFocusWindow(client.getWindow().getHandle());')
observer=replace(observer,'private static void check(boolean condition,String message){','''public static void lockCamera(MinecraftClient client){
  var self=INSTANCE;if(self==null||!self.active||self.done||client.player==null||self.route==null||self.index>=self.route.size()||!Set.of("NAVIGATE","SETTLE","WARM","SCREENSHOT").contains(self.phase))return;
  var c=self.route.get(self.index).getAsJsonArray("camera");float yaw=c.get(3).getAsFloat(),pitch=c.get(4).getAsFloat();client.player.setYaw(yaw);client.player.setPitch(pitch);client.player.prevYaw=yaw;client.player.prevPitch=pitch;
  if(self.phase.equals("WARM")||self.phase.equals("SCREENSHOT")){client.player.setPosition(c.get(0).getAsDouble(),c.get(1).getAsDouble(),c.get(2).getAsDouble());client.player.setVelocity(Vec3d.ZERO);}
 }
 private static void check(boolean condition,String message){''')
observer=replace(observer,'case "BOOT"->{if(ticks<40)return;','''case "BOOT"->{if(ticks<40)return;
    if(input.has("worldEditQa")&&input.get("worldEditQa").getAsBoolean()){
        var nativeServer=client.getServer();var playerUuid=client.player.getUuid();
        var qa=java.util.concurrent.CompletableFuture.supplyAsync(()->GalleryWorldEditQa.run(nativeServer,nativeServer.getPlayerManager().getPlayer(playerUuid)),nativeServer).join();
        report.add("actualWorldEditCommandProof",qa);input.addProperty("worldEditQa",false);
    }''')
# GC/non-heap are observations only; never request a GC during frame sampling.
observer=replace(observer,'memory.add(row);','''var gc=new JsonArray();for(var bean:java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()){var g=new JsonObject();g.addProperty("name",bean.getName());g.addProperty("collections",bean.getCollectionCount());g.addProperty("collectionMilliseconds",bean.getCollectionTime());gc.add(g);}row.add("garbageCollectors",gc);row.addProperty("nonHeapUsedBytes",java.lang.management.ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());memory.add(row);''')
path=SOURCE/'dev/dreamwalker/bloodbornedw/galleryqa/GalleryBenchmarkV127.java';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(observer,encoding='utf8')
resources=OUT/'resources';resources.mkdir()
meta={'schemaVersion':1,'id':'dreamwalker_gallery_author','version':'1.2.7-new-gallery','name':'Dreamwalker V12.7 isolated gallery author and observer','environment':'*','entrypoints':{'main':['dev.dreamwalker.bloodbornedw.gallery.FlatGalleryV127Author','dev.dreamwalker.bloodbornedw.galleryqa.GalleryWorldEditPreflight'],'client':['dev.dreamwalker.bloodbornedw.galleryqa.GalleryBenchmarkV127']},'depends':{'minecraft':'1.20.1','java':'>=17','fabricloader':'>=0.16.10','fabric-api':'*','bloodborne_dw':'*'}}
write(resources/'fabric.mod.json',meta)
meta['mixins']=[{'config':'gallery127-camera.mixins.json','environment':'client'}];write(resources/'fabric.mod.json',meta)
write(resources/'gallery127-camera.mixins.json',{'required':True,'package':'dev.dreamwalker.bloodbornedw.galleryqa.capture.mixin','compatibilityLevel':'JAVA_17','client':['GalleryCameraLock'],'injectors':{'defaultRequire':1}})
init=OUT/'gallery.init.gradle'
init.write_text('''gradle.projectsEvaluated {
 def p=gradle.rootProject
 p.sourceSets { v127gallery {
  java.srcDirs=['SOURCE_DIR'];resources.srcDirs=['RESOURCE_DIR']
  compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
  runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
 } }
 p.configurations.v127galleryImplementation.extendsFrom(p.configurations.implementation)
 def qa=p.tasks.register('v127NewGalleryJar',Jar) {dependsOn p.tasks.named('v127galleryClasses');from p.sourceSets.v127gallery.output;archiveBaseName='dreamwalker-v127-new-gallery-qa';archiveVersion='1.2.7';archiveClassifier='dev'}
 def remapType=p.tasks.named('remapJar').get().getClass().superclass
 p.tasks.register('remapV127NewGalleryJar',remapType) {dependsOn qa;inputFile.set(qa.flatMap{it.archiveFile});classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath);archiveBaseName='dreamwalker-v127-new-gallery-qa';archiveVersion='1.2.7';archiveClassifier='';addNestedDependencies=false}
}'''.replace('RESOURCE_DIR',resources.as_posix()).replace('SOURCE_DIR',SOURCE.as_posix()),encoding='utf8')
with (OUT/'build.log').open('wb') as log:
    code=subprocess.run([str(ROOT/'gradlew.bat'),'--offline','--no-daemon','-I',str(init),'remapV127NewGalleryJar'],cwd=ROOT,env=dict(os.environ,JAVA_HOME=JAVA),stdout=log,stderr=subprocess.STDOUT).returncode
addon=ROOT/'build/libs/dreamwalker-v127-new-gallery-qa-1.2.7.jar'
value={'status':'PASS' if code==0 and addon.is_file() and sha(PROD)==SHA else 'FAIL','exitCode':code,'productionJarSha256':SHA,'productionJarUnchanged':sha(PROD)==SHA,'addon':str(addon),'addonSha256':sha(addon) if addon.is_file() else None}
write(OUT/'build-report.json',value)
print(json.dumps(value));assert value['status']=='PASS'
