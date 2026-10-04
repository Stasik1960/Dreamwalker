package dev.dreamwalker.bloodborneblocks.compat;

import java.util.HashMap;
import java.util.Map;

/** One-property transitions through the state manager's existing canonical map. */
public final class ArchitectureStateTransitions {
 private ArchitectureStateTransitions() {}

 public static Object with(Map<?,?> entries,Map<? extends Map<?,?>,?> states,Object property,
   Object value,Object owner,Object current,boolean allowMissing) {
  Object previous=entries.get(property);
  if(previous==null) {
   if(allowMissing)return current;
   throw new IllegalArgumentException("Cannot set property "+property+" as it does not exist in "+owner);
  }
  if(previous.equals(value))return current;
  Map<Object,Object> changed=new HashMap<>(entries);
  changed.put(property,value);
  Object result=states.get(changed);
  if(result==null)throw new IllegalArgumentException("Cannot set property "+property+" to "+value+" on "+owner+", it is not an allowed value");
  return result;
 }
}
