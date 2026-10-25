package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.InteractionReach;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.*;

/** The old authored three-seat bench contract, adapted to retained ownership
 * and shared static recipes. No block/geometry/position registry is added. */
public final class BenchSeating {
    public static EntityType<BenchSeatEntity> ENTITY;
    public record Point(Vec3d contact,float yaw){}
    public record Target(BlockPos root,String identity,List<Point> seats){public Target{root=root.toImmutable();seats=List.copyOf(seats);}}
    private BenchSeating(){}
    public static void initialize(){
        if(ENTITY!=null)return;
        ENTITY=Registry.register(Registries.ENTITY_TYPE,new Identifier("bloodborne_dw","bench_seat"),EntityType.Builder.<BenchSeatEntity>create(BenchSeatEntity::new,SpawnGroup.MISC).setDimensions(.01f,.01f).maxTrackingRange(8).trackingTickInterval(20).disableSaving().disableSummon().build("bloodborne_dw:bench_seat"));
        UseBlockCallback.EVENT.register(BenchSeating::use);
    }
    private static void rotate(Matrix4d m,CompositeSpec.Vec p,double degrees,boolean pitch){if(degrees==0)return;m.translate(p.x()/16,p.y()/16,p.z()/16);if(pitch)m.rotateX(java.lang.Math.toRadians(degrees));else m.rotateY(java.lang.Math.toRadians(degrees));m.translate(-p.x()/16,-p.y()/16,-p.z()/16);}
    private static List<Point> points(BlockPos root,BlockState state,NbtCompound payload){
        if(!(state.getBlock() instanceof CompositeRootBlock block))return List.of();
        var shift=CompositeSourceShift.world(state,payload);var m=new Matrix4d().translate(shift.x(),shift.y()+payload.getDouble("MountY")+payload.getDouble(VerticalMount.KEY),shift.z());
        m.translate(.5,.5,.5).rotateY(java.lang.Math.toRadians(-state.get(CompositeRootBlock.ROTATION)*45)).translate(-.5,-.5,-.5);
        List<Point> points=new ArrayList<>();
        for(var part:block.spec.pose(state).parts()){
            String model=part.model().toString();boolean straight=model.equals("bloodborne_dw:remaining/source/minecraft/block/hold/bench")||model.equals("bloodborne_dw:remaining/source/minecraft/block/hold/bench_1");boolean diagonal=model.equals("bloodborne_dw:remaining/source/minecraft/block/hold/bench_rotate");
            if(!straight&&!diagonal)continue;
            var transform=new Matrix4d(m).translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);
            rotate(transform,part.pivot(),-part.yaw(),false);rotate(transform,part.pivot(),-part.pitch(),true);rotate(transform,part.extraPivot(),part.extraYaw(),false);
            // Same authored plank contacts as old o_bench; the rotated source
            // model stores its plank along Z and rotates it -45 about (8,8).
            if(diagonal)transform.translate(.5,2.33134/16,.5).rotateY(java.lang.Math.toRadians(-45)).translate(-.5,-2.33134/16,-.5);
            var forward=transform.transformDirection(diagonal?new Vector3d(-1,0,0):new Vector3d(0,0,-1));float yaw=(float)java.lang.Math.toDegrees(java.lang.Math.atan2(-forward.x,forward.z));
            for(double x:new double[]{-.45,.5,1.45}){
                var p=transform.transformPosition(diagonal?new Vector3d(.3372025,0,x):new Vector3d(x,0,.3372025));
                points.add(new Point(new Vec3d(root.getX()+p.x,root.getY()+p.y,root.getZ()+p.z),yaw));
            }
        }
        return List.copyOf(points);
    }
    private static String poseIdentity(BlockState state,NbtCompound payload,String owner){return owner+"/"+state+"/"+CompositeSourceShift.local(payload)+"/"+payload.getDouble("MountY")+"/"+payload.getDouble(VerticalMount.KEY);}
    private static void add(Map<String,Target> out,BlockPos root,BlockState state,NbtCompound payload,String identity){var points=points(root,state,payload);if(!points.isEmpty())out.putIfAbsent(root+"/"+identity,new Target(root,identity,points));}
    public static List<Target> targets(World world,BlockPos cell){
        if(!world.isChunkLoaded(cell))return List.of();Map<String,Target> result=new LinkedHashMap<>();BlockState state=world.getBlockState(cell);
        if(state.getBlock() instanceof StaticArchitectureBlock){
            for(var fragment:StaticArchitectureCatalog.recipe(state).fragments()){
                var offset=fragment.origin();var root=cell.add(offset.x(),offset.y(),offset.z());var pose=fragment.pose();
                if(world.isChunkLoaded(root)&&StaticArchitectureCatalog.contains(world.getBlockState(root))&&StaticArchitectureCatalog.recipe(world.getBlockState(root)).fragments().stream().anyMatch(f->f.pose()==pose&&f.origin().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)))add(result,root,pose.state(),pose.payload(),"static/"+pose.key());
            }
            return List.copyOf(result.values());
        }
        if(!(state.getBlock() instanceof CompositeRootBlock)&&!state.isOf(CompositeArchitecture.CELL))return List.of();
        for(var contribution:CompositeRuntime.contributions(world,cell)){
            var root=CompositeData.pos(contribution.owner().root());if(!world.isChunkLoaded(root))continue;
            var original=CompositeData.state(contribution.rootData());var payload=CompositeData.nbt(contribution.rootData().blockEntityNbt());
            add(result,root,original,payload,poseIdentity(original,payload,contribution.owner().instanceId().toString()));
        }
        if(result.isEmpty()&&state.getBlock() instanceof CompositeRootBlock){
            NbtCompound payload=world.getBlockEntity(cell) instanceof CompositeBlockEntity own?own.sourceRenderPayload():new NbtCompound();
            add(result,cell,state,payload,poseIdentity(state,payload,"direct"));
        }
        return List.copyOf(result.values());
    }
    public static boolean valid(World world,BlockPos root,String identity){
        if(!world.isChunkLoaded(root))return false;var state=world.getBlockState(root);
        if(identity.startsWith("static/"))return state.getBlock() instanceof StaticArchitectureBlock&&StaticArchitectureCatalog.recipe(state).fragments().stream().anyMatch(f->f.origin().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)&&identity.equals("static/"+f.pose().key()));
        if(!(state.getBlock() instanceof CompositeRootBlock)&&!state.isOf(CompositeArchitecture.CELL))return false;
        for(var c:CompositeRuntime.contributions(world,root))if(CompositeData.pos(c.owner().root()).equals(root)){
            var original=CompositeData.state(c.rootData());var payload=CompositeData.nbt(c.rootData().blockEntityNbt());if(identity.equals(poseIdentity(original,payload,c.owner().instanceId().toString())))return true;
        }
        if(state.getBlock() instanceof CompositeRootBlock){var payload=world.getBlockEntity(root) instanceof CompositeBlockEntity own?own.sourceRenderPayload():new NbtCompound();return identity.equals(poseIdentity(state,payload,"direct"));}
        return false;
    }
    public static ActionResult use(PlayerEntity player,World world,Hand hand,BlockHitResult hit){
        if(hand!=Hand.MAIN_HAND||player.isSpectator()||player.isSneaking()||player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem||player.getMainHandStack().getItem() instanceof dev.dreamwalker.bloodbornedw.tool.BbToolItem)return ActionResult.PASS;
        double reach=InteractionReach.block(player);if(player.getEyePos().squaredDistanceTo(hit.getPos())>(reach+.01)*(reach+.01))return ActionResult.PASS;
        Target best=null;double distance=9;
        for(var target:targets(world,hit.getBlockPos()))for(var point:target.seats){double d=point.contact.squaredDistanceTo(hit.getPos());if(d<distance){best=target;distance=d;}}
        if(best==null)return ActionResult.PASS;
        return world instanceof ServerWorld server?sit(server,best,player,hit.getPos()):ActionResult.SUCCESS;
    }
    public static ActionResult sit(ServerWorld world,Target target,PlayerEntity player,Vec3d click){
        if(player.hasVehicle()||!valid(world,target.root,target.identity))return ActionResult.FAIL;
        Vec3d min=target.seats.get(0).contact,max=min;for(var p:target.seats){min=new Vec3d(java.lang.Math.min(min.x,p.contact.x),java.lang.Math.min(min.y,p.contact.y),java.lang.Math.min(min.z,p.contact.z));max=new Vec3d(java.lang.Math.max(max.x,p.contact.x),java.lang.Math.max(max.y,p.contact.y),java.lang.Math.max(max.z,p.contact.z));}
        Set<Integer> occupied=new HashSet<>();
        for(var seat:world.getEntitiesByClass(BenchSeatEntity.class,new Box(min,max).expand(1),e->e.rootPos().equals(target.root)&&e.identity().equals(target.identity))){if(seat.hasPassengers())occupied.add(seat.index());else seat.discard();}
        int selected=-1;double nearest=9;for(int i=0;i<target.seats.size();i++)if(!occupied.contains(i)){double d=target.seats.get(i).contact.squaredDistanceTo(click);if(d<nearest){selected=i;nearest=d;}}
        if(selected<0)return ActionResult.FAIL;var point=target.seats.get(selected);var seat=ENTITY.create(world);if(seat==null)return ActionResult.FAIL;
        seat.bind(target.root,target.identity,selected,point.contact,point.yaw,player.getPos());
        if(!world.spawnEntity(seat)||!player.startRiding(seat,true)){seat.discard();return ActionResult.FAIL;}
        seat.updatePassengerPosition(player);player.setYaw(point.yaw);player.setHeadYaw(point.yaw);player.setBodyYaw(point.yaw);return ActionResult.CONSUME;
    }
}
