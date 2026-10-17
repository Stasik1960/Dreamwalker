package dev.dreamwalker.bloodbornedw.gallery.mixin;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explicit capture-only launch option. Rendering continues at the same
 * resolution/settings while the user's foreground application keeps focus. */
@Mixin(Window.class)
public abstract class GalleryBackgroundMixin {
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",remap=false))
    private void background(CallbackInfo callback){
        if(Boolean.getBoolean("dw.capture.background")){
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        }
    }
}
