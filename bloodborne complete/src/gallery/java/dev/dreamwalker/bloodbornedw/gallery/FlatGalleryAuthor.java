package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;

/** Explicit-marker author for a new flat world; absent from the production JAR. */
public final class FlatGalleryAuthor implements ModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path INPUT = Path.of("catalogue-gallery-input.json");
    private static final Path OUTPUT = Path.of("catalogue-gallery-output.json");
    private static final Path PROGRESS = Path.of("flat-gallery-progress.json");
    private static final int FLOOR = 63, GAP = 2, MARGIN = 2, COLUMNS = 16;
    private static final BlockPos VIRTUAL = new BlockPos(0,64,0);
    private static final Map<MinecraftServer, Author> AUTHORS = new WeakHashMap<>();
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("dreamwalker_flat_gallery", Comparator.comparingLong(ChunkPos::toLong));
    private record Exhibit(JsonObject source, ItemStack item, BlockState state, Box local) {}
    private record Station(Exhibit exhibit, int row, int column, int x, int z, int width, int depth, BlockPos root, Box bounds) {}

    private static final class Author {
        final MinecraftServer server;
        final ServerWorld world;
        final ServerPlayerEntity actor;
        final JsonObject report = new JsonObject();
        final JsonArray rows = new JsonArray();
        final List<Station> stations = new ArrayList<>();
        final Set<ChunkPos> tickets = new HashSet<>();
        final Map<String, Integer> sectionRows = new LinkedHashMap<>();
        int index, age, width, depth;
        boolean settling;

        Author(MinecraftServer server, JsonObject marker) throws Exception {
            this.server = server; world = server.getOverworld();
            actor = new ServerPlayerEntity(server, world, new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:FlatGalleryAuthor".getBytes(StandardCharsets.UTF_8)), "FlatGalleryAuthor")) {
                @Override public boolean hasPermissionLevel(int level) { return level <= 4; }
            };
            actor.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketCallbacks callbacks) {}
            }, actor);
            actor.changeGameMode(GameMode.CREATIVE); actor.getAbilities().allowModifyWorld = true;
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server); world.setTimeOfDay(6000);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false, server);
            world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
            world.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
            world.setSpawnPos(new BlockPos(0,64,-6),0); server.setDefaultGameMode(GameMode.CREATIVE);
            report.addProperty("schema", "dreamwalker-flat-gallery-v1");
            report.addProperty("status", "RUNNING");
            report.addProperty("productionJarSha256", marker.get("productionJarSha256").getAsString());
            report.addProperty("platformFloorY", FLOOR); report.addProperty("platformGap", GAP);
            report.addProperty("platformMargin", MARGIN); report.addProperty("columns", COLUMNS);
            report.addProperty("qaRequiredForSavedWorld", false);
            report.add("catalogue", marker.get("catalogue").deepCopy()); report.add("exhibits", rows);
            JsonObject visual = marker.getAsJsonObject("localVisualEnvelopes");
            List<Exhibit> exhibits = new ArrayList<>(); Set<String> keys = new HashSet<>();
            for (JsonElement element : marker.getAsJsonObject("catalogue").getAsJsonArray("exhibits")) {
                JsonObject input = element.getAsJsonObject();
                require(keys.add(input.get("key").getAsString()), "Duplicate exhibit key");
                String kind = input.get("kind").getAsString(); ItemStack item = ItemStack.EMPTY; BlockState state = null;
                Box local = new Box(0,0,0,1,1,1);
                if (!kind.equals("diagnostic")) {
                    Identifier itemId = new Identifier(input.get("itemId").getAsString());
                    require(Registries.ITEM.containsId(itemId), "Missing offered item: " + itemId);
                    item = new ItemStack(Registries.ITEM.get(itemId));
                    if (input.has("blockStateTag")) for (var entry : input.getAsJsonObject("blockStateTag").entrySet()) item.getOrCreateSubNbt("BlockStateTag").putString(entry.getKey(),entry.getValue().getAsString());
                    if (kind.equals("architecture")) {
                        require(item.getItem() instanceof BlockItem, "Architecture item is not a BlockItem: " + itemId);
                        Block block = ((BlockItem)item.getItem()).getBlock(); state = block.getDefaultState();
                        if (input.has("blockStateTag")) for (var entry : input.getAsJsonObject("blockStateTag").entrySet()) state = property(state, entry.getKey(), entry.getValue().getAsString());
                        if (state.getBlock() instanceof PrototypeLadderBlock) state = PrototypeLadderBlock.withYaw(state.with(PrototypeLadderBlock.FREESTANDING,true),0);
                        var instance = CompositeRuntime.instance(world,VIRTUAL,state,UUID.randomUUID(),new NbtCompound());
                        for (var part : instance.cells().entrySet()) {
                            var cell = part.getKey();
                            local = local.union(new Box(cell.x(),cell.y(),cell.z(),cell.x()+1,cell.y()+1,cell.z()+1));
                            for (var b : part.getValue().selection()) local = local.union(new Box(cell.x()+b.minX(),cell.y()+b.minY(),cell.z()+b.minZ(),cell.x()+b.maxX(),cell.y()+b.maxY(),cell.z()+b.maxZ()));
                        }
                        for (Box b : PlacementPhysics.physicalBoxes(instance)) local = local.union(b.offset(0,-64,0));
                        String visualKey = input.get("registryId").getAsString() + "|" + input.get("variant").getAsInt();
                        if (visual != null && visual.has(visualKey)) local = local.union(readBox(visual.getAsJsonArray(visualKey)));
                    } else if (kind.equals("rp_object")) {
                        var type = ObjectRegistry.TYPES.get(new Identifier(input.get("registryId").getAsString()).getPath());
                        require(type != null, "Unknown RP construction object");
                        RpObjectEntity object = type.create(world); pose(object,VIRTUAL);
                        local = local.union(object.visualBounds().offset(0,-64,0));
                        for (Box b : object.activePhysicalBoxes()) local = local.union(b.offset(0,-64,0));
                        local = local.union(object.queryBounds().offset(0,-64,0));
                    } else throw new IllegalStateException("Unsupported gallery kind: " + kind);
                }
                exhibits.add(new Exhibit(input,item,state,local));
            }
            plan(exhibits);
            report.addProperty("catalogueTypes", stations.size());
            progress();
        }

        void plan(List<Exhibit> exhibits) {
            int[] widths = new int[COLUMNS]; Arrays.fill(widths, 5);
            List<Integer> depths = new ArrayList<>(); List<int[]> positions = new ArrayList<>();
            String previousSection = ""; int row = -1, column = COLUMNS;
            for (Exhibit exhibit : exhibits) {
                String section = exhibit.source.get("section").getAsString();
                if (!section.equals(previousSection)) { column = COLUMNS; previousSection = section; }
                if (column == COLUMNS) { row++; column=0; depths.add(5); sectionRows.putIfAbsent(section,row); }
                Box b = exhibit.local;
                widths[column] = Math.max(widths[column],ceiling(b.maxX)-floor(b.minX)+2*MARGIN);
                depths.set(row, Math.max(depths.get(row),ceiling(b.maxZ)-floor(b.minZ)+2*MARGIN));
                positions.add(new int[]{row,column++});
            }
            int[] xs = new int[COLUMNS], zs = new int[depths.size()];
            for (int i=1;i<COLUMNS;i++) xs[i]=xs[i-1]+widths[i-1]+GAP;
            for (int i=1;i<zs.length;i++) zs[i]=zs[i-1]+depths.get(i-1)+GAP;
            width = xs[COLUMNS-1]+widths[COLUMNS-1]; depth=zs[zs.length-1]+depths.get(zs.length-1);
            for (int i=0;i<exhibits.size();i++) {
                Exhibit e=exhibits.get(i); int r=positions.get(i)[0],c=positions.get(i)[1]; Box b=e.local;
                BlockPos root=new BlockPos(xs[c]+MARGIN-floor(b.minX),64-Math.min(0,floor(b.minY)),zs[r]+MARGIN-floor(b.minZ));
                Box actual=b.offset(root);
                require(actual.minY>=64-1e-7,"Exhibit below platform floor");
                stations.add(new Station(e,r,c,xs[c],zs[r],widths[c],depths.get(r),root,actual));
            }
            report.add("columnWidths",JSON.toJsonTree(widths)); report.add("rowDepths",JSON.toJsonTree(depths));
            report.add("sectionStartRows",JSON.toJsonTree(sectionRows));
            report.addProperty("rows",depths.size()); report.addProperty("widthBlocks",width); report.addProperty("depthBlocks",depth);
        }

        void load(Box box) {
            for(int cx=floor(box.minX)>>4;cx<=floor(box.maxX)>>4;cx++) for(int cz=floor(box.minZ)>>4;cz<=floor(box.maxZ)>>4;cz++) {
                ChunkPos chunk=new ChunkPos(cx,cz);
                if(tickets.add(chunk))world.getChunkManager().addTicket(TICKET,chunk,2,chunk);
                world.getChunk(cx,cz);
            }
        }

        void place(Station station) {
            Exhibit e=station.exhibit; JsonObject source=e.source; BlockPos root=station.root;
            load(new Box(station.x,63,station.z,station.x+station.width,station.bounds.maxY+2,station.z+station.depth).expand(2));
            Block flooring=switch(source.get("section").getAsString()) {
                case "v12" -> Blocks.LIGHT_BLUE_CONCRETE; case "new" -> Blocks.LIME_CONCRETE;
                case "absent" -> Blocks.YELLOW_CONCRETE; case "error" -> Blocks.RED_CONCRETE;
                default -> throw new IllegalStateException("Unknown section");
            };
            for(int x=station.x;x<station.x+station.width;x++)for(int z=station.z;z<station.z+station.depth;z++)world.setBlockState(new BlockPos(x,FLOOR,z),flooring.getDefaultState(),Block.NOTIFY_LISTENERS);
            actor.setPosition(root.getX()+.5,root.getY(),root.getZ()-3);actor.setYaw(0);actor.setPitch(0);actor.setSneaking(true);
            actor.setStackInHand(Hand.MAIN_HAND,e.item.copy());actor.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
            String kind=source.get("kind").getAsString(),representation;UUID uuid=null;BlockState placed=null;Box actual=new Box(root);
            if(kind.equals("diagnostic")) {
                world.setBlockState(root,Blocks.RED_STAINED_GLASS.getDefaultState(),Block.NOTIFY_LISTENERS);
                representation="diagnostic_placeholder_no_authored_mod_object";
            } else if(kind.equals("rp_object")) {
                RpObjectEntity object=ObjectRegistry.TYPES.get(new Identifier(source.get("registryId").getAsString()).getPath()).create(world);
                pose(object,root);require(world.spawnEntity(object),"Cannot spawn RP construction object");
                uuid=object.getUuid();actual=object.visualBounds();for(Box b:object.activePhysicalBoxes())actual=actual.union(b);
                representation="registered_rp_entity";
            } else {
                if(e.state.getBlock() instanceof CompositeRootBlock) {
                    UUID requested=UUID.randomUUID();
                    var result=CompositeRuntime.place(world,root,e.state,requested,actor,new NbtCompound());
                    require(result.outcome()==TransactionCore.Outcome.COMMITTED,"Composite placement refused: "+source.get("key")+" "+result);
                    representation="native_composite_placement_transaction";
                } else {
                    if(root.getY()>64)world.setBlockState(root.down(),Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_ALL);
                    ActionResult result=e.item.getItem().useOnBlock(new ItemUsageContext(actor,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5),Direction.UP,root.down(),false)));
                    require(result.isAccepted(),"Native item placement refused: "+source.get("key"));
                    representation="ordinary_native_block_item";
                }
                placed=world.getBlockState(root);require(Registries.BLOCK.getId(placed.getBlock()).toString().equals(source.get("registryId").getAsString()),"Placed registry differs");
                var owner=VerticalMount.owner(world,root);require(owner!=null,"Missing persisted architecture UUID");uuid=owner.instanceId();
                var be=(CompositeBlockEntity)world.getBlockEntity(root);
                var instance=CompositeRuntime.instance(world,root,placed,uuid,be.payload());
                actual=new Box(root);
                for(var entry:instance.cells().entrySet())for(var b:entry.getValue().selection()){
                    var cell=entry.getKey();actual=actual.union(new Box(root.getX()+cell.x()+b.minX(),root.getY()+cell.y()+b.minY(),root.getZ()+cell.z()+b.minZ(),root.getX()+cell.x()+b.maxX(),root.getY()+cell.y()+b.maxY(),root.getZ()+cell.z()+b.maxZ()));
                }
                for(Box b:PlacementPhysics.physicalBoxes(instance))actual=actual.union(b);
            }
            require(station.bounds.expand(1e-6).contains(actual.minX,actual.minY,actual.minZ)&&station.bounds.expand(1e-6).contains(actual.maxX-1e-8,actual.maxY-1e-8,actual.maxZ-1e-8),"Placed geometry escapes planned envelope: "+source.get("key")+" "+actual+" / "+station.bounds);
            label(new BlockPos(station.x,64,station.z),source.get("name").getAsString(),source.get("numericId").getAsString(),source.get("section").getAsString(),kind.equals("diagnostic")?"Нет авторской модели":"Вариант "+source.get("variant").getAsInt());
            JsonObject row=source.deepCopy();
            row.addProperty("id",source.get("numericId").getAsString());row.addProperty("registry",source.get("registryId").getAsString());row.addProperty("item",source.get("itemId").getAsString());
            row.addProperty("representation",representation);row.addProperty("uuid",uuid==null?"":uuid.toString());
            row.addProperty("floor",Registries.BLOCK.getId(flooring).toString());row.addProperty("row",station.row);row.addProperty("column",station.column);
            row.add("root",JSON.toJsonTree(List.of(root.getX(),root.getY(),root.getZ())));
            row.add("platform",JSON.toJsonTree(List.of(station.x,station.z,station.width,station.depth)));
            row.add("bounds",box(station.bounds));row.add("actualBounds",box(actual));
            if(placed!=null){JsonObject properties=new JsonObject();for(var entry:placed.getEntries().entrySet())properties.addProperty(entry.getKey().getName(),propertyName(entry.getKey(),entry.getValue()));row.add("placedState",properties);}
            rows.add(row);
            if(index%16==0)try{progress();}catch(Exception failure){throw new RuntimeException(failure);}
        }

        void label(BlockPos pos,String name,String number,String section,String state) {
            world.setBlockState(pos,Blocks.OAK_SIGN.getDefaultState(),Block.NOTIFY_ALL);
            if(world.getBlockEntity(pos) instanceof SignBlockEntity sign){
                String shortName=name.length()>26?name.substring(0,26):name;
                sign.setText(sign.getFrontText().withMessage(0,Text.literal(shortName)).withMessage(1,Text.literal(number.isEmpty()?"Без ID мода":"ID "+number)).withMessage(2,Text.literal(sectionTitle(section))).withMessage(3,Text.literal(state)),true);
                sign.setWaxed(true);sign.markDirty();
            }
        }

        void finish() throws Exception {
            load(new Box(-8,63,-10,4,69,1));
            for(int x=-8;x<4;x++)for(int z=-10;z<0;z++)world.setBlockState(new BlockPos(x,63,z),Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            label(new BlockPos(0,64,-9),"Dreamwalker BB · V12.1","","v12","16 колонок · шаг 2");
            for(var entry:sectionRows.entrySet()) {
                Station first=stations.stream().filter(s->s.row==entry.getValue()).findFirst().orElseThrow();
                load(new Box(-7,63,first.z-1,-1,69,first.z+6));
                for(int x=-7;x<-1;x++)for(int z=first.z;z<first.z+6;z++)world.setBlockState(new BlockPos(x,63,z),Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
                label(new BlockPos(-5,64,first.z),sectionTitle(entry.getKey()),"",entry.getKey(),"Начало секции →");
            }
            server.save(false,true,true);
            report.addProperty("cataloguePlaced",rows.size());report.addProperty("status","PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN");
            Files.writeString(OUTPUT,JSON.toJson(report));progress();
        }

        void progress() throws Exception {JsonObject p=new JsonObject();p.addProperty("status",index<stations.size()?"RUNNING":"PLACED");p.addProperty("placed",rows.size());p.addProperty("total",stations.size());p.addProperty("serverTicks",age);Files.writeString(PROGRESS,JSON.toJson(p));}
        void close(){for(ChunkPos chunk:tickets)world.getChunkManager().removeTicket(TICKET,chunk,2,chunk);actor.discard();}
    }

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(!Files.isRegularFile(INPUT))return;try{
            JsonObject marker=JsonParser.parseString(Files.readString(INPUT)).getAsJsonObject();
            require("FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY".equals(marker.get("guard").getAsString())&&"dreamwalker-flat-gallery-v1".equals(marker.get("gallerySchema").getAsString()),"Wrong isolated four-section gallery guard");
            require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world")&&server.getOverworld().getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator&&!Files.exists(OUTPUT),"New isolated flat world required");
            AUTHORS.put(server,new Author(server,marker));
        }catch(Throwable error){failure(error);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{Author author=AUTHORS.get(server);if(author==null)return;try{
            if(author.age++<10)return;
            if(author.index<author.stations.size()){author.place(author.stations.get(author.index++));return;}
            if(!author.settling){author.settling=true;author.age=0;return;}
            author.finish();AUTHORS.remove(server);author.close();
        }catch(Throwable error){AUTHORS.remove(server);author.close();failure(error);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{Author author=AUTHORS.remove(server);if(author!=null)author.close();});
    }

    private static void pose(RpObjectEntity object,BlockPos root){
        String id=object.assetId();Vec3d surface=new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5);
        Vec3d origin=new Vec3d(root.getX(),ObjectRegistry.placementY(id,root.getY()),root.getZ());
        if(RpObjectGeometry.surfaceMounted(id))origin=surface.subtract(RpObjectGeometry.placementAnchor(id,Direction.UP,object.isOpen()).multiply(object.asset().scale()*object.objectScale()));
        object.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);
    }
    private static int floor(double value){return (int)Math.floor(value+1e-8);}
    private static int ceiling(double value){return (int)Math.ceil(value-1e-8);}
    private static JsonElement box(Box b){return JSON.toJsonTree(List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));}
    private static Box readBox(JsonArray a){return new Box(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());}
    private static String sectionTitle(String section){return switch(section){case "v12"->"Исходная V12";case "new"->"Новые объекты";case "absent"->"Нет на исходной карте";case "error"->"Ошибки исходников";default->section;};}
    @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState property(BlockState state,String key,String value){Property prop=state.getBlock().getStateManager().getProperty(key);require(prop!=null,"Unknown block state property: "+key);return state.with(prop,(Comparable)prop.parse(value).orElseThrow());}
    @SuppressWarnings({"rawtypes","unchecked"}) private static String propertyName(Property property,Comparable value){return property.name(value);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void failure(Throwable error){try{JsonObject output=new JsonObject();output.addProperty("status","FAIL_GALLERY_AUTHORING");output.addProperty("failure",error.toString());Files.writeString(OUTPUT,JSON.toJson(output));error.printStackTrace();}catch(Exception ignored){}}
}
