package com.yharnam.casino.client;

import com.yharnam.casino.YharnamCasino;
import com.yharnam.casino.client.render.SlotMachineRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class CasinoClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(YharnamCasino.SLOT_ENTITY, SlotMachineRenderer::new);
    }
}
