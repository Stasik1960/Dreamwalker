package com.yharnam.casino.client;

import com.yharnam.casino.YharnamCasino;
import com.yharnam.casino.client.render.CardTableRenderer;
import com.yharnam.casino.client.render.FortuneWheelRenderer;
import com.yharnam.casino.client.render.ChipCabinetRenderer;
import com.yharnam.casino.client.render.MachineModelLoader;
import com.yharnam.casino.client.render.SlotMachineRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class CasinoClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MachineModelLoader.register();
        BlockEntityRendererFactories.register(YharnamCasino.SLOT_ENTITY, SlotMachineRenderer::new);
        BlockEntityRendererFactories.register(YharnamCasino.CHIP_CABINET_ENTITY,ChipCabinetRenderer::new);
        CardTableRenderer.registerPrivateHands();
        BlockEntityRendererFactories.register(YharnamCasino.CARD_TABLE_ENTITY, CardTableRenderer::new);
        BlockEntityRendererFactories.register(YharnamCasino.WHEEL_ENTITY, FortuneWheelRenderer::new);
        HandledScreens.register(YharnamCasino.DENOMINATION_MENU,DenominationScreen::new);
    }
}
