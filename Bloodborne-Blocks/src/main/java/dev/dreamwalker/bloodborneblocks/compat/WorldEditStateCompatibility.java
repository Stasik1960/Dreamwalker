package dev.dreamwalker.bloodborneblocks.compat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.lang.ref.WeakReference;

/**
 * Keeps WorldEdit's state lookup lazy for the unusually wide Bloodborne block
 * palettes. WorldEdit normally materializes every one-property transition in a
 * Guava table; the complete state map already contains those transitions.
 */
public final class WorldEditStateCompatibility {
 private static final int WIDE_PROPERTY_VALUES=64;
 private static final long WIDE_TRANSITIONS=32_768L;

 private WorldEditStateCompatibility() {}

 /** generateStateMap populates one palette at a time; retain no registry map. */
 public static final class PaletteDecision {
  private WeakReference<Map<?,?>> previous=new WeakReference<>(null);
  private boolean wide;
  public boolean isWide(Map<? extends Map<?,?>,?> stateMap) {
   if(previous.get()!=stateMap) {
    wide=WorldEditStateCompatibility.isWide(stateMap);
    previous=new WeakReference<>(stateMap);
   }
   return wide;
  }
 }

 public static boolean isWide(Map<? extends Map<?,?>,?> stateMap) {
  if(stateMap.isEmpty())return false;
  Map<Object,Set<Object>> valuesByProperty=new HashMap<>();
  for(Map<?,?> stateValues:stateMap.keySet()) {
   for(Map.Entry<?,?> entry:stateValues.entrySet()) {
    Set<Object> values=valuesByProperty.computeIfAbsent(entry.getKey(),ignored->new HashSet<>());
    values.add(entry.getValue());
   }
  }
  long valuesTotal=0;
  for(Set<Object> values:valuesByProperty.values()) {
   if(values.size()>=WIDE_PROPERTY_VALUES)return true;
   valuesTotal+=values.size();
  }
  return valuesTotal>0&&stateMap.size()>=WIDE_TRANSITIONS/valuesTotal;
 }

 /**
 * Mirrors WorldEdit's one-property state transition without allocating its
 * dense neighbour table. Invalid property/value pairs retain the current
 * state, matching the compatibility path's conservative contract.
 */
 public static Object with(Map<?,?> values,Map<? extends Map<?,?>,?> stateMap,Object property,Object value,Object current) {
  if(!values.containsKey(property))return current;
  Map<Object,Object> updated=new HashMap<>(values);
  updated.put(property,value);
  Object state=stateMap.get(updated);
  return state!=null?state:current;
 }
}
