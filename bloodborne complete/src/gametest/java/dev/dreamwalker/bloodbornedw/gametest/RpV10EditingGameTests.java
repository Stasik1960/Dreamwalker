package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornerp.content.SourceLegacyPayload;
import dev.dreamwalker.bloodbornerp.object.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;

/** Real item/entity/player APIs; packet/crosshair GUI acceptance remains a separate actual-client proof. */
public final class RpV10EditingGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_animated_placement")
    public void ordinaryVisibleDogCagePlacementRetainsFiniteSourcePartsAndLegacyMovement(TestContext c){
        Vec3d at=base(c);BlockPos root=BlockPos.ofFloored(at),support=root.down();
        c.getWorld().setBlockState(support,Blocks.STONE.getDefaultState());var beforeSupport=c.getWorld().getBlockState(support);
        ServerPlayerEntity player=realModePlayer(c);player.setPosition(at.x,at.y,at.z-4);player.setYaw(0);
        ItemStack stack=Registries.ITEM.get(new Identifier("bloodborne_rp:cage_obj_1_placer")).getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,stack);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
        Box query=new Box(root).expand(32);Set<UUID> before=new HashSet<>();for(var rp:c.getWorld().getEntitiesByClass(RpObjectEntity.class,query,e->!e.isRemoved()))before.add(rp.getUuid());
        ActionResult used=stack.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(at.x,root.getY(),at.z),Direction.UP,support,false)));
        c.assertTrue(used.isAccepted(),"ordinary visible-dog cage item places inside real world bounds with only the local5x5 chunks loaded");
        var added=c.getWorld().getEntitiesByClass(RpObjectEntity.class,query,e->!e.isRemoved()&&!before.contains(e.getUuid())&&e.originalRegistryAssetId().equals("cage_obj_1"));
        c.assertTrue(added.size()==1,"one ordinary construction use creates one actual registered cage UUID");RpObjectEntity cage=added.get(0);c.assertTrue(cage.dogsVisible(),"default visible dog animation is actually active");
        Box bounds=cage.queryBounds();c.assertTrue(bounds.minY>=c.getWorld().getBottomY()&&bounds.maxY<=c.getWorld().getTopY()&&bounds.getXLength()<32&&bounds.getYLength()<32&&bounds.getZLength()<32,"nested authored dog bones stay bounded without repeated sphere-AABB corner inflation");
        var physical=cage.activePhysicalBoxes();c.assertTrue(physical.size()==1&&Math.abs(physical.get(0).getXLength()-2.8)<1e-6&&Math.abs(physical.get(0).getYLength()-6.5)<1e-6,"selection correction retains registered legacy cage movement dimensions");
        UUID uuid=cage.getUuid();cage.setDogsVisible(false);c.assertTrue(cage.activePhysicalBoxes().equals(physical),"hiding per-instance dog art cannot alter movement collider");cage.setDogsVisible(true);
        c.assertTrue(cage.getUuid().equals(uuid)&&c.getWorld().getBlockState(support).equals(beforeSupport)&&c.getWorld().getBlockState(root).isAir(),"source identity and exact native support/AIR survive ordinary RP placement");
        near(player,cage);aim(player,cage.selectionBoxes().get(0).getCenter());var selected=RpObjectSelection.playerTarget(player,6);c.assertTrue(selected!=null&&selected.getEntity()==cage,"actual source-part selection survives finite animation envelope");player.attack(cage);c.assertTrue(cage.isRemoved()&&c.getWorld().getBlockState(support).equals(beforeSupport),"intentional ordinary part attack removes only cage, retaining native support");
        c.getWorld().setBlockState(support,Blocks.AIR.getDefaultState());player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_item_passthrough")
    public void registeredConstructionItemsPassThroughOpenableRpWithoutChangingItsTypedIdentity(TestContext c){
        ServerPlayerEntity player=realModePlayer(c);RpObjectEntity door=create(c,"door_1");Vec3d at=base(c);door.refreshPositionAndAngles(at.x,at.y,at.z,90,0);door.setCustomName(Text.literal("unchanged openable source door"));
        NbtCompound source=door.writeNbt(new NbtCompound());source.putString("OpaquePassThroughV10","retained");source.putDouble("UnknownTypedFiniteValue",.125);door.readNbt(source);c.getWorld().spawnEntity(door);near(player,door);
        NbtCompound before=door.writeNbt(new NbtCompound());UUID uuid=door.getUuid();int canonical=0,aliases=0;
        for(String id:ObjectRegistry.TYPES.keySet()){
            ItemStack placer=Registries.ITEM.get(new Identifier("bloodborne_rp",id+"_placer")).getDefaultStack();c.assertTrue(!placer.isEmpty()&&ObjectRegistry.isPlacementItem(placer),"exact registered RP construction item recognized including legacy aliases: "+id);
            player.setStackInHand(Hand.MAIN_HAND,placer);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);ItemStack itemBefore=placer.copy();
            c.assertTrue(door.interact(player,Hand.MAIN_HAND)==ActionResult.PASS,"openable RP passes construction item through instead of toggling: "+id);
            c.assertTrue(!door.isOpen()&&door.getUuid().equals(uuid)&&door.writeNbt(new NbtCompound()).equals(before)&&ItemStack.areEqual(placer,itemBefore),"construction interaction retains exact typed data/UUID/name/source payload and held item: "+id);
            if(ObjectRegistry.isCanonicalPlacementItem(id))canonical++;else aliases++;
        }
        c.assertTrue(canonical==69&&aliases==7,"all current canonical construction items plus compatible old aliases covered");
        for(Hand hand:Hand.values()){
            player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);player.setStackInHand(hand,Items.STONE.getDefaultStack());
            c.assertTrue(!ObjectRegistry.isPlacementItem(player.getStackInHand(hand))&&door.interact(player,Hand.MAIN_HAND)==ActionResult.PASS&&door.writeNbt(new NbtCompound()).equals(before),"ordinary native BlockItem keeps existing pass-through and never toggles RP: "+hand);
            player.setStackInHand(hand,dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture.BUILDER.getDefaultStack());
            c.assertTrue(!ObjectRegistry.isPlacementItem(player.getStackInHand(hand))&&door.interact(player,Hand.MAIN_HAND)==ActionResult.PASS&&door.writeNbt(new NbtCompound()).equals(before),"any-hand tool guard retained and tool is not classified as a placer: "+hand);
        }
        player.setStackInHand(Hand.MAIN_HAND,Items.STICK.getDefaultStack());player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
        c.assertTrue(!ObjectRegistry.isPlacementItem(player.getMainHandStack())&&!ObjectRegistry.isPlacementItem(ItemStack.EMPTY)&&!ObjectRegistry.isPlacementItem(null),"unrelated ordinary items/empty/null are not redirected as registered RP placers");
        c.assertTrue(door.interact(player,Hand.MAIN_HAND)==ActionResult.CONSUME&&door.isOpen(),"unrelated ordinary item retains real openable RP interaction instead of construction priority");
        door.removeByBuilder();player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="rp_v10_catalogue")
    public void everyCanonicalRpTypeCanBeAttackedAtItsLoadedSourcePart(TestContext c){
        ServerPlayerEntity player=c.createMockCreativeServerPlayerInWorld();Vec3d origin=base(c).add(0,90,0);
        int checked=0;
        for(String id:ObjectRegistry.TYPES.keySet()){
            if(!ObjectRegistry.isCanonicalPlacementItem(id))continue;
            RpObjectEntity object=create(c,id);object.refreshPositionAndAngles(origin.x,origin.y,origin.z,90,0);c.getWorld().spawnEntity(object);
            Box part=object.selectionBoxes().get(0);Vec3d target=new Vec3d((part.minX+part.maxX)/2,(part.minY+part.maxY)/2,(part.minZ+part.maxZ)/2);
            player.setPosition(target.x,target.y-player.getStandingEyeHeight(),part.minZ-2);aim(player,target);
            var hit=RpObjectSelection.playerTarget(player,6);
            c.assertTrue(hit!=null&&hit.getEntity()==object,"actual source-part ray selects canonical RP "+id);
            player.attack(object);c.assertTrue(object.isRemoved(),"real PlayerEntity.attack reaches selected source part: "+id);checked++;
        }
        c.assertTrue(checked>60,"test covers every registered canonical construction type, not a handpicked subset: "+checked);player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_delete")
    public void creativePartAttackRejectsToolsEitherHandSurvivalAndEnvironmentalDamage(TestContext c){
        ServerPlayerEntity player=realModePlayer(c);RpObjectEntity ladder=create(c,"ladder");Vec3d at=base(c);ladder.refreshPositionAndAngles(at.x,at.y,at.z,90,0);c.getWorld().spawnEntity(ladder);
        Box bottom=ladder.selectionBoxes().get(1);Vec3d target=new Vec3d((bottom.minX+bottom.maxX)/2,bottom.minY+1,(bottom.minZ+bottom.maxZ)/2);
        player.setPosition(target.x,target.y-player.getStandingEyeHeight(),bottom.minZ-2);aim(player,target);
        var selected=RpObjectSelection.playerTarget(player,6);c.assertTrue(selected!=null&&selected.getEntity()==ladder,"actual lower authored part is selected before intentional attack guards");
        c.assertTrue(player.squaredDistanceTo(ladder)>64,"real lower part is beyond source origin reach");
        var tool=dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture.BUILDER.getDefaultStack();
        for(Hand hand:Hand.values()){player.setStackInHand(hand,tool.copy());player.attack(ladder);c.assertTrue(!ladder.isRemoved(),"any-hand building tool prevents ordinary attack: "+hand);player.setStackInHand(hand,ItemStack.EMPTY);}
        player.setStackInHand(Hand.OFF_HAND,tool.copy());ItemStack placer=Registries.ITEM.get(new Identifier("bloodborne_rp:chair_placer")).getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,placer);Set<UUID> before=ids(c);
        c.assertTrue(!placer.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(target,Direction.UP,BlockPos.ofFloored(target),false))).isAccepted()&&before.equals(ids(c)),"offhand tool also prevents ordinary RP item placement");player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);c.assertTrue(!player.isCreative()&&!player.getAbilities().creativeMode&&player.interactionManager.getGameMode()==net.minecraft.world.GameMode.SURVIVAL,"actual ordinary server player is Survival, not TestContext's hardcoded Creative subclass");player.attack(ladder);c.assertTrue(!ladder.isRemoved(),"Survival ordinary attack does not delete RP");player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);c.assertTrue(player.isCreative()&&player.getAbilities().creativeMode,"actual server Creative restored");
        c.assertTrue(!ladder.damage(player.getDamageSources().generic(),100)&&!ladder.isRemoved(),"environmental/non-player damage never removes RP");
        player.attack(ladder);c.assertTrue(ladder.isRemoved(),"intentional ordinary Creative player attack removes selected real lower part");player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_offset")
    public void offsetMovesEveryWorkingShapeExactlyAndPersistsIdentityWithoutDoubleApplication(TestContext c){
        ServerPlayerEntity player=c.createMockCreativeServerPlayerInWorld();RpObjectEntity ladder=create(c,"ladder");Vec3d at=base(c);ladder.refreshPositionAndAngles(at.x,at.y,at.z,90,0);ladder.setCustomName(Text.literal("same source ladder"));
        NbtCompound source=ladder.writeNbt(new NbtCompound());source.putInt("AnimationId",0);source.putString("OpaqueV10","retained");NbtList links=new NbtList();links.add(NbtHelper.fromUuid(UUID.randomUUID()));source.put("Links",links);ladder.readNbt(source);c.getWorld().spawnEntity(ladder);
        UUID uuid=ladder.getUuid();Box visual=ladder.visualBounds();var physical=ladder.activePhysicalBoxes();var selection=ladder.selectionBoxes();var climbing=ladder.climbingBoxes();
        near(player,ladder);c.assertTrue(ladder.setVerticalOffset(.125,player),"exact1/8 shift accepted");
        c.assertTrue(ladder.verticalOffset()==.125&&ladder.getPos().equals(at.add(0,.125,0)),"metadata and actual server pose shift once");
        c.assertTrue(ladder.visualBounds().equals(visual.offset(0,.125,0))&&shifted(physical,.125).equals(ladder.activePhysicalBoxes())&&shifted(selection,.125).equals(ladder.selectionBoxes())&&shifted(climbing,.125).equals(ladder.climbingBoxes()),"visual/physics/selection/climbing have identical exact displacement");
        NbtCompound saved=ladder.writeNbt(new NbtCompound());RpObjectEntity loaded=create(c,"ladder");loaded.readNbt(saved);
        c.assertTrue(saved.contains("VerticalOffset",NbtElement.DOUBLE_TYPE)&&loaded.verticalOffset()==.125&&loaded.getPos().equals(ladder.getPos()),"reload retains exact DOUBLE and already-shifted Pos without second offset");
        c.assertTrue(loaded.getUuid().equals(uuid)&&loaded.getCustomName().equals(ladder.getCustomName())&&saved.get("Links").equals(links)&&saved.getCompound(SourceLegacyPayload.KEY).getCompound("Original").equals(source),"UUID/CustomName/typed source provenance/links retained");
        c.assertTrue(ladder.setVerticalOffset(32.125,player),"offset has no arbitrary8-block or32-block edit cap");near(player,ladder);c.assertTrue(ladder.setVerticalOffset(0,player)&&ladder.getPos().equals(at),"reset restores exact authored origin");
        near(player,ladder);var before=ladder.writeNbt(new NbtCompound());c.assertTrue(!ladder.setVerticalOffset(Double.NaN,player)&&!ladder.setVerticalOffset(10000,player)&&before.equals(ladder.writeNbt(new NbtCompound())),"nonfinite/out-of-world request leaves all typed state unchanged");
        ladder.removeByBuilder();player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_overlap")
    public void ordinaryRpPlacementAndEditingOverlapNativeChestAndRpWithoutAnyNativeMutation(TestContext c){
        ServerPlayerEntity player=c.createMockCreativeServerPlayerInWorld();Vec3d at=base(c);BlockPos pos=BlockPos.ofFloored(at);c.getWorld().setBlockState(pos,Blocks.CHEST.getDefaultState());ChestBlockEntity chest=(ChestBlockEntity)c.getWorld().getBlockEntity(pos);chest.setStack(0,new ItemStack(Items.DIAMOND,7));NbtCompound original=chest.createNbtWithId();var nativeState=c.getWorld().getBlockState(pos);
        player.setPosition(at.x,at.y,at.z-3);ItemStack stack=Registries.ITEM.get(new Identifier("bloodborne_rp:npc_window_placer")).getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,stack);
        Set<UUID> before=ids(c);var context=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(at,Direction.UP,pos,false));
        c.assertTrue(stack.getItem().useOnBlock(context).isAccepted()&&stack.getItem().useOnBlock(context).isAccepted(),"two real construction uses overlap native chest and each other");
        List<RpObjectEntity> created=new ArrayList<>();for(Entity value:c.getWorld().iterateEntities())if(value instanceof RpObjectEntity rp&&!before.contains(rp.getUuid()))created.add(rp);c.assertTrue(created.size()==2,"one canonical object per ordinary use");
        RpObjectEntity object=created.get(0);near(player,object);c.assertTrue(object.rotateByBuilder(player,object.getYaw()+90)&&object.setVerticalOffset(.125,player),"rotation/offset through native/RP volumes are allowed");
        c.assertTrue(c.getWorld().getBlockState(pos).equals(nativeState)&&c.getWorld().getBlockEntity(pos)==chest&&chest.createNbtWithId().equals(original),"no foreign state/BE identity/typed NBT is replaced or reconstructed");
        for(var rp:created)rp.removeByBuilder();c.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState());player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_placement")
    public void nativePlacementIgnoresRpWhileMovementAndLivingObstructionRemainReal(TestContext c){
        Vec3d at=base(c);RpObjectEntity window=create(c,"npc_window");window.refreshPositionAndAngles(at.x,at.y,at.z,90,0);c.getWorld().spawnEntity(window);window.intersectionChecked=true;
        Box plane=window.activePhysicalBoxes().get(0);var shape=VoxelShapes.cuboid(plane);c.assertTrue(c.getWorld().doesNotIntersectEntities(null,shape),"native placement collision gate ignores RP even when another producer marks intersectionChecked");
        ServerPlayerEntity player=c.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=false;player.intersectionChecked=true;player.setPosition(at.x,plane.minY,plane.minZ);
        c.assertTrue(c.getWorld().getOtherEntities(null,player.getBoundingBox()).contains(player),"living obstruction fixture is actually loaded in the native entity query");
        c.assertTrue(!c.getWorld().doesNotIntersectEntities(null,VoxelShapes.cuboid(player.getBoundingBox())),"native living obstruction remains authoritative");
        player.setPosition(at.x,at.y,plane.minZ-3);double start=player.getZ();player.move(MovementType.SELF,new Vec3d(0,0,6));c.assertTrue(player.getZ()-start<4,"movement still collides with the RP working plane");
        window.removeByBuilder();player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_platform")
    public void ladderHasFullAuthoredTopPlatformWithNativeSupportAcrossItsWorkingArea(TestContext c){
        RpObjectEntity ladder=create(c,"ladder");Vec3d at=base(c);ladder.refreshPositionAndAngles(at.x,at.y,at.z,90,0);c.getWorld().spawnEntity(ladder);
        Box local=RpObjectGeometry.ladderPlatform(),platform=ladder.activePhysicalBoxes().get(2);double scale=ladder.asset().scale();
        c.assertTrue(local.getXLength()==1.375&&local.getZLength()==3&&local.maxY==.75&&local.minY==.6875,"source plane full width/length and exact one-pixel support thickness");
        PlayerEntity player=c.createMockSurvivalPlayer();
        for(double x:List.of(platform.minX+.05,platform.maxX-.05))for(double z:List.of(platform.minZ+.4,platform.maxZ-.4)){
            player.setPosition(x,platform.maxY+.125,z);player.move(MovementType.SELF,new Vec3d(0,-1,0));c.assertTrue(Math.abs(player.getY()-platform.maxY)<1e-6,"actual native gravity support reaches every tested platform corner");
        }
        player.setPosition((platform.minX+platform.maxX)/2,platform.maxY,platform.minZ+.4);double start=player.getZ();player.move(MovementType.SELF,new Vec3d(0,-.1,platform.getZLength()-.8));c.assertTrue(player.getZ()-start>platform.getZLength()-1,"actual player crosses the platform without falling through its central region");
        ladder.setOpen(false);c.assertTrue(ladder.activePhysicalBoxes().get(2).equals(platform),"bottom animation does not move authored static root platform");ladder.removeByBuilder();player.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v10_animated_selection")
    public void openedAnimatedDoorHasSelectableMovingEnvelopeWithoutExpandingMovementPhysics(TestContext c){
        RpObjectEntity door=create(c,"door_1");Vec3d at=base(c);door.refreshPositionAndAngles(at.x,at.y,at.z,90,0);c.getWorld().spawnEntity(door);Box closed=door.visualBounds();var physical=door.activePhysicalBoxes();
        c.assertTrue(physical.size()==1&&physical.get(0).getXLength()==door.getWidth(),"legacy movement remains exact registered dimensions");
        c.assertTrue(door.setOpen(true)&&door.activePhysicalBoxes().isEmpty(),"opened passage preserves accepted passability");
        c.assertTrue(door.visualBounds().getZLength()>closed.getZLength()+.1,"source-authored leaf rotation has a bounded selection/culling envelope beyond closed pose");
        ServerPlayerEntity player=c.createMockCreativeServerPlayerInWorld();Box moving=door.selectionBoxes().get(door.selectionBoxes().size()-1);Vec3d target=new Vec3d((moving.minX+moving.maxX)/2,(moving.minY+moving.maxY)/2,moving.minZ+.1);player.setPosition(target.x,target.y-player.getStandingEyeHeight(),moving.minZ-2);aim(player,target);
        c.assertTrue(RpObjectSelection.playerTarget(player,6)!=null,"real player ray can select the moving envelope rather than the source origin");player.attack(door);c.assertTrue(door.isRemoved(),"ordinary player attack removes selected open RP");player.discard();c.complete();
    }
    private static List<Box> shifted(List<Box> boxes,double y){return boxes.stream().map(b->b.offset(0,y,0)).toList();}
    private static ServerPlayerEntity realModePlayer(TestContext c){
        var player=new ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),new GameProfile(UUID.randomUUID(),"RPV10ActualMode"));
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND){@Override public void send(net.minecraft.network.packet.Packet<?> packet){}@Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks){}};
        player.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(c.getWorld().getServer(),connection,player);player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);player.getAbilities().allowModifyWorld=true;c.getWorld().spawnEntity(player);return player;
    }
    private static Set<UUID> ids(TestContext c){Set<UUID> ids=new HashSet<>();for(Entity e:c.getWorld().iterateEntities())ids.add(e.getUuid());return ids;}
    private static RpObjectEntity create(TestContext c,String id){Entity entity=Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp",id)).create(c.getWorld());if(!(entity instanceof RpObjectEntity rp))throw new AssertionError("Missing RP factory:"+id);return rp;}
    private static Vec3d base(TestContext c){BlockPos pos=c.getAbsolutePos(new BlockPos(3,3,3));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.getWorld().getChunk(pos.add(x*16,0,z*16));return new Vec3d(pos.getX()+.5,150,pos.getZ()+.5);}
    private static void near(ServerPlayerEntity player,RpObjectEntity object){Box b=object.selectionBoxes().get(0);player.setPosition((b.minX+b.maxX)/2,(b.minY+b.maxY)/2-player.getStandingEyeHeight(),b.minZ-2);}
    private static void aim(PlayerEntity player,Vec3d target){Vec3d d=target.subtract(player.getEyePos());player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
}
