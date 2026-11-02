package dev.dreamwalker.perfqa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;

/** Render-thread-only, preallocated samples; no allocations or I/O on each frame. */
final class FrameWindow {
    private static final int LIMIT=131072;
    private static final long[] SAMPLES=new long[LIMIT];
    private static int size;
    private static long previous;
    private static boolean active,overflow;
    static void begin(){size=0;previous=0;overflow=false;active=true;}
    static void frame(){
        if(!active)return;
        long now=System.nanoTime();
        if(previous!=0){if(size<LIMIT)SAMPLES[size++]=now-previous;else overflow=true;}
        previous=now;
    }
    static JsonObject stop(){
        active=false;
        JsonObject result=new JsonObject();
        result.addProperty("samples",size);result.addProperty("overflow",overflow);
        result.addProperty("clock","System.nanoTime at RenderSystem.flipFrame HEAD; CPU-side presentation intervals, not GPU timer queries");
        JsonArray raw=new JsonArray();
        long total=0;int over50=0,over100=0,over250=0,over1000=0;
        for(int i=0;i<size;i++){
            long ns=SAMPLES[i];raw.add(ns);total+=ns;
            if(ns>50_000_000)over50++;if(ns>100_000_000)over100++;if(ns>250_000_000)over250++;if(ns>1_000_000_000)over1000++;
        }
        result.add("rawIntervalsNs",raw);result.addProperty("intervalTotalNs",total);
        result.addProperty("over50ms",over50);result.addProperty("over100ms",over100);
        result.addProperty("over250ms",over250);result.addProperty("over1000ms",over1000);
        if(size>0){
            long[] sorted=Arrays.copyOf(SAMPLES,size);Arrays.sort(sorted);
            result.addProperty("meanMs",total/1e6/size);result.addProperty("meanFps",1e9*size/total);
            result.addProperty("medianMs",percentile(sorted,.50)/1e6);result.addProperty("p95Ms",percentile(sorted,.95)/1e6);
            result.addProperty("p99Ms",percentile(sorted,.99)/1e6);result.addProperty("maxMs",sorted[size-1]/1e6);
            int worst=Math.max(1,(int)Math.ceil(size*.01));long worstTotal=0;
            for(int i=size-worst;i<size;i++)worstTotal+=sorted[i];
            result.addProperty("onePercentLowFps",1e9*worst/worstTotal);
        }
        return result;
    }
    private static double percentile(long[] values,double fraction){
        double index=(values.length-1)*fraction;int lo=(int)Math.floor(index),hi=(int)Math.ceil(index);
        return values[lo]+(values[hi]-values[lo])*(index-lo);
    }
}
