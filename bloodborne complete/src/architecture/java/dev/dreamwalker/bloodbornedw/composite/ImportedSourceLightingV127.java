package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;

/** Exact source-carrier emission for NEW logical poses only. The native source
 * states are already registered when Fabric initializes our blocks. Settings
 * capture the ID, never block.spec or its unfinished StateManager. */
public final class ImportedSourceLightingV127 {
    private record Light(int[] closed,int[] opened){}
    private record Loaded(Map<String,Light> lights,JsonArray conflicts,int groups,int sources){}
    private static final Loaded TABLES=load();
    @SuppressWarnings({"unchecked","rawtypes"})private static BlockState property(BlockState state,Property<?> property,String name){
        var value=property.parse(name);if(value.isEmpty())throw new IllegalArgumentException("Invalid source light property "+property.getName()+"="+name);
        return state.with((Property)property,(Comparable)value.get());
    }
    private static BlockState source(String text){
        int start=text.indexOf('[');var id=new Identifier(start<0?text:text.substring(0,start));
        if(!id.getNamespace().equals("minecraft")||!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Unavailable vanilla emission source: "+text);
        var block=Registries.BLOCK.get(id);var state=block.getDefaultState();
        if(start>=0){if(!text.endsWith("]"))throw new IllegalArgumentException("Malformed source emission state: "+text);
            String tail=text.substring(start+1,text.length()-1);if(!tail.isEmpty())for(String part:tail.split(",")){
                int equals=part.indexOf('=');if(equals<1)throw new IllegalArgumentException("Malformed source emission property: "+part);
                var property=block.getStateManager().getProperty(part.substring(0,equals));if(property==null)throw new IllegalArgumentException("Unknown source emission property: "+part);
                state=property(state,property,part.substring(equals+1));
            }
        }
        return state;
    }
    private static Loaded load(){
        var input=ImportedSourceLightingV127.class.getResourceAsStream("/bloodborne_dw/imported-source-lighting-v127.json");
        if(input==null)return new Loaded(Map.of(),new JsonArray(),0,0);
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var doc=JsonParser.parseReader(reader).getAsJsonObject();if(doc.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Source lighting schema");
            Map<String,Map<Integer,Integer>> values=new LinkedHashMap<>();JsonArray conflicts=new JsonArray();int groups=0,sources=0;
            for(var raw:doc.getAsJsonArray("objects")){
                var row=raw.getAsJsonObject();String id=row.get("registryId").getAsString();int pose=row.get("variant").getAsInt();boolean opened=row.get("open").getAsBoolean();
                if(!id.startsWith("bloodborne_dw:source_")||pose<0||pose>479)throw new IllegalArgumentException("Original/invalid source lighting target: "+id);
                Map<Integer,JsonArray> actual=new TreeMap<>();
                for(var source:row.getAsJsonArray("sources")){var entry=source.getAsJsonObject();int emitted=source(entry.get("sourceState").getAsString()).getLuminance();actual.computeIfAbsent(emitted,ignored->new JsonArray()).add(entry);sources++;}
                if(actual.isEmpty())throw new IllegalArgumentException("Empty source emission group: "+id+"/"+pose);
                int light=actual.size()==1?actual.keySet().iterator().next():0;
                if(actual.size()!=1){var issue=new JsonObject();issue.addProperty("registryId",id);issue.addProperty("variant",pose);issue.addProperty("open",opened);var emissions=new JsonObject();actual.forEach((level,rows)->emissions.add(level.toString(),rows));issue.add("nativeSourceLuminances",emissions);issue.addProperty("policy","UNRESOLVED_SOURCE_CONFLICT_TARGET_KEEPS_PREVIOUS_ZERO_EMISSION");conflicts.add(issue);}
                var poses=values.computeIfAbsent(id,ignored->new TreeMap<>());if(poses.put(pose*2+(opened?1:0),light)!=null)throw new IllegalArgumentException("Repeated source emission state: "+id+"/"+pose);groups++;
            }
            Map<String,Light> lights=new LinkedHashMap<>();
            for(var entry:values.entrySet()){
                if(entry.getValue().values().stream().noneMatch(level->level>0))continue;
                int length=entry.getValue().keySet().stream().mapToInt(key->key/2).max().orElse(0)+1;int[] closed=new int[length],opened=new int[length];Arrays.fill(closed,-1);Arrays.fill(opened,-1);
                entry.getValue().forEach((key,level)->{if((key&1)==0)closed[key/2]=level;else opened[key/2]=level;});lights.put(entry.getKey(),new Light(closed,opened));
            }
            if(!conflicts.isEmpty())LoggerFactory.getLogger(ImportedSourceLightingV127.class).warn("{} NEW source pose emission conflicts require explicit source states; no luminance is guessed",conflicts.size());
            return new Loaded(Map.copyOf(lights),conflicts,groups,sources);
        }catch(Exception failure){throw new IllegalStateException("Cannot read exact NEW source light metadata",failure);}
    }
    public static AbstractBlock.Settings settings(CompositeSpec spec,AbstractBlock.Settings settings){
        String id=spec.id.toString();if(!id.startsWith("bloodborne_dw:source_")||!TABLES.lights.containsKey(id))return settings;
        return settings.luminance(state->emission(id,state));
    }
    private static int emission(String id,BlockState state){
        var light=TABLES.lights.get(id);if(light==null)return 0;int variant=0,bank=0;boolean open=false;
        // owner.StateManager and block.spec are not initialized yet when the
        // native AbstractBlockState constructor evaluates this lambda.
        for(var entry:state.getEntries().entrySet())switch(entry.getKey().getName()){
            case "variant"->variant=(Integer)entry.getValue();case "art_bank"->bank=(Integer)entry.getValue();case "open"->open=(Boolean)entry.getValue();default->{}
        }
        int pose=variant+16*bank;if(pose>=light.closed.length)return 0;
        int exact=open?light.opened[pose]:light.closed[pose];if(exact<0)exact=open?light.closed[pose]:light.opened[pose];return Math.max(0,exact);
    }
    public static JsonObject diagnostics(){var result=new JsonObject();result.addProperty("nativeSourcePoseGroups",TABLES.groups);result.addProperty("nativeSourceStateReferences",TABLES.sources);result.addProperty("emittingNewRegistryTypes",TABLES.lights.size());result.addProperty("conflictGroups",TABLES.conflicts.size());result.add("conflicts",TABLES.conflicts.deepCopy());return result;}
    private ImportedSourceLightingV127(){}
}
