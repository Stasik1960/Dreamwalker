package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

/** Context-only input and bounded, server-confirmed visual data. */
public final class BuilderClient {
    private static boolean initialized;
    private static BuilderControls.Binding cancelSelection,undoChange,primaryModifier,heightModifier,reverseModifier,editKey,menuKey,pickKey;
    private static JsonObject latest=new JsonObject();
    private static BuilderScreen suspended;
    private static long sequence,lastViewSequence;private static int refreshTicks;
    private static List<Box> selections=List.of(),collisions=List.of(),sourceSelections=List.of();
    private static List<VisualLink> links=List.of();
    private static boolean showSelection=true,showCollision,showLinks=true,showRoot=true;
    private record VisualLink(Vec3d from,Vec3d to,String dimension,boolean both,boolean loaded) {}
    private BuilderClient(){}
    public static void initialize(){
        if(initialized)return;initialized=true;
        editKey=key("Действие 90009",InputUtil.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_LEFT);
        menuKey=key("Настройки 90009",InputUtil.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        pickKey=key("Следующий объект 90009",InputUtil.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        cancelSelection=key("Завершить выбор 90009",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_X);
        undoChange=key("Отмена 90009 (с модификатором режима)",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_Z);
        primaryModifier=key("Модификатор режима / отмены 90009",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_CONTROL);
        heightModifier=key("Модификатор шага высоты 90009",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_ALT);
        reverseModifier=key("Модификатор обратного действия 90009",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_SHIFT);
        BuilderControls.load();
        ClientPlayNetworking.registerGlobalReceiver(BuilderServer.VIEW,(client,handler,buf,sender)->{
            String raw;try{raw=buf.readString(BuilderServer.MAX_VIEW);}catch(RuntimeException malformed){return;}
            client.execute(()->{try{JsonObject next=JsonParser.parseString(raw).getAsJsonObject();long ack=next.has("sequence")?next.get("sequence").getAsLong():0;if(ack<lastViewSequence)return;lastViewSequence=ack;
                if(bool(next,"compact")){for(var e:next.entrySet())latest.add(e.getKey(),e.getValue());}else latest=next;
                cacheVisuals();if(client.currentScreen instanceof BuilderScreen screen)screen.accept(latest);else if(bool(next,"openMenu")){if(suspended!=null){var screen=suspended;suspended=null;screen.accept(latest);client.setScreen(screen);}else client.setScreen(new BuilderScreen(latest));}
            }catch(RuntimeException invalid){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(null,"UNASSIGNED","builder_client",null,"BUILDER_VIEW","Malformed bounded server view",invalid);}});
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null){BuilderControls.clearEvents();suspended=null;latest=new JsonObject();lastViewSequence=0;selections=collisions=sourceSelections=List.of();links=List.of();return;}
            if(!client.isWindowFocused()){BuilderControls.clearEvents();return;}
            while(editKey.wasPressed())if(context(client))performClick(editKey.reverseAtPress());
            while(menuKey.wasPressed())if(context(client))performMenu();
            while(pickKey.wasPressed())if(context(client))performPick();
            while(cancelSelection.wasPressed())if(context(client))send(request("quick_cancel"));
            while(undoChange.wasPressed())if(context(client)&&undoChange.primaryAtPress())send(request("undo"));
            if(++refreshTicks>=20){refreshTicks=0;if(BuildingTool.mainHeld(client.player)&&(latest.has("target")||client.currentScreen instanceof BuilderScreen))send(request(client.currentScreen instanceof BuilderScreen?"refresh":"hud_refresh"));}
        });
        HudRenderCallback.EVENT.register((draw,delta)->{
            MinecraftClient mc=MinecraftClient.getInstance();if(!context(mc))return;JsonObject target=object(latest,"target"),source=object(latest,"source");int w=Math.min(540,draw.getScaledWindowWidth()-20),left=(draw.getScaledWindowWidth()-w)/2,y=draw.getScaledWindowHeight()-86;
            List<String> rows=List.of("90009 · "+string(target,"name","Нет выбора")+" ["+string(target,"id","—")+"] · "+string(latest,"actionLabel","Выбор"),
                "Y: "+string(latest,"step","0.125")+" · угол "+string(target,"rotationStep","—")+"° · "+keyName(editKey)+": действие · "+keyName(reverseModifier)+": обратно",
                source.size()>0?"Источник: "+string(source,"name","")+" · целей/линий: "+links.size()+" · "+keyName(cancelSelection)+": завершить":keyName(menuKey)+": настройки · "+keyName(pickKey)+": другой объект · отмена "+string(latest,"undoCount","0"),
                bool(target,"busy")?"Анимация выполняется":bool(target,"pending")?"Ожидается безопасное закрытие / загрузка":target.size()>0&&!bool(target,"available")?"Выбранный UUID недоступен; выбор сохранён":string(latest,"message",""));
            draw.fill(left-5,y-4,left+w+5,y+46,0xC018181D);for(String row:rows){draw.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(row,w),left,y,0xFFE2D5B6);y+=11;}
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            MinecraftClient mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null||!BuildingTool.mainHeld(mc.player)||context.consumers()==null)return;
            var matrices=context.matrixStack();Vec3d camera=context.camera().getPos();matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);var linesBuffer=context.consumers().getBuffer(RenderLayer.getLines());
            if(showSelection){drawBoxes(matrices,linesBuffer,selections,camera,.95f,.65f,.12f);drawBoxes(matrices,linesBuffer,sourceSelections,camera,.2f,.7f,1f);}if(showCollision)drawBoxes(matrices,linesBuffer,collisions,camera,1f,.2f,.2f);
            if(showRoot){Vec3d root=vector(object(latest,"target").get("rootPoint"));if(root!=null&&root.squaredDistanceTo(camera)<=128*128)WorldRenderer.drawBox(matrices,linesBuffer,new Box(root,root).expand(.06),.9f,.9f,.9f,1);}
            if(showLinks)for(var link:links){if(!link.dimension().equals(mc.world.getRegistryKey().getValue().toString())||link.from().squaredDistanceTo(camera)>128*128)continue;Vec3d to=link.to();if(to.squaredDistanceTo(camera)>128*128)to=link.from().add(to.subtract(link.from()).normalize().multiply(64));drawLine(matrices,linesBuffer,link.from(),to,.3f,.8f,.9f);Vec3d d=to.subtract(link.from()).normalize(),side=new Vec3d(-d.z,0,d.x).multiply(.15),tail=to.subtract(d.multiply(.3));drawLine(matrices,linesBuffer,to,tail.add(side),.3f,.8f,.9f);drawLine(matrices,linesBuffer,to,tail.subtract(side),.3f,.8f,.9f);if(link.both()){Vec3d start=link.from(),head=start.add(d.multiply(.3));drawLine(matrices,linesBuffer,start,head.add(side),.3f,.8f,.9f);drawLine(matrices,linesBuffer,start,head.subtract(side),.3f,.8f,.9f);}if(!link.loaded())WorldRenderer.drawBox(matrices,linesBuffer,new Box(to,to).expand(.1),.9f,.6f,.2f,1);}
            matrices.pop();
        });
    }
    private static BuilderControls.Binding key(String label,InputUtil.Type type,int code){return BuilderControls.add(label,type,code);}
    private static boolean context(MinecraftClient mc){return mc.currentScreen==null&&BuildingTool.mainHeld(mc.player);}
    private static boolean down(BuilderControls.Binding binding){
        if(binding==null)return false;MinecraftClient mc=MinecraftClient.getInstance();var key=InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());int code=key.getCode();if(code<0)return false;long window=mc.getWindow().getHandle();if(key.getCategory()==InputUtil.Type.MOUSE)return GLFW.glfwGetMouseButton(window,code)==GLFW.GLFW_PRESS;
        if(InputUtil.isKeyPressed(window,code))return true;int paired=switch(code){case GLFW.GLFW_KEY_LEFT_CONTROL->GLFW.GLFW_KEY_RIGHT_CONTROL;case GLFW.GLFW_KEY_LEFT_ALT->GLFW.GLFW_KEY_RIGHT_ALT;case GLFW.GLFW_KEY_LEFT_SHIFT->GLFW.GLFW_KEY_RIGHT_SHIFT;default->-1;};return paired>=0&&InputUtil.isKeyPressed(window,paired);
    }
    private static void performClick(boolean reverse){JsonObject q=request("click");q.addProperty("reverse",reverse);send(q);}
    private static void performMenu(){send(request("open"));}
    private static void performPick(){send(request("pick"));}
    /** Native press events retain short clicks and modifiers after physical release. */
    public static void inputEvent(long window,InputUtil.Key key,int action,int modifiers){
        MinecraftClient mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle()||!initialized)return;
        BuilderControls.event(key,action);if(action!=GLFW.GLFW_PRESS||!context(mc))return;
        boolean primary=BuilderControls.modifierAtEvent(primaryModifier,modifiers),reverse=BuilderControls.modifierAtEvent(reverseModifier,modifiers);
        for(var binding:List.of(editKey,menuKey,pickKey,cancelSelection,undoChange))if(binding.matches(key)&&(binding!=undoChange||primary))binding.press(primary,reverse);
    }
    public static void selectInWorld(BuilderScreen screen){suspended=screen;MinecraftClient.getInstance().setScreen(null);}
    /** Consume matching vanilla actions before they can open another screen; modifier movement stays intact. */
    public static void beforeVanillaInput(){MinecraftClient mc=MinecraftClient.getInstance();if(!context(mc))return;for(var ours:List.of(editKey,menuKey,pickKey,cancelSelection,undoChange)){if(!ours.hasPress()&&!down(ours)||ours==undoChange&&!ours.hasPress()&&!down(primaryModifier))continue;for(var nativeKey:mc.options.allKeys)if(nativeKey.getBoundKeyTranslationKey().equals(ours.getBoundKeyTranslationKey())){while(nativeKey.wasPressed()){}nativeKey.setPressed(false);}}}
    public static void openReportFolder(){MinecraftClient mc=MinecraftClient.getInstance();java.nio.file.Path root=mc.runDirectory.toPath().toAbsolutePath().normalize(),folder=root.resolve("diagnostics").normalize();try{if(!folder.startsWith(root))throw new IllegalStateException("Invalid local report path");java.nio.file.Files.createDirectories(folder);if(!folder.toRealPath().startsWith(root.toRealPath()))throw new IllegalStateException("Report directory escapes local game directory");net.minecraft.util.Util.getOperatingSystem().open(folder.toFile());if(mc.player!=null)mc.player.sendMessage(net.minecraft.text.Text.literal("90009: локальная папка отчётов клиента: "+folder+". Серверный путь не открывался."),false);}catch(RuntimeException|java.io.IOException failure){if(mc.player!=null)mc.player.sendMessage(net.minecraft.text.Text.literal("90009: не удалось открыть локальную папку diagnostics клиента: "+folder),false);}}
    public static boolean attack(){MinecraftClient mc=MinecraftClient.getInstance();return mc.currentScreen==null&&BuildingTool.isHeld(mc.player);}
    public static boolean use(){return attack();}
    public static boolean pick(){return attack();}
    public static boolean scroll(double amount){MinecraftClient mc=MinecraftClient.getInstance();if(amount==0||!context(mc))return false;boolean mode=down(primaryModifier),height=down(heightModifier);if(!mode&&!height)return false;JsonObject q=request(mode?"quick_cycle":"quick_step");q.addProperty("direction",amount>0?1:-1);send(q);return true;}
    public static JsonObject request(String op){JsonObject q=new JsonObject();q.addProperty("op",op);q.addProperty("requestId",UUID.randomUUID().toString());q.addProperty("expectedLampVersion",string(latest,"lampVersion",""));JsonObject t=object(latest,"target"),r=object(latest,"rule");if(t.has("instance")){q.addProperty("expectedTarget",string(t,"instance",""));q.addProperty("expectedVersion",string(t,"version",""));}if(r.has("id")){q.addProperty("expectedRule",string(r,"id",""));q.addProperty("expectedRuleVersion",string(r,"version","none"));}return q;}
    public static void send(JsonObject q){if(!ClientPlayNetworking.canSend(BuilderServer.REQUEST))return;q.addProperty("sequence",++sequence);String json=q.toString();if(json.length()>BuilderServer.MAX_REQUEST)return;var buf=PacketByteBufs.create();buf.writeString(json,BuilderServer.MAX_REQUEST);ClientPlayNetworking.send(BuilderServer.REQUEST,buf);}
    public static String keyName(BuilderControls.Binding key){return key.getBoundKeyLocalizedText().getString();}
    public static List<String> controls(){return List.of("Действие: "+keyName(editKey),"Настройки: "+keyName(menuKey),"Перебор кандидатов: "+keyName(pickKey),"Режим / отмена: "+keyName(primaryModifier),"Шаг высоты: "+keyName(heightModifier),"Обратно: "+keyName(reverseModifier),"Отмена: "+keyName(primaryModifier)+" + "+keyName(undoChange),"Завершить выбор: "+keyName(cancelSelection));}
    public static List<String> conflicts(){var mc=MinecraftClient.getInstance();List<String> result=new ArrayList<>();for(var ours:BuilderControls.bindings()){for(var other:mc.options.allKeys)if(ours.getBoundKeyTranslationKey().equals(other.getBoundKeyTranslationKey())&&!ours.isUnbound()){result.add(keyName(ours)+": "+net.minecraft.text.Text.translatable(other.getTranslationKey()).getString());if(result.size()>=16)return result;}for(var other:BuilderControls.bindings())if(other!=ours&&ours.getBoundKeyTranslationKey().equals(other.getBoundKeyTranslationKey())&&!ours.isUnbound()){result.add(keyName(ours)+": "+other.getTranslationKey());if(result.size()>=16)return result;}}return result;}
    public static String toggleVisual(String which){switch(which){case "selection"->showSelection=!showSelection;case "collision"->showCollision=!showCollision;case "links"->showLinks=!showLinks;case "root"->showRoot=!showRoot;}return visualLabel(which);}
    public static String visualLabel(String which){return switch(which){case "selection"->"Выбор (жёлтый): "+(showSelection?"вкл":"выкл");case "collision"->"Коллизия игрока (красный): "+(showCollision?"вкл":"выкл");case "links"->"Связи (голубой): "+(showLinks?"вкл":"выкл");default->"Корень / точка монтажа: "+(showRoot?"вкл":"выкл");};}
    private static void cacheVisuals(){selections=readBoxes(object(latest,"target"),"selectionBoxes");collisions=readBoxes(object(latest,"target"),"collisionBoxes");sourceSelections=readBoxes(object(latest,"source"),"selectionBoxes");List<VisualLink> data=new ArrayList<>();for(var e:array(latest,"links")){if(data.size()>=64)break;JsonObject l=e.getAsJsonObject();Vec3d a=vector(l.get("from")),b=vector(l.get("to"));if(a!=null&&b!=null)data.add(new VisualLink(a,b,string(l,"dimension",""),bool(l,"both"),bool(l,"loaded")));}links=List.copyOf(data);}
    private static List<Box> readBoxes(JsonObject o,String field){List<Box> data=new ArrayList<>();for(var e:array(o,field)){if(data.size()>=128)break;JsonArray a=e.getAsJsonArray();if(a.size()!=6)continue;double[] v=new double[6];boolean valid=true;for(int i=0;i<6;i++){v[i]=a.get(i).getAsDouble();valid&=Double.isFinite(v[i]);}if(valid)data.add(new Box(v[0],v[1],v[2],v[3],v[4],v[5]));}return List.copyOf(data);}
    private static Vec3d vector(JsonElement e){if(e==null||!e.isJsonArray()||e.getAsJsonArray().size()!=3)return null;var a=e.getAsJsonArray();Vec3d v=new Vec3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)?v:null;}
    private static void drawBoxes(MatrixStack matrices,VertexConsumer lines,List<Box> boxes,Vec3d camera,float r,float g,float b){for(Box box:boxes)if(box.getCenter().squaredDistanceTo(camera)<=128*128)WorldRenderer.drawBox(matrices,lines,box,r,g,b,1);}
    private static void drawLine(MatrixStack matrices,VertexConsumer lines,Vec3d from,Vec3d to,float r,float g,float b){Vec3d n=to.subtract(from).normalize();var entry=matrices.peek();lines.vertex(entry.getPositionMatrix(),(float)from.x,(float)from.y,(float)from.z).color(r,g,b,1).normal(entry.getNormalMatrix(),(float)n.x,(float)n.y,(float)n.z).next();lines.vertex(entry.getPositionMatrix(),(float)to.x,(float)to.y,(float)to.z).color(r,g,b,1).normal(entry.getNormalMatrix(),(float)n.x,(float)n.y,(float)n.z).next();}
    private static JsonObject object(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static String string(JsonObject o,String k,String fallback){return o.has(k)&&o.get(k).isJsonPrimitive()?o.get(k).getAsString():fallback;}
    private static boolean bool(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonPrimitive()&&o.get(k).getAsBoolean();}
}
