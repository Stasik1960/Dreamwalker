package dev.dreamwalker.selectorindex.mixin;
import dev.dreamwalker.selectorindex.IndexedMultipart;
import dev.dreamwalker.selectorindex.SelectorIndex;
import java.util.List;
import net.minecraft.class_2248;
import net.minecraft.class_2680;
import net.minecraft.class_2689;
import net.minecraft.class_816;
import net.minecraft.class_819;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
@Mixin(value = class_816.class, remap = false)
public abstract class MultipartIndexMixin implements IndexedMultipart {
    @Shadow @Final private class_2689<class_2248, class_2680> field_4329;
    @Unique private SelectorIndex dreamwalker$index;
    @Unique private boolean dreamwalker$indexAttempted;
    @Unique private int dreamwalker$uses;
    @Override public synchronized List<class_819> dreamwalker$select(class_2680 state) {
        class_816 self = (class_816)(Object)this;
        List<class_819> all = self.method_3519();
        if (state.method_26204().method_9595() != field_4329) return all;
        if (!dreamwalker$indexAttempted) {
            dreamwalker$indexAttempted = true;
            dreamwalker$index = SelectorIndex.create(field_4329, all);
        }
        if (dreamwalker$index == null) return all;
        List<class_819> selected = dreamwalker$index.select(state);
        if (++dreamwalker$uses >= field_4329.method_11662().size()) dreamwalker$index = null;
        return selected == null ? all : selected;
    }
}
