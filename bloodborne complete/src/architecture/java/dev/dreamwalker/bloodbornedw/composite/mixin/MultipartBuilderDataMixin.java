package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.MultipartBuilderData;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.*;
@Mixin(MultipartBakedModel.Builder.class)
public abstract class MultipartBuilderDataMixin implements MultipartBuilderData {
    @Shadow @Final private List<Pair<Predicate<BlockState>,BakedModel>> components;
    @Override public List<Pair<Predicate<BlockState>,BakedModel>> dreamwalker$components(){return components;}
}
