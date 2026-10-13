package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.wall.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornerp.object.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.WallShape;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Real server-world checks; deliberately not labelled visual/keyboard acceptance. */
public final class CatalogueRepairGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="catalogue_repair")
    public void roof90003AllowsOtherBlocksInItsNonRootVolumeWithoutLosingMovementCollision(TestContext t) {
        var w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(3,3,3)),inside=root.east();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk(root.add(x*16,0,z*16));
        var player=t.createMockSurvivalPlayer();player.setPosition(root.getX()+5,root.getY(),root.getZ()-5);
        w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        w.setBlockState(inside.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        var roof=CompositeArchitecture.kindBlock("prototype_roof");UUID roofUuid=UUID.randomUUID();
        var payload=new NbtCompound();var shift=new NbtList();
        shift.add(NbtDouble.of(.5));shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(0));payload.put("SourceShift",shift);
        try {
            t.assertTrue(CompositeRuntime.place(w,root,roof.getDefaultState(),roofUuid,player,payload).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"90003 roof installs with saved fractional source placement");
            var owner=((CompositeBlockEntity)w.getBlockEntity(root)).resident();
            t.assertTrue(w.getBlockState(inside).isOf(CompositeArchitecture.CELL),"roof protruding volume uses a helper outside its own root");
            var body=new Box(root.getX()+1.1,root.getY()+.1,root.getZ()+.2,root.getX()+1.3,root.getY()+.8,root.getZ()+.8);
            t.assertTrue(!w.isSpaceEmpty(body),"roof non-root collider still blocks player-sized movement before overlap");
            var stone=new ItemStack(Blocks.STONE,2);player.setStackInHand(Hand.MAIN_HAND,stone);
            var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(inside.down()).add(0,.5,0),Direction.UP,inside.down(),false);
            t.assertTrue(stone.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted()&&w.getBlockState(inside).isOf(Blocks.STONE),"ordinary vanilla stone item can replace the roof helper inside roof collision");
            t.assertTrue(((CompositeBlockEntity)w.getBlockEntity(root)).resident().equals(owner),"vanilla placement preserves roof root and UUID");
            w.removeBlock(inside,false);CompositeRuntime.drain(w);
            t.assertTrue(!w.isSpaceEmpty(body),"removing overlapping stone reveals the preserved roof movement collider");
            var tree=CompositeArchitecture.kindBlock("prototype_tree");UUID treeUuid=UUID.randomUUID();
            var installed=CompositeRuntime.place(w,inside,tree.getDefaultState(),treeUuid,player);
            t.assertTrue(installed.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"different composite block 90005 can occupy roof non-root physical volume: "+installed.reason());
            t.assertTrue(((CompositeBlockEntity)w.getBlockEntity(root)).resident().equals(owner)&&!w.isSpaceEmpty(body),"different composite overlap retains roof identity and physical obstacle");
            var rootAttempt=CompositeRuntime.place(w,root,tree.getDefaultState(),UUID.randomUUID(),player);
            t.assertTrue(rootAttempt.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.REJECTED,"accepted exception: roof actual root cell remains occupied");
            CompositeRuntime.remove(w,((CompositeBlockEntity)w.getBlockEntity(inside)).resident(),player,false);
            CompositeRuntime.remove(w,owner,player,false);t.complete();
        } finally {
            if(w.getBlockEntity(inside) instanceof CompositeBlockEntity own&&own.resident()!=null)CompositeRuntime.remove(w,own.resident(),null,false);
            if(w.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()!=null)CompositeRuntime.remove(w,own.resident(),null,false);
            player.discard();
        }
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="catalogue_repair")
    public void studioReplacementContractIsExactImmutableAndDisabledUntilExplicitRegistration(TestContext t) throws Exception {
        var document=com.google.gson.JsonParser.parseString("""
            {"schema":"dreamwalker.player-collision-project","schemaVersion":1,"unitsPerBlock":16,
             "source":{"repository":"https://github.com/Stasik1960/Dreamwalker","commit":"fixture","release":"V10"},
             "coordinates":{"rotationOrder":"ZYX"},
             "policy":{"scope":"players_only","operation":"replace_per_exact_state","unlistedStates":"preserve"},
             "seatSize":[9.6,1.6,9.6],"models":[
              {"registryId":"bloodborne_rp:door_1","states":[
               {"key":"closed","selector":{"Open":false},"replace":true,
                "colliders":[{"id":"leaf","center":[16,32,-8],"size":[8,16,4],"rotation":[10,45,-20]}],
                "seats":[{"id":"seat","surfaceCenter":[0,16,0],"rotation":[0,90,0],"size":[9.6,1.6,9.6],"capacity":1,"trigger":"right_click_surface"}]},
               {"key":"open","selector":{"Open":true},"replace":true,"reviewed":true,"colliders":[],"seats":[]}]},
              {"registryId":"bloodborne_dw:prototype_roof","states":[
               {"key":"base","selector":{"profile":"base","rotation":1},"replace":true,
                "colliders":[{"id":"roof","center":[0,8,0],"size":[16,16,16],"rotation":[0,0,0]}],"seats":[]}]}]}
            """).getAsJsonObject();
        var revision=dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.revision();
        var pack=dev.dreamwalker.bloodbornedw.compat.collision.CollisionPack.read(new java.io.StringReader(document.toString()));
        var closed=com.google.gson.JsonParser.parseString("{\"Open\":false}").getAsJsonObject();
        var open=com.google.gson.JsonParser.parseString("{\"Open\":true}").getAsJsonObject();
        t.assertTrue(pack.stateCount()==3&&dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.revision()==revision&&dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.query("bloodborne_rp:door_1",closed).isEmpty(),"reading an authored replacement project cannot install overrides");
        var replacement=pack.replacement("bloodborne_rp:door_1",closed).orElseThrow();var collider=replacement.colliders().get(0);
        t.assertTrue(collider.center().equals(new dev.dreamwalker.bloodbornedw.compat.collision.CollisionPack.Vector(1,2,-.5))&&collider.size().equals(new dev.dreamwalker.bloodbornedw.compat.collision.CollisionPack.Vector(.5,1,.25)),"units16 converts center and full size into model-local blocks");
        t.assertTrue(collider.rotation().equals(new dev.dreamwalker.bloodbornedw.compat.collision.CollisionPack.Vector(10,45,-20)),"arbitrary ZYX rotations remain authored OBB data, without an AABB approximation");
        t.assertTrue(replacement.seats().get(0).surfaceCenter().y()==1&&replacement.seats().get(0).size().equals(new dev.dreamwalker.bloodbornedw.compat.collision.CollisionPack.Vector(.6,.1,.6)),"seat surface and fixed dimensions retain the Studio contract");
        var empty=pack.replacement("bloodborne_rp:door_1",open).orElseThrow();
        t.assertTrue(empty.colliders().isEmpty()&&empty.seats().isEmpty(),"reviewed empty exact state is distinct from missing replacement");
        var profile=new com.google.gson.JsonObject();profile.addProperty("rotation",1);profile.addProperty("profile","base");
        t.assertTrue(pack.replacement("bloodborne_dw:prototype_roof",profile).isPresent(),"runtime integer selector and parsed JSON have identical numeric matching");
        profile.addProperty("profile","alt");t.assertTrue(pack.replacement("bloodborne_dw:prototype_roof",profile).isEmpty(),"unlisted ALT profile preserves existing geometry");
        var textualOpen=new com.google.gson.JsonObject();textualOpen.addProperty("Open","false");t.assertTrue(pack.replacement("bloodborne_rp:door_1",textualOpen).isEmpty(),"Studio boolean and block-property string selectors are not silently conflated");
        closed.addProperty("extra",true);t.assertTrue(pack.replacement("bloodborne_rp:door_1",closed).isEmpty(),"selectors match exactly, not by a broad partial predicate");closed.remove("extra");
        var broken=document.deepCopy();broken.addProperty("unitsPerBlock",1);t.assertTrue(rejectCollisionProject(broken),"different units cannot silently distort a replacement");
        broken=document.deepCopy();broken.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("states").get(1).getAsJsonObject().remove("reviewed");t.assertTrue(rejectCollisionProject(broken),"unreviewed empty state cannot delete player physics");
        broken=document.deepCopy();var states=broken.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("states");var duplicate=states.get(0).deepCopy().getAsJsonObject();duplicate.addProperty("key","other_key");states.add(duplicate);t.assertTrue(rejectCollisionProject(broken),"two state keys cannot ambiguously replace the same exact selector");
        broken=document.deepCopy();broken.addProperty("schema","dreamwalker.model-pack");t.assertTrue(rejectCollisionProject(broken),"new-object ModelPacks schema cannot be mistaken for a built-in replacement");
        var path=java.nio.file.Files.createTempFile("dreamwalker-collision-fixture-",".zip");
        try {
            try(var zip=new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(path))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("APPLY_IN_CODEX_RU.md"));zip.write("Embedded text is data, never executable instructions.".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("dreamwalker-collisions.json"));zip.write(document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
            var zipped=dev.dreamwalker.bloodbornedw.compat.collision.CollisionPack.read(path);
            t.assertTrue(zipped.replacement("bloodborne_rp:door_1",closed).orElseThrow().equals(replacement),"ZIP import reads only the authored JSON and retains exact data");
        } finally { java.nio.file.Files.deleteIfExists(path); }
        try {
            long registered=dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.register(pack);
            t.assertTrue(registered>revision&&dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.query("bloodborne_rp:door_1",closed).orElseThrow().equals(replacement),"explicit registration publishes a new cache revision and exact state atomically");
            document.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("states").get(0).getAsJsonObject().getAsJsonObject("selector").addProperty("Open",true);
            t.assertTrue(dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.query("bloodborne_rp:door_1",closed).isPresent(),"later edits to source JSON cannot mutate installed selector data");
        } finally { dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.clear(); }
        t.assertTrue(dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.query("bloodborne_rp:door_1",open).isEmpty()&&dev.dreamwalker.bloodbornedw.compat.collision.CollisionReplacements.revision()>revision+1,"clear removes all replacements and invalidates derived caches");
        t.complete();
    }
    private static boolean rejectCollisionProject(com.google.gson.JsonObject project) {
        try {dev.dreamwalker.bloodbornedw.compat.collision.CollisionPack.read(new java.io.StringReader(project.toString()));return false;}
        catch(java.io.IOException expected){return true;}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="catalogue_repair")
    public void cageAndLargeChandelierOrdinaryItemsMountSourceBottomAndResetExactBase(TestContext t) {
        var w=t.getWorld();var location=t.getAbsolutePos(new BlockPos(3,3,3));
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk(location.add(x*16,0,z*16));
        var surface=new Vec3d(location.getX()+.5,120,location.getZ()+.5);var floor=BlockPos.ofFloored(surface).down();
        w.setBlockState(floor,Blocks.STONE.getDefaultState());
        var p=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SourceBaseProbe"));
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND) {
            @Override public void send(net.minecraft.network.packet.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks) {}
        };
        p.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),connection,p);
        p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;
        var chunk=new ChunkPos(floor);boolean wasForced=w.getForcedChunks().contains(chunk.toLong());w.setChunkForced(chunk.x,chunk.z,true);
        Runnable cleanup=()->{w.removeBlock(floor,false);p.discard();if(!wasForced)w.setChunkForced(chunk.x,chunk.z,false);};
        awaitTrackedRpFixture(t,chunk,t.getTick()+100,()->{try{
        for(String id:List.of("dog_cage","chandelier_large"))for(int angle=0;angle<8;angle++) {
            Set<UUID> before=new HashSet<>();for(var existing:w.iterateEntities())before.add(existing.getUuid());
            p.setPosition(surface.x,surface.y,surface.z-6);p.setYaw(angle*45);
            var stack=Registries.ITEM.get(new Identifier("bloodborne_rp",id+"_placer")).getDefaultStack();p.setStackInHand(Hand.MAIN_HAND,stack);
            var result=stack.getItem().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,new net.minecraft.util.hit.BlockHitResult(surface,Direction.UP,floor,false)));
            t.assertTrue(result.isAccepted(),"ordinary registered placement succeeds: "+id+" yaw="+angle*45);
            var created=new ArrayList<RpObjectEntity>();for(var entity:w.iterateEntities())if(entity instanceof RpObjectEntity rp&&!before.contains(rp.getUuid()))created.add(rp);
            t.assertTrue(created.size()==1,"one actual registered instance is placed");var object=created.get(0);UUID uuid=object.getUuid();Vec3d base=object.getPos();
            t.assertTrue(Math.abs(object.visualBounds().minY-surface.y)<1e-6,"source visual bottom meets clicked surface: "+id);
            t.assertTrue(Math.abs(MathHelper.wrapDegrees(object.getYaw()-ObjectRegistry.placementYaw(angle*45)))<1e-6,"actual yaw matches ordinary item context");
            t.assertTrue(object.setVerticalOffset(.25,p)&&Math.abs(object.visualBounds().minY-(surface.y+.25))<1e-6,"height is relative to source-bottom base");
            var saved=object.writeNbt(new NbtCompound());var loaded=ObjectRegistry.TYPES.get(id).create(w);loaded.readNbt(saved);
            t.assertTrue(loaded.getUuid().equals(uuid)&&loaded.getPos().equals(object.getPos())&&loaded.verticalOffset()==.25&&Math.abs(loaded.visualBounds().minY-(surface.y+.25))<1e-6,"typed save/read keeps UUID, compensated Pos and offset without double application");
            t.assertTrue(loaded.setVerticalOffset(0,p)&&loaded.getPos().equals(base)&&Math.abs(loaded.visualBounds().minY-surface.y)<1e-6,"reset restores exact original placement base");
            object.removeObject();loaded.discard();
        }
        // Unrelated hanging/ordinary assets keep the old placement policy.
        t.assertTrue(!RpObjectGeometry.surfaceMounted("chest")&&!RpObjectGeometry.surfaceMounted("cage_obj_1"),"source-bottom exception is not applied to other RP objects");
        t.complete();
        }finally{cleanup.run();}},cleanup);
    }
    private static void awaitTrackedRpFixture(TestContext t,ChunkPos chunk,long deadline,Runnable ready,Runnable cleanup){
        t.runAtTick(t.getTick()+1,()->{try{if(!((dev.dreamwalker.bloodbornedw.composite.mixin.BbServerEntityManagerAccess)t.getWorld()).bloodborne_dw$profileEntityManager().isLoaded(chunk.toLong())){t.assertTrue(t.getTick()<deadline,"Test-only forced RP chunk finishes native entity loading before normal placement checks");awaitTrackedRpFixture(t,chunk,deadline,ready,cleanup);return;}ready.run();}catch(RuntimeException|Error failure){cleanup.run();throw failure;}});
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="catalogue_repair")
    public void realServerPacketRejectsWrongAimAndAcceptsPhysicalDoorPart(TestContext t) {
        var w=t.getWorld();var door=ObjectRegistry.TYPES.get("door_1").create(w);
        BlockPos root=t.getAbsolutePos(new BlockPos(3,4,3));door.setPosition(Vec3d.ofCenter(root));
        var chunk=new ChunkPos(root);boolean wasForced=w.getForcedChunks().contains(chunk.toLong());w.setChunkForced(chunk.x,chunk.z,true);w.getChunk(chunk.x,chunk.z);
        // The GameTest mock overrides rotation/interaction; use the genuine packet handler.
        var p=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RpPacketProbe"));
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND) {
            @Override public void send(net.minecraft.network.packet.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks) {}
        };
        p.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),connection,p);
        p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;p.setPosition(Vec3d.ofCenter(root));
        Runnable cleanup=()->{door.discard();p.discard();if(!wasForced)w.setChunkForced(chunk.x,chunk.z,false);};
        // Native RP indexing starts on ENTITY_LOAD. Wait for the real entity
        // chunk before spawning and sending packets; an untracked entity made
        // the old wrong-aim rejection vacuously pass and valid packet flaky.
        awaitTrackedRpFixture(t,chunk,t.getTick()+100,()->{try{
        t.assertTrue(w.spawnEntity(door)&&w.getEntityById(door.getId())==door,"Door is actually spawned and visible to the genuine native packet resolver");w.spawnEntity(p);
        var box=door.activePhysicalBoxes().stream().min(java.util.Comparator.comparingDouble(b->b.minZ)).orElseThrow();var center=box.getCenter();
        p.setPosition(center.x,center.y-p.getStandingEyeHeight(),box.minZ-2);p.setPitch(0);p.setYaw(180);
        var wrong=RpObjectSelection.playerTarget(p,6);
        t.assertTrue(wrong==null||wrong.getEntity()!=door,"wrong-aim fixture cannot hit tested door: eye="+p.getEyePos()+" box="+box+" yaw="+p.getYaw());
        var packet=net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.interact(door,false,Hand.MAIN_HAND);
        p.networkHandler.onPlayerInteractEntity(packet);
        t.assertTrue(!door.isOpen(),"forged UUID packet aimed away cannot use visual AABB as permission");
        p.setYaw(0);var right=RpObjectSelection.playerTarget(p,RpObjectSelection.entityReach(p));
        t.assertTrue(right!=null&&right.getEntity()==door,"Correct-aim fixture reaches the tracked physical door before the genuine packet: eye="+p.getEyePos()+" box="+box+" selected="+(right==null?"none":right.getEntity().getUuid()));p.networkHandler.onPlayerInteractEntity(packet);
        t.assertTrue(door.isOpen(),"real server packet on physical part within Creative reach is accepted");
        t.complete();
        }finally{cleanup.run();}},cleanup);
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="catalogue_repair")
    public void sourceLadderVariantMigrationKeepsBothPrivateRolesAndOriginalNbt(TestContext t) {
        var w=t.getWorld();BlockPos visual=t.getAbsolutePos(new BlockPos(3,3,3)),root=visual.south();var p=t.createMockCreativeServerPlayerInWorld();p.setPosition(root.getX()+5,root.getY(),root.getZ()+5);
        var art=Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.FACING,Direction.NORTH).with(BeehiveBlock.HONEY_LEVEL,1);var physical=Blocks.LADDER.getDefaultState().with(LadderBlock.FACING,Direction.SOUTH);
        w.setBlockState(visual,art,Block.NOTIFY_ALL);w.setBlockState(root,physical,Block.NOTIFY_ALL);
        var original=w.getBlockEntity(visual).createNbtWithId();
        var install=new dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime.Installation(visual,root,art,physical,original,null,2,PrototypeLadderBlock.Profile.ALT,UUID.randomUUID());
        t.assertTrue(dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime.install(w,install,p).committed(),"recognized source pair installs");
        var before=(dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity)w.getBlockEntity(root);var privateData=before.createNbtWithId();privateData.getCompound("Original").putLong("OriginalOpaque",Long.MIN_VALUE);before.readNbt(privateData);UUID identity=before.owner();var provenance=before.provenance();var fixed=w.getBlockEntity(visual).createNbtWithId();
        CatalogueMigration.loadedRoot(w,root);var after=(dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity)w.getBlockEntity(root);
        t.assertTrue(w.getBlockState(root).get(PrototypeLadderBlock.VARIANT)==0&&w.getBlockState(root).get(PrototypeLadderBlock.SOURCE_CLONE),"retired artistic variant becomes canonical without removing source-clone behavior");
        t.assertTrue(after.owner().equals(identity)&&after.provenance().equals(provenance)&&w.getBlockEntity(visual).createNbtWithId().equals(fixed)&&dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime.resolveRoot(w,visual).equals(root),"UUID, opaque Original and fixed backing role stay exact");p.discard();t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="catalogue_repair")
    public void thirdGlazingRestoresItsExactHistoricalBackFaceWithoutObliqueRotation(TestContext t) throws Exception {
        String prefix="/assets/bloodborne_dw/models/";
        com.google.gson.JsonObject original,current;
        try(var stream=getClass().getResourceAsStream(prefix+"base/source/minecraft/block/hold/window_03.json")){original=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
        try(var stream=getClass().getResourceAsStream(prefix+"base/v10_windows/window_03.json")){current=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
        var old=original.getAsJsonArray("elements").get(0).getAsJsonObject();var now=current.getAsJsonArray("elements").get(0).getAsJsonObject();
        t.assertTrue(old.getAsJsonObject("faces").equals(now.getAsJsonObject("faces")),"both original window03 UV faces are exact, no invented artwork");
        t.assertTrue(now.getAsJsonObject("rotation").get("angle").getAsInt()==0,"restoration does not reintroduce the prohibited intrinsic45-degree model");
        t.assertTrue(!now.getAsJsonObject("faces").get("north").equals(now.getAsJsonObject("faces").get("south")),"third glazing retains its source-specific two-sided visual");t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="catalogue_repair")
    public void allowedFenceAnglesNoBottomAndMutualPostIsolation(TestContext t) {
        var w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(3,3,3));
        var normal=PrototypeWallArchitecture.WALL;
        for(int yaw=0;yaw<8;yaw++) {
            var state=normal.rotate45(normal.getDefaultState().with(PrototypeWallBlock.ROTATION,yaw));
            t.assertTrue((state.get(PrototypeWallBlock.ROTATION)&1)==0&&state.canPlaceAt(w,root),"90002 cardinal and no bottom support");
            var post=PrototypeWallBlock.canonicalForm(PrototypeWallArchitecture.materialBlock(1).getDefaultState().with(PrototypeWallBlock.ROTATION,yaw).with(PrototypeWallBlock.NORTH,WallShape.TALL));
            t.assertTrue((post.get(PrototypeWallBlock.ROTATION)&1)==1&&PrototypeWallBlock.sideCode(post)==0&&post.get(PrototypeWallBlock.POST),"90012 odd yaw standalone form");
        }
        w.setBlockState(root,PrototypeWallArchitecture.materialBlock(1).getDefaultState(),Block.NOTIFY_ALL);
        w.setBlockState(root.east(),Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState(),Block.NOTIFY_ALL);
        var oracle=Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState().getStateForNeighborUpdate(Direction.WEST,w.getBlockState(root),w,root.east(),root);
        t.assertTrue(oracle.get(WallBlock.WEST_SHAPE)==WallShape.NONE,"vanilla wall cannot connect back to90012");
        t.assertTrue(!((FenceBlock)Blocks.OAK_FENCE).canConnect(w.getBlockState(root),true,Direction.WEST),"fence cannot connect to90012 even full-face hint");
        t.assertTrue(!((PaneBlock)Blocks.GLASS_PANE).connectsTo(w.getBlockState(root),true),"pane cannot connect to90012");
        // Fence gates use the WALLS tag directly instead of the wall/fence/pane
        // connection helpers. A diagonal post must not lower their model either.
        t.assertTrue(!w.getBlockState(root).isIn(net.minecraft.registry.tag.BlockTags.WALLS),"diagonal post is not a gate wall neighbor");
        var gate=Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING,Direction.NORTH);
        var nextGate=gate.getStateForNeighborUpdate(Direction.WEST,w.getBlockState(root),w,root.east(),root);
        t.assertTrue(!nextGate.get(FenceGateBlock.IN_WALL),"fence gate keeps its ordinary height beside90012");
        w.setBlockState(root,normal.getDefaultState(),Block.NOTIFY_ALL);w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);w.removeBlock(root.down(),false);
        t.assertTrue(w.getBlockState(root).isOf(normal),"removing bottom does not destroy90002");t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="catalogue_repair")
    public void stackReadMigratesContainersDropsAndOldActionsWithoutLosingTypedNbt(TestContext t) {
        for(String path:List.of("builder_tool","composite_builder","wall_builder"))
            t.assertTrue(!Registries.ITEM.containsId(new Identifier("bloodborne_dw",path)),"removed tool has no registry item: "+path);
        for(String path:List.of("prototype_wall_skin_2","prototype_wall_skin_3","prototype_wall_skin_4","prototype_wall_skin_5","prototype_wall_skin_7","prototype_ladder_art_1","prototype_ladder_art_2")) {
            ItemStack old=new ItemStack(Registries.ITEM.get(new Identifier("bloodborne_dw",path)),1);
            old.getOrCreateNbt().putInt("BuilderAction",6);old.getOrCreateNbt().putLong("OpaqueLong",Long.MIN_VALUE);old.setCustomName(net.minecraft.text.Text.literal("retained"));
            old.getOrCreateSubNbt("BlockStateTag").putString("rotation","3");
            ItemStack next=ItemStack.fromNbt(old.writeNbt(new NbtCompound()));
            t.assertTrue(!CatalogueMigration.retired(Registries.ITEM.getId(next.getItem()).getPath()),"old item reads as current type: "+path);
            t.assertTrue(next.getNbt().getLong("OpaqueLong")==Long.MIN_VALUE&&next.getNbt().getInt("BuilderAction")==6&&next.hasCustomName(),"typed payload/name/action survive: "+path);
            t.assertTrue(ItemStack.areEqual(next,ItemStack.fromNbt(next.writeNbt(new NbtCompound()))),"repeat save/read is idempotent");
        }
        t.assertTrue(UnifiedCreativeCatalogue.canonicalItems().stream().noneMatch(s->CatalogueMigration.retired(Registries.ITEM.getId(s.getItem()).getPath())),"no removed variants offered in Creative");t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="catalogue_repair")
    public void loadedRootAliasKeepsUuidAndOpaquePayload(TestContext t) {
        var w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(3,3,3));
        var old=PrototypeWallArchitecture.materialBlock(3).getDefaultState().with(PrototypeWallBlock.ROTATION,3);
        w.setBlockState(root,old,Block.NOTIFY_ALL);var own=(CompositeBlockEntity)w.getBlockEntity(root);UUID uuid=own.resident().instanceId();NbtCompound data=own.payload();data.putString("Opaque","preserved");own.set(own.resident(),own.contributions(),data);
        CatalogueMigration.loadedRoot(w,root);own=(CompositeBlockEntity)w.getBlockEntity(root);
        t.assertTrue(PrototypeWallBlock.diagonalPost(w.getBlockState(root))&&own.resident().instanceId().equals(uuid)&&own.payload().getString("Opaque").equals("preserved"),"lazy old alias migration retains root/UUID/payload");
        NbtCompound saved=own.createNbt();CatalogueMigration.loadedRoot(w,root);t.assertTrue(saved.equals(((CompositeBlockEntity)w.getBlockEntity(root)).createNbt()),"second migration creates no duplicate");t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="catalogue_repair")
    public void diagonalLadderCollisionStaysEmptyWithOrWithoutActualMountFaces(TestContext t) {
        var w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(3,3,3));var p=t.createMockCreativeServerPlayerInWorld();p.setPosition(root.getX()+5,root.getY(),root.getZ()+5);
        var ladder=(PrototypeLadderBlock)PrototypeArchitecture.ladderItem(0).getBlock();
        var npc=net.minecraft.entity.EntityType.ZOMBIE.create(w);
        var contexts=List.of(ShapeContext.absent(),ShapeContext.of(p),ShapeContext.of(npc));
        for(int yaw:new int[]{1,3,5,7}) {
            var state=PrototypeLadderBlock.withYaw(ladder.getDefaultState(),yaw);w.setBlockState(root,state,Block.NOTIFY_ALL);
            for(var context:contexts)t.assertTrue(state.getCollisionShape(w,root,context).isEmpty(),"unsupported diagonal has no physical collider in any context "+yaw);
            for(Direction dir:PrototypeLadderBlock.backingDirections(state))w.setBlockState(root.offset(dir),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            for(var context:contexts)t.assertTrue(state.getCollisionShape(w,root,context).isEmpty(),"two actual side supports retain pass-through in every context "+yaw);
            w.removeBlock(root.offset(PrototypeLadderBlock.backingDirections(state).get(0)),false);
            for(var context:contexts)t.assertTrue(state.getCollisionShape(w,root,context).isEmpty()&&w.getBlockState(root).isOf(ladder),"removing one support cannot restore an angular collider "+yaw);
            t.assertTrue(!state.getOutlineShape(w,root,ShapeContext.of(p)).isEmpty(),"pass-through section keeps its aiming outline "+yaw);
            for(var context:contexts)t.assertTrue(ladder.getCollisionShape(state.with(PrototypeLadderBlock.SOURCE_CLONE,true),w,root,context).isEmpty(),"source-clone angular section itself is also pass-through "+yaw);
            for(Direction dir:PrototypeLadderBlock.backingDirections(state))w.removeBlock(root.offset(dir),false);
        }
        p.discard();npc.discard();t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="catalogue_repair")
    public void ordinaryLadderItemAndSavedAngularOwnersHaveNoPhysicalColliderButRetainOutline(TestContext t) {
        var w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(3,3,3));
        var p=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AngularLadderProbe"));
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND) {
            @Override public void send(net.minecraft.network.packet.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks) {}
        };
        p.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),connection,p);
        p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;
        var npc=net.minecraft.entity.EntityType.ZOMBIE.create(w);
        var contexts=List.of(ShapeContext.absent(),ShapeContext.of(p),ShapeContext.of(npc));
        var ladder=(PrototypeLadderBlock)PrototypeArchitecture.ladderItem(0).getBlock();
        try {
            w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            for(int yaw=0;yaw<8;yaw++) {
                p.refreshPositionAndAngles(root.getX()+.5,root.getY(),root.getZ()-5,yaw*45,0);
                var stack=PrototypeArchitecture.ladderItem(0).getDefaultStack();p.setStackInHand(Hand.MAIN_HAND,stack);
                var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(root.down()).add(0,.5,0),Direction.UP,root.down(),false);
                t.assertTrue(stack.getItem().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,hit)).isAccepted(),"ordinary registered90006 item places yaw "+yaw);
                var placed=w.getBlockState(root);
                t.assertTrue(placed.isOf(ladder)&&PrototypeLadderBlock.yaw(placed)==yaw&&placed.get(PrototypeLadderBlock.FREESTANDING)&&!placed.get(PrototypeLadderBlock.SOURCE_CLONE),"placement derives orientation and ordinary state from real item context");
                t.assertTrue(w.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()!=null,"ordinary item retains its native resident owner");
                for(var context:contexts) {
                    var actual=placed.getCollisionShape(w,root,context);
                    if((yaw&1)!=0)t.assertTrue(actual.isEmpty(),"angular ordinary item has no collider in absent/player/NPC context "+yaw);
                    else {
                        var authored=new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(0,0,13.19785/16,1,1,1).rotate(yaw/2);
                        var expected=net.minecraft.util.shape.VoxelShapes.cuboid(authored.minX(),authored.minY(),authored.minZ(),authored.maxX(),authored.maxY(),authored.maxZ());
                        t.assertTrue(!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(actual,expected,net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"cardinal authored collider remains exactly unchanged "+yaw);
                    }
                }
                var outline=placed.getOutlineShape(w,root,ShapeContext.of(p));
                t.assertTrue(!outline.isEmpty(),"ordinary pass-through item retains a visible aiming outline "+yaw);
                t.assertTrue(outline.getBoundingBoxes().size()==1,"one authored ladder section uses one cached aiming box, without a strip comb "+yaw);
                var center=outline.getBoundingBoxes().get(0).getCenter().add(Vec3d.of(root));
                t.assertTrue(outline.raycast(center.add(0,0,-2),center.add(0,0,2),root)!=null,"outline remains a real raycast target "+yaw);
                if(yaw<7){w.removeBlock(root,false);CompositeRuntime.drain(w);}
            }
            // Load an old fractional owner with deliberately nonempty cached
            // physical prisms, rather than regenerating it through new geometry.
            var state=w.getBlockState(root);var own=(CompositeBlockEntity)w.getBlockEntity(root);var owner=own.resident();
            var legacy=VerticalMount.nativePayload(w,root,state,new NbtCompound());legacy.putDouble(VerticalMount.KEY,.25);legacy.putString("Opaque","preserved");
            var coarse=new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(0,0,0,1,1,1);
            legacy.getCompound("NativeGeometry").put("collision",CompositeData.boxes(List.of(coarse)));
            legacy.getCompound("NativeGeometry").put("selection",CompositeData.boxes(List.of(coarse)));
            var rootData=CompositeData.data(state,legacy);
            var bottom=new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(0,.25,0,1,1,1);
            var top=new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(0,0,0,1,.25,1);
            var lower=CompositeData.contribution(CompositeData.contribution(new CompositeData.Contribution(owner,rootData,new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint(List.of(bottom),List.of(bottom)))));
            var upper=CompositeData.contribution(CompositeData.contribution(new CompositeData.Contribution(owner,rootData,new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint(List.of(top),List.of(top)))));
            var saved=own.createNbt();saved.put("payload",legacy);saved.put("owners",CompositeData.contributions(List.of(lower)));own.readNbt(saved);
            w.setBlockState(root.up(),CompositeArchitecture.CELL.getDefaultState(),Block.NOTIFY_ALL);
            ((CompositeBlockEntity)w.getBlockEntity(root.up())).set(null,List.of(upper),new NbtCompound());
            CompositeLedger.get(w).put(CompositeData.cell(root),List.of(lower));CompositeLedger.get(w).put(CompositeData.cell(root.up()),List.of(upper));
            t.assertTrue(VerticalMount.shifted(w,root)&&!CompositeRuntime.contributions(w,root).get(0).shape().collision().isEmpty(),"loaded owner contains actual old coarse collision and fractional offset");
            for(var cell:List.of(root,root.up()))for(var context:contexts) {
                t.assertTrue(CompositeRuntime.cellShape(w,cell,true,w.getBlockState(cell),context).isEmpty(),"loaded root/helper collision is empty in every context");
                t.assertTrue(!CompositeRuntime.cellShape(w,cell,false,w.getBlockState(cell),context).isEmpty(),"loaded root/helper retains selection in every context");
                t.assertTrue(w.getBlockState(cell).getCollisionShape(w,cell,context).isEmpty(),"native/helper collision hook cannot restore the old collider");
            }
            Box inside=new Box(.1,.3,.1,.9,1.2,.9).offset(root);
            t.assertTrue(w.isSpaceEmpty(p,inside)&&w.isSpaceEmpty(npc,inside),"old coarse owner does not obstruct player or living NPC bodies");
            var object=CompositeRuntime.instance(w,root,state,owner.instanceId(),legacy);
            t.assertTrue(PlacementPhysics.physicalBoxes(object).isEmpty(),"old NativeGeometry cannot regenerate physical placement prisms");
            t.assertTrue(CompositeRuntime.topHeight(w,root,null)==null&&CompositeRuntime.bottomHeight(w,root)==null,"old angular collider is not a physical support surface");
            t.assertTrue(PlacementPhysics.conflict(w,List.of(inside),null,null)==null,"ordinary placement conflict also ignores old cached angular collision");
            t.assertTrue(own.resident().equals(owner)&&own.payload().equals(legacy)&&!own.contributions().get(0).shape().collision().isEmpty(),"read-time filtering preserves UUID, typed payload and old saved contribution");
            var source=state.with(PrototypeLadderBlock.SOURCE_CLONE,true);var sourcePayload=legacy.copy();sourcePayload.putInt("NativeBackingX",1);sourcePayload.putInt("NativeBackingY",0);sourcePayload.putInt("NativeBackingZ",0);
            var sourceEntry=new CompositeData.Contribution(owner,CompositeData.data(source,sourcePayload),lower.shape());
            t.assertTrue(CompositeRuntime.physicalParts(sourceEntry,root).isEmpty()&&!CompositeRuntime.physicalParts(sourceEntry,root.east()).isEmpty(),"source angular rail is empty while its separately owned original backing cube remains physical");
            var sourceFootprint=VerticalMount.nativeFootprint(w,root,source,sourcePayload);
            t.assertTrue(sourceFootprint.get(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN).collision().isEmpty()&&sourceFootprint.entrySet().stream().anyMatch(e->e.getKey().x()==1&&!e.getValue().collision().isEmpty()),"legacy native footprint also preserves only the source backing cube");
            t.complete();
        } finally {
            if(w.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()!=null)CompositeRuntime.remove(w,own.resident(),null,false);
            CompositeLedger.get(w).put(CompositeData.cell(root),List.of());CompositeLedger.get(w).put(CompositeData.cell(root.up()),List.of());
            p.discard();npc.discard();w.removeBlock(root.up(),false);w.removeBlock(root.down(),false);
        }
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="catalogue_repair")
    public void animationBusyIsPerInstanceAndDoesNotChangeStateOnRejectedRepeat(TestContext t) {
        var w=t.getWorld();var a=ObjectRegistry.TYPES.get("door_1").create(w);var b=ObjectRegistry.TYPES.get("door_1").create(w);
        t.assertTrue(a.setOpen(true)&&a.animationBusy(),"first actual door clip starts");
        t.assertTrue(!a.setOpen(false)&&a.isOpen(),"repeat cannot interrupt opening");
        t.assertTrue(b.setOpen(true),"other same-type instance remains available");
        for(int tick=0;tick<120&&a.animationBusy();tick++)a.tick();
        t.assertTrue(!a.animationBusy()&&a.setOpen(false),"new action accepted after actual clip ends");
        var restored=ObjectRegistry.TYPES.get("door_1").create(w);restored.readNbt(a.writeNbt(new NbtCompound()));t.assertTrue(!restored.animationBusy(),"reload does not leave permanent transition lock");t.complete();
    }
}
