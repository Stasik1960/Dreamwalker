package dev.dreamwalker.bloodbornedw.tool;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Actual server callbacks, packets and scoreboard effects; GUI acceptance is separate. */
public final class ToolCommandsGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="v12_tool_commands")
    public void npcInteractAtAndOrdinaryInteractAcrossTicksSpendOnlyOneUse(TestContext c){var f=new Fixture(c);var p=f.player(true);try{
        f.mob=dev.dreamwalker.bloodbornerp.mob.MobRegistry.TYPES.get("huntsman_a").create(c.getWorld());f.mob.setAiDisabled(true);f.mob.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(3,4,3))));c.getWorld().spawnEntity(f.mob);f.target=ToolTarget.entity(f.mob);c.assertTrue(ToolCommands.save(p,f.target,f.batch(),2,2),"NPC command binding saved");var center=f.mob.getBoundingBox().getCenter();p.setPosition(center.x,center.y-p.getStandingEyeHeight(),f.mob.getBoundingBox().minZ-2);p.setPitch(0);p.setYaw(0);
        p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.interactAt(f.mob,false,Hand.MAIN_HAND,center.subtract(f.mob.getPos())));c.assertTrue(f.score(p)==0&&ToolCommands.view(f.server,f.target).globalUsed()==0,"NPC preliminary PASS consumes no command use");
        c.runAtTick(2,()->{try{p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.interact(f.mob,false,Hand.MAIN_HAND));c.assertTrue(f.score(p)==1&&ToolCommands.view(f.server,f.target).globalUsed()==1,"normal NPC INTERACT on a later tick consumes exactly one command batch");c.complete();}finally{f.close();}});
    }catch(Throwable failure){f.close();throw failure;}}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="v12_tool_commands")
    public void restoredBindingCannotAttachToDifferentBlockAtSavedCell(TestContext c){var f=new Fixture(c);var p=f.player(true);var manager=f.server.getOverworld().getPersistentStateManager();var original=ToolCommands.get(f.server);try{
        f.blockTarget();c.assertTrue(ToolCommands.save(p,f.target,f.batch(),2,2),"original stone binding saved");NbtCompound saved=original.writeNbt(new NbtCompound());c.getWorld().setBlockState(f.block,Blocks.GOLD_BLOCK.getDefaultState());var restored=ToolCommands.read(saved);manager.set("bloodborne_dw_tool_commands",restored);ToolCommands.get(f.server);f.target=ToolTarget.block(c.getWorld(),f.block,p);
        c.assertTrue(f.target.registry().equals("minecraft:gold_block")&&!ToolCommands.fire(p,f.target)&&f.score("total")==0&&ToolCommands.view(f.server,f.target).text().isEmpty(),"offline/restored old stone binding cannot execute on a different block and is retired");c.getWorld().setBlockState(f.block,Blocks.STONE.getDefaultState());var reused=ToolTarget.block(c.getWorld(),f.block,p);c.assertTrue(!ToolCommands.fire(p,reused)&&f.score("total")==0,"later rebuilding the old block cannot resurrect retired commands");c.complete();
    }finally{manager.set("bloodborne_dw_tool_commands",original);ToolCommands.get(f.server);f.close();}}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="v12_tool_commands")
    public void rpInteractAtAndOrdinaryInteractAcrossTicksSpendOnlyOneUse(TestContext c){var f=new Fixture(c);var p=f.player(true);try{
        f.door=ObjectRegistry.TYPES.get("door_1").create(c.getWorld());f.door.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(3,4,3))));c.getWorld().spawnEntity(f.door);f.target=ToolTarget.entity(f.door);c.assertTrue(ToolCommands.save(p,f.target,f.batch(),2,2),"native packet fixture command binding saved");f.aim(p);
        p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.interactAt(f.door,false,Hand.MAIN_HAND,p.getEyePos().add(0,0,2).subtract(f.door.getPos())));
        c.assertTrue(f.score(p)==0&&!f.door.isOpen()&&ToolCommands.view(f.server,f.target).globalUsed()==0,"RP INTERACT_AT preliminary PASS does not spend command uses");
        c.runAtTick(2,()->{try{f.aim(p);p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.interact(f.door,false,Hand.MAIN_HAND));c.assertTrue(f.door.isOpen()&&f.score(p)==1&&ToolCommands.view(f.server,f.target).globalUsed()==1,"following real INTERACT on a different tick opens door and spends exactly one batch");c.complete();}finally{f.close();}});
    }catch(Throwable failure){f.close();throw failure;}}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="v12_tool_commands")
    public void orderedBatchDeduplicatesHandsAndSeparatesPersonalAndGlobalLimits(TestContext c) {
        var f=new Fixture(c);var a=f.player(true);var b=f.player(false);var third=f.player(false);
        try {
            f.blockTarget();var defaults=ToolCommands.view(f.server,f.target);
            c.assertTrue(defaults.text().isEmpty()&&defaults.personalLimit()==999999&&defaults.globalLimit()==999999,"new binding defaults are both 999999");
            c.assertTrue(ToolCommands.save(a,f.target,f.batch(),999999,999999),"maximum limit accepted");
            var before=ToolCommands.view(f.server,f.target);
            for(int[] limits:List.of(new int[]{0,1},new int[]{1,0},new int[]{1000000,1},new int[]{1,1000000}))
                c.assertTrue(!ToolCommands.save(a,f.target,f.batch(),limits[0],limits[1])&&ToolCommands.view(f.server,f.target).equals(before),"invalid limit cannot alter saved binding");
            c.assertTrue(ToolCommands.save(a,f.target,f.batch(),1,1)&&ToolCommands.save(a,f.target,f.batch(),2,4),"minimum and independent active limits accepted");
            f.useBlock(a,Hand.MAIN_HAND);f.useBlock(a,Hand.MAIN_HAND);f.useBlock(a,Hand.OFF_HAND);
            c.assertTrue(f.score(a)==1&&f.score("total")==1&&f.score("order")==1,"main-hand repeat and offhand in same server tick execute one ordered batch");
            c.runAtTick(2,()->{
                try {
                    f.useBlock(a,Hand.MAIN_HAND);f.useBlock(b,Hand.MAIN_HAND);
                    c.assertTrue(f.score(a)==2&&f.score(b)==1&&f.score("total")==3&&f.score("order")==3,"later click and different player each execute the complete batch in order");
                    c.runAtTick(4,()->{
                        try {
                            f.useBlock(a,Hand.MAIN_HAND);
                            c.assertTrue(f.score(a)==2&&f.score("total")==3,"exhausted personal limit rejects a later click while shared capacity remains");
                            f.useBlock(b,Hand.MAIN_HAND);f.useBlock(third,Hand.MAIN_HAND);
                            c.assertTrue(f.score(b)==2&&f.score(third)==0&&f.score("total")==4&&ToolCommands.view(f.server,f.target).globalUsed()==4,"global limit stops a fresh player after four accepted batches");
                            c.complete();
                        } finally { f.close(); }
                    });
                } catch(Throwable failure) { f.close();throw failure; }
            });
        } catch(Throwable failure) { f.close();throw failure; }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="v12_tool_commands")
    public void typedNbtReloadPreservesUuidCountsAndCannotResetSpentUses(TestContext c) {
        var f=new Fixture(c);var a=f.player(true);var b=f.player(false);var third=f.player(false);
        var manager=f.server.getOverworld().getPersistentStateManager();var original=ToolCommands.get(f.server);
        try {
            f.blockTarget();c.assertTrue(ToolCommands.save(a,f.target,f.batch(),1,2)&&ToolCommands.fire(a,f.target),"one batch spent before save");
            var saved=original.writeNbt(new NbtCompound());var unchanged=saved.copy();var restored=ToolCommands.read(saved);
            c.assertTrue(saved.equals(unchanged)&&restored.writeNbt(new NbtCompound()).equals(saved),"typed NBT read/write preserves target UUID keys, ordered text and all durable counters");
            manager.set("bloodborne_dw_tool_commands",restored);
            c.assertTrue(ToolCommands.get(f.server)==restored&&ToolCommands.view(f.server,f.target).globalUsed()==1,"runtime actually uses the restored PersistentState");
            // Reconnect clears only transient same-tick suppression, as a fresh server would.
            ToolCommands.forgetPlayer(f.server,a.getUuid());
            c.assertTrue(!ToolCommands.fire(a,f.target)&&f.score(a)==1&&ToolCommands.view(f.server,f.target).globalUsed()==1,"same UUID cannot regain its personal use after reload/reconnect");
            c.assertTrue(ToolCommands.fire(b,f.target)&&f.score(b)==1&&ToolCommands.view(f.server,f.target).globalUsed()==2,"another UUID spends the remaining shared use after reload");
            c.assertTrue(!ToolCommands.fire(third,f.target)&&f.score(third)==0&&f.score("total")==2,"shared spent count also survives reload");
            c.complete();
        } finally { manager.set("bloodborne_dw_tool_commands",original);ToolCommands.get(f.server);f.close(); }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v12_tool_commands")
    public void creativeAndForgedSaveAreRejectedWhileNativeDoorUseStillWorks(TestContext c) {
        var f=new Fixture(c);var operator=f.player(true);var creative=f.player(false);
        try {
            f.door=ObjectRegistry.TYPES.get("door_1").create(c.getWorld());
            f.door.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(3,4,3))));c.getWorld().spawnEntity(f.door);f.target=ToolTarget.entity(f.door);
            c.assertTrue(!creative.hasPermissionLevel(4)&&!ToolCommands.save(creative,f.target,f.batch(),1,1),"Creative without OP4 cannot attach elevated console commands");
            c.assertTrue(BbToolService.ITEM!=null,"real V12 tool service is registered");
            for(var p:List.of(operator,creative)) {
                var tool=new ItemStack(BbToolService.ITEM);tool.getOrCreateNbt().putInt("BBMode",BbToolItem.Mode.COMMANDS.ordinal());p.setStackInHand(Hand.MAIN_HAND,tool);
                c.assertTrue(!BbToolService.save(p,UUID.randomUUID(),f.batch(),1,1)&&ToolCommands.view(f.server,f.target).text().isEmpty(),"forged editor token is rejected even with correct mode and operator authority");
            }
            c.assertTrue(ToolCommands.save(operator,f.target,f.batch(),1,1),"authorized direct server save supplies the packet fixture binding");
            operator.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);f.aim(operator);
            var main=PlayerInteractEntityC2SPacket.interact(f.door,false,Hand.MAIN_HAND);
            operator.networkHandler.onPlayerInteractEntity(main);
            operator.networkHandler.onPlayerInteractEntity(main);
            operator.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.interact(f.door,false,Hand.OFF_HAND));
            c.assertTrue(f.door.isOpen()&&f.door.animationBusy()&&f.score(operator)==1&&f.score("total")==1,"real main/offhand packet handling fires one batch and retains ordinary door opening");
            var clip=f.door.asset().clip("open");int ticks=clip==null?2:(int)Math.ceil(clip.seconds()*20)+2;
            for(int tick=0;f.door.animationBusy()&&tick<ticks;tick++)f.door.tick();
            c.assertTrue(!f.door.animationBusy(),"actual authored door clip completes");
            c.runAtTick(2,()->{
                try {
                    f.aim(operator);operator.networkHandler.onPlayerInteractEntity(main);
                    c.assertTrue(!f.door.isOpen()&&f.score(operator)==1&&ToolCommands.view(f.server,f.target).globalUsed()==1,"exhausted command batch still allows the ordinary native close action on a later tick");
                    c.complete();
                } finally { f.close(); }
            });
        } catch(Throwable failure) { f.close();throw failure; }
    }

    private static final class Fixture implements AutoCloseable {
        final TestContext c;final MinecraftServer server;final String objective;
        final List<ServerPlayerEntity> players=new ArrayList<>();ToolTarget target;BlockPos block;RpObjectEntity door;dev.dreamwalker.bloodbornerp.mob.RpMobEntity mob;boolean closed;
        Fixture(TestContext c) {
            this.c=c;server=c.getWorld().getServer();objective="tc"+UUID.randomUUID().toString().replace("-","").substring(0,12);
            c.assertTrue(command("scoreboard objectives add "+objective+" dummy")>0&&server.getScoreboard().getObjective(objective)!=null,"native test scoreboard created");
            command("scoreboard players set total "+objective+" 0");command("scoreboard players set order "+objective+" 0");
        }
        ServerPlayerEntity player(boolean op) {
            var p=new ServerPlayerEntity(server,c.getWorld(),new GameProfile(UUID.randomUUID(),"TC"+UUID.randomUUID().toString().replace("-","").substring(0,12)));
            var connection=new ClientConnection(NetworkSide.SERVERBOUND) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet,PacketCallbacks callbacks) {}
            };
            p.networkHandler=new ServerPlayNetworkHandler(server,connection,p);p.changeGameMode(GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;
            p.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(20,3,20))));c.getWorld().spawnEntity(p);players.add(p);
            if(op)server.getPlayerManager().getOpList().add(new OperatorEntry(p.getGameProfile(),4,false));
            c.assertTrue(p.hasPermissionLevel(4)==op,"genuine player permission matches operator fixture");
            command("scoreboard players set "+p.getGameProfile().getName()+" "+objective+" 0");return p;
        }
        void blockTarget() {block=c.getAbsolutePos(new BlockPos(3,3,3));c.getWorld().setBlockState(block,Blocks.STONE.getDefaultState());target=ToolTarget.block(c.getWorld(),block,players.get(0));c.assertTrue(target!=null&&target.valid(server),"real loaded block target exists");}
        String batch() {return "scoreboard players add {player} "+objective+" 1\nscoreboard players add total "+objective+" 1\nscoreboard players operation order "+objective+" = total "+objective;}
        void useBlock(ServerPlayerEntity p,Hand hand) {var hit=new BlockHitResult(Vec3d.ofCenter(block),Direction.UP,block,false);c.assertTrue(UseBlockCallback.EVENT.invoker().interact(p,c.getWorld(),hand,hit)==ActionResult.PASS,"command callback preserves ordinary block interaction");}
        void aim(ServerPlayerEntity p) {var box=door.interactionBoxes().stream().min(Comparator.comparingDouble(b->b.minZ)).orElseThrow();var center=box.getCenter();p.setPosition(center.x,center.y-p.getStandingEyeHeight(),box.minZ-2);p.setPitch(0);p.setYaw(0);var hit=dev.dreamwalker.bloodbornerp.object.RpObjectSelection.playerTarget(p,dev.dreamwalker.bloodbornerp.object.RpObjectSelection.entityReach(p));c.assertTrue(hit!=null&&hit.getEntity()==door,"native packet aims at the actual current door interaction part");}
        int command(String text) {return server.getCommandManager().executeWithPrefix(server.getCommandSource().withLevel(4).withSilent(),text);}
        int score(ServerPlayerEntity p) {return score(p.getGameProfile().getName());}
        int score(String holder) {return command("scoreboard players get "+holder+" "+objective);}
        @Override public void close() {
            if(closed)return;closed=true;ToolCommands.remove(server,target);
            if(door!=null)door.removeObject();if(mob!=null)mob.discard();if(block!=null)c.getWorld().removeBlock(block,false);
            for(var p:players){ToolCommands.forgetPlayer(server,p.getUuid());if(server.getPlayerManager().isOperator(p.getGameProfile()))server.getPlayerManager().removeFromOperators(p.getGameProfile());p.discard();}
            command("scoreboard objectives remove "+objective);
        }
    }
}
