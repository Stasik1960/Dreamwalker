package daot.compat.network;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface PacketCodec<B, T> {
    T decode(B buffer);
    void encode(B buffer, T value);

    static <B, T> PacketCodec<B, T> ofStatic(BiConsumer<B, T> writer, Function<B, T> reader) {
        return new PacketCodec<>() {
            public T decode(B buffer) { return reader.apply(buffer); }
            public void encode(B buffer, T value) { writer.accept(buffer, value); }
        };
    }

    static <B, T> PacketCodec<B, T> unit(T value) {
        return ofStatic((buffer, received) -> {}, buffer -> value);
    }

    static <B, T, A> PacketCodec<B, T> tuple(PacketCodec<? super B, A> a, Function<T, A> getA, Function<A, T> factory) {
        return ofStatic((b, t) -> a.encode(b, getA.apply(t)), b -> factory.apply(a.decode(b)));
    }

    static <B, T, A, C> PacketCodec<B, T> tuple(PacketCodec<? super B, A> a, Function<T, A> getA,
            PacketCodec<? super B, C> c, Function<T, C> getC, BiFunction<A, C, T> factory) {
        return ofStatic((b, t) -> { a.encode(b, getA.apply(t)); c.encode(b, getC.apply(t)); },
                b -> factory.apply(a.decode(b), c.decode(b)));
    }

    @FunctionalInterface interface Factory3<A, C, D, T> { T apply(A a, C c, D d); }
    @FunctionalInterface interface Factory5<A, C, D, E, F, T> { T apply(A a, C c, D d, E e, F f); }

    static <B, T, A, C, D> PacketCodec<B, T> tuple(PacketCodec<? super B, A> a, Function<T, A> getA,
            PacketCodec<? super B, C> c, Function<T, C> getC, PacketCodec<? super B, D> d, Function<T, D> getD,
            Factory3<A, C, D, T> factory) {
        return ofStatic((b, t) -> { a.encode(b, getA.apply(t)); c.encode(b, getC.apply(t)); d.encode(b, getD.apply(t)); },
                b -> factory.apply(a.decode(b), c.decode(b), d.decode(b)));
    }

    static <B, T, A, C, D, E, F> PacketCodec<B, T> tuple(PacketCodec<? super B, A> a, Function<T, A> getA,
            PacketCodec<? super B, C> c, Function<T, C> getC, PacketCodec<? super B, D> d, Function<T, D> getD,
            PacketCodec<? super B, E> e, Function<T, E> getE, PacketCodec<? super B, F> f, Function<T, F> getF,
            Factory5<A, C, D, E, F, T> factory) {
        return ofStatic((b, t) -> { a.encode(b, getA.apply(t)); c.encode(b, getC.apply(t)); d.encode(b, getD.apply(t));
                    e.encode(b, getE.apply(t)); f.encode(b, getF.apply(t)); },
                b -> factory.apply(a.decode(b), c.decode(b), d.decode(b), e.decode(b), f.decode(b)));
    }
}
