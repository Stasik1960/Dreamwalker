package dev.yharnamdice.client;

import dev.yharnamdice.Protocol;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TableRulesScreen extends Screen {
    private final TableScreen parent;
    private final Text gameTitle;
    private final List<Text> paragraphs;
    private int left, top, panelWidth, panelHeight;
    private RulesText rulesText;

    TableRulesScreen(TableScreen parent, Protocol.View view) {
        super(ruleText("title"));
        this.parent = parent;
        gameTitle = view.title().isBlank() ? Text.translatable("yharnam_dice.table.free") : Text.literal(view.title());
        paragraphs = describe(view);
    }

    static List<Text> describe(Protocol.View view) {
        List<Text> body = new ArrayList<>();
        Protocol.RuleDetails details = view.rules();
        if (details != null) {
            int min = view.minPlayers(), max = details.maxPlayers();
            body.add(ruleText(min == max ? "players_exact" : "players_range", min, max));
        }
        switch (view.game()) {
            case "DICE_POKER" -> {
                body.add(ruleText("poker.roll", view.required()));
                if (details == null) body.add(ruleText("poker.reroll_unknown"));
                else if (details.maxRerolls() > 0) body.add(ruleText("poker.reroll", details.maxRerolls()));
                body.add(ruleText("poker.win"));
                body.add(ruleText("poker.ranks"));
            }
            case "FARKLE" -> {
                body.add(details == null ? ruleText("farkle.roll", view.required())
                        : ruleText("farkle.goal", view.required(), details.target()));
                body.add(ruleText("farkle.turn"));
                body.add(ruleText("farkle.bust"));
                if (details != null) body.add(ruleText(details.hotDice() ? "farkle.hot" : "farkle.no_hot"));
                body.add(ruleText("farkle.score"));
            }
            case "TWENTY_ONE" -> {
                if (details == null) body.add(ruleText("twenty_one.generic", view.required()));
                else {
                    body.add(ruleText("twenty_one.roll", view.required(), details.target()));
                    body.add(ruleText("twenty_one.win", details.target(), details.target()));
                }
            }
            default -> {
                body.add(ruleText("free.roll", view.required()));
                body.add(ruleText("free.result"));
            }
        }
        if (details != null) {
            if (!details.allowDuplicateDice()) body.add(ruleText("unique_dice"));
            if (!"FREE".equals(view.game())) body.add(ruleText("host." + details.hostExit().toLowerCase(Locale.ROOT)));
        }
        return List.copyOf(body);
    }

    private static Text ruleText(String name, Object... args) {
        return Text.translatable("yharnam_dice.rules." + name,args);
    }

    @Override protected void init() {
        panelWidth = Math.min(322,width-20);
        left = (width-panelWidth)/2;
        var paragraphs = RulesText.wrap(textRenderer,panelWidth-26,this.paragraphs);
        int contentHeight = RulesText.measure(paragraphs);
        panelHeight = Math.min(Math.min(288,height-16),contentHeight+74);
        top = (height-panelHeight)/2;
        double scroll = rulesText == null ? 0 : rulesText.position();
        rulesText = addDrawableChild(new RulesText(textRenderer,left+10,top+34,
                panelWidth-26,panelHeight-64,paragraphs,contentHeight,scroll));
        setInitialFocus(rulesText);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"),button -> close())
                .dimensions(width/2-37,top+panelHeight-25,74,18).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(left,top,left+panelWidth,top+panelHeight,0xf5231318);
        context.drawBorder(left,top,panelWidth,panelHeight,0xff9b7352);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,top+7,0xffebcb9e);
        context.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(gameTitle.getString(),panelWidth-18),
                width/2,top+20,0xffbaa093);
        super.render(context,mouseX,mouseY,delta);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        return DiceClient.openChatFromTable(client,this,key,scan) || super.keyPressed(key,scan,modifiers);
    }

    @Override public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override public boolean shouldPause() { return false; }

    private static final class RulesText extends ScrollableWidget {
        private static final int LINE_HEIGHT = 11;
        private final TextRenderer font;
        private final List<List<OrderedText>> lines;
        private final int contentHeight;

        RulesText(TextRenderer font, int x, int y, int width, int height,
                  List<List<OrderedText>> lines, int contentHeight, double scroll) {
            super(x,y,width,height,ruleText("title"));
            this.font = font;
            this.lines = lines;
            this.contentHeight = contentHeight;
            setScrollY(scroll);
        }

        static List<List<OrderedText>> wrap(TextRenderer font, int width, List<Text> paragraphs) {
            return paragraphs.stream().map(p -> font.wrapLines(p,width-12)).toList();
        }

        static int measure(List<List<OrderedText>> lines) {
            return lines.stream().mapToInt(p -> p.size()*LINE_HEIGHT+5).sum()-5;
        }

        double position() { return getScrollY(); }

        @Override protected int getContentsHeight() { return contentHeight; }
        @Override protected double getDeltaYPerScroll() { return 3*LINE_HEIGHT; }

        @Override protected void drawBox(DrawContext context) {
            context.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xf8170d10);
            context.drawBorder(getX(),getY(),getWidth(),getHeight(),0xff6f4f3f);
        }

        @Override protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
            int y = getY()+getPadding();
            for (List<OrderedText> paragraph : lines) {
                for (OrderedText line : paragraph) {
                    context.drawText(font,line,getX()+getPadding()+2,y,0xffe2cbb1,false);
                    y += LINE_HEIGHT;
                }
                y += 5;
            }
        }

        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
