package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import org.slf4j.LoggerFactory;

/** Native source light and fluid values for NEW saved logical poses only.
 * Dynamic bounds preserve runtime opacity dispatch. State construction reads
 * no unfinished owner StateManager; old V12 settings and optics stay exact. */
public final class ImportedSourceLightingV127 {
    private record Optics(int[] light,int[] opacity,FluidState[] fluid,BlockState[] renderCarrier){}
    private record Values(int emission,int opacity,FluidState fluid,BlockState renderCarrier){}
    private record Loaded(Map<Identifier,Optics> optics,Set<Identifier> emitting,JsonArray conflicts,JsonArray opacityConflicts,JsonArray fluidConflicts,int groups,int sources){}
    private record Selector(Property<Integer> variant,Property<Integer> bank,Property<Boolean> open,Property<Boolean> wet){}
    // Bound by the finite mod block registrations, never world coordinates.
    private static final Map<Block,Selector> SELECTORS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Loaded TABLES=load();
    @SuppressWarnings({"unchecked","rawtypes"})private static BlockState property(BlockState state,Property<?> property,String name){
        var value=property.parse(name);if(value.isEmpty())throw new IllegalArgumentException("Invalid source light property "+property.getName()+"="+name);
        return state.with((Property)property,(Comparable)value.get());
    }
    static BlockState source(String text){
        int start=text.indexOf('[');var id=new Identifier(start<0?text:text.substring(0,start));
        if(!id.getNamespace().equals("minecraft")||!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Unavailable vanilla optics source: "+text);
        var block=Registries.BLOCK.get(id);var state=block.getDefaultState();
        if(start>=0){if(!text.endsWith("]"))throw new IllegalArgumentException("Malformed source optics state: "+text);
            String tail=text.substring(start+1,text.length()-1);if(!tail.isEmpty())for(String part:tail.split(",")){
                int equals=part.indexOf('=');if(equals<1)throw new IllegalArgumentException("Malformed source optics property: "+part);
                var property=block.getStateManager().getProperty(part.substring(0,equals));if(property==null)throw new IllegalArgumentException("Unknown source optics property: "+part);
                state=property(state,property,part.substring(equals+1));
            }
        }
        return state;
    }
    public static String fluidDescription(FluidState fluid){return Registries.FLUID.getId(fluid.getFluid())+"[level="+fluid.getLevel()+",still="+fluid.isStill()+"]";}
    private static void conflict(JsonArray issues,String id,int pose,boolean opened,boolean wet,String kind,Map<?,JsonArray> actual){
        if(actual.size()<=1)return;
        var issue=new JsonObject();issue.addProperty("registryId",id);issue.addProperty("variant",pose);issue.addProperty("open",opened);issue.addProperty("waterlogged",wet);
        var levels=new JsonObject();actual.forEach((level,rows)->levels.add(level.toString(),rows));issue.add(kind,levels);
        issue.addProperty("policy","UNRESOLVED_NATIVE_SOURCE_CONFLICT_PRESERVES_PRIOR_OPTICS_UNTIL_EXPLICIT_SAVED_STATE_SPLIT");issues.add(issue);
    }
    private static int[] array(int length){var result=new int[length];Arrays.fill(result,-1);return result;}
    private static Loaded load(){
        var input=ImportedSourceLightingV127.class.getResourceAsStream("/bloodborne_dw/imported-source-lighting-v127.json");
        if(input==null)return new Loaded(Map.of(),Set.of(),new JsonArray(),new JsonArray(),new JsonArray(),0,0);
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var doc=JsonParser.parseReader(reader).getAsJsonObject();if(doc.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Source optics schema");
            Map<Identifier,Map<Integer,Values>> values=new LinkedHashMap<>();JsonArray conflicts=new JsonArray(),opacityConflicts=new JsonArray(),fluidConflicts=new JsonArray();int groups=0,sources=0;
            for(var raw:doc.getAsJsonArray("objects")){
                var row=raw.getAsJsonObject();String id=row.get("registryId").getAsString();int pose=row.get("variant").getAsInt();boolean opened=row.get("open").getAsBoolean();
                boolean wet=row.has("stateProperties")&&row.getAsJsonObject("stateProperties").has("waterlogged")&&Boolean.parseBoolean(row.getAsJsonObject("stateProperties").get("waterlogged").getAsString());
                if(row.has("stateProperties"))for(String key:row.getAsJsonObject("stateProperties").keySet())if(!key.equals("waterlogged"))throw new IllegalArgumentException("Unreviewed optics state property: "+key);
                if(!id.startsWith("bloodborne_dw:source_")||pose<0||pose>479)throw new IllegalArgumentException("Original/invalid source optics target: "+id);
                Map<Integer,JsonArray> emitted=new TreeMap<>(),opaque=new TreeMap<>();Map<String,JsonArray> fluids=new TreeMap<>();Map<String,FluidState> states=new HashMap<>();BlockState carrier=null;boolean sameRender=true;
                for(var source:row.getAsJsonArray("sources")){
                    var entry=source.getAsJsonObject();BlockState state=source(entry.get("sourceState").getAsString());int light=state.getLuminance(),opacity=state.getOpacity(EmptyBlockView.INSTANCE,BlockPos.ORIGIN);
                    if(carrier==null)carrier=state;else if(!sameRenderCarrier(carrier,state))sameRender=false;
                    var fluid=state.getFluidState();String fluidName=fluidDescription(fluid);states.put(fluidName,fluid);
                    emitted.computeIfAbsent(light,ignored->new JsonArray()).add(entry);opaque.computeIfAbsent(opacity,ignored->new JsonArray()).add(entry);fluids.computeIfAbsent(fluidName,ignored->new JsonArray()).add(entry);sources++;
                }
                if(emitted.isEmpty())throw new IllegalArgumentException("Empty source optics group: "+id+"/"+pose);
                conflict(conflicts,id,pose,opened,wet,"nativeSourceLuminances",emitted);conflict(opacityConflicts,id,pose,opened,wet,"nativeSourceOpacities",opaque);conflict(fluidConflicts,id,pose,opened,wet,"nativeSourceFluids",fluids);
                var optics=new Values(emitted.size()==1?emitted.keySet().iterator().next():-1,opaque.size()==1?opaque.keySet().iterator().next():-1,fluids.size()==1?states.get(fluids.keySet().iterator().next()):null,sameRender?carrier:null);
                var poses=values.computeIfAbsent(new Identifier(id),ignored->new TreeMap<>());if(poses.put(pose*4+(opened?2:0)+(wet?1:0),optics)!=null)throw new IllegalArgumentException("Repeated source optics state: "+id+"/"+pose+"/"+wet);groups++;
            }
            Map<Identifier,Optics> optics=new LinkedHashMap<>();Set<Identifier> emitting=new LinkedHashSet<>();
            for(var entry:values.entrySet()){
                int length=(entry.getValue().keySet().stream().mapToInt(key->key/4).max().orElse(0)+1)*4;
                int[] light=array(length),opacity=array(length);FluidState[] fluid=new FluidState[length];BlockState[] renderCarrier=new BlockState[length];
                entry.getValue().forEach((key,value)->{
                    light[key]=value.emission;opacity[key]=value.opacity;fluid[key]=value.fluid;
                    renderCarrier[key]=value.renderCarrier;
                    if(value.emission>0)emitting.add(entry.getKey());
                });
                optics.put(entry.getKey(),new Optics(light,opacity,fluid,renderCarrier));
            }
            if(!conflicts.isEmpty()||!opacityConflicts.isEmpty()||!fluidConflicts.isEmpty())LoggerFactory.getLogger(ImportedSourceLightingV127.class).warn("NEW source optics require explicit saved states: emission={}, opacity={}, fluid={}; values are never guessed",conflicts.size(),opacityConflicts.size(),fluidConflicts.size());
            return new Loaded(Map.copyOf(optics),Set.copyOf(emitting),conflicts,opacityConflicts,fluidConflicts,groups,sources);
        }catch(Exception failure){throw new IllegalStateException("Cannot read exact NEW source optics metadata",failure);}
    }
    private static Identifier id(CompositeSpec spec){return spec!=null&&spec.id.getNamespace().equals("bloodborne_dw")&&spec.id.getPath().startsWith("source_")?spec.id:null;}
    @SuppressWarnings("unchecked")private static Selector selector(BlockState state){
        return SELECTORS.computeIfAbsent(state.getBlock(),ignored->{Property<Integer> variant=null,bank=null;Property<Boolean> open=null,wet=null;
            for(var property:state.getEntries().keySet())switch(property.getName()){
                case "variant"->variant=(Property<Integer>)property;case "art_bank"->bank=(Property<Integer>)property;case "open"->open=(Property<Boolean>)property;case "waterlogged"->wet=(Property<Boolean>)property;default->{}
            }
            return new Selector(variant,bank,open,wet);
        });
    }
    private static int key(BlockState state){
        var s=selector(state);int variant=s.variant==null?0:state.get(s.variant),bank=s.bank==null?0:state.get(s.bank);boolean open=s.open!=null&&state.get(s.open),wet=s.wet!=null&&state.get(s.wet);
        return (variant+16*bank)*4+(open?2:0)+(wet?1:0);
    }
    private static int value(int[] values,int key,int fallback){
        if(key>=values.length)return fallback;if(values[key]>=0)return values[key];if(values[key^2]>=0)return values[key^2];int dry=key&~1;
        if(values[dry]>=0)return values[dry];return values[dry^2]>=0?values[dry^2]:fallback;
    }
    public static AbstractBlock.Settings settings(CompositeSpec spec,AbstractBlock.Settings settings){
        Identifier id=id(spec);if(id==null||!TABLES.emitting.contains(id))return settings;
        return settings.luminance(state->{var optics=TABLES.optics.get(id);return value(optics.light,key(state),0);});
    }
    public static int opacity(CompositeSpec spec,BlockState state,int fallback){
        Identifier id=id(spec);if(id==null)return fallback;var optics=TABLES.optics.get(id);return optics==null?fallback:value(optics.opacity,key(state),fallback);
    }
    private static boolean sameRenderCarrier(BlockState a,BlockState b){
        var world=EmptyBlockView.INSTANCE;var pos=BlockPos.ORIGIN;
        return a.getLuminance()==b.getLuminance()&&a.isFullCube(world,pos)==b.isFullCube(world,pos)
            &&a.isOpaqueFullCube(world,pos)==b.isOpaqueFullCube(world,pos)
            &&a.getAmbientOcclusionLightLevel(world,pos)==b.getAmbientOcclusionLightLevel(world,pos);
    }
    /** Native carrier semantics decide partial-face neighbour sampling. A NEW
     * non-cube root must not turn a source cube's inset face into an inside-root
     * light sample. Conflicting source semantics never select an arbitrary cube.
     */
    public static BlockState renderCarrier(CompositeSpec spec,BlockState state){
        Identifier id=id(spec);if(id==null)return state;var optics=TABLES.optics.get(id);if(optics==null)return state;int key=key(state);
        if(key<optics.renderCarrier.length){var source=optics.renderCarrier[key];if(source!=null)return source;source=optics.renderCarrier[key^2];if(source!=null)return source;int dry=key&~1;source=optics.renderCarrier[dry];if(source!=null)return source;source=optics.renderCarrier[dry^2];if(source!=null)return source;}
        return state;
    }
    public static FluidState fluid(CompositeSpec spec,BlockState state,FluidState fallback){
        Identifier id=id(spec);if(id==null)return fallback;var optics=TABLES.optics.get(id);if(optics==null)return fallback;int key=key(state);if(key>=optics.fluid.length)return fallback;
        if(optics.fluid[key]!=null)return optics.fluid[key];if(optics.fluid[key^2]!=null)return optics.fluid[key^2];int dry=key&~1;
        if(optics.fluid[dry]!=null)return optics.fluid[dry];return optics.fluid[dry^2]!=null?optics.fluid[dry^2]:fallback;
    }
    public static JsonObject diagnostics(){
        var result=new JsonObject();result.addProperty("nativeSourcePoseGroups",TABLES.groups);result.addProperty("nativeSourceStateReferences",TABLES.sources);result.addProperty("emittingNewRegistryTypes",TABLES.emitting.size());
        result.addProperty("conflictGroups",TABLES.conflicts.size());result.add("conflicts",TABLES.conflicts.deepCopy());result.addProperty("opacityConflictGroups",TABLES.opacityConflicts.size());result.add("opacityConflicts",TABLES.opacityConflicts.deepCopy());result.addProperty("fluidConflictGroups",TABLES.fluidConflicts.size());result.add("fluidConflicts",TABLES.fluidConflicts.deepCopy());return result;
    }
    private ImportedSourceLightingV127(){}
}
