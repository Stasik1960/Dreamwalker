package dev.dreamwalker.bloodbornedw.composite.blockrepairqa.mixin;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Window.class)
public abstract class BlockRepairBackgroundMixin {
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",remap=false))
    private void hints(CallbackInfo ci){if(Boolean.getBoolean("dw.blockqa.hidden")){GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);}}
    @Inject(method="<init>",at=@At("TAIL"))
    private void hide(CallbackInfo ci){if(Boolean.getBoolean("dw.blockqa.hidden"))GLFW.glfwHideWindow(((Window)(Object)this).getHandle());}
}
