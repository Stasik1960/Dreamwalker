package dev.dreamwalker.bloodbornedw.restorationqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornerp.mob.*;
import dev.dreamwalker.bloodbornerp.client.CatalogMobRenderer;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Runs the real Custom NPC renderer against every model and animation state. */
public final class NpcClientQa implements ClientModInitializer {
 private final JsonArray checks=new JsonArray();private final List<String> ids=new ArrayList<>(MobRegistry.TYPES.keySet());private Path out;private int index,ticks,total;private LivingEntity npc;private RpMobEntity proxy,normal;private Object data;private Method getEntity;private boolean failed,rendered;private double originalHealth;private final Set<String> states=new HashSet<>();
 private void check(boolean value,String text){if(!value)throw new AssertionError(text);checks.add(text);}
 private final boolean finalCorpses=Boolean.getBoolean("dw.npc.qa.finalCorpses");private String heldPose;
 @Override public void onInitializeClient(){out=Path.of(System.getProperty("dw.npc.qa.output"));ClientTickEvents.END_CLIENT_TICK.register(c->{if(failed||c.world==null||c.player==null||c.getOverlay()!=null)return;try{tick(c);}catch(Throwable t){t.printStackTrace();save("FAIL",t.toString());failed=true;c.scheduleStop();}});}
 private void prepare(MinecraftClient c)throws Exception{
  var type=Registries.ENTITY_TYPE.get(new Identifier("customnpcs","customnpc"));var created=type.create(c.world);check(created instanceof LivingEntity&&created.getClass().getName().equals("noppes.npcs.entity.EntityCustomNpc"),"actual Custom NPC entity available");npc=(LivingEntity)created;
  originalHealth=npc.getMaxHealth();data=npc.getClass().getField("modelData").get(npc);data.getClass().getMethod("setEntity",Identifier.class).invoke(data,new Identifier("bloodborne_rp",ids.get(index)));data.getClass().getField("simpleRender").setBoolean(data,false);
  getEntity=data.getClass().getMethod("getEntity",Class.forName("noppes.npcs.entity.EntityNPCInterface"));proxy=(RpMobEntity)getEntity.invoke(data,npc);normal=MobRegistry.TYPES.get(ids.get(index)).create(c.world);check(proxy.assetId().equals(ids.get(index)),"selected model "+ids.get(index));
  if(finalCorpses){var n=new net.minecraft.nbt.NbtCompound();n.putBoolean("bloodborne_rp_corpse",true);normal.readCustomDataFromNbt(n);check(normal.isCorpse()&&normal.getHealth()==1,"actual mob corpse state "+ids.get(index));}
  ticks=0;states.clear();rendered=false;c.setScreen(new Gallery());
 }
 private void tick(MinecraftClient c)throws Exception{
  if(++total<45)return;if(npc==null){prepare(c);return;}ticks++;npc.age++;normal.age++;if(finalCorpses){finalPose(c);return;}String state=ticks<16?"idle":ticks<31?"walk":ticks<46?"hurt":ticks<61?"attack":"death";
  npc.setVelocity(state.equals("walk")?new Vec3d(.08,0,0):Vec3d.ZERO);npc.hurtTime=state.equals("hurt")?5:0;npc.deathTime=state.equals("death")?1:0;npc.handSwingProgress=state.equals("attack")?.5f:0;
  if(rendered){check(proxy.isNpcModelProxy(),"actual renderer bridge active "+ids.get(index)+" "+state);String suffix=proxy.currentAnimationSuffix();if(states.add(state)){var row=new JsonObject();row.addProperty("model",ids.get(index));row.addProperty("state",state);row.addProperty("clip",suffix);row.addProperty("bones",((CatalogMobRenderer)c.getEntityRenderDispatcher().getRenderer(proxy)).getGeoModel().getAnimationProcessor().getRegisteredBones().size());checks.add(row);check(!suffix.isBlank()&&proxy.asset().clip(suffix)!=null,"available animation for "+ids.get(index)+" "+state);}}
  if(ticks==14||ticks==84)ScreenshotRecorder.saveScreenshot(c.runDirectory,"npc-"+ids.get(index)+(ticks==14?"-idle":"-death")+".png",c.getFramebuffer(),text->{});
  if(ticks<90)return;check(states.size()==5,"all five animation states rendered "+ids.get(index));check(npc.getMaxHealth()==originalHealth,"Custom NPC own max health unchanged "+ids.get(index));check(!data.getClass().getField("simpleRender").getBoolean(data),"saved simpleRender setting unchanged "+ids.get(index));
  if(++index==ids.size()){save("PASS","");failed=true;c.scheduleStop();}else prepare(c);
 }
 private String bonePose(MinecraftClient c){var b=new StringBuilder();for(var bone:((CatalogMobRenderer)c.getEntityRenderDispatcher().getRenderer(normal)).getGeoModel().getAnimationProcessor().getRegisteredBones()){b.append(bone.getName());for(float f:new float[]{bone.getRotX(),bone.getRotY(),bone.getRotZ(),bone.getPosX(),bone.getPosY(),bone.getPosZ(),bone.getScaleX(),bone.getScaleY(),bone.getScaleZ()})b.append(':').append(Math.round(f*1000));}return b.toString();}
 private void finalPose(MinecraftClient c)throws Exception{npc.deathTime=1;npc.hurtTime=0;npc.handSwingProgress=0;npc.setVelocity(Vec3d.ZERO);if(ticks==20||ticks==125)ScreenshotRecorder.saveScreenshot(c.runDirectory,"corpse-"+ids.get(index)+(ticks==20?"-early":"-final")+".png",c.getFramebuffer(),text->{});if(ticks==125)heldPose=bonePose(c);if(ticks<145)return;check(normal.isCorpse()&&normal.getHealth()==1,"corpse remains present "+ids.get(index));check(heldPose.equals(bonePose(c)),"death last frame remains held "+ids.get(index));check(proxy.isNpcModelProxy(),"Custom NPC corpse renderer remains active "+ids.get(index));var renderer=(CatalogMobRenderer)c.getEntityRenderDispatcher().getRenderer(normal);var model=renderer.getGeoModel().getBakedModel(renderer.getGeoModel().getModelResource(normal));if(!MobAnimationSelector.death(normal.asset()).isEmpty()){double min=dev.dreamwalker.bloodbornerp.client.CorpseFloor.minimum(model),lift=dev.dreamwalker.bloodbornerp.client.CorpseFloor.offset(normal,model,0);check(min+lift>=-.0001,"authored corpse stays above feet plane "+ids.get(index));var row=new JsonObject();row.addProperty("id",ids.get(index));row.addProperty("rawMinimumY",min);row.addProperty("floorLift",lift);checks.add(row);}normal.readCustomDataFromNbt(new net.minecraft.nbt.NbtCompound());check(dev.dreamwalker.bloodbornerp.client.CorpseFloor.offset(normal,model,0)==0,"living actor clears floor correction "+ids.get(index));if(++index==ids.size()){save("PASS","");failed=true;c.scheduleStop();}else prepare(c);}
 private final class Gallery extends Screen {
  Gallery(){super(Text.literal("NPC model QA"));}
  @Override public boolean shouldPause(){return false;}
  @Override public void render(DrawContext context,int mx,int my,float delta){if(failed||index>=ids.size())return;try{context.fill(0,0,width,height,0xff657582);context.drawCenteredTextWithShadow(textRenderer,Text.literal(ids.get(index)+" / "+ticks),width/2,10,0xffffff);context.drawCenteredTextWithShadow(textRenderer,Text.literal("Custom NPC"),width/3,30,0xffffff);context.drawCenteredTextWithShadow(textRenderer,Text.literal("RP entity"),width*2/3,30,0xffffff);int scale=(int)Math.min(60,(height-65)/Math.max(1,proxy.getHeight()));InventoryScreen.drawEntity(context,width/3,height-25,scale,-15,0,npc);InventoryScreen.drawEntity(context,width*2/3,height-25,scale,-15,0,normal);rendered=true;}catch(Throwable t){t.printStackTrace();save("FAIL",t.toString());failed=true;client.scheduleStop();}}
 }
 private synchronized void save(String status,String error){try{var j=new JsonObject();j.addProperty("status",status);j.addProperty("modelIndex",index);j.addProperty("modelCount",ids.size());j.add("checks",checks);if(!error.isBlank())j.addProperty("error",error);Files.writeString(out.resolve("client-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(j));}catch(Exception e){throw new RuntimeException(e);}}
}
