package dev.dreamwalker.bloodbornedw.dh;

import com.google.gson.*;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.*;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.*;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.*;

/** Experimental static district proxy. Reads only exported files; never asks a
 * server or an unfinished chunk future for architecture or block entities. */
public final class CityDhPilot implements ClientModInitializer {
    private static volatile CityDhPilot instance;
    private static boolean enabled=true;
    private static final Logger LOG=LoggerFactory.getLogger("dreamwalker-dh-pilot");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private record Tile(int x,int z,List<DhApiRenderableBox> boxes){}
    private record Scene(List<Tile> tiles,int boxes,String sha,boolean readOnly){}
    private record Installed(Tile tile,IDhApiRenderableBoxGroup group){}
    private final List<Installed> groups=new ArrayList<>();
    private Path binding,receipt;private Object clientWorld;
    private CompletableFuture<Scene> loading;private Scene scene;private IDhApiLevelWrapper level;
    private int nextTile,ticks;private boolean failed,registered,priorReadOnly,readOnlyChanged;private long renderPasses;
    @Override public void onInitializeClient(){
        instance=this;ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // Explicit isolated-QA opt-in before the first world opens. This is
        // an API override for this process; it does not rewrite DH's settings.
        Path qa=MinecraftClient.getInstance().runDirectory.toPath().resolve("dreamwalker-dh-pilot-qa.json");
        if(Files.isRegularFile(qa,LinkOption.NOFOLLOW_LINKS))DhApi.events.bind(DhApiAfterDhInitEvent.class,new DhApiAfterDhInitEvent(){
            @Override public void afterDistantHorizonsInit(DhApiEventParam<Void> event){try{
                if(Files.size(qa)>4096)throw new IOException("QA settings limit");
                JsonObject marker=JsonParser.parseString(Files.readString(qa)).getAsJsonObject();
                if(!marker.get("disableDistantGeneration").getAsBoolean())return;
                var setting=DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration();boolean accepted=setting.setValue(false);
                Files.writeString(qa.resolveSibling("dreamwalker-dh-pilot-settings-result.json"),JSON.toJson(Map.of("accepted",accepted,"effectiveDistantGeneration",setting.getValue(),"storedDistantGeneration",setting.getTrueValue())));
            }catch(Exception failure){LOG.error("Cannot apply explicit pilot QA settings",failure);}}
        });
    }
    /** QA waits for real DH render callbacks, not merely a successful file read. */
    public static boolean ready(){CityDhPilot value=instance;return enabled&&value!=null&&value.registered&&!value.failed&&value.renderPasses>0;}
    public static void setEnabled(boolean value){enabled=value;if(value&&instance!=null)instance.renderPasses=0;}
    public static Map<String,Object> metrics(){CityDhPilot value=instance;return value==null?Map.of("status","ABSENT"):Map.of("enabled",enabled,"registered",value.registered,"failed",value.failed,"tiles",value.groups.size(),"boxes",value.scene==null?0:value.scene.boxes,"renderPasses",value.renderPasses,"activeTiles",value.groups.stream().filter(g->g.group.isActive()).count());}
    private void tick(MinecraftClient client){
        if(clientWorld!=client.world){release();clientWorld=client.world;}
        if(failed||client.world==null||client.getServer()==null||client.world.getRegistryKey()!=net.minecraft.world.World.OVERWORLD)return;
        try{
            if(binding==null){
                Path world=client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
                Path manifest=world.resolve("dreamwalker-dh-pilot/scene.json");
                if(!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS))return;
                binding=world;receipt=world.resolve("dreamwalker-dh-pilot/runtime-result.json");
                loading=CompletableFuture.supplyAsync(()->{try{return readScene(world,manifest);}catch(Exception e){throw new CompletionException(e);}});
            }
            if(scene==null){if(!loading.isDone())return;scene=loading.join();}
            if(DhApi.Delayed.worldProxy==null||DhApi.Delayed.customRenderObjectFactory==null||!DhApi.Delayed.worldProxy.worldLoaded())return;
            if(level==null){
                var candidate=DhApi.Delayed.worldProxy.getSinglePlayerLevel();
                if(candidate!=null&&candidate.getRenderRegister()!=null)level=candidate;
                if(level==null)return;
                if(scene.readOnly){priorReadOnly=DhApi.Delayed.worldProxy.getReadOnly();DhApi.Delayed.worldProxy.setReadOnly(true);readOnlyChanged=true;}
            }
            long started=System.nanoTime();int added=0;
            while(nextTile<scene.tiles.size()&&added<2&&(added==0||System.nanoTime()-started<4_000_000L)){
                Tile tile=scene.tiles.get(nextTile++);
                var group=DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup("dreamwalker:city_pilot/"+tile.x+"_"+tile.z,new DhApiVec3d(tile.x*16,0,tile.z*16),tile.boxes);
                group.setSkyLight(15);group.setBlockLight(0);group.setShading(DhApiRenderableBoxGroupShading.getDefaultShaded());
                group.setSsaoEnabled(false);group.setPreRenderFunc(param->{renderPasses++;});
                level.getRenderRegister().add(group);groups.add(new Installed(tile,group));added++;
            }
            int active=0;
            var camera=client.gameRenderer.getCamera().getPos();int cameraX=(int)Math.floor(camera.x/16),cameraZ=(int)Math.floor(camera.z/16);int nativeRadius=client.options.getViewDistance().getValue();
            for(Installed installed:groups){
                Tile tile=installed.tile;
                // ClientChunkMap retains a three-chunk delivery margin beyond
                // the render distance. Cached chunks in that margin still
                // need a distant proxy; presence alone would leave a gap.
                boolean withinNativeGrid=Math.max(Math.abs(tile.x-cameraX),Math.abs(tile.z-cameraZ))<nativeRadius;
                boolean distant=enabled&&(!withinNativeGrid||client.world.getChunkManager().getChunk(tile.x,tile.z,ChunkStatus.FULL,false)==null);
                installed.group.setActive(distant);if(distant)active++;
            }
            if(nextTile==scene.tiles.size()&&!registered){registered=true;LOG.info("City DH pilot registered {} tiles / {} boxes without loading city chunks",groups.size(),scene.boxes);write("REGISTERED",active);}
            else if(++ticks%100==0)write(registered?"ACTIVE":"REGISTERING",active);
        }catch(Exception failure){failed=true;LOG.error("City DH pilot disabled for this world",failure);writeFailure(failure);releaseGroups();restoreReadOnly();}
    }
    private static Scene readScene(Path world,Path manifest)throws Exception{
        if(Files.size(manifest)>65536)throw new IOException("Scene manifest limit");
        JsonObject doc=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        if(doc.get("schema").getAsInt()!=1||!Path.of(doc.get("worldDirectory").getAsString()).toAbsolutePath().normalize().equals(world))throw new IOException("Scene belongs to another world");
        Path data=manifest.getParent().resolve("city-boxes.bin.gz");
        if(!Files.isRegularFile(data,LinkOption.NOFOLLOW_LINKS)||Files.size(data)>16_000_000)throw new IOException("Scene file limit");
        byte[] compressed=Files.readAllBytes(data);String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compressed));
        if(!sha.equals(doc.get("dataSha256").getAsString()))throw new IOException("Scene hash mismatch");
        try(InputStream catalog=CityDhPilot.class.getResourceAsStream("/bloodborne_dw/static-architecture-v1274.json")){
            if(catalog==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(catalog.readAllBytes())).equals(doc.get("catalogSha256").getAsString()))throw new IOException("Scene uses a different architecture catalog");
        }
        byte[] raw;try(var gzip=new GZIPInputStream(new ByteArrayInputStream(compressed))){raw=gzip.readNBytes(16_000_001);}
        if(raw.length>16_000_000)throw new IOException("Expanded scene limit");
        List<Tile> tiles=new ArrayList<>();Set<Long> seen=new HashSet<>();int total=0;
        try(var in=new DataInputStream(new ByteArrayInputStream(raw))){
            if(in.readInt()!=0x44574331)throw new IOException("Scene magic");int count=in.readInt();if(count<1||count>256)throw new IOException("Tile count limit");
            for(int i=0;i<count;i++){
                int x=in.readInt(),z=in.readInt(),n=in.readInt();if(Math.abs(x)>1_875_000||Math.abs(z)>1_875_000||n<0||n>32768||!seen.add(((long)x<<32)^(z&0xffffffffL)))throw new IOException("Invalid tile");
                total+=n;if(total>250000)throw new IOException("Box count limit");List<DhApiRenderableBox> boxes=new ArrayList<>();
                for(int j=0;j<n;j++){
                    double a=in.readFloat(),b=in.readFloat(),c=in.readFloat(),d=in.readFloat(),e=in.readFloat(),f=in.readFloat();int color=in.readInt();
                    if(!(a>=0&&a<d&&d<=16&&c>=0&&c<f&&f<=16&&b>=-64&&b<e&&e<=320))throw new IOException("Invalid box bounds");
                    boxes.add(new DhApiRenderableBox(new DhApiVec3d(a,b,c),new DhApiVec3d(d,e,f),new Color(color,true),EDhApiBlockMaterial.STONE));
                }
                tiles.add(new Tile(x,z,Collections.unmodifiableList(boxes)));
            }
            if(in.available()!=0)throw new IOException("Unexpected scene tail");
        }
        return new Scene(List.copyOf(tiles),total,sha,doc.has("readOnlyDhTerrain")&&doc.get("readOnlyDhTerrain").getAsBoolean());
    }
    private void write(String status,int active){
        if(receipt==null)return;JsonObject out=new JsonObject();out.addProperty("status",status);out.addProperty("enabled",enabled);out.addProperty("tiles",groups.size());out.addProperty("activeDistantTiles",active);out.addProperty("boxes",scene==null?0:scene.boxes);out.addProperty("renderPasses",renderPasses);out.addProperty("minecraftChunksRequested",0);out.addProperty("dataSha256",scene==null?"":scene.sha);out.addProperty("readOnlyDhTerrain",scene!=null&&scene.readOnly);
        try{Files.writeString(receipt,JSON.toJson(out));}catch(IOException e){LOG.warn("Cannot write pilot receipt",e);}
    }
    private void writeFailure(Exception failure){if(receipt==null)return;try{Files.writeString(receipt,JSON.toJson(Map.of("status","FAIL","error",failure.toString())));}catch(IOException ignored){}}
    private void releaseGroups(){for(Installed installed:groups)try{if(level!=null&&level.getRenderRegister()!=null)level.getRenderRegister().remove(installed.group.getId());}catch(RuntimeException ignored){}groups.clear();}
    private void restoreReadOnly(){if(readOnlyChanged&&DhApi.Delayed.worldProxy!=null&&DhApi.Delayed.worldProxy.worldLoaded())try{DhApi.Delayed.worldProxy.setReadOnly(priorReadOnly);}catch(RuntimeException ignored){}readOnlyChanged=false;}
    private void release(){
        if(registered)write("WORLD_UNLOADED",0);releaseGroups();
        restoreReadOnly();
        if(loading!=null)loading.cancel(false);binding=null;receipt=null;loading=null;scene=null;level=null;nextTile=0;ticks=0;renderPasses=0;failed=false;registered=false;readOnlyChanged=false;enabled=true;
    }
}
