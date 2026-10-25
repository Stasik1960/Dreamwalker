package dev.dreamwalker.bloodbornedw.restorationqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Disposable fixtures; never edits the user's map. */
public final class RestorationQa implements ModInitializer {
    private final JsonArray checks=new JsonArray(); private Path out; private boolean done; private int age;
    public static final BlockPos BASE=new BlockPos(14080,200,14080);
    private void check(boolean ok,String text){var r=new JsonObject();r.addProperty("ok",ok);r.addProperty("name",text);checks.add(r);}
    private void stage(String name,Checked action){try{action.run();}catch(Throwable e){e.printStackTrace();check(false,name+": "+e);}}
    private interface Checked{void run()throws Exception;}
    @Override public void onInitialize(){String dir=System.getProperty("dw.restore.qa.output");if(dir==null)return;out=Path.of(dir);
        ServerWorldEvents.LOAD.register((s,w)->{if(w.getRegistryKey()==World.OVERWORLD){w.setSpawnPos(BASE,0);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done||++age<30)return;done=true;var w=s.getOverworld();var p=actor(s,w);
            stage("compact",()->compact(w,p));stage("worldedit",()->worldedit(w,p));stage("tree targeting",()->tree(w,p));
            var result=new JsonObject();boolean pass=true;for(var c:checks)pass&=c.getAsJsonObject().get("ok").getAsBoolean();result.addProperty("status",pass?"PASS":"FAIL");result.add("checks",checks);
            try{Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new RuntimeException(e);}s.stop(false);
        });
    }
    private ServerPlayerEntity actor(MinecraftServer s,ServerWorld w){var p=new ServerPlayerEntity(s,w,new GameProfile(UUID.randomUUID(),"RestoreProbe")){@Override public void sendMessage(Text text,boolean action){}@Override public boolean hasPermissionLevel(int level){return true;}};new ServerPlayNetworkHandler(s,new ClientConnection(NetworkSide.SERVERBOUND),p);p.changeGameMode(GameMode.CREATIVE);p.setPosition(BASE.getX(),BASE.getY(),BASE.getZ()+4);return p;}
    private void aim(ServerPlayerEntity p,BlockPos at){p.setPosition(at.getX()+.5,at.getY(),at.getZ()+3);var d=Vec3d.ofCenter(at).subtract(p.getEyePos());p.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    private void compact(ServerWorld w,ServerPlayerEntity p){
        var state=CompositeArchitecture.kindBlock("source_andesite").getDefaultState().with(ImportedCompactBlock.COMPACT,true).with(CompositeRootBlock.VARIANT,1);
        var at=BASE;w.setBlockState(at,state,Block.NOTIFY_ALL);aim(p,at);check(w.getBlockEntity(at)==null,"compact state intentionally has no BE");check(p.interactionManager.tryBreakBlock(at)&&w.getBlockState(at).isAir(),"creative compact cube removed");
        w.setBlockState(at,state,Block.NOTIFY_ALL);p.changeGameMode(GameMode.SURVIVAL);check(p.interactionManager.tryBreakBlock(at)&&w.getBlockState(at).isAir(),"survival compact cube removed");p.changeGameMode(GameMode.CREATIVE);
        var protectedAt=BASE.add(2,0,0);w.setBlockState(protectedAt,state,Block.NOTIFY_ALL);PlayerBlockBreakEvents.BEFORE.register((world,player,pos,b,e)->!pos.equals(protectedAt));aim(p,protectedAt);check(!p.interactionManager.tryBreakBlock(protectedAt)&&w.getBlockState(protectedAt).equals(state),"BEFORE cancellation preserves compact cube");
    }
    private com.sk89q.worldedit.world.World adapter(ServerWorld w)throws Exception{for(var m:Class.forName("com.sk89q.worldedit.fabric.FabricAdapter").getMethods())if(m.getName().equals("adapt")&&m.getParameterCount()==1&&m.getParameterTypes()[0].isInstance(w)&&com.sk89q.worldedit.world.World.class.isAssignableFrom(m.getReturnType()))return (com.sk89q.worldedit.world.World)m.invoke(null,w);throw new IllegalStateException("No WE adapter");}
    private void worldedit(ServerWorld w,ServerPlayerEntity p)throws Exception{
        var a=adapter(w);var at=BASE.add(10,0,0);var v=BlockVector3.at(at.getX(),at.getY(),at.getZ());
        for(String id:List.of("source_andesite","source_hold_bench","dw_darkness")){
            var type=Objects.requireNonNull(com.sk89q.worldedit.world.block.BlockTypes.get("bloodborne_dw:"+id));
            try(var edit=WorldEdit.getInstance().newEditSession(a)){edit.setBlocks(new CuboidRegion(a,v,v),type.getDefaultState());edit.flushSession();
                check(Registries.BLOCK.getId(w.getBlockState(at).getBlock()).getPath().equals(id),"real WE setBlocks writes "+id+" (actual "+w.getBlockState(at)+")");
                if(w.getBlockState(at).getBlock() instanceof CompositeRootBlock&&!ImportedCompactBlock.compact(w.getBlockState(at)))check(w.getBlockEntity(at) instanceof CompositeBlockEntity be&&be.resident()!=null,"WE creates complete owner "+id);
                try(var undo=WorldEdit.getInstance().newEditSession(a)){edit.undo(undo);undo.flushSession();}check(w.getBlockState(at).isAir(),"WE undo "+id);
                try(var redo=WorldEdit.getInstance().newEditSession(a)){edit.redo(redo);redo.flushSession();}check(Registries.BLOCK.getId(w.getBlockState(at).getBlock()).getPath().equals(id),"WE redo "+id);
            }
            var state=w.getBlockState(at);if(w.getBlockEntity(at) instanceof CompositeBlockEntity be&&be.resident()!=null)CompositeRuntime.remove(w,be.resident(),null,false);else w.setBlockState(at,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
    }
    private void tree(ServerWorld w,ServerPlayerEntity p){
        var type=Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp","tree1"));var tree=(dev.dreamwalker.bloodbornerp.object.RpObjectEntity)type.create(w);tree.setPosition(BASE.getX()+40,BASE.getY(),BASE.getZ());w.spawnEntity(tree);
        var start=new Vec3d(BASE.getX()+60,BASE.getY()+20,BASE.getZ()+20);var end=start.add(0,0,-4);
        var fake=new net.minecraft.util.hit.EntityHitResult(tree,start);var hit=dev.dreamwalker.bloodbornerp.object.RpObjectSelection.raycast(p,start,end,new Box(start,end).expand(1),e->true,16,fake);check(hit==null,"coarse RP entity AABB cannot survive precise ray miss");
    }
}
