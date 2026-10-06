package dev.dreamwalker.bloodbornedw.client;

import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import dev.dreamwalker.bloodbornedw.block.Visual;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;

/** Loads only an explicitly named test copy and exercises real chunk rendering/S2C rules. */
public final class DwWorldSmoke implements ClientModInitializer {
    private boolean started,positioned,finished; private int ticks;
    private final long deadline=System.nanoTime()+240_000_000_000L;
    public void onInitializeClient() { if(System.getProperty("dw.smoke.world")!=null) ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(MinecraftClient client) {
        if(finished)return;
        if(System.nanoTime()>deadline)throw new IllegalStateException("DW world smoke timed out");
        if(!started && client.getOverlay()==null && DwClientSmoke.passed && dev.dreamwalker.bloodbornerp.clienttest.RpClientSmoke.passed) {
            started=true;client.options.pauseOnLostFocus=false;
            try { var loader=client.createIntegratedServerLoader();var method=loader.getClass().getDeclaredMethod("start",Screen.class,String.class,boolean.class,boolean.class);method.setAccessible(true);method.invoke(loader,client.currentScreen,System.getProperty("dw.smoke.world"),false,false); }
            catch(ReflectiveOperationException error){throw new IllegalStateException("DW test world load failed",error);}
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        BlockPos root=new BlockPos(Integer.getInteger("dw.smoke.x",-59),337,Integer.getInteger("dw.smoke.z",-1101));
        if(!positioned) {
            positioned=true;client.setScreen(null);client.player.getAbilities().flying=true;client.player.sendAbilitiesUpdate();
            var server=client.getServer();var uuid=client.player.getUuid();server.execute(()->{var player=server.getPlayerManager().getPlayer(uuid);if(player!=null)player.teleport(server.getOverworld(),root.getX()+5.5,root.getY()+4.5,root.getZ()+7.5,145,24);});
            client.getNetworkHandler().sendChatCommand("bb visual alt all");
        }
        ticks++;
        if(ticks<160)return;
        var state=client.world.getBlockState(root);String id=BloodborneDwClient.id(state);
        if(id==null || BloodborneDwClient.effective(root,id,state.get(DwBlocks.VISUAL))!=Visual.ALT)throw new IllegalStateException("DW S2C visual rule/render root missing at "+root+" state="+state);
        if(Boolean.getBoolean("dw.smoke.alt")) {
            var model=client.getBlockRenderManager().getModel(DwBlocks.byId("00001").block().getDefaultState().with(DwBlocks.VISUAL,Visual.ALT));
            if(!model.getParticleSprite().getContents().getId().getPath().equals("block/gold_block"))throw new IllegalStateException("External ALT full model was not loaded");
        }
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"DW-gallery-smoke.png",client.getFramebuffer(),text->{});
        dev.dreamwalker.bloodbornerp.BloodborneRp.LOGGER.info("DW_WORLD_SMOKE complete root={} id={} externalAlt={} ticks={}",root,id,Boolean.getBoolean("dw.smoke.alt"),ticks);
        finished=true;client.scheduleStop();
    }
}
