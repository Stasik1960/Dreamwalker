"""Prepare/build the v12.7.1 gallery author from the reviewed v12.7 author.

Production is not built or modified. --prepare-only creates source/init/report
without starting any JVM, so rendering measurements can run independently.
"""
import argparse, hashlib, json, os, subprocess, traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=Path.home()/'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as source:
        for data in iter(lambda:source.read(1<<20),b''):h.update(data)
    return h.hexdigest()
def replace(text,old,new):
    assert text.count(old)==1,old[:100]
    return text.replace(old,new)
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--output',type=Path,default=ROOT/'build/v1271-gallery-addon')
    p.add_argument('--worldedit-api-jar',type=Path,default=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar'))
    p.add_argument('--prepare-only',action='store_true');args=p.parse_args()
    output=args.output.absolute();digest=sha(args.production_jar);report=output/'build-report.json'
    try:
        source=ROOT/'tools/gallery-v127/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV127Author.java'
        author=source.read_text(encoding='utf8')
        author=author.replace('FlatGalleryV127Author','FlatGalleryV1271Author').replace('GalleryV127Author','GalleryV1271Author').replace('dreamwalker-flat-gallery-v127','dreamwalker-flat-gallery-v1271').replace('dreamwalker_flat_gallery_v127','dreamwalker_flat_gallery_v1271').replace('V12.7','V12.7.1')
        author=replace(author,'int index,reviewIndex,cityIndex,age,width,depth,shelfX,shelfZ,shelfDepth;boolean settling,cityStarted;', 'int index,reviewIndex,cityIndex,age,width,depth,shelfX,shelfZ,shelfDepth,verifiedNativeOffsets;boolean settling,cityStarted;')
        author=replace(author,'report.addProperty("productionJarSha256",marker.get("productionJarSha256").getAsString());','report.addProperty("productionJarSha256",marker.get("productionJarSha256").getAsString());\n            report.add("galleryPreparation",marker.get("galleryPreparation").deepCopy());')
        author=replace(author,'int yaw=state.contains(CompositeRootBlock.ROTATION)?state.get(CompositeRootBlock.ROTATION):0;', '''if(input.has("originalSourceBlockEntitiesBase64")){
                var preserved=new net.minecraft.nbt.NbtList();
                for(var encoded:input.getAsJsonArray("originalSourceBlockEntitiesBase64")){
                    byte[] original=Base64.getDecoder().decode(encoded.getAsString());
                    try(var data=new java.io.DataInputStream(new java.io.ByteArrayInputStream(original))){
                        preserved.add(net.minecraft.nbt.NbtIo.read(data,new net.minecraft.nbt.NbtTagSizeTracker(16_777_216)));
                        require(data.available()==0,"Trailing original source NBT bytes");
                    }catch(java.io.IOException failure){throw new IllegalStateException("Invalid exact typed source block entity archive",failure);}
                }
                payload.put("OriginalSourceBlockEntities",preserved);
            }
            int yaw=state.contains(CompositeRootBlock.ROTATION)?state.get(CompositeRootBlock.ROTATION):0;''')
        author=replace(author,'int yaw=state.contains(CompositeRootBlock.ROTATION)?state.get(CompositeRootBlock.ROTATION):0;','''int yaw=state.contains(CompositeRootBlock.ROTATION)?state.get(CompositeRootBlock.ROTATION):0;
            if(input.has("sourceNativeModelOffsetState")){
                require(namespace.equals("city")&&t.x==floor(t.x)&&t.y==floor(t.y)&&t.z==floor(t.z),"Native source offset requires integer translated source city");
                var a=input.getAsJsonArray("sourceNativeModelOffsetPosition");var sourcePos=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                var actual=SourceNativeModelOffsetV127.sourceState(input.get("sourceNativeModelOffsetState").getAsString()).getModelOffset(net.minecraft.world.EmptyBlockView.INSTANCE,sourcePos);
                var planned=input.getAsJsonArray("plannedNativeSourceModelOffset");
                require(Math.abs(actual.x-planned.get(0).getAsDouble())<1e-12&&Math.abs(actual.y-planned.get(1).getAsDouble())<1e-12&&Math.abs(actual.z-planned.get(2).getAsDouble())<1e-12,"Source offset preview differs from actual Minecraft BlockState.getModelOffset");
                // Input SourceShift includes this independently checked native
                // offset exactly once; do not apply a second translated seed.
                verifiedNativeOffsets++;
            }''')
        author=replace(author,'server.save(false,true,true);report.addProperty("cataloguePlaced",rows.size());','report.addProperty("nativeSourceModelOffsetsVerified",verifiedNativeOffsets);server.save(false,true,true);report.addProperty("cataloguePlaced",rows.size());')
        author=replace(author,'require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world")','verifyProductionJar(marker);\n            require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world")')
        author=replace(author,'    private static void require(boolean value,String message)', '''    private static void verifyProductionJar(JsonObject marker)throws Exception{
        var origins=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
        require(origins.size()==1&&Files.isRegularFile(origins.get(0)),"Packaged production JAR required for isolated gallery authoring");
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(origins.get(0))){byte[] bytes=new byte[1<<20];int n;while((n=input.read(bytes))!=-1)digest.update(bytes,0,n);}
        require(HexFormat.of().formatHex(digest.digest()).equals(marker.get("productionJarSha256").getAsString()),"Gallery marker production SHA differs from actual loaded mod");
        require(marker.getAsJsonObject("galleryPreparation").get("sourcePreparationReused").getAsBoolean(),"Reused full-city preparation required");
    }
    private static void require(boolean value,String message)''')
        java_source=output/'java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV1271Author.java'
        java_source.parent.mkdir(parents=True,exist_ok=True);java_source.write_text(author,encoding='utf8')
        resources=output/'resources'
        write(resources/'fabric.mod.json',{'schemaVersion':1,'id':'dreamwalker_gallery_author','version':'1.2.7.1','name':'Dreamwalker v12.7.1 isolated gallery author','environment':'*','entrypoints':{'main':['dev.dreamwalker.bloodbornedw.gallery.FlatGalleryV1271Author']},'depends':{'minecraft':'1.20.1','java':'>=17','fabricloader':'>=0.16.10','fabric-api':'*','bloodborne_dw':'*'}})
        init=output/'gallery.init.gradle'
        init.write_text('''gradle.projectsEvaluated {
 def p=gradle.rootProject
 p.sourceSets { v1271gallery {
  java.srcDirs=['__QA_JAVA_ROOT__'];resources.srcDirs=['__QA_RESOURCE_ROOT__']
  compileClasspath += p.sourceSets.main.output + p.sourceSets.main.compileClasspath
  runtimeClasspath += p.sourceSets.main.output + p.sourceSets.main.runtimeClasspath
 } }
 p.configurations.v1271galleryImplementation.extendsFrom(p.configurations.implementation)
 def qa=p.tasks.register('v1271GalleryJar',Jar) {dependsOn p.tasks.named('v1271galleryClasses');from p.sourceSets.v1271gallery.output;archiveBaseName='dreamwalker-v1271-gallery-author';archiveVersion='1.2.7.1';archiveClassifier='dev'}
 def remapType=p.tasks.named('remapJar').get().getClass().superclass
 p.tasks.register('remapV1271GalleryJar',remapType) {dependsOn qa;inputFile.set(qa.flatMap{it.archiveFile});classpath.from(p.sourceSets.main.output,p.sourceSets.main.compileClasspath);archiveBaseName='dreamwalker-v1271-gallery-author';archiveVersion='1.2.7.1';archiveClassifier='';addNestedDependencies=false}
}'''.replace('__QA_JAVA_ROOT__',(output/'java').as_posix()).replace('__QA_RESOURCE_ROOT__',resources.as_posix()),encoding='utf8')
        command=[str(ROOT/'gradlew.bat'),'--offline','--no-daemon','-Pworldedit_api_jar='+str(args.worldedit_api_jar.absolute()),'-I',str(init),'remapV1271GalleryJar']
        addon=ROOT/'build/libs/dreamwalker-v1271-gallery-author-1.2.7.1.jar'
        value={'status':'PASS_PREPARED_REQUIRES_BUILD','productionJar':str(args.production_jar.absolute()),'productionJarSha256':digest,'reviewedV127AuthorSha256':sha(source),'generatedAuthorSha256':sha(java_source),'productionJarUnchanged':sha(args.production_jar)==digest,'buildCommand':command,'addon':str(addon),'addonSource':str(java_source)}
        if not args.prepare_only:
            with (output/'build.log').open('wb') as log:
                code=subprocess.run(command,cwd=ROOT,env=dict(os.environ,JAVA_HOME=str(JAVA)),stdout=log,stderr=subprocess.STDOUT).returncode
            value.update(status='PASS' if code==0 and addon.exists() and sha(args.production_jar)==digest else 'FAIL',exitCode=code,addonSha256=sha(addon) if addon.exists() else None,productionJarUnchanged=sha(args.production_jar)==digest)
        write(report,value)
        assert value['status']!='FAIL'
    except BaseException:
        write(report,{'status':'FAIL','error':traceback.format_exc()});raise

if __name__=='__main__':main()
