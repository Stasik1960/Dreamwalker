package dev.dreamwalker.renderdiag;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Opt-in counters only; no world or render object is retained. */
public final class RenderDiag implements ClientModInitializer {
    public static final boolean ENABLED = Boolean.getBoolean("dw.renderdiag.enabled");
    public static final LongAdder prepare=new LongAdder(), bake=new LongAdder(), preparedMain=new LongAdder(), preparedWorker=new LongAdder();
    public static final LongAdder signatureTotal=new LongAdder(), signatureInvalid=new LongAdder(), rootKnown=new LongAdder(), rootDependency=new LongAdder();
    public static final LongAdder invalidRoot=new LongAdder(), invalidOther=new LongAdder(), invalidUnknown=new LongAdder();
    public static final LongAdder facade=new LongAdder(), styleWorld=new LongAdder(), styleOther=new LongAdder(), updateEmpty=new LongAdder(), updateFull=new LongAdder();
    private static final Gson JSON=new Gson();
    private BufferedWriter writer;
    private long next;
    @Override public void onInitializeClient() {
        if (!ENABLED) return;
        try {
            String output=System.getProperty("dw.renderdiag.output");
            if(output==null||output.isBlank()) throw new IllegalArgumentException("dw.renderdiag.output is required");
            writer=Files.newBufferedWriter(Path.of(output), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch(Exception e) { throw new IllegalStateException("Cannot open render diagnostics output",e); }
        ClientTickEvents.END_CLIENT_TICK.register(this::sample);
    }
    private void sample(MinecraftClient client) {
        long now=System.nanoTime(); if(now<next) return; next=now+1_000_000_000L;
        JsonObject row=new JsonObject(); row.addProperty("epochMillis",System.currentTimeMillis()); row.addProperty("nanoTime",now);
        if(client.player!=null) { JsonObject p=new JsonObject(); p.addProperty("x",client.player.getX()); p.addProperty("y",client.player.getY()); p.addProperty("z",client.player.getZ()); p.addProperty("yaw",client.player.getYaw()); p.addProperty("pitch",client.player.getPitch()); row.add("player",p); }
        row.add("worldMesh",JSON.toJsonTree(CompositeWorldMesh.metrics()));
        if(client.world!=null) row.add("nativeQueues",NativeQueues.sample(client.world));
        JsonObject c=new JsonObject();
        put(c,"prepare",prepare); put(c,"bake",bake); put(c,"preparedMain",preparedMain); put(c,"preparedWorker",preparedWorker);
        put(c,"signatureTotal",signatureTotal); put(c,"signatureInvalid",signatureInvalid); put(c,"rootKnown",rootKnown); put(c,"rootDependency",rootDependency);
        put(c,"invalidRoot",invalidRoot); put(c,"invalidOther",invalidOther); put(c,"invalidUnknown",invalidUnknown);
        put(c,"facade",facade); put(c,"styleWorld",styleWorld); put(c,"styleOther",styleOther); put(c,"updateEmpty",updateEmpty); put(c,"updateFull",updateFull);
        row.add("counters",c);
        try { writer.write(JSON.toJson(row)); writer.newLine(); writer.flush(); }
        catch(Exception e) { throw new IllegalStateException("Cannot write render diagnostics",e); }
    }
    private static void put(JsonObject row,String name,LongAdder value) { row.addProperty(name,value.sum()); }
}
