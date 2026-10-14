package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Native source compatibility must survive real engine flag dispatch and
 * binary saved palette codecs, without owners or source block callbacks. */
public final class NativeCompatibilityCarriersV1271GameTests implements FabricGameTest {
    private static Set<String> solidInvisible() throws IOException {
        try(var reader=new InputStreamReader(NativeCompatibilityCarriersV1271GameTests.class.getResourceAsStream("/bloodborne_dw/invisible-source-carriers-v1271.json"),StandardCharsets.UTF_8)){
            var doc=JsonParser.parseReader(reader).getAsJsonObject();var result=new HashSet<String>();
            for(var value:doc.getAsJsonArray("solidSourceStates"))result.add(value.getAsString());
            return result;
        }
    }
    private static List<String> invisibleNames() throws IOException {
        try(var reader=new InputStreamReader(NativeCompatibilityCarriersV1271GameTests.class.getResourceAsStream("/bloodborne_dw/invisible-source-carriers-v1271.json"),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("sourceStates").asList().stream().map(value->value.getAsString()).toList();
        }
    }
    private static List<String> fallbackTargets() throws IOException {
        try(var reader=new InputStreamReader(NativeCompatibilityCarriersV1271GameTests.class.getResourceAsStream("/bloodborne_dw/source-fallback-carriers-v1271.json"),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("targetRegistryIds").asList().stream().map(value->value.getAsString()).toList();
        }
    }
    private static void exact(TestContext t,BlockState target,BlockState source,BlockPos pos,String context){
        var world=t.getWorld();world.setBlockState(pos,target,Block.NOTIFY_LISTENERS);
        t.assertTrue(target.isOpaque()==source.isOpaque(),"Native opaque setting "+context);
        t.assertTrue(target.isFullCube(world,pos)==source.isFullCube(world,pos)&&target.isOpaqueFullCube(world,pos)==source.isOpaqueFullCube(world,pos),"Native full-cube flags "+context);
        t.assertTrue(target.hasSidedTransparency()==source.hasSidedTransparency()&&target.isTransparent(world,pos)==source.isTransparent(world,pos),"Native partial light propagation "+context);
        t.assertTrue(target.getAmbientOcclusionLightLevel(world,pos)==source.getAmbientOcclusionLightLevel(world,pos),"Native AO "+context);
        t.assertTrue(target.getOpacity(world,pos)==source.getOpacity(world,pos)&&target.getLuminance()==source.getLuminance()&&target.getFluidState().equals(source.getFluidState()),"Native opacity, emission and fluid "+context);
        var collision=target.getBlock() instanceof SourceFallbackCarrierV1271?SourceFallbackCarrierV1271.collisionOverride(target):null;
        var outline=target.getBlock() instanceof SourceFallbackCarrierV1271?SourceFallbackCarrierV1271.outlineOverride(target):null;
        t.assertTrue(target.getCollisionShape(world,pos).getBoundingBoxes().equals((collision!=null?collision:source.getCollisionShape(world,pos)).getBoundingBoxes()),"Exact preserved physical collision "+context);
        t.assertTrue(target.getOutlineShape(world,pos).getBoundingBoxes().equals((outline!=null?outline:source.getOutlineShape(world,pos)).getBoundingBoxes()),"Exact preserved physical outline "+context);
        t.assertTrue(target.getCullingShape(world,pos).getBoundingBoxes().equals(source.getCullingShape(world,pos).getBoundingBoxes()),"Exact native culling "+context);
        t.assertTrue(target.getSidesShape(world,pos).getBoundingBoxes().equals(source.getSidesShape(world,pos).getBoundingBoxes()),"Exact native side support "+context);
        t.assertTrue(target.shouldBlockVision(world,pos)==source.shouldBlockVision(world,pos)&&target.shouldSuffocate(world,pos)==source.shouldSuffocate(world,pos)&&target.isSolidBlock(world,pos)==source.isSolidBlock(world,pos),"Native light-sampling predicates "+context);
        t.assertTrue(!target.hasBlockEntity()&&world.getBlockEntity(pos)==null&&target.getBlock().asItem()==Items.AIR&&CompositeRuntime.contributions(world,pos).isEmpty(),"No compatibility item, block entity or owner membership "+context);
    }
    private static void paletteRoundTrip(TestContext t,BlockState state,String context) throws IOException {
        var palette=new NbtList();palette.add(BlockState.CODEC.encodeStart(NbtOps.INSTANCE,state).result().orElseThrow());var root=new NbtCompound();root.put("Palette",palette);
        var bytes=new ByteArrayOutputStream();try(var output=new DataOutputStream(bytes)){NbtIo.write(root,output);}
        NbtCompound loaded;try(var input=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))){loaded=NbtIo.read(input);}
        var decoded=BlockState.CODEC.parse(NbtOps.INSTANCE,loaded.getList("Palette",NbtElement.COMPOUND_TYPE).getCompound(0)).result().orElseThrow();
        t.assertTrue(decoded==state,"Native binary NBT palette retains exact registry/source/profile state identity "+context);
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="final_native_compatibility",tickLimit=100)
    public void allInvisibleSourceAssignmentsRetainNativePhysicsAndBinaryPalette(TestContext t) throws Exception {
        var names=invisibleNames();var solid=solidInvisible();var sources=InvisibleSourceCarrierV127.sourceStates();t.assertTrue(sources.size()==66&&names.size()==66&&solid.size()==33,"Stable36-state prefix plus30 native empty multipart states");
        var pos=t.getAbsolutePos(new BlockPos(3,4,3));
        try{
            for(int i=0;i<sources.size();i++){
                var block=Registries.BLOCK.get(solid.contains(names.get(i))?InvisibleSourceCarrierV127.SOLID_ID:InvisibleSourceCarrierV127.ID);
                for(var profile:CompositeRootBlock.Profile.values()){
                    var target=block.getDefaultState().with(InvisibleSourceCarrierV127.SOURCE,i).with(CompositeRootBlock.PROFILE,profile);var source=sources.get(i);String context=i+"/"+profile;
                    t.assertTrue(target.getRenderType()==BlockRenderType.INVISIBLE&&InvisibleSourceCarrierV127.source(target)==source,"Original invisible source identity "+context);
                    exact(t,target,source,pos,context);paletteRoundTrip(t,target,context);
                }
            }
            t.complete();
        }finally{t.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="final_native_compatibility",tickLimit=100)
    public void allDrawableFallbackAssignmentsRetainNativePhysicsAndBinaryPalette(TestContext t) throws Exception {
        var sources=SourceFallbackCarrierV1271.sourceStates();var targets=fallbackTargets();t.assertTrue(sources.size()==156&&targets.size()==156,"Accepted77 source prefix plus79 exact frozen optical contexts");var pos=t.getAbsolutePos(new BlockPos(3,4,3));
        try{
            for(int i=0;i<sources.size();i++){
                var source=sources.get(i);var block=Registries.BLOCK.get(new net.minecraft.util.Identifier(targets.get(i)));
                for(var profile:CompositeRootBlock.Profile.values()){
                    var target=block.getDefaultState().with(SourceFallbackCarrierV1271.SOURCE,i%128).with(SourceFallbackCarrierV1271.SOURCE_BANK,i/128).with(CompositeRootBlock.PROFILE,profile);String context=i+"/"+profile;
                    t.assertTrue(target.getRenderType()==BlockRenderType.MODEL&&SourceFallbackCarrierV1271.source(target)==source,"Native source model identity "+context);
                    t.assertTrue(target.getRenderingSeed(pos)==source.getRenderingSeed(pos),"Original native model random seed "+context);
                    exact(t,target,source,pos,context);paletteRoundTrip(t,target,context);
                }
            }
            t.complete();
        }finally{t.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="final_native_compatibility",tickLimit=100)
    public void everyBankedNativeStateTransitionAndLegacyBanklessPaletteIsCanonical(TestContext t) throws Exception {
        for(var id:List.of(SourceFallbackCarrierV1271.ID,SourceFallbackCarrierV1271.TRANSLUCENT_ID,SourceFallbackCarrierV1271.MULTIPART_ID,SourceFallbackCarrierV1271.SOLID_MULTIPART_ID)){
            var block=Registries.BLOCK.get(id);t.assertTrue(block.getStateManager().getStates().size()==512,"Finite two-bank state domain");
            for(var state:block.getStateManager().getStates()){
                t.assertTrue(SourceFallbackCarrierV1271.source(state)!=null,"Unassigned source/bank slots have safe finite native fallback");
                for(var raw:state.getProperties()){
                    net.minecraft.state.property.Property property=raw;
                    for(var value:property.getValues()){
                        var changed=state.with(property,(Comparable)value);
                        t.assertTrue(changed.getBlock()==block&&changed.get(property).equals(value),"Native dense transition retains requested property");
                    }
                }
                paletteRoundTrip(t,state,id+"/"+state);
            }
            for(int source=0;source<77;source++){
                var legacy=block.getDefaultState().with(SourceFallbackCarrierV1271.SOURCE,source);var tag=(NbtCompound)BlockState.CODEC.encodeStart(NbtOps.INSTANCE,legacy).result().orElseThrow();tag.getCompound("Properties").remove("source_bank");
                var decoded=BlockState.CODEC.parse(NbtOps.INSTANCE,tag).result().orElseThrow();t.assertTrue(decoded==legacy,"Existing saved source index defaults to bank0 when bank property is absent");
            }
        }
        t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="final_native_compatibility",tickLimit=100)
    public void transparentInternalFacesAndInvisibleNativeLadderRemainOriginal(TestContext t){
        var fallback=SourceFallbackCarrierV1271.sourceStates();int glass=-1;for(int i=0;i<fallback.size();i++)if(fallback.get(i).isOf(Blocks.LIME_STAINED_GLASS))glass=i;t.assertTrue(glass>=0,"Original lime glass source branch exists");
        var target=Registries.BLOCK.get(SourceFallbackCarrierV1271.TRANSLUCENT_ID).getDefaultState().with(SourceFallbackCarrierV1271.SOURCE,glass);var alternate=target.with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT);var source=fallback.get(glass);
        for(var direction:Direction.values()){
            t.assertTrue(target.isSideInvisible(alternate,direction)&&target.isSideInvisible(source,direction),"Same native glass hides internal faces across saved profile/source identities "+direction);
            t.assertTrue(target.isSideInvisible(Blocks.STONE.getDefaultState(),direction)==source.isSideInvisible(Blocks.STONE.getDefaultState(),direction),"Unrelated neighbor retains native glass culling "+direction);
        }
        int ladder=-1;var invisible=InvisibleSourceCarrierV127.sourceStates();for(int i=0;i<invisible.size();i++)if(invisible.get(i).isOf(Blocks.LADDER)){ladder=i;break;}t.assertTrue(ladder>=0,"Original invisible source ladder exists");
        var state=Registries.BLOCK.get(InvisibleSourceCarrierV127.ID).getDefaultState().with(InvisibleSourceCarrierV127.SOURCE,ladder);var pos=t.getAbsolutePos(new BlockPos(3,4,3));var world=t.getWorld();
        try{world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);var zombie=new ZombieEntity(world);zombie.setPosition(pos.getX()+.5,pos.getY(),pos.getZ()+.5);t.assertTrue(zombie.isClimbing(),"Actual living entity climbs invisible native source ladder");t.complete();}
        finally{world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);}
    }
}
