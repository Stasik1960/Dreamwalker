package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class BbAlternateV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v127_tool_profiles")
    public void everyOfferedModBlockHasBaseAndAltWhileRpVisualPreservesPhysics(TestContext c){
        int blocks=0,objects=0;
        for(var stack:UnifiedCreativeCatalogue.canonicalItems()){
            if(stack.getItem() instanceof BlockItem item&&ToolTarget.modRegistry(Registries.ITEM.getId(item))){var base=item.getBlock().getDefaultState();c.assertTrue(BbAlternateService.profile(base,"base")!=null&&BbAlternateService.profile(base,"alt")!=null,"Every offered native BB block has both profile states: "+Registries.ITEM.getId(item));blocks++;}
            var id=Registries.ITEM.getId(stack.getItem());if(id.getNamespace().equals("bloodborne_rp")&&id.getPath().endsWith("_placer")){
                String path=id.getPath().substring(0,id.getPath().length()-7);var object=ObjectRegistry.TYPES.get(path).create(c.getWorld());c.assertTrue(object!=null,"Actual registered RP placement type: "+path);var physical=object.activePhysicalBoxes();var selected=object.selectionBoxes();NbtCompound saved=new NbtCompound();object.writeNbt(saved);c.assertTrue(!saved.contains("BbAltVisual"),"BASE keeps previous optional NBT shape: "+path);object.setAlternateVisual(true);c.assertTrue(object.activePhysicalBoxes().equals(physical)&&object.selectionBoxes().equals(selected),"RP ALT changes visual only: "+path);NbtCompound alt=new NbtCompound();object.writeNbt(alt);c.assertTrue(alt.getBoolean("BbAltVisual"),"RP ALT persists exact BYTE: "+path);var restored=ObjectRegistry.TYPES.get(path).create(c.getWorld());restored.readNbt(alt);c.assertTrue(restored.alternateVisual()&&restored.activePhysicalBoxes().equals(physical),"RP ALT reload keeps geometry: "+path);object.discard();restored.discard();objects++;
            }
        }
        c.assertTrue(blocks>500&&objects>10,"Coverage includes the full offered native and RP building catalogue");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v127_tool_profiles")
    public void foreignTargetsCannotMoveReceiveCommandsOrChangeProfile(TestContext c){
        var world=c.getWorld();var player=BbToolGameTests.player(c);var pos=c.getAbsolutePos(new BlockPos(3,3,3));world.setBlockState(pos,Blocks.STONE.getDefaultState());var forged=new ToolTarget(ToolTarget.Kind.BLOCK,world.getRegistryKey().getValue().toString(),null,pos.asLong(),"minecraft:stone");
        c.assertTrue(ToolTarget.block(world,pos,player)==null&&!forged.valid(world.getServer()),"Vanilla block cannot become a BB target even through forged target metadata");c.assertTrue(!BbToolService.move(player,forged,1)&&!BbAlternateService.change(player,forged,"alt"),"BB actions reject foreign registry");
        var armor=EntityType.ARMOR_STAND.create(world);armor.setPosition(Vec3d.ofCenter(pos.up()));world.spawnEntity(armor);c.assertTrue(ToolTarget.entity(armor)==null,"Vanilla armour stand is never a BB target");armor.discard();player.discard();world.removeBlock(pos,false);c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v127_tool_profiles")
    public void actualRadiusCommandChangesNewOwnersAndLeavesOtherTypesAndVanilla(TestContext c){
        var world=c.getWorld();var player=BbToolGameTests.player(c);var root=c.getAbsolutePos(new BlockPos(3,22,3));load(world,root);var block=smallNew();var state=block.getDefaultState();var near=root.east(3);var far=root.east(16);var owners=new ArrayList<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner>();
        try{for(var pos:List.of(root,near,far)){var id=UUID.randomUUID();c.assertTrue(CompositeRuntime.place(world,pos,state,id,null).outcome()==TransactionCore.Outcome.COMMITTED,"Actual NEW owner placement");owners.add(VerticalMount.owner(world,pos));}var stone=root.south(3);world.setBlockState(stone,Blocks.STONE.getDefaultState());player.setPosition(Vec3d.ofCenter(root));var tool=BbToolService.ITEM.getDefaultStack();tool.getOrCreateNbt().putInt("BBMode",BbToolItem.Mode.SPECIAL.ordinal());player.setStackInHand(Hand.MAIN_HAND,tool);
            c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2),"bb select 6")==1,"Native radius command parses");BbToolService.apply(player,ToolTarget.block(world,root,player));c.assertTrue("alt".equals(BbAlternateService.profile(world.getBlockState(root)))&&"alt".equals(BbAlternateService.profile(world.getBlockState(near)))&&"base".equals(BbAlternateService.profile(world.getBlockState(far))),"NEW same-ID radius edits are exact");c.assertTrue(world.getBlockState(stone).isOf(Blocks.STONE),"Native stone remains unchanged");world.removeBlock(stone,false);c.complete();
        }finally{for(var owner:owners)CompositeRuntime.remove(world,owner,null,false);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="v127_tool_all_job")
    public void batchedWholeMapVisitsChangeDifferentNativeTypesAndRpThenRestoreBase(TestContext c){
        var world=c.getWorld();var player=BbToolGameTests.player(c);var root=c.getAbsolutePos(new BlockPos(1024,80,1024));load(world,root);var second=root.east(32);load(world,second);var state=smallNew().getDefaultState();UUID uuid=UUID.randomUUID();NbtCompound payload=new NbtCompound();payload.putString("PreservedEvent","on_use");payload.putUuid("PreservedLink",UUID.randomUUID());
        c.assertTrue(CompositeRuntime.place(world,root,state,uuid,null,payload).outcome()==TransactionCore.Outcome.COMMITTED,"Saved-map fixture has an actual NEW native object");var owner=VerticalMount.owner(world,root);var oldNative=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.FREESTANDING,true);world.setBlockState(second,oldNative);VerticalMount.placed(world,second,oldNative);var oldOwner=VerticalMount.owner(world,second);var rp=ObjectRegistry.TYPES.get("curtainsmall").create(world);rp.setPosition(Vec3d.ofCenter(second.north(3)));world.spawnEntity(rp);var stone=root.south(3);world.setBlockState(stone,Blocks.STONE.getDefaultState());player.setPosition(Vec3d.ofCenter(root));var visits=List.of(new BbAlternateService.Visit(world,new ChunkPos(root)),new BbAlternateService.Visit(world,new ChunkPos(second)),new BbAlternateService.Visit(world,new ChunkPos(root)));
        c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2),"bb select all")==1,"Real all selector command accepts literal instead of radius");c.assertTrue(BbAlternateService.startVisits(player,"alt",visits),"Start real batched job on explicit existing native chunks");
        c.runAtTick(60,()->{
            try{var status=BbAlternateService.status(world.getServer());c.assertTrue(!status.running()&&status.changed()>=3&&status.failed()==0,"Sequential all job reaches different types and RP exactly once: "+status);c.assertTrue("alt".equals(BbAlternateService.profile(world.getBlockState(root)))&&"alt".equals(BbAlternateService.profile(world.getBlockState(second)))&&rp.alternateVisual(),"Whole-map ALT reaches original native, NEW native and placed RP");c.assertTrue(VerticalMount.owner(world,root).instanceId().equals(uuid)&&((CompositeBlockEntity)world.getBlockEntity(root)).payload().getString("PreservedEvent").equals("on_use")&&world.getBlockState(stone).isOf(Blocks.STONE),"Profiles retain UUID, events, typed instance data and native stone");c.assertTrue(BbAlternateService.startVisits(player,"base",visits),"Explicit BASE starts next job");
                c.runAtTick(120,()->{try{var next=BbAlternateService.status(world.getServer());c.assertTrue(!next.running()&&next.failed()==0&&"base".equals(BbAlternateService.profile(world.getBlockState(root)))&&"base".equals(BbAlternateService.profile(world.getBlockState(second)))&&!rp.alternateVisual(),"Batched BASE reverses every profile");c.complete();}finally{BbAlternateService.cancel(world.getServer());CompositeRuntime.remove(world,owner,null,false);CompositeRuntime.remove(world,oldOwner,null,false);rp.removeObject();world.removeBlock(stone,false);player.discard();}});
            }catch(Throwable failure){BbAlternateService.cancel(world.getServer());CompositeRuntime.remove(world,owner,null,false);CompositeRuntime.remove(world,oldOwner,null,false);rp.removeObject();world.removeBlock(stone,false);player.discard();throw failure;}
        });
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v127_tool_profiles")
    public void savedRegionIndexReadsExistingNegativeCoordinateChunksOnly(TestContext c)throws Exception{
        Path directory=Files.createTempDirectory(c.getWorld().getServer().getSavePath(WorldSavePath.ROOT),"bb-profile-header-test-");Path file=directory.resolve("r.-2.3.mca");byte[] header=new byte[8192];header[2]=2;header[3]=1;int last=1023*4;header[last+2]=7;header[last+3]=1;header[4+2]=1;header[4+3]=1;Files.write(file,header);
        try{var actual=BbAlternateService.savedChunks(file);c.assertTrue(actual.equals(Set.of(ChunkPos.toLong(-64,96),ChunkPos.toLong(-33,127))),"Header reader handles negative region coordinates and excludes absent/corrupt sector1 entries");c.complete();}finally{Files.delete(file);Files.delete(directory);}
    }
    private static CompositeRootBlock smallNew(){return CompositeArchitecture.blocks().stream().filter(block->block.spec.simplePhysicalCollision&&!block.spec.requiredSupport&&!block.spec.openable&&!(block instanceof ThinWindowRootBlock)&&!(block instanceof ImportedStairBlock)&&!(block instanceof ImportedLadderBlock)&&!ImportedAssemblyV126.participant(block.spec.id.toString())&&!ImportedTypeAliases.merged(block.spec.id.toString())&&block.spec.footprint(block.getDefaultState(),0).size()<=3).findFirst().orElseThrow();}
    private static void load(net.minecraft.server.world.ServerWorld world,BlockPos root){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunk(root.add(x*16,0,z*16));}
}
