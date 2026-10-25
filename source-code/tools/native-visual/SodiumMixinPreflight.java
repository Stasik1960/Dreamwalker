package dev.dreamwalker.preflight;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import java.nio.file.*;
import java.io.*;
/** A separate QA-only entrypoint. No world, registry or renderer is started. */
public final class SodiumMixinPreflight implements PreLaunchEntrypoint {
    public void onPreLaunch(){
        Path out=Path.of(System.getProperty("dw.sodium.preflight.out"));
        String[] names={
            "me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection",
            "me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext",
            "me.jellysquid.mods.sodium.client.world.WorldSlice",
            "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext",
            "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller",
            "link.infra.indium.renderer.render.AbstractBlockRenderContext",
            "link.infra.indium.renderer.render.TerrainRenderContext"
        };
        try{
            for(String name:names)Class.forName(name,false,getClass().getClassLoader());
            Class.forName("net.irisshaders.iris.compat.sodium.impl.vertex_format.IrisModelVertexFormats",true,getClass().getClassLoader());
            Files.writeString(out,"PASS_MIXIN_CLASS_TRANSFORMS_ONLY\n"+String.join("\n",names)+"\n");System.exit(0);
        }catch(Throwable error){
            var text=new StringWriter();error.printStackTrace(new PrintWriter(text));
            try{Files.writeString(out,"FAIL\n"+text);}catch(IOException ignored){}
            System.exit(70);
        }
    }
}
