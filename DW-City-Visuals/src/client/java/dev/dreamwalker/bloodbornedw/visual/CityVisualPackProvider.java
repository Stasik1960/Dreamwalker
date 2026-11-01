package dev.dreamwalker.bloodbornedw.visual;

import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;

public final class CityVisualPackProvider implements ResourcePackProvider {
    @Override public void register(java.util.function.Consumer<ResourcePackProfile> consumer) {
        consumer.accept(ResourcePackProfile.of("dw_city_visuals", Text.literal("City visuals"), true,
            name -> new CityVisualPack(name, CityVisualClient.packSnapshot()),
            new ResourcePackProfile.Metadata(Text.literal("Server-controlled city visuals"), 15, FeatureSet.empty()),
            ResourceType.CLIENT_RESOURCES, ResourcePackProfile.InsertionPosition.TOP, true, ResourcePackSource.BUILTIN));
    }
}
