package dev.dreamwalker.bloodbornedw.v127qa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Explicit small isolated function fixture, without catalogue/gallery authoring. */
public final class NativeV127Fixture implements ModInitializer {
    private boolean enabled;
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try{Path marker=FabricLoader.getInstance().getGameDir().resolve("client-capture-input.json");if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))return;var input=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();if(!input.has("v127NativeFunctions")||!input.get("v127NativeFunctions").getAsBoolean())return;if(!input.get("freshTestWorldOnly").getAsBoolean())throw new IllegalStateException("Fresh test world marker required");
                Path root=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();if(!root.toString().replace('\\','/').contains("/build/"))throw new IllegalStateException("V127 QA is restricted to project-build test worlds");enabled=true;
                boolean reopen=input.has("v127ReopenOnly")&&input.get("v127ReopenOnly").getAsBoolean();if(reopen){Path evidence=root.resolve("v127-qa-fixture.json");if(!Files.isRegularFile(evidence))throw new IllegalStateException("Reopen requires existing isolated QA world");var prior=JsonParser.parseString(Files.readString(evidence)).getAsJsonObject();if(!prior.get("productionJarSha256").getAsString().equals(input.get("productionJarSha256").getAsString()))throw new IllegalStateException("Saved fixture production JAR SHA mismatch");return;}
                if(Files.exists(root.resolve("v127-qa-fixture.json")))return;
                var world=server.getOverworld();for(int x=-4;x<=50;x++)for(int z=-8;z<=8;z++)world.getChunk(x>>4,z>>4);
                for(int x:new int[]{0,6,12,20,30,32,38,44})if(!world.getBlockState(new BlockPos(x,64,0)).isAir())throw new IllegalStateException("QA fixture target is not empty: "+x);
                for(int x=-4;x<=50;x++)for(int z=-8;z<=8;z++)world.setBlockState(new BlockPos(x,63,z),Blocks.SMOOTH_STONE.getDefaultState());
                world.setBlockState(new BlockPos(32,64,0),Blocks.BARRIER.getDefaultState());world.setTimeOfDay(6000);world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
                String compactId="";int compactVariant=-1;
                outer:for(var block:CompositeArchitecture.blocks())if(block instanceof ImportedCompactBlock)for(int pose=0;pose<block.spec.variants.size()&&pose<16;pose++){var state=block.getDefaultState().with(CompositeRootBlock.VARIANT,pose).with(ImportedCompactBlock.COMPACT,true);if(ImportedCompactBlock.compact(state)){world.setBlockState(new BlockPos(44,64,0),state);compactId=block.spec.id.toString();compactVariant=pose;break outer;}}
                var report=new JsonObject();report.addProperty("status","PASS");report.addProperty("purpose","ISOLATED_V127_FUNCTION_TEST_NOT_GALLERY");report.addProperty("productionJarSha256",input.get("productionJarSha256").getAsString());report.addProperty("compactRegistryId",compactId);report.addProperty("compactVariant",compactVariant);Files.writeString(root.resolve("v127-qa-fixture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            }catch(Exception failure){throw new IllegalStateException("V127 isolated fixture preparation failed",failure);}
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{if(!enabled)return;handler.player.changeGameMode(GameMode.CREATIVE);handler.player.getAbilities().allowModifyWorld=true;server.getPlayerManager().addToOperators(handler.player.getGameProfile());handler.player.requestTeleport(.5,64,-4);});
    }
}
