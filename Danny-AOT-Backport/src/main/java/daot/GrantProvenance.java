package daot;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public record GrantProvenance(GrantProvenance.Issuer issuer, String issuerName, UUID issuerUuid, long gameTime) {
   public static GrantProvenance ofCommand(ServerCommandSource source, GrantProvenance.Issuer issuer) {
      String name = "Console";
      UUID uuid = null;
      if (source != null) {
         try {
            name = source.getName();
         } catch (Throwable var6) {
         }

         if (source.getEntity() instanceof ServerPlayerEntity sp) {
            uuid = sp.getUuid();
         }
      }

      return new GrantProvenance(issuer, name, uuid, gameTimeOf(source == null ? null : source.getServer()));
   }

   public static GrantProvenance ofInheritance(MinecraftServer server) {
      return new GrantProvenance(GrantProvenance.Issuer.INHERITANCE, "<earned>", null, gameTimeOf(server));
   }

   public static GrantProvenance ofRoll(MinecraftServer server) {
      return new GrantProvenance(GrantProvenance.Issuer.ROLL, "<rolled>", null, gameTimeOf(server));
   }

   public static GrantProvenance unknown(MinecraftServer server) {
      return new GrantProvenance(GrantProvenance.Issuer.UNKNOWN, "<internal>", null, gameTimeOf(server));
   }

   public static GrantProvenance migrated() {
      return new GrantProvenance(GrantProvenance.Issuer.MIGRATION, "<pre-2.5.0>", null, 0L);
   }

   private static long gameTimeOf(MinecraftServer server) {
      if (server == null) {
         return 0L;
      } else {
         try {
            return server.getOverworld().getTime();
         } catch (Throwable var2) {
            return 0L;
         }
      }
   }

   public NbtCompound save() {
      NbtCompound tag = new NbtCompound();
      tag.putInt("issuer", this.issuer.ordinal());
      if (this.issuerName != null) {
         tag.putString("issuerName", this.issuerName);
      }

      if (this.issuerUuid != null) {
         tag.putUuid("issuerUuid", this.issuerUuid);
      }

      tag.putLong("gameTime", this.gameTime);
      return tag;
   }

   public static GrantProvenance load(NbtCompound tag) {
      return tag == null
         ? migrated()
         : new GrantProvenance(
            GrantProvenance.Issuer.fromOrdinal(tag.getInt("issuer")),
            tag.contains("issuerName") ? tag.getString("issuerName") : "<unknown>",
            tag.containsUuid("issuerUuid") ? tag.getUuid("issuerUuid") : null,
            tag.getLong("gameTime")
         );
   }

   public String describe() {
      return switch (this.issuer) {
         case INHERITANCE -> "earned in-game";
         default -> this.issuerName + " via " + this.issuer.name().toLowerCase(Locale.ROOT);
         case MIGRATION -> "pre-2.5.0 (migrated)";
         case UNKNOWN -> "internal";
         case ROLL -> "rolled";
      };
   }

   public static enum Issuer {
      COMMAND_SHIFTER_SET,
      COMMAND_DANNY,
      INHERITANCE,
      NETWORK_GRANT,
      MIGRATION,
      UNKNOWN,
      COMMAND_BLOODLINE_SET,
      ROLL;

      public static GrantProvenance.Issuer fromOrdinal(int ordinal) {
         GrantProvenance.Issuer[] values = values();
         return ordinal >= 0 && ordinal < values.length ? values[ordinal] : UNKNOWN;
      }
   }
}
