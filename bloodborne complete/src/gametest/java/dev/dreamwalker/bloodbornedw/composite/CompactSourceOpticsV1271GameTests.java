package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.world.BlockView;

/** Native rendering/light flags must retain the original carrier's partial
 * shape, even when its v15 artwork and mod physical collider are full cubes. */
public final class CompactSourceOpticsV1271GameTests implements FabricGameTest {
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState property(BlockState state,Property property,String value){return state.with(property,(Comparable)property.parse(value).orElseThrow());}
    private static BlockState decode(String text){
        int bracket=text.indexOf('[');var id=new Identifier(bracket<0?text:text.substring(0,bracket));
        if(!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Missing native optical audit target "+text);
        var block=Registries.BLOCK.get(id);var state=block.getDefaultState();
        if(bracket>=0)for(String entry:text.substring(bracket+1,text.length()-1).split(",")){
            int equals=entry.indexOf('=');var p=block.getStateManager().getProperty(entry.substring(0,equals));if(p==null)throw new IllegalArgumentException("Unknown native audit property "+text);state=property(state,p,entry.substring(equals+1));
        }
        return state;
    }
    private static JsonObject flags(BlockState state,BlockView world,BlockPos pos){
        var row=new JsonObject();
        row.addProperty("opaque",state.isOpaque());row.addProperty("fullCube",state.isFullCube(world,pos));row.addProperty("opaqueFullCube",state.isOpaqueFullCube(world,pos));
        row.addProperty("ambientOcclusion",state.getAmbientOcclusionLightLevel(world,pos));row.addProperty("opacity",state.getOpacity(world,pos));row.addProperty("luminance",state.getLuminance());
        row.addProperty("fluid",ImportedSourceLightingV127.fluidDescription(state.getFluidState()));row.addProperty("blockVision",state.shouldBlockVision(world,pos));row.addProperty("suffocation",state.shouldSuffocate(world,pos));row.addProperty("solid",state.isSolidBlock(world,pos));
        row.addProperty("sidedTransparency",state.hasSidedTransparency());row.addProperty("transparent",state.isTransparent(world,pos));
        var boxes=new JsonArray();for(var box:state.getCullingShape(world,pos).getBoundingBoxes()){var b=new JsonArray();for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})b.add(value);boxes.add(b);}row.add("cullingBoxes",boxes);
        row.addProperty("renderingSeedAtOriginalXYZ",state.getRenderingSeed(pos));return row;
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v1271_compact_optics",tickLimit=200)
    public void allReachablePreparedCompactNativeFlagsAndLightSamplingAreAudited(TestContext t) throws Exception {
        JsonObject input;
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/bloodborne_dw/v1271-compact-native-flag-cases.json"),StandardCharsets.UTF_8)){input=JsonParser.parseReader(reader).getAsJsonObject();}
        var proof=new JsonObject();var results=new JsonArray();var differences=new JsonArray();var counts=new TreeMap<String,Long>();int targets=0,decodeFailures=0;
        Set<String> critical=Set.of("fullCube","opaqueFullCube","ambientOcclusion","opacity","luminance","fluid");Set<Integer> criticalGroups=new HashSet<>();long criticalOccurrences=0;
        for(var raw:input.getAsJsonArray("groups")){
            var group=raw.getAsJsonObject();int caseId=group.get("caseId").getAsInt();var sample=group.getAsJsonArray("samples").get(0).getAsJsonObject().getAsJsonArray("position");var pos=new BlockPos(sample.get(0).getAsInt(),sample.get(1).getAsInt(),sample.get(2).getAsInt());
            var source=decode(group.get("sourceState").getAsString());var sourceFlags=flags(source,t.getWorld(),pos);Set<String> groupDifferences=new HashSet<>();
            for(var rawTarget:group.getAsJsonArray("targets")){
                var targetInput=rawTarget.getAsJsonObject();var row=new JsonObject();row.addProperty("caseId",caseId);row.add("sourceState",group.get("sourceState"));row.add("sourceFlags",sourceFlags);row.add("targetState",targetInput.get("state"));row.add("decisionIds",targetInput.get("decisionIds"));row.add("originalGroupOccurrences",group.get("originalOccurrences"));
                try{
                    var nativeTarget=decode(targetInput.get("state").getAsString());
                    boolean exactSourceShape=Registries.BLOCK.getId(nativeTarget.getBlock()).toString().equals("bloodborne_dw:source_snow_height12")
                            &&nativeTarget.get(CompositeRootBlock.VARIANT)+16*nativeTarget.get(ImportedArtworkBlock.ART_BANK)>=4;
                    var targetFlags=flags(nativeTarget,t.getWorld(),pos);row.add("targetFlags",targetFlags);var changed=new JsonArray();
                    for(String key:sourceFlags.keySet())if(!sourceFlags.get(key).equals(targetFlags.get(key))){changed.add(key);groupDifferences.add(key);if(critical.contains(key)||(exactSourceShape&&key.equals("cullingBoxes")))criticalGroups.add(caseId);}
                    row.add("differentFields",changed);row.addProperty("status",changed.isEmpty()?"EXACT_NATIVE_FLAGS":"NATIVE_FLAG_DIFFERENCES_RECORDED");if(!changed.isEmpty())differences.add(row);targets++;
                }catch(Exception failure){row.addProperty("status","FAIL_NATIVE_TARGET_DECODE");row.addProperty("error",failure.toString());decodeFailures++;}
                results.add(row);
            }
            for(String key:groupDifferences)counts.merge(key,group.get("originalOccurrences").getAsLong(),Long::sum);
            if(criticalGroups.contains(caseId))criticalOccurrences+=group.get("originalOccurrences").getAsLong();
        }
        proof.addProperty("status",decodeFailures==0&&criticalGroups.isEmpty()?"PASS_ALL_REACHABLE_COMPACT_NATIVE_AO_FULLCUBE_LIGHT_FLAGS_WITH_REPORTED_NONCRITICAL_SHAPE_DIFFERENCES":"FAIL_REACHABLE_COMPACT_NATIVE_LIGHT_FLAGS");
        proof.add("input",input.deepCopy());proof.getAsJsonObject("input").remove("groups");proof.addProperty("targetCases",targets);proof.addProperty("nativeDecodeFailures",decodeFailures);proof.addProperty("criticalDifferenceGroups",criticalGroups.size());proof.addProperty("criticalDifferenceOccurrences",criticalOccurrences);proof.add("differenceOccurrencesByField",new Gson().toJsonTree(counts));proof.add("differences",differences);proof.add("results",results);
        proof.addProperty("scope","Every1810 original positive compact source case plus previously-kept fillers; exact current or planned target dictionary. Occurrences are exact original groups, counted once per differing field. Source rendering seed sampled at original XYZ; no blocks or source world modified. Native baked-model headers/vertex/UV/RGBA parity require the separate client proof. Collider changes are intentionally outside optical equality.");
        Files.writeString(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("v1271-compact-native-flag-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        t.assertTrue(input.get("originalReachableCompactGroups").getAsInt()==1810&&targets==input.get("uniqueCurrentOrPlannedTargetCases").getAsInt()&&decodeFailures==0,"Every reachable compact optical target decodes through actual native registries; see proof");
        t.assertTrue(criticalGroups.isEmpty(),"Every reachable compact native AO/full-cube/emission/opacity/fluid value and appended optical source shape at its actual prepared yaw is exact; see proof");t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v1271_compact_optics",tickLimit=100)
    public void everyAppendedCompactSourceCarrierRetainsNativeLightFlagsAtEachCardinalYaw(TestContext t) throws Exception {
        var block=(ImportedCompactBlock)CompositeArchitecture.kindBlock("source_snow_height12");
        JsonObject row;
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/bloodborne_dw/imported-compact-v127.json"),StandardCharsets.UTF_8)){
            row=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("types").asList().stream().map(JsonElement::getAsJsonObject).filter(r->r.get("registryId").getAsString().equals(block.spec.id.toString())).findFirst().orElseThrow();
        }
        var optics=row.getAsJsonObject("nativeSourceOptics");
        t.assertTrue(optics.size()==24&&block.spec.variants.size()==28,"Only the 24 proven carriers were appended after the original four artwork poses");
        var pos=BlockPos.ORIGIN;var world=t.getWorld();
        for(var entry:optics.entrySet()){
            int pose=Integer.parseInt(entry.getKey());var metadata=entry.getValue().getAsJsonObject();
            var original=ImportedSourceLightingV127.source(metadata.get("sourceState").getAsString());int reference=metadata.get("referenceRotation").getAsInt();
            for(int yaw=0;yaw<8;yaw+=2){
                var source=original.rotate(switch(Math.floorMod(yaw-reference,8)/2){case 1->BlockRotation.CLOCKWISE_90;case 2->BlockRotation.CLOCKWISE_180;case 3->BlockRotation.COUNTERCLOCKWISE_90;default->BlockRotation.NONE;});
                var target=block.getDefaultState().with(ImportedCompactBlock.COMPACT,true).with(CompositeRootBlock.VARIANT,pose%16).with(ImportedArtworkBlock.ART_BANK,pose/16).with(CompositeRootBlock.ROTATION,yaw);
                t.assertTrue(block.getRenderType(target)==BlockRenderType.MODEL&&block.createBlockEntity(pos,target)==null,"Appended optical states stay ordinary native chunk meshes, without block entities");
                t.assertTrue(target.getCollisionShape(world,pos).getBoundingBoxes().equals(net.minecraft.util.shape.VoxelShapes.fullCube().getBoundingBoxes()),"The existing full artwork collider is preserved");
                t.assertTrue(target.getCullingShape(world,pos).getBoundingBoxes().equals(source.getCullingShape(world,pos).getBoundingBoxes()),"Exact source culling shape and orientation "+pose+"/"+yaw);
                t.assertTrue(target.isFullCube(world,pos)==source.isFullCube(world,pos)&&target.isOpaqueFullCube(world,pos)==source.isOpaqueFullCube(world,pos),"Native full-cube lighting flags "+pose+"/"+yaw);
                t.assertTrue(target.getAmbientOcclusionLightLevel(world,pos)==source.getAmbientOcclusionLightLevel(world,pos),"Native source AO "+pose+"/"+yaw);
                t.assertTrue(target.getOpacity(world,pos)==source.getOpacity(world,pos)&&target.getLuminance()==source.getLuminance()&&target.getFluidState().equals(source.getFluidState()),"Native emission, opacity and fluid "+pose+"/"+yaw);
                t.assertTrue(target.shouldBlockVision(world,pos)==source.shouldBlockVision(world,pos)&&target.shouldSuffocate(world,pos)==source.shouldSuffocate(world,pos)&&target.isSolidBlock(world,pos)==source.isSolidBlock(world,pos),"Native face sampling predicates "+pose+"/"+yaw);
                t.assertTrue(target.hasSidedTransparency()==source.hasSidedTransparency()&&target.isTransparent(world,pos)==source.isTransparent(world,pos),"Native partial light propagation flags, including constructor-time state cache "+pose+"/"+yaw);
                var picked=block.getPickStack(world,pos,target).getSubNbt("BlockStateTag");
                t.assertTrue(picked!=null&&picked.getString("variant").equals("2")&&picked.getString("art_bank").equals("0"),"Picking an imported optical state returns its existing installation artwork");
            }
        }
        var old=block.getDefaultState().with(ImportedCompactBlock.COMPACT,true).with(CompositeRootBlock.VARIANT,3);
        t.assertTrue(old.isFullCube(world,pos)&&old.isOpaqueFullCube(world,pos)&&old.getAmbientOcclusionLightLevel(world,pos)==.2F&&!old.hasSidedTransparency(),"Existing saved pose3 retains its exact old optics; only appended conversion states opt in");
        t.complete();
    }
}
