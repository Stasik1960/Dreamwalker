package dev.dreamwalker.bloodbornedw.remainingqa;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Captures actual production renderer vertex RGB without changing the world. */
final class RemainingTransferTintQa {
    static JsonObject audit(MinecraftClient client)throws Exception {
        java.awt.image.BufferedImage palette;
        try(var input=client.getResourceManager().getResourceOrThrow(new Identifier("bloodborne_dw","textures/remaining/colormap/grass.png")).getInputStream()){palette=ImageIO.read(input);}
        if(palette==null||palette.getWidth()!=256||palette.getHeight()!=256)throw new IllegalStateException("Exact source grass palette unavailable");
        BlockPos root=client.player.getBlockPos();
        int world=client.world.calculateColor(root,(biome,x,z)->{
            int base=biome.getEffects().getGrassColor().orElseGet(()->{
                var weather=net.minecraft.world.biome.Biome.NETWORK_CODEC.encodeStart(JsonOps.INSTANCE,biome).result().orElseThrow().getAsJsonObject();
                double temperature=Math.max(0,Math.min(1,weather.get("temperature").getAsDouble())),humidity=Math.max(0,Math.min(1,weather.get("downfall").getAsDouble()));
                return sample(palette,temperature,humidity);
            });
            return biome.getEffects().getGrassColorModifier().getModifiedGrassColor(x,z,base);
        })&0xffffff;
        int item=palette.getRGB(127,127)&0xffffff;
        var result=new JsonObject();result.addProperty("scope","Actual production renderParts vertex RGB against independently decoded exact authored palette and native biome climate/blend/modifier. Untinted faces and source/old controls remain white.");result.addProperty("expectedWorldGrassRgb",world);result.addProperty("expectedItemGrassRgb",item);
        JsonArray cases=new JsonArray();boolean pass=true;var json=new Gson();
        for(String path:List.of("source_grass_block","source_bricks_grass_block_0","source_potted_fern","source_bricks","prototype_double_door")){
            var block=CompositeArchitecture.blocks().stream().filter(value->value.spec.id.getPath().equals(path)).findFirst().orElseThrow();
            boolean tinted=Set.of("source_grass_block","source_bricks_grass_block_0","source_potted_fern").contains(path);
            Set<Integer> actualWorld=capture(block,root),actualItem=capture(block,null);
            Set<Integer> expectedWorld=tinted?new HashSet<>(List.of(0xffffff,world)):Set.of(0xffffff),expectedItem=tinted?new HashSet<>(List.of(0xffffff,item)):Set.of(0xffffff);
            boolean valid=actualWorld.equals(expectedWorld)&&actualItem.equals(expectedItem);pass&=valid;
            var row=new JsonObject();row.addProperty("id",block.spec.id.toString());row.add("worldVertexRgb",json.toJsonTree(actualWorld));row.add("itemVertexRgb",json.toJsonTree(actualItem));row.addProperty("status",valid?"PASS":"FAIL");cases.add(row);
        }
        result.add("cases",cases);result.addProperty("status",pass?"PASS":"FAIL");return result;
    }
    private static int sample(java.awt.image.BufferedImage palette,double temperature,double humidity){int horizontal=(int)(255*(1-temperature)),vertical=(int)(255*(1-temperature*humidity));return palette.getRGB(horizontal,vertical)&0xffffff;}
    private static Set<Integer> capture(CompositeRootBlock block,BlockPos root)throws Exception {
        Set<Integer> colors=new TreeSet<>();
        VertexConsumer vertices=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
            if(args!=null&&args.length==4&&method.getParameterTypes()[0]==int.class){colors.add(((Number)args[0]).intValue()<<16|((Number)args[1]).intValue()<<8|((Number)args[2]).intValue());}
            if(args!=null&&args.length==4&&method.getParameterTypes()[0]==float.class){colors.add(Math.round(((Number)args[0]).floatValue()*255)<<16|Math.round(((Number)args[1]).floatValue()*255)<<8|Math.round(((Number)args[2]).floatValue()*255));}
            if(method.getReturnType()==VertexConsumer.class)return proxy;
            return null;
        });
        VertexConsumerProvider provider=layer->vertices;
        var method=CompositeClient.class.getDeclaredMethod("renderParts",CompositeRootBlock.class,BlockState.class,MatrixStack.class,VertexConsumerProvider.class,int.class,int.class,String.class,BlockPos.class);method.setAccessible(true);
        method.invoke(null,block,block.getDefaultState(),new MatrixStack(),provider,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null,root);
        return colors;
    }
    private RemainingTransferTintQa(){}
}
