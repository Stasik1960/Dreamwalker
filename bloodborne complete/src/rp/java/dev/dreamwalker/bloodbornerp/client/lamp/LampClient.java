package dev.dreamwalker.bloodbornerp.client.lamp;

import dev.dreamwalker.bloodbornerp.lamp.LampService;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class LampClient {
 private LampClient(){}
 public static void initialize(){ClientPlayNetworking.registerGlobalReceiver(LampService.LIST_PACKET,(client,handler,buf,response)->{
  dev.dreamwalker.bloodbornerp.client.RpClientDiagnostics.network("receive",LampService.LIST_PACKET.toString(),buf.readableBytes());
  try{
   if(buf.readableBytes()<9||buf.readableBytes()>65536){fault("lamp_list_invalid_payload_bound");return;}
   long token=buf.readLong();int count=buf.readVarInt();if(count<0||count>256){fault("lamp_list_invalid_destination_count");return;}
   List<Entry> entries=new ArrayList<>();Set<UUID> ids=new HashSet<>();
   for(int i=0;i<count;i++){UUID id=buf.readUuid();String name=buf.readString(64);if(name.isBlank()||!ids.add(id)){fault("lamp_list_invalid_duplicate_destination");return;}entries.add(new Entry(id,name));}
   String source=buf.isReadable()?buf.readString(64):"Охотничий фонарь";
   String message=buf.isReadable()?buf.readString(256):(entries.isEmpty()?"Нет доступных назначений.":"Выберите назначение.");
   int cooldown=buf.isReadable()?buf.readVarInt():0;boolean pending=buf.isReadable()&&buf.readBoolean();
   if(cooldown<0||cooldown>12000||buf.isReadable()){fault("lamp_list_invalid_status_or_trailing_bytes");return;}
   client.execute(()->{if(token==0&&entries.isEmpty()&&message.isBlank()){if(client.currentScreen instanceof LampScreen)client.setScreen(null);}else client.setScreen(new LampScreen(token,entries,source,message,cooldown,pending));});
  }catch(RuntimeException failure){dev.dreamwalker.bloodbornerp.client.RpClientDiagnostics.error(null,"malformed_lamp_packet","lamp_list_atomic_decode_refused",failure);}
 });}
 private static void fault(String reason){dev.dreamwalker.bloodbornerp.client.RpClientDiagnostics.error(null,"malformed_lamp_packet",reason,null);}
 private record Entry(UUID id,String name){}
 /** No world pause. Source name is the exact clicked node, line names/UUIDs stay out of the player menu. */
 public static final class LampScreen extends Screen {
  private final long token;private final List<Entry> entries;private final String source,message;private final boolean pending;
  private int page,cooldown;private boolean inFlight;private final List<ButtonWidget> destinations=new ArrayList<>();
  private int pageSize=6,listTop,listBottom;
  public LampScreen(long token,List<Entry> entries,String source,String message,int cooldown,boolean pending){super(Text.literal("Охотничий фонарь · "+source));this.token=token;this.entries=List.copyOf(entries);this.source=source;this.message=message;this.cooldown=cooldown;this.pending=pending;}
  @Override public boolean shouldPause(){return false;}
  @Override protected void init(){
   destinations.clear();int w=Math.min(320,Math.max(160,width-40));listTop=70;listBottom=height-65;pageSize=Math.max(1,(listBottom-listTop)/24);page=Math.max(0,Math.min(page,Math.max(0,(entries.size()-1)/pageSize)));
   int start=page*pageSize,end=Math.min(entries.size(),start+pageSize),y=listTop;
   for(Entry e:entries.subList(start,end)){ButtonWidget b=ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(e.name,w-16)),button->travel(e.id)).dimensions((width-w)/2,y,w,20).tooltip(Tooltip.of(Text.literal(e.name))).build();b.active=!pending&&!inFlight&&cooldown==0;destinations.add(b);addDrawableChild(b);y+=24;}
   if(page>0)addDrawableChild(ButtonWidget.builder(Text.literal("←"),button->{page--;clearAndInit();}).dimensions(width/2-110,height-48,45,20).build());
   if(end<entries.size())addDrawableChild(ButtonWidget.builder(Text.literal("→"),button->{page++;clearAndInit();}).dimensions(width/2+65,height-48,45,20).build());
   addDrawableChild(ButtonWidget.builder(Text.literal(pending?"Отменить ожидание":"Закрыть"),button->close()).dimensions(width/2-60,height-25,120,20).build());
  }
  @Override public void tick(){if(cooldown>0)cooldown--;for(ButtonWidget b:destinations)b.active=!pending&&!inFlight&&cooldown==0;}
  @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
   renderBackground(context);context.drawCenteredTextWithShadow(textRenderer,Text.literal("Фонарь: "+source),width/2,15,0xE2CAA0);
   String status=pending?"Загружается назначение… Esc отменяет переход.":cooldown>0?String.format(Locale.ROOT,"До следующего перехода: %.1f сек.",cooldown/20.0):message;
   int y=32;for(var line:textRenderer.wrapLines(Text.literal(status),Math.max(100,width-30))){context.drawCenteredTextWithShadow(textRenderer,line,width/2,y,0xDDDDDD);y+=10;if(y>=62)break;}
   if(entries.isEmpty())context.drawCenteredTextWithShadow(textRenderer,Text.literal("Нет настроенных исходящих назначений."),width/2,listTop+18,0xCCCCCC);
   if(entries.size()>pageSize)context.drawCenteredTextWithShadow(textRenderer,Text.literal("Страница "+(page+1)+" / "+Math.max(1,(entries.size()+pageSize-1)/pageSize)),width/2,height-44,0xAAAAAA);
   super.render(context,mouseX,mouseY,delta);
  }
  @Override public boolean mouseScrolled(double x,double y,double amount){if(amount!=0&&entries.size()>pageSize){int next=Math.max(0,Math.min((entries.size()-1)/pageSize,page+(amount<0?1:-1)));if(next!=page){page=next;clearAndInit();}return true;}return super.mouseScrolled(x,y,amount);}
  private void travel(UUID id){if(pending||inFlight||cooldown>0||token==0)return;inFlight=true;PacketByteBuf out=new PacketByteBuf(Unpooled.buffer(24));out.writeUuid(id);out.writeLong(token);dev.dreamwalker.bloodbornerp.client.RpClientDiagnostics.network("send",LampService.TRAVEL_PACKET.toString(),out.readableBytes());ClientPlayNetworking.send(LampService.TRAVEL_PACKET,out);for(ButtonWidget b:destinations)b.active=false;}
  @Override public void close(){if(token!=0){PacketByteBuf out=new PacketByteBuf(Unpooled.buffer(8));out.writeLong(token);dev.dreamwalker.bloodbornerp.client.RpClientDiagnostics.network("send",LampService.CANCEL_PACKET.toString(),8);ClientPlayNetworking.send(LampService.CANCEL_PACKET,out);}super.close();}
 }
}