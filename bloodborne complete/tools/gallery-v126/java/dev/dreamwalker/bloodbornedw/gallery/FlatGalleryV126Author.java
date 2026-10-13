package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.*;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;

/** Explicit-marker QA author. This class is never included in production. */
public final class FlatGalleryV126Author implements ModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path INPUT=Path.of("catalogue-gallery-input.json"),OUTPUT=Path.of("catalogue-gallery-output.json"),PROGRESS=Path.of("flat-gallery-progress.json");
    private static final int FLOOR=63,GAP=2,MARGIN=1,COLUMNS=16;
    private static final BlockPos VIRTUAL=new BlockPos(0,64,0);
    private static final Map<MinecraftServer,Author> AUTHORS=new WeakHashMap<>();
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("dreamwalker_flat_gallery_v126",Comparator.comparingLong(ChunkPos::toLong));
    private record Exhibit(JsonObject source,ItemStack item,BlockState state,Box local){}
    private record Station(Exhibit exhibit,String grid,int row,int column,int x,int z,int width,int depth,BlockPos root,Vec3d origin,Box visual){}
    private record Review(JsonObject source,int x,int z,int width,int depth,Vec3d translation,Box visual){}
    private static final class Author {
        final MinecraftServer server;final ServerWorld world;final ServerPlayerEntity actor;
        final JsonObject report=new JsonObject();final JsonArray rows=new JsonArray();
        final List<Station> stations=new ArrayList<>();final Set<ChunkPos> tickets=new HashSet<>();
        final List<Review> reviews=new ArrayList<>();final JsonArray reviewRows=new JsonArray(),cityRows=new JsonArray();
        final JsonObject city;final List<JsonObject> cityInput=new ArrayList<>();Review cityPlan;
        int index,reviewIndex,cityIndex,age,width,depth,shelfX,shelfZ,shelfDepth;boolean settling,cityStarted;
        Author(MinecraftServer server,JsonObject marker)throws Exception{
            this.server=server;world=server.getOverworld();city=marker.getAsJsonObject("city").deepCopy();
            actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:FlatGalleryV126Author".getBytes(StandardCharsets.UTF_8)),"GalleryV126Author")){
                @Override public boolean hasPermissionLevel(int level){return level<=4;}};
            actor.networkHandler=new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND){
                @Override public void send(Packet<?> packet){}
                @Override public void send(Packet<?> packet,PacketCallbacks callbacks){}
            },actor);
            actor.changeGameMode(GameMode.CREATIVE);actor.getAbilities().allowModifyWorld=true;
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);world.setTimeOfDay(6000);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,server);
            world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false,server);
            world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0,server);
            world.getGameRules().get(GameRules.DO_TILE_DROPS).set(false,server);
            world.setSpawnPos(new BlockPos(0,64,-6),0);server.setDefaultGameMode(GameMode.CREATIVE);
            report.addProperty("schema","dreamwalker-flat-gallery-v126");report.addProperty("status","RUNNING");
            report.addProperty("productionJarSha256",marker.get("productionJarSha256").getAsString());
            report.addProperty("platformFloorY",FLOOR);report.addProperty("platformGap",GAP);report.addProperty("platformMargin",MARGIN);
            report.addProperty("columns",COLUMNS);report.addProperty("qaRequiredForSavedWorld",false);
            report.addProperty("platformSizingPolicy","ceil(active rendered X/Z extent)+2 cells, in separate uniform-size grids");
            report.addProperty("surfacePolicy","active rendered minimum Y equals 64 exactly; zero-Y-thickness planes only use gallery-only 1/1024 anti-z-fighting clearance; persistent existing mount payload / entity Pos");
            report.add("catalogue",marker.get("catalogue").deepCopy());report.add("exhibits",rows);
            report.add("removedRedExhibits",marker.get("removedRedExhibits").deepCopy());
            report.add("styleRetirements",marker.get("styleRetirements").deepCopy());report.add("reviewPlatforms",reviewRows);
            List<Exhibit> offered=new ArrayList<>();Set<String> keys=new HashSet<>(),buildingItems=new HashSet<>();
            JsonObject visuals=marker.getAsJsonObject("localVisualBounds");
            for(JsonElement element:marker.getAsJsonObject("catalogue").getAsJsonArray("exhibits")){
                JsonObject input=element.getAsJsonObject();require(keys.add(input.get("key").getAsString()),"Duplicate exhibit key");
                require(!input.get("section").getAsString().equals("error"),"Red/error platforms are removed");
                Identifier itemId=new Identifier(input.get("itemId").getAsString());require(Registries.ITEM.containsId(itemId),"Missing offered item: "+itemId);
                ItemStack item=new ItemStack(Registries.ITEM.get(itemId));buildingItems.add(itemId.toString());
                for(var entry:input.getAsJsonObject("blockStateTag").entrySet())item.getOrCreateSubNbt("BlockStateTag").putString(entry.getKey(),entry.getValue().getAsString());
                BlockState state=null;Box local;
                if(input.get("kind").getAsString().equals("architecture")){
                    require(item.getItem() instanceof BlockItem,"Architecture is not BlockItem");
                    state=((BlockItem)item.getItem()).getBlock().getDefaultState();
                    for(var entry:input.getAsJsonObject("blockStateTag").entrySet())state=property(state,entry.getKey(),entry.getValue().getAsString());
                    if(state.getBlock() instanceof PrototypeLadderBlock)state=PrototypeLadderBlock.withYaw(state.with(PrototypeLadderBlock.FREESTANDING,true),0);
                    local=readBox(visuals.getAsJsonArray(input.get("key").getAsString()));
                }else{
                    require(input.get("kind").getAsString().equals("rp_object"),"Unsupported placeable kind");
                    var type=ObjectRegistry.TYPES.get(new Identifier(input.get("registryId").getAsString()).getPath());require(type!=null,"Missing registered RP object");
                    RpObjectEntity object=type.create(world);object.refreshPositionAndAngles(0,64,0,0,0);
                    local=actualRpVisual(object).offset(0,-64,0);input.addProperty("open",object.isOpen());
                    input.add("localVisualBounds",box(local));input.addProperty("visualGeometryEvidence","RpObjectGeometry.ordinaryVisualBoxes: current rendered Gecko cube bounds, no motion/physics/query envelope");
                }
                offered.add(new Exhibit(input,item,state,local));
            }
            JsonObject ru=marker.getAsJsonObject("russianItemNames");Set<String> framed=new HashSet<>();List<ItemStack> creative=UnifiedCreativeCatalogue.canonicalItems();
            for(ItemStack item:creative){
                String id=Registries.ITEM.getId(item.getItem()).toString();if(buildingItems.contains(id)||!framed.add(id))continue;
                require(!(item.getItem() instanceof BlockItem)&&!ObjectRegistry.isPlacementItem(item),"Uncatalogued placeable creative object: "+id);
                var entry=DebugCatalogue.entry(item);String key="framed:"+id;require(keys.add(key),"Duplicate framed item");
                String name=entry==null?(ru.has(item.getTranslationKey())?ru.get(item.getTranslationKey()).getAsString():item.getName().getString()):entry.name();
                JsonObject source=new JsonObject();source.addProperty("key",key);source.addProperty("section","items");source.addProperty("origin","V12");source.addProperty("kind","item_frame");
                source.addProperty("id",entry==null?"":entry.temporaryId());source.addProperty("numericId",entry==null?"":entry.temporaryId());
                source.addProperty("registry",id);source.addProperty("registryId",id);source.addProperty("item",id);source.addProperty("itemId",id);source.addProperty("name",name);
                source.addProperty("variant",0);source.addProperty("profile","base");source.addProperty("open",false);source.add("blockStateTag",new JsonObject());
                source.add("state",new JsonObject());source.add("sourceIssues",new JsonArray());source.addProperty("status","OFFERED_CREATIVE_ITEM");
                source.addProperty("mapStatus","NOT_APPLICABLE_ITEM");source.add("sourceModels",new JsonArray());source.add("sourceParts",new JsonArray());
                source.addProperty("framePolicy","Held item only; spawn eggs never spawn a mob");
                Box local=new Box(0,0,-.0625,1,1,1);source.add("localVisualBounds",box(local));
                offered.add(new Exhibit(source,item.copy(),null,local));report.getAsJsonObject("catalogue").getAsJsonArray("exhibits").add(source.deepCopy());
            }
            require(creative.size()==offered.size(),"Prepared building catalogue and actual offered Creative variants differ: "+creative.size()+" / "+offered.size());
            JsonArray planar=new JsonArray();for(Exhibit e:offered)if(clearance(e.source)>0){require(Math.abs(e.local.maxY-e.local.minY)<1e-8,"Planar clearance allowed only for zero-thickness art");planar.add(e.source.get("key").getAsString());}
            report.add("planarExhibits",planar);report.addProperty("planarAntiZFightClearance",1d/1024);
            report.getAsJsonObject("catalogue").getAsJsonObject("summary").addProperty("framedCreativeItems",framed.size());
            JsonArray completeCatalogue=new JsonArray();for(Exhibit e:offered)completeCatalogue.add(e.source.deepCopy());report.getAsJsonObject("catalogue").add("exhibits",completeCatalogue);
            report.addProperty("oneMainStationPerNewWorkingType",true);report.addProperty("offeredCreativeStacks",creative.size());report.addProperty("framedCreativeItems",framed.size());plan(offered);
            for(JsonElement e:marker.getAsJsonArray("reviewPlatforms"))reviews.add(planReview(e.getAsJsonObject()));
            city.addProperty("key","CITY-SOURCE-CLIP");city.addProperty("section","city");cityPlan=planReview(city);
            for(JsonElement e:city.getAsJsonArray("owners"))cityInput.add(e.getAsJsonObject());
            cityInput.sort(Comparator.comparingInt(e->e.get("registryId").getAsString().startsWith("bloodborne_dw:source_")?1:0));
            report.addProperty("catalogueTypes",stations.size());report.addProperty("widthBlocks",width);report.addProperty("depthBlocks",depth);progress();
        }
        int[] reserve(int w,int d){
            if(shelfX>0&&shelfX+w>256){shelfZ+=shelfDepth+GAP;shelfX=0;shelfDepth=0;}
            int[] pos={shelfX,shelfZ};shelfX+=w+GAP;shelfDepth=Math.max(shelfDepth,d);width=Math.max(width,shelfX-GAP);depth=Math.max(depth,shelfZ+shelfDepth);return pos;
        }
        void plan(List<Exhibit> exhibits){
            LinkedHashMap<String,List<Exhibit>> grids=new LinkedHashMap<>();
            for(Exhibit exhibit:exhibits){Box b=exhibit.local;int w=ceiling(b.maxX-b.minX)+2,d=ceiling(b.maxZ-b.minZ)+2;
                String key=exhibit.source.get("section").getAsString()+"/"+w+"x"+d;grids.computeIfAbsent(key,k->new ArrayList<>()).add(exhibit);}
            JsonArray layout=new JsonArray();
            for(var group:grids.entrySet()){
                List<Exhibit> values=group.getValue();Box first=values.get(0).local;
                int w=ceiling(first.maxX-first.minX)+2,d=ceiling(first.maxZ-first.minZ)+2,cols=Math.max(1,Math.min(Math.min(COLUMNS,values.size()),Math.min(256/(w+GAP),(int)Math.ceil(Math.sqrt(values.size()*(d+2d)/(w+2d)))))),count=(values.size()+cols-1)/cols;
                int[] start=reserve(cols*w+(cols-1)*GAP,count*d+(count-1)*GAP);
                JsonObject meta=new JsonObject();meta.addProperty("gridId",group.getKey());meta.addProperty("columns",cols);meta.addProperty("rows",count);meta.addProperty("width",w);meta.addProperty("depth",d);meta.addProperty("startX",start[0]);meta.addProperty("startZ",start[1]);layout.add(meta);
                for(int i=0;i<values.size();i++){
                    Exhibit e=values.get(i);Box b=e.local;int r=i/cols,c=i%cols,x=start[0]+c*(w+GAP),z=start[1]+r*(d+GAP);
                    double surface=64+clearance(e.source);
                    Vec3d origin=new Vec3d(x+1+((w-2)-(b.maxX-b.minX))/2-b.minX,surface-b.minY,z+1+((d-2)-(b.maxZ-b.minZ))/2-b.minZ);
                    BlockPos root=new BlockPos(MathHelper.clamp(floor(origin.x),x,x+w-1),64,MathHelper.clamp(floor(origin.z),z,z+d-1));
                    if(e.source.has("surfaceTileReplacesPlatformCell")&&e.source.get("surfaceTileReplacesPlatformCell").getAsBoolean())root=root.withY(63);
                    if(e.state!=null&&!(e.state.getBlock() instanceof CompositeRootBlock)){
                        // Native V12 geometry supports only its existing vertical offset.
                        root=new BlockPos(floor(origin.x+.5),64,floor(origin.z+.5));origin=new Vec3d(root.getX(),origin.y,root.getZ());
                    }
                    if(e.source.get("kind").getAsString().equals("item_frame")){root=new BlockPos(x+1,64,z+1);origin=Vec3d.of(root);}
                    Box visual=b.offset(origin);require(Math.abs(visual.minY-surface)<1e-7,"Floating/buried rendered object: "+e.source.get("key"));
                    require(visual.minX>=x-1e-7&&visual.maxX<=x+w+1e-7&&visual.minZ>=z-1e-7&&visual.maxZ<=z+d+1e-7,"Rendered object escapes its footprint");
                    stations.add(new Station(e,group.getKey(),r,c,x,z,w,d,root,origin,visual));
                }
            }
            report.add("grids",layout);report.addProperty("layoutPolicy","Compact two-dimensional shelf of near-square uniform-size grids; all platform edges separated by at least2cells");
        }
        Review planReview(JsonObject source){Box b=readBox(source.getAsJsonArray("localVisualBounds"));int w=ceiling(b.maxX-b.minX)+2,d=ceiling(b.maxZ-b.minZ)+2;int[] pos=reserve(w,d);
            Vec3d t=new Vec3d(pos[0]+1+((w-2)-(b.maxX-b.minX))/2-b.minX,64-b.minY,pos[1]+1+((d-2)-(b.maxZ-b.minZ))/2-b.minZ);
            return new Review(source.deepCopy(),pos[0],pos[1],w,d,t,b.offset(t));}
        void paintFloor(Review r,Block block){load(new Box(r.x,63,r.z,r.x+r.width,r.visual.maxY+2,r.z+r.depth).expand(4));
            for(int x=r.x;x<r.x+r.width;x++)for(int z=r.z;z<r.z+r.depth;z++)world.setBlockState(new BlockPos(x,63,z),block.getDefaultState(),Block.NOTIFY_LISTENERS);}
        JsonObject component(JsonObject input,Vec3d t,String namespace){
            JsonObject row=input.deepCopy();JsonArray relative=input.getAsJsonArray("relativePosition");
            BlockPos root=new BlockPos(floor(t.x)+relative.get(0).getAsInt(),floor(t.y)+relative.get(1).getAsInt(),floor(t.z)+relative.get(2).getAsInt());
            Identifier id=new Identifier(input.get("registryId").getAsString());require(Registries.BLOCK.containsId(id),"Missing conversion block "+id);BlockState state=Registries.BLOCK.get(id).getDefaultState();
            for(var e:input.getAsJsonObject("properties").entrySet())state=property(state,e.getKey(),e.getValue().getAsString());
            NbtCompound payload=new NbtCompound();if(input.has("payload"))try{payload=net.minecraft.nbt.StringNbtReader.parse(JSON.toJson(input.get("payload")));}catch(Exception e){throw new IllegalStateException(e);}
            int yaw=state.contains(CompositeRootBlock.ROTATION)?state.get(CompositeRootBlock.ROTATION):0;
            JsonArray shift=input.has("sourceShift")?input.getAsJsonArray("sourceShift"):new JsonArray();double sx=shift.size()==3?shift.get(0).getAsDouble():0,sy=shift.size()==3?shift.get(1).getAsDouble():0,sz=shift.size()==3?shift.get(2).getAsDouble():0;
            var fractional=new CompositeSourceShift.Shift(t.x-floor(t.x),0,t.z-floor(t.z)).rotate(-yaw);
            var localShift=new net.minecraft.nbt.NbtList();for(double v:new double[]{sx+fractional.x(),sy,sz+fractional.z()})localShift.add(net.minecraft.nbt.NbtDouble.of(v));payload.put("SourceShift",localShift);
            payload.putDouble(VerticalMount.KEY,input.has("mountY")?input.get("mountY").getAsDouble()+t.y-floor(t.y):t.y-floor(t.y));
            UUID uuid=input.has("instanceUuid")&&namespace.equals("city")?UUID.fromString(input.get("instanceUuid").getAsString()):UUID.nameUUIDFromBytes((namespace+":"+input.get("registryId")+":"+relative).getBytes(StandardCharsets.UTF_8));
            actor.setPosition(root.getX()+.5,root.getY(),root.getZ()-3);actor.setSneaking(true);
            if(state.getBlock() instanceof CompositeRootBlock){payload.putInt(ImportedTypeAliases.VERSION_KEY,1);final BlockState selected=state;final NbtCompound supplied=payload;
                var result=SourceConversionScope.initialInstances(Set.of(uuid),()->CompositeRuntime.place(world,root,selected,uuid,actor,supplied));
                require(result.outcome()==TransactionCore.Outcome.COMMITTED,"Source component refused "+id+" "+root+" "+result);
            }else{require(VerticalMount.isNative(state),"Conversion target is not architecture");require(fractional.x()==0&&fractional.z()==0&&sx==0&&sy==0&&sz==0,"Native source state cannot receive horizontal art shifts");
                world.setBlockState(root,state,Block.FORCE_STATE|Block.SKIP_DROPS);VerticalMount.placed(world,root,state);var nativeOwner=(CompositeBlockEntity)world.getBlockEntity(root);
                require(nativeOwner!=null,"Native source owner missing");nativeOwner.set(new dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner(uuid,id.toString(),CompositeData.cell(root)),List.of(),new NbtCompound());require(VerticalMount.setOffset(world,root,payload.getDouble(VerticalMount.KEY),actor),"Native source offset refused");}
            var own=(CompositeBlockEntity)world.getBlockEntity(root);require(own!=null&&own.resident()!=null,"Converted component resident missing");
            row.addProperty("uuid",uuid.toString());row.add("root",JSON.toJsonTree(List.of(root.getX(),root.getY(),root.getZ())));row.addProperty("registry",id.toString());
            JsonObject props=new JsonObject();for(var e:world.getBlockState(root).getEntries().entrySet())props.addProperty(e.getKey().getName(),propertyName(e.getKey(),e.getValue()));row.add("placedState",props);
            Box visual=readBox(input.getAsJsonArray("relativeVisualBounds")).offset(t);row.add("bounds",box(visual));row.addProperty("savedPayload",own.payload().toString());return row;
        }
        void placeReview(Review r){paintFloor(r,r.source.get("section").getAsString().equals("doors")?Blocks.PURPLE_CONCRETE:Blocks.MAGENTA_CONCRETE);
            JsonObject row=r.source.deepCopy();JsonArray components=new JsonArray();for(JsonElement c:r.source.getAsJsonArray("components"))components.add(component(c.getAsJsonObject(),r.translation,r.source.get("key").getAsString()));row.add("components",components);
            row.add("platform",JSON.toJsonTree(List.of(r.x,r.z,r.width,r.depth)));row.add("bounds",box(r.visual));row.add("translation",JSON.toJsonTree(List.of(r.translation.x,r.translation.y,r.translation.z)));
            BlockPos label=findCorner(r);label(label,row.get("russianLabel").getAsString(),row.get("primaryId").getAsString(),row.get("section").getAsString(),row.has("open")&&row.get("open").getAsBoolean()?"Открыто на 90°":"Цельный объект");row.add("labelPos",JSON.toJsonTree(List.of(label.getX(),label.getY(),label.getZ())));
            row.addProperty("registrationsMerged",true);row.addProperty("doorPropertiesChanged",row.get("section").getAsString().equals("doors"));reviewRows.add(row);
        }
        BlockPos findCorner(Review r){for(int[] c:new int[][]{{r.x,r.z},{r.x+r.width-1,r.z},{r.x,r.z+r.depth-1},{r.x+r.width-1,r.z+r.depth-1}}){BlockPos p=new BlockPos(c[0],64,c[1]);if(world.getBlockState(p).isAir())return p;}throw new IllegalStateException("Review label corner occupied "+r.source.get("key"));}
        void startCity(){paintFloor(cityPlan,Blocks.GRAY_CONCRETE);Vec3d t=cityPlan.translation;
            // Keep native V12 states at integer source grid positions. The city
            // receives one global translation, never per-model floor seating.
            t=new Vec3d(Math.ceil(t.x),Math.ceil(t.y),Math.ceil(t.z));cityPlan=new Review(cityPlan.source,cityPlan.x,cityPlan.z,cityPlan.width,cityPlan.depth,t,readBox(city.getAsJsonArray("localVisualBounds")).offset(t));
            for(JsonElement e:city.getAsJsonArray("passThroughBlocks")){JsonObject b=e.getAsJsonObject();JsonArray p=b.getAsJsonArray("relativePosition");Identifier id=new Identifier(b.get("registryId").getAsString());BlockState state=Registries.BLOCK.get(id).getDefaultState();for(var prop:b.getAsJsonObject("properties").entrySet())state=property(state,prop.getKey(),prop.getValue().getAsString());world.setBlockState(new BlockPos(floor(t.x)+p.get(0).getAsInt(),floor(t.y)+p.get(1).getAsInt(),floor(t.z)+p.get(2).getAsInt()),state,Block.FORCE_STATE|Block.SKIP_DROPS);}
            city.add("placedOwners",cityRows);city.add("platform",JSON.toJsonTree(List.of(cityPlan.x,cityPlan.z,cityPlan.width,cityPlan.depth)));city.add("translation",JSON.toJsonTree(List.of(t.x,t.y,t.z)));city.add("bounds",box(cityPlan.visual));city.addProperty("oneTimePrivilegeSerialized",false);report.add("city",city);cityStarted=true;
        }
        void placeCityBatch(){if(!cityStarted)startCity();for(int n=0;n<24&&cityIndex<cityInput.size();n++)cityRows.add(component(cityInput.get(cityIndex++),cityPlan.translation,"city"));}
        void load(Box box){for(int cx=floor(box.minX)>>4;cx<=floor(box.maxX)>>4;cx++)for(int cz=floor(box.minZ)>>4;cz<=floor(box.maxZ)>>4;cz++){
            ChunkPos chunk=new ChunkPos(cx,cz);if(tickets.add(chunk))world.getChunkManager().addTicket(TICKET,chunk,2,chunk);world.getChunk(cx,cz);}}
        void place(Station station){
            Exhibit e=station.exhibit;JsonObject source=e.source;BlockPos root=station.root;String kind=source.get("kind").getAsString();
            load(new Box(station.x,63,station.z,station.x+station.width,station.visual.maxY+2,station.z+station.depth).expand(3));
            Block flooring=switch(source.get("section").getAsString()){case "v12"->Blocks.LIGHT_BLUE_CONCRETE;case "new"->Blocks.LIME_CONCRETE;case "absent"->Blocks.YELLOW_CONCRETE;case "items"->Blocks.BLUE_CONCRETE;default->throw new IllegalStateException("Invalid section");};
            for(int x=station.x;x<station.x+station.width;x++)for(int z=station.z;z<station.z+station.depth;z++)world.setBlockState(new BlockPos(x,FLOOR,z),flooring.getDefaultState(),Block.NOTIFY_LISTENERS);
            if(source.has("surfaceTileReplacesPlatformCell")&&source.get("surfaceTileReplacesPlatformCell").getAsBoolean())world.setBlockState(root,Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);
            actor.setPosition(root.getX()+.5,64,root.getZ()-3);actor.setSneaking(true);
            String representation;UUID uuid;BlockState placed=null;Box actualVisual=station.visual;NbtCompound payload=new NbtCompound();
            if(kind.equals("item_frame")){
                world.setBlockState(root,Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_ALL);
                ItemFrameEntity frame=new ItemFrameEntity(world,root.north(),Direction.NORTH);frame.setHeldItemStack(e.item.copy(),false);frame.setInvulnerable(true);
                NbtCompound fixed=frame.writeNbt(new NbtCompound());fixed.putBoolean("Fixed",true);frame.readNbt(fixed);
                require(world.spawnEntity(frame),"Cannot spawn framed item");uuid=frame.getUuid();representation="item_frame";actualVisual=new Box(root).union(frame.getBoundingBox());
            }else if(kind.equals("rp_object")){
                RpObjectEntity object=ObjectRegistry.TYPES.get(new Identifier(source.get("registryId").getAsString()).getPath()).create(world);
                object.refreshPositionAndAngles(station.origin.x,station.origin.y,station.origin.z,0,0);require(world.spawnEntity(object),"Cannot spawn RP construction object");
                uuid=object.getUuid();actualVisual=actualRpVisual(object);representation="registered_rp_entity";
            }else{
                payload.putDouble(VerticalMount.KEY,station.origin.y-root.getY());
                if(e.state.getBlock() instanceof CompositeRootBlock imported && imported.spec.simplePhysicalCollision)payload.putInt(ImportedTypeAliases.VERSION_KEY,1);
                if(e.state.getBlock() instanceof CompositeRootBlock){
                    var shift=new net.minecraft.nbt.NbtList();shift.add(net.minecraft.nbt.NbtDouble.of(station.origin.x-root.getX()));shift.add(net.minecraft.nbt.NbtDouble.of(0));shift.add(net.minecraft.nbt.NbtDouble.of(station.origin.z-root.getZ()));payload.put("SourceShift",shift);
                    UUID requested=UUID.randomUUID();var result=CompositeRuntime.place(world,root,e.state,requested,actor,payload);
                    require(result.outcome()==TransactionCore.Outcome.COMMITTED,"Composite placement refused: "+source.get("key")+" "+result);representation="native_composite_placement_transaction";
                }else{
                    world.setBlockState(root,e.state,Block.NOTIFY_ALL);VerticalMount.placed(world,root,e.state);
                    require(VerticalMount.setOffset(world,root,payload.getDouble(VerticalMount.KEY),actor),"Native exact-height placement refused: "+source.get("key"));representation="native_gallery_pose_with_existing_mount";
                }
                placed=world.getBlockState(root);require(Registries.BLOCK.getId(placed.getBlock()).toString().equals(source.get("registryId").getAsString()),"Placed registry differs");
                var own=(CompositeBlockEntity)world.getBlockEntity(root);require(own!=null&&own.resident()!=null,"Missing typed architecture resident");uuid=own.resident().instanceId();payload=own.payload();
            }
            require(Math.abs(actualVisual.minY-64-clearance(source))<1e-6,"Actual visual bottom is not seated on platform: "+source.get("key"));
            require(equalBounds(actualVisual,station.visual),"Actual rendered RP geometry differs from exact plan");
            BlockPos labelPos=freeLabelCorner(station);
            label(labelPos,source.get("name").getAsString(),source.get("numericId").getAsString(),source.get("section").getAsString(),kind.equals("item_frame")?"Предмет V12":"Вариант "+source.get("variant").getAsInt());
            JsonObject row=source.deepCopy();row.addProperty("id",source.get("numericId").getAsString());row.addProperty("registry",source.get("registryId").getAsString());row.addProperty("item",source.get("itemId").getAsString());
            row.addProperty("representation",representation);row.addProperty("uuid",uuid.toString());row.addProperty("floor",Registries.BLOCK.getId(flooring).toString());
            row.addProperty("gridId",station.grid);row.addProperty("row",station.row);row.addProperty("column",station.column);
            row.add("root",JSON.toJsonTree(List.of(root.getX(),root.getY(),root.getZ())));row.add("renderOrigin",JSON.toJsonTree(List.of(station.origin.x,station.origin.y,station.origin.z)));
            row.add("platform",JSON.toJsonTree(List.of(station.x,station.z,station.width,station.depth)));row.add("bounds",box(actualVisual));row.add("actualBounds",box(actualVisual));
            row.add("labelPos",JSON.toJsonTree(List.of(labelPos.getX(),labelPos.getY(),labelPos.getZ())));
            row.addProperty("renderedBottomOnPlatform",true);row.addProperty("verticalOffset",payload.getDouble(VerticalMount.KEY));
            if(kind.equals("item_frame")){row.add("frameSupport",JSON.toJsonTree(List.of(root.getX(),root.getY(),root.getZ())));row.addProperty("frameFacing","north");row.addProperty("frameHeldItemNbt",e.item.getNbt()==null?"":e.item.getNbt().toString());}
            if(placed!=null){JsonObject properties=new JsonObject();for(var entry:placed.getEntries().entrySet())properties.addProperty(entry.getKey().getName(),propertyName(entry.getKey(),entry.getValue()));row.add("placedState",properties);}
            rows.add(row);if(index%16==0)try{progress();}catch(Exception failure){throw new RuntimeException(failure);}
        }
        BlockPos freeLabelCorner(Station station){
            // Offset source anchors can be on a margin cell although their rendered geometry is centered.
            // A label must never replace a logical root or one of its physical proxy blocks.
            for(int[] corner:new int[][]{{station.x,station.z},{station.x+station.width-1,station.z},{station.x,station.z+station.depth-1},{station.x+station.width-1,station.z+station.depth-1}}){
                BlockPos pos=new BlockPos(corner[0],64,corner[1]);
                if(!pos.equals(station.root)&&world.getBlockState(pos).isAir())return pos;
            }
            throw new IllegalStateException("No free platform corner for label: "+station.exhibit.source.get("key"));
        }
        void label(BlockPos pos,String name,String number,String section,String state){
            world.setBlockState(pos,Blocks.OAK_SIGN.getDefaultState(),Block.NOTIFY_ALL);
            if(world.getBlockEntity(pos) instanceof SignBlockEntity sign){String shortName=name.length()>26?name.substring(0,26):name;
                var text=sign.getFrontText().withMessage(0,Text.literal(shortName)).withMessage(1,Text.literal(number.isEmpty()?"Без ID мода":"ID "+number)).withMessage(2,Text.literal(sectionTitle(section))).withMessage(3,Text.literal(state));
                sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.markDirty();}}
        void finish()throws Exception{
            load(new Box(-8,63,-10,4,69,1));for(int x=-8;x<4;x++)for(int z=-10;z<0;z++)world.setBlockState(new BlockPos(x,63,z),Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            label(new BlockPos(0,64,-9),"Dreamwalker BB · V12.6","","v12","Модель +2 · шаг 2");
            label(findCorner(cityPlan),"Фрагмент исходного города","","city","Прежнее расположение");
            verifyInitialCityOverlap();report.addProperty("reviewPlatformsPlaced",reviewRows.size());report.addProperty("cityOwnersPlaced",cityRows.size());
            server.save(false,true,true);report.addProperty("cataloguePlaced",rows.size());report.addProperty("labelsBothSides",true);report.addProperty("status","PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN");Files.writeString(OUTPUT,JSON.toJson(report));progress();}
        void verifyInitialCityOverlap(){
            JsonObject result=new JsonObject();result.addProperty("scopeIsEmptyOutsideInitialPlacement",true);Set<UUID> checked=new HashSet<>();boolean tested=false;
            for(JsonElement e:cityRows){JsonObject row=e.getAsJsonObject();if(!row.get("registryId").getAsString().startsWith("bloodborne_dw:source_"))continue;JsonArray a=row.getAsJsonArray("root");BlockPos root=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);var owner=own.resident();if(!checked.add(owner.instanceId()))continue;BlockState state=world.getBlockState(root);NbtCompound payload=own.payload();var object=CompositeRuntime.instance(world,root,state,owner.instanceId(),payload);
                boolean overlap=false;for(var cell:object.cells().entrySet())for(var other:CompositeRuntime.contributions(world,CompositeData.pos(owner.root().add(cell.getKey()))))if(!other.owner().equals(owner)&&cell.getValue().collides(other.shape())){overlap=true;break;}
                if(!overlap)continue;
                require(!SourceConversionScope.initialInstance(owner.instanceId()),"Initial conversion privilege leaked");
                require(CompositeRuntime.remove(world,owner,actor,false).outcome()==TransactionCore.Outcome.COMMITTED,"Cannot break imported overlapping instance");
                var ordinary=CompositeRuntime.place(world,root,state,owner.instanceId(),actor,payload);require(ordinary.outcome()!=TransactionCore.Outcome.COMMITTED,"Ordinary placement inherited force-overlap privilege");
                var restored=SourceConversionScope.initialInstances(Set.of(owner.instanceId()),()->CompositeRuntime.place(world,root,state,owner.instanceId(),actor,payload));require(restored.outcome()==TransactionCore.Outcome.COMMITTED,"Cannot restore initial conversion test");
                result.addProperty("status","PASS_BREAK_THEN_NORMAL_REPLACEMENT_REJECTED_RESTORED_INITIAL_UUID");result.addProperty("testedRegistry",row.get("registryId").getAsString());result.addProperty("testedUuid",owner.instanceId().toString());result.addProperty("normalRejection",ordinary.toString());tested=true;break;
            }
            if(!tested)result.addProperty("status","NOT_APPLICABLE_NO_PHYSICAL_OVERLAP_IN_SELECTED_CITY_AFTER_COLLISION_REDUCTION");city.add("initialOverlapRuntimeCheck",result);
        }
        void progress()throws Exception{JsonObject p=new JsonObject();p.addProperty("status",index<stations.size()||reviewIndex<reviews.size()||cityIndex<cityInput.size()?"RUNNING":"PLACED");p.addProperty("placed",rows.size());p.addProperty("total",stations.size());p.addProperty("reviewPlaced",reviewRows.size());p.addProperty("reviewTotal",reviews.size());p.addProperty("cityPlaced",cityRows.size());p.addProperty("cityTotal",cityInput.size());p.addProperty("serverTicks",age);Files.writeString(PROGRESS,JSON.toJson(p));}
        void close(){for(ChunkPos chunk:tickets)world.getChunkManager().removeTicket(TICKET,chunk,2,chunk);actor.discard();}
    }
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(!Files.isRegularFile(INPUT))return;try{JsonObject marker=JsonParser.parseString(Files.readString(INPUT)).getAsJsonObject();
            require("FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY".equals(marker.get("guard").getAsString())&&"dreamwalker-flat-gallery-v126".equals(marker.get("gallerySchema").getAsString()),"Wrong new V12.6 gallery marker");
            require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world")&&server.getOverworld().getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator&&!Files.exists(OUTPUT),"Fresh isolated flat world required");AUTHORS.put(server,new Author(server,marker));}catch(Throwable error){failure(error);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{Author author=AUTHORS.get(server);if(author==null)return;try{if(author.age++<10)return;
            if(author.index<author.stations.size()){author.place(author.stations.get(author.index++));return;}
            if(author.reviewIndex<author.reviews.size()){author.placeReview(author.reviews.get(author.reviewIndex++));author.progress();return;}
            if(author.cityIndex<author.cityInput.size()){author.placeCityBatch();author.progress();return;}if(!author.settling){author.settling=true;author.age=0;return;}
            author.finish();AUTHORS.remove(server);author.close();}catch(Throwable error){AUTHORS.remove(server);author.close();failure(error);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{Author author=AUTHORS.remove(server);if(author!=null)author.close();});
    }
    private static Box actualRpVisual(RpObjectEntity object){Box result=null;for(Box box:RpObjectGeometry.ordinaryVisualBoxes(object))result=result==null?box:result.union(box);require(result!=null,"Empty RP rendered geometry");return result;}
    private static double clearance(JsonObject source){double value=source.has("surfaceClearance")?source.get("surfaceClearance").getAsDouble():0;require(value==0||value==1d/1024,"Unsupported surface clearance");return value;}
    private static boolean equalBounds(Box a,Box b){return Math.abs(a.minX-b.minX)<1e-6&&Math.abs(a.minY-b.minY)<1e-6&&Math.abs(a.minZ-b.minZ)<1e-6&&Math.abs(a.maxX-b.maxX)<1e-6&&Math.abs(a.maxY-b.maxY)<1e-6&&Math.abs(a.maxZ-b.maxZ)<1e-6;}
    private static int floor(double value){return(int)Math.floor(value+1e-8);}private static int ceiling(double value){return(int)Math.ceil(value-1e-8);}
    private static JsonElement box(Box b){return JSON.toJsonTree(List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));}
    private static Box readBox(JsonArray a){return new Box(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());}
    private static String sectionTitle(String section){return switch(section){case"v12"->"Исходная V12";case"new"->"Новые объекты";case"absent"->"Нет на исходной карте";case"items"->"Предметы V12 в рамках";case"assemblies"->"Составные · на проверку";case"doors"->"Двери · на проверку";case"city"->"Фрагмент города";default->section;};}
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState property(BlockState state,String key,String value){Property p=state.getBlock().getStateManager().getProperty(key);require(p!=null,"Unknown property: "+key);return state.with(p,(Comparable)p.parse(value).orElseThrow());}
    @SuppressWarnings({"rawtypes","unchecked"})private static String propertyName(Property p,Comparable value){return p.name(value);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void failure(Throwable error){try{JsonObject result=new JsonObject();result.addProperty("status","FAIL_GALLERY_AUTHORING");result.addProperty("failure",error.toString());Files.writeString(OUTPUT,JSON.toJson(result));error.printStackTrace();}catch(Exception ignored){}}
}
