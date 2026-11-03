package dev.dreamwalker.editorqa.mixin;
import dev.dreamwalker.bloodbornedw.editor.EditorRenderScope;
import dev.dreamwalker.bloodbornedw.editor.EditorView;
import dev.dreamwalker.editorqa.EditorModelProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=EditorRenderScope.class,remap=false)
public abstract class EditorScopeProbeMixin {
    @Inject(method="begin",at=@At("HEAD"),remap=false)
    private static void input(EditorView view,CallbackInfo ci){EditorModelProbe.scopeInput(view);}
}
