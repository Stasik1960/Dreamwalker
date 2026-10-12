package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.composite.mixin.BbServerEntityManagerAccess;
import dev.dreamwalker.bloodbornedw.tool.mixin.BbEntityStorageTestAccess;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.ChunkDataList;

/** Real separate entity-region write followed by an asynchronous cold load.
 * No synthetic loaded RP index, block placement bypass or full original map. */
public final class BbColdAlternateV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=600,batchId="v127_tool_cold_region")
    public void savedEntityOnlyColdChunkReceivesWholeMapAlternateWithoutLosingInstanceData(TestContext context)throws Exception {
        new Proof(context).prepare();
    }
    private static final class Proof {
        final TestContext context;
        final net.minecraft.server.world.ServerWorld world;
        final ServerPlayerEntity player;
        final BlockPos pos;
        final ChunkPos chunk;
        final UUID uuid=UUID.randomUUID();
        NbtCompound original;
        boolean started;
        Proof(TestContext context){
            this.context=context;world=context.getWorld();player=BbToolGameTests.player(context);
            pos=context.getAbsolutePos(new BlockPos(8192,80,-8192));chunk=new ChunkPos(pos);
        }
        void prepare()throws Exception {
            var manager=((BbServerEntityManagerAccess)world).bloodborne_dw$profileEntityManager();
            context.assertTrue(!manager.isLoaded(chunk.toLong())&&world.getEntity(uuid)==null,"Fixture starts as a genuinely cold entity-only chunk");
            var object=ObjectRegistry.TYPES.get("curtainsmall").create(world);
            context.assertTrue(object!=null,"Actual RP registry creates the saved object");
            object.setUuid(uuid);object.setPosition(Vec3d.ofBottomCenter(pos));object.setYaw(45);object.setObjectScale(1.125f);object.setCustomName(Text.literal("cold-alt-proof"));
            original=stable(object);
            var storage=((BbEntityStorageTestAccess)manager).bloodborne_dw$testEntityStorage();
            storage.writeChunkData(new ChunkDataList<>(chunk,List.of(object)));storage.awaitAll(true);
            var region=DimensionType.getSaveDirectory(world.getRegistryKey(),world.getServer().getSavePath(WorldSavePath.ROOT)).resolve("entities").resolve("r."+chunk.getRegionX()+"."+chunk.getRegionZ()+".mca");
            context.assertTrue(Files.isRegularFile(region)&&BbAlternateService.savedChunks(region).contains(chunk.toLong()),"Native entity-region writer persists the cold chunk in the real ALL discovery index");
            context.assertTrue(!manager.isLoaded(chunk.toLong())&&world.getEntity(uuid)==null,"Persisting alone does not insert the entity into the loaded RP index");
            object.discard();context.runAtTick(context.getTick()+1,this::step);
        }
        void step(){
            try {
                if(!started){if(BbAlternateService.status(world.getServer()).running()){again();return;}started=BbAlternateService.startVisits(player,"alt",List.of(new BbAlternateService.Visit(world,chunk)));context.assertTrue(started,"Start the actual ALL async chunk/edit path");again();return;}
                var status=BbAlternateService.status(world.getServer());if(status.running()){again();return;}
                context.assertTrue(status.changed()==1&&status.failed()==0&&status.chunksDone()==1,"Cold persisted RP is edited exactly once after native entity storage acknowledges its load: "+status);
                var entity=world.getEntity(uuid);context.assertTrue(entity instanceof RpObjectEntity,"The saved entity loads through the actual native manager");var restored=(RpObjectEntity)entity;
                context.assertTrue(restored.alternateVisual()&&original.equals(stable(restored)),"ALT preserves the cold saved UUID, Pos, Rotation, Scale, custom name and typed RP/source state");
                var proof=new JsonObject();proof.addProperty("status","PASS_ACTUAL_COLD_ENTITY_REGION_ALT");proof.addProperty("entityUuid",uuid.toString());proof.addProperty("chunkX",chunk.x);proof.addProperty("chunkZ",chunk.z);proof.addProperty("nativeColdEntityLoad",true);proof.addProperty("stableInstanceNbtPreserved",true);proof.addProperty("changed",status.changed());proof.addProperty("failed",status.failed());
                try{Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-cold-rp-alt-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception failure){throw new IllegalStateException(failure);}
                restored.removeObject();player.discard();context.complete();
            }catch(RuntimeException|Error failure){BbAlternateService.cancel(world.getServer());var object=world.getEntity(uuid);if(object instanceof RpObjectEntity rp)rp.removeObject();player.discard();throw failure;}
        }
        void again(){context.runAtTick(context.getTick()+1,this::step);}
        static NbtCompound stable(RpObjectEntity object){var nbt=new NbtCompound();object.writeNbt(nbt);for(String key:List.of("BbAltVisual","Motion","FallDistance","Fire","Air","OnGround","PortalCooldown"))nbt.remove(key);return nbt;}
    }
}
