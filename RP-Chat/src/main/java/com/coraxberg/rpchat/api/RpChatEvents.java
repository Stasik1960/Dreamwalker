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

    /** Short chat controls, handled before RP mode parsing. True consumes the input. */
    public static final Event<ChatControl> CHAT_CONTROL = EventFactory.createArrayBacked(
            ChatControl.class, listeners -> (sender, raw) -> {
                for (ChatControl listener : listeners) {
                    if (listener.handle(sender, raw)) return true;
                }
                return false;
            });

    /** Prepare once, then resolve the visible body separately for every local/radio listener. */
    public static final Event<IcBodyDecorator> PREPARE_IC_BODY = EventFactory.createArrayBacked(
            IcBodyDecorator.class, listeners -> (sender, original, previous) -> {
                RecipientBody result = previous;
                for (IcBodyDecorator listener : listeners) {
                    try {
                        result = java.util.Objects.requireNonNull(listener.prepare(sender, original, result));
                    } catch (RuntimeException ex) {
                        LOGGER.error("Cannot prepare IC message; concealing its body", ex);
                        return target -> "[Речь недоступна]";
                    }
                }
                return result;
            });

    public static RecipientBody prepareIcBody(ServerPlayerEntity sender, String message) {
        RecipientBody prepared = PREPARE_IC_BODY.invoker().prepare(sender, message, target -> message);
        return target -> {
            try {
                return java.util.Objects.requireNonNull(prepared.forRecipient(target));
            } catch (RuntimeException ex) {
                LOGGER.error("Cannot resolve IC message; concealing its body", ex);
                return "[Речь недоступна]";
            }
        };
    }

    @FunctionalInterface
    public interface ChatControl {
        boolean handle(ServerPlayerEntity sender, String raw);
    }

    @FunctionalInterface
    public interface RecipientBody {
        String forRecipient(ServerPlayerEntity target);
    }

    @FunctionalInterface
    public interface IcBodyDecorator {
        RecipientBody prepare(ServerPlayerEntity sender, String original, RecipientBody previous);
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
            String volumeLabel,
            RecipientBody body
    ) {
        /** Compatibility constructor for callers of the original RP Chat API. */
        public LocalIcMessage(ServerPlayerEntity sender, String message, int radius, String volumeLabel) {
            this(sender, message, radius, volumeLabel, prepareIcBody(sender, message));
        }
    }
}
