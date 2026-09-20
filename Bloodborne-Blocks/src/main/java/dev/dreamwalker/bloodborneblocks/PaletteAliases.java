package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Explicit, offline-verified replacements. Registry IDs are retained for old saves. */
final class PaletteAliases {
 private static final class Data {Set<String> removed=Set.of();Map<String,Alias> aliases=Map.of();}
 private static final class Alias {String target;Map<String,Target> states;}
 private static final class Target {String state;int[] offset;}
 record Replacement(BlockState state,BlockPos offset) {}
 private static final Data DATA=load();
 private static final Set<String> LOGICAL_HIDDEN=loadLogicalHidden();
 private PaletteAliases() {}
 private static Data load(){
  var stream=PaletteAliases.class.getResourceAsStream("/bloodborne_blocks/aliases.json");
  if(stream==null)throw new IllegalStateException("Missing palette migration table");
  try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return new Gson().fromJson(reader,Data.class);}
  catch(java.io.IOException e){throw new IllegalStateException("Cannot read palette migration table",e);}
 }
 static boolean removed(String id){return DATA.removed.contains(id);}
 static boolean hidden(String id){return removed(id)||DATA.aliases.containsKey(id)||LOGICAL_HIDDEN.contains(id);}
 private static Set<String> loadLogicalHidden(){
  var stream=PaletteAliases.class.getResourceAsStream("/bloodborne_blocks/logical/hidden-items.json");
  if(stream==null)return Set.of();
  try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){String[] ids=new Gson().fromJson(reader,String[].class);return ids==null?Set.of():Set.of(ids);}
  catch(java.io.IOException|RuntimeException e){throw new IllegalStateException("Cannot read logical hidden items",e);}
 }
 static ArchitectureBlock canonical(ArchitectureBlock block){Alias a=DATA.aliases.get(block.definition.id);return a==null?block:BloodborneBlocks.BLOCKS.get(a.target);}
 static Replacement replacement(BlockState state){
  if(!(state.getBlock() instanceof ArchitectureBlock block))return new Replacement(state,BlockPos.ORIGIN);
  Alias alias=DATA.aliases.get(block.definition.id);if(alias==null)return new Replacement(state,BlockPos.ORIGIN);
  Target target=alias.states.get(BloodborneBlocks.key(state));
  if(target==null)throw new IllegalStateException("Missing palette state migration: "+state);
  BlockState result=BloodborneBlocks.BLOCKS.get(alias.target).getDefaultState();
  if(!target.state.isEmpty())for(String pair:target.state.split(",")){
   String[] p=pair.split("=",2);result=BloodborneBlocks.set(result,result.getBlock().getStateManager().getProperty(p[0]),p[1]);
  }
  return new Replacement(result,new BlockPos(target.offset[0],target.offset[1],target.offset[2]));
 }
}
