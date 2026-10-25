package dev.dreamwalker.bloodbornedw.composite;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
/** Only recently touched lamps animate; idle geometry keeps its existing cache. */
final class LanternMotionClient {
 private static final Map<Long,Long> ACTIVE=new HashMap<>();
 static void initialize(){ClientPlayNetworking.registerGlobalReceiver(LanternMotion.PACKET,(client,handler,buf,response)->{var dimension=buf.readIdentifier();var root=buf.readBlockPos();if(buf.isReadable())throw new IllegalArgumentException("Lantern sway packet");client.execute(()->{if(client.world!=null&&client.world.getRegistryKey().getValue().equals(dimension)){if(ACTIVE.size()>256)ACTIVE.clear();ACTIVE.put(root.asLong(),System.nanoTime());}});});net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c)->ACTIVE.clear());}
 static boolean render(CompositeBlockEntity e,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int overlay){
  if(e.resident()==null||!LanternMotion.lantern(e.resident().registryId()))return false;var time=ACTIVE.get(e.getPos().asLong());if(time==null)return false;double age=(System.nanoTime()-time)/1e9;if(age>2.6){ACTIVE.remove(e.getPos().asLong());return false;}
  var state=e.getCachedState();var block=(CompositeRootBlock)state.getBlock();var bounds=block.spec.variant(state).visible();double top=bounds==null?1:bounds.to().y()/16;var payload=e.sourceRenderPayload();top+=CompositeSourceShift.local(payload).y()+payload.getDouble("MountY");float angle=(float)(Math.sin(age*11)*7*Math.exp(-age*1.5));
  matrices.push();matrices.translate(.5,top,.5);matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));matrices.translate(-.5,-top,-.5);CompositeWorldMesh.render(e,block,state,payload,matrices,consumers,overlay);matrices.pop();return true;
 }
}
