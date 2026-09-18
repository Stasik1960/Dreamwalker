package daot.compat.components;

import com.mojang.serialization.Codec;
import daot.compat.network.PacketCodec;
import net.minecraft.util.Identifier;

public final class ComponentType<T> {
    final Codec<T> codec;
    Identifier id;
    private ComponentType(Codec<T> codec) { this.codec = codec; }
    public static <T> Builder<T> builder() { return new Builder<>(); }
    public static final class Builder<T> {
        private Codec<T> codec;
        public Builder<T> codec(Codec<T> codec) { this.codec = codec; return this; }
        // In 1.20.1 ItemStack sync transmits the NBT encoded by the data codec.
        public Builder<T> packetCodec(PacketCodec<?, T> packetCodec) { return this; }
        public ComponentType<T> build() {
            if (codec == null) throw new IllegalStateException("Component data codec is required");
            return new ComponentType<>(codec);
        }
    }
}
