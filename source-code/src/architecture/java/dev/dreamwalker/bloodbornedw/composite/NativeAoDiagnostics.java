package dev.dreamwalker.bloodbornedw.composite;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
/** Optional bounded evidence of the factor passed to Indium's byte packer. */
public final class NativeAoDiagnostics {
    private static final String OUTPUT=System.getProperty("dw.native.ao.output");
    private static int count;
    private NativeAoDiagnostics(){}
    public static void observe(int color,float raw,float normalized){
        if(OUTPUT==null||raw==normalized)return;
        write(color,raw,normalized);
    }
    private static synchronized void write(int color,float raw,float normalized){
        if(count++>=128)return;
        try{Path p=Path.of(OUTPUT);Files.createDirectories(p.getParent());Files.writeString(p,new Gson().toJson(Map.of("phase",SourceLightingDiagnostics.currentPhase(),"color",Integer.toHexString(color),"rawAo",raw,"normalizedAo",normalized))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
