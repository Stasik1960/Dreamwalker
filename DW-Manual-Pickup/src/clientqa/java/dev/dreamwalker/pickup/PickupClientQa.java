package dev.dreamwalker.pickup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Disposable real client + integrated server + keyboard callback + Fabric network test. */
public final class PickupClientQa implements ClientModInitializer {
    private record Step(String name, int delay, Consumer<MinecraftClient> action) {}
    private final Queue<Step> steps = new ArrayDeque<>();
    private final StringBuilder report = new StringBuilder();
    private CompletableFuture<?> pending;
    private int ticks, wait;
    private boolean started, prepared, stopped;

    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if(stopped) return;
        try {
            if(++ticks>6000) throw new AssertionError("Client QA timed out");
            if(!started && c.getOverlay()==null && c.currentScreen!=null) {
                started=true;
                c.options.getViewDistance().setValue(2);
                c.options.getSimulationDistance().setValue(5);
                c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("pickup-qa-"+System.currentTimeMillis(),
                        new LevelInfo("Pickup QA",GameMode.SURVIVAL,false,Difficulty.PEACEFUL,true,
                                new GameRules(),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(123L,false,false),
                        registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                return;
            }
            if(c.player==null || c.world==null || c.getServer()==null) return;
            if(pending!=null) {
                if(!pending.isDone()) return;
                pending.join(); pending=null;
            }
            if(!prepared) {
                prepared=true;
                // Every run starts from a known control layout, regardless of saved QA options.
                c.options.getSneakToggled().setValue(false);
                c.options.sneakKey.setPressed(false);
                c.options.sneakKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_LEFT_SHIFT));
                KeyBinding.updateKeysByCode();
                c.setScreen(null);
                c.onWindowFocusChanged(true);
                plan();
            }
            if(++wait<steps.element().delay) return;
            Step step=steps.remove(); wait=0;
            step.action.accept(c);
            report.append("PASS: ").append(step.name).append('\n');
            save(stopped ? "PASS" : "RUNNING");
        } catch(Throwable error) {
            report.append("FAIL: ").append(error).append('\n');
            error.printStackTrace();
            save("FAIL"); stopped=true; c.scheduleStop();
        }
    }

    private void step(String name,int delay,Consumer<MinecraftClient> action) { steps.add(new Step(name,delay,action)); }
    private void server(MinecraftClient c,Consumer<ServerPlayerEntity> action) {
        pending=c.getServer().submit(()-> action.accept(c.getServer().getPlayerManager().getPlayer(c.player.getUuid())));
    }
    private void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private void key(MinecraftClient c,int code,int action) {
        c.onWindowFocusChanged(true);
        c.keyboard.onKey(c.getWindow().getHandle(),code,0,action,0);
    }
    private void mouse(MinecraftClient c,int action) {
        try {
            c.onWindowFocusChanged(true);
            var callback=net.minecraft.client.Mouse.class.getDeclaredMethod("onMouseButton",long.class,int.class,int.class,int.class);
            callback.setAccessible(true);
            callback.invoke(c.mouse,c.getWindow().getHandle(),GLFW.GLFW_MOUSE_BUTTON_4,action,0);
        } catch(ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
    private void spawn(ServerPlayerEntity p,Item type,int count,double offset) {
        ItemEntity i=new ItemEntity(p.getWorld(),p.getX()+offset,p.getY(),p.getZ(),new ItemStack(type,count));
        i.setNoGravity(true); i.setVelocity(Vec3d.ZERO); i.resetPickupDelay(); p.getWorld().spawnEntity(i);
    }
    private void plan() {
        step("prepare isolated survival scene",1,c->server(c,p->{
            p.getInventory().clear();
            for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) p.getServerWorld().setBlockState(new BlockPos(x,100,z),Blocks.STONE.getDefaultState());
            p.teleport(p.getServerWorld(),.5,101,.5,0,0);
            spawn(p,Items.DIAMOND,12,.1); spawn(p,Items.EMERALD,7,.7); spawn(p,Items.GOLD_INGOT,3,1.0);
        }));
        step("standing near drops does not collect",30,c->server(c,p->require(p.getInventory().isEmpty(),"Auto pickup occurred")));
        step("first Shift key-down callback",1,c->key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS));
        step("first press collects only nearest stack and preserves sneak",15,c->{
            require(c.player.input.sneaking,"Client sneak input lost");
            server(c,p->{
                require(p.isSneaking(),"Server sneak state lost");
                require(p.getInventory().count(Items.DIAMOND)==12 && p.getInventory().count(Items.EMERALD)==0
                        && p.getInventory().count(Items.GOLD_INGOT)==0,"Nearest stack not collected exactly once");
            });
        });
        for(int n=0;n<15;n++) step("OS key repeat ignored "+n,2,c->key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_REPEAT));
        step("hold does not collect second stack",10,c->server(c,p->require(p.getInventory().count(Items.EMERALD)==0
                && p.getInventory().count(Items.GOLD_INGOT)==0 && p.isSneaking(),"Holding Shift collected more items")));
        step("release and press again",1,c->{key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_RELEASE);key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS);});
        step("second press collects exactly second stack",15,c->server(c,p->require(p.getInventory().count(Items.EMERALD)==7
                && p.getInventory().count(Items.GOLD_INGOT)==0,"Second press wrong result")));
        step("Shift in inventory screen",1,c->{
            key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_RELEASE);
            c.setScreen(new InventoryScreen(c.player));
            key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS);
            key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_RELEASE);
        });
        step("inventory Shift does not pick up",15,c->{
            c.setScreen(null);
            server(c,p->require(p.getInventory().count(Items.GOLD_INGOT)==0,"Inventory key collected drop"));
        });
        step("third press after closing inventory",1,c->key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS));
        step("third stack collected",15,c->server(c,p->require(p.getInventory().count(Items.GOLD_INGOT)==3,"Third stack not collected")));
        step("rebind sneak to G",1,c->{
            key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_RELEASE);
            c.options.sneakKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_G));
            KeyBinding.updateKeysByCode();
            server(c,p->spawn(p,Items.REDSTONE,4,.1));
        });
        step("old Shift key no longer requests pickup",15,c->key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS));
        step("old key left item untouched; use new sneak key",15,c->{
            require(c.player.getInventory().count(Items.REDSTONE)==0,"Old binding picked up item");
            key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_RELEASE);
            key(c,GLFW.GLFW_KEY_G,GLFW.GLFW_PRESS);
        });
        step("new sneak key works",15,c->server(c,p->require(p.getInventory().count(Items.REDSTONE)==4 && p.isSneaking(),
                "Rebinding failed: redstone="+p.getInventory().count(Items.REDSTONE)+", sneak="+p.isSneaking())));
        step("real connected player enters spectator mode",1,c->{
            key(c,GLFW.GLFW_KEY_G,GLFW.GLFW_RELEASE);
            server(c,p->{ p.changeGameMode(GameMode.SPECTATOR); spawn(p,Items.LAPIS_LAZULI,2,.1); });
        });
        step("spectator key-down callback",15,c->key(c,GLFW.GLFW_KEY_G,GLFW.GLFW_PRESS));
        step("spectator cannot pick up even by direct server request",15,c->server(c,p->{
            require(p.isSpectator() && !PickupService.pickUpNearest(p) && p.getInventory().count(Items.LAPIS_LAZULI)==0,
                    "Spectator collected an item");
            p.changeGameMode(GameMode.SURVIVAL);
            // Sneak descends through the floor in spectator flight. Reset the scene
            // before testing the next binding, rather than testing from below the drops.
            p.teleport(p.getServerWorld(),.5,101,.5,0,0);
            p.setVelocity(Vec3d.ZERO);
        }));
        step("rebind sneak to mouse button",15,c->{
            key(c,GLFW.GLFW_KEY_G,GLFW.GLFW_RELEASE);
            c.options.sneakKey.setBoundKey(InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_4));
            KeyBinding.updateKeysByCode(); mouse(c,GLFW.GLFW_PRESS);
        });
        step("mouse binding keeps pickup and crouching",15,c->server(c,p->require(p.getInventory().count(Items.LAPIS_LAZULI)==2
                && p.isSneaking(),"Mouse binding failed: lapis="+p.getInventory().count(Items.LAPIS_LAZULI)
                +", sneak="+p.isSneaking()+", position="+p.getPos())));
        step("enable toggle sneak",1,c->{
            mouse(c,GLFW.GLFW_RELEASE);
            c.options.sneakKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_LEFT_SHIFT));
            KeyBinding.updateKeysByCode(); c.options.getSneakToggled().setValue(true);
            server(c,p->{ spawn(p,Items.IRON_INGOT,2,.1); spawn(p,Items.COPPER_INGOT,3,.7); });
        });
        step("toggle sneak on and pick up",15,c->key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS));
        step("toggle on collects one stack",15,c->{
            key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_RELEASE);
            server(c,p->require(p.isSneaking() && p.getInventory().count(Items.IRON_INGOT)==2
                    && p.getInventory().count(Items.COPPER_INGOT)==0,"Toggle on failed"));
        });
        step("remaining crouched with toggle does not collect",20,c->server(c,p->require(p.isSneaking()
                && p.getInventory().count(Items.COPPER_INGOT)==0,"Toggle sneak caused auto pickup")));
        step("toggle sneak off also counts as one press",1,c->key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_PRESS));
        step("toggle off collects next stack and stands up",15,c->server(c,p->require(!p.isSneaking()
                && p.getInventory().count(Items.COPPER_INGOT)==3,"Toggle off failed")));
        step("complete",10,c->{
            key(c,GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_RELEASE);
            stopped=true; save("PASS");
            ScreenshotRecorder.saveScreenshot(c.runDirectory,"pickup-qa.png",c.getFramebuffer(),message->c.scheduleStop());
        });
    }

    private void save(String status) {
        try { Files.writeString(Path.of("pickup-client-qa.txt"),"STATUS: "+status+"\n"+report); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
}
