package daot;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import daot.advancement.ModCriteriaTriggers;
import daot.network.ModNetworking;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.GameRules.BooleanRule;
import net.minecraft.world.GameRules.Key;

public class ModCommands {
   private static final Set<UUID> founderChatActive = new HashSet<>();
   private static final Set<UUID> royalBeastEnabled = new HashSet<>();
   private static final Set<String> VALID_TYPES = Set.of("attack", "colossal", "armored", "beast", "female", "warhammer", "jaw");
   private static final Set<String> ALL_SHIFTER_TAGS = Set.of("attack", "colossal", "armored", "beast", "female", "warhammer", "jaw");
   private static final Set<String> VALID_BLOODLINES = Set.of("eldian", "ackerman", "royal", "marleyan");
   private static LinkedHashMap<String, Key<BooleanRule>> modGamerules;

   public static boolean isRoyalBeastEnabled(UUID uuid) {
      return royalBeastEnabled.contains(uuid);
   }

   public static boolean isFounderChatActive(UUID uuid) {
      return founderChatActive.contains(uuid);
   }

   public static void removeFounderChat(UUID uuid) {
      founderChatActive.remove(uuid);
   }

   public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                           "daot"
                        )
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                   "shifter"
                                                )
                                                .requires(source -> source.hasPermissionLevel(2)))
                                             .then(
                                                CommandManager.literal("set")
                                                   .then(
                                                      CommandManager.argument("targets", EntityArgumentType.players())
                                                         .then(
                                                            CommandManager.argument("type", StringArgumentType.word())
                                                               .suggests(ModCommands::suggestTitanTypes)
                                                               .executes(ModCommands::setShifter)
                                                         )
                                                   )
                                             ))
                                          .then(
                                             CommandManager.literal("remove")
                                                .then(
                                                   CommandManager.argument("targets", EntityArgumentType.players())
                                                      .then(
                                                         CommandManager.argument("type", StringArgumentType.word())
                                                            .suggests(ModCommands::suggestTargetShifterType)
                                                            .executes(ModCommands::removeShifter)
                                                      )
                                                )
                                          ))
                                       .then(
                                          CommandManager.literal("stamina")
                                             .then(
                                                CommandManager.literal("fill")
                                                   .then(CommandManager.argument("targets", EntityArgumentType.players()).executes(ModCommands::fillStamina))
                                             )
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)CommandManager.literal("check").requires(source -> source.hasPermissionLevel(2)))
                                          .executes(ModCommands::checkShifters)
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)CommandManager.literal("audit").requires(source -> source.hasPermissionLevel(2)))
                                       .executes(ModCommands::auditShifters)
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)CommandManager.literal("reset").requires(source -> source.hasPermissionLevel(2)))
                                    .executes(ModCommands::resetAllShifters)
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                           "danny"
                                                                        )
                                                                        .requires(ModCommands::canTraverseDanny))
                                                                     .then(
                                                                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("founder")
                                                                                 .requires(ModCommands::hasFullDannyAccess))
                                                                              .executes(ModCommands::toggleFounder))
                                                                           .then(
                                                                              CommandManager.argument("targets", EntityArgumentType.players())
                                                                                 .executes(ModCommands::toggleFounderTargets)
                                                                           )
                                                                     ))
                                                                  .then(
                                                                     ((LiteralArgumentBuilder)CommandManager.literal("founderchat")
                                                                           .requires(ModCommands::hasFullDannyAccess))
                                                                        .executes(ModCommands::toggleFounderChat)
                                                                  ))
                                                               .then(
                                                                  ((LiteralArgumentBuilder)CommandManager.literal("sound")
                                                                        .requires(ModCommands::hasFullDannyAccess))
                                                                     .executes(ModCommands::playDannySound)
                                                               ))
                                                            .then(
                                                               ((LiteralArgumentBuilder)CommandManager.literal("tung")
                                                                     .requires(ModCommands::hasFullDannyAccess))
                                                                  .executes(ModCommands::toggleTripleT)
                                                            ))
                                                         .then(
                                                            ((LiteralArgumentBuilder)CommandManager.literal("ogre").requires(ModCommands::canUseDannySubset))
                                                               .executes(ModCommands::toggleOgreShifter)
                                                         ))
                                                      .then(
                                                         ((LiteralArgumentBuilder)CommandManager.literal("titan").requires(ModCommands::hasTitanCommandAccess))
                                                            .executes(ModCommands::toggleTitanBloodline)
                                                      ))
                                                   .then(
                                                      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("cart")
                                                               .requires(ModCommands::hasFullDannyAccess))
                                                            .executes(ModCommands::toggleCartShifter))
                                                         .then(
                                                            CommandManager.argument("targets", EntityArgumentType.players())
                                                               .executes(ModCommands::toggleCartShifterTargets)
                                                         )
                                                   ))
                                                .then(
                                                   ((LiteralArgumentBuilder)CommandManager.literal("vanish").requires(ModCommands::hasFullDannyAccess))
                                                      .executes(ctx -> {
                                                         ServerPlayerEntity player = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                                                         VanishManager.toggle(player);
                                                         return 1;
                                                      })
                                                ))
                                             .then(
                                                ((LiteralArgumentBuilder)CommandManager.literal("crawler").requires(ModCommands::hasFullDannyAccess))
                                                   .executes(ctx -> {
                                                      ServerPlayerEntity player = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                                                      return summonRideableCrawler(player);
                                                   })
                                             ))
                                          .then(
                                             ((LiteralArgumentBuilder)CommandManager.literal("sadtitan").requires(ModCommands::hasFullDannyAccess))
                                                .executes(ctx -> {
                                                   ServerPlayerEntity player = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                                                   return summonRideableSadTitan(player);
                                                })
                                          ))
                                       .then(
                                          ((LiteralArgumentBuilder)CommandManager.literal("bloodmoon").requires(ModCommands::hasFullDannyAccess))
                                             .then(CommandManager.argument("active", BoolArgumentType.bool()).executes(ModCommands::setBloodmoon))
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)CommandManager.literal("kenny").requires(ModCommands::hasFullDannyAccess))
                                          .executes(ModCommands::giveKennyHat)
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)CommandManager.literal("royalBeast").requires(ModCommands::hasFullDannyAccess))
                                       .then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(ModCommands::setRoyalBeast))
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                      "power"
                                                   )
                                                   .then(
                                                      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("homelander")
                                                               .requires(ModCommands::canUseDannySubset))
                                                            .executes(ModCommands::grantHomelanderSelf))
                                                         .then(
                                                            CommandManager.argument("targets", EntityArgumentType.players())
                                                               .executes(ModCommands::grantHomelanderTargets)
                                                         )
                                                   ))
                                                .then(
                                                   ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("soldierboy")
                                                            .requires(ModCommands::hasFullDannyAccess))
                                                         .executes(ModCommands::grantSoldierboySelf))
                                                      .then(
                                                         CommandManager.argument("targets", EntityArgumentType.players())
                                                            .executes(ModCommands::grantSoldierboyTargets)
                                                      )
                                                ))
                                             .then(
                                                ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("atrain")
                                                         .requires(ModCommands::canUseDannySubset))
                                                      .executes(ModCommands::grantAtrainSelf))
                                                   .then(
                                                      CommandManager.argument("targets", EntityArgumentType.players())
                                                         .executes(ModCommands::grantAtrainTargets)
                                                   )
                                             ))
                                          .then(
                                             ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("translucent")
                                                      .requires(ModCommands::hasFullDannyAccess))
                                                   .executes(ModCommands::grantTranslucentSelf))
                                                .then(
                                                   CommandManager.argument("targets", EntityArgumentType.players())
                                                      .executes(ModCommands::grantTranslucentTargets)
                                                )
                                          ))
                                       .then(
                                          ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("butcher")
                                                   .requires(ModCommands::hasFullDannyAccess))
                                                .executes(ModCommands::grantButcherSelf))
                                             .then(CommandManager.argument("targets", EntityArgumentType.players()).executes(ModCommands::grantButcherTargets))
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("remove")
                                                .requires(ModCommands::canUseDannySubset))
                                             .executes(ModCommands::removePowerSelf))
                                          .then(
                                             ((RequiredArgumentBuilder)CommandManager.argument("targets", EntityArgumentType.players())
                                                   .requires(ModCommands::hasFullDannyAccess))
                                                .executes(ModCommands::removePowerTargets)
                                          )
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("shifter").requires(ModCommands::canUseDannySubset))
                                    .then(CommandManager.literal("remove").executes(ModCommands::removeDiscreetShifterSelf)))
                                 .then(
                                    CommandManager.argument("type", StringArgumentType.word())
                                       .suggests(ModCommands::suggestTitanTypes)
                                       .executes(ModCommands::grantDiscreetShifterSelf)
                                 )
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)CommandManager.literal("skin")
                           .requires(
                              source -> source.getEntity() instanceof ServerPlayerEntity sp
                                 && (
                                    DannysAot.TDXM_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       || DannysAot.BERSERK_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       || DannysAot.KIRITO_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       || DannysAot.NEEDLE_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       || DannysAot.AARON_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       || DannysAot.ROYALTY_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       || DannysAot.TITAN_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       || DannysAot.HYPER_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                      "apply"
                                                   )
                                                   .then(
                                                      ((LiteralArgumentBuilder)CommandManager.literal("ogre")
                                                            .requires(
                                                               source -> source.getEntity() instanceof ServerPlayerEntity sp
                                                                  && DannysAot.TDXM_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                                            ))
                                                         .executes(ModCommands::applyOgreSkin)
                                                   ))
                                                .then(
                                                   ((LiteralArgumentBuilder)CommandManager.literal("berserk")
                                                         .requires(
                                                            source -> source.getEntity() instanceof ServerPlayerEntity sp
                                                               && DannysAot.BERSERK_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                                         ))
                                                      .executes(ModCommands::applyBerserkSkin)
                                                ))
                                             .then(
                                                ((LiteralArgumentBuilder)CommandManager.literal("kirito")
                                                      .requires(
                                                         source -> source.getEntity() instanceof ServerPlayerEntity sp
                                                            && DannysAot.KIRITO_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                                      ))
                                                   .executes(ModCommands::applyKiritoSkin)
                                             ))
                                          .then(
                                             ((LiteralArgumentBuilder)CommandManager.literal("needle")
                                                   .requires(
                                                      source -> source.getEntity() instanceof ServerPlayerEntity sp
                                                         && DannysAot.NEEDLE_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                                   ))
                                                .executes(ModCommands::applyNeedleSkin)
                                          ))
                                       .then(
                                          ((LiteralArgumentBuilder)CommandManager.literal("aaron")
                                                .requires(
                                                   source -> source.getEntity() instanceof ServerPlayerEntity sp
                                                      && DannysAot.AARON_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                                ))
                                             .executes(ModCommands::applyAaronSkin)
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)CommandManager.literal("royalty")
                                             .requires(
                                                source -> source.getEntity() instanceof ServerPlayerEntity sp
                                                   && DannysAot.ROYALTY_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                             ))
                                          .executes(ModCommands::applyRoyaltySkin)
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)CommandManager.literal("titan")
                                          .requires(
                                             source -> source.getEntity() instanceof ServerPlayerEntity sp
                                                && DannysAot.TITAN_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                          ))
                                       .executes(ModCommands::applyTitanSkin)
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)CommandManager.literal("hyper")
                                       .requires(
                                          source -> source.getEntity() instanceof ServerPlayerEntity sp
                                             && DannysAot.HYPER_AUTHORIZED_UUIDS.contains(sp.getUuid())
                                       ))
                                    .executes(ModCommands::applyHyperSkin)
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("gamerule").requires(source -> source.hasPermissionLevel(2)))
                        .executes(ModCommands::listGamerules))
                     .then(
                        ((RequiredArgumentBuilder)CommandManager.argument("rule", StringArgumentType.word())
                              .suggests(ModCommands::suggestGameruleNames)
                              .executes(ModCommands::showGamerule))
                           .then(CommandManager.argument("value", BoolArgumentType.bool()).executes(ModCommands::setGamerule))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("bloodline")
                           .then(
                              ((LiteralArgumentBuilder)CommandManager.literal("set").requires(source -> source.hasPermissionLevel(2)))
                                 .then(
                                    CommandManager.argument("targets", EntityArgumentType.players())
                                       .then(
                                          CommandManager.argument("bloodline", StringArgumentType.word())
                                             .suggests(ModCommands::suggestBloodlineTypes)
                                             .executes(ModCommands::setBloodline)
                                       )
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)CommandManager.literal("remove").requires(source -> source.hasPermissionLevel(2)))
                              .then(
                                 CommandManager.argument("targets", EntityArgumentType.players())
                                    .then(
                                       CommandManager.argument("bloodline", StringArgumentType.word())
                                          .suggests(ModCommands::suggestTargetBloodline)
                                          .executes(ModCommands::removeBloodline)
                                    )
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)CommandManager.literal("check").executes(ModCommands::checkBloodlineSelf))
                           .then(
                              ((RequiredArgumentBuilder)CommandManager.argument("targets", EntityArgumentType.players())
                                    .requires(source -> source.hasPermissionLevel(2)))
                                 .executes(ModCommands::checkBloodline)
                           )
                     ))
                  .then(
                     CommandManager.literal("stamina")
                        .then(
                           ((LiteralArgumentBuilder)CommandManager.literal("fill").requires(source -> source.hasPermissionLevel(2)))
                              .then(CommandManager.argument("targets", EntityArgumentType.players()).executes(ModCommands::fillBloodlineStamina))
                        )
                  )
            )
      );
   }

   private static boolean hasFullDannyAccess(ServerCommandSource source) {
      return DannyAccess.hasFullDannyAccess(source);
   }

   private static boolean hasLimitedDannyAccess(ServerCommandSource source) {
      return DannyAccess.hasLimitedDannyAccess(source);
   }

   private static boolean canUseDannySubset(ServerCommandSource source) {
      return DannyAccess.canUseDannySubset(source);
   }

   private static boolean hasTitanCommandAccess(ServerCommandSource source) {
      return DannyAccess.hasTitanCommandAccess(source);
   }

   private static boolean canTraverseDanny(ServerCommandSource source) {
      return DannyAccess.canTraverseDanny(source);
   }

   private static boolean isDiscreetShifterUser(ServerPlayerEntity player) {
      return DannyAccess.mayHoldDiscreetShifter(player);
   }

   private static int setRoyalBeast(CommandContext<ServerCommandSource> ctx) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
         boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
         if (enabled) {
            royalBeastEnabled.add(player.getUuid());
         } else {
            royalBeastEnabled.remove(player.getUuid());
         }

         Text msg = Text.literal("royalBeast " + (enabled ? "enabled" : "disabled")).formatted(enabled ? Formatting.GOLD : Formatting.GRAY, Formatting.BOLD);
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> msg, false);
         return 1;
      } catch (Exception var4) {
         ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("Error: " + var4.getMessage()));
         return 0;
      }
   }

   private static int setBloodmoon(CommandContext<ServerCommandSource> ctx) {
      boolean active = BoolArgumentType.getBool(ctx, "active");
      boolean was = BloodmoonState.isActive();
      MinecraftServer server = ((ServerCommandSource)ctx.getSource()).getServer();
      BloodmoonState.setActive(active, server.getTicks());
      ModNetworking.broadcastBloodmoon(server, active);
      if (active && !was) {
         ModNetworking.broadcastBloodmoonMusic(server, BloodmoonState.getCurrentTrackIndex(server.getTicks()));
         RegistryKey<World> paradisKey = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
         Text title = Text.literal("The sky shines red").formatted(Formatting.DARK_RED);
         Text subtitle = Text.literal("Bloodmoon has begun.").formatted(Formatting.RED);

         for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getWorld().getRegistryKey() == paradisKey) {
               p.networkHandler.sendPacket(new TitleS2CPacket(title));
               p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            }
         }
      }

      Text msg = Text.literal("Bloodmoon " + (active ? "enabled" : "disabled")).formatted(active ? Formatting.RED : Formatting.GRAY);
      ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> msg, false);
      return 1;
   }

   private static CompletableFuture<Suggestions> suggestTitanTypes(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      builder.suggest("attack");
      builder.suggest("colossal");
      builder.suggest("armored");
      builder.suggest("beast");
      builder.suggest("female");
      builder.suggest("warhammer");
      builder.suggest("jaw");
      return builder.buildFuture();
   }

   private static CompletableFuture<Suggestions> suggestTargetShifterType(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         Set<String> suggested = new HashSet<>();

         for (ServerPlayerEntity player : targets) {
            for (String type : VALID_TYPES) {
               if (player.getCommandTags().contains(type)) {
                  suggested.add(type);
               }
            }
         }

         if (!suggested.isEmpty()) {
            for (String s : suggested) {
               builder.suggest(s);
            }

            return builder.buildFuture();
         }
      } catch (Exception var8) {
      }

      return suggestTitanTypes(context, builder);
   }

   private static String getTitanDisplayName(String type) {
      return switch (type) {
         case "attack" -> "Attack Titan";
         case "colossal" -> "Colossal Titan";
         case "armored" -> "Armored Titan";
         case "beast" -> "Beast Titan";
         case "female" -> "Female Titan";
         case "warhammer" -> "Warhammer Titan";
         case "jaw" -> "Jaw Titan";
         default -> type;
      };
   }

   private static TitanPowerType getTitanPowerType(String type) {
      return switch (type) {
         case "attack" -> TitanPowerType.ATTACK;
         case "colossal" -> TitanPowerType.COLOSSAL;
         case "armored" -> TitanPowerType.ARMORED;
         case "beast" -> TitanPowerType.BEAST;
         case "female" -> TitanPowerType.FEMALE;
         case "warhammer" -> TitanPowerType.WARHAMMER;
         case "jaw" -> TitanPowerType.JAW;
         default -> null;
      };
   }

   private static int setShifter(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         String type = StringArgumentType.getString(context, "type").toLowerCase();
         if (!VALID_TYPES.contains(type)) {
            ((ServerCommandSource)context.getSource())
               .sendError(Text.literal("Invalid titan type. Use 'attack', 'colossal', 'armored', 'beast', 'female', 'warhammer', or 'jaw'."));
            return 0;
         } else {
            MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
            BloodlineData bloodlineData = BloodlineData.get(server);
            boolean speakorAmongTargets = false;

            for (ServerPlayerEntity t : targets) {
               if ("Speakor".equals(t.getGameProfile().getName())) {
                  speakorAmongTargets = true;
                  break;
               }
            }

            TitanPowerType requestedPower = getTitanPowerType(type);
            boolean multiShifters = server.getGameRules().getBoolean(DannysAot.RULE_MULTIPLE_SHIFTERS);
            if (requestedPower != null && !speakorAmongTargets && !multiShifters) {
               ServerWorld overworld = server.getWorld(World.OVERWORLD);
               if (overworld != null) {
                  TitanPowerData powerData = TitanPowerData.get(overworld);
                  UUID existingHolder = powerData.getPlayerWithPower(requestedPower);
                  if (existingHolder != null) {
                     boolean targetingExistingHolder = false;

                     for (ServerPlayerEntity tx : targets) {
                        if (tx.getUuid().equals(existingHolder)) {
                           targetingExistingHolder = true;
                           break;
                        }
                     }

                     if (!targetingExistingHolder) {
                        ServerPlayerEntity holderPlayer = server.getPlayerManager().getPlayer(existingHolder);
                        String holderName = holderPlayer != null ? holderPlayer.getName().getString() : existingHolder.toString();
                        String titanName = getTitanDisplayName(type);
                        ((ServerCommandSource)context.getSource()).sendError(Text.literal("Cannot set shifter: " + holderName + " already has " + titanName));
                        return 0;
                     }
                  }
               }
            }

            int count = 0;

            for (ServerPlayerEntity player : targets) {
               boolean isSpeakor = "Speakor".equals(player.getGameProfile().getName());
               BloodlineType bloodline = bloodlineData.getBloodline(player.getUuid());
               if (bloodline == BloodlineType.ACKERMAN) {
                  ((ServerCommandSource)context.getSource())
                     .sendError(Text.literal(player.getName().getString() + " is an Ackerman - Subjects of Ymir cannot access this power"));
               } else if (bloodline == BloodlineType.MARLEYAN) {
                  ((ServerCommandSource)context.getSource()).sendError(Text.literal(player.getName().getString() + " is a Marleyan - not a Subject of Ymir"));
               } else {
                  ServerWorld overworld = server.getWorld(World.OVERWORLD);
                  TitanPowerData powerData = overworld != null ? TitanPowerData.get(overworld) : null;
                  if (!isSpeakor) {
                     for (String otherType : ALL_SHIFTER_TAGS) {
                        if (!otherType.equals(type) && player.getCommandTags().contains(otherType)) {
                           player.removeScoreboardTag(otherType);
                           if (powerData != null) {
                              TitanPowerType oldPower = getTitanPowerType(otherType);
                              if (oldPower != null) {
                                 powerData.removePlayerFromPower(player.getUuid(), oldPower);
                              }
                           }
                        }
                     }
                  }

                  if (!player.getCommandTags().contains(type)) {
                     player.addCommandTag(type);
                     ModCriteriaTriggers.INHERITED_TITAN_POWER.trigger(player);
                  }

                  if (powerData != null && !isSpeakor) {
                     TitanPowerType newPower = getTitanPowerType(type);
                     if (newPower != null) {
                        GrantProvenance issued = GrantProvenance.ofCommand((ServerCommandSource)context.getSource(), GrantProvenance.Issuer.COMMAND_SHIFTER_SET);
                        if (multiShifters) {
                           powerData.addPlayerToPower(player.getUuid(), newPower, issued);
                        } else {
                           powerData.setPlayerPower(player.getUuid(), newPower, issued);
                        }

                        if (overworld != null) {
                           TitanPowerHelper.broadcastShifterClaimed(overworld, newPower);
                        }
                     }
                  }

                  count++;
               }
            }

            String titanName = getTitanDisplayName(type);
            if (count == 1) {
               ServerPlayerEntity target = targets.iterator().next();
               ((ServerCommandSource)context.getSource())
                  .sendFeedback(() -> Text.literal("Set " + target.getName().getString() + " as " + titanName + " shifter"), true);
            } else {
               int finalCount = count;
               ((ServerCommandSource)context.getSource())
                  .sendFeedback(() -> Text.literal("Set " + finalCount + " players as " + titanName + " shifters"), true);
            }

            return count;
         }
      } catch (Exception var18) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var18.getMessage()));
         return 0;
      }
   }

   private static int removeShifter(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         String type = StringArgumentType.getString(context, "type").toLowerCase();
         if (!VALID_TYPES.contains(type)) {
            ((ServerCommandSource)context.getSource())
               .sendError(Text.literal("Invalid titan type. Use 'attack', 'colossal', 'armored', 'beast', 'female', 'warhammer', or 'jaw'."));
            return 0;
         } else {
            MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            TitanPowerData powerData = overworld != null ? TitanPowerData.get(overworld) : null;
            int count = 0;

            for (ServerPlayerEntity player : targets) {
               if (player.getCommandTags().contains(type)) {
                  player.removeScoreboardTag(type);
                  if (powerData != null) {
                     TitanPowerType power = getTitanPowerType(type);
                     if (power != null) {
                        powerData.removePlayerFromPower(player.getUuid(), power);
                        if (overworld != null && !powerData.playerHasPower(power)) {
                           TitanPowerHelper.broadcastShifterUnclaimed(overworld, power);
                        }
                     }
                  }

                  count++;
               }
            }

            String titanName = getTitanDisplayName(type);
            if (count == 0) {
               ((ServerCommandSource)context.getSource()).sendError(Text.literal("No players had the " + titanName + " shifter tag."));
               return 0;
            } else {
               if (count == 1) {
                  ServerPlayerEntity target = targets.iterator().next();
                  ((ServerCommandSource)context.getSource())
                     .sendFeedback(() -> Text.literal("Removed " + titanName + " shifter tag from " + target.getName().getString()), true);
               } else {
                  int finalCount = count;
                  ((ServerCommandSource)context.getSource())
                     .sendFeedback(() -> Text.literal("Removed " + titanName + " shifter tag from " + finalCount + " players"), true);
               }

               return count;
            }
         }
      } catch (Exception var10) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var10.getMessage()));
         return 0;
      }
   }

   private static int grantDiscreetShifterSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         String type = StringArgumentType.getString(context, "type").toLowerCase();
         return grantDiscreetShifterTo(context, List.of(player), type);
      } catch (Exception var3) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var3.getMessage()));
         return 0;
      }
   }

   private static int grantDiscreetShifterTo(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets, String type) {
      if (!VALID_TYPES.contains(type)) {
         ((ServerCommandSource)context.getSource())
            .sendError(Text.literal("Invalid titan type. Use 'attack', 'colossal', 'armored', 'beast', 'female', 'warhammer', or 'jaw'."));
         return 0;
      } else {
         int count = 0;

         for (ServerPlayerEntity player : targets) {
            for (String otherType : ALL_SHIFTER_TAGS) {
               if (!otherType.equals(type) && player.getCommandTags().contains(otherType)) {
                  player.removeScoreboardTag(otherType);
               }
            }

            player.addCommandTag(type);
            count++;
         }

         String titanName = getTitanDisplayName(type);
         if (count == 1) {
            ServerPlayerEntity target = targets.iterator().next();
            ((ServerCommandSource)context.getSource())
               .sendFeedback(() -> Text.literal("Discreetly set " + target.getName().getString() + " as " + titanName + " shifter"), false);
         } else {
            int finalCount = count;
            ((ServerCommandSource)context.getSource())
               .sendFeedback(() -> Text.literal("Discreetly set " + finalCount + " players as " + titanName + " shifters"), false);
         }

         return count;
      }
   }

   private static int removeDiscreetShifterSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         return removeDiscreetShifterFrom(context, List.of(player));
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int removeDiscreetShifterFrom(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets) {
      int count = 0;

      for (ServerPlayerEntity player : targets) {
         boolean removedAny = false;

         for (String tag : ALL_SHIFTER_TAGS) {
            if (player.getCommandTags().contains(tag)) {
               player.removeScoreboardTag(tag);
               removedAny = true;
            }
         }

         if (removedAny) {
            count++;
         }
      }

      if (count == 0) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("No targeted players had a shifter to remove."));
         return 0;
      } else {
         if (count == 1) {
            ServerPlayerEntity target = targets.iterator().next();
            ((ServerCommandSource)context.getSource())
               .sendFeedback(() -> Text.literal("Discreetly removed shifter from " + target.getName().getString()), false);
         } else {
            int finalCount = count;
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Discreetly removed shifter from " + finalCount + " players"), false);
         }

         return count;
      }
   }

   private static int applyOgreSkin(CommandContext<ServerCommandSource> context) {
      return applyCloakSkin(context, "TDXM", "Ogre");
   }

   private static int applyBerserkSkin(CommandContext<ServerCommandSource> context) {
      return applyCloakSkin(context, "BERSERK", "Berserk");
   }

   private static int applyAaronSkin(CommandContext<ServerCommandSource> context) {
      return applyCloakSkin(context, "AARON", "Aaron");
   }

   private static int applyRoyaltySkin(CommandContext<ServerCommandSource> context) {
      return applyCloakSkin(context, "ROYALTY", "Royalty");
   }

   private static int applyHyperSkin(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         ItemStack held = player.getMainHandStack();
         if (held.getItem() instanceof CloakItem) {
            return applyCloakSkin(context, "HYPER", "Hyper");
         } else if (held.getItem() instanceof ODMGearItem) {
            daot.compat.components.Components.set(held, DannysAot.ODM_GEAR_SKIN, "HYPER");
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Applied Hyper skin to " + held.getName().getString() + "."), false);
            return 1;
         } else if (!(held.getItem() instanceof BladeItem) && !(player.getOffHandStack().getItem() instanceof BladeItem)) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Hold a cloak, ODM gear, or an ODM grip in your hand to apply the Hyper skin."));
            return 0;
         } else {
            return applyBladeSkin(context, "HYPER", "Hyper");
         }
      } catch (Exception var3) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var3.getMessage()));
         return 0;
      }
   }

   private static int applyKiritoSkin(CommandContext<ServerCommandSource> context) {
      return applyBladeSkin(context, "KIRITO", "Kirito");
   }

   private static int applyTitanSkin(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         ItemStack held = player.getMainHandStack();
         if (held.getItem() instanceof CloakItem) {
            daot.compat.components.Components.set(held, DannysAot.CLOAK_SKIN, "TITAN");
         } else if (held.getItem() instanceof UniformItem
            || held.getItem() instanceof GarrisonUniformItem
            || held.getItem() instanceof ScoutUniformItem
            || held.getItem() instanceof MilitaryPoliceUniformItem) {
            daot.compat.components.Components.set(held, DannysAot.UNIFORM_SKIN, "TITAN");
         } else if (held.getItem() instanceof ODMBootsItem) {
            daot.compat.components.Components.set(held, DannysAot.ODM_BOOTS_SKIN, "TITAN");
         } else {
            if (!(held.getItem() instanceof TrenchCoatItem)) {
               ((ServerCommandSource)context.getSource())
                  .sendError(
                     Text.literal(
                        "Hold a cloak, a uniform (uniform / scout / garrison / military police), ODM boots, or a trench coat in your main hand to apply the Titan skin."
                     )
                  );
               return 0;
            }

            daot.compat.components.Components.set(held, DannysAot.TRENCH_COAT_SKIN, "TITAN");
         }

         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Applied Titan skin to " + held.getName().getString() + "."), false);
         return 1;
      } catch (Exception var3) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var3.getMessage()));
         return 0;
      }
   }

   private static int applyNeedleSkin(CommandContext<ServerCommandSource> context) {
      return applyBladeSkin(context, "NEEDLE", "Needle");
   }

   private static int applyBladeSkin(CommandContext<ServerCommandSource> context, String skinTag, String displayName) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         ItemStack main = player.getMainHandStack();
         ItemStack off = player.getOffHandStack();
         int applied = 0;
         if (main.getItem() instanceof BladeItem) {
            daot.compat.components.Components.set(main, DannysAot.BLADE_SKIN, skinTag);
            applied++;
         }

         if (off.getItem() instanceof BladeItem) {
            daot.compat.components.Components.set(off, DannysAot.BLADE_SKIN, skinTag);
            applied++;
         }

         if (applied == 0) {
            ((ServerCommandSource)context.getSource())
               .sendError(Text.literal("Hold an ODM grip in your main or off hand to apply the " + displayName + " skin."));
            return 0;
         } else {
            int finalApplied = applied;
            ((ServerCommandSource)context.getSource())
               .sendFeedback(
                  () -> Text.literal("Applied " + displayName + " skin to " + finalApplied + " ODM blade" + (finalApplied == 1 ? "" : "s") + "."), false
               );
            return applied;
         }
      } catch (Exception var8) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var8.getMessage()));
         return 0;
      }
   }

   private static int applyCloakSkin(CommandContext<ServerCommandSource> context, String skinTag, String displayName) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         ItemStack held = player.getMainHandStack();
         if (!(held.getItem() instanceof CloakItem)) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Hold a cloak in your main hand to apply the " + displayName + " skin."));
            return 0;
         } else {
            daot.compat.components.Components.set(held, DannysAot.CLOAK_SKIN, skinTag);
            ((ServerCommandSource)context.getSource())
               .sendFeedback(() -> Text.literal("Applied " + displayName + " skin to " + held.getName().getString() + "."), false);
            return 1;
         }
      } catch (Exception var5) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var5.getMessage()));
         return 0;
      }
   }

   private static int fillStamina(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         int count = 0;

         for (ServerPlayerEntity player : targets) {
            ModNetworking.fillStamina(player.getUuid());
            count++;
         }

         if (count == 1) {
            ServerPlayerEntity target = targets.iterator().next();
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Filled stamina for " + target.getName().getString()), true);
         } else {
            int finalCount = count;
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Filled stamina for " + finalCount + " players"), true);
         }

         return count;
      } catch (Exception var5) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var5.getMessage()));
         return 0;
      }
   }

   private static int checkShifters(CommandContext<ServerCommandSource> context) {
      try {
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         ServerWorld overworld = server.getWorld(World.OVERWORLD);
         if (overworld == null) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Could not access world data."));
            return 0;
         } else {
            TitanPowerData powerData = TitanPowerData.get(overworld);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("=== Titan Shifter Status ==="), false);

            for (TitanPowerType power : TitanPowerType.values()) {
               Set<UUID> holderUUIDs = powerData.getPlayersWithPower(power);
               String displayName = power.getDisplayName();
               List<String> holderNames = new ArrayList<>();

               for (UUID holderUUID : holderUUIDs) {
                  ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(holderUUID);
                  String name;
                  if (onlinePlayer != null) {
                     name = onlinePlayer.getName().getString();
                  } else {
                     Optional<GameProfile> profile = server.getUserCache().getByUuid(holderUUID);
                     name = profile.<String>map(GameProfile::getName).orElse(holderUUID.toString());
                  }

                  GrantProvenance grant = powerData.getPowerProvenance(holderUUID, power);
                  holderNames.add(grant == null ? name : name + " [" + grant.describe() + "]");
               }

               List<UUID> villagers = powerData.getVillagersWithPower(power);
               int villagerCount = villagers.size();
               String villagerSuffix = villagerCount > 0 ? " (" + villagerCount + " powered villagers)" : "";
               String line;
               if (!holderNames.isEmpty()) {
                  line = displayName + ": " + String.join(", ", holderNames) + villagerSuffix;
               } else {
                  line = displayName + ": (unclaimed)" + villagerSuffix;
               }

               ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(line), false);
            }

            return 1;
         }
      } catch (Exception var16) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var16.getMessage()));
         return 0;
      }
   }

   private static int fillBloodlineStamina(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         int count = 0;

         for (ServerPlayerEntity player : targets) {
            AwakenedPowerTracker.fillCharge(player);
            count++;
         }

         if (count == 1) {
            ServerPlayerEntity target = targets.iterator().next();
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Filled Ackerman stamina for " + target.getName().getString()), true);
         } else {
            int finalCount = count;
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Filled Ackerman stamina for " + finalCount + " players"), true);
         }

         return count;
      } catch (Exception var5) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var5.getMessage()));
         return 0;
      }
   }

   private static CompletableFuture<Suggestions> suggestBloodlineTypes(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      builder.suggest("eldian");
      builder.suggest("ackerman");
      builder.suggest("royal");
      builder.suggest("marleyan");
      return builder.buildFuture();
   }

   private static CompletableFuture<Suggestions> suggestTargetBloodline(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         BloodlineData data = BloodlineData.get(server);
         Set<String> suggested = new HashSet<>();

         for (ServerPlayerEntity player : targets) {
            BloodlineType bloodline = data.getRealBloodline(player.getUuid());
            if (bloodline != null) {
               suggested.add(bloodline.getCommandName());
            }
         }

         if (!suggested.isEmpty()) {
            for (String s : suggested) {
               builder.suggest(s);
            }

            return builder.buildFuture();
         }
      } catch (Exception var9) {
      }

      builder.suggest("eldian");
      builder.suggest("ackerman");
      builder.suggest("royal");
      builder.suggest("marleyan");
      return builder.buildFuture();
   }

   public static MutableText buildBloodlineMessage(BloodlineType type) {
      return Text.literal("You have inherited ").append(type.getStyledName()).append(Text.literal(" blood."));
   }

   private static int grantHomelanderSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         return grantHomelanderTo(context, List.of(player));
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantHomelanderTargets(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         return grantHomelanderTo(context, targets);
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantHomelanderTo(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      BloodlineData data = BloodlineData.get(server);
      int count = 0;

      for (ServerPlayerEntity player : targets) {
         if (!DannyAccess.mayHoldDannyPower(player.getUuid())) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal(player.getName().getString() + " is not on the permitted power-holder list."));
         } else {
            BloodlineType old = data.getBloodline(player.getUuid());
            if (old == BloodlineType.ACKERMAN) {
               ModNetworking.removeAckermanAttributes(player);
            }

            data.setPower(
               player.getUuid(),
               BloodlineType.HOMELANDER,
               GrantProvenance.ofCommand((ServerCommandSource)context.getSource(), GrantProvenance.Issuer.COMMAND_DANNY)
            );
            ModNetworking.updateHomelanderAbilities(player, BloodlineType.HOMELANDER);
            player.sendMessage(buildBloodlineMessage(BloodlineType.HOMELANDER));
            ModNetworking.broadcastBloodlineToAll(player, BloodlineType.HOMELANDER.ordinal());
            count++;
         }
      }

      if (count == 1) {
         ServerPlayerEntity target = targets.iterator().next();
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Homelander bloodline to " + target.getName().getString()), false);
      } else {
         int finalCount = count;
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Homelander bloodline to " + finalCount + " players"), false);
      }

      return count;
   }

   private static int grantSoldierboySelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         return grantSoldierboyTo(context, List.of(player));
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantSoldierboyTargets(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         return grantSoldierboyTo(context, targets);
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantSoldierboyTo(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      BloodlineData data = BloodlineData.get(server);
      int count = 0;

      for (ServerPlayerEntity player : targets) {
         if (!DannyAccess.mayHoldDannyPower(player.getUuid())) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal(player.getName().getString() + " is not on the permitted power-holder list."));
         } else {
            BloodlineType old = data.getBloodline(player.getUuid());
            if (old == BloodlineType.ACKERMAN) {
               ModNetworking.removeAckermanAttributes(player);
            }

            data.setPower(
               player.getUuid(),
               BloodlineType.SOLDIERBOY,
               GrantProvenance.ofCommand((ServerCommandSource)context.getSource(), GrantProvenance.Issuer.COMMAND_DANNY)
            );
            ModNetworking.updateHomelanderAbilities(player, BloodlineType.SOLDIERBOY);
            player.sendMessage(buildBloodlineMessage(BloodlineType.SOLDIERBOY));
            ModNetworking.broadcastBloodlineToAll(player, BloodlineType.SOLDIERBOY.ordinal());
            count++;
         }
      }

      if (count == 1) {
         ServerPlayerEntity target = targets.iterator().next();
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Soldierboy bloodline to " + target.getName().getString()), false);
      } else {
         int finalCount = count;
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Soldierboy bloodline to " + finalCount + " players"), false);
      }

      return count;
   }

   private static int grantAtrainSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         return grantAtrainTo(context, List.of(player));
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantAtrainTargets(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         return grantAtrainTo(context, targets);
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantAtrainTo(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      BloodlineData data = BloodlineData.get(server);
      int count = 0;

      for (ServerPlayerEntity player : targets) {
         if (!DannyAccess.mayHoldDannyPower(player.getUuid())) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal(player.getName().getString() + " is not on the permitted power-holder list."));
         } else {
            BloodlineType old = data.getBloodline(player.getUuid());
            if (old == BloodlineType.ACKERMAN) {
               ModNetworking.removeAckermanAttributes(player);
            }

            data.setPower(
               player.getUuid(),
               BloodlineType.ATRAIN,
               GrantProvenance.ofCommand((ServerCommandSource)context.getSource(), GrantProvenance.Issuer.COMMAND_DANNY)
            );
            ModNetworking.updateHomelanderAbilities(player, BloodlineType.ATRAIN);
            ModNetworking.applyAtrainAttributes(player);
            player.sendMessage(buildBloodlineMessage(BloodlineType.ATRAIN));
            ModNetworking.broadcastBloodlineToAll(player, BloodlineType.ATRAIN.ordinal());
            count++;
         }
      }

      if (count == 1) {
         ServerPlayerEntity target = targets.iterator().next();
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted A-Train bloodline to " + target.getName().getString()), false);
      } else {
         int finalCount = count;
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted A-Train bloodline to " + finalCount + " players"), false);
      }

      return count;
   }

   private static int grantTranslucentSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         return grantTranslucentTo(context, List.of(player));
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantTranslucentTargets(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         return grantTranslucentTo(context, targets);
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantTranslucentTo(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      BloodlineData data = BloodlineData.get(server);
      int count = 0;

      for (ServerPlayerEntity player : targets) {
         if (!DannyAccess.mayHoldDannyPower(player.getUuid())) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal(player.getName().getString() + " is not on the permitted power-holder list."));
         } else {
            BloodlineType old = data.getBloodline(player.getUuid());
            if (old == BloodlineType.ACKERMAN) {
               ModNetworking.removeAckermanAttributes(player);
            }

            if (old == BloodlineType.ATRAIN) {
               AtrainSpeedTracker.clear(player);
               ModNetworking.removeAtrainAttributes(player);
            }

            data.setPower(
               player.getUuid(),
               BloodlineType.TRANSLUCENT,
               GrantProvenance.ofCommand((ServerCommandSource)context.getSource(), GrantProvenance.Issuer.COMMAND_DANNY)
            );
            ModNetworking.updateHomelanderAbilities(player, BloodlineType.TRANSLUCENT);
            player.sendMessage(buildBloodlineMessage(BloodlineType.TRANSLUCENT));
            ModNetworking.broadcastBloodlineToAll(player, BloodlineType.TRANSLUCENT.ordinal());
            count++;
         }
      }

      if (count == 1) {
         ServerPlayerEntity target = targets.iterator().next();
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Translucent bloodline to " + target.getName().getString()), false);
      } else {
         int finalCount = count;
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Translucent bloodline to " + finalCount + " players"), false);
      }

      return count;
   }

   private static int grantButcherSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         return grantButcherTo(context, List.of(player));
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantButcherTargets(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         return grantButcherTo(context, targets);
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int grantButcherTo(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      BloodlineData data = BloodlineData.get(server);
      int count = 0;

      for (ServerPlayerEntity player : targets) {
         if (!DannyAccess.mayHoldDannyPower(player.getUuid())) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal(player.getName().getString() + " is not on the permitted power-holder list."));
         } else {
            BloodlineType old = data.getBloodline(player.getUuid());
            if (old == BloodlineType.ACKERMAN) {
               ModNetworking.removeAckermanAttributes(player);
            }

            if (old == BloodlineType.ATRAIN) {
               AtrainSpeedTracker.clear(player);
               ModNetworking.removeAtrainAttributes(player);
            }

            if (old == BloodlineType.TRANSLUCENT) {
               TranslucentTracker.clear(player);
            }

            data.setPower(
               player.getUuid(),
               BloodlineType.BUTCHER,
               GrantProvenance.ofCommand((ServerCommandSource)context.getSource(), GrantProvenance.Issuer.COMMAND_DANNY)
            );
            ModNetworking.updateHomelanderAbilities(player, BloodlineType.BUTCHER);
            player.sendMessage(buildBloodlineMessage(BloodlineType.BUTCHER));
            ModNetworking.broadcastBloodlineToAll(player, BloodlineType.BUTCHER.ordinal());
            count++;
         }
      }

      if (count == 1) {
         ServerPlayerEntity target = targets.iterator().next();
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Butcher bloodline to " + target.getName().getString()), false);
      } else {
         int finalCount = count;
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Butcher bloodline to " + finalCount + " players"), false);
      }

      return count;
   }

   private static int removePowerSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         return removePowerTo(context, List.of(player));
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int removePowerTargets(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         return removePowerTo(context, targets);
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int removePowerTo(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      BloodlineData data = BloodlineData.get(server);
      int count = 0;

      for (ServerPlayerEntity player : targets) {
         BloodlineType existing = data.getPower(player.getUuid());
         if (existing != null) {
            if (existing == BloodlineType.ATRAIN) {
               AtrainSpeedTracker.clear(player);
               ModNetworking.removeAtrainAttributes(player);
            }

            if (existing == BloodlineType.TRANSLUCENT) {
               TranslucentTracker.clear(player);
            }

            data.removePower(player.getUuid());
            BloodlineType realBloodline = data.getRealBloodline(player.getUuid());
            ModNetworking.updateHomelanderAbilities(player, realBloodline);
            if (realBloodline == BloodlineType.ACKERMAN) {
               ModNetworking.applyAckermanAttributes(player);
            }

            player.sendMessage(Text.literal("Your ").append(existing.getStyledName()).append(Text.literal(" power has been removed.")));
            ModNetworking.broadcastBloodlineToAll(player, realBloodline != null ? realBloodline.ordinal() : -1);
            count++;
         }
      }

      if (count == 0) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("None of the targets had a Speakor-granted power to remove."));
         return 0;
      } else {
         if (count == 1) {
            ServerPlayerEntity target = targets.iterator().next();
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Removed power from " + target.getName().getString()), false);
         } else {
            int finalCount = count;
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Removed power from " + finalCount + " players"), false);
         }

         return count;
      }
   }

   private static int setBloodline(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         String bloodlineName = StringArgumentType.getString(context, "bloodline").toLowerCase();
         if (!VALID_BLOODLINES.contains(bloodlineName)) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Invalid bloodline. Use 'eldian', 'ackerman', 'royal', or 'marleyan'."));
            return 0;
         } else {
            BloodlineType type = BloodlineType.fromName(bloodlineName);
            if (type == null) {
               ((ServerCommandSource)context.getSource()).sendError(Text.literal("Invalid bloodline type."));
               return 0;
            } else {
               MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
               BloodlineData data = BloodlineData.get(server);
               int count = 0;

               for (ServerPlayerEntity player : targets) {
                  if (type == BloodlineType.ACKERMAN || type == BloodlineType.MARLEYAN) {
                     boolean hasShifterPowers = player.getCommandTags().contains("attack")
                        || player.getCommandTags().contains("colossal")
                        || player.getCommandTags().contains("armored")
                        || player.getCommandTags().contains("female")
                        || player.getCommandTags().contains("beast")
                        || player.getCommandTags().contains("warhammer")
                        || player.getCommandTags().contains("jaw");
                     if (hasShifterPowers) {
                        String reason = type == BloodlineType.ACKERMAN ? " - Subjects of Ymir cannot become Ackerman" : " - Marleyans are not Subjects of Ymir";
                        ((ServerCommandSource)context.getSource()).sendError(Text.literal(player.getName().getString() + reason));
                        continue;
                     }
                  }

                  BloodlineType oldBloodline = data.getRealBloodline(player.getUuid());
                  if (oldBloodline == BloodlineType.ACKERMAN && type != BloodlineType.ACKERMAN) {
                     ModNetworking.removeAckermanAttributes(player);
                  }

                  data.setBloodline(
                     player.getUuid(), type, GrantProvenance.ofCommand((ServerCommandSource)context.getSource(), GrantProvenance.Issuer.COMMAND_BLOODLINE_SET)
                  );
                  BloodlineType effective = data.getBloodline(player.getUuid());
                  ModNetworking.updateHomelanderAbilities(player, effective);
                  if (type == BloodlineType.ACKERMAN) {
                     VillagerTransformTracker.removeInjectedPlayer(player);
                     ModNetworking.applyAckermanAttributes(player);
                  }

                  if (type == BloodlineType.MARLEYAN) {
                     VillagerTransformTracker.removeInjectedPlayer(player);
                  }

                  player.sendMessage(buildBloodlineMessage(type));
                  ModNetworking.broadcastBloodlineToAll(player, effective.ordinal());
                  count++;
               }

               String displayName = type.getCommandName();
               if (count == 1) {
                  ServerPlayerEntity target = targets.iterator().next();
                  ((ServerCommandSource)context.getSource())
                     .sendFeedback(() -> Text.literal("Set " + target.getName().getString() + "'s bloodline to " + displayName), true);
               } else {
                  int finalCount = count;
                  ((ServerCommandSource)context.getSource())
                     .sendFeedback(() -> Text.literal("Set " + finalCount + " players' bloodline to " + displayName), true);
               }

               return count;
            }
         }
      } catch (Exception var11) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var11.getMessage()));
         return 0;
      }
   }

   private static int removeBloodline(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         String bloodlineName = StringArgumentType.getString(context, "bloodline").toLowerCase();
         if (!VALID_BLOODLINES.contains(bloodlineName)) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Invalid bloodline. Use 'eldian', 'ackerman', 'royal', or 'marleyan'."));
            return 0;
         } else {
            BloodlineType typeToRemove = BloodlineType.fromName(bloodlineName);
            if (typeToRemove == null) {
               ((ServerCommandSource)context.getSource()).sendError(Text.literal("Invalid bloodline type."));
               return 0;
            } else {
               MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
               BloodlineData data = BloodlineData.get(server);
               int count = 0;

               for (ServerPlayerEntity player : targets) {
                  BloodlineType existing = data.getRealBloodline(player.getUuid());
                  if (existing == typeToRemove) {
                     data.removeBloodline(player.getUuid());
                     if (typeToRemove == BloodlineType.ACKERMAN) {
                        ModNetworking.removeAckermanAttributes(player);
                     }

                     player.sendMessage(Text.literal("Your ").append(existing.getStyledName()).append(Text.literal(" bloodline has been removed.")));
                     BloodlineType effective = data.getBloodline(player.getUuid());
                     ModNetworking.broadcastBloodlineToAll(player, effective != null ? effective.ordinal() : -1);
                     count++;
                  }
               }

               if (count == 0) {
                  ((ServerCommandSource)context.getSource())
                     .sendError(Text.literal("No targeted players had the " + typeToRemove.getCommandName() + " bloodline."));
                  return 0;
               } else {
                  if (count == 1) {
                     ServerPlayerEntity target = targets.iterator().next();
                     ((ServerCommandSource)context.getSource())
                        .sendFeedback(() -> Text.literal("Removed " + typeToRemove.getCommandName() + " bloodline from " + target.getName().getString()), true);
                  } else {
                     int finalCount = count;
                     ((ServerCommandSource)context.getSource())
                        .sendFeedback(() -> Text.literal("Removed " + typeToRemove.getCommandName() + " bloodline from " + finalCount + " players"), true);
                  }

                  return count;
               }
            }
         }
      } catch (Exception var11) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var11.getMessage()));
         return 0;
      }
   }

   private static int checkBloodline(CommandContext<ServerCommandSource> context) {
      try {
         Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         BloodlineData data = BloodlineData.get(server);

         for (ServerPlayerEntity player : targets) {
            BloodlineType bloodline = data.getRealBloodline(player.getUuid());
            if (bloodline != null) {
               ((ServerCommandSource)context.getSource())
                  .sendFeedback(
                     () -> Text.literal(player.getName().getString() + " has ").append(bloodline.getStyledName()).append(Text.literal(" blood.")), false
                  );
            } else {
               ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(player.getName().getString() + " has no bloodline."), false);
            }
         }

         return targets.size();
      } catch (Exception var7) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var7.getMessage()));
         return 0;
      }
   }

   private static int checkBloodlineSelf(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         BloodlineData data = BloodlineData.get(server);
         BloodlineType bloodline = data.getRealBloodline(player.getUuid());
         if (bloodline != null) {
            ((ServerCommandSource)context.getSource())
               .sendFeedback(() -> Text.literal("You have ").append(bloodline.getStyledName()).append(Text.literal(" blood.")), false);
         } else {
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("You have no bloodline."), false);
         }

         return 1;
      } catch (Exception var5) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var5.getMessage()));
         return 0;
      }
   }

   private static int auditShifters(CommandContext<ServerCommandSource> context) {
      try {
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         ServerWorld overworld = server.getWorld(World.OVERWORLD);
         if (overworld == null) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Could not access world data."));
            return 0;
         } else {
            TitanPowerData powerData = TitanPowerData.get(overworld);
            int fixed = 0;
            int stripped = 0;
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("=== Shifter Audit ===").formatted(Formatting.GOLD), false);
            boolean multiShiftersAudit = server.getGameRules().getBoolean(DannysAot.RULE_MULTIPLE_SHIFTERS);

            for (TitanPowerType power : TitanPowerType.values()) {
               String tagName = power.getTagName();
               Set<UUID> trackedHolders = new HashSet<>(powerData.getPlayersWithPower(power));
               List<ServerPlayerEntity> playersWithTag = new ArrayList<>();

               for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                  if (!isDiscreetShifterUser(player) && player.getCommandTags().contains(tagName)) {
                     playersWithTag.add(player);
                  }
               }

               String displayName = power.getDisplayName();

               for (ServerPlayerEntity holder : playersWithTag) {
                  if (!trackedHolders.contains(holder.getUuid())) {
                     holder.removeScoreboardTag(tagName);
                     String pName = holder.getName().getString();
                     ((ServerCommandSource)context.getSource())
                        .sendFeedback(
                           () -> Text.literal("  " + displayName + ": Stripped unbacked tag from " + pName + " (no grant record)").formatted(Formatting.RED),
                           false
                        );
                     stripped++;
                  }
               }

               for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                  if (trackedHolders.contains(online.getUuid()) && !online.getCommandTags().contains(tagName)) {
                     online.addCommandTag(tagName);
                     String pName = online.getName().getString();
                     ((ServerCommandSource)context.getSource())
                        .sendFeedback(
                           () -> Text.literal("  " + displayName + ": Restored tag for " + pName + " (record exists)").formatted(Formatting.GREEN), false
                        );
                     fixed++;
                  }
               }

               if (!multiShiftersAudit && trackedHolders.size() > 1) {
                  UUID keeper = trackedHolders.iterator().next();

                  for (UUID extra : trackedHolders) {
                     if (!extra.equals(keeper)) {
                        powerData.removePlayerFromPower(extra, power);
                        ServerPlayerEntity onlinex = server.getPlayerManager().getPlayer(extra);
                        if (onlinex != null) {
                           onlinex.removeScoreboardTag(tagName);
                        }

                        ((ServerCommandSource)context.getSource())
                           .sendFeedback(
                              () -> Text.literal("  " + displayName + ": Released extra holder " + extra + " (multipleShifters is off)")
                                 .formatted(Formatting.YELLOW),
                              false
                           );
                        fixed++;
                     }
                  }
               }
            }

            int finalFixed = fixed;
            int finalStripped = stripped;
            ((ServerCommandSource)context.getSource())
               .sendFeedback(
                  () -> Text.literal("Audit complete: " + finalFixed + " registered, " + finalStripped + " duplicates stripped").formatted(Formatting.GOLD),
                  true
               );
            return fixed + stripped;
         }
      } catch (Exception var19) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var19.getMessage()));
         return 0;
      }
   }

   private static int resetAllShifters(CommandContext<ServerCommandSource> context) {
      try {
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         ServerWorld overworld = server.getWorld(World.OVERWORLD);
         if (overworld == null) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Could not access world data."));
            return 0;
         } else {
            TitanPowerData powerData = TitanPowerData.get(overworld);
            int onlineCount = 0;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               for (String tag : ALL_SHIFTER_TAGS) {
                  if (player.getCommandTags().contains(tag)) {
                     player.removeScoreboardTag(tag);
                     onlineCount++;
                     String pName = player.getName().getString();
                     ((ServerCommandSource)context.getSource())
                        .sendFeedback(() -> Text.literal("Removed " + tag + " from " + pName + " (online)").formatted(Formatting.RED), false);
                  }
               }
            }

            int offlineCount = 0;
            File playerDataDir = new File(server.getSavePath(WorldSavePath.PLAYERDATA).toFile().getAbsolutePath());
            if (playerDataDir.exists() && playerDataDir.isDirectory()) {
               File[] datFiles = playerDataDir.listFiles((dir, namex) -> namex.endsWith(".dat"));
               if (datFiles != null) {
                  Set<String> onlineUuids = new HashSet<>();

                  for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                     onlineUuids.add(p.getUuidAsString());
                  }

                  for (File datFile : datFiles) {
                     String fileName = datFile.getName().replace(".dat", "");
                     if (!onlineUuids.contains(fileName)) {
                        try {
                           NbtCompound nbt = NbtIo.readCompressed(datFile);
                           if (nbt.contains("Tags")) {
                              NbtList tags = nbt.getList("Tags", 8);
                              boolean modified = false;

                              for (int i = tags.size() - 1; i >= 0; i--) {
                                 String tagx = tags.getString(i);
                                 if (ALL_SHIFTER_TAGS.contains(tagx)) {
                                    tags.remove(i);
                                    modified = true;
                                    offlineCount++;
                                 }
                              }

                              if (modified) {
                                 NbtIo.writeCompressed(nbt, datFile);

                                 try {
                                    UUID uuid = UUID.fromString(fileName);
                                    Optional<GameProfile> profile = server.getUserCache().getByUuid(uuid);
                                    String name = profile.<String>map(GameProfile::getName).orElse(fileName);
                                    ((ServerCommandSource)context.getSource())
                                       .sendFeedback(() -> Text.literal("Removed shifter tags from " + name + " (offline)").formatted(Formatting.RED), false);
                                 } catch (Exception var21) {
                                    ((ServerCommandSource)context.getSource())
                                       .sendFeedback(
                                          () -> Text.literal("Removed shifter tags from " + fileName + " (offline)").formatted(Formatting.RED), false
                                       );
                                 }
                              }
                           }
                        } catch (Exception var22) {
                           DannysAot.LOGGER.warn("Failed to process playerdata file: " + datFile.getName(), var22);
                        }
                     }
                  }
               }
            }

            for (TitanPowerType power : TitanPowerType.values()) {
               powerData.clearPlayerPower(power);
            }

            powerData.clearAllVillagerPowers();
            int finalOnline = onlineCount;
            int finalOffline = offlineCount;
            ((ServerCommandSource)context.getSource())
               .sendFeedback(
                  () -> Text.literal(
                        "Reset complete: Stripped tags from "
                           + finalOnline
                           + " online + "
                           + finalOffline
                           + " offline players. All power data and powered villagers cleared."
                     )
                     .formatted(Formatting.GOLD),
                  true
               );
            return onlineCount + offlineCount;
         }
      } catch (Exception var23) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var23.getMessage()));
         return 0;
      }
   }

   private static int toggleFounder(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         toggleFounderFor(context, player);
         return 1;
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int toggleFounderTargets(CommandContext<ServerCommandSource> context) {
      try {
         int count = 0;

         for (ServerPlayerEntity target : EntityArgumentType.getPlayers(context, "targets")) {
            toggleFounderFor(context, target);
            count++;
         }

         return count;
      } catch (Exception var4) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var4.getMessage()));
         return 0;
      }
   }

   private static void toggleFounderFor(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
      String tag = "founder";
      String name = player.getName().getString();
      if (player.getCommandTags().contains(tag)) {
         player.removeScoreboardTag(tag);
         recordExtraTagGrant((ServerCommandSource)context.getSource(), player, tag, false);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Founder power removed from " + name + "."), false);
      } else {
         player.addCommandTag(tag);
         recordExtraTagGrant((ServerCommandSource)context.getSource(), player, tag, true);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Founder power granted to " + name + "."), false);
      }
   }

   private static void recordExtraTagGrant(ServerCommandSource source, ServerPlayerEntity player, String tag, boolean granted) {
      try {
         TitanPowerData data = TitanPowerData.get(source.getServer().getOverworld());
         if (granted) {
            data.addExtraTagGrant(player.getUuid(), tag);
         } else {
            data.removeExtraTagGrant(player.getUuid(), tag);
         }
      } catch (Throwable var5) {
         DannysAot.LOGGER
            .error(
               "Failed to record '{}' grant for {} — the tag will be stripped by PowerAuthority because nothing backs it",
               new Object[]{tag, player.getName().getString(), var5}
            );
      }
   }

   private static int toggleTripleT(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         String tag = "triple_t";
         if (player.getCommandTags().contains(tag)) {
            player.removeScoreboardTag(tag);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Triple T power removed."), false);
         } else {
            player.addCommandTag(tag);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Triple T power granted."), false);
         }

         return 1;
      } catch (Exception var3) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var3.getMessage()));
         return 0;
      }
   }

   private static int toggleOgreShifter(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         String tag = "ogre_shifter";
         if (player.getCommandTags().contains(tag)) {
            player.removeScoreboardTag(tag);
            ModNetworking.removeOgreShifterAttributes(player);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Ogre Shifter power removed."), false);
         } else {
            player.addCommandTag(tag);
            ModNetworking.applyOgreShifterAttributes(player);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Ogre Shifter power granted."), false);
         }

         return 1;
      } catch (Exception var3) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var3.getMessage()));
         return 0;
      }
   }

   private static int toggleTitanBloodline(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         String tag = "titan_bloodline";
         if (player.getCommandTags().contains(tag)) {
            player.removeScoreboardTag(tag);
            ModNetworking.removeTitanBloodlineAttributes(player);
            ModNetworking.sendTitanBloodlineToPlayer(player);
            TitanDashTracker.clear(player);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Titan bloodline removed."), false);
         } else {
            player.addCommandTag(tag);
            ModNetworking.applyTitanBloodlineAttributes(player);
            ModNetworking.sendTitanBloodlineToPlayer(player);
            TitanDashTracker.ensureAndSync(player);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Titan bloodline granted."), false);
         }

         return 1;
      } catch (Exception var3) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var3.getMessage()));
         return 0;
      }
   }

   private static int toggleCartShifter(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         toggleCartShifterFor(context, player);
         return 1;
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int toggleCartShifterTargets(CommandContext<ServerCommandSource> context) {
      try {
         int count = 0;

         for (ServerPlayerEntity target : EntityArgumentType.getPlayers(context, "targets")) {
            toggleCartShifterFor(context, target);
            count++;
         }

         return count;
      } catch (Exception var4) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var4.getMessage()));
         return 0;
      }
   }

   private static void toggleCartShifterFor(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
      String tag = "cart_shifter";
      String name = player.getName().getString();
      if (player.getCommandTags().contains(tag)) {
         player.removeScoreboardTag(tag);
         recordExtraTagGrant((ServerCommandSource)context.getSource(), player, tag, false);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Cart Shifter power removed from " + name + "."), false);
      } else {
         player.addCommandTag(tag);
         recordExtraTagGrant((ServerCommandSource)context.getSource(), player, tag, true);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Cart Shifter power granted to " + name + "."), false);
      }
   }

   private static int toggleFounderChat(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         if (founderChatActive.contains(player.getUuid())) {
            founderChatActive.remove(player.getUuid());
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Founder Chat disabled."), false);
         } else {
            founderChatActive.add(player.getUuid());
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Founder Chat enabled."), false);
         }

         return 1;
      } catch (Exception var2) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var2.getMessage()));
         return 0;
      }
   }

   private static int giveKennyHat(CommandContext<ServerCommandSource> context) {
      try {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         ItemStack hat = new ItemStack(DannysAot.KENNY_HAT);
         if (!player.getInventory().insertStack(hat)) {
            player.dropItem(hat, false);
         }

         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Granted Kenny's Hat."), false);
         return 1;
      } catch (Exception var3) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Error: " + var3.getMessage()));
         return 0;
      }
   }

   private static int playDannySound(CommandContext<ServerCommandSource> context) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         player.playSound(ModSounds.DANNY, SoundCategory.MASTER, 1.0F, 1.0F);
      }

      ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Playing danny sound for all players."), false);
      return 1;
   }

   private static LinkedHashMap<String, Key<BooleanRule>> getModGamerules() {
      if (modGamerules == null) {
         modGamerules = new LinkedHashMap<>();
         modGamerules.put("titanGriefing", DannysAot.RULE_TITAN_GRIEFING);
         modGamerules.put("fairTitanPowerLoss", DannysAot.RULE_FAIR_TITAN_POWER_LOSS);
         modGamerules.put("villagersSpawnWithPowers", DannysAot.RULE_VILLAGERS_SPAWN_WITH_POWERS);
         modGamerules.put("injectOtherPlayers", DannysAot.RULE_INJECT_OTHER_PLAYERS);
         modGamerules.put("allowODM", DannysAot.RULE_ALLOW_ODM);
         modGamerules.put("allowShifting", DannysAot.RULE_ALLOW_SHIFTING);
         modGamerules.put("allowSelfInject", DannysAot.RULE_ALLOW_SELF_INJECT);
         modGamerules.put("allowThunderSpears", DannysAot.RULE_ALLOW_THUNDER_SPEARS);
         modGamerules.put("thunderSpearGriefing", DannysAot.RULE_THUNDER_SPEAR_GRIEFING);
         modGamerules.put("unfairPureTitans", DannysAot.RULE_UNFAIR_PURE_TITANS);
         modGamerules.put("allowCuffing", DannysAot.RULE_ALLOW_CUFFING);
         modGamerules.put("shifterSnitching", DannysAot.RULE_SHIFTER_SNITCHING);
         modGamerules.put("shiftBossbars", DannysAot.RULE_SHIFT_BOSS_BARS);
         modGamerules.put("ShifterExplosionDamage", DannysAot.RULE_SHIFTER_EXPLOSION_DAMAGE);
         modGamerules.put("YmirCurse", DannysAot.RULE_YMIR_CURSE);
         modGamerules.put("confirmedTitanShifterKill", DannysAot.RULE_CONFIRMED_TITAN_SHIFTER_KILL);
         modGamerules.put("weatherAffectsODM", DannysAot.RULE_WEATHER_AFFECTS_ODM);
         modGamerules.put("kineticODMdamage", DannysAot.RULE_KINETIC_ODM_DAMAGE);
         modGamerules.put("forceShifting", DannysAot.RULE_FORCE_SHIFTING);
         modGamerules.put("realisticResourceUse", DannysAot.RULE_REALISTIC_RESOURCE_USE);
         modGamerules.put("titanGore", DannysAot.RULE_TITAN_GORE);
         modGamerules.put("multipleShifters", DannysAot.RULE_MULTIPLE_SHIFTERS);
         modGamerules.put("allowRerolls", DannysAot.RULE_ALLOW_REROLLS);
         modGamerules.put("allowOgreSpawns", DannysAot.RULE_ALLOW_OGRE_SPAWNS);
         modGamerules.put("grassODM", DannysAot.RULE_GRASS_ODM);
         modGamerules.put("chargedODMAttacks", DannysAot.RULE_CHARGED_ODM_ATTACKS);
      }

      return modGamerules;
   }

   private static CompletableFuture<Suggestions> suggestGameruleNames(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      for (String name : getModGamerules().keySet()) {
         if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   private static int listGamerules(CommandContext<ServerCommandSource> context) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      GameRules rules = server.getGameRules();
      ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("=== Danny's AoT Gamerules ===").formatted(Formatting.GOLD), false);

      for (Entry<String, Key<BooleanRule>> entry : getModGamerules().entrySet()) {
         String name = entry.getKey();
         boolean value = rules.getBoolean(entry.getValue());
         Formatting color = value ? Formatting.GREEN : Formatting.RED;
         ((ServerCommandSource)context.getSource())
            .sendFeedback(() -> Text.literal("  " + name + ": ").append(Text.literal(String.valueOf(value)).formatted(color)), false);
      }

      return 1;
   }

   private static int showGamerule(CommandContext<ServerCommandSource> context) {
      String ruleName = StringArgumentType.getString(context, "rule");
      Key<BooleanRule> key = getModGamerules().get(ruleName);
      if (key == null) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Unknown gamerule: " + ruleName));
         return 0;
      } else {
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         boolean value = server.getGameRules().getBoolean(key);
         Formatting color = value ? Formatting.GREEN : Formatting.RED;
         ((ServerCommandSource)context.getSource())
            .sendFeedback(() -> Text.literal(ruleName + ": ").append(Text.literal(String.valueOf(value)).formatted(color)), false);
         return 1;
      }
   }

   private static int setGamerule(CommandContext<ServerCommandSource> context) {
      String ruleName = StringArgumentType.getString(context, "rule");
      boolean value = BoolArgumentType.getBool(context, "value");
      Key<BooleanRule> key = getModGamerules().get(ruleName);
      if (key == null) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Unknown gamerule: " + ruleName));
         return 0;
      } else {
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         BooleanRule rule = server.getGameRules().get(key);
         rule.set(value, server);
         if (key == DannysAot.RULE_ALLOW_ODM) {
            ModNetworking.broadcastAllowODM(server);
         } else if (key == DannysAot.RULE_ALLOW_SHIFTING) {
            ModNetworking.broadcastAllowShifting(server);
         } else if (key == DannysAot.RULE_ALLOW_THUNDER_SPEARS) {
            ModNetworking.broadcastAllowThunderSpears(server);
         }

         Formatting color = value ? Formatting.GREEN : Formatting.RED;
         ((ServerCommandSource)context.getSource())
            .sendFeedback(() -> Text.literal("Set " + ruleName + " to ").append(Text.literal(String.valueOf(value)).formatted(color)), true);
         return 1;
      }
   }

   private static int summonRideableCrawler(ServerPlayerEntity player) {
      ServerWorld level = player.getServerWorld();
      CrawlerTitanEntity crawler = new CrawlerTitanEntity(DannysAot.CRAWLER_TITAN, level);
      crawler.setRideable(true);
      float yawRad = (float)Math.toRadians(player.getYaw());
      double spawnX = player.getX() - Math.sin(yawRad) * 2.0;
      double spawnZ = player.getZ() + Math.cos(yawRad) * 2.0;
      crawler.setPosition(spawnX, player.getY(), spawnZ);
      crawler.setYaw(player.getYaw());
      crawler.bodyYaw = player.getYaw();
      level.spawnEntity(crawler);
      player.startRiding(crawler, true);
      player.sendMessage(Text.literal("Summoned rideable Crawler.").styled(s -> s.withColor(16746496)));
      return 1;
   }

   private static int summonRideableSadTitan(ServerPlayerEntity player) {
      ServerWorld level = player.getServerWorld();
      SadTitanEntity sadTitan = new SadTitanEntity(DannysAot.SAD_TITAN, level);
      sadTitan.setRideable(true);
      float yawRad = (float)Math.toRadians(player.getYaw());
      double spawnX = player.getX() - Math.sin(yawRad) * 2.0;
      double spawnZ = player.getZ() + Math.cos(yawRad) * 2.0;
      sadTitan.setPosition(spawnX, player.getY(), spawnZ);
      sadTitan.setYaw(player.getYaw());
      sadTitan.bodyYaw = player.getYaw();
      level.spawnEntity(sadTitan);
      player.startRiding(sadTitan, true);
      player.sendMessage(Text.literal("Summoned rideable Sad Titan.").styled(s -> s.withColor(16746496)));
      return 1;
   }
}
