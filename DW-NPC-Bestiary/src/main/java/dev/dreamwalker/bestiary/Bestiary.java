package dev.dreamwalker.bestiary;

import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class Bestiary implements ModInitializer {
    public static final String MOD_ID = "dw_npc_bestiary";
    private static final Map<ActorSpec, EntityType<RpMobEntity>> TYPES = new HashMap<>();
    private static final Map<Identifier, SoundEvent> SOUNDS = new HashMap<>();

    static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    public static EntityType<RpMobEntity> type(ActorSpec actor) {
        return TYPES.get(actor);
    }

    public static SoundEvent sound(ActorSpec actor, String event) {
        return SOUNDS.get(id("entity." + actor.key() + "." + event));
    }

    @Override
    public void onInitialize() {
        for (ActorSpec actor : ActorSpec.ACTORS) {
            registerActor(actor);
            for (String event : new String[]{"idle", "hurt", "death", "attack"}) {
                Identifier soundId = id("entity." + actor.key() + "." + event);
                SOUNDS.put(soundId, Registry.register(Registries.SOUND_EVENT, soundId, SoundEvent.of(soundId)));
            }
        }
    }

    private static void registerActor(ActorSpec actor) {
        var asset = actor.asset();
        var type = FabricEntityTypeBuilder.<RpMobEntity>create(SpawnGroup.CREATURE,
                (entityType, world) -> new RpMobEntity(entityType, world, asset.id()))
            .dimensions(EntityDimensions.fixed(asset.width(), asset.height()))
            .trackRangeChunks(8)
            .build();
        Registry.register(Registries.ENTITY_TYPE, id(actor.key()), type);
        TYPES.put(actor, type);
        FabricDefaultAttributeRegistry.register(type, MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 2)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, actor.speed())
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32));
        Item egg = Registry.register(Registries.ITEM, id(actor.key() + "_spawn_egg"),
            new SpawnEggItem(type, actor.eggBase(), actor.eggSpots(), new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(egg));
    }
}
