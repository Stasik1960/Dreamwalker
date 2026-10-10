package ru.modelprops;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.modelprops.command.ModelPropsCommands;
import ru.modelprops.net.ModelPropsNetworking;
import ru.modelprops.net.ModelActionNetworking;
import ru.modelprops.server.ServerModelCatalog;

public final class ModelProps implements ModInitializer {
    public static final String MOD_ID = "modelprops";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item MODEL_PROP = Registry.register(
            Registries.ITEM,
            id("model_prop"),
            new ModelPropItem(new FabricItemSettings().maxCount(64))
    );

    public static final RegistryKey<ItemGroup> GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, id("models"));
    public static final ItemGroup GROUP = Registry.register(
            Registries.ITEM_GROUP,
            GROUP_KEY,
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.modelprops.models"))
                    .icon(() -> new ItemStack(MODEL_PROP))
                    .build()
    );

    @Override
    public void onInitialize() {
        ModelPropsNetworking.initializeServerReceiver();
        ModelActionNetworking.initializeServer();
        ModelPropsCommands.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ServerModelCatalog.INSTANCE.initialize());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ModelPropsNetworking.sendFullSync(handler.player));
        LOGGER.info("Model Props initialized");
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
