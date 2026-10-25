package dev.dreamwalker.bloodbornedw.restorationqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.tool.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornerp.object.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Targeted executable regressions for the user's new interaction fixes. */
public final class InteractionQa implements ModInitializer {
    public static final BlockPos BASE=new BlockPos(15080,200,15080);
    private static final JsonArray checks=new JsonArray();private boolean done;private int age;
    public static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks.add(message);}
    public static void save(Path p,String status,Throwable error){try{var j=new JsonObject();j.addProperty("status",status);j.add("checks",checks);if(error!=null)j.addProperty("error",error.toString());Files.writeString(p,new GsonBuilder().setPrettyPrinting().create().toJson(j));}catch(Exception e){throw new RuntimeException(e);}}
    public static final class Actor extends ServerPlayerEntity {
        public final List<String> messages=new ArrayList<>();
        Actor(MinecraftServer s,ServerWorld w){super(s,w,new GameProfile(UUID.randomUUID(),"InteractionProbe"));new ServerPlayNetworkHandler(s,new ClientConnection(NetworkSide.SERVERBOUND),this);changeGameMode(GameMode.CREATIVE);setPosition(BASE.getX()-20,BASE.getY()+3,BASE.getZ()-20);}
        @Override public boolean hasPermissionLevel(int level){return true;}
        @Override public void sendMessage(Text text,boolean action){messages.add(text.getString());}
    }
    @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{if(done||++age<25)return;done=true;Path out=Path.of(System.getProperty("dw.interaction.qa.output"));try{run(s);save(out.resolve("server-checks.json"),"PASS",null);}catch(Throwable t){t.printStackTrace();save(out.resolve("server-checks.json"),"FAIL",t);}s.stop(false);});}
    public static void run(MinecraftServer server){
        var w=server.getOverworld();w.getChunk(BASE);var p=new Actor(server,w);var point=BASE.add(10,0,0);
        for(Block block:List.of(FaerieVeilBlock.BLOCK,DwUtilityBlocks.DARKNESS)){
            w.setBlockState(point,block.getDefaultState(),Block.NOTIFY_ALL);var state=w.getBlockState(point);
            check(!state.get(DwUtilityBlocks.PLAYER_COLLISION),"default remains passable "+block);
            check(!state.getOutlineShape(w,point).isEmpty()&&state.getOutlineShape(w,point).getBoundingBox().getLengthX()==1,"contextless hit-particle shape has bounds "+block);
            check(state.shouldBlockVision(w,point)&&!state.shouldSuffocate(w,point),"opaque in-wall view without suffocation "+block);
            for(var mode:List.of(GameMode.CREATIVE,GameMode.SURVIVAL,GameMode.ADVENTURE)){
                p.changeGameMode(mode);check(state.getOutlineShape(w,point,ShapeContext.of(p)).isEmpty()==(mode!=GameMode.CREATIVE),"selection restriction "+mode+" "+block);
                check(state.getCollisionShape(w,point,ShapeContext.of(p)).isEmpty(),"passable player mode "+mode+" "+block);
            }
            p.changeGameMode(GameMode.CREATIVE);var target=ToolTarget.block(w,point,p);var held=new ItemStack(BbToolService.ITEM);held.getOrCreateNbt().putInt("BBMode",BbToolItem.Mode.SPECIAL.ordinal());p.getInventory().setStack(p.getInventory().selectedSlot,held);BbToolService.apply(p,target);state=w.getBlockState(point);
            check(state.get(DwUtilityBlocks.PLAYER_COLLISION)&&!state.getCollisionShape(w,point,ShapeContext.of(p)).isEmpty(),"Special enables only player collision "+block);
            var pig=EntityType.PIG.create(w);check(state.getCollisionShape(w,point,ShapeContext.of(pig)).isEmpty()&&state.getCollisionShape(w,point,ShapeContext.absent()).isEmpty(),"non-player and placement queries remain passable "+block);
            var picked=block.getPickStack(w,point,state);check("true".equals(picked.getSubNbt("BlockStateTag").getString("player_collision")),"middle pick preserves player collision property "+block);
            BbToolService.apply(p,target);check(!w.getBlockState(point).get(DwUtilityBlocks.PLAYER_COLLISION),"Special switches collision back "+block);
            check(p.interactionManager.tryBreakBlock(point)&&w.getBlockState(point).isAir(),"creative removal remains valid "+block);
        }
        var plain=CompositeArchitecture.kindBlock("source_andesite").getDefaultState();var original=put(w,BASE.add(14,0,0),plain,ImportedTypeAliases.markPayload(null));var target=ToolTarget.block(w,BASE.add(14,0,0),p);var data=w.getBlockEntity(BASE.add(14,0,0)).createNbt();int count=p.messages.size();check(!BbToolService.special(p,target)&&p.messages.size()==count&&data.equals(w.getBlockEntity(BASE.add(14,0,0)).createNbt())&&w.getBlockState(BASE.add(14,0,0)).equals(plain),"unsupported Special silently preserves appearance and data");
        for(int i=0;i<5;i++){var item=new ItemStack(BbToolService.ITEM);item.getOrCreateNbt().putInt("BBMode",i);check(BbToolItem.mode(item).ordinal()==i,"historical mode ordinal "+i);}
        check(BbToolRotations.rotate(p,target)&&w.getBlockState(BASE.add(14,0,0)).get(CompositeRootBlock.ROTATION)==1,"owned source rotates clockwise45");
        var thin=CompositeArchitecture.kindBlock("prototype_thin_window").getDefaultState();var thinRoot=BASE.add(20,0,0);w.setBlockState(thinRoot.down(),Blocks.STONE.getDefaultState());put(w,thinRoot,thin,new NbtCompound());var thinBefore=w.getBlockState(thinRoot);check(BbToolRotations.rotate(p,ToolTarget.block(w,thinRoot,p))&&w.getBlockState(thinRoot).equals(GlazingTypes.step90(thinBefore)),"cardinal thin glass uses90 orientation step");
        for(String id:List.of("tree3","cage_obj_3","door_1","ladder")){
            var rp=(RpObjectEntity)net.minecraft.registry.Registries.ENTITY_TYPE.get(new net.minecraft.util.Identifier("bloodborne_rp",id)).create(w);rp.setPosition(BASE.getX()+30,BASE.getY(),BASE.getZ());
            check(rp.getBoundingBox().equals(rp.physicalBounds()),"public entity bounds match actual physical union "+id);
            check(rp.getVisibilityBoundingBox().equals(rp.queryBounds()),"render and query envelope retained "+id);
            var physical=List.copyOf(rp.activePhysicalBoxes());rp.refreshCollider();check(physical.equals(rp.activePhysicalBoxes()),"bounding-box update never changes player colliders "+id);
        }
        p.getInventory().setStack(p.getInventory().selectedSlot,new ItemStack(plain.getBlock()));check(NoCollidePlacement.arm(p)&&NoCollidePlacement.armed(p),"no-collide arms actual held stack");var stack=p.getMainHandStack();stack.increment(1);check(NoCollidePlacement.armed(p),"stack count changes retain established lease");count=p.messages.size();p.getInventory().selectedSlot=(p.getInventory().selectedSlot+1)%9;check(!NoCollidePlacement.armed(p)&&p.messages.size()==count+1&&p.messages.get(count).equals("Режим без коллизий сброшен"),"slot change expires lease with one notification");check(!NoCollidePlacement.armed(p)&&p.messages.size()==count+1,"expired lease does not repeat notification");p.getInventory().setStack(p.getInventory().selectedSlot,new ItemStack(Items.STICK));check(!NoCollidePlacement.arm(p),"unsuitable held item denied");
        prepareScene(w);
        var owner=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(w,BASE.add(-6,0,-1));var beforeId=owner.instanceId();
        p.setPosition(BASE.getX()-5.5,BASE.getY(),BASE.getZ()-4);p.setYaw(0);p.setPitch(0);
        check(CompositeRuntime.use(w,BASE.add(-6,1,0),p).isAccepted()&&w.getBlockState(BASE.add(-6,0,-1)).get(CompositeRootBlock.OPEN),"RMB opens separated shutter window beside wall");
        check(CompositeRuntime.use(w,BASE.add(-6,1,0),p).isAccepted()&&!w.getBlockState(BASE.add(-6,0,-1)).get(CompositeRootBlock.OPEN),"RMB closes separated shutter window beside wall");
        check(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(w,BASE.add(-6,0,-1)).instanceId().equals(beforeId),"window toggling retains owner identity");
    }
    public static ObjectInstance put(ServerWorld w,BlockPos pos,BlockState state,NbtCompound payload){var obj=CompositeRuntime.instance(w,pos,state,UUID.randomUUID(),payload);var result=dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditObjectTransactions.replace(w,List.of(),List.of(obj));check(result.outcome()==TransactionCore.Outcome.COMMITTED,"fixture placement "+state+": "+result.reason());return obj;}
    public static void prepareScene(ServerWorld w){
        for(int x=-10;x<12;x++)for(int z=-5;z<5;z++)w.setBlockState(BASE.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        for(int x=-8;x<-3;x++)for(int y=0;y<4;y++)w.setBlockState(BASE.add(x,y,3),Blocks.RED_WOOL.getDefaultState(),Block.NOTIFY_ALL);
        put(w,BASE.add(-6,0,0),CompositeArchitecture.kindBlock("source_shutter_window_surround").getDefaultState(),ImportedTypeAliases.markPayload(null));
        var payload=ImportedTypeAliases.markPayload(null);var shift=new net.minecraft.nbt.NbtList();shift.add(net.minecraft.nbt.NbtDouble.of(0));shift.add(net.minecraft.nbt.NbtDouble.of(0));shift.add(net.minecraft.nbt.NbtDouble.of(1));payload.put("SourceShift",shift);
        put(w,BASE.add(-6,0,-1),CompositeArchitecture.kindBlock("source_shutter_window").getDefaultState(),payload);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++){w.setBlockState(BASE.add(x,y,z),DwUtilityBlocks.DARKNESS.getDefaultState(),Block.NOTIFY_ALL);w.setBlockState(BASE.add(5+x,y,z),FaerieVeilBlock.BLOCK.getDefaultState(),Block.NOTIFY_ALL);}
    }
}
