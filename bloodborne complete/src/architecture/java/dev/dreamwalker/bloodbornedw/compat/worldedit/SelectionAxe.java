package dev.dreamwalker.bloodbornedw.compat.worldedit;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/** The wooden axe sends one selection click and never starts normal combat or mining. */
public final class SelectionAxe implements ModInitializer {
    private static final Identifier PHASE = new Identifier("bloodborne_dw", "selection_axe");

    public static boolean heldBy(PlayerEntity player) {
        return player != null && player.getMainHandStack().isOf(Items.WOODEN_AXE);
    }

    @Override
    public void onInitialize() {
        // WorldEdit handles the server's click in the default phase. A click
        // it declines must still not fall through to vanilla mining.
        AttackBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
        AttackBlockCallback.EVENT.register(PHASE, (player, world, hand, pos, direction) -> {
            if (hand != Hand.MAIN_HAND || !heldBy(player)) return ActionResult.PASS;
            // Fabric sends the sequenced packet for CONSUME without local
            // block prediction, hit particles, or the hand animation.
            return world.isClient ? ActionResult.CONSUME : ActionResult.FAIL;
        });
        AttackEntityCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(PHASE, (player, world, hand, entity, hit) ->
                hand == Hand.MAIN_HAND && heldBy(player) ? ActionResult.FAIL : ActionResult.PASS);
    }
}
