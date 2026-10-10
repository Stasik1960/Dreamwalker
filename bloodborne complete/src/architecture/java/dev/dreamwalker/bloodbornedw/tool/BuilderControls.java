package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Independent contextual bindings do not replace vanilla's one-binding-per-key map. */
public final class BuilderControls {
    public static final class Binding {
        private final String name,defaultKey;private String key;private boolean eventHeld;
        private record Press(boolean primary,boolean reverse) {}
        private final Deque<Press> presses=new ArrayDeque<>();private Press last=new Press(false,false);
        private Binding(String name,InputUtil.Type type,int code){this.name=name;defaultKey=type.createFromCode(code).getTranslationKey();key=defaultKey;}
        public String getTranslationKey(){return name;}
        public String getBoundKeyTranslationKey(){return key;}
        public Text getBoundKeyLocalizedText(){return InputUtil.fromTranslationKey(key).getLocalizedText();}
        public boolean isUnbound(){return InputUtil.fromTranslationKey(key).getCode()<0;}
        public boolean wasPressed(){Press press=presses.pollFirst();if(press==null)return false;last=press;return true;}
        public boolean hasPress(){return !presses.isEmpty();}
        public boolean primaryAtPress(){return last.primary;}
        public boolean reverseAtPress(){return last.reverse;}
        boolean matches(InputUtil.Key value){return !isUnbound()&&key.equals(value.getTranslationKey());}
        void press(boolean primary,boolean reverse){if(presses.size()<8)presses.addLast(new Press(primary,reverse));}
        public void bind(InputUtil.Key value){key=value.getTranslationKey();save();}
    }
    private static final List<Binding> BINDINGS=new ArrayList<>();
    private static boolean loaded;private static String error="";
    private BuilderControls(){}
    static Binding add(String label,InputUtil.Type type,int code){Binding binding=new Binding(label,type,code);BINDINGS.add(binding);return binding;}
    static void load(){if(loaded)return;loaded=true;Path file=path();if(!Files.isRegularFile(file))return;try{JsonObject data=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();for(var b:BINDINGS)if(data.has(b.name)){String value=data.get(b.name).getAsString();InputUtil.fromTranslationKey(value);b.key=value;}}catch(Exception invalid){error="Настройки клавиш не прочитаны; используются доступные значения по умолчанию.";}}
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("bloodborne-builder-controls.json");}
    private static void save(){try{JsonObject data=new JsonObject();for(var b:BINDINGS)data.addProperty(b.name,b.key);Files.createDirectories(path().getParent());Files.writeString(path(),new GsonBuilder().setPrettyPrinting().create().toJson(data),StandardCharsets.UTF_8);error="";}catch(Exception failure){error="Изменено для текущего клиента; запись файла настроек не выполнена.";}}
    public static List<Binding> bindings(){return List.copyOf(BINDINGS);}
    static void event(InputUtil.Key key,int action){for(var binding:BINDINGS)if(binding.matches(key))binding.eventHeld=action!=GLFW.GLFW_RELEASE;}
    static void clearEvents(){for(var binding:BINDINGS){binding.eventHeld=false;binding.presses.clear();}}
    static boolean modifierAtEvent(Binding binding,int modifiers){
        if(binding==null)return false;var key=InputUtil.fromTranslationKey(binding.key);int code=key.getCode();
        if(key.getCategory()==InputUtil.Type.KEYSYM){int mask=switch(code){case GLFW.GLFW_KEY_LEFT_CONTROL,GLFW.GLFW_KEY_RIGHT_CONTROL->GLFW.GLFW_MOD_CONTROL;case GLFW.GLFW_KEY_LEFT_ALT,GLFW.GLFW_KEY_RIGHT_ALT->GLFW.GLFW_MOD_ALT;case GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_KEY_RIGHT_SHIFT->GLFW.GLFW_MOD_SHIFT;default->0;};if(mask!=0)return (modifiers&mask)!=0;}
        return binding.eventHeld||down(binding);
    }
    public static String error(){return error;}
    public static void reset(){for(var b:BINDINGS)b.key=b.defaultKey;save();}
    static boolean down(Binding binding){if(binding==null)return false;var key=InputUtil.fromTranslationKey(binding.key);int code=key.getCode();if(code<0)return false;long window=MinecraftClient.getInstance().getWindow().getHandle();if(key.getCategory()==InputUtil.Type.MOUSE)return GLFW.glfwGetMouseButton(window,code)==GLFW.GLFW_PRESS;if(InputUtil.isKeyPressed(window,code))return true;int paired=switch(code){case GLFW.GLFW_KEY_LEFT_CONTROL->GLFW.GLFW_KEY_RIGHT_CONTROL;case GLFW.GLFW_KEY_LEFT_ALT->GLFW.GLFW_KEY_RIGHT_ALT;case GLFW.GLFW_KEY_LEFT_SHIFT->GLFW.GLFW_KEY_RIGHT_SHIFT;default->-1;};return paired>=0&&InputUtil.isKeyPressed(window,paired);}
}
