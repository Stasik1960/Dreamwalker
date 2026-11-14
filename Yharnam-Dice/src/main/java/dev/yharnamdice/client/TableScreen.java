package dev.yharnamdice.client;

import dev.yharnamdice.DiceMod;
import dev.yharnamdice.Protocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class TableScreen extends Screen {
    private static final int PANEL = 188;
    private static final int GOLD=0xffe4c698, DIM=0xffbea28a;
    private Protocol.View view;
    private final List<Control> controls = new ArrayList<>();
    private int page;
    private boolean choosingBag;

    private record Control(int x, int y, int w, int h, int action, int argument, boolean enabled) {
        boolean hit(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x+w && mouseY >= y && mouseY < y+h;
        }
    }

    TableScreen(Protocol.View view) {
        super(Text.translatable("block.yharnam_dice.dice_table"));
        this.view=view;
        choosingBag=view.bagSlot()<0;
    }

    boolean sameTable(Protocol.View other) { return other.table() == view.table(); }

    void update(Protocol.View view) {
        if (this.view.bagSlot()!=view.bagSlot()) choosingBag=view.bagSlot()<0;
        this.view=view;
    }

    private static Text label(String key, Object... args) {
        return Text.translatable("yharnam_dice.table."+key,args);
    }

    private Text status() {
        String note = view.note();
        if (note.isBlank()) return Text.empty();
        int split=note.indexOf(':');
        return split < 0 ? label(note,view.turn()) : label(note.substring(0,split),note.substring(split+1));
    }

    @Override public void render(DrawContext c, int mx, int my, float tick) {
        controls.clear();
        int left = width-PANEL-9;
        if (left<5) left=5;
        boolean bags = !view.started() && choosingBag;
        page = Math.min(page,Math.max(0,(view.bags().size()-1)/4));
        int count = Math.min(4,Math.max(0,view.bags().size()-4*page));
        int content = bags ? Math.max(1,count)*19 + (view.bags().size()>4 || view.bagSlot()>=0 ? 19 : 0)
                : view.started() ? 40 : 55;
        int h = 57 + content + view.board().size()*11;
        int top = Math.max(4,height-h-9);
        int w = Math.min(PANEL,width-left-5);
        c.fill(left,top,left+w,top+h,0xe51a0f14);
        c.fill(left,top,left+w,top+1,0xff9b7352);
        c.fill(left,top+h-1,left+w,top+h,0xff604436);
        c.fill(left,top,left+1,top+h,0xff543f37);
        c.fill(left+w-1,top,left+w,top+h,0xff543f37);
        String heading = view.title().isBlank() ? label("free").getString() : view.title();
        c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(heading,w-16),left+8,top+7,GOLD);
        c.drawText(textRenderer,textRenderer.trimToWidth(status().getString(),w-16),left+8,top+21,DIM,false);
        int y=top+34;
        for (String line : view.board()) {
            c.drawText(textRenderer,textRenderer.trimToWidth(line,w-16),left+8,y,
                    line.startsWith("›")?0xfff3d0a3:0xffbdb0aa,false);
            y+=11;
        }
        if (bags) {
            if (count == 0) {
                c.drawText(textRenderer,label("no_bags"),left+8,y+4,DIM,false);
                y+=20;
            } else {
                for (int i=0;i<count;i++) {
                    Protocol.Bag bag=view.bags().get(4*page+i);
                    button(c,mx,my,left+8,y,w-16,17,Text.literal(bag.name()),Protocol.BAG,bag.slot(),true);
                    y+=19;
                }
            }
            int pages=(view.bags().size()+3)/4;
            if (pages>1) {
                button(c,mx,my,left+8,y,21,17,Text.literal("‹"),100,-1,page>0);
                button(c,mx,my,left+32,y,21,17,Text.literal("›"),100,1,page+1<pages);
            }
            if (view.bagSlot()>=0) button(c,mx,my,left+w-87,y,79,17,label("back"),101,0,true);
            y+=20;
        } else if (!view.started()) {
            for (int i=0; i<view.skins().length; i++) {
                int value=i<view.values().length ? view.values()[i] : 0;
                dieButton(c,mx,my,left+8+i%10*17,y+i/10*18,i,view.skins()[i],value,Protocol.SELECT);
            }
            y+=36;
            c.drawText(textRenderer,label("picked",Integer.bitCount(view.selected()),view.required()),left+8,y+3,DIM,false);
            button(c,mx,my,left+w-82,y,74,17,label("bag"),101,0,!view.busy());
            y+=20;
        } else {
            int col=0;
            for (int i:view.active()) {
                if (i>=0 && i<view.skins().length && col<10) {
                    int value=i<view.values().length ? view.values()[i]:0;
                    dieButton(c,mx,my,left+8+col++*19,y,i,view.skins()[i],value,Protocol.MARK);
                }
            }
            y+=24;
            if ("TWENTY_ONE".equals(view.game())) {
                button(c,mx,my,left+8,y,86,17,label("stand_button"),Protocol.STAND,0,view.canStand());
            } else {
                button(c,mx,my,left+8,y,86,17,label("keep_button"),Protocol.KEEP,0,view.canKeep());
                if ("FARKLE".equals(view.game())) button(c,mx,my,left+99,y,86,17,label("bank_button"),Protocol.BANK,0,view.canBank());
            }
            y+=20;
        }
        int buttons=top+h-24;
        if (!view.started() && !"FREE".equals(view.game())) {
            if (view.host())
                button(c,mx,my,left+8,buttons,w-76,18,label("start"),Protocol.START,0,view.playerCount()>=view.minPlayers()&&!view.busy());
        } else {
            button(c,mx,my,left+8,buttons,w-76,18,label("throw"),Protocol.THROW,0,view.canRoll());
        }
        button(c,mx,my,left+w-64,buttons,56,18,label("leave"),Protocol.LEAVE,0,view.canLeave());
    }

    private void button(DrawContext c,int mx,int my,int x,int y,int w,int h, Text title,
                        int action,int argument,boolean enabled) {
        Control control=new Control(x,y,w,h,action,argument,enabled);
        controls.add(control);
        int border=enabled ? (control.hit(mx,my)?0xffd2ab76:0xff81583e):0xff44332e;
        c.fill(x,y,x+w,y+h,border);
        c.fill(x+1,y+1,x+w-1,y+h-1,enabled&&control.hit(mx,my)?0xff553125:0xff2b1b1d);
        c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(title.getString(),w-5),
                x+w/2,y+(h-8)/2,enabled?GOLD:0xff756462);
    }

    private void dieButton(DrawContext c,int mx,int my,int x,int y,int index,int skin,int value,int action) {
        boolean enabled=skin>=0&&!view.busy()&&(action==Protocol.SELECT||value>0&&view.canMark());
        Control control=new Control(x,y,17,17,action,index,enabled);
        controls.add(control);
        boolean chosen=((action==Protocol.MARK?view.marked():view.selected())&(1<<index))!=0;
        c.fill(x,y,x+17,y+17,chosen?0xffe3b775:enabled&&control.hit(mx,my)?0xffbc955f:0xff694633);
        c.fill(x+1,y+1,x+16,y+16,chosen?0xff583422:0xff24191d);
        if (skin>=0 && skin<DiceMod.DICE.size()) {
            c.drawItem(new ItemStack(DiceMod.DICE.get(skin)),x+1,y);
            if (value>0) {
                c.fill(x+10,y+8,x+17,y+17,0xee181015);
                c.drawTextWithShadow(textRenderer,Integer.toString(value),x+11,y+9,GOLD);
            }
            if (control.hit(mx,my)) c.drawItemTooltip(textRenderer,new ItemStack(DiceMod.DICE.get(skin)),mx,my);
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        for (Control control : controls) {
            if (!control.enabled || !control.hit(x,y)) continue;
            if (control.action==100) page=Math.max(0,page+control.argument);
            else if (control.action==101) { choosingBag=!choosingBag; page=0; }
            else DiceClient.action(view.table(),control.action,control.argument);
            return true;
        }
        return false;
    }

    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (key==GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override public void close() {
        DiceClient.action(view.table(),Protocol.LEAVE,0);
    }

    @Override public boolean shouldPause() { return false; }
}
