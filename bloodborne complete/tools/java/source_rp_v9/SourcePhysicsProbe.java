package dev.dreamwalker.qa.source;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Isolated QA-only Forge mod. Never included in the Fabric product JAR. */
@Mod("dreamwalker_source_probe")
public final class SourcePhysicsProbe {
    public SourcePhysicsProbe() { MinecraftForge.EVENT_BUS.register(this); System.out.println("Dreamwalker source QA event listener registered"); }
    private JsonObject methods;
    private boolean autoOpened;
    private int clientTitleTicks;
    private int clientTicks;
    private String lastScreen="";
    private String name(String key) { return methods.get(key).getAsString(); }
    private static Object invoke(Object receiver, String name, Class<?>[] types, Object... args) throws Exception {
        return receiver.getClass().getMethod(name, types).invoke(receiver, args);
    }
    private static double coordinate(Object entity,String method) throws Exception {
        return ((Number)entity.getClass().getMethod(method).invoke(entity)).doubleValue();
    }
    /** Standard world-open API in an isolated QA client, without simulated UI input. */
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event) {
        if(autoOpened||event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("dreamwalker.source.autoOpen"))return;
        try {
            JsonObject input=JsonParser.parseString(Files.readString(Path.of("probe-input.json"))).getAsJsonObject();
            JsonObject client=input.getAsJsonObject("clientMethods");
            Class<?> type=Class.forName("net.minecraft.client.Minecraft");
            Object minecraft=type.getMethod(client.get("getInstance").getAsString()).invoke(null);
            Object screen=type.getField(client.get("screen").getAsString()).get(minecraft);
            String screenName=screen==null?"null":screen.getClass().getName();
            clientTicks++;
            if(clientTicks==1||!screenName.equals(lastScreen)) {
                lastScreen=screenName;
                JsonObject diagnostic=new JsonObject();diagnostic.addProperty("screen_class",screenName);
                diagnostic.addProperty("client_ticks",clientTicks);diagnostic.addProperty("status","WAITING_FOR_STANDARD_TITLE_SCREEN");
                Files.writeString(Path.of("source-client-state.json"),new GsonBuilder().setPrettyPrinting().create().toJson(diagnostic));
                System.out.println("Dreamwalker source QA client screen: "+screenName+"; ticks="+clientTicks);
            }
            if(screen==null||!screenName.equals("net.minecraft.client.gui.screens.TitleScreen"))return;
            if(++clientTitleTicks<40)return;
            autoOpened=true;
            type.getMethod(client.get("loadLevel").getAsString(),String.class).invoke(minecraft,"source-reference-world");
            System.out.println("Dreamwalker source QA invoked standard loadLevel on isolated copied world");
        }catch(Throwable error){autoOpened=true;error.printStackTrace();}
    }
    private Object pendingServer;
    private int pendingTicks;
    @SubscribeEvent public void started(ServerStartedEvent event) {
        try {
            JsonObject input=JsonParser.parseString(Files.readString(Path.of("probe-input.json"))).getAsJsonObject();
            if(input.has("rpEntityProbe")) {
                methods=input.getAsJsonObject("methods");pendingServer=event.getServer();
                Object world=invoke(pendingServer,name("overworld"),new Class<?>[0]);
                Class<?> posType=Class.forName("net.minecraft.core.BlockPos");
                JsonArray source=input.getAsJsonObject("rpEntityProbe").getAsJsonArray("sourcePos");
                int x=(int)Math.floor(source.get(0).getAsDouble()),z=(int)Math.floor(source.get(2).getAsDouble());
                for(int cx=Math.floorDiv(x-24,16);cx<=Math.floorDiv(x+24,16);cx++)for(int cz=Math.floorDiv(z-24,16);cz<=Math.floorDiv(z+24,16);cz++)invoke(world,name("getBlockState"),new Class<?>[]{posType},posType.getConstructor(int.class,int.class,int.class).newInstance(cx*16,48,cz*16));
                System.out.println("Source RP probe loaded original entity chunks; waiting80 ticks for entity manager");return;
            }
            measure(event.getServer());
        }catch(Throwable error){error.printStackTrace();}
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if(pendingServer==null||event.phase!=TickEvent.Phase.END||++pendingTicks<80)return;
        Object server=pendingServer;pendingServer=null;measure(server);
    }
    private void measure(Object server) {
        JsonObject output = new JsonObject();
        output.addProperty("schema", "dreamwalker-original-forge-physics-v1");
        output.addProperty("manual_gameplay", "NOT_RUN");
        output.addProperty("runtime_mode",System.getProperty("dreamwalker.source.mode","dedicated"));
        output.addProperty("source", "Actual Forge1.18.2 with original Bloodborne6.0 and GeckoLib3.0.57; immutable-source world copy");
        try {
            JsonObject input = JsonParser.parseString(Files.readString(Path.of("probe-input.json"))).getAsJsonObject();
            methods = input.getAsJsonObject("methods");
            Object world = invoke(server, name("overworld"), new Class<?>[0]);
            Class<?> positionType=Class.forName("net.minecraft.core.BlockPos");
            Class<?> getterType=Class.forName("net.minecraft.world.level.BlockGetter");
            Class<?> contextType=Class.forName("net.minecraft.world.phys.shapes.CollisionContext");
            Class<?> entityType=Class.forName("net.minecraft.world.entity.Entity");
            Class<?> serverLevelType=Class.forName("net.minecraft.server.level.ServerLevel");
            Object player=Class.forName("net.minecraftforge.common.util.FakePlayerFactory")
                .getMethod("get",serverLevelType,GameProfile.class).invoke(null,world,
                    new GameProfile(UUID.fromString("c969bc29-7ff4-49f6-a4f9-3657ed6885ce"),"SourcePhysicsQA"));
            Object context=contextType.getMethod(name("contextOf"),entityType).invoke(null,player);
            JsonArray shapes = new JsonArray();
            for (JsonElement element : input.getAsJsonArray("cells")) {
                JsonObject sample=element.getAsJsonObject();JsonArray p=sample.getAsJsonArray("pos");
                Object pos=positionType.getConstructor(int.class,int.class,int.class).newInstance(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                Object state=invoke(world,name("getBlockState"),new Class<?>[]{positionType},pos);
                JsonObject row=sample.deepCopy();row.addProperty("actual_state",state.toString());
                row.addProperty("lightEmission",((Number)invoke(state,name("lightEmission"),new Class<?>[0])).intValue());
                row.addProperty("isAir",(Boolean)invoke(state,name("isAir"),new Class<?>[0]));
                row.addProperty("actualBlockClass",invoke(state,name("getBlock"),new Class<?>[0]).getClass().getName());
                Object material=invoke(state,name("getMaterial"),new Class<?>[0]);row.addProperty("replaceable",(Boolean)invoke(material,name("materialReplaceable"),new Class<?>[0]));
                for (String kind : List.of("collisionShape","outlineShape")) {
                    Object shape=invoke(state,name(kind),new Class<?>[]{getterType,positionType,contextType},world,pos,context);
                    List<?> boxes=(List<?>)invoke(shape,name("toAabbs"),new Class<?>[0]);JsonArray values=new JsonArray();
                    for (Object box : boxes) {
                        JsonArray value=new JsonArray();
                        for (JsonElement field : methods.getAsJsonArray("boxFields")) value.add(box.getClass().getField(field.getAsString()).getDouble(box));
                        values.add(value);
                    }
                    row.add(kind,values);
                }
                shapes.add(row);
            }
            output.add("actual_shapes",shapes);
            Class<?> moverType=Class.forName("net.minecraft.world.entity.MoverType");
            Class<?> vecType=Class.forName("net.minecraft.world.phys.Vec3");
            Object self=moverType.getField(name("moverSelf")).get(null);
            JsonArray movements=new JsonArray();
            for(JsonElement element:input.getAsJsonArray("movements")) {
                JsonObject sample=element.getAsJsonObject();JsonArray p=sample.getAsJsonArray("start"),d=sample.getAsJsonArray("delta");
                double x=p.get(0).getAsDouble(),y=p.get(1).getAsDouble(),z=p.get(2).getAsDouble();
                // Entity collision iteration ignores unavailable chunks. Force the entire swept path
                // and a1-block margin to load before measuring, including the adjacent window brick.
                double dx=d.get(0).getAsDouble(),dz=d.get(2).getAsDouble();
                for(int cx=(int)Math.floor((Math.min(x,x+dx)-1)/16);cx<=(int)Math.floor((Math.max(x,x+dx)+1)/16);cx++)
                    for(int cz=(int)Math.floor((Math.min(z,z+dz)-1)/16);cz<=(int)Math.floor((Math.max(z,z+dz)+1)/16);cz++){
                        Object load=positionType.getConstructor(int.class,int.class,int.class).newInstance(cx*16,(int)Math.floor(y),cz*16);
                        invoke(world,name("getBlockState"),new Class<?>[]{positionType},load);
                    }
                invoke(player,name("setPos"),new Class<?>[]{double.class,double.class,double.class},x,y,z);
                Object vector=vecType.getConstructor(double.class,double.class,double.class).newInstance(d.get(0).getAsDouble(),d.get(1).getAsDouble(),d.get(2).getAsDouble());
                invoke(player,name("move"),new Class<?>[]{moverType,vecType},self,vector);
                double[] actual={coordinate(player,name("getX"))-x,coordinate(player,name("getY"))-y,coordinate(player,name("getZ"))-z};
                JsonObject row=sample.deepCopy();JsonArray displacement=new JsonArray();boolean full=true;
                for(int axis=0;axis<3;axis++){displacement.add(actual[axis]);full&=Math.abs(actual[axis]-d.get(axis).getAsDouble())<1e-5;}
                row.add("actual_displacement",displacement);row.addProperty("unobstructed",full);movements.add(row);
                row.addProperty("allSweptMovementChunksPreloaded",true);
            }
            if(input.has("rpEntityProbe")) {
                JsonObject probe=input.getAsJsonObject("rpEntityProbe");UUID uuid=UUID.fromString(probe.get("uuid").getAsString());
                Object gate=invoke(world,name("getEntityByUuid"),new Class<?>[]{UUID.class},uuid);
                if(gate==null||!gate.getClass().getName().equals("com.potomy.bloodborne.common.entity.decorations.WoodenGateEntity"))throw new IllegalStateException("Original woodgate exactUUID not loaded: "+uuid);
                double ox=coordinate(gate,name("getX")),oy=coordinate(gate,name("getY")),oz=coordinate(gate,name("getZ"));float originalYaw=((Number)invoke(gate,name("getYaw"),new Class<?>[0])).floatValue();
                JsonObject evidence=new JsonObject();evidence.addProperty("uuid",uuid.toString());evidence.addProperty("actualClass",gate.getClass().getName());evidence.addProperty("canBeCollidedWith",(Boolean)invoke(gate,name("entityCollidable"),new Class<?>[0]));evidence.addProperty("actualScale",((Number)invoke(gate,"getScale",new Class<?>[0])).floatValue());
                JsonArray originalPos=new JsonArray();originalPos.add(ox);originalPos.add(oy);originalPos.add(oz);evidence.add("originalPos",originalPos);evidence.addProperty("originalYaw",originalYaw);
                JsonArray box=new JsonArray();Object actualBox=invoke(gate,name("getEntityBox"),new Class<?>[0]);for(JsonElement f:methods.getAsJsonArray("boxFields"))box.add(actualBox.getClass().getField(f.getAsString()).getDouble(actualBox));evidence.add("originalRegisteredBox",box);
                JsonArray cases=new JsonArray();
                try {
                    for(double baseY:new double[]{oy,200})for(float yaw:new float[]{originalYaw,originalYaw+90})for(int direction:new int[]{-1,1}) {
                        invoke(gate,name("setPos"),new Class<?>[]{double.class,double.class,double.class},ox,baseY,oz);invoke(gate,name("setYaw"),new Class<?>[]{float.class},yaw);
                        boolean xAxis=Math.abs(Math.sin(Math.toRadians(yaw)))<.5;double dx=xAxis?20*direction:0,dz=xAxis?0:20*direction;double x=ox-dx/2,y=baseY+1.05,z=oz-dz/2;
                        double[] with=measureMove(player,self,moverType,vecType,x,y,z,dx,dz);
                        invoke(gate,name("setPos"),new Class<?>[]{double.class,double.class,double.class},ox,baseY+96,oz);
                        double[] control=measureMove(player,self,moverType,vecType,x,y,z,dx,dz);
                        JsonObject row=new JsonObject();row.addProperty("pose",baseY==oy?"ORIGINAL_POSITION":"ISOLATED_AIR_VERTICAL_TRANSLATION_SAME_ENTITY");row.addProperty("baseY",baseY);row.addProperty("yaw",yaw);row.addProperty("direction",direction);JsonArray start=new JsonArray(),delta=new JsonArray(),actual=new JsonArray(),without=new JsonArray();for(double n:new double[]{x,y,z})start.add(n);for(double n:new double[]{dx,0,dz})delta.add(n);for(double n:with)actual.add(n);for(double n:control)without.add(n);row.add("start",start);row.add("requestedDelta",delta);row.add("withGate",actual);row.add("nativeControlWithoutGate",without);boolean same=true;for(int a=0;a<3;a++)same&=Math.abs(with[a]-control[a])<1e-5;row.addProperty("sameDisplacementWithAndWithoutGate",same);row.addProperty("crossedGatePlane",Math.abs(xAxis?with[0]:with[2])>10.3);cases.add(row);
                    }
                } finally {invoke(gate,name("setPos"),new Class<?>[]{double.class,double.class,double.class},ox,oy,oz);invoke(gate,name("setYaw"),new Class<?>[]{float.class},originalYaw);}
                evidence.add("actualMovements",cases);evidence.addProperty("originalPositionYawRestored",true);evidence.addProperty("sourceModPatched",false);evidence.addProperty("selectionBoxIsNotPhysicsProof",true);output.add("woodGateActualSource",evidence);
            }
            output.add("actual_fake_player_movements",movements);output.addProperty("status","MEASURED_ACTUAL_SOURCE_SERVER");
            output.addProperty("limitation","Fake-player physical movement and source shape queries; not manual keyboard, visual comparison or all source modpack interactions.");
        } catch (Throwable error) {
            output.addProperty("status","FAIL_PROBE");output.addProperty("error",error.toString());error.printStackTrace();
        }
        try { Files.writeString(Path.of("source-physics-output.json"),new GsonBuilder().setPrettyPrinting().create().toJson(output)); }
        catch(Exception error){throw new RuntimeException(error);}
        System.out.println("Dreamwalker original source physics probe written: "+output.get("status"));
        if(Boolean.getBoolean("dreamwalker.source.autoStop")) {
            try {
                JsonObject input=JsonParser.parseString(Files.readString(Path.of("probe-input.json"))).getAsJsonObject();
                JsonObject client=input.getAsJsonObject("clientMethods");
                Class<?> type=Class.forName("net.minecraft.client.Minecraft");
                Object minecraft=type.getMethod(client.get("getInstance").getAsString()).invoke(null);
                type.getMethod(client.get("stop").getAsString()).invoke(minecraft);
                System.out.println("Dreamwalker source QA requested standard Minecraft.stop after measurement");
            } catch(Throwable error){error.printStackTrace();}
        }
    }
    private double[] measureMove(Object player,Object self,Class<?> moverType,Class<?> vecType,double x,double y,double z,double dx,double dz)throws Exception{
        invoke(player,name("setPos"),new Class<?>[]{double.class,double.class,double.class},x,y,z);Object vector=vecType.getConstructor(double.class,double.class,double.class).newInstance(dx,0,dz);invoke(player,name("move"),new Class<?>[]{moverType,vecType},self,vector);return new double[]{coordinate(player,name("getX"))-x,coordinate(player,name("getY"))-y,coordinate(player,name("getZ"))-z};
    }
}
