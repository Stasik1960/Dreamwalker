package dev.dreamwalker.bloodbornedw.fixqa.mixin;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(MinecraftClient.class)
public interface FixClientAccess {@Invoker("doItemPick") void dw$pick();}
