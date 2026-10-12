package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.ImportedSourceLightingV127;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

/** Actual source/target engine state values, independent of static texture
 * interpretation. Original world/ZIP and test-world blocks are not modified. */
public final class SourceLightingV127GameTests implements FabricGameTest {
    private static JsonObject fixture(){
        var stream=SourceLightingV127GameTests.class.getResourceAsStream("/bloodborne_dw/v127-source-lighting-decisions.json");
        if(stream==null)throw new IllegalStateException("Missing complete source lighting decision fixture");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(Exception failure){throw new IllegalStateException("Invalid source lighting fixture",failure);}
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState property(BlockState state,Property<?> property,String value){
        var parsed=property.parse(value);if(parsed.isEmpty())throw new IllegalArgumentException("Invalid native property "+property.getName()+"="+value);
        return state.with((Property)property,(Comparable)parsed.get());
    }
    /** Decode complete state text solely through the actual local registry and
     * its native Property.parse API. Unknown keys/registrations are diagnosed. */
    private static BlockState decode(String text){
        int bracket=text.indexOf('[');String name=bracket<0?text:text.substring(0,bracket);var id=new Identifier(name);
        if(!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Source/target registry unavailable: "+id);
        var block=Registries.BLOCK.get(id);var state=block.getDefaultState();
        if(bracket>=0){if(!text.endsWith("]"))throw new IllegalArgumentException("Incomplete native state syntax: "+text);
            String tail=text.substring(bracket+1,text.length()-1);
            if(!tail.isEmpty())for(String item:tail.split(",")){int equals=item.indexOf('=');if(equals<1)throw new IllegalArgumentException("Invalid property expression: "+item);
                String key=item.substring(0,equals);var property=block.getStateManager().getProperty(key);if(property==null)throw new IllegalArgumentException("Native state has no property "+key+": "+text);
                state=property(state,property,item.substring(equals+1));}
        }
        return state;
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v127_source_lighting",tickLimit=6000)
    public void everyReachableSourceDecisionAuditsActualEmissionOpacityAndHeight(TestContext t){new Audit(t).step();}
    private static final class Audit {
        final TestContext t;final JsonObject fixture=fixture();final JsonArray cases=fixture.getAsJsonArray("cases"),results=new JsonArray();
        final BlockPos probe;int index,decodeFailures,newEmissionDifferences,protectedEmissionDifferences,opacityDifferences,heightConflicts;
        long occurrences,newEmissionDifferenceOccurrences,opacityDifferenceOccurrences;
        Audit(TestContext t){this.t=t;probe=t.getAbsolutePos(new BlockPos(2,2,2));}
        void footprint(JsonObject input,JsonObject row,BlockState target,String key)throws Exception{
            var payload=StringNbtReader.parse(input.getAsJsonObject("payload").toString());
            var instance=CompositeRuntime.instance(t.getWorld(),probe,target,UUID.nameUUIDFromBytes(("source-lighting-audit:"+key).getBytes(StandardCharsets.UTF_8)),payload);
            int minimum=instance.cells().keySet().stream().mapToInt(cell->cell.y()).min().orElse(0),maximum=instance.cells().keySet().stream().mapToInt(cell->cell.y()).max().orElse(0);
            row.addProperty("occupiedCellCount",instance.cells().size());row.addProperty("minimumOccupiedYOffset",minimum);row.addProperty("maximumOccupiedYOffset",maximum);
            int minimumAbsolute=input.get("anchorYMinimum").getAsInt()+minimum,maximumAbsolute=input.get("anchorYMaximum").getAsInt()+maximum;
            boolean outside=minimumAbsolute<t.getWorld().getBottomY()||maximumAbsolute>=t.getWorld().getTopY();
            row.addProperty("minimumAbsoluteOccupiedY",minimumAbsolute);row.addProperty("maximumAbsoluteOccupiedY",maximumAbsolute);row.addProperty("outsideNativeOverworldBuildHeight",outside);if(outside)heightConflicts++;
        }
        void step(){
            int budget=100;
            while(budget-->0&&index<cases.size()){
                var input=cases.get(index++).getAsJsonObject();var row=new JsonObject();int caseId=input.get("caseId").getAsInt();long count=input.get("occurrences").getAsLong();String action=input.get("action").getAsString();
                row.addProperty("caseId",caseId);row.addProperty("action",action);row.addProperty("occurrences",count);row.add("sourceState",input.get("sourceState"));row.add("targetState",input.get("targetState"));row.add("samples",input.get("samples"));occurrences+=count;
                try{
                    var source=decode(input.get("sourceState").getAsString());var target=decode(input.get("targetState").getAsString());
                    int sourceEmission=source.getLuminance(),targetEmission=target.getLuminance();int sourceOpacity=source.getOpacity(t.getWorld(),probe),targetOpacity=target.getOpacity(t.getWorld(),probe);
                    row.addProperty("sourceLuminance",sourceEmission);row.addProperty("targetLuminance",targetEmission);row.addProperty("sourceOpacity",sourceOpacity);row.addProperty("targetOpacity",targetOpacity);
                    row.addProperty("emissionEqual",sourceEmission==targetEmission);row.addProperty("opacityEqual",sourceOpacity==targetOpacity);row.add("parityPolicy",input.get("parityPolicy"));
                    if(sourceOpacity!=targetOpacity){opacityDifferences++;opacityDifferenceOccurrences+=count;}
                    if((action.equals("OWNER")||action.equals("COMPACT"))&&sourceEmission!=targetEmission){
                        if(Registries.BLOCK.getId(target.getBlock()).toString().startsWith("bloodborne_dw:source_")){newEmissionDifferences++;newEmissionDifferenceOccurrences+=count;}
                        else protectedEmissionDifferences++;
                    }
                    if(action.equals("OWNER"))footprint(input,row,target,Integer.toString(caseId));
                    row.addProperty("nativeStatus","PASS_DECODE_AND_ENGINE_READ");
                }catch(Exception failure){
                    row.addProperty("nativeStatus",action.equals("REMOVE")?"AUTHORIZED_REMOVAL_SOURCE_REGISTRY_UNAVAILABLE":"FAIL_NATIVE_DECISION_DECODE");row.addProperty("error",failure.toString());if(!action.equals("REMOVE"))decodeFailures++;
                }
                results.add(row);
            }
            if(index<cases.size())t.runAtTick(t.getTick()+1,this::step);else finish();
        }
        void finish(){
            JsonArray wholeHeights=new JsonArray();int wholeIndex=0;
            for(var raw:fixture.getAsJsonArray("wholeOwnerHeightCases")){
                var input=raw.getAsJsonObject();var row=input.deepCopy();
                try{footprint(input,row,decode(input.get("targetState").getAsString()),"whole:"+(wholeIndex++));row.addProperty("nativeStatus","PASS_NATIVE_WHOLE_OWNER_OCCUPANCY");}
                catch(Exception failure){row.addProperty("nativeStatus","FAIL_NATIVE_WHOLE_OWNER_OCCUPANCY");row.addProperty("error",failure.toString());decodeFailures++;}
                wholeHeights.add(row);
            }
            boolean originalGuard=true;
            for(var block:CompositeArchitecture.blocks())if(!block.spec.id.getPath().startsWith("source_")){var settings=net.minecraft.block.AbstractBlock.Settings.create();originalGuard&=ImportedSourceLightingV127.settings(block.spec,settings)==settings;}
            var proof=new JsonObject();proof.addProperty("status",decodeFailures==0&&newEmissionDifferences==0&&heightConflicts==0&&originalGuard?"PASS_NATIVE_SOURCE_EMISSION_AND_OWNER_HEIGHT_WITH_REPORTED_OPACITY_NUANCES":"FAIL_NATIVE_SOURCE_EMISSION_OR_OWNER_HEIGHT");
            proof.addProperty("uniqueDecisionCases",cases.size());proof.addProperty("exactSourceCellOccurrences",occurrences);proof.addProperty("nativeDecodeFailures",decodeFailures);
            proof.addProperty("newEmissionDifferenceCases",newEmissionDifferences);proof.addProperty("newEmissionDifferenceOccurrences",newEmissionDifferenceOccurrences);proof.addProperty("protectedV12EmissionDifferenceCases",protectedEmissionDifferences);
            proof.addProperty("opacityDifferenceCases",opacityDifferences);proof.addProperty("opacityDifferenceOccurrences",opacityDifferenceOccurrences);proof.addProperty("potentialOwnerBuildHeightConflictCases",heightConflicts);
            proof.addProperty("nativeOverworldBottomY",t.getWorld().getBottomY());proof.addProperty("nativeOverworldTopYExclusive",t.getWorld().getTopY());proof.add("sourceHashes",fixture.get("sourceHashes"));proof.add("coordinateRunFile",fixture.get("coordinateRunFile"));proof.add("coordinateRunSha256",fixture.get("coordinateRunSha256"));proof.add("cases",results);
            proof.add("wholeOwnerHeightCases",wholeHeights);proof.add("wholeOwnerOccurrences",fixture.get("wholeOwnerOccurrences"));
            proof.add("newEmissionMetadata",ImportedSourceLightingV127.diagnostics());
            proof.addProperty("originalV12SettingsIdentityGuard",originalGuard);
            proof.addProperty("scope","Actual native BlockState.getLuminance/getOpacity in the same isolated test context. No original map or test-world blocks modified. Exact source coordinates join through caseId lossless runs; opacity differences and approved removals are reported separately. Height findings use occupied-cell extrema, not model approximations.");
            try{Files.writeString(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-source-lighting-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception e){throw new IllegalStateException(e);}
            t.assertTrue(cases.size()>0&&occurrences==fixture.get("allPaletteCells").getAsLong(),"Every reachable full-plan source cell occurrence is audited exactly once");
            t.assertTrue(decodeFailures==0,"All kept/converted native decisions decode through actual registries; see v127-source-lighting-native-proof.json");
            t.assertTrue(originalGuard,"Every original V12 root keeps the exact original Settings object through the new-only lighting guard");
            t.assertTrue(heightConflicts==0,"Every source owner occupied cell remains within native build height; see v127-source-lighting-native-proof.json");
            t.assertTrue(newEmissionDifferences==0,"Every NEW converted source pose preserves actual source emission; see v127-source-lighting-native-proof.json");t.complete();
        }
    }
}
