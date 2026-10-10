package dev.dreamwalker.bloodbornedw.debug;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Frozen TEMP type numbers, never stored in or confused with instance NBT/UUID. */
public final class DebugCatalogue {
    public record Entry(String temporaryId,String name,String kind,Identifier registryId,List<Identifier> aliases) {}
    private static final Map<Identifier,Entry> ALIASES=new LinkedHashMap<>();
    private static final List<Entry> ENTRIES=new ArrayList<>();
    private record StateAlias(Identifier registry,String property,String value,Identifier canonical) {}
    private static final List<StateAlias> STATE_ALIASES=new ArrayList<>();
    private static boolean loaded;
    private DebugCatalogue(){}
    public static synchronized void initialize(){
        if(loaded)return;
        var stream=DebugCatalogue.class.getResourceAsStream("/bloodborne_dw/debug_catalogue.json");
        if(stream==null)throw new IllegalStateException("Missing frozen TEMP catalogue");
        Set<String> numbers=new HashSet<>();
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("schemaVersion").getAsInt()!=1)throw new IllegalStateException("Unsupported TEMP catalogue schema");
            for(JsonElement value:root.getAsJsonArray("entries")){
                JsonObject row=value.getAsJsonObject();String number=row.get("temporaryId").getAsString();
                if(!number.matches("[0-9]{5}")||!numbers.add(number)||!row.get("finalId").isJsonNull())throw new IllegalStateException("Invalid/repeated TEMP catalogue number");
                List<Identifier> aliases=new ArrayList<>();for(JsonElement alias:row.getAsJsonArray("aliases"))aliases.add(new Identifier(alias.getAsString()));
                Entry entry=new Entry(number,row.get("name").getAsString(),row.get("kind").getAsString(),new Identifier(row.get("registryId").getAsString()),List.copyOf(aliases));
                for(Identifier alias:aliases)if(ALIASES.putIfAbsent(alias,entry)!=null)throw new IllegalStateException("Repeated TEMP alias "+alias);
                ENTRIES.add(entry);
            }
            // Retired furniture numbers remain reserved in ENTRIES. Their old
            // real registrations/UUIDs keep working and display canonical art.
            for(JsonElement value:root.getAsJsonArray("entries")){
                JsonObject row=value.getAsJsonObject();if(!row.has("canonicalRegistryId"))continue;
                Entry canonical=ALIASES.get(new Identifier(row.get("canonicalRegistryId").getAsString()));
                if(canonical==null)throw new IllegalStateException("Unknown canonical TEMP target");
                for(JsonElement alias:row.getAsJsonArray("aliases"))ALIASES.put(new Identifier(alias.getAsString()),canonical);
            }
            if(root.has("stateAliases"))for(JsonElement value:root.getAsJsonArray("stateAliases")){
                JsonObject row=value.getAsJsonObject();Identifier target=new Identifier(row.get("canonicalRegistryId").getAsString());
                if(!ALIASES.containsKey(target))throw new IllegalStateException("Unknown legacy art TEMP target");
                STATE_ALIASES.add(new StateAlias(new Identifier(row.get("registryId").getAsString()),row.get("property").getAsString(),row.get("value").getAsString(),target));
            }
        }catch(Exception failure){ALIASES.clear();ENTRIES.clear();STATE_ALIASES.clear();throw new IllegalStateException("Invalid frozen TEMP catalogue",failure);}
        loaded=true;
    }
    public static Entry entry(Identifier registry){initialize();return ALIASES.get(registry);}
    public static Entry entry(BlockState state){initialize();Identifier registry=Registries.BLOCK.getId(state.getBlock());
        BlockState current=dev.dreamwalker.bloodbornedw.architecture.CatalogueMigration.target(state);
        if(!current.equals(state))return entry(current);
        for(StateAlias alias:STATE_ALIASES)if(alias.registry.equals(registry))for(var property:state.getEntries().entrySet())
            if(property.getKey().getName().equals(alias.property)&&property.getValue().toString().equals(alias.value))return entry(alias.canonical);
        return entry(registry);
    }
    public static Entry entry(ItemStack stack){initialize();Identifier registry=Registries.ITEM.getId(stack.getItem());var tag=stack.getSubNbt("BlockStateTag");
        if(tag!=null)for(StateAlias alias:STATE_ALIASES)if(alias.registry.equals(registry)&&tag.getString(alias.property).equals(alias.value))return entry(alias.canonical);
        return entry(registry);
    }
    public static List<Entry> entries(){initialize();return List.copyOf(ENTRIES);}
    public static String prefix(Identifier registry){Entry entry=entry(registry);return entry==null?"TEMP UNASSIGNED":"TEMP ["+entry.temporaryId()+"]";}
    public static String prefix(BlockState state){Entry entry=entry(state);return entry==null?"TEMP UNASSIGNED":"TEMP ["+entry.temporaryId()+"]";}
    public static Text name(Identifier registry,Text plain){Entry entry=entry(registry);return entry==null?plain:plain.copy().append(" ["+entry.temporaryId()+"]");}
    public static Text itemName(ItemStack stack,Text plain){Entry entry=entry(stack);return entry==null?plain:plain.copy().append(" ["+entry.temporaryId()+"]");}
    public static void appendTooltip(Identifier registry,List<Text> tooltip){Entry entry=entry(registry);if(entry!=null)tooltip.add(Text.literal(prefix(registry)+" · временный номер типа; окончательный ID не назначен · "+registry));}
    public static void itemTooltip(ItemStack stack,List<Text> tooltip){Entry entry=entry(stack);if(entry!=null)tooltip.add(Text.literal("TEMP ["+entry.temporaryId()+"] · временный номер типа; окончательный ID не назначен · "+Registries.ITEM.getId(stack.getItem())));}
    public static int debug(ServerCommandSource source){return CatalogueDebug.inspect(source);}
}
