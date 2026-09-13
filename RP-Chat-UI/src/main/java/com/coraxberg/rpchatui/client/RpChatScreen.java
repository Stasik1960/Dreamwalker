package com.coraxberg.rpchatui.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class RpChatScreen extends Screen {
    public RpChatScreen() {
        super(Text.literal("RP Chat"));
    }

    @Override
    protected void init() {
        ChatUiState.focusChatInput();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ChatUiState.render(context, 0, mouseX, mouseY);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return ChatUiState.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return ChatUiState.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return ChatUiState.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return ChatUiState.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return ChatUiState.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return ChatUiState.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
