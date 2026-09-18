package daot;

import daot.network.ModNetworking;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class BloodlineRerollItem extends Item {
   private static final BloodlineType[] ROLLABLE = new BloodlineType[]{
      BloodlineType.ELDIAN, BloodlineType.ACKERMAN, BloodlineType.ROYAL, BloodlineType.MARLEYAN
   };
   private static final Map<UUID, BloodlineRerollItem.RollState> ACTIVE_ROLLS = new ConcurrentHashMap<>();
   private static boolean tickRegistered = false;

   public BloodlineRerollItem(Settings properties) {
      super(properties);
      registerTick();
   }

   private static synchronized void registerTick() {
      if (!tickRegistered) {
         tickRegistered = true;
         ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            Iterator<Entry<UUID, BloodlineRerollItem.RollState>> it = ACTIVE_ROLLS.entrySet().iterator();

            while (it.hasNext()) {
               Entry<UUID, BloodlineRerollItem.RollState> entry = it.next();
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
               if (player == null) {
                  it.remove();
               } else {
                  BloodlineRerollItem.RollState state = entry.getValue();
                  state.tick++;
                  if (state.tick >= state.steps[state.stepIndex]) {
                     state.stepIndex++;
                     if (state.stepIndex >= state.steps.length) {
                        showFinalResult(player, state);
                        it.remove();
                     } else {
                        showRollTick(player, state);
                     }
                  }
               }
            }
         });
      }
   }

   @Override
   public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
      ItemStack stack = user.getStackInHand(hand);
      if (world.isClient) {
         return TypedActionResult.consume(stack);
      } else {
         ServerPlayerEntity serverPlayer = (ServerPlayerEntity)user;
         if (!DannysAot.areRerollsAllowed(world)) {
            user.sendMessage(Text.literal("Bloodline rerolls are disabled!").formatted(Formatting.RED));
            return TypedActionResult.fail(stack);
         } else if (ACTIVE_ROLLS.containsKey(user.getUuid())) {
            user.sendMessage(Text.literal("Already rolling!").formatted(Formatting.RED));
            return TypedActionResult.fail(stack);
         } else {
            boolean hasShifter = user.getCommandTags().contains("attack")
               || user.getCommandTags().contains("colossal")
               || user.getCommandTags().contains("armored")
               || user.getCommandTags().contains("female")
               || user.getCommandTags().contains("beast")
               || user.getCommandTags().contains("warhammer")
               || user.getCommandTags().contains("jaw");
            if (hasShifter) {
               user.sendMessage(Text.literal("Remove your Shifter power before using this item!").formatted(Formatting.RED, Formatting.BOLD));
               return TypedActionResult.fail(stack);
            } else {
               BloodlineType result = rollWeighted(user.getRandom());
               BloodlineRerollItem.RollState state = new BloodlineRerollItem.RollState(result, user.getRandom());
               ACTIVE_ROLLS.put(user.getUuid(), state);
               stack.decrement(1);
               serverPlayer.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 5, 0));
               showRollTick(serverPlayer, state);
               return TypedActionResult.success(stack);
            }
         }
      }
   }

   private static BloodlineType rollWeighted(Random random) {
      ModConfig cfg = ModConfig.get();
      float total = cfg.eldianWeight + cfg.marleyanWeight + cfg.ackermanWeight + cfg.royalWeight;
      float roll = random.nextFloat() * total;
      if (roll < cfg.royalWeight) {
         return BloodlineType.ROYAL;
      } else if (roll < cfg.royalWeight + cfg.ackermanWeight) {
         return BloodlineType.ACKERMAN;
      } else {
         return roll < cfg.royalWeight + cfg.ackermanWeight + cfg.marleyanWeight ? BloodlineType.MARLEYAN : BloodlineType.ELDIAN;
      }
   }

   private static void showRollTick(ServerPlayerEntity player, BloodlineRerollItem.RollState state) {
      BloodlineType display = state.displayOrder[state.stepIndex];
      player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 5, 2));
      player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Rerolling Bloodline...").formatted(Formatting.GRAY)));
      player.networkHandler.sendPacket(new TitleS2CPacket(display.getStyledName()));
      float pitch = 0.8F + player.getRandom().nextFloat() * 0.4F;
      player.getServerWorld()
         .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.6F, pitch);
   }

   private static void showFinalResult(ServerPlayerEntity player, BloodlineRerollItem.RollState state) {
      BloodlineType result = state.result;
      player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
      player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Your bloodline has changed!").formatted(Formatting.GREEN)));
      player.networkHandler.sendPacket(new TitleS2CPacket(result.getStyledName()));
      player.getServerWorld()
         .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
      applyBloodlineChange(player, result);
   }

   private static void applyBloodlineChange(ServerPlayerEntity player, BloodlineType newType) {
      BloodlineData data = BloodlineData.get(player.server);
      BloodlineType oldType = data.getRealBloodline(player.getUuid());
      if (oldType == BloodlineType.ACKERMAN && newType != BloodlineType.ACKERMAN) {
         ModNetworking.removeAckermanAttributes(player);
      }

      data.setBloodline(player.getUuid(), newType, GrantProvenance.ofRoll(player.getServer()));
      BloodlineType effective = data.getBloodline(player.getUuid());
      ModNetworking.updateHomelanderAbilities(player, effective);
      if (newType == BloodlineType.ACKERMAN) {
         VillagerTransformTracker.removeInjectedPlayer(player);
         ModNetworking.applyAckermanAttributes(player);
      }

      if (newType == BloodlineType.MARLEYAN) {
         VillagerTransformTracker.removeInjectedPlayer(player);
      }

      player.sendMessage(ModCommands.buildBloodlineMessage(newType));
      ModNetworking.broadcastBloodlineToAll(player, effective.ordinal());
   }

   private static class RollState {
      final BloodlineType result;
      final BloodlineType[] displayOrder;
      final int[] steps;
      int tick = 0;
      int stepIndex = 0;

      RollState(BloodlineType result, Random random) {
         List<Integer> schedule = new ArrayList<>();
         int t = 0;

         for (int i = 0; i < 10; i++) {
            t += 2;
            schedule.add(t);
         }

         for (int i = 0; i < 6; i++) {
            t += 4;
            schedule.add(t);
         }

         for (int i = 0; i < 3; i++) {
            t += 7;
            schedule.add(t);
         }

         t += 12;
         schedule.add(t);
         this.steps = schedule.stream().mapToInt(Integer::intValue).toArray();
         int totalDisplays = this.steps.length;
         this.displayOrder = new BloodlineType[totalDisplays];

         for (int i = 0; i < totalDisplays - 1; i++) {
            this.displayOrder[i] = BloodlineRerollItem.ROLLABLE[random.nextInt(BloodlineRerollItem.ROLLABLE.length)];
         }

         this.displayOrder[totalDisplays - 1] = result;
         this.result = result;
      }
   }
}
