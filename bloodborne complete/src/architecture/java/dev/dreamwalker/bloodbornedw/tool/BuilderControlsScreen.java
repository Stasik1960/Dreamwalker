package dev.dreamwalker.bloodbornedw.tool;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

final class BuilderControlsScreen extends Screen {
    private final Screen parent;private BuilderControls.Binding awaiting;private int page,conflictPage;
    BuilderControlsScreen(Screen parent){super(Text.literal("Управление 90009"));this.parent=parent;}
    @Override public boolean shouldPause(){return false;}
    private int footerTop(){return Math.max(72,height-116);}
    private int bindingsPerPage(){return Math.max(1,(footerTop()-44)/24);}
    @Override protected void init(){int y=36,w=Math.min(440,width-24),x=(width-w)/2,count=bindingsPerPage();var bindings=BuilderControls.bindings();page=Math.min(page,Math.max(0,(bindings.size()-1)/count));for(var b:bindings.subList(page*count,Math.min(bindings.size(),(page+1)*count))){String label=b.getTranslationKey()+": "+(awaiting==b?"нажмите клавишу / кнопку":b.getBoundKeyLocalizedText().getString());addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,w-12)),button->{awaiting=b;clearAndInit();}).dimensions(x,y,w,20).tooltip(Tooltip.of(Text.literal(label))).build());y+=24;}
        if(bindings.size()>count){addDrawableChild(ButtonWidget.builder(Text.literal("←"),button->{page=Math.max(0,page-1);clearAndInit();}).dimensions(x,height-54,30,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("→"),button->{page=Math.min((bindings.size()-1)/count,page+1);clearAndInit();}).dimensions(x+w-30,height-54,30,20).build());}
        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить"),button->{BuilderControls.reset();clearAndInit();}).dimensions(x,height-28,w/2-4,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),button->close()).dimensions(x+w/2+4,height-28,w/2-4,20).build());}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(awaiting!=null){if(key!=GLFW.GLFW_KEY_ESCAPE)awaiting.bind(InputUtil.fromKeyCode(key,scan));awaiting=null;clearAndInit();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseClicked(double x,double y,int button){if(awaiting!=null){awaiting.bind(InputUtil.Type.MOUSE.createFromCode(button));awaiting=null;clearAndInit();return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double amount){if(awaiting==null&&y>=footerTop()&&y<height-58){int pages=Math.max(1,(BuilderClient.conflicts().size()+2)/3);conflictPage=Math.max(0,Math.min(pages-1,conflictPage+(amount>0?-1:1)));return true;}return super.mouseScrolled(x,y,amount);}
    @Override public void close(){client.setScreen(parent);}
    @Override public void render(DrawContext draw,int mx,int my,float delta){renderBackground(draw);draw.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xE2D5B6);int y=footerTop();draw.drawCenteredTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth("Комбинации действуют только с инструментом и вне чата/форм.",width-24)),width/2,y,0xC5C5C5);y+=12;var conflicts=BuilderClient.conflicts();int conflictPages=Math.max(1,(conflicts.size()+2)/3);conflictPage=Math.min(conflictPage,conflictPages-1);for(String conflict:conflicts.subList(Math.min(conflicts.size(),conflictPage*3),Math.min(conflicts.size(),(conflictPage+1)*3))){draw.drawTextWithShadow(textRenderer,textRenderer.trimToWidth("Общий ключ: "+conflict,width-24),12,y,0xE1B272);y+=11;}String hint="Клавиши "+(page+1)+"/"+Math.max(1,(BuilderControls.bindings().size()+bindingsPerPage()-1)/bindingsPerPage())+" · совпадения "+(conflictPage+1)+"/"+conflictPages+" · колесо";draw.drawCenteredTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(BuilderControls.error().isBlank()?hint:BuilderControls.error(),width-24)),width/2,height-66,BuilderControls.error().isBlank()?0xC5C5C5:0xFF8888);super.render(draw,mx,my,delta);}
}
