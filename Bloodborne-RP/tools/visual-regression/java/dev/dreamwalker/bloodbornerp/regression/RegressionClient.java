package dev.dreamwalker.bloodbornerp.regression;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.mob.*;
import dev.dreamwalker.bloodbornerp.object.*;
import dev.dreamwalker.bloodbornerp.weapon.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import java.nio.file.*;

/** Build-only rendered-world regression; never shipped inside the mod. */
public final class RegressionClient implements ClientModInitializer {
 long began=System.nanoTime();boolean loaded,queued,prepared,done,pending;int ticks,frames,shot=-1;
 @Override public void onInitializeClient(){
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  WorldRenderEvents.END.register(c->{if(prepared)frames++;});
 }
 void tick(MinecraftClient c){
  if(done)return;
  try{
   if(System.nanoTime()-began>240_000_000_000L)throw new IllegalStateException("regression timeout");
   if(GeckoLibCache.getBakedModels().size()<119)return;
   if(!loaded){loaded=true;var l=c.createIntegratedServerLoader();var m=l.getClass().getDeclaredMethod("start",Screen.class,String.class,boolean.class,boolean.class);m.setAccessible(true);m.invoke(l,c.currentScreen,"rp-regression-world",false,false);}
   if(c.world==null||c.player==null||c.getServer()==null)return;
   c.setScreen(null);
   if(!queued){queued=true;var uuid=c.player.getUuid();c.getServer().execute(()->{
    var s=c.getServer();var w=s.getOverworld();var p=s.getPlayerManager().getPlayer(uuid);p.changeGameMode(GameMode.CREATIVE);
    for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)w.setBlockState(new BlockPos(x,160,z),Blocks.GLOWSTONE.getDefaultState());
    w.setTimeOfDay(6000);w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,s);
    for(var e:w.iterateEntities())if((e instanceof RpMobEntity||e instanceof RpObjectEntity)&&e.getY()>150&&e.squaredDistanceTo(0,161,0)<900)e.discard();
    p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION,12000,0,false,false));
    p.teleport(w,0,161,-4,0,0);p.getAbilities().flying=true;p.sendAbilitiesUpdate();
    for(var pair:java.util.List.of(new String[]{"huntsman_a","-3"},new String[]{"rabid_dog","3"})){
     var mob=MobRegistry.TYPES.get(pair[0]).create(w);mob.setAiDisabled(true);mob.refreshPositionAndAngles(Double.parseDouble(pair[1]),161,3,180,0);w.spawnEntity(mob);
    }
    var door=ObjectRegistry.TYPES.get("door_1").create(w);door.refreshPositionAndAngles(7,161,4,ObjectRegistry.placementYaw(0),0);w.spawnEntity(door);
    var lamp=ObjectRegistry.TYPES.get("hunterlamp").create(w);lamp.refreshPositionAndAngles(-6,161,3,0,0);lamp.setObjectScale(1.6f);w.spawnEntity(lamp);
    prepared=true;
   });return;}
   if(!prepared||c.player.squaredDistanceTo(0,161,-4)>4)return;
   if(++ticks<240)return;
   var huntsman=c.world.getEntitiesByType(MobRegistry.TYPES.get("huntsman_a"),c.player.getBoundingBox().expand(20),e->true).stream().findFirst().orElseThrow();
   var ctrl=huntsman.getAnimatableInstanceCache().getManagerForId(huntsman.getId()).getAnimationControllers().get("main");
   if(ctrl.getAnimationState()!=AnimationController.State.RUNNING||ctrl.getCurrentAnimation()==null||ctrl.getCurrentAnimation().loopType()!=Animation.LoopType.LOOP)throw new IllegalStateException("idle stopped after 12 seconds "+ctrl.getAnimationState());
   int next=(ticks-240)/30;
   if(next>=24){done=true;Files.writeString(c.runDirectory.toPath().resolve("regression-proof.json"),"{\"idleTicks\":"+ticks+",\"renderedFrames\":"+frames+",\"weaponScreenshots\":24,\"huntsmanIdle\":\"LOOP_RUNNING\",\"rabidDogScale\":"+AssetCatalog.get("rabid_dog").scale()+"}");BloodborneRp.LOGGER.info("BBRP_REGRESSION complete ticks={} frames={} shots=24",ticks,frames);c.scheduleStop();return;}
   if(next!=shot&&!pending){shot=next;int weaponIndex=next/8;boolean extended=next%8>=4,left=next%4>=2,third=next%2==1;
    var weapon=java.util.List.of(WeaponRegistry.SAW_CLEAVER,WeaponRegistry.SAW_SPEAR,WeaponRegistry.BOOM_HAMMER).get(weaponIndex);
    var stack=weapon.getDefaultStack();TrickWeaponItem.setForm(stack,extended?WeaponForm.EXTENDED:WeaponForm.FOLDED);
    var uuid=c.player.getUuid();c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(uuid);p.setStackInHand(Hand.MAIN_HAND,stack);p.setMainArm(left?Arm.LEFT:Arm.RIGHT);});
    c.player.setMainArm(left?Arm.LEFT:Arm.RIGHT);c.player.setStackInHand(Hand.MAIN_HAND,stack);c.options.setPerspective(third?Perspective.THIRD_PERSON_BACK:Perspective.FIRST_PERSON);
   }
   if(ticks%30==20&&!pending){pending=true;ScreenshotRecorder.saveScreenshot(c.runDirectory,String.format("%02d-weapon.png",shot),c.getFramebuffer(),message->{pending=false;});}
  }catch(Throwable ex){done=true;BloodborneRp.LOGGER.error("BBRP_REGRESSION failed",ex);c.scheduleStop();throw new IllegalStateException(ex);}
 }
}
