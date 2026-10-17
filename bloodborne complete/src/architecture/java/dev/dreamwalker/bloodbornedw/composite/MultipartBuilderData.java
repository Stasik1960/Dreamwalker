package dev.dreamwalker.bloodbornedw.composite;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import org.apache.commons.lang3.tuple.Pair;
/** Plain bridge outside the mixin package; does not change vanilla builders. */
public interface MultipartBuilderData { List<Pair<Predicate<BlockState>,BakedModel>> dreamwalker$components(); }
