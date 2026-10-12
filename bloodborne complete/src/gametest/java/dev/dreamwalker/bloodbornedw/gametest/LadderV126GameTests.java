package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Actual native travel of the NEW90586 ladder, rather than merely checking
 * intersection with an abstract climbing box. No original V12 ladder is edited. */
public final class LadderV126GameTests implements FabricGameTest {
    private static ImportedLadderBlock ladder() {
        return (ImportedLadderBlock)CompositeArchitecture.kindBlock("source_hold_ladder_02");
    }
    private static BlockState state(int height,int yaw) {
        return ImportedLadderBlock.normalize(ladder().getDefaultState()
                .with(ImportedLadderBlock.HEIGHT,height==2?ImportedLadderBlock.Height.TWO:ImportedLadderBlock.Height.ONE)
                .with(CompositeRootBlock.ROTATION,yaw));
    }
    private static NbtCompound payload() {
        NbtCompound n=new NbtCompound();n.putInt(ImportedTypeAliases.VERSION_KEY,ImportedTypeAliases.VERSION);return n;
    }
    private static ServerPlayerEntity player(ServerWorld world,boolean creative) {
        var p=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"Ladder126Probe"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND){
            @Override public void send(Packet<?> packet){}
            @Override public void send(Packet<?> packet,PacketCallbacks callbacks){}
        };
        p.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,p);
        p.changeGameMode(creative?GameMode.CREATIVE:GameMode.SURVIVAL);
        p.getAbilities().allowModifyWorld=true;p.getAbilities().flying=false;p.noClip=false;return p;
    }
    private static void load(ServerWorld world,BlockPos root) {
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            int x=(root.getX()>>4)+dx,z=(root.getZ()>>4)+dz;
            world.setChunkForced(x,z,true);world.getChunk(new BlockPos(x<<4,root.getY(),z<<4));
        }
    }
    private static void floor(ServerWorld world,BlockPos root,boolean present) {
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)
            world.setBlockState(root.add(x,-1,z),present?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
    }
    private static Owner place(TestContext t,BlockPos root,BlockState state,NbtCompound supplied) {
        load(t.getWorld(),root);
        var result=CompositeRuntime.place(t.getWorld(),root,state,UUID.randomUUID(),null,supplied);
        t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"ordinary90586 placement commits: "+result);
        return ((CompositeBlockEntity)t.getWorld().getBlockEntity(root)).resident();
    }
    private static void remove(TestContext t,Owner owner) {
        t.assertTrue(CompositeRuntime.remove(t.getWorld(),owner,null,false).outcome()==TransactionCore.Outcome.COMMITTED,"ladder removal commits");
        CompositeRuntime.drain(t.getWorld());
    }
    /** The player's axis-aligned0.6-wide body starts outside the visual front
     * plane. Forward travel must reach the real physics, not start inside it. */
    private static Vec3d front(BlockPos root,BlockState state,NbtCompound n,double feetAboveBase) {
        int yaw=state.get(CompositeRootBlock.ROTATION);double a=Math.toRadians(yaw*45);
        double s=Math.sin(a),c=Math.cos(a),halfProjection=.3*(Math.abs(s)+Math.abs(c));
        var shift=CompositeSourceShift.world(state,n);double distance=.5+halfProjection+.0125;
        return new Vec3d(root.getX()+.5-s*distance+shift.x(),
                root.getY()+n.getDouble("MountY")+n.getDouble(VerticalMount.KEY)+shift.y()+feetAboveBase,
                root.getZ()+.5+c*distance+shift.z());
    }
    private static void reset(ServerPlayerEntity p,Vec3d at,int yaw) {
        p.refreshPositionAndAngles(at.x,at.y,at.z,180+yaw*45,0);
        p.setVelocity(Vec3d.ZERO);p.setSneaking(false);p.setOnGround(false);
        p.getAbilities().flying=false;p.noClip=false;
    }
    private static double forward(TestContext t,ServerPlayerEntity p,int iterations,String scenario) {
        double initial=p.getY(),highest=initial;boolean pressedAgainst=false;
        t.assertTrue(p.isClimbing(),"actual native LivingEntity.isClimbing recognizes90586: "+scenario);
        for(int i=0;i<iterations;i++){
            p.travel(new Vec3d(0,0,1));highest=Math.max(highest,p.getY());pressedAgainst|=p.horizontalCollision;
        }
        t.assertTrue(pressedAgainst,"ordinary forward movement meets functional ladder face rather than walking between rails: "+scenario);
        t.assertTrue(highest-initial>.35,"actual forward travel ascends90586 without jumping/flying: "+scenario+"; gain="+(highest-initial));
        return highest-initial;
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="v126_ladder_native_travel")
    public void actualNativePlayerClimbsDescendsAndHoldsBothHeightsInAllEightOrientations(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(384,96,384));load(world,root);floor(world,root,true);
        var p=player(world,false);
        try {
            for(int height:new int[]{1,2})for(int yaw=0;yaw<8;yaw++){
                var selected=state(height,yaw);var n=payload();var owner=place(t,root,selected,n);
                String scenario="height="+height+",yaw="+yaw;
                reset(p,front(root,selected,n,.075),yaw);forward(t,p,20,scenario);
                // Without forward input, an unsneaking player descends while
                // remaining in contact with the working rung zone.
                Vec3d at=front(root,selected,n,.65);reset(p,at,yaw);p.travel(new Vec3d(0,0,1));
                p.setPosition(p.getX(),at.y,p.getZ());p.setVelocity(Vec3d.ZERO);
                double before=p.getY();for(int i=0;i<14;i++)p.travel(Vec3d.ZERO);
                t.assertTrue(before-p.getY()>.2,"native ladder descent works: "+scenario+"; drop="+(before-p.getY()));
                // Sneak uses vanilla hold-on-ladder behavior, including diagonal
                // forms, rather than a custom positive vertical velocity.
                reset(p,at,yaw);p.travel(new Vec3d(0,0,1));p.setPosition(p.getX(),at.y,p.getZ());
                p.setVelocity(Vec3d.ZERO);p.setSneaking(true);before=p.getY();
                for(int i=0;i<10;i++)p.travel(Vec3d.ZERO);
                t.assertTrue(Math.abs(p.getY()-before)<.02,"native sneaking holds position on90586: "+scenario);
                p.setSneaking(false);p.setPosition(root.getX()+4,root.getY()+.2,root.getZ()+4);
                t.assertTrue(!p.isClimbing(),"90586 does not create a distant climbable cube: "+scenario);
                remove(t,owner);
            }
        } finally {p.discard();floor(world,root,false);}t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=250,batchId="v126_ladder_native_stack")
    public void ordinaryOneInstallerStacksAllHeightPairsAndPlayerClimbsAcrossTheirJoinAtEveryYaw(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(416,96,416));load(world,root);floor(world,root,true);
        var builder=player(world,true);var p=player(world,false);
        try {
            for(int lowerHeight:new int[]{1,2})for(int upperHeight:new int[]{1,2})for(int yaw=0;yaw<8;yaw++){
                var lowerState=state(lowerHeight,yaw);var n=payload();var lower=place(t,root,lowerState,n);
                BlockPos upperRoot=root.up(lowerHeight);load(world,upperRoot);
                // Both lengths come from the same canonical installation item.
                var installer=ladder().art(state(upperHeight,yaw),n);builder.setStackInHand(Hand.MAIN_HAND,installer);
                builder.refreshPositionAndAngles(root.getX()+.5,root.getY(),root.getZ()-8,yaw*45,0);
                // The source rung face rotates about the cell center. The top
                // click is an actual cell of the lower ladder, not a fake floor.
                double angle=Math.toRadians(yaw*45),s=Math.sin(angle),c=Math.cos(angle);
                Vec3d topPoint=new Vec3d(root.getX()+.5-s*.47,upperRoot.getY(),root.getZ()+.5+c*.47);
                BlockPos clicked=BlockPos.ofFloored(topPoint.add(0,-.001,0));
                var hit=new BlockHitResult(topPoint,Direction.UP,clicked,false);
                var result=installer.getItem().useOnBlock(new ItemUsageContext(builder,Hand.MAIN_HAND,hit));
                t.assertTrue(result.isAccepted()&&world.getBlockState(upperRoot).isOf(ladder()),"ordinary item stacks90586 at rotated top cell: "+lowerHeight+"+"+upperHeight+",yaw="+yaw+"; "+CompositeRuntime.lastResult());
                var upperState=world.getBlockState(upperRoot);var upper=(CompositeBlockEntity)world.getBlockEntity(upperRoot);
                t.assertTrue(upperState.get(ImportedLadderBlock.HEIGHT)==(upperHeight==2?ImportedLadderBlock.Height.TWO:ImportedLadderBlock.Height.ONE)
                        &&upperState.get(CompositeRootBlock.ROTATION)==yaw,"ordinary stacking retains requested length and yaw");
                var picked=CompositeRuntime.pick(world,upper.resident());
                t.assertTrue(picked.isOf(ladder().asItem())&&picked.getOrCreateSubNbt("BlockStateTag").getString("height").equals(upperHeight==2?"two":"one"),"one installer copies the stacked height");
                reset(p,front(root,lowerState,n,.075),yaw);
                double gain=forward(t,p,50,"stack="+lowerHeight+"+"+upperHeight+",yaw="+yaw);
                t.assertTrue(gain>lowerHeight+.25,"real native player crosses the seam and reaches the upper ladder: "+lowerHeight+"+"+upperHeight+",yaw="+yaw+"; gain="+gain);
                remove(t,upper.resident());remove(t,lower);
            }
        } finally {builder.discard();p.discard();floor(world,root,false);}t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="v126_ladder_mounted_saved")
    public void actualClimbingFollowsSavedSourceShiftAndUserMountWithoutCopyingPrivateInstanceFields(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(448,96,448));load(world,root);
        var builder=player(world,true);var p=player(world,false);
        try {
            for(int height:new int[]{1,2})for(int yaw=0;yaw<8;yaw++){
                var selected=state(height,yaw);var n=payload();NbtList shift=new NbtList();
                shift.add(NbtDouble.of(.3125));shift.add(NbtDouble.of(.125));shift.add(NbtDouble.of(-.1875));n.put("SourceShift",shift);
                n.putString("CustomEvent","keep ladder event");n.putUuid("LinkedInstance",UUID.randomUUID());
                n.putIntArray("CustomNumbers",new int[]{1,-2,17});var owner=place(t,root,selected,n);
                t.assertTrue(VerticalMount.setOffset(world,root,3.375,builder),"ordinary V12 mount tool transaction moves NEW90586 without changing original ladders");
                var be=(CompositeBlockEntity)world.getBlockEntity(root);var retained=be.payload();var saved=be.createNbt();
                var decoded=new CompositeBlockEntity(root,selected);decoded.readNbt(saved.copy());
                t.assertTrue(decoded.resident().equals(owner)&&decoded.payload().equals(retained)&&decoded.contributions().equals(be.contributions()),"saved NBT round-trip retains exact UUID, links, event, source shift, mount and owned cells");
                be.readNbt(saved.copy());t.assertTrue(be.createNbt().equals(saved),"actual live block entity reload preserves typed saved bytes");
                reset(p,front(root,selected,retained,.075),yaw);forward(t,p,20,"saved shifted height="+height+",yaw="+yaw);
                // The old unshifted root is3.5 blocks below the actual ladder;
                // a full player body there must not acquire a phantom climb.
                reset(p,front(root,selected,n,.075),yaw);
                t.assertTrue(!p.isClimbing(),"user mount removes climbing from the old vertical location");
                var picked=CompositeRuntime.pick(world,owner);
                t.assertTrue(picked.isOf(ladder().asItem())&&ImportedTypeAliases.readArtShift(picked.getNbt()).equals(CompositeSourceShift.local(retained)),"copied installer retains source art anchoring and canonical90586 identity");
                t.assertTrue(picked.getNbt()!=null&&!picked.getNbt().contains("CompositePayload")&&!picked.getNbt().contains("BlockEntityTag")
                        &&!picked.getNbt().contains("LinkedInstance")&&!picked.getNbt().contains("CustomEvent")&&!picked.getNbt().contains(VerticalMount.KEY),"ordinary copying does not leak instance UUID, private links/events or user height into a new installation");
                remove(t,owner);
            }
        } finally {builder.discard();p.discard();}t.complete();
    }
}
