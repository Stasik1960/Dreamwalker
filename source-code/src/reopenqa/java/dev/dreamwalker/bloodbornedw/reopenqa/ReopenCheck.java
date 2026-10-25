package dev.dreamwalker.bloodbornedw.reopenqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Read-only probes from the actual normally saved working map, reopened in a
 * new dedicated JVM. No second travel/performance route or map reset. */
public final class ReopenCheck implements ModInitializer {
    private boolean done;
    @Override public void onInitialize(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(done)return;done=true;Path out=Path.of(System.getProperty("dw.reopen.output"));JsonObject report=new JsonObject();JsonArray checks=new JsonArray();report.add("checks",checks);
            try{
                var expected=NbtIo.readCompressed(out.resolve("expected.nbt").toFile()).getList("probes",NbtElement.COMPOUND_TYPE);
                var world=server.getOverworld();
                for(int i=0;i<expected.size();i++){
                    var row=expected.getCompound(i);int[] p=row.getIntArray("pos");BlockPos pos=new BlockPos(p[0],p[1],p[2]);var chunk=world.getChunk(pos);
                    var saved=row.getCompound("state");BlockState state=Registries.BLOCK.get(new Identifier(saved.getString("Name"))).getDefaultState();
                    var properties=saved.getCompound("Properties");for(var key:properties.getKeys())state=property(state,key,properties.getString(key));
                    require(chunk.getBlockState(pos)==state,"saved palette differs at "+pos);
                    var entity=chunk.getBlockEntity(pos);var tag=row.getCompound("entity");
                    if(row.getBoolean("hasEntity")){
                        require(entity instanceof CompositeBlockEntity,"saved architectural BE missing at "+pos);var own=(CompositeBlockEntity)entity;
                        require(own.contributions().equals(CompositeData.savedContributions(tag)),"saved owner graph differs at "+pos);
                        require(own.payload().equals(tag.getCompound("payload")),"typed payload differs at "+pos);
                        require(Objects.equals(own.resident(),tag.contains("resident",10)?CompositeData.owner(tag.getCompound("resident")):null),"resident differs at "+pos);
                    }else require(entity==null,"static cell gained a BE at "+pos);
                    var collision=state.getCollisionShape(world,pos);var outline=state.getOutlineShape(world,pos);
                    if(state.getBlock() instanceof StaticArchitectureBlock)require(collision==StaticArchitectureCatalog.recipe(state).collisionShape(),"native saved palette lost shared collision at "+pos);
                    JsonObject check=new JsonObject();check.addProperty("pos",pos.toShortString());check.addProperty("state",state.toString());check.addProperty("collisionBoxes",collision.getBoundingBoxes().size());check.addProperty("selectionBoxes",outline.getBoundingBoxes().size());check.addProperty("exactSavedStateAndOwnerPayload",true);checks.add(check);
                }
                require(checks.size()==5,"all five bounded saved positions required");report.addProperty("status","PASS_NEW_PROCESS_SAVED_MAP_REOPEN");server.save(false,true,true);
            }catch(Throwable failure){failure.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",failure.toString());}
            try{Files.writeString(out.resolve("reopen.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(Exception failure){throw new RuntimeException(failure);}
            server.stop(false);
        });
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static BlockState property(BlockState state,String key,String value){Property<?> property=state.getBlock().getStateManager().getProperty(key);if(property==null)throw new IllegalStateException("Unknown saved property "+key);return apply(state,property,value);}
    private static <T extends Comparable<T>> BlockState apply(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow());}
}
