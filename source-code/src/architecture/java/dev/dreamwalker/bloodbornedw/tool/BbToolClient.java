package dev.dreamwalker.bloodbornedw.tool;

import java.util.UUID;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class BbToolClient {
    private BbToolClient(){}
    public static void initialize(){NoCollideKeyClient.initialize();ClientPlayNetworking.registerGlobalReceiver(BbToolService.OPEN,(client,handler,buf,response)->{
        try{UUID token=buf.readUuid();String target=buf.readString(256),commands=buf.readString(ToolCommands.MAX_TEXT);int personal=buf.readVarInt(),global=buf.readVarInt(),used=buf.readVarInt();
            if(buf.isReadable()||personal<1||personal>ToolCommands.MAX_USES||global<1||global>ToolCommands.MAX_USES||used<0||used>global)return;
            client.execute(()->client.setScreen(new CommandScreen(token,target,commands,personal,global,used)));
        }catch(RuntimeException malformed){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Invalid BB command editor response rejected");}
    });}
    public static final class CommandScreen extends Screen {
        private final UUID token;private final String target;private final int used;
        private String commandText,personalText,globalText,error="";
        private EditBoxWidget commands;private TextFieldWidget personal,global;private int left,contentWidth,editorBottom,personalY,globalY;
        public CommandScreen(UUID token,String target,String text,int personal,int global,int used){super(Text.literal("Команды объекта"));this.token=token;this.target=target;commandText=text;personalText=Integer.toString(personal);globalText=Integer.toString(global);this.used=used;}
        @Override public boolean shouldPause(){return false;}
        @Override protected void init(){
            boolean compact=height<216;
            contentWidth=Math.min(500,width-24);left=(width-contentWidth)/2;int editorHeight=Math.max(compact?24:44,height-(compact?148:172));editorBottom=48+editorHeight;
            personalY=editorBottom+(compact?10:14);globalY=editorBottom+(compact?36:48);
            commands=new EditBoxWidget(textRenderer,left,48,contentWidth,editorHeight,Text.literal("Одна команда в строке"),Text.literal("Команды"));commands.setMaxLength(ToolCommands.MAX_TEXT);commands.setText(commandText);commands.setChangeListener(value->commandText=value);addDrawableChild(commands);
            personal=field(personalY,personalText);personal.setChangedListener(value->personalText=value);
            global=field(globalY,globalText);global.setChangedListener(value->globalText=value);
            addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"),button->save()).dimensions(width/2-105,height-27,100,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"),button->close()).dimensions(width/2+5,height-27,100,20).build());setInitialFocus(commands);
        }
        private TextFieldWidget field(int y,String text){var field=new TextFieldWidget(textRenderer,left+contentWidth-82,y,82,20,Text.literal("Лимит 1–999999"));field.setMaxLength(6);field.setTextPredicate(value->value.matches("[0-9]*"));field.setText(text);addDrawableChild(field);return field;}
        private void label(DrawContext context,String value,int y){int lineY=y;for(var line:textRenderer.wrapLines(Text.literal(value),contentWidth-94)){context.drawTextWithShadow(textRenderer,line,left,lineY,0xDDDDDD);lineY+=10;}}
        @Override public void tick(){commands.tick();personal.tick();global.tick();}
        @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){renderBackground(context);context.drawCenteredTextWithShadow(textRenderer,title,width/2,9,0xFFFFFF);context.drawCenteredTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(target,contentWidth)),width/2,23,0xAAAAAA);
            context.drawTextWithShadow(textRenderer,Text.literal("Одна команда в строке; @s — нажавший игрок"),left,36,0xBBBBBB);
            label(context,"Срабатывания персонально для одного игрока",personalY);label(context,"Общее количество срабатываний для всех игроков",globalY);
            context.drawCenteredTextWithShadow(textRenderer,Text.literal(error.isEmpty()?"Лимиты 1–999999. Использовано общих: "+used:error),width/2,height-42,error.isEmpty()?0xAAAAAA:0xFF7777);super.render(context,mouseX,mouseY,delta);
        }
        private void save(){try{int per=Integer.parseInt(personalText),all=Integer.parseInt(globalText);if(per<1||per>ToolCommands.MAX_USES||all<1||all>ToolCommands.MAX_USES){error="Лимиты должны быть от 1 до 999999";return;}ToolCommands.parse(commandText);
            var out=PacketByteBufs.create();out.writeUuid(token);out.writeString(commandText,ToolCommands.MAX_TEXT);out.writeVarInt(per);out.writeVarInt(all);ClientPlayNetworking.send(BbToolService.SAVE,out);close();
        }catch(IllegalArgumentException invalid){error="Проверьте команды и лимиты 1–999999";}}
    }
}
