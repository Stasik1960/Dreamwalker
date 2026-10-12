package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
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
        if(value.getBlock() instanceof ImportedArtworkBlock)value=value.with(ImportedArtworkBlock.bankProperty(value),door.pose/16);
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
        for(var offset:value.cells().keySet()) {
            var pos=CompositeData.pos(value.owner().root().add(offset));
            for(var bounds:world.getBlockState(pos).getOutlineShape(world,pos,ShapeContext.of(player)).getBoundingBoxes()) {
                double x=pos.getX()+(bounds.minX+bounds.maxX)/2,y=pos.getY()+(bounds.minY+bounds.maxY)/2,z=pos.getZ()+bounds.minZ;
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.refreshPositionAndAngles(x,y-player.getStandingEyeHeight(),z-2,0,0);
                if(!value.owner().equals(CompositeRuntime.target(world,pos,player)))continue;
                var hit=new BlockHitResult(new Vec3d(x,y,z),Direction.NORTH,pos,false);var current=world.getBlockState(pos);
                t.assertTrue(current.getBlock().onUse(current,world,pos,player,Hand.MAIN_HAND,hit).isAccepted(),"ordinary selected root/helper RMB callback opens/closes "+value.owner().registryId());
                return;
            }
        }
        throw new AssertionError("Door has no reachable ordinary aiming part: "+value.owner().registryId());
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
        player.move(MovementType.SELF,delta);
        if(open)t.assertTrue(player.getPos().squaredDistanceTo(end)<1e-8,"real 0.6×1.8 player crosses the OPEN door without an enclosing AABB: "+door.number+" yaw="+yaw+" actual="+player.getPos()+" expected="+end);
        else t.assertTrue(player.getPos().squaredDistanceTo(end)>.01,"the CLOSED door blocks real player movement: "+door.number+" yaw="+yaw);
        player.refreshPositionAndAngles(root.getX()+20,root.getY()+20,root.getZ()+20,0,0);
    }
    private static void eachTick(TestContext t,ServerPlayerEntity player,List<Runnable> cases) {
        for(int i=0;i<cases.size();i++){var action=cases.get(i);t.runAtTick(i+1,()->{try{action.run();}catch(RuntimeException|Error failure){player.discard();throw failure;}});}
        t.runAtTick(cases.size()+2,()->{player.discard();t.complete();});
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="v126_functional_doors")
    public void sevenApprovedDoorsOpenAndCloseOnRmbAtEveryYawAndActuallyReleasePlayerPassage(TestContext t) {
        var references=doors();var expected=Set.of("90031","93160","90266","93152","90458","93153","90923");
        t.assertTrue(references.size()==7&&references.stream().map(Door::number).collect(java.util.stream.Collectors.toSet()).equals(expected),"exactly seven approved new functional door types are checked");
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
    private static boolean close(CompositeSpec.Vec value,Vec3d expected){return Math.abs(value.x()-expected.x)<1e-8&&Math.abs(value.y()-expected.y)<1e-8&&Math.abs(value.z()-expected.z)<1e-8;}
    private static void assertPart(TestContext t,CompositeSpec.Part actual,JsonObject expected,String message) {
        t.assertTrue(actual.model().toString().equals(expected.get("model").getAsString()),message+" model");
        t.assertTrue(close(actual.offset(),expected.has("offset")?vec(expected.getAsJsonArray("offset")):Vec3d.ZERO),message+" offset");
        t.assertTrue(actual.yaw()==(expected.has("yaw")?expected.get("yaw").getAsDouble():0)&&actual.pitch()==(expected.has("pitch")?expected.get("pitch").getAsDouble():0),message+" unchanged source orientation");
        t.assertTrue(actual.extraYaw()==(expected.has("extraYaw")?expected.get("extraYaw").getAsDouble():0),message+" additional source orientation");
    }
}
