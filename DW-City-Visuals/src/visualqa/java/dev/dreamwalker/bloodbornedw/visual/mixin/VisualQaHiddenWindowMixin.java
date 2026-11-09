package dev.dreamwalker.bloodbornedw.visual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep an automated screenshot run from taking focus from the user's game. */
@Mixin(Window.class)
abstract class VisualQaHiddenWindowMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", remap = false,
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private long dw$createWindow(int width, int height, CharSequence title, long monitor, long share,
            Operation<Long> original) {
        if (Boolean.getBoolean("dw.visualqa.hidden")) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        }
        return original.call(width, height, title, monitor, share);
    }
}
