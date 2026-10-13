package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual original coordinates without loading any original terrain. */
public final class SourceModelOffsetV127GameTests implements FabricGameTest {
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState decode(String text){int at=text.indexOf('[');var block=Registries.BLOCK.get(new Identifier(at<0?text:text.substring(0,at)));var state=block.getDefaultState();if(at>=0)for(String part:text.substring(at+1,text.length()-1).split(",")){var pair=part.split("=",2);Property p=block.getStateManager().getProperty(pair[0]);if(p==null)throw new IllegalArgumentException("Missing target property "+part);state=state.with(p,(Comparable)p.parse(pair[1]).orElseThrow());}return state;}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_source_model_offset",tickLimit=30)
    public void all64OriginalNativeOffsetsPreserveCanonicalShiftAtEveryYaw(TestContext c)throws Exception{
        JsonObject fixture;try(var reader=new InputStreamReader(SourceModelOffsetV127GameTests.class.getResourceAsStream("/bloodborne_dw/v127-source-model-offset-cases.json"),StandardCharsets.UTF_8)){fixture=JsonParser.parseReader(reader).getAsJsonObject();}
        int cells=0,yaws=0;var results=new JsonArray();
        for(var raw:fixture.getAsJsonArray("rows")){var row=raw.getAsJsonObject();var a=row.getAsJsonArray("position");var position=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());String sourceText=row.get("sourceState").getAsString();var source=SourceNativeModelOffsetV127.sourceState(sourceText);var expected=source.getModelOffset(c.getWorld(),position);var target=decode(row.get("targetState").getAsString());var payload=StringNbtReader.parse(row.getAsJsonObject("payload").toString());var before=payload.copy();
            c.assertTrue(source.hasModelOffset()&&expected.y==0,"Every declared source offset is actual native horizontal flower offset");
            for(int yaw=0;yaw<8;yaw++){var rotated=target.with(CompositeRootBlock.ROTATION,yaw);var original=CompositeSourceShift.world(rotated,payload);var next=SourceNativeModelOffsetV127.apply(rotated,payload,sourceText,position);var actual=CompositeSourceShift.world(rotated,next);c.assertTrue(Math.abs(actual.x()-original.x()-expected.x)<1e-10&&Math.abs(actual.y()-original.y()-expected.y)<1e-10&&Math.abs(actual.z()-original.z()-expected.z)<1e-10,"Original native offset combines with exact existing canonical SourceShift: "+sourceText+" at "+position+" yaw="+yaw);c.assertTrue(payload.equals(before),"Importer leaves immutable source row payload unchanged");yaws++;}
            var proof=row.deepCopy();var vector=new JsonArray();vector.add(expected.x);vector.add(expected.y);vector.add(expected.z);proof.add("nativeSourceModelOffset",vector);results.add(proof);cells++;
        }
        boolean refused=false;try{SourceNativeModelOffsetV127.apply(CompositeArchitecture.kindBlock("prototype_roof").getDefaultState(),new net.minecraft.nbt.NbtCompound(),"minecraft:poppy",BlockPos.ORIGIN);}catch(IllegalArgumentException expected){refused=true;}
        c.assertTrue(cells==64&&yaws==512&&refused,"All64 source coordinates and eight canonical yaws proven; original V12 is excluded");var report=new JsonObject();report.addProperty("status","PASS_NATIVE_EXACT_ORIGINAL_SOURCE_MODEL_OFFSETS");report.addProperty("sourceCells",cells);report.addProperty("yawChecks",yaws);report.add("sourceHashes",fixture.get("sourceHashes"));report.add("rows",results);report.addProperty("originalWorldWritten",false);Files.writeString(c.getWorld().getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("v127-source-model-offset-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));c.complete();
    }
}
