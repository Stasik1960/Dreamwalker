package dev.dreamwalker.bloodbornerp.client.lamp;

import dev.dreamwalker.bloodbornerp.lamp.LampService;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class LampClient {
 private LampClient(){}
 public static void initialize(){ClientPlayNetworking.registerGlobalReceiver(LampService.LIST_PACKET,(client,handler,buf,response)->{if(buf.readableBytes()<9)return;long token=buf.readLong();int count=buf.readVarInt();if(count<0||count>256)return;List<Entry> entries=new ArrayList<>();for(int i=0;i<count;i++){if(buf.readableBytes()<16)return;entries.add(new Entry(buf.readUuid(),buf.readString(64)));}client.execute(()->{if(token==0&&entries.isEmpty()){if(client.currentScreen instanceof LampScreen)client.setScreen(null);}else client.setScreen(new LampScreen(token,entries));});});}
 private record Entry(UUID id,String name){}
 private static final class LampScreen extends Screen {
  private final long token;private final List<Entry> entries;private int page;
  LampScreen(long token,List<Entry> entries){super(Text.translatable("screen.bloodborne_rp.lamp.title"));this.token=token;this.entries=entries;}
  @Override protected void init(){
   int start=page*6,end=Math.min(entries.size(),start+6),y=height/2-80;
   for(Entry entry:entries.subList(start,end)){addDrawableChild(ButtonWidget.builder(Text.literal(entry.name),(button)->travel(entry.id)).dimensions(width/2-100,y,200,20).build());y+=24;}
   if(page>0)addDrawableChild(ButtonWidget.builder(Text.literal("<"),button->{page--;clearAndInit();}).dimensions(width/2-100,height/2+75,45,20).build());
   if(end<entries.size())addDrawableChild(ButtonWidget.builder(Text.literal(">"),button->{page++;clearAndInit();}).dimensions(width/2+55,height/2+75,45,20).build());
  }
  private void travel(UUID id){PacketByteBuf out=new PacketByteBuf(Unpooled.buffer(24));out.writeUuid(id);out.writeLong(token);ClientPlayNetworking.send(LampService.TRAVEL_PACKET,out);close();}
 }
}
