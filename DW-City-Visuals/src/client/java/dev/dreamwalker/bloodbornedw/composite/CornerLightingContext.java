package dev.dreamwalker.bloodbornedw.composite;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
/** Per-consumer context; reset before each source part. */
public interface CornerLightingContext {
 void dw$setCornerContext(StaticLightingViewAccess view);
}
