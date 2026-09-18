package daot.verification;

import java.io.*;
import java.util.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;

public final class OfflineMixinApply {
    public static void main(String[] args) throws Exception {
        OfflineMixinService.readArchive(java.nio.file.Path.of(args[0]));
        OfflineMixinService.readArchive(java.nio.file.Path.of(args[1]));
        MixinBootstrap.init();
        var service=(OfflineMixinService)MixinService.getService();
        var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        env.setOption(MixinEnvironment.Option.DISABLE_REFMAP,false);
        var transformer=service.factory.createTransformer();
        MixinExtrasBootstrap.init();
        Set<String> targets=new TreeSet<>();int mixins=0;
        List<String> configs=List.of("dannys-aot.mixins.json","dannys-aot.client.mixins.json","player_animation_library.mixins.json");
        for(String config:configs) {
            Mixins.addConfiguration(config);
            JsonObject json;
            try(var in=new InputStreamReader(service.getResourceAsStream(config),java.nio.charset.StandardCharsets.UTF_8)) {
                json=JsonParser.parseReader(in).getAsJsonObject();
            }
            for(String section:List.of("mixins","client")) if(json.has(section)) for(JsonElement item:json.getAsJsonArray(section)) {
                String name=json.get("package").getAsString()+"."+item.getAsString();
                ClassNode node=service.getClassNode(name);
                for(AnnotationNode a:node.invisibleAnnotations) if(a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                    for(int i=0;i<a.values.size();i+=2) if(a.values.get(i).equals("value"))
                        for(Object type:(List<?>)a.values.get(i+1))targets.add(((Type)type).getClassName());
                }
                mixins++;
            }
        }
        var advance=MixinEnvironment.class.getDeclaredMethod("gotoPhase",MixinEnvironment.Phase.class);
        advance.setAccessible(true);advance.invoke(null,MixinEnvironment.Phase.DEFAULT);
        int transformed=0;
        List<String> failed=new ArrayList<>();
        for(String name:targets) {
            try(var in=service.getResourceAsStream(name.replace('.','/')+".class")) {
                byte[] source=in.readAllBytes();
                byte[] result=transformer.transformClass(env,name,source);
                if(Arrays.equals(source,result))throw new AssertionError("No mixins applied to "+name);
                transformed++;
                System.out.println("APPLIED "+name);
            } catch(Throwable failure) {
                failed.add(name+": "+failure);failure.printStackTrace();
            }
        }
        System.out.println("Offline actual Mixin application: configs="+configs.size()+" mixins="+mixins+" targets="+targets.size()+" transformed="+transformed+" failures="+failed.size());
        if(!failed.isEmpty())throw new AssertionError(String.join("\n",failed));
    }
}
