package dev.dreamwalker.bloodbornedw.v127qa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.MultipartPredicateCache;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;

/** Actual client-only predicate contract. Kept separate from frame/input QA,
 * never packaged in production and never creates a world or gallery. */
public final class NativeMultipartPredicateQa implements ClientModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static MultipartModelComponent component(MultipartModelSelector selector){return new MultipartModelComponent(selector,new WeightedUnbakedModel(List.of(new ModelVariant(new Identifier("minecraft:block/stone"),AffineTransformation.identity(),false,1))));}
    @Override public void onInitializeClient(){
        Path output=null;JsonObject report=new JsonObject();
        try {
            Path directory=MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize();Path marker=directory.resolve("client-capture-input.json");
            if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))return;
            var input=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
            if(!input.has("v127NativeFunctions")||!input.get("v127NativeFunctions").getAsBoolean())return;
            require(input.get("freshTestWorldOnly").getAsBoolean(),"Isolated native QA marker required");
            output=directory.resolve("v127-native-client-predicate-proof.json");require(!Files.exists(output),"Fresh client predicate proof required");
            var paths=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();require(paths.size()==1,"One bound production artifact");
            String artifact=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(paths.get(0))));
            require(artifact.equals(input.get("productionJarSha256").getAsString()),"Exact production JAR binding");report.addProperty("productionJarSha256",artifact);
            Block first=Registries.BLOCK.get(new Identifier("bloodborne_dw:source_assembly_brick_wall")),second=Registries.BLOCK.get(new Identifier("bloodborne_dw:source_assembly_deepslate_brick_wall"));
            require(first!=Blocks.AIR&&second!=Blocks.AIR&&first!=second,"Two distinct registered owned managers");
            var a=first.getStateManager();var b=second.getStateManager();require(MultipartPredicateCache.owned(a)&&MultipartPredicateCache.owned(b),"Owned namespace exact guards");
            List<MultipartModelSelector> selectors=List.of(
                new SimpleMultipartModelSelector("variant","0|1"),
                new SimpleMultipartModelSelector("rotation","!0|2"),
                new AndMultipartModelSelector(List.of(new SimpleMultipartModelSelector("variant","0|1"),new SimpleMultipartModelSelector("profile","base"),new SimpleMultipartModelSelector("compact","true"))),
                new OrMultipartModelSelector(List.of(new SimpleMultipartModelSelector("open","true"),new SimpleMultipartModelSelector("rotation","0|2|4|6"))),
                new AndMultipartModelSelector(List.of(new SimpleMultipartModelSelector("art_bank","0|1"),new OrMultipartModelSelector(List.of(new SimpleMultipartModelSelector("variant","!0"),new SimpleMultipartModelSelector("profile","alt"))))));
            long checks=0;int identities=0;
            for(var selector:selectors){
                var model=component(selector);Predicate<BlockState> cached=model.getPredicate(a),raw=selector.getPredicate(a);require(cached==model.getPredicate(a),"Same parsed component + exact manager reuses predicate");identities++;
                for(var state:a.getStates()){require(cached.test(state)==raw.test(state),"Independent native raw selector matches every first-manager state");checks++;}
                var changed=model.getPredicate(b);require(changed!=cached&&changed==model.getPredicate(b),"Different manager cannot reuse prior predicate");identities++;
                var rawChanged=selector.getPredicate(b);for(var state:b.getStates()){require(changed.test(state)==rawChanged.test(state),"Independent native raw selector matches every changed-manager state");checks++;}
                require(model.getPredicate(a)!=cached,"One-pair cache releases/replaces the previous manager entry");
                var fresh=component(selector);require(fresh.getPredicate(a)!=cached,"New resource component has a fresh instance-lifetime cache");
            }
            var foreign=Blocks.OAK_STAIRS.getStateManager();require(!MultipartPredicateCache.owned(foreign),"Vanilla is outside owned namespace");
            var foreignSelector=new SimpleMultipartModelSelector("facing","north|south");var foreignComponent=component(foreignSelector);
            var foreignOne=foreignComponent.getPredicate(foreign);var foreignTwo=foreignComponent.getPredicate(foreign);require(foreignOne!=foreignTwo,"Vanilla predicate compilation remains uncached");
            var foreignRaw=foreignSelector.getPredicate(foreign);for(var state:foreign.getStates()){require(foreignOne.test(state)==foreignRaw.test(state),"Vanilla results preserve the raw selector");checks++;}
            report.addProperty("status","PASS_ACTUAL_CLIENT_MULTIPART_PREDICATE_CONTRACT");report.addProperty("nativeStatePredicateComparisons",checks);report.addProperty("repeatedIdentityChecks",identities);report.addProperty("andOrPipeNegation","PASS");report.addProperty("differentManagerAndReloadInstance","PASS");report.addProperty("vanillaUncached","PASS");report.addProperty("boundedEntriesPerParsedComponent",1);report.addProperty("noWorldOrPositionCache",true);report.addProperty("compiledOwnedComponentPredicates",MultipartPredicateCache.compiledOwnedComponentPredicates());
            Files.writeString(output,JSON.toJson(report)+"\n");
        }catch(Throwable failure){report.addProperty("status","FAIL_ACTUAL_CLIENT_MULTIPART_PREDICATE_CONTRACT");report.addProperty("error",failure.toString());if(output!=null)try{Files.writeString(output,JSON.toJson(report)+"\n");}catch(Exception ignored){}throw new IllegalStateException("Native client multipart predicate contract failed",failure);}
    }
}
