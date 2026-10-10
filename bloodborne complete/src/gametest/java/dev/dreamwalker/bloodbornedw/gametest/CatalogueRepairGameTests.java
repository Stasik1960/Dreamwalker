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
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="catalogue_repair")
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
        w.removeBlock(floor,false);p.discard();t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="catalogue_repair")
    public void realServerPacketRejectsWrongAimAndAcceptsPhysicalDoorPart(TestContext t) {
        var w=t.getWorld();var door=ObjectRegistry.TYPES.get("door_1").create(w);
        BlockPos root=t.getAbsolutePos(new BlockPos(3,4,3));door.setPosition(Vec3d.ofCenter(root));w.spawnEntity(door);
        // The GameTest mock overrides rotation/interaction; use the genuine packet handler.
        var p=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RpPacketProbe"));
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND) {
            @Override public void send(net.minecraft.network.packet.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks) {}
        };
        p.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),connection,p);
        p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;w.spawnEntity(p);
        var box=door.activePhysicalBoxes().stream().min(java.util.Comparator.comparingDouble(b->b.minZ)).orElseThrow();var center=box.getCenter();
        p.setPosition(center.x,center.y-p.getStandingEyeHeight(),box.minZ-2);p.setPitch(0);p.setYaw(180);
        var wrong=RpObjectSelection.playerTarget(p,6);
        t.assertTrue(wrong==null||wrong.getEntity()!=door,"wrong-aim fixture cannot hit tested door: eye="+p.getEyePos()+" box="+box+" yaw="+p.getYaw());
        var packet=net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.interact(door,false,Hand.MAIN_HAND);
        p.networkHandler.onPlayerInteractEntity(packet);
        t.assertTrue(!door.isOpen(),"forged UUID packet aimed away cannot use visual AABB as permission");
        p.setYaw(0);p.networkHandler.onPlayerInteractEntity(packet);
        t.assertTrue(door.isOpen(),"real server packet on physical part within Creative reach is accepted");
        door.discard();p.discard();t.complete();
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
    public void diagonalLadderMaskUsesBothActualMountFacesAndUpdatesImmediately(TestContext t) {
        var w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(3,3,3));var p=t.createMockCreativeServerPlayerInWorld();p.setPosition(root.getX()+5,root.getY(),root.getZ()+5);
        var ladder=(PrototypeLadderBlock)PrototypeArchitecture.ladderItem(0).getBlock();
        for(int yaw:new int[]{1,3,5,7}) {
            var state=PrototypeLadderBlock.withYaw(ladder.getDefaultState(),yaw);w.setBlockState(root,state,Block.NOTIFY_ALL);
            t.assertTrue(!state.getCollisionShape(w,root,ShapeContext.of(p)).isEmpty(),"unsupported diagonal has player collider "+yaw);
            for(Direction dir:PrototypeLadderBlock.backingDirections(state))w.setBlockState(root.offset(dir),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            t.assertTrue(state.getCollisionShape(w,root,ShapeContext.of(p)).isEmpty(),"two actual side supports retain pass-through "+yaw);
            w.removeBlock(root.offset(PrototypeLadderBlock.backingDirections(state).get(0)),false);
            t.assertTrue(!state.getCollisionShape(w,root,ShapeContext.of(p)).isEmpty()&&w.getBlockState(root).isOf(ladder),"removing one support restores collider without re-placement "+yaw);
            for(Direction dir:PrototypeLadderBlock.backingDirections(state))w.removeBlock(root.offset(dir),false);
        }
        p.discard();t.complete();
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
