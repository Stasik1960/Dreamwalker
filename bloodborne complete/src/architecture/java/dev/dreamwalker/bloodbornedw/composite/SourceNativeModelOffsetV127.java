package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

/** Explicit importer helper for the 64 authored objects carried by vanilla
 * flowers. Native deterministic source model offsets are read at original
 * coordinates, never reconstructed from a guessed hash or applied globally. */
public final class SourceNativeModelOffsetV127 {
    private static final Set<String> STATES=load();
    private SourceNativeModelOffsetV127(){}
    private static Set<String> load(){var stream=SourceNativeModelOffsetV127.class.getResourceAsStream("/bloodborne_dw/imported-source-model-offset-v127.json");if(stream==null)throw new IllegalStateException("Missing exact source offset policy");try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){var doc=JsonParser.parseReader(reader).getAsJsonObject();if(doc.get("schemaVersion").getAsInt()!=1||doc.get("ownerSourceCells").getAsInt()!=64)throw new IllegalArgumentException("Unexpected source model offset cohort");Set<String> values=new HashSet<>();for(var raw:doc.getAsJsonArray("sourceStates")){String value=raw.getAsString();if(!value.startsWith("minecraft:")||!values.add(value))throw new IllegalArgumentException("Invalid source offset selector");}if(doc.has("additionalSourceStates")){if(doc.get("additionalOwnerSourceCells").getAsInt()!=6||doc.getAsJsonArray("additionalSourceStates").size()!=1||!doc.getAsJsonArray("additionalSourceStates").get(0).getAsString().equals("minecraft:dandelion")||!values.add("minecraft:dandelion"))throw new IllegalArgumentException("Unexpected restored source offset cohort");}return Set.copyOf(values);}catch(Exception failure){throw new IllegalStateException("Invalid exact source offset policy",failure);}}
    public static boolean supports(String sourceState){return STATES.contains(sourceState);}
    @SuppressWarnings({"rawtypes","unchecked"})public static BlockState sourceState(String text){if(!supports(text))throw new IllegalArgumentException("Unreviewed native source model offset "+text);int bracket=text.indexOf('[');var id=new Identifier(bracket<0?text:text.substring(0,bracket));if(!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Missing native source offset block "+id);var block=Registries.BLOCK.get(id);var state=block.getDefaultState();if(bracket>=0){if(!text.endsWith("]"))throw new IllegalArgumentException("Malformed source offset state");String tail=text.substring(bracket+1,text.length()-1);if(!tail.isEmpty())for(String expression:tail.split(",")){String[] pair=expression.split("=",2);if(pair.length!=2)throw new IllegalArgumentException("Malformed source property");Property p=block.getStateManager().getProperty(pair[0]);if(p==null)throw new IllegalArgumentException("Unknown source property");state=state.with(p,(Comparable)p.parse(pair[1]).orElseThrow());}}if(!state.hasModelOffset())throw new IllegalArgumentException("Native source offset disappeared "+text);return state;}
    public static NbtCompound apply(BlockState target,NbtCompound supplied,String sourceState,BlockPos originalPosition){
        if(!Registries.BLOCK.getId(target.getBlock()).toString().startsWith("bloodborne_dw:source_")||!(target.getBlock() instanceof CompositeRootBlock)||ImportedCompactBlock.compact(target))throw new IllegalArgumentException("Source native offsets are restricted to reviewed NEW owned instances");
        var offset=sourceState(sourceState).getModelOffset(EmptyBlockView.INSTANCE,originalPosition);var local=CompositeSourceShift.local(supplied);var added=new CompositeSourceShift.Shift(offset.x,offset.y,offset.z).rotate(-target.get(CompositeRootBlock.ROTATION));
        var result=supplied.copy();var shift=new NbtList();shift.add(NbtDouble.of(local.x()+added.x()));shift.add(NbtDouble.of(local.y()+added.y()));shift.add(NbtDouble.of(local.z()+added.z()));result.put("SourceShift",shift);return result;
    }
}
