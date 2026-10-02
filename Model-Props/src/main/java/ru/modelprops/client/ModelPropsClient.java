package ru.modelprops.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import ru.modelprops.ModelPropItem;
import ru.modelprops.ModelProps;
import ru.modelprops.client.render.DynamicModelRenderer;
import ru.modelprops.client.upload.ClientUploadManager;
import ru.modelprops.model.ModelTransform;
import ru.modelprops.net.ModelActionNetworking;
import ru.modelprops.net.ModelPropsAssetProtocol;
import ru.modelprops.net.ModelPropsNetworking;

public final class ModelPropsClient implements ClientModInitializer {
    private KeyBinding debugKey;
    private KeyBinding uploadKey;
    private KeyBinding customActionKey;

    @Override
    public void onInitializeClient() {
        BuiltinItemRendererRegistry.INSTANCE.register(ModelProps.MODEL_PROP, DynamicModelRenderer.INSTANCE::render);
        ItemGroupEvents.modifyEntriesEvent(ModelProps.GROUP_KEY).register(entries -> {
            ItemGroup.Entries vanillaEntries = entries;
            for (ItemStack stack : ClientModelCatalog.INSTANCE.creativeStacks()) {
                vanillaEntries.add(stack);
            }
        });
        registerNetworkReceivers();
        ClientAnimationController.initialize();

        debugKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.modelprops.debug", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "category.modelprops"));
        uploadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.modelprops.upload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, "category.modelprops"));
        customActionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.modelprops.custom_action", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, "category.modelprops"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientUploadManager.tick(client);
            ClientAnimationController.tick(client);
            while (uploadKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new ModelUploadScreen());
                }
            }
            while (debugKey.wasPressed()) {
                if (client.player == null) {
                    continue;
                }
                ItemStack stack = client.player.getMainHandStack();
                if (!(stack.getItem() instanceof ModelPropItem)) {
                    stack = client.player.getOffHandStack();
                }
                if (!(stack.getItem() instanceof ModelPropItem) || ModelPropItem.getModelId(stack).isBlank()) {
                    client.player.sendMessage(Text.translatable("message.modelprops.hold_item"), true);
                } else if (ClientModelCatalog.INSTANCE.get(ModelPropItem.getModelId(stack)) == null) {
                    client.player.sendMessage(Text.literal("Unknown or unloaded model: " + ModelPropItem.getModelId(stack)), true);
                } else {
                    client.setScreen(new DebugTransformScreen(stack));
                }
            }
            while (customActionKey.wasPressed()) {
                ClientAnimationController.handleAnimationKey(client);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientModelCatalog.INSTANCE.clear();
            ClientAnimationController.clear();
            ClientUploadManager.reset();
        });
    }

    private static void registerNetworkReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.SYNC_BEGIN,
                (client, handler, buffer, responseSender) -> {
                    int protocol = buffer.readVarInt();
                    int generation = buffer.readVarInt();
                    boolean canEdit = buffer.readBoolean();
                    int modelCount = buffer.readVarInt();
                    client.execute(() -> {
                        if (protocol != ModelPropsNetworking.PROTOCOL) {
                            ClientModelCatalog.INSTANCE.clear();
                            if (client.player != null) {
                                client.player.sendMessage(Text.literal("Model Props protocol mismatch: server " + protocol
                                        + ", client " + ModelPropsNetworking.PROTOCOL), false);
                            }
                        } else {
                            ClientModelCatalog.INSTANCE.begin(generation, canEdit, modelCount);
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.SYNC_MODEL,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String id = buffer.readString(128);
                    String name = buffer.readString(128);
                    String json = buffer.readString(ModelPropsNetworking.MAX_STRING);
                    client.execute(() -> ClientModelCatalog.INSTANCE.acceptModel(generation, id, name, json));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.TEXTURE_BEGIN,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String key = buffer.readString(256);
                    int size = buffer.readVarInt();
                    String digest = buffer.readString(64);
                    client.execute(() -> ClientModelCatalog.INSTANCE.beginTexture(generation, key, size, digest));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.TEXTURE_CHUNK,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String key = buffer.readString(256);
                    int offset = buffer.readVarInt();
                    byte[] data = buffer.readByteArray(ModelPropsNetworking.CHUNK_SIZE);
                    client.execute(() -> ClientModelCatalog.INSTANCE.acceptTextureChunk(generation, key, offset, data));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.TEXTURE_END,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String key = buffer.readString(256);
                    client.execute(() -> ClientModelCatalog.INSTANCE.endTexture(generation, key));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.ANIMATION_BEGIN,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    int size = buffer.readVarInt();
                    String digest = buffer.readString(64);
                    client.execute(() -> ClientModelCatalog.INSTANCE.beginAnimation(
                            generation, modelId, size, digest));
                });
        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.ANIMATION_CHUNK,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    int offset = buffer.readVarInt();
                    byte[] data = buffer.readByteArray(ModelPropsNetworking.CHUNK_SIZE);
                    client.execute(() -> ClientModelCatalog.INSTANCE.acceptAnimationChunk(
                            generation, modelId, offset, data));
                });
        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.ANIMATION_END,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    client.execute(() -> ClientModelCatalog.INSTANCE.endAnimation(generation, modelId));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.SOUND_BEGIN,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    String action = buffer.readString(16);
                    int size = buffer.readVarInt();
                    String digest = buffer.readString(64);
                    client.execute(() -> ClientModelCatalog.INSTANCE.beginSound(
                            generation, modelId, action, size, digest));
                });
        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.SOUND_CHUNK,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    String action = buffer.readString(16);
                    int offset = buffer.readVarInt();
                    byte[] data = buffer.readByteArray(ModelPropsNetworking.CHUNK_SIZE);
                    client.execute(() -> ClientModelCatalog.INSTANCE.acceptSoundChunk(
                            generation, modelId, action, offset, data));
                });
        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.SOUND_END,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    String action = buffer.readString(16);
                    client.execute(() -> ClientModelCatalog.INSTANCE.endSound(
                            generation, modelId, action));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.SOUND_V2_BEGIN,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    String soundKey = buffer.readString(ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH);
                    int size = buffer.readVarInt();
                    String digest = buffer.readString(64);
                    client.execute(() -> ClientModelCatalog.INSTANCE.beginSound(
                            generation, modelId, soundKey, size, digest));
                });
        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.SOUND_V2_CHUNK,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    String soundKey = buffer.readString(ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH);
                    int offset = buffer.readVarInt();
                    byte[] data = buffer.readByteArray(ModelPropsNetworking.CHUNK_SIZE);
                    client.execute(() -> ClientModelCatalog.INSTANCE.acceptSoundChunk(
                            generation, modelId, soundKey, offset, data));
                });
        ClientPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.SOUND_V2_END,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    String modelId = buffer.readString(128);
                    String soundKey = buffer.readString(ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH);
                    client.execute(() -> ClientModelCatalog.INSTANCE.endSound(
                            generation, modelId, soundKey));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.SYNC_TRANSFORM,
                (client, handler, buffer, responseSender) -> {
                    String id = buffer.readString(128);
                    ModelTransform transform = ModelPropsNetworking.readTransform(buffer);
                    client.execute(() -> ClientModelCatalog.INSTANCE.acceptTransform(id, transform));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.SYNC_END,
                (client, handler, buffer, responseSender) -> {
                    int generation = buffer.readVarInt();
                    client.execute(() -> ClientModelCatalog.INSTANCE.finish(generation));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.SERVER_MESSAGE,
                (client, handler, buffer, responseSender) -> {
                    String message = buffer.readString(512);
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal(message), false);
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.UPLOAD_STATUS,
                (client, handler, buffer, responseSender) -> {
                    var requestId = buffer.readUuid();
                    byte status = buffer.readByte();
                    String modelId = buffer.readString(128);
                    String message = buffer.readString(512);
                    client.execute(() -> ClientUploadManager.handleStatus(
                            client, requestId, status, modelId, message));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModelActionNetworking.ACTION_PLAY,
                (client, handler, buffer, responseSender) -> {
                    int entityId = buffer.readVarInt();
                    Hand hand = buffer.readByte() == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                    String modelId = buffer.readString(128);
                    String action = buffer.readString(ModelActionNetworking.MAX_ACTION_LENGTH);
                    long sequence = buffer.readLong();
                    client.execute(() -> ClientAnimationController.play(
                            client, entityId, hand, modelId, action, sequence));
                });

    }
}
