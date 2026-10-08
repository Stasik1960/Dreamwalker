package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.server.world.ServerWorld;

/** V9 user actions: independent artistic types, four yaw poses and durable airborne mounts. */
public final class GlazingMountGameTests implements FabricGameTest {
    private static final String TEMPLATE="bloodborne_dw:window_test";
    private static final BlockPos LOCAL=new BlockPos(8,4,8);
    @GameTest(templateName=TEMPLATE,tickLimit=400,batchId="glazing_mount")
    public void everyCreativeArtAndPickedItemUsesRealVariantInThreeMountedPlanes(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(LOCAL);PlayerEntity p=t.createMockSurvivalPlayer();
        try{
            Set<String> ids=new HashSet<>();
            for(String path:List.of(GlazingTypes.WINDOW01,GlazingTypes.WINDOW02,GlazingTypes.WINDOW03))for(var mount:ThinWindowRootBlock.Mount.values())for(var slab:List.of(SlabType.BOTTOM,SlabType.TOP))for(int yaw=0;yaw<8;yaw++){
                clear(w,root);p.setSneaking(false);p.setPosition(root.getX()+.5,root.getY()+4,root.getZ()-5);p.setYaw(yaw*45);
                var block=CompositeArchitecture.kindBlock(path);Direction face=mount==ThinWindowRootBlock.Mount.CEILING?Direction.DOWN:mount==ThinWindowRootBlock.Mount.FLOOR?Direction.UP:Direction.NORTH;
                BlockPos support=root.offset(face.getOpposite());BlockState backing=mount==ThinWindowRootBlock.Mount.VERTICAL?Blocks.STONE_BRICKS.getDefaultState():Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,slab);w.setBlockState(support,backing,Block.NOTIFY_ALL);
                ItemStack item=block.art(block.getDefaultState().with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT),new NbtCompound());ids.add(DebugCatalogue.entry(item).temporaryId());
                t.assertTrue(place(p,item,support,face).isAccepted(),"actual offered item places its type "+path+"/"+mount+"/"+slab+"/"+yaw);
                BlockState state=w.getBlockState(root);var be=(CompositeBlockEntity)w.getBlockEntity(root);
                t.assertTrue(state.isOf(block)&&state.get(CompositeRootBlock.VARIANT)==0&&state.get(ThinWindowRootBlock.MOUNT)==mount&&(state.get(CompositeRootBlock.ROTATION)&1)==0,"item -> server type/property remains exact and cardinal");
                String expected=path.equals(GlazingTypes.WINDOW01)?"window_01":path.equals(GlazingTypes.WINDOW02)?"window_02":"window_03";
                t.assertTrue(block.spec.pose(state).parts().get(0).model().getPath().endsWith(expected),"server state selects the held artistic source model, not first variant");
                var b=block.spec.mountedBounds(state);double min=root.getY()+be.mountY()+b.from().y()/16,max=root.getY()+be.mountY()+b.to().y()/16;
                if(mount==ThinWindowRootBlock.Mount.FLOOR)t.assertTrue(Math.abs(min-(support.getY()+(slab==SlabType.BOTTOM?.5:1)))<1e-8,"horizontal pane starts at true partial top");
                else if(mount==ThinWindowRootBlock.Mount.CEILING)t.assertTrue(Math.abs(max-(support.getY()+(slab==SlabType.TOP?.5:0)))<1e-8,"horizontal pane starts below true partial underside");
                else t.assertTrue(Math.abs(min-root.getY())<1e-8,"vertical wall pane starts above clicked surface");
                var owner=be.resident();NbtCompound before=be.createNbt();w.removeBlock(support,false);CompositeRuntime.drain(w);
                t.assertTrue(w.getBlockState(root).equals(state)&&owner.equals(((CompositeBlockEntity)w.getBlockEntity(root)).resident()),"removing clicked support never deletes or drops glass");
                t.assertTrue(((CompositeBlockEntity)w.getBlockEntity(root)).createNbt().equals(before),"neighbor removal does not reseat existing glass");
                ItemStack picked=CompositeRuntime.pick(w,owner);t.assertTrue(picked.isOf(block.asItem())&&picked.getSubNbt("CompositePayload")==null,"pick retains exact type while stripping instance mount/source exceptions");
                var copy=new CompositeBlockEntity(root,state);copy.readNbt(before);t.assertTrue(copy.createNbt().equals(before),"airborne mount/owner typed save roundtrip");
                t.assertTrue(CompositeRuntime.remove(w,owner,null,false).outcome()==Outcome.COMMITTED,"whole glass removal works");w.setBlockState(support,backing,Block.NOTIFY_ALL);
                t.assertTrue(place(p,picked,support,face).isAccepted()&&w.getBlockState(root).isOf(block),"picked item places the same independent drawing");
            }
            t.assertTrue(ids.equals(Set.of("90004","90010","90020")),"three independently offered artistic types have separate stable TEMP numbers");t.complete();
        }finally{clear(w,root);p.discard();}
    }
    @GameTest(templateName=TEMPLATE,tickLimit=260,batchId="glazing_mount")
    public void verticalFloorFootAndSourceWorldAnchorUseSeparatePaths(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(LOCAL);PlayerEntity p=t.createMockSurvivalPlayer();var legacy=CompositeArchitecture.kindBlock(GlazingTypes.WINDOW01);
        try{
            for(int oldVariant=0;oldVariant<3;oldVariant++){
                clear(w,root);w.setBlockState(root.down(),Blocks.STONE_SLAB.getDefaultState(),Block.NOTIFY_ALL);p.setPosition(root.getX(),root.getY()+4,root.getZ()-4);p.setYaw(45);p.setSneaking(true);
                ItemStack old=new ItemStack(legacy.asItem());old.getOrCreateSubNbt("BlockStateTag").putString("variant",Integer.toString(oldVariant));old.getOrCreateSubNbt("BlockStateTag").putString("profile","alt");
                var canonical=CompositeArchitecture.kindBlock(oldVariant==0?GlazingTypes.WINDOW01:oldVariant==1?GlazingTypes.WINDOW02:GlazingTypes.WINDOW03);
                t.assertTrue(place(p,old,root.down(),Direction.UP).isAccepted(),"old V8 artistic stack migrates explicitly "+oldVariant);
                var be=(CompositeBlockEntity)w.getBlockEntity(root);BlockState actual=w.getBlockState(root);var b=canonical.spec.mountedBounds(actual);
                t.assertTrue(actual.isOf(canonical)&&actual.get(CompositeRootBlock.VARIANT)==0&&(actual.get(CompositeRootBlock.ROTATION)&1)==0&&Math.abs(be.mountY()+b.from().y()/16+.5)<1e-8,"old item migration preserves drawing/profile and seats cardinal vertical foot on partial surface");
                CompositeRuntime.remove(w,be.resident(),null,false);w.removeBlock(root.down(),false);NbtCompound provenance=new NbtCompound();NbtList shift=new NbtList();for(double axis:new double[]{0,0,.25})shift.add(NbtDouble.of(axis));provenance.put("SourceShift",shift);
                BlockState sourceState=legacy.getDefaultState().with(CompositeRootBlock.VARIANT,oldVariant);
                t.assertTrue(CompositeRuntime.place(w,root,sourceState,UUID.randomUUID(),null,provenance).outcome()==Outcome.COMMITTED,"explicit legacy source installation retains its original art and intrinsic angle");
                var source=(CompositeBlockEntity)w.getBlockEntity(root);t.assertTrue(source.mountY()==0&&source.payload().getList("SourceShift",6).equals(shift)&&!source.payload().getBoolean("GlazingMounted"),"source original negativeY/world compensation stays exact");
                ItemStack picked=CompositeRuntime.pick(w,source.resident());t.assertTrue(picked.isOf(canonical.asItem())&&picked.getSubNbt("CompositePayload")==null,"source pick cannot recreate original angular instance or conversion permission");
            }t.complete();
        }finally{p.setSneaking(false);clear(w,root);p.discard();}
    }
    @GameTest(templateName=TEMPLATE,tickLimit=220,batchId="glazing_mount")
    public void decorativeChangesRequireToolAndConstructionRightsAndKeepMountAndOwner(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(LOCAL);PlayerEntity p=t.createMockSurvivalPlayer();var block=CompositeArchitecture.kindBlock(GlazingTypes.WINDOW02);
        try{
            clear(w,root);w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);p.setPosition(root.getX(),root.getY()+4,root.getZ()-4);p.setYaw(0);
            t.assertTrue(place(p,block.art(block.getDefaultState(),new NbtCompound()),root.down(),Direction.UP).isAccepted(),"ordinary item placement remains available");
            var owner=((CompositeBlockEntity)w.getBlockEntity(root)).resident();BlockState initial=w.getBlockState(root);w.removeBlock(root.down(),false);CompositeRuntime.drain(w);
            ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);tool.getOrCreateNbt().putInt("BuilderAction",BuildingTool.Action.ROTATE.ordinal());p.setStackInHand(Hand.MAIN_HAND,tool);
            aim(p,root);var hit=new BlockHitResult(Vec3d.of(root).add(.5,.05,.5),Direction.UP,root,false);
            t.assertTrue(PrototypeArchitecture.BUILDER_TOOL.useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,hit)).isAccepted()&&w.getBlockState(root).equals(initial),"RMB menu consumes interaction without changing survival geometry");p.getAbilities().creativeMode=true;
            t.assertTrue(BuildingTool.applyBlock(p,root,BuildingTool.Action.ROTATE).isAccepted()&&w.getBlockState(root).get(CompositeRootBlock.ROTATION)==2,"server LKM action adapter turns airborne glass90; graphical menu is a separate client check");
            t.assertTrue(CompositeRuntime.transition(w,owner,initial,p).outcome()==Outcome.COMMITTED,"prepare identical cardinal roundtrip start");
            for(int turn=1;turn<=4;turn++){
                // The public tool uses its actual selection ray; direct transition verifies every airborne mode.
                t.assertTrue(CompositeRuntime.transition(w,owner,GlazingTypes.step90(w.getBlockState(root)),p).outcome()==Outcome.COMMITTED,"airborne cardinal editor turn "+turn);
                t.assertTrue(w.getBlockState(root).isOf(block)&&w.getBlockState(root).get(CompositeRootBlock.ROTATION)==(turn*2)%8,"rotation keeps the artistic type and skips all45 poses");
                for(var mount:ThinWindowRootBlock.Mount.values())t.assertTrue(CompositeRuntime.transition(w,owner,w.getBlockState(root).with(ThinWindowRootBlock.MOUNT,mount),p).outcome()==Outcome.COMMITTED,"mode changes need no remaining surface "+mount);
            }
            t.assertTrue(owner.equals(((CompositeBlockEntity)w.getBlockEntity(root)).resident()),"airborne transformations retain owner UUID");t.complete();
        }finally{clear(w,root);p.discard();}
    }
    private static void aim(PlayerEntity p,BlockPos root){p.setPosition(root.getX()+.5,root.getY()+2,root.getZ()+.5);p.setYaw(0);p.setHeadYaw(0);p.setPitch(90);}
    private static ActionResult place(PlayerEntity p,ItemStack item,BlockPos support,Direction face){p.setStackInHand(Hand.MAIN_HAND,item);return item.getItem().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(face.getVector()).multiply(.5)),face,support,false)));}
    private static void clear(ServerWorld w,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-5,-3,-5),root.add(5,7,5)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(w);}
}
