package dev.dreamwalker.bloodbornedw.diagnostics;

import java.util.*;

/** Retained-window quantiles are never presented as whole-session quantiles. */
final class DiagnosticBuffers {
    private DiagnosticBuffers(){}
    static final class Ring<T> {
        final int capacity; final ArrayDeque<T> rows=new ArrayDeque<>(); long total,dropped;
        Ring(int capacity){this.capacity=capacity;}
        synchronized void add(T value){total++;if(rows.size()==capacity){rows.removeFirst();dropped++;}rows.addLast(value);}
        synchronized List<T> copy(){return List.copyOf(rows);}
        synchronized Map<String,Object> counts(){return Map.of("allCount",total,"retained",rows.size(),"dropped",dropped,"capacity",capacity);}
    }
    static final class Durations {
        final Ring<Long> values; long count,sum,max,over50ms;
        Durations(int capacity){values=new Ring<>(capacity);}
        synchronized void add(long ns){if(ns<0)return;count++;sum+=ns;max=Math.max(max,ns);if(ns>50_000_000L)over50ms++;values.add(ns);}
        synchronized Map<String,Object> report(){
            List<Long> sorted=new ArrayList<>(values.copy());Collections.sort(sorted);
            Map<String,Object> result=new LinkedHashMap<>(values.counts());result.put("unit","ns");result.put("allSumNs",sum);
            result.put("status",count==0?"NOT_MEASURED_NO_SAMPLES":"MEASURED");result.put("allMeanNs",count==0?"NOT_MEASURED":(double)sum/count);result.put("allMaxNs",count==0?"NOT_MEASURED":max);result.put("allCountOver50ms",over50ms);
            result.put("quantileScope","RETAINED_WINDOW_ONLY");result.put("p95Ns",sorted.isEmpty()?"NOT_MEASURED":quantile(sorted,.95));result.put("p99Ns",sorted.isEmpty()?"NOT_MEASURED":quantile(sorted,.99));return result;
        }
        private static long quantile(List<Long> sorted,double p){return sorted.isEmpty()?0:sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*p)-1));}
    }
    static String shortText(String value,int limit){if(value==null)return "";return value.length()<=limit?value:value.substring(0,limit)+"…";}
    static Map<String,Object> safeMap(Map<String,?> input){Object safe=safe(input,0);return safe instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();}
    static Object safe(Object value,int depth){
        if(value==null)return null;if(value instanceof Boolean)return value;
        if(value instanceof Number n)return Double.isFinite(n.doubleValue())?n:"NON_FINITE";
        if(value instanceof CharSequence||value instanceof UUID||value instanceof Enum<?>)return shortText(value.toString(),512);
        if(depth>=3)return "TRUNCATED_DEPTH";
        if(value instanceof Map<?,?> map){Map<String,Object> out=new LinkedHashMap<>();int n=0;for(var e:map.entrySet()){if(n++==32){out.put("_truncated",true);break;}out.put(shortText(String.valueOf(e.getKey()),96),safe(e.getValue(),depth+1));}return out;}
        if(value instanceof Collection<?> values){List<Object> out=new ArrayList<>();int n=0;for(var v:values){if(n++==32){out.add("TRUNCATED_ITEMS");break;}out.add(safe(v,depth+1));}return out;}
        // NBT, entities and arbitrary toString() payloads are intentionally excluded.
        return "NOT_CAPTURED:"+value.getClass().getSimpleName();
    }
}
