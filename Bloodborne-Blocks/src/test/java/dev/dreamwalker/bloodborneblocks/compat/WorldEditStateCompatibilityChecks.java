package dev.dreamwalker.bloodborneblocks.compat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure Java regression check for the lazy WorldEdit state lookup. */
public final class WorldEditStateCompatibilityChecks {
 private WorldEditStateCompatibilityChecks() {}

 public static void main(String[] args) {
  Map<Object,Object> north=Map.of("facing","north","variant",0);
  Map<Object,Object> south=Map.of("facing","south","variant",0);
  Map<Object,Object> northOne=Map.of("facing","north","variant",1);
  Object northZeroState=new Object();
  Object southZeroState=new Object();
  Object northOneState=new Object();
  Map<Map<?,?>,Object> states=new HashMap<>();
  states.put(north,northZeroState);
  states.put(south,southZeroState);
  states.put(northOne,northOneState);
  assertSame(southZeroState,WorldEditStateCompatibility.with(north,states,"facing","south",northZeroState));
  assertSame(northOneState,WorldEditStateCompatibility.with(north,states,"variant",1,northZeroState));
  assertSame(northZeroState,WorldEditStateCompatibility.with(north,states,"facing","north",northZeroState));
  assertSame(northZeroState,WorldEditStateCompatibility.with(north,states,"missing",1,northZeroState));
  assertSame(northZeroState,WorldEditStateCompatibility.with(north,states,"variant",9,northZeroState));
  assertEquals(Map.of("facing","north","variant",0),north);

  Map<Map<?,?>,Object> wideStates=new HashMap<>(8_192);
  Object wideTarget=new Object();
  for(int variant=0;variant<4_096;variant++) {
   Map<Object,Object> state=new LinkedHashMap<>();
   state.put("variant",variant);
   wideStates.put(state,variant==4_095?wideTarget:variant);
  }
  if(!WorldEditStateCompatibility.isWide(wideStates))throw new AssertionError("Wide state domain must stay lazy");
  if(wideStates.size()!=4_096)throw new AssertionError("Lazy lookup must retain the original O(S) state map");
  assertSame(wideTarget,WorldEditStateCompatibility.with(Map.of("variant",0),wideStates,"variant",4_095,0));
  assertSame(southZeroState,ArchitectureStateTransitions.with(north,states,"facing","south","owner",northZeroState,false));
  assertSame(northZeroState,ArchitectureStateTransitions.with(north,states,"missing",1,"owner",northZeroState,true));
  assertSame(northZeroState,ArchitectureStateTransitions.with(north,states,"facing",new String("north"),"owner",northZeroState,false));
  rejects(()->ArchitectureStateTransitions.with(north,states,"missing",1,"owner",northZeroState,false));
  rejects(()->ArchitectureStateTransitions.with(north,states,"variant",9,"owner",northZeroState,false));
 }

 private static void assertSame(Object expected,Object actual) {
  if(expected!=actual)throw new AssertionError("Expected identical state instance");
 }

 private static void assertEquals(Object expected,Object actual) {
  if(!java.util.Objects.equals(expected,actual))throw new AssertionError("Unexpected value change");
 }

 private static void rejects(Runnable action){
  try{action.run();throw new AssertionError("Invalid native transition accepted");}
  catch(IllegalArgumentException expected){}
 }
}
