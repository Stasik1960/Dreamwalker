package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.object.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;

/** Small standalone fresh-world author; it never runs in the production mod. */
public final class GalleryAuthor implements ModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final BlockPos VIRTUAL = new BlockPos(0,64,0);
    private static final Path INPUT = Path.of("catalogue-gallery-input.json");
    private static final Path OUTPUT = Path.of("catalogue-gallery-output.json");
    private static final Map<MinecraftServer,Author> AUTHORS = new WeakHashMap<>();
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("dreamwalker_gallery_author",Comparator.comparingLong(ChunkPos::toLong));
    private record Exhibit(ItemStack item,String kind,Identifier registry,String number,String name) {}
    private record Platform(int x,int z,int width,int depth,BlockPos root,Box expected) {}

    private static final class Author {
        final MinecraftServer server;
        final ServerWorld world;
        final ServerPlayerEntity actor;
        final List<Exhibit> exhibits = new ArrayList<>();
        final Set<ChunkPos> tickets = new HashSet<>();
        final JsonObject report = new JsonObject();
        final JsonArray rows = new JsonArray();
        int index,age,nextX;
        boolean finalizing;
        Author(MinecraftServer s,JsonObject marker) {
            server=s;world=s.getOverworld();
            report.addProperty("schema","dreamwalker-v11-gallery-v1");
            report.addProperty("status","RUNNING");
            report.addProperty("productionJarSha256",marker.get("productionJarSha256").getAsString());
            report.add("exhibits",rows);
            actor=new ServerPlayerEntity(s,world,new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:GalleryAuthor".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"GalleryAuthor")) {
                @Override public boolean hasPermissionLevel(int level){return level<=4;}
            };
            actor.networkHandler=new ServerPlayNetworkHandler(s,new ClientConnection(NetworkSide.SERVERBOUND) {
                @Override public void send(Packet<?> p){}
                @Override public void send(Packet<?> p,PacketCallbacks callbacks){}
            },actor);
            actor.changeGameMode(GameMode.CREATIVE);actor.getAbilities().allowModifyWorld=true;
            JsonArray itemIds=new JsonArray(),npcIds=new JsonArray();Set<Identifier> seen=new HashSet<>();
            for(ItemStack item:UnifiedCreativeCatalogue.canonicalItems()) {
                Identifier itemId=Registries.ITEM.getId(item.getItem());
                require(!Set.of("builder_tool","composite_builder").contains(itemId.getPath()),"Removed tool still offered: "+itemId);
                require(seen.add(itemId),"Duplicate offered item: "+itemId);itemIds.add(itemId.toString());
                var entry=DebugCatalogue.entry(item);
                String kind=item.getItem() instanceof BlockItem?"architecture":itemId.getPath().endsWith("_placer")?"rp_object":"rp_item";
                Identifier registry=kind.equals("rp_object")?entry.registryId():itemId;
                exhibits.add(new Exhibit(item,kind,registry,entry==null?"":entry.temporaryId(),entry==null?item.getName().getString():entry.name()));
            }
            for(var type:MobRegistry.TYPES.entrySet()) {
                Identifier registry=Registries.ENTITY_TYPE.getId(type.getValue());npcIds.add(registry.toString());
                var entry=DebugCatalogue.entry(registry);
                exhibits.add(new Exhibit(ItemStack.EMPTY,"rp_mob",registry,entry==null?"":entry.temporaryId(),entry==null?type.getKey():entry.name()));
            }
            report.add("expectedItems",itemIds);report.add("expectedNpcs",npcIds);
            report.addProperty("catalogueTypes",exhibits.size());
            report.addProperty("inventoryPolicy","Every offered current item, including spawn eggs in frames; each NPC also has a real persistent exhibit; retired aliases excluded.");
            world.setSpawnPos(new BlockPos(0,64,-6),0);s.setDefaultGameMode(GameMode.CREATIVE);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);world.setTimeOfDay(6000);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,s);
            world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false,s);
        }
        void load(Box box) {
            for(int cx=MathHelper.floor(box.minX)>>4;cx<=MathHelper.floor(box.maxX)>>4;cx++)
                for(int cz=MathHelper.floor(box.minZ)>>4;cz<=MathHelper.floor(box.maxZ)>>4;cz++) {
                    ChunkPos chunk=new ChunkPos(cx,cz);
                    if(tickets.add(chunk))world.getChunkManager().addTicket(TICKET,chunk,2,chunk);
                    world.getChunk(cx,cz);
                }
        }
        Box prototypeBounds(Exhibit e) {
            if(e.kind.equals("rp_object")) {
                var object=ObjectRegistry.TYPES.get(e.registry.getPath()).create(world);pose(object,VIRTUAL);return objectBounds(object);
            }
            if(e.kind.equals("rp_mob")) {
                var mob=MobRegistry.TYPES.get(e.registry.getPath()).create(world);mob.setPosition(.5,64,.5);return mob.getBoundingBox();
            }
            if(e.item.getItem() instanceof BlockItem item) {
                var state=item.getBlock().getDefaultState();
                if(state.getBlock() instanceof PrototypeLadderBlock)state=PrototypeLadderBlock.withYaw(state.with(PrototypeLadderBlock.FREESTANDING,true),0);
                var physical=PlacementPhysics.physicalBoxes(CompositeRuntime.instance(world,VIRTUAL,state,UUID.randomUUID(),new NbtCompound()));
                if(!physical.isEmpty())return union(physical);
                var outline=state.getOutlineShape(world,VIRTUAL);
                return outline.isEmpty()?new Box(VIRTUAL):outline.getBoundingBox().offset(VIRTUAL);
            }
            return new Box(VIRTUAL).union(new ItemFrameEntity(world,VIRTUAL.north(),Direction.NORTH).getBoundingBox());
        }
        Platform allocate(Box original) {
            int x=nextX,z=0;
            BlockPos root=new BlockPos(x+2-(int)Math.floor(original.minX),64,z+2-(int)Math.floor(original.minZ));
            Box actual=original.offset(root.getX(),0,root.getZ());
            // Round after translation so trigonometric residue cannot add a whole block.
            root=root.add(x+2-(int)Math.floor(actual.minX),0,z+2-(int)Math.floor(actual.minZ));
            actual=original.offset(root.getX(),0,root.getZ());
            int width=(int)Math.ceil(actual.maxX)-(int)Math.floor(actual.minX)+4;
            int depth=(int)Math.ceil(actual.maxZ)-(int)Math.floor(actual.minZ)+4;
            require(Math.floor(actual.minX)-2==x&&Math.floor(actual.minZ)-2==z,"Platform origin differs");
            nextX=x+width+2;
            return new Platform(x,z,width,depth,root,actual);
        }
        void place(Exhibit e) {
            Platform platform=allocate(prototypeBounds(e));BlockPos root=platform.root;
            load(platform.expected.expand(2));
            Block flooring=e.kind.equals("rp_mob")?Blocks.DEEPSLATE_TILES:e.kind.equals("architecture")?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE;
            for(int px=platform.x;px<platform.x+platform.width;px++)for(int pz=platform.z;pz<platform.z+platform.depth;pz++)
                world.setBlockState(new BlockPos(px,63,pz),flooring.getDefaultState(),Block.NOTIFY_ALL);
            actor.setPosition(root.getX()+.5,64,root.getZ()-4);actor.setYaw(0);actor.setPitch(0);actor.setSneaking(true);
            actor.setStackInHand(Hand.MAIN_HAND,e.item.copy());actor.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
            Box actual;UUID uuid;String representation,boundsSource;
            if(e.kind.equals("rp_object")) {
                RpObjectEntity object=ObjectRegistry.TYPES.get(e.registry.getPath()).create(world);pose(object,root);load(object.queryBounds());
                require(world.spawnEntity(object),"Cannot spawn RP exhibit: "+e.registry);
                actual=objectBounds(object);uuid=object.getUuid();representation="registered_rp_entity";
                boundsSource=object.activePhysicalBoxes().isEmpty()?"visual_fallback_no_player_collision":"player_collision";
            } else if(e.kind.equals("rp_mob")) {
                var mob=MobRegistry.TYPES.get(e.registry.getPath()).create(world);
                mob.refreshPositionAndAngles(root.getX()+.5,64,root.getZ()+.5,180,0);
                mob.setAiDisabled(true);mob.setPersistent();mob.setInvulnerable(true);mob.setNoGravity(true);
                require(world.spawnEntity(mob),"Cannot spawn real NPC: "+e.registry);
                actual=mob.getBoundingBox();uuid=mob.getUuid();representation="registered_npc";boundsSource="npc_dimensions";
            } else if(e.item.getItem() instanceof BlockItem) {
                ActionResult result=e.item.getItem().useOnBlock(new ItemUsageContext(actor,Hand.MAIN_HAND,
                    new BlockHitResult(new Vec3d(root.getX()+.5,64,root.getZ()+.5),Direction.UP,root.down(),false)));
                require(result.isAccepted(),"Ordinary block item placement refused: "+e.registry+" "+result);
                var owner=VerticalMount.owner(world,root);require(owner!=null,"Placed architecture root has no UUID: "+e.registry);
                uuid=owner.instanceId();var be=(CompositeBlockEntity)world.getBlockEntity(root);
                var physical=PlacementPhysics.physicalBoxes(CompositeRuntime.instance(world,root,world.getBlockState(root),uuid,be.payload()));
                var outline=world.getBlockState(root).getOutlineShape(world,root);
                actual=physical.isEmpty()?(outline.isEmpty()?new Box(root):outline.getBoundingBox().offset(root)):union(physical);
                representation="ordinary_block_item";boundsSource=physical.isEmpty()?"outline_fallback_no_player_collision":"player_collision";
            } else {
                world.setBlockState(root,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                ItemFrameEntity frame=new ItemFrameEntity(world,root.north(),Direction.NORTH);frame.setHeldItemStack(e.item.copy());frame.setInvulnerable(true);
                NbtCompound fixed=frame.writeNbt(new NbtCompound());fixed.putBoolean("Fixed",true);frame.readNbt(fixed);
                require(world.spawnEntity(frame),"Cannot spawn item frame: "+e.registry);
                actual=new Box(root).union(frame.getBoundingBox());uuid=frame.getUuid();representation="item_frame";boundsSource="frame_and_support";
            }
            require(Math.floor(actual.minX)-2==platform.x&&Math.ceil(actual.maxX)+2==platform.x+platform.width
                &&Math.floor(actual.minZ)-2==platform.z&&Math.ceil(actual.maxZ)+2==platform.z+platform.depth,
                "Final geometry differs from platform footprint +2: "+e.registry+" expected="+platform.expected+" actual="+actual);
            label(new BlockPos(platform.x,64,platform.z),e.name,e.number,e.kind);
            world.setBlockState(new BlockPos(platform.x+platform.width-1,64,platform.z),Blocks.LANTERN.getDefaultState(),Block.NOTIFY_ALL);
            JsonObject row=new JsonObject();row.addProperty("id",e.number);row.addProperty("registry",e.registry.toString());row.addProperty("name",e.name);row.addProperty("kind",e.kind);
            row.addProperty("item",e.item.isEmpty()?"":Registries.ITEM.getId(e.item.getItem()).toString());row.addProperty("uuid",uuid.toString());
            row.addProperty("representation",representation);row.addProperty("boundsSource",boundsSource);row.addProperty("floor",Registries.BLOCK.getId(flooring).toString());
            row.add("root",JSON.toJsonTree(List.of(root.getX(),root.getY(),root.getZ())));row.add("bounds",box(actual));
            row.add("platform",JSON.toJsonTree(List.of(platform.x,platform.z,platform.width,platform.depth)));rows.add(row);
        }
        void label(BlockPos pos,String name,String number,String kind) {
            world.setBlockState(pos,Blocks.OAK_SIGN.getDefaultState(),Block.NOTIFY_ALL);
            if(world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(sign.getFrontText().withMessage(0,Text.literal(name)).withMessage(1,Text.literal(number.isEmpty()?"Предмет":"ID "+number))
                    .withMessage(2,Text.literal(kind)).withMessage(3,Text.literal("Галерея → восток")),true);
                sign.setWaxed(true);sign.markDirty();
            }
        }
        void finish() throws Exception {
            Path pack=server.getSavePath(WorldSavePath.DATAPACKS).resolve("dw_gallery_entry");
            Files.createDirectories(pack.resolve("data/minecraft/tags/functions"));Files.createDirectories(pack.resolve("data/dw_gallery/functions"));
            Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Dreamwalker gallery first entry\"}}");
            Files.writeString(pack.resolve("data/minecraft/tags/functions/tick.json"),"{\"values\":[\"dw_gallery:entry\"]}");
            Files.writeString(pack.resolve("data/dw_gallery/functions/entry.mcfunction"),
                "execute as @a[tag=!dw_gallery_entered] run gamemode creative @s\nexecute as @a[tag=!dw_gallery_entered] run tp @s 0.5 64 -5.5 0 0\ntag @a[tag=!dw_gallery_entered] add dw_gallery_entered\n");
            load(new Box(-2,63,-9,4,68,-2));
            for(int px=-2;px<4;px++)for(int pz=-9;pz<-2;pz++)world.setBlockState(new BlockPos(px,63,pz),Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_ALL);
            label(new BlockPos(0,64,-8),"Dreamwalker BB v11","","Каталог → восток");
            report.addProperty("cataloguePlaced",rows.size());report.addProperty("standPlaced",0);
            for(String kind:List.of("architecture","rp_object","rp_mob","rp_item"))report.addProperty(kind,rows.asList().stream().filter(r->r.getAsJsonObject().get("kind").getAsString().equals(kind)).count());
            report.addProperty("platformMargin",2);report.addProperty("nearestPlatformEdgeGap",2);report.addProperty("layout","single_east_west_sequence");
            report.addProperty("lengthBlocks",nextX-2);report.addProperty("qaRequiredForSavedWorld",false);report.addProperty("actualKeyboardVisualTests","NOT_RUN");
            report.addProperty("rpPlacementScope","Registered authoring instances, not a claim of ordinary player placement testing.");
            server.save(false,true,true);report.addProperty("status","PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN");
            Files.writeString(OUTPUT,JSON.toJson(report));
        }
        void close(){for(ChunkPos chunk:tickets)world.getChunkManager().removeTicket(TICKET,chunk,2,chunk);actor.discard();}
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(!Files.isRegularFile(INPUT))return;
            try {
                JsonObject marker=JsonParser.parseString(Files.readString(INPUT)).getAsJsonObject();
                require("FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY".equals(marker.get("guard").getAsString()),"Wrong gallery guard");
                require("V11".equals(marker.get("revision").getAsString()),"Wrong gallery revision");
                require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world")
                    &&server.getOverworld().getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator&&!Files.exists(OUTPUT),"Fresh isolated flat world required");
                AUTHORS.put(server,new Author(server,marker));
            }catch(Throwable failure){failure(failure);}
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{Author author=AUTHORS.get(server);if(author==null)return;
            try {
                if(author.age++<10)return;
                if(author.index<author.exhibits.size()){author.place(author.exhibits.get(author.index++));return;}
                if(!author.finalizing){author.finalizing=true;author.age=0;return;}
                author.finish();AUTHORS.remove(server);author.close();
            }catch(Throwable failure){AUTHORS.remove(server);author.close();failure(failure);}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{Author author=AUTHORS.remove(server);if(author!=null)author.close();});
    }
    private static void pose(RpObjectEntity object,BlockPos root) {
        String id=object.assetId();Vec3d surface=new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5);
        Vec3d origin=new Vec3d(root.getX(),ObjectRegistry.placementY(id,root.getY()),root.getZ());
        if(RpObjectGeometry.surfaceMounted(id))origin=surface.subtract(RpObjectGeometry.placementAnchor(id,Direction.UP,object.isOpen()).multiply(object.asset().scale()*object.objectScale()));
        object.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);
    }
    private static Box objectBounds(RpObjectEntity object){return object.activePhysicalBoxes().isEmpty()?object.visualBounds():union(object.activePhysicalBoxes());}
    private static Box union(List<Box> boxes){require(!boxes.isEmpty(),"No exhibit geometry");Box out=boxes.get(0);for(int i=1;i<boxes.size();i++)out=out.union(boxes.get(i));return out;}
    private static JsonElement box(Box b){return JSON.toJsonTree(List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void failure(Throwable failure){try{JsonObject report=new JsonObject();report.addProperty("status","FAIL_GALLERY_AUTHORING");report.addProperty("failure",failure.toString());Files.writeString(OUTPUT,JSON.toJson(report));failure.printStackTrace();}catch(Exception ignored){}}
}
