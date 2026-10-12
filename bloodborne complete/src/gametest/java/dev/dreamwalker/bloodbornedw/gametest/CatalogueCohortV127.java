package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Frozen V12.6 names/registries and explicit approved V12.7 set deltas. This
 * fixture is independent of all live catalogue/retirement/alias resources. */
public final class CatalogueCohortV127 {
    private static final JsonObject DOC=load();
    private static JsonObject load(){
        var stream=CatalogueCohortV127.class.getResourceAsStream("/bloodborne_dw/v127-catalogue-expectations.json");
        if(stream==null)throw new IllegalStateException("Missing frozen V12.6/V12.7 catalogue expectations");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            var doc=JsonParser.parseReader(reader).getAsJsonObject();
            if(doc.get("schemaVersion").getAsInt()!=1||!doc.get("baselineCommit").getAsString().equals("0c985dc28e4a38900cce3055ddb5658a71ed95e5"))throw new IllegalStateException("Unknown catalogue fixture baseline");
            return doc;
        }catch(Exception failure){throw new IllegalStateException("Invalid frozen catalogue fixture",failure);}
    }
    public static Set<String> strings(String field){Set<String> values=new TreeSet<>();for(var raw:DOC.getAsJsonArray(field))if(!values.add(raw.getAsString()))throw new IllegalStateException("Repeated frozen registry: "+field);return Set.copyOf(values);}
    public static Map<String,String> numbers(String field){Map<String,String> result=new TreeMap<>();for(var entry:DOC.getAsJsonObject(field).entrySet())result.put(entry.getKey(),entry.getValue().getAsString());return Map.copyOf(result);}
    public static Set<String> registered(){return strings("frozenV126SourceRegistrations");}
    public static Set<String> working(){return strings("frozenV126WorkingSourceTypes");}
    public static Set<String> newSources(){return strings("approvedV127AdditionalSourceTypes");}
    public static Set<String> hiddenClones(){return strings("approvedV127HiddenCloneTypes");}
    public static Set<String> expectedWorking(){Set<String> result=new TreeSet<>(working());result.removeAll(hiddenClones());result.addAll(newSources());return Set.copyOf(result);}
    public static Map<String,String> oldNumbers(){return numbers("frozenV126ReservedNumbers");}
    public static Map<String,String> newNumbers(){return numbers("approvedV127AdditionalReservedNumbers");}
    private CatalogueCohortV127(){}
}
