package dev.dreamwalker.bestiary;

import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ActorSpec(String key, AssetSpec asset, double speed, int eggBase, int eggSpots) {
    public static final List<ActorSpec> ACTORS = List.of(
        create("mire_weaver", "Mire Weaver", 1.16f, 1.15f, 0.24, 0x322b2b, 0x77bfa1, 0.85f, 0.76f),
        create("vesper_warden", "Vesper Warden", 0.69f, 2.07f, 0.20, 0x443348, 0xe6b472, 0.92f, 0.88f)
    );
    private static final Map<String, ActorSpec> BY_ID = ACTORS.stream()
        .collect(Collectors.toUnmodifiableMap(spec -> spec.asset().id(), Function.identity()));

    public static ActorSpec find(String assetId) {
        return BY_ID.get(assetId);
    }

    private static ActorSpec create(String key, String name, float width, float height, double speed,
                                    int eggBase, int eggSpots, float attack1, float attack2) {
        String prefix = Bestiary.MOD_ID + ":";
        AssetSpec asset = new AssetSpec(prefix + key, prefix + "geo/" + key + ".geo.json",
            prefix + "textures/entity/" + key + ".png", prefix + "animations/" + key + ".animation.json",
            width, height, 1, name, Map.of(
                "idle", clip(key, "idle", 2.8f, true),
                "walk", clip(key, "walk", 0.72f, true),
                "attack1", clip(key, "attack1", attack1, false),
                "attack2", clip(key, "attack2", attack2, false),
                "hit", clip(key, "hit", 0.32f, false),
                "death", clip(key, "death", 1.05f, false)
            ));
        return new ActorSpec(key, asset, speed, eggBase, eggSpots);
    }

    private static AssetSpec.Clip clip(String key, String suffix, float seconds, boolean loop) {
        return new AssetSpec.Clip("animation." + key + "." + suffix, seconds, loop);
    }
}
