package com.coraxberg.rpchat.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Public integration events exposed by RP Chat. */
public final class RpChatEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("RPChat/API");

    private RpChatEvents() {
    }

    /** Fired after RP Chat delivers an ordinary IC local message. */
    public static final Event<LocalIcMessageListener> LOCAL_IC_MESSAGE = EventFactory.createArrayBacked(
            LocalIcMessageListener.class,
            listeners -> event -> {
                for (LocalIcMessageListener listener : listeners) {
                    try {
                        listener.onLocalIcMessage(event);
                    } catch (Throwable throwable) {
                        LOGGER.error("RP local IC chat listener {} failed",
                                listener.getClass().getName(), throwable);
                    }
                }
            });

    @FunctionalInterface
    public interface LocalIcMessageListener {
        void onLocalIcMessage(LocalIcMessage event);
    }

    /** Context for an already-delivered ordinary IC local message. */
    public record LocalIcMessage(
            ServerPlayerEntity sender,
            String message,
            int localRadius,
            String volumeLabel
    ) {
    }
}
