package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.wall.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.*;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornerp.object.*;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.lamp.LampEditor;
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
import net.minecraft.world.gen.chunk.FlatChunkGenerator;

/** Optional authoring add-on, not part of the production JAR or the saved world's requirements. */
public final class CatalogueGalleryBootstrap implements ModInitializer {
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("dw_catalogue_author",Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<MinecraftServer,Author> AUTHORS=new WeakHashMap<>();
    private static final BlockPos VIRTUAL=new BlockPos(0,64,0);
    private record Exhibit(ItemStack item,String kind,DebugCatalogue.Entry entry,String note) {}
    private record Platform(int x,int z,int width,int depth,BlockPos root,Box expected) {}
    private static final class Author {
        final MinecraftServer server;final ServerWorld world;final JsonObject report=new JsonObject();
        final JsonArray rows=new JsonArray();final List<Exhibit> exhibits=new ArrayList<>();final Set<ChunkPos> tickets=new HashSet<>();
        final Map<String,Entity> stand=new HashMap<>();final Map<String,BlockPos> standBlocks=new HashMap<>();
        final ServerPlayerEntity actor;int index,age,x,z,rowDepth;boolean finalizing;
        Author(MinecraftServer s,JsonObject marker) throws Exception {
            server=s;world=s.getOverworld();report.addProperty("status","RUNNING");report.addProperty("productionJarSha256",marker.get("productionJarSha256").getAsString());report.add("exhibits",rows);
            ReviewV10ProofSupport.artifact(marker,report);
            actor=new ServerPlayerEntity(s,world,new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:CatalogueAuthor".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"CatalogueAuthor")) {@Override public boolean hasPermissionLevel(int level){return level<=4;}};
            actor.networkHandler=new ServerPlayNetworkHandler(s,new ClientConnection(NetworkSide.SERVERBOUND) {@Override public void send(Packet<?> p){}@Override public void send(Packet<?> p,PacketCallbacks c){}},actor);
            actor.changeGameMode(GameMode.CREATIVE);actor.getAbilities().allowModifyWorld=true;
            Set<String> seen=new HashSet<>();
            for(ItemStack item:UnifiedCreativeCatalogue.canonicalItems()) {
                Identifier id=Registries.ITEM.getId(item.getItem());var entry=DebugCatalogue.entry(item);
                require(entry!=null,"Working catalogue item has no reserved ID: "+id);
                if(item.getItem() instanceof SpawnEggItem)continue; // A real NPC is exhibited instead.
                require(seen.add(entry.registryId().toString()),"Repeated offered type: "+entry.registryId());
                exhibits.add(new Exhibit(item,entry.kind(),entry,""));
            }
            for(var type:MobRegistry.TYPES.entrySet()) {
                var entry=DebugCatalogue.entry(Registries.ENTITY_TYPE.getId(type.getValue()));require(entry!=null,"NPC ID missing: "+type.getKey());
                require(seen.add(entry.registryId().toString()),"NPC duplicated by an item");exhibits.add(new Exhibit(Registries.ITEM.get(new Identifier("bloodborne_rp",type.getKey()+"_spawn_egg")).getDefaultStack(),"rp_mob",entry,""));
            }
            report.addProperty("catalogueTypes",exhibits.size());report.addProperty("expectedCatalogueIds",String.join(",",exhibits.stream().map(e->e.entry.temporaryId()).toList()));
            for(String path:List.of("lever_1","lever_2","door_1","door_2","hunterlamp","hunterlamp","hunterlamp","cage_obj_1","cage_obj_2","cage_obj_3")) {
                ItemStack item=Registries.ITEM.get(new Identifier("bloodborne_rp",path+"_placer")).getDefaultStack();
                String note=path.equals("hunterlamp")?"lamp_"+(exhibits.stream().filter(e->e.note.startsWith("lamp_")).count()+1):"stand_"+path;
                exhibits.add(new Exhibit(item,"rp_object",DebugCatalogue.entry(item),note));
            }
            for(String path:List.of("prototype_double_door","prototype_thin_window","prototype_glass_window_02","prototype_glass_window_03","prototype_wall","prototype_wall_skin_1","prototype_ladder","prototype_ladder")) {
                ItemStack item=Registries.ITEM.get(new Identifier("bloodborne_dw",path)).getDefaultStack();
                String note=path.equals("prototype_ladder")?(exhibits.stream().anyMatch(e->e.note.equals("ladder_free"))?"ladder_supported":"ladder_free"):"stand_"+path;
                exhibits.add(new Exhibit(item,"architecture",DebugCatalogue.entry(item),note));
            }
            ticket(new Box(-8,60,-12,12,72,12));world.setSpawnPos(new BlockPos(0,64,-6),0);
            world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,s);world.setTimeOfDay(6000);
            world.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false,s);
            world.getGameRules().get(net.minecraft.world.GameRules.DO_WEATHER_CYCLE).set(false,s);
            s.setDefaultGameMode(GameMode.CREATIVE);
        }
        void ticket(Box box) {
            for(int cx=MathHelper.floor(box.minX)>>4;cx<=MathHelper.floor(box.maxX)>>4;cx++)for(int cz=MathHelper.floor(box.minZ)>>4;cz<=MathHelper.floor(box.maxZ)>>4;cz++) {
                ChunkPos chunk=new ChunkPos(cx,cz);if(tickets.add(chunk))world.getChunkManager().addTicket(TICKET,chunk,2,chunk);world.getChunk(cx,cz);
            }
        }
        Box prototypeBounds(Exhibit e) {
            if(e.kind.equals("rp_object")) {
                var object=ObjectRegistry.TYPES.get(e.entry.registryId().getPath()).create(world);pose(object,VIRTUAL);return bounds(object);
            }
            if(e.kind.equals("rp_mob")) {var mob=MobRegistry.TYPES.get(e.entry.registryId().getPath()).create(world);mob.setPosition(.5,64,.5);return mob.getBoundingBox();}
            if(e.item.getItem() instanceof BlockItem item) {
                var state=item.getBlock().getDefaultState();
                if(e.note.equals("stand_prototype_glass_window_02"))state=state.with(ThinWindowRootBlock.MOUNT,ThinWindowRootBlock.Mount.FLOOR);
                if(e.note.equals("stand_prototype_glass_window_03"))state=state.with(ThinWindowRootBlock.MOUNT,ThinWindowRootBlock.Mount.CEILING);
                if(state.getBlock() instanceof PrototypeLadderBlock)state=PrototypeLadderBlock.withYaw(state.with(PrototypeLadderBlock.FREESTANDING,true),e.note.startsWith("ladder_")?1:0);
                var physical=PlacementPhysics.physicalBoxes(CompositeRuntime.instance(world,VIRTUAL,state,UUID.randomUUID(),new NbtCompound()));
                if(!physical.isEmpty())return union(physical);
                return state.getOutlineShape(world,VIRTUAL).getBoundingBox().offset(VIRTUAL);
            }
            return new Box(0,64,-1,1,66,1);
        }
        Platform allocate(Box original) {
            int lowX=(int)Math.floor(original.minX),lowZ=(int)Math.floor(original.minZ);
            BlockPos root=new BlockPos(x+2-lowX,64,z+2-lowZ);Box actual=original.offset(root.getX(),0,root.getZ());
            // Round the final world bounds: a tiny cos(90deg) residue at origin can disappear
            // after integer translation. Pre-translation ceil would add an unwanted whole block.
            root=root.add(x+2-(int)Math.floor(actual.minX),0,z+2-(int)Math.floor(actual.minZ));
            actual=original.offset(root.getX(),0,root.getZ());
            int width=(int)Math.ceil(actual.maxX)-(int)Math.floor(actual.minX)+4;
            int depth=(int)Math.ceil(actual.maxZ)-(int)Math.floor(actual.minZ)+4;
            if(x!=0&&x+width>128){x=0;z+=rowDepth+2;rowDepth=0;return allocate(original);}
            require(Math.floor(actual.minX)-2==x&&Math.floor(actual.minZ)-2==z,"Final world platform origin differs");
            Platform platform=new Platform(x,z,width,depth,root,actual);x+=width+2;rowDepth=Math.max(rowDepth,depth);return platform;
        }
        void place(Exhibit e) {
            // NPCs use their physical dimensions; collision-free RP uses authored visual bounds.
            Platform platform=allocate(prototypeBounds(e));BlockPos root=platform.root;ticket(platform.expected.expand(32));
            Block flooring=e.note.isEmpty()?e.kind.equals("rp_mob")?Blocks.DEEPSLATE_TILES:e.kind.equals("architecture")?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE:Blocks.POLISHED_BLACKSTONE;
            for(int px=platform.x;px<platform.x+platform.width;px++)for(int pz=platform.z;pz<platform.z+platform.depth;pz++)world.setBlockState(new BlockPos(px,63,pz),flooring.getDefaultState(),Block.NOTIFY_ALL);
            Box actual;UUID uuid=null;String representation;
            actor.setPosition(root.getX()+.5,64,root.getZ()-4);actor.setYaw(0);actor.setPitch(0);actor.setSneaking(true);actor.setStackInHand(Hand.MAIN_HAND,e.item.copy());actor.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
            if(e.kind.equals("rp_object")) {
                RpObjectEntity object=ObjectRegistry.TYPES.get(e.entry.registryId().getPath()).create(world);pose(object,root);ticket(object.queryBounds());
                if(e.note.equals("stand_cage_obj_2"))object.setDogsVisible(false);
                // Authoring creates genuine registered instances; not claimed as a user-placement test.
                require(world.spawnEntity(object),"Cannot spawn exhibit: "+e.entry.temporaryId());actual=bounds(object);uuid=object.getUuid();representation="registered_rp_entity";
                if(!e.note.isEmpty())stand.put(e.note,object);
            } else if(e.kind.equals("rp_mob")) {
                var mob=MobRegistry.TYPES.get(e.entry.registryId().getPath()).create(world);mob.refreshPositionAndAngles(root.getX()+.5,64,root.getZ()+.5,180,0);mob.setAiDisabled(true);mob.setPersistent();mob.setInvulnerable(true);
                require(world.spawnEntity(mob),"Cannot spawn real NPC");actual=mob.getBoundingBox();uuid=mob.getUuid();representation="registered_npc_noai_persistent";
            } else if(e.item.getItem() instanceof BlockItem) {
                if(e.note.startsWith("ladder_"))actor.setYaw(45);
                if(e.note.equals("stand_prototype_glass_window_02"))actor.setSneaking(false);
                boolean ceiling=e.note.equals("stand_prototype_glass_window_03");
                if(ceiling)world.setBlockState(root.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                ActionResult result=e.item.getItem().useOnBlock(new ItemUsageContext(actor,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(root.getX()+.5,ceiling?65:64,root.getZ()+.5),ceiling?Direction.DOWN:Direction.UP,ceiling?root.up():root.down(),false)));
                require(result.isAccepted(),"Real block item placement refused "+e.entry.temporaryId()+" "+result);
                if(e.note.equals("ladder_supported"))for(Direction side:PrototypeLadderBlock.backingDirections(world.getBlockState(root)))world.setBlockState(root.offset(side),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                var own=VerticalMount.owner(world,root);require(own!=null,"Placed architecture root has no UUID");uuid=own.instanceId();actual=union(PlacementPhysics.physicalBoxes(CompositeRuntime.instance(world,root,world.getBlockState(root),uuid,((CompositeBlockEntity)world.getBlockEntity(root)).payload())));representation="real_architecture_root";
                if(e.note.equals("stand_prototype_thin_window"))require(VerticalMount.setOffset(world,root,.125,actor),"height example placement");
                if(!e.note.isEmpty())standBlocks.put(e.note,root);
            } else {
                world.setBlockState(root,Blocks.STONE.getDefaultState());ItemFrameEntity frame=new ItemFrameEntity(world,root.north(),Direction.NORTH);frame.setHeldItemStack(e.item.copy());frame.setInvulnerable(true);NbtCompound fixed=frame.writeNbt(new NbtCompound());fixed.putBoolean("Fixed",true);frame.readNbt(fixed);
                require(world.spawnEntity(frame),"Cannot spawn item frame");actual=new Box(root.getX(),64,root.getZ()-1,root.getX()+1,66,root.getZ()+1);uuid=frame.getUuid();representation="item_frame";
            }
            require(Math.floor(actual.minX)-2==platform.x&&Math.ceil(actual.maxX)+2==platform.x+platform.width&&Math.floor(actual.minZ)-2==platform.z&&Math.ceil(actual.maxZ)+2==platform.z+platform.depth,"Platform must exactly match final physical bounds plus2: "+e.entry.temporaryId());
            label(new BlockPos(platform.x,64,platform.z),e.entry.name(),e.entry.temporaryId(),e.note.isEmpty()?e.kind:e.note);
            world.setBlockState(new BlockPos(platform.x+platform.width-1,64,platform.z),Blocks.LANTERN.getDefaultState(),Block.NOTIFY_ALL);
            JsonObject row=new JsonObject();row.addProperty("id",e.entry.temporaryId());row.addProperty("registry",e.entry.registryId().toString());row.addProperty("name",e.entry.name());row.addProperty("kind",e.kind);row.addProperty("uuid",uuid.toString());row.addProperty("representation",representation);row.addProperty("stand",!e.note.isEmpty());row.addProperty("note",e.note);row.add("root",vector(root));row.add("bounds",box(actual));row.add("platform",new Gson().toJsonTree(List.of(platform.x,platform.z,platform.width,platform.depth)));row.add("capabilities",capabilities(e,root,uuid));rows.add(row);
        }
        JsonObject capabilities(Exhibit e,BlockPos root,UUID uuid) {
            JsonObject out=new JsonObject();boolean architecture=e.kind.equals("architecture"),rp=e.kind.equals("rp_object");
            out.addProperty("builderEditable",architecture||rp);if(!architecture&&!rp)return out;
            out.addProperty("height",true);out.addProperty("resetHeight",true);
            if(rp) {
                var object=(RpObjectEntity)world.getEntity(uuid);var status=MechanismLinks.inspect(server,MechanismLinks.TargetRef.rp(object));
                out.addProperty("rotationStepDegrees",45);out.addProperty("profile",false);out.addProperty("mount",false);out.addProperty("dogs",object.supportsDogVisibility());
                out.addProperty("lever",object.isMechanism());out.addProperty("lamp",object.assetId().equals("hunterlamp"));out.addProperty("openClose",status.availability()==MechanismLinks.Availability.LOADED&&!status.pulseOnly());out.addProperty("pulse",status.pulseOnly());
                out.addProperty("link",object.isMechanism()||object.canBeLinked()||object.assetId().equals("hunterlamp"));out.addProperty("physicalPartCount",object.activePhysicalBoxes().size());
            } else {
                var state=world.getBlockState(root);var status=MechanismLinks.inspect(server,VerticalMount.reference(world,root));
                out.addProperty("rotationStepDegrees",state.getBlock() instanceof ThinWindowRootBlock||state.getBlock() instanceof PrototypeWallBlock&&PrototypeWallBlock.retainedFence(state)?90:45);
                out.addProperty("profile",state.contains(CompositeRootBlock.PROFILE)||state.getBlock() instanceof PrototypeLadderBlock||state.getBlock() instanceof PrototypeWallBlock&&!PrototypeWallBlock.diagonalPost(state));out.addProperty("mount",state.contains(ThinWindowRootBlock.MOUNT));out.addProperty("dogs",false);
                out.addProperty("openClose",status.availability()==MechanismLinks.Availability.LOADED&&!status.pulseOnly());out.addProperty("pulse",status.pulseOnly());out.addProperty("link",status.availability()==MechanismLinks.Availability.LOADED);
            }
            return out;
        }
        void label(BlockPos pos,String name,String id,String note) {
            world.setBlockState(pos,Blocks.OAK_SIGN.getDefaultState(),Block.NOTIFY_ALL);
            if(world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                var text=sign.getFrontText().withMessage(0,Text.literal(name)).withMessage(1,Text.literal("ID "+id)).withMessage(2,Text.literal(note)).withMessage(3,Text.literal("90009: ПКМ меню"));sign.setText(text,true);sign.setWaxed(true);sign.markDirty();
            }
        }
        void finish() throws Exception {
            // The optional stand uses the same persistent production graph as ordinary objects.
            RpObjectEntity lever=(RpObjectEntity)stand.get("stand_lever_1"),door=(RpObjectEntity)stand.get("stand_door_1");
            require(lever.addLink(door),"stand RP link");require(lever.addLink((RpObjectEntity)stand.get("stand_door_2")),"stand third target link");var doorRoot=standBlocks.get("stand_prototype_double_door");require(MechanismLinks.link(lever,VerticalMount.reference(world,doorRoot)),"stand architecture link");
            RpObjectEntity lever2=(RpObjectEntity)stand.get("stand_lever_2");require(lever2.addLink(door),"second lever stand link");
            var a=(RpObjectEntity)stand.get("lamp_1");actor.setPosition(a.getX(),a.getY(),a.getZ()-2);require(LampEditor.selectSource(actor,a.getUuid()).success(),"lamp A source");
            for(String key:List.of("lamp_2","lamp_3")){var b=(RpObjectEntity)stand.get(key);actor.setPosition(b.getX(),b.getY(),b.getZ()-2);require(LampEditor.edit(actor,new LampEditor.Request(LampEditor.Action.CONNECT,a.getUuid(),b.getUuid(),"Галерея","",true)).success(),"bidirectional lamp route");}
            Path pack=server.getSavePath(WorldSavePath.DATAPACKS).resolve("dw_gallery_entry");Files.createDirectories(pack.resolve("data/minecraft/tags/functions"));Files.createDirectories(pack.resolve("data/dw_gallery/functions"));
            Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Gallery first entry, vanilla functions\"}}");
            Files.writeString(pack.resolve("data/minecraft/tags/functions/tick.json"),"{\"values\":[\"dw_gallery:entry\"]}");
            Files.writeString(pack.resolve("data/dw_gallery/functions/entry.mcfunction"),"execute as @a[tag=!dw_gallery_entered] run gamemode creative @s\nexecute as @a[tag=!dw_gallery_entered] run give @s bloodborne_dw:composite_builder\nexecute as @a[tag=!dw_gallery_entered] run tp @s 0.5 64 -5.5 0 0\ntag @a[tag=!dw_gallery_entered] add dw_gallery_entered\n");
            label(new BlockPos(0,64,-8),"Bloodborne · галерея","90009","Каталог впереди →");
            report.addProperty("cataloguePlaced",rows.asList().stream().filter(r->!r.getAsJsonObject().get("stand").getAsBoolean()).count());report.addProperty("standPlaced",rows.size()-report.get("cataloguePlaced").getAsInt());
            for(String kind:List.of("architecture","rp_object","rp_mob","rp_item","technical_tool"))report.addProperty(kind,rows.asList().stream().filter(r->!r.getAsJsonObject().get("stand").getAsBoolean()&&r.getAsJsonObject().get("kind").getAsString().equals(kind)).count());
            report.addProperty("platformMargin",2);report.addProperty("nearestPlatformEdgeGap",2);report.addProperty("temporaryAuthorChunks",tickets.size());report.addProperty("qaRequiredForSavedWorld",false);report.addProperty("actualKeyboardVisualTests","NOT_RUN");
            server.save(false,true,true);report.addProperty("status","PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN");
            Files.writeString(Path.of("catalogue-gallery-output.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
        void close(){for(ChunkPos chunk:tickets)world.getChunkManager().removeTicket(TICKET,chunk,2,chunk);actor.discard();}
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->{Path input=Path.of("catalogue-gallery-input.json");if(!Files.isRegularFile(input))return;
            try {var marker=JsonParser.parseString(Files.readString(input)).getAsJsonObject();require("FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY".equals(marker.get("guard").getAsString()),"Wrong gallery guard");require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world")&&server.getOverworld().getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator&&!Files.exists(Path.of("catalogue-gallery-output.json")),"Only fresh isolated flat authoring is allowed");AUTHORS.put(server,new Author(server,marker));}catch(Throwable failure){failure(failure);}
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{Author author=AUTHORS.get(server);if(author==null)return;try {
            if(author.age++<10)return;
            if(author.index<author.exhibits.size()){author.place(author.exhibits.get(author.index++));return;}
            if(!author.finalizing){author.finalizing=true;author.age=0;return;}
            author.finish();AUTHORS.remove(server);author.close();
        }catch(Throwable failure){AUTHORS.remove(server);author.close();failure(failure);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{Author author=AUTHORS.remove(server);if(author!=null)author.close();});
    }
    private static void pose(RpObjectEntity object,BlockPos root) {
        String id=object.assetId();Vec3d surface=new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5),origin=new Vec3d(root.getX(),ObjectRegistry.placementY(id,root.getY()),root.getZ());
        if(RpObjectGeometry.surfaceMounted(id))origin=surface.subtract(RpObjectGeometry.placementAnchor(id,Direction.UP,object.isOpen()).multiply(object.asset().scale()*object.objectScale()));
        object.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);
    }
    private static Box bounds(RpObjectEntity object){return object.activePhysicalBoxes().isEmpty()?object.visualBounds():union(object.activePhysicalBoxes());}
    private static Box union(List<Box> boxes){require(!boxes.isEmpty(),"No exhibition geometry");Box box=boxes.get(0);for(int i=1;i<boxes.size();i++)box=box.union(boxes.get(i));return box;}
    private static JsonArray vector(BlockPos p){return new Gson().toJsonTree(List.of(p.getX(),p.getY(),p.getZ())).getAsJsonArray();}
    private static JsonArray box(Box b){return new Gson().toJsonTree(List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).getAsJsonArray();}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
    private static void failure(Throwable failure){try {JsonObject report=new JsonObject();report.addProperty("status","FAIL_GALLERY_AUTHORING");report.addProperty("failure",failure.getClass().getName()+": "+failure.getMessage());Files.writeString(Path.of("catalogue-gallery-output.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));failure.printStackTrace();}catch(Exception ignored){}}
}
