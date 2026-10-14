package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

/** Native server item/use/movement checks. Client baked geometry is verified
 * separately against source quads and actual client captures. */
public final class FunctionalDoorsV126GameTests implements FabricGameTest {
    private record Door(String number,String registry,int pose,int leaves,JsonObject reference) {}
    private static List<Door> doors() {
        var input=FunctionalDoorsV126GameTests.class.getResourceAsStream("/bloodborne_dw/v126-door-expectations.json");
        if(input==null)throw new IllegalStateException("Missing independently staged door QA references");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            List<Door> result=new ArrayList<>();
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("doors")) {
                var row=value.getAsJsonObject();
                result.add(new Door(row.get("mainId").getAsString(),row.get("registryId").getAsString(),row.get("variant").getAsInt(),row.get("leafCount").getAsInt(),row));
            }
            return List.copyOf(result);
        }catch(Exception failure){throw new IllegalStateException("Invalid V12.6 door QA references",failure);}
    }
    private static CompositeRootBlock block(Door door) {
        var block=Registries.BLOCK.get(new Identifier(door.registry));
        if(!(block instanceof CompositeRootBlock composite))throw new IllegalStateException("Door is not a native composite: "+door.number);
        return composite;
    }
    private static NbtCompound payload(){var value=new NbtCompound();value.putInt(ImportedTypeAliases.VERSION_KEY,1);return value;}
    private static BlockState state(Door door,int yaw,boolean open) {
        var value=block(door).getDefaultState().with(CompositeRootBlock.VARIANT,door.pose%16).with(CompositeRootBlock.ROTATION,yaw).with(CompositeRootBlock.OPEN,open);
        if(value.getBlock() instanceof ImportedArtworkBlock&&ImportedArtworkBlock.bankProperty(value)!=null)value=value.with(ImportedArtworkBlock.bankProperty(value),door.pose/16);
        return value;
    }
    private static ServerPlayerEntity player(ServerWorld world) {
        var value=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"Door126Probe"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){}};
        value.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,value);
        value.changeGameMode(net.minecraft.world.GameMode.CREATIVE);value.getAbilities().allowModifyWorld=true;value.noClip=false;value.getAbilities().flying=false;
        return value;
    }
    private static void load(ServerWorld world,BlockPos root,Door door,int yaw) {
        Set<ChunkPos> chunks=new HashSet<>();
        for(boolean open:List.of(false,true))for(var offset:block(door).spec.footprint(state(door,yaw,open),0).keySet()) {
            var pos=root.add(offset.x(),offset.y(),offset.z());
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)chunks.add(new ChunkPos((pos.getX()>>4)+dx,(pos.getZ()>>4)+dz));
        }
        for(var chunk:chunks){world.setChunkForced(chunk.x,chunk.z,true);world.getChunk(new BlockPos(chunk.x<<4,0,chunk.z<<4));}
    }
    private static ObjectInstance instance(ServerWorld world,BlockPos root) {
        var entity=(CompositeBlockEntity)world.getBlockEntity(root);
        return CompositeRuntime.instance(world,root,world.getBlockState(root),entity.resident().instanceId(),entity.payload());
    }
    private static ObjectInstance place(TestContext t,ServerPlayerEntity player,BlockPos root,Door door,int yaw,ItemStack supplied) {
        var world=t.getWorld();load(world,root,door,yaw);
        world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        player.refreshPositionAndAngles(root.getX()+.5,root.getY()+12,root.getZ()-12,yaw*45,0);
        var stack=supplied==null?block(door).art(state(door,yaw,false),payload()):supplied;
        player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(root.down()).add(0,.5,0),Direction.UP,root.down(),false);
        t.assertTrue(stack.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"ordinary door installation accepts "+door.number+" yaw="+yaw+": "+CompositeRuntime.lastResult());
        t.assertTrue(world.getBlockState(root).isOf(block(door))&&CompositeSpec.poseVariant(world.getBlockState(root))==door.pose&&world.getBlockState(root).get(CompositeRootBlock.ROTATION)==yaw,"installation preserves completed door type/pose/yaw "+door.number);
        return instance(world,root);
    }
    private static void remove(TestContext t,ObjectInstance value) {
        var world=t.getWorld();
        t.assertTrue(CompositeRuntime.remove(world,value.owner(),null,false).outcome()==TransactionCore.Outcome.COMMITTED,"whole door removal commits");CompositeRuntime.drain(world);
        for(var offset:value.cells().keySet()) {
            var pos=CompositeData.pos(value.owner().root().add(offset));
            t.assertTrue(CompositeRuntime.contributions(world,pos).stream().noneMatch(row->row.owner().equals(value.owner())),"door removal clears every owned helper");
        }
    }
    private static void use(TestContext t,ServerPlayerEntity player,ObjectInstance value) {
        var world=t.getWorld();
        var cells=value.cells().keySet().stream().map(offset->CompositeData.pos(value.owner().root().add(offset))).toList();
        var hit=CompositeAimV126.aim(t,player,value.owner(),cells);var current=world.getBlockState(hit.getBlockPos());
        t.assertTrue(current.getBlock().onUse(current,world,hit.getBlockPos(),player,Hand.MAIN_HAND,hit).isAccepted(),"ordinary selected root/helper RMB callback opens/closes "+value.owner().registryId());
    }
    private static void outline(TestContext t,ObjectInstance value) {
        var world=t.getWorld();int physical=0;
        for(var entry:value.cells().entrySet()) {
            var shape=entry.getValue();
            if(shape.collision().isEmpty())continue;physical+=shape.collision().size();
            t.assertTrue(shape.selection().equals(shape.collision()),"door outline and physical contribution coincide at "+entry.getKey());
            var pos=CompositeData.pos(value.owner().root().add(entry.getKey()));var current=world.getBlockState(pos);
            t.assertTrue(!VoxelShapes.matchesAnywhere(current.getCollisionShape(world,pos),current.getOutlineShape(world,pos),BooleanBiFunction.NOT_SAME),"native root/helper selection equals physical door shape");
        }
        t.assertTrue(physical>0,"a functional door retains real physical panels/frame");
    }
    private static Vec3d vec(JsonArray value){return new Vec3d(value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble());}
    private static Vec3d global(Vec3d point,int yaw,BlockPos root) {
        double radians=Math.toRadians(yaw*45),c=Math.cos(radians),s=Math.sin(radians),x=point.x-.5,z=point.z-.5;
        return new Vec3d(root.getX()+.5+c*x-s*z,root.getY()+point.y,root.getZ()+.5+s*x+c*z);
    }
    private static void movement(TestContext t,ServerPlayerEntity player,BlockPos root,Door door,int yaw,boolean open) {
        var passage=door.reference.getAsJsonObject("passage");
        var centre=vec(passage.getAsJsonArray("footCentreUnits")).multiply(1./16);
        double length=passage.get("crossingDistanceUnits").getAsDouble()/16;
        var normal=vec(passage.getAsJsonArray("normal"));
        var start=global(centre.subtract(normal.multiply(length/2)),yaw,root);
        var end=global(centre.add(normal.multiply(length/2)),yaw,root);
        var delta=end.subtract(start);
        player.refreshPositionAndAngles(start.x,start.y,start.z,0,0);player.setVelocity(Vec3d.ZERO);
        // Entity.move resolves X/Z separately. One four-block diagonal move
        // can jump around a short wall's end, unlike ordinary player walking.
        // Probe thirty-two actual0.125-block moves and stop CLOSED at first contact.
        var step=delta.multiply(1./32);boolean blocked=false;
        for(int i=0;i<32;i++) {
            var requested=player.getPos().add(step);player.move(MovementType.SELF,step);
            if(player.getPos().squaredDistanceTo(requested)>1e-8){blocked=true;if(!open)break;}
        }
        if(open)t.assertTrue(!blocked&&player.getPos().squaredDistanceTo(end)<1e-8,"real 0.6×1.8 player crosses the OPEN door in ordinary walking steps without any physical obstruction: "+door.number+" yaw="+yaw+" actual="+player.getPos()+" expected="+end);
        else t.assertTrue(blocked,"the CLOSED door physically blocks the real player's direct walking approach: "+door.number+" yaw="+yaw);
        player.refreshPositionAndAngles(root.getX()+20,root.getY()+20,root.getZ()+20,0,0);
    }
    private static void eachTick(TestContext t,ServerPlayerEntity player,List<Runnable> cases) {
        for(int i=0;i<cases.size();i++){var action=cases.get(i);t.runAtTick(i+1,()->{try{action.run();}catch(RuntimeException|Error failure){player.discard();throw failure;}});}
        t.runAtTick(cases.size()+2,()->{player.discard();t.complete();});
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="v126_functional_doors")
    public void sevenApprovedDoorsOpenAndCloseOnRmbAtEveryYawAndActuallyReleasePlayerPassage(TestContext t) {
        var references=doors();var expected=Set.of("90031","93160","90266","93152","90458","93153","90923");
        t.assertTrue(references.size()==8&&references.stream().map(Door::number).collect(java.util.stream.Collectors.toSet()).equals(expected),"exactly seven approved functional door types plus90923's alternate city mounting state are checked");
        t.assertTrue(references.stream().filter(row->row.number.equals("90923")).map(Door::pose).collect(java.util.stream.Collectors.toSet()).equals(Set.of(1,2)),"90923 keeps two mounting states within the same type/item");
        var world=t.getWorld();var player=player(world);var root=t.getAbsolutePos(new BlockPos(16384,96,64));List<Runnable> cases=new ArrayList<>();
        for(var door:references)for(int yaw=0;yaw<8;yaw++){final int selected=yaw;cases.add(()->{
            t.assertTrue(block(door).spec.openable,"approved door declares actual opening mechanism "+door.number);
            var closed=place(t,player,root,door,selected,null);outline(t,closed);movement(t,player,root,door,selected,false);
            use(t,player,closed);var opened=instance(world,root);
            t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&opened.owner().equals(closed.owner()),"RMB opens while retaining the exact instance UUID/type/root");
            outline(t,opened);movement(t,player,root,door,selected,true);
            use(t,player,opened);var again=instance(world,root);
            t.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN)&&again.owner().equals(closed.owner())&&again.cells().equals(closed.cells()),"second RMB closes and exactly restores physical occupancy");
            remove(t,again);
        });}
        eachTick(t,player,cases);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="final_door_default")
    public void ordinaryTallDoorItemUsesExactCityMountingAtEveryYawAndRetainsItThroughOpeningAndSaving(TestContext t) {
        var world=t.getWorld();var player=player(world);var root=t.getAbsolutePos(new BlockPos(16512,96,64));
        var door=doors().stream().filter(d->d.number.equals("90923")&&d.pose==2).findFirst().orElseThrow();
        List<Runnable> cases=new ArrayList<>();
        for(int yaw=0;yaw<8;yaw++){final int selected=yaw;cases.add(()->{
            var plain=new ItemStack(block(door).asItem());plain.getOrCreateNbt().putString("DoorCustomText","сохранено");
            var closed=place(t,player,root,door,selected,plain);
            t.assertTrue(player.getStackInHand(Hand.MAIN_HAND).getNbt().getString("DoorCustomText").equals("сохранено"),"ordinary canonicalization retains custom item data");
            use(t,player,closed);var opened=instance(world,root);
            t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&CompositeSpec.poseVariant(world.getBlockState(root))==2&&opened.owner().equals(closed.owner()),"opening retains the repaired mounting and instance UUID at yaw"+selected);
            var entity=(CompositeBlockEntity)world.getBlockEntity(root);var saved=entity.createNbt();
            var restored=new CompositeBlockEntity(root,world.getBlockState(root));restored.readNbt(saved);
            t.assertTrue(restored.resident().equals(entity.resident())&&restored.contributions().equals(entity.contributions())&&restored.payload().equals(entity.payload()),"open repaired door survives native NBT roundtrip without spatial or owner changes");
            use(t,player,opened);var again=instance(world,root);
            t.assertTrue(again.owner().equals(closed.owner())&&again.cells().equals(closed.cells()),"closing restores the exact city mounting occupancy");
            remove(t,again);
        });}
        eachTick(t,player,cases);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="v126_functional_doors")
    public void openDoorArtCopiesWithNewUuidAndNativeNbtRetainsOpenStateAndPrivateSettings(TestContext t) {
        var world=t.getWorld();var player=player(world);var root=t.getAbsolutePos(new BlockPos(16448,96,64));List<Runnable> cases=new ArrayList<>();
        for(var door:doors())cases.add(()->{
            var closed=place(t,player,root,door,2,null);use(t,player,closed);var opened=instance(world,root);
            var entity=(CompositeBlockEntity)world.getBlockEntity(root);var data=entity.payload();data.putLong("DoorOpaqueLong",Long.MIN_VALUE);data.putString("DoorOpaqueText","сохранено");data.putUuid("DoorExternalLink",UUID.randomUUID());entity.set(entity.resident(),entity.contributions(),data);
            var saved=entity.createNbt();var restored=new CompositeBlockEntity(root,world.getBlockState(root));restored.readNbt(saved);
            t.assertTrue(restored.resident().equals(opened.owner())&&restored.payload().equals(data)&&restored.contributions().equals(entity.contributions()),"native door NBT exactly retains UUID, links, private settings and open helper geometry "+door.number);
            t.assertTrue(CompositeData.state(opened.rootData()).get(CompositeRootBlock.OPEN),"serialized root data carries the OPEN state");
            var picked=CompositeRuntime.pick(world,opened.owner());
            t.assertTrue(picked.isOf(block(door).asItem())&&picked.getSubNbt("BlockStateTag").getString("open").equals("true"),"pick exposes one completed functional door item and the open art");
            t.assertTrue(picked.getSubNbt("CompositePayload")==null&&picked.getSubNbt("BlockEntityTag")==null,"art copy does not duplicate UUID, links or instance settings");
            remove(t,opened);var copy=place(t,player,root,door,6,picked);
            t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&!copy.owner().instanceId().equals(opened.owner().instanceId()),"copied open door installs with a genuinely new UUID");
            t.assertTrue(!((CompositeBlockEntity)world.getBlockEntity(root)).payload().contains("DoorExternalLink"),"copy has no private link from the previous instance");
            use(t,player,copy);t.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN),"ordinary RMB also closes the copied door");remove(t,instance(world,root));
        });
        eachTick(t,player,cases);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v126_door_anatomy")
    public void doorFramesStayFixedAndOnlyApprovedSingleOrPairedLeavesRotateExactlyNinetyDegrees(TestContext t) {
        for(var door:doors()) {
            var spec=block(door).spec;var selected=spec.variants.get(door.pose);var closedReference=door.reference.getAsJsonArray("sourceClosedParts");
            t.assertTrue(selected.closed().parts().size()==closedReference.size(),"closed source scene preserves its exact original part count "+door.number);
            for(int i=0;i<closedReference.size();i++)assertPart(t,selected.closed().parts().get(i),closedReference.get(i).getAsJsonObject(),"closed source part "+door.number+" #"+i);
            Map<String,JsonObject> models=new HashMap<>();for(var value:door.reference.getAsJsonArray("models")){var model=value.getAsJsonObject();models.put(model.get("model").getAsString(),model);}
            Set<String> movingRoles=new HashSet<>();int fixed=0;
            for(var part:selected.open().parts()) {
                var model=models.get(part.model().toString());t.assertTrue(model!=null,"OPEN contains only the separately verified source partitions "+door.number+": "+part.model());
                String role=model.get("role").getAsString();var offset=vec(model.getAsJsonArray("partOffset"));
                t.assertTrue(close(part.offset(),offset)&&part.pitch()==0&&part.extraYaw()==0,"partition location/scale keeps the source placement "+door.number+" role="+role);
                if(role.equals("static")){fixed++;t.assertTrue(part.yaw()==0,"stone frame/header/outer ornament remains fixed "+door.number);}
                else {
                    movingRoles.add(role);var hinge=door.reference.getAsJsonObject("leafHinges").getAsJsonObject(role);
                    t.assertTrue(part.yaw()==hinge.get("yaw").getAsDouble()&&Math.abs(part.yaw())==90,"leaf makes exactly one quarter-turn "+door.number+" role="+role);
                    t.assertTrue(close(part.pivot(),vec(hinge.getAsJsonArray("pivot")).subtract(offset)),"leaf rotates at its prescribed edge hinge, not the whole door centre "+door.number+" role="+role);
                }
            }
            t.assertTrue(movingRoles.size()==door.leaves,"exact single/double leaf count follows the user's marked moving panels "+door.number);
            if(door.leaves==2)t.assertTrue(movingRoles.equals(Set.of("left","right")),"both paired leaves belong to the same OPEN state");
            else t.assertTrue(movingRoles.equals(Set.of("single")),"single door has one moving leaf");
            if(!door.number.equals("93160"))t.assertTrue(fixed>0,"framed doors retain independently fixed frame pieces "+door.number);
        }
        for(String number:List.of("90247","93156","90143","93159","90777","93147")) {
            var entry=DebugCatalogue.entries().stream().filter(value->value.temporaryId().equals(number)).findFirst().orElseThrow();var candidate=Registries.BLOCK.get(entry.registryId());
            t.assertTrue(candidate instanceof CompositeRootBlock&&!((CompositeRootBlock)candidate).spec.openable,"explicitly rejected candidate does not acquire a door mechanism "+number);
        }
        t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="v126_original_door_control")
    public void original90001RetainsItsExistingSingleLeafRmbMechanismAndExactClosedGeometry(TestContext t) {
        var reference=new JsonObject();reference.addProperty("mainId","90001");
        var door=new Door("90001","bloodborne_dw:prototype_double_door",0,1,reference);
        var original=block(door);
        t.assertTrue(original.spec.variants.size()==1&&!original.spec.simplePhysicalCollision&&original.spec.requiredSupport,"original V12 single-leaf door retains its authored variant, physics and support contract");
        var closedPose=original.spec.variants.get(0).closed();var openPose=original.spec.variants.get(0).open();
        t.assertTrue(closedPose.parts().size()==4&&openPose.parts().size()==4,"90001 keeps its existing frame/header and one central leaf without a second inserted door mechanism");
        for(int i:new int[]{0,1,3})t.assertTrue(closedPose.parts().get(i).equals(openPose.parts().get(i)),"original V12 side panels/header remain unchanged during opening");
        var world=t.getWorld();var player=player(world);var root=t.getAbsolutePos(new BlockPos(16576,96,64));
        try {
            var closed=place(t,player,root,door,0,null);use(t,player,closed);var open=instance(world,root);
            t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&open.owner().equals(closed.owner()),"existing 90001 RMB opens without replacing its instance");
            use(t,player,open);var again=instance(world,root);
            t.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN)&&again.cells().equals(closed.cells()),"existing 90001 second RMB restores its exact original authored geometry");remove(t,again);
        }finally{player.discard();}
        t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="v1273_single_door")
    public void door90960KeepsItsFrameAndBlocksOnlyWhenClosedAtAllEightYaws(TestContext t){
        var reference=JsonParser.parseString("{\"passage\":{\"footCentreUnits\":[8,1,8],\"normal\":[0,0,1],\"crossingDistanceUnits\":64}}").getAsJsonObject();
        var door=new Door("90960","bloodborne_dw:source_stairs_andesite_stairs_top",0,1,reference);
        var spec=block(door).spec;
        t.assertTrue(spec.openable&&spec.functionalDoorPhysics,"90960 is an explicitly reviewed functional single door");
        t.assertTrue(spec.variants.get(0).closed().parts().get(0).equals(spec.variants.get(0).open().parts().get(0)),"The entire stone frame remains fixed while the wooden leaf rotates");
        var world=t.getWorld();var player=player(world);var root=t.getAbsolutePos(new BlockPos(16640,96,64));List<Runnable> cases=new ArrayList<>();
        for(int yaw=0;yaw<8;yaw++){final int rotation=yaw;cases.add(()->{
            var closed=place(t,player,root,door,rotation,null);outline(t,closed);movement(t,player,root,door,rotation,false);
            use(t,player,closed);var opened=instance(world,root);
            t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&closed.owner().equals(opened.owner()),"Native RMB opens the same single door owner");
            outline(t,opened);movement(t,player,root,door,rotation,true);
            use(t,player,opened);var restored=instance(world,root);
            t.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN)&&closed.cells().equals(restored.cells()),"Second RMB exactly restores closed aim and collision geometry");remove(t,restored);
        });}
        eachTick(t,player,cases);
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="v1274_single_door_90986")
    public void door90986CentralLeafOpensAndClosesWithFixedSurroundingsAndActualPassageAtAllEightYaws(TestContext t){
        var reference=JsonParser.parseString("{\"passage\":{\"footCentreUnits\":[8,1,8],\"normal\":[0,0,1],\"crossingDistanceUnits\":64}}").getAsJsonObject();
        var door=new Door("90986","bloodborne_dw:source_stairs_dark_prismarine_stairs_inner_right",0,1,reference);
        var spec=block(door).spec;var closedPose=spec.variants.get(0).closed();var openPose=spec.variants.get(0).open();
        t.assertTrue(spec.openable&&spec.functionalDoorPhysics&&!spec.simplePhysicalCollision,"90986 alone opts into the reviewed single-leaf physics instead of the old entire-bay cuboid");
        t.assertTrue(closedPose.collision().size()==6&&openPose.collision().size()==6,"five ordinary fixed surrounding boxes and one moving panel define this door");
        double nativeLeft=8+(11./12-8)*Math.sqrt(2),nativeRight=8+(181./12-8)*Math.sqrt(2),nativeTop=34*Math.sqrt(2);
        t.assertTrue(close(closedPose.collision().get(5).from(),new Vec3d(nativeLeft,0,2))&&close(closedPose.collision().get(5).to(),new Vec3d(nativeRight,nativeTop,14)),"the CLOSED leaf spans its actual native-rescaled width and full visible height");
        t.assertTrue(closedPose.collision().get(5).from().z()==2&&closedPose.collision().get(5).to().z()==14,"closed physical wood spans both source z2/z14 planes, so the rear face is solid too");
        t.assertTrue(closedPose.collision().subList(0,5).equals(openPose.collision().subList(0,5)),"masonry, side windows and upper arch never move physically");
        t.assertTrue(closedPose.selection().equals(closedPose.collision())&&openPose.selection().equals(openPose.collision()),"the player's target outline is the real door collision in both states");
        var world=t.getWorld();var player=player(world);var root=t.getAbsolutePos(new BlockPos(16704,96,64));List<Runnable> cases=new ArrayList<>();
        for(int yaw=0;yaw<8;yaw++){final int rotation=yaw;cases.add(()->{
            var closed=place(t,player,root,door,rotation,null);outline(t,closed);leaf90986Contact(t,player,root,rotation);movement(t,player,root,door,rotation,false);movement(t,player,root,reverse(door),rotation,false);
            movement(t,player,root,raised90986(door),rotation,false);movement(t,player,root,reverse(raised90986(door)),rotation,false);
            use(t,player,closed);var opened=instance(world,root);
            t.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN)&&opened.owner().equals(closed.owner()),"actual root/helper RMB opens the central leaf without changing instance identity at yaw"+rotation);
            outline(t,opened);movement(t,player,root,door,rotation,true);movement(t,player,root,reverse(door),rotation,true);
            movement(t,player,root,raised90986(door),rotation,true);movement(t,player,root,reverse(raised90986(door)),rotation,true);
            var entity=(CompositeBlockEntity)world.getBlockEntity(root);var saved=entity.createNbt();
            var restored=new CompositeBlockEntity(root,world.getBlockState(root));restored.readNbt(saved);
            t.assertTrue(restored.resident().equals(entity.resident())&&restored.contributions().equals(entity.contributions())&&restored.payload().equals(entity.payload()),"native saving retains the open central door and all exact fixed/moving helper data at yaw"+rotation);
            t.assertTrue(CompositeData.state(opened.rootData()).get(CompositeRootBlock.OPEN),"cached root data records the open state at yaw"+rotation);
            use(t,player,opened);var again=instance(world,root);
            t.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN)&&again.owner().equals(closed.owner())&&again.cells().equals(closed.cells()),"second RMB restores exact closed physical and selection occupancy at yaw"+rotation);
            remove(t,again);
        });}
        eachTick(t,player,cases);
    }

    private static Door reverse(Door door){
        var reference=door.reference.deepCopy();var normal=reference.getAsJsonObject("passage").getAsJsonArray("normal");
        for(int i=0;i<normal.size();i++)normal.set(i,new JsonPrimitive(-normal.get(i).getAsDouble()));
        return new Door(door.number,door.registry,door.pose,door.leaves,reference);
    }
    private static Door raised90986(Door door){
        var reference=door.reference.deepCopy();reference.getAsJsonObject("passage").getAsJsonArray("footCentreUnits").set(1,new JsonPrimitive(17));
        return new Door(door.number,door.registry,door.pose,door.leaves,reference);
    }
    private static void leaf90986Contact(TestContext t,ServerPlayerEntity player,BlockPos root,int yaw){
        leaf90986Contact(t,player,root,yaw,new NbtCompound());
    }
    private static void leaf90986Contact(TestContext t,ServerPlayerEntity player,BlockPos root,int yaw,NbtCompound payload){
        var shift=CompositeSourceShift.local(payload);double mount=payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);
        for(double height:List.of(1.,34*Math.sqrt(2)/16-.25))for(boolean rear:List.of(false,true)){
            var start=global(new Vec3d(.5+shift.x(),height+shift.y()+mount,(rear?2.5:-1.5)+shift.z()),yaw,root);
            var end=global(new Vec3d(.5+shift.x(),height+shift.y()+mount,(rear?-1.5:2.5)+shift.z()),yaw,root);
            var expected=global(new Vec3d(.5+shift.x(),height+shift.y()+mount,(rear?14.:2.)/16+shift.z()),yaw,root);
            var hit=t.getWorld().raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
            t.assertTrue(hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK&&hit.getPos().distanceTo(expected)<.125,"physical contact reaches the visible "+(rear?"rear":"front")+" wooden face at ordinary and near-top height, not a low leaf/static-header obstruction, at yaw"+yaw+" y="+height+": "+hit.getPos());
        }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="v1274_saved_door_90986")
    public void actualV1273SavedDoor90986RebuildsWholeBayLedgerWithoutGhostsOrForeignDataLoss(TestContext t){
        var fixture=legacy90986();var sourceRoot=fixture.getIntArray("sourceRoot");
        var rawRoot=fixture.getCompound("rootBlockEntity").copy();var originalOwner=CompositeData.owner(rawRoot.getCompound("resident"));
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(16768,96,64));
        var owner=new dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner(originalOwner.instanceId(),originalOwner.registryId(),CompositeData.cell(root));
        var rows=fixture.getList("cells",10);var before=new TreeMap<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell,dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint>();
        dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData rootData=null;
        for(int i=0;i<rows.size();i++){
            var row=rows.getCompound(i);var position=row.getIntArray("pos");var entries=CompositeData.contributions(row.getList("owners",10));
            t.assertTrue(entries.size()==1&&entries.get(0).owner().equals(originalOwner),"fixture retains only the actual old90986 owner at its saved cell");
            var entry=entries.get(0);before.put(new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell(position[0]-sourceRoot[0],position[1]-sourceRoot[1],position[2]-sourceRoot[2]),entry.shape());
            if(rootData==null)rootData=entry.rootData();else t.assertTrue(rootData.equals(entry.rootData()),"the old saved whole bay carries one identical typed root payload");
        }
        t.assertTrue(before.size()==24&&rootData!=null,"the test loads the real .3 saved24-cell enclosing bay, not today's door descriptor");
        var selected=CompositeData.state(rootData);var oldPayload=CompositeData.nbt(rootData.blockEntityNbt());
        var old=new ObjectInstance(owner,rootData,before);var door=new Door("90986",owner.registryId(),0,1,JsonParser.parseString("{\"passage\":{\"footCentreUnits\":[8,1,8],\"normal\":[0,0,1],\"crossingDistanceUnits\":64}}").getAsJsonObject());
        var shift=CompositeSourceShift.local(oldPayload);double mount=oldPayload.getDouble("MountY")+oldPayload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);
        var foot=door.reference.getAsJsonObject("passage").getAsJsonArray("footCentreUnits");foot.set(0,new JsonPrimitive(8+16*shift.x()));foot.set(1,new JsonPrimitive(1+16*(shift.y()+mount)));foot.set(2,new JsonPrimitive(8+16*shift.z()));
        var after=CompositeRuntime.instance(world,root,selected,owner.instanceId(),oldPayload);load(world,root,door,0);
        for(var cell:before.keySet())world.getChunk(CompositeData.pos(owner.root().add(cell)));
        var access=new FabricCompositeWorld(world,Map.of(owner,old));
        t.assertTrue(CompositeRuntime.execute(access,CompositeRuntime.core().placement(access,old,List.of())).outcome()==TransactionCore.Outcome.COMMITTED,"the old native saved footprint is installed unchanged before any migration");
        rawRoot.putInt("x",root.getX());rawRoot.putInt("y",root.getY());rawRoot.putInt("z",root.getZ());rawRoot.put("resident",CompositeData.owner(owner));
        rawRoot.put("owners",CompositeData.contributions(CompositeRuntime.contributions(world,root)));
        ((CompositeBlockEntity)world.getBlockEntity(root)).readNbt(rawRoot);
        var changed=before.keySet().stream().filter(cell->!cell.equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)&&!before.get(cell).equals(after.cells().get(cell))).toList();
        t.assertTrue(changed.size()>=2,"saved helpers contain enclosing-bay physical shapes different from the current fixed frame and moving leaf");
        var guestRoot=CompositeData.pos(owner.root().add(changed.get(0)));var guestBlock=Registries.BLOCK.get(new Identifier("bloodborne_dw:prototype_glass_window_02"));
        var guestPayload=new NbtCompound();guestPayload.putString("NeighbourPrivate","retained");guestPayload.putLong("NeighbourLong",Long.MIN_VALUE);
        var guest=CompositeRuntime.instance(world,guestRoot,guestBlock.getDefaultState(),UUID.randomUUID(),guestPayload);
        for(var cell:guest.cells().keySet())world.getChunk(CompositeData.pos(guest.owner().root().add(cell)));
        var guestAccess=new FabricCompositeWorld(world,Map.of(guest.owner(),guest));
        t.assertTrue(SourceConversionScope.initialInstances(Set.of(guest.owner().instanceId()),()->CompositeRuntime.execute(guestAccess,CompositeRuntime.core().placement(guestAccess,guest,List.of()))).outcome()==TransactionCore.Outcome.COMMITTED,"an independent native owner already shares one of the old bay cells");
        var chestPosition=changed.stream().map(owner.root()::add).filter(cell->!guest.cells().containsKey(cell.subtract(guest.owner().root()))).map(CompositeData::pos).findFirst().orElseThrow();
        world.setBlockState(chestPosition,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);var chest=(ChestBlockEntity)world.getBlockEntity(chestPosition);
        var diamonds=new ItemStack(Items.DIAMOND,7);diamonds.getOrCreateNbt().putLong("ForeignTypedLong",Long.MIN_VALUE);diamonds.getOrCreateNbt().putIntArray("ForeignTypedArray",new int[]{1,-2,17});chest.setStack(0,diamonds);
        chest.setCustomName(net.minecraft.text.Text.literal("Foreign chest retained"));var chestBefore=chest.createNbt();var chestState=world.getBlockState(chestPosition);
        var guestBindings=new TreeMap<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell,List<CompositeData.Contribution>>();
        for(var cell:guest.cells().keySet()){var absolute=guest.owner().root().add(cell);guestBindings.put(absolute,CompositeLedger.get(world).at(absolute).stream().filter(entry->entry.owner().equals(guest.owner())).toList());}
        ImportedTypeMigration.forget(world);ImportedCollisionMigration.forget(world);ImportedCollisionMigration.loadedChunk(world,new ChunkPos(root));
        var cached=(CompositeBlockEntity)world.getBlockEntity(root);
        t.assertTrue(cached.resident().equals(owner)&&cached.payload().equals(oldPayload)&&world.getBlockState(root).equals(selected),"loading migration retains the original .3 UUID, state and exact typed payload");
        for(var cell:before.keySet()){
            var entries=CompositeLedger.get(world).at(owner.root().add(cell)).stream().filter(entry->entry.owner().equals(owner)).toList();
            t.assertTrue(after.cells().containsKey(cell)?entries.size()==1&&entries.get(0).shape().equals(after.cells().get(cell)):entries.isEmpty(),"no whole-bay ghost binding remains after rebuilding saved cell"+cell);
        }
        for(var cell:after.cells().entrySet())t.assertTrue(CompositeLedger.get(world).at(owner.root().add(cell.getKey())).stream().anyMatch(entry->entry.owner().equals(owner)&&entry.shape().equals(cell.getValue())),"every current door cell has its correct saved contribution");
        for(var binding:guestBindings.entrySet())t.assertTrue(CompositeLedger.get(world).at(binding.getKey()).stream().filter(entry->entry.owner().equals(guest.owner())).toList().equals(binding.getValue()),"the neighbouring owner's typed helper binding is untouched");
        t.assertTrue(world.getBlockState(chestPosition).equals(chestState)&&world.getBlockEntity(chestPosition).createNbt().equals(chestBefore),"foreign chest block, inventory and opaque typed item data survive migration exactly");
        t.assertTrue(!ImportedCollisionMigration.pending(world,owner)&&!SourceConversionScope.initialInstance(owner.instanceId()),"saved-door migration finishes without a persistent placement privilege");
        CompositeRuntime.remove(world,guest.owner(),null,false);world.setBlockState(chestPosition,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        var player=player(world);
        try{var closed=instance(world,root);outline(t,closed);leaf90986Contact(t,player,root,0,oldPayload);movement(t,player,root,door,0,false);use(t,player,closed);var opened=instance(world,root);outline(t,opened);movement(t,player,root,door,0,true);movement(t,player,root,reverse(door),0,true);remove(t,opened);}finally{player.discard();}
        t.complete();
    }

    private static NbtCompound legacy90986(){
        var input=FunctionalDoorsV126GameTests.class.getResourceAsStream("/bloodborne_dw/door90986-v1273-saved-owner.nbt");
        if(input==null)throw new IllegalStateException("Missing actual v12.7.3 saved90986 fixture");
        try(var source=input){return net.minecraft.nbt.NbtIo.readCompressed(source);}
        catch(Exception failure){throw new IllegalStateException("Invalid old saved90986 fixture",failure);}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1274_single_door_90986")
    public void door90986KeepsExactClosedSourceAndOnlyPartitionsTheMarkedLowerCentralLeaf(TestContext t){
        var spec=CompositeSpec.load("source_stairs_dark_prismarine_stairs_inner_right");
        var closed=spec.variants.get(0).closed();var opened=spec.variants.get(0).open();
        t.assertTrue(closed.parts().size()==1&&closed.parts().get(0).model().toString().equals("bloodborne_dw:remaining/source/minecraft/block/stairs/dark_prismarine_stairs_inner_right")&&close(closed.parts().get(0).offset(),new Vec3d(0,16,0)),"closed90986 keeps the original source model and exact installation offset");
        t.assertTrue(opened.parts().size()==2,"the open pose has exactly one central leaf and one fixed remainder");
        var moving=opened.parts().get(0);var fixed=opened.parts().get(1);
        double nativeLeft=8+(11./12-8)*Math.sqrt(2);
        t.assertTrue(moving.model().toString().equals("bloodborne_dw:remaining/final_followups/door_90986_leaf")&&moving.yaw()==90&&moving.pitch()==0&&moving.extraYaw()==0&&close(moving.pivot(),new Vec3d(nativeLeft,0,2))&&close(moving.offset(),new Vec3d(0,16,0)),"only the marked central panel makes one quarter-turn about its actual native-rescaled left edge");
        t.assertTrue(fixed.model().toString().equals("bloodborne_dw:remaining/final_followups/door_90986_frame")&&fixed.yaw()==0&&fixed.pitch()==0&&fixed.extraYaw()==0&&close(fixed.offset(),new Vec3d(0,16,0)),"all surrounding source architecture retains its original location and orientation");
        var source=modelResource("remaining/source/minecraft/block/stairs/dark_prismarine_stairs_inner_right").getAsJsonArray("elements");
        var frame=modelResource("remaining/final_followups/door_90986_frame").getAsJsonArray("elements");
        var leaf=modelResource("remaining/final_followups/door_90986_leaf").getAsJsonArray("elements");
        t.assertTrue(source.size()>=75&&frame.size()==source.size()+1&&leaf.size()==1,"only the lower marked source panel is partitioned; all74 other old elements and reviewed cap planes stay fixed");
        int cursor=0,movingIndex=0;
        for(int index=0;index<source.size();index++){
            var element=source.get(index).getAsJsonObject();
            if(index!=0){t.assertTrue(frame.get(cursor++).equals(element),"fixed source geometry/rotation/texture/UV is untouched for element"+index);continue;}
            var left=frame.get(cursor++).getAsJsonObject();var right=frame.get(cursor++).getAsJsonObject();var centre=leaf.get(movingIndex++).getAsJsonObject();
            t.assertTrue(left.getAsJsonArray("from").get(0).getAsDouble()==-9&&Math.abs(left.getAsJsonArray("to").get(0).getAsDouble()-11./12)<1e-10&&Math.abs(right.getAsJsonArray("from").get(0).getAsDouble()-181./12)<1e-10&&right.getAsJsonArray("to").get(0).getAsDouble()==25,"fixed side strips meet the exact central texture boundaries for layer"+index);
            t.assertTrue(Math.abs(centre.getAsJsonArray("from").get(0).getAsDouble()-11./12)<1e-10&&Math.abs(centre.getAsJsonArray("to").get(0).getAsDouble()-181./12)<1e-10,"the moving region is the single marked centre, not the sidelights, for layer"+index);
            for(String side:List.of("north","south")){
                var old=element.getAsJsonObject("faces").getAsJsonObject(side);var central=centre.getAsJsonObject("faces").getAsJsonObject(side);
                t.assertTrue(old.get("texture").equals(central.get("texture"))&&old.getAsJsonArray("uv").get(1).equals(central.getAsJsonArray("uv").get(1))&&old.getAsJsonArray("uv").get(3).equals(central.getAsJsonArray("uv").get(3)),"the source panel texture and complete vertical UV interval survive layer"+index+" face"+side);
                t.assertTrue(Math.abs(central.getAsJsonArray("uv").get(0).getAsDouble()-1.75)<1e-10&&Math.abs(central.getAsJsonArray("uv").get(2).getAsDouble()-4.25)<1e-10,"the central leaf uses only the original atlas columns14..34 at layer"+index+" face"+side);
            }
        }
        t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1274_single_door_90986")
    public void door90986UpperWoodenArchInfillStaysWholeAboveTheMarkedLowerLeafAndNeverFollowsItsHinge(TestContext t){
        var spec=CompositeSpec.load("source_stairs_dark_prismarine_stairs_inner_right");
        var source=modelResource("remaining/source/minecraft/block/stairs/dark_prismarine_stairs_inner_right").getAsJsonArray("elements");
        var upper=source.get(3).getAsJsonObject();var lower=source.get(0).getAsJsonObject();
        var ur=upper.getAsJsonObject("rotation");var lr=lower.getAsJsonObject("rotation");
        t.assertTrue(ur.get("angle").getAsDouble()==0&&lr.get("angle").getAsDouble()==0&&ur.get("rescale").getAsBoolean()&&lr.get("rescale").getAsBoolean()&&ur.get("axis").getAsString().equals("z")&&lr.get("axis").getAsString().equals("z"),"the source's authored zero-angle rescale uses native sqrt2 in X/Y; raw overlap cannot classify the upper infill as a lower layer");
        double lowerTop=nativeRescaledDoorY(lower,lower.getAsJsonArray("to").get(1).getAsDouble());
        double upperBottom=nativeRescaledDoorY(upper,upper.getAsJsonArray("from").get(1).getAsDouble());
        double upperTop=nativeRescaledDoorY(upper,upper.getAsJsonArray("to").get(1).getAsDouble());
        t.assertTrue(lowerTop>3&&lowerTop<3.01&&upperBottom>3.15&&upperTop>5.10&&upperBottom>lowerTop,"the independently classified original upper fan is visibly above the entire lower red-marked door, despite overlapping raw coordinates");
        var frame=modelResource("remaining/final_followups/door_90986_frame").getAsJsonArray("elements");var leaf=modelResource("remaining/final_followups/door_90986_leaf").getAsJsonArray("elements");
        int fullUpper=0;for(var element:frame)if(element.equals(upper))fullUpper++;
        t.assertTrue(fullUpper==1,"OPEN retains the complete old upper infill, including its centre, UV, native rescale origin and all original faces exactly once in the fixed model");
        for(var value:leaf){var faces=value.getAsJsonObject().getAsJsonObject("faces");for(String side:List.of("north","south")){
            var uv=faces.getAsJsonObject(side).getAsJsonArray("uv");
            t.assertTrue(uv.get(1).getAsDouble()==10&&uv.get(3).getAsDouble()==16,"the moving panel contains only the lower source atlas row; the upper fan's UV rows2..6 cannot rotate or leave a hole");
        }}
        var fixed=spec.variants.get(0).open().parts().get(1);
        t.assertTrue(fixed.yaw()==0&&fixed.pitch()==0&&fixed.extraYaw()==0&&fixed.offset().equals(spec.variants.get(0).closed().parts().get(0).offset()),"the whole upper infill's CLOSED and OPEN spatial transforms are identical");
        t.complete();
    }
    private static double nativeRescaledDoorY(JsonObject element,double rawY){
        double origin=element.getAsJsonObject("rotation").getAsJsonArray("origin").get(1).getAsDouble();return (origin+(rawY-origin)*Math.sqrt(2)+16)/16;
    }

    private static JsonObject modelResource(String model){
        var input=FunctionalDoorsV126GameTests.class.getResourceAsStream("/assets/bloodborne_dw/models/"+model+".json");
        if(input==null)throw new IllegalStateException("Missing door source partition "+model);
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(Exception failure){throw new IllegalStateException("Invalid door source partition "+model,failure);}
    }
    private static boolean close(CompositeSpec.Vec value,Vec3d expected){return Math.abs(value.x()-expected.x)<1e-8&&Math.abs(value.y()-expected.y)<1e-8&&Math.abs(value.z()-expected.z)<1e-8;}
    private static void assertPart(TestContext t,CompositeSpec.Part actual,JsonObject expected,String message) {
        t.assertTrue(actual.model().toString().equals(expected.get("model").getAsString()),message+" model");
        t.assertTrue(close(actual.offset(),expected.has("offset")?vec(expected.getAsJsonArray("offset")):Vec3d.ZERO),message+" offset");
        t.assertTrue(actual.yaw()==(expected.has("yaw")?expected.get("yaw").getAsDouble():0)&&actual.pitch()==(expected.has("pitch")?expected.get("pitch").getAsDouble():0),message+" unchanged source orientation");
        t.assertTrue(actual.extraYaw()==(expected.has("extraYaw")?expected.get("extraYaw").getAsDouble():0),message+" additional source orientation");
    }
}

