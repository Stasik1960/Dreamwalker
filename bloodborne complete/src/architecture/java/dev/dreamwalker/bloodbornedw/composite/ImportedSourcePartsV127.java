package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;

/** Incomplete original source fragments are saved compatibility states, not
 * additional installation items or choices in ordinary builder art cycling. */
public final class ImportedSourcePartsV127 {
    private record Key(String registry,int pose){}
    private static final Map<Key,Integer> PARTS=load();
    private ImportedSourcePartsV127(){}
    private static Map<Key,Integer> load(){var input=ImportedSourcePartsV127.class.getResourceAsStream("/bloodborne_dw/imported-source-parts-v127.json");if(input==null)return Map.of();try(var r=new InputStreamReader(input,StandardCharsets.UTF_8)){Map<Key,Integer> result=new HashMap<>();for(var raw:JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("parts")){var row=raw.getAsJsonObject();String id=row.get("registryId").getAsString();if(!id.startsWith("bloodborne_dw:source_"))throw new IllegalArgumentException("Original V12 source parts cannot be altered");result.put(new Key(id,row.get("variant").getAsInt()),row.get("defaultInstallationPose").getAsInt());}return Map.copyOf(result);}catch(IOException e){throw new IllegalStateException(e);}}
    public static boolean part(CompositeRootBlock block,int pose){return PARTS.containsKey(new Key(block.spec.id.toString(),pose));}
    public static boolean part(BlockState state){return state.getBlock() instanceof CompositeRootBlock block&&part(block,CompositeSpec.poseVariant(state));}
    public static BlockState installation(BlockState state){if(!(state.getBlock() instanceof CompositeRootBlock block))return state;Integer pose=PARTS.get(new Key(block.spec.id.toString(),CompositeSpec.poseVariant(state)));if(pose==null)return state;BlockState normal=state.with(CompositeRootBlock.VARIANT,pose%16).with(CompositeRootBlock.OPEN,false);var bank=ImportedArtworkBlock.bankProperty(normal);return bank==null?normal:normal.with(bank,pose/16);}
}
