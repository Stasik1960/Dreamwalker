package daot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import daot.network.ModNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class DmnkCommands {
   private static final RegistryKey<World> PARADIS = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
   private static final SuggestionProvider<ServerCommandSource> WALL_SUGGEST = (ctx, builder) -> CommandSource.suggestMatching(
      BreachManager.WALL_NAMES, builder
   );
   private static final SuggestionProvider<ServerCommandSource> DISTRICT_FOR_WALL_SUGGEST = (ctx, builder) -> {
      int ring = BreachManager.wallIndexByName(StringArgumentType.getString(ctx, "wall"));
      List<String> names = new ArrayList<>();
      if (ring >= 0) {
         for (int id = ring * 4; id < ring * 4 + 4; id++) {
            names.add(BreachManager.districtName(id));
         }
      }

      return CommandSource.suggestMatching(names, builder);
   };
   private static final SuggestionProvider<ServerCommandSource> CLEAR_SUGGEST = (ctx, builder) -> {
      BreachManager data = BreachManager.get(((ServerCommandSource)ctx.getSource()).getServer());
      List<String> options = new ArrayList<>();

      for (int ring : data.getBreachedWalls()) {
         options.add(BreachManager.wallName(ring));
      }

      for (int id : data.getBreachedDistricts()) {
         options.add(BreachManager.districtName(id));
      }

      if (!options.isEmpty()) {
         options.add("all");
      }

      return CommandSource.suggestMatching(options, builder);
   };

   private DmnkCommands() {
   }

   public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("dmnk").requires(src -> src.hasPermissionLevel(2)))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("breach")
                              .then(
                                 CommandManager.literal("wall")
                                    .then(CommandManager.argument("wall", StringArgumentType.word()).suggests(WALL_SUGGEST).executes(DmnkCommands::breachWall))
                              ))
                           .then(
                              CommandManager.literal("district")
                                 .then(
                                    CommandManager.argument("wall", StringArgumentType.word())
                                       .suggests(WALL_SUGGEST)
                                       .then(
                                          CommandManager.argument("district", StringArgumentType.word())
                                             .suggests(DISTRICT_FOR_WALL_SUGGEST)
                                             .executes(DmnkCommands::breachDistrict)
                                       )
                                 )
                           ))
                        .then(
                           CommandManager.literal("clear")
                              .then(CommandManager.argument("target", StringArgumentType.word()).suggests(CLEAR_SUGGEST).executes(DmnkCommands::clear))
                        ))
                     .then(CommandManager.literal("check").executes(DmnkCommands::check))
               ))
            .then(
               ((LiteralArgumentBuilder)CommandManager.literal("fog").then(CommandManager.literal("start").executes(DmnkCommands::fogStart)))
                  .then(CommandManager.literal("end").executes(DmnkCommands::fogEnd))
            )
      );
   }

   private static int breachWall(CommandContext<ServerCommandSource> ctx) {
      String name = StringArgumentType.getString(ctx, "wall");
      int ring = BreachManager.wallIndexByName(name);
      if (ring < 0) {
         ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("Unknown wall '" + name + "'. Valid walls: Maria, Rose, Sina."));
         return 0;
      } else {
         BreachManager data = BreachManager.get(((ServerCommandSource)ctx.getSource()).getServer());
         if (data.breachWall(ring)) {
            ModNetworking.broadcastFogState(((ServerCommandSource)ctx.getSource()).getServer());
            ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Wall " + BreachManager.wallName(ring) + " Breached."), true);
         } else {
            ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Wall " + BreachManager.wallName(ring) + " is already breached."), false);
         }

         return 1;
      }
   }

   private static int breachDistrict(CommandContext<ServerCommandSource> ctx) {
      String wallName = StringArgumentType.getString(ctx, "wall");
      int ring = BreachManager.wallIndexByName(wallName);
      if (ring < 0) {
         ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("Unknown wall '" + wallName + "'. Valid walls: Maria, Rose, Sina."));
         return 0;
      } else {
         String name = StringArgumentType.getString(ctx, "district");
         int id = BreachManager.districtIdByName(name);
         if (id >= 0 && BreachManager.wallOfDistrict(id) == ring) {
            BreachManager data = BreachManager.get(((ServerCommandSource)ctx.getSource()).getServer());
            if (data.breachDistrict(id)) {
               ModNetworking.broadcastFogState(((ServerCommandSource)ctx.getSource()).getServer());
               ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("District " + BreachManager.districtName(id) + " Breached."), true);
            } else {
               ((ServerCommandSource)ctx.getSource())
                  .sendFeedback(() -> Text.literal("District " + BreachManager.districtName(id) + " is already breached."), false);
            }

            return 1;
         } else {
            ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("'" + name + "' is not a district of Wall " + BreachManager.wallName(ring) + "."));
            return 0;
         }
      }
   }

   private static int clear(CommandContext<ServerCommandSource> ctx) {
      String target = StringArgumentType.getString(ctx, "target");
      BreachManager data = BreachManager.get(((ServerCommandSource)ctx.getSource()).getServer());
      if ("all".equalsIgnoreCase(target)) {
         if (data.clearAll()) {
            ModNetworking.broadcastFogState(((ServerCommandSource)ctx.getSource()).getServer());
            ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("All Breaches Cleared."), true);
         } else {
            ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Nothing is currently breached."), false);
         }

         return 1;
      } else {
         int ring = BreachManager.wallIndexByName(target);
         if (ring >= 0) {
            if (data.clearWall(ring)) {
               ModNetworking.broadcastFogState(((ServerCommandSource)ctx.getSource()).getServer());
               ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Wall " + BreachManager.wallName(ring) + " Cleared."), true);
            } else {
               ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Wall " + BreachManager.wallName(ring) + " was not breached."), false);
            }

            return 1;
         } else {
            int id = BreachManager.districtIdByName(target);
            if (id >= 0) {
               if (data.clearDistrict(id)) {
                  ModNetworking.broadcastFogState(((ServerCommandSource)ctx.getSource()).getServer());
                  ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("District " + BreachManager.districtName(id) + " Cleared."), true);
               } else {
                  ((ServerCommandSource)ctx.getSource())
                     .sendFeedback(() -> Text.literal("District " + BreachManager.districtName(id) + " was not breached."), false);
               }

               return 1;
            } else {
               ((ServerCommandSource)ctx.getSource())
                  .sendError(Text.literal("Unknown wall/district '" + target + "'. Use /dmnk breach clear <tab> to see breached ones, or 'all'."));
               return 0;
            }
         }
      }
   }

   private static int check(CommandContext<ServerCommandSource> ctx) {
      BreachManager data = BreachManager.get(((ServerCommandSource)ctx.getSource()).getServer());
      List<Integer> walls = new ArrayList<>(data.getBreachedWalls());
      List<Integer> districts = new ArrayList<>(data.getBreachedDistricts());
      if (walls.isEmpty() && districts.isEmpty()) {
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Nothing is currently breached."), false);
         return 1;
      } else {
         DmnkCommands.MutableBuilder msg = new DmnkCommands.MutableBuilder();
         msg.line("Current breaches:", Formatting.GOLD);
         if (!walls.isEmpty()) {
            StringBuilder sb = new StringBuilder("  Walls: ");

            for (int i = 0; i < walls.size(); i++) {
               if (i > 0) {
                  sb.append(", ");
               }

               sb.append(BreachManager.wallName(walls.get(i)));
            }

            msg.line(sb.toString(), Formatting.RED);
         }

         if (!districts.isEmpty()) {
            StringBuilder sb = new StringBuilder("  Districts: ");

            for (int i = 0; i < districts.size(); i++) {
               if (i > 0) {
                  sb.append(", ");
               }

               sb.append(districtLabel(districts.get(i)));
            }

            msg.line(sb.toString(), Formatting.YELLOW);
         }

         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> msg.build(), false);
         return 1;
      }
   }

   private static int fogStart(CommandContext<ServerCommandSource> ctx) {
      MinecraftServer server = ((ServerCommandSource)ctx.getSource()).getServer();
      if (FogEventState.isActive()) {
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("The fog event is already active."), false);
         return 0;
      } else {
         FogEventState.setActive(true);
         ServerWorld paradis = server.getWorld(PARADIS);
         applyParadisRain(paradis, true);
         Text rollIn = Text.literal("A deep fog rolls in...").formatted(Formatting.RED, Formatting.BOLD);

         for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getWorld() == paradis && BreachManager.isFogZone(server, p.getBlockX(), p.getBlockZ())) {
               p.sendMessage(rollIn, false);
            }
         }

         ModNetworking.broadcastFogState(server);
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Fog event started."), true);
         return 1;
      }
   }

   private static int fogEnd(CommandContext<ServerCommandSource> ctx) {
      MinecraftServer server = ((ServerCommandSource)ctx.getSource()).getServer();
      if (!FogEventState.isActive()) {
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("The fog event is not active."), false);
         return 0;
      } else {
         FogEventState.setActive(false);
         applyParadisRain(server.getWorld(PARADIS), false);
         ModNetworking.broadcastFogState(server);
         ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("Fog event ended."), true);
         return 1;
      }
   }

   private static void applyParadisRain(ServerWorld paradis, boolean raining) {
      if (paradis != null) {
         if (raining) {
            paradis.setWeather(0, Integer.MAX_VALUE, true, false);
         } else {
            paradis.setWeather(Integer.MAX_VALUE, 0, false, false);
         }

         GameStateChangeS2CPacket toggle = new GameStateChangeS2CPacket(
            raining ? GameStateChangeS2CPacket.RAIN_STARTED : GameStateChangeS2CPacket.RAIN_STOPPED, 0.0F
         );
         GameStateChangeS2CPacket level = new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, raining ? 1.0F : 0.0F);

         for (ServerPlayerEntity p : paradis.getPlayers()) {
            p.networkHandler.sendPacket(toggle);
            p.networkHandler.sendPacket(level);
         }
      }
   }

   private static String districtLabel(int id) {
      return BreachManager.districtName(id) + " (" + BreachManager.wallName(BreachManager.wallOfDistrict(id)) + ")";
   }

   private static final class MutableBuilder {
      private final MutableText root = Text.empty();
      private boolean first = true;

      void line(String text, Formatting color) {
         if (!this.first) {
            this.root.append(Text.literal("\n"));
         }

         this.root.append(Text.literal(text).formatted(color));
         this.first = false;
      }

      MutableText build() {
         return this.root;
      }
   }
}
