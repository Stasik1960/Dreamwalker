package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.NativeSessionPublication;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.NativeGridClient",remap=false)
abstract class NativeSessionPrepareMixin {
    @Redirect(method="lambda$onInitializeClient$1",at=@At(value="FIELD",target="Ldev/dreamwalker/bloodbornedw/composite/NativeGridClient;session:Ldev/dreamwalker/bloodbornedw/composite/NativeGridClient$Session;",opcode=Opcodes.PUTSTATIC),remap=false)
    private static void dw$prepare(@Coerce Object loading) { NativeSessionPublication.prepare(loading); }
}
