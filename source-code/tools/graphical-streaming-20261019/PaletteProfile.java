import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
public final class PaletteProfile {
 public static void main(String[] args)throws Exception {
  Map<String,Long> types=new HashMap<>(),sites=new HashMap<>();long samples=0,bytes=0,heap=0;
  List<Object> retained=new ArrayList<>();
  try(var file=new RecordingFile(Path.of(args[0]))){while(file.hasMoreEvents()){
   var e=file.readEvent();String name=e.getEventType().getName();
   if(name.equals("jdk.GCHeapSummary"))heap=Math.max(heap,e.getLong("heapUsed"));
   if(name.equals("jdk.OldObjectSample")){var stack=JfrShapeSummary.stack(e);if(retained.size()<100)retained.add(Map.of("object",e.getValue("object").toString(),"stack",stack.stream().limit(14).toList()));}
   if(!name.equals("jdk.ObjectAllocationSample"))continue;
   var stack=JfrShapeSummary.stack(e);if(stack.stream().noneMatch(s->s.contains("LithiumHashPalette")))continue;
   long weight=e.getLong("weight");samples++;bytes+=weight;String type=e.getClass("objectClass").getName();JfrShapeSummary.count(types,type,weight);
   JfrShapeSummary.count(sites,type+"\n"+String.join("\n",stack.stream().limit(23).toList()),weight);
  }}
  Files.writeString(Path.of(args[1]),JfrShapeSummary.json(Map.of("samples",samples,"weightedBytes",bytes,"maxObservedHeap",heap,"allocatedTypes",types,"sites",JfrShapeSummary.top(sites,15),"oldObjectSamples",retained))+"\n");
 }
}
