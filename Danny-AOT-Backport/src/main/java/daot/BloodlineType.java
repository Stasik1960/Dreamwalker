package daot;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public enum BloodlineType {
   ELDIAN(Formatting.AQUA),
   ACKERMAN(Formatting.BLACK),
   ROYAL(Formatting.GOLD),
   MARLEYAN(9849600),
   HOMELANDER(Formatting.RED),
   SOLDIERBOY(Formatting.YELLOW),
   ATRAIN(Formatting.LIGHT_PURPLE),
   TRANSLUCENT(8369090),
   BUTCHER(8912896);

   private final TextColor color;

   private BloodlineType(Formatting color) {
      this.color = TextColor.fromFormatting(color);
   }

   private BloodlineType(int rgb) {
      this.color = TextColor.fromRgb(rgb);
   }

   public MutableText getStyledName() {
      String name = this.name().charAt(0) + this.name().substring(1).toLowerCase();
      return Text.literal(name).setStyle(Style.EMPTY.withColor(this.color).withBold(true));
   }

   public String getCommandName() {
      return this.name().toLowerCase();
   }

   public boolean isPower() {
      return this == HOMELANDER || this == SOLDIERBOY || this == ATRAIN || this == TRANSLUCENT || this == BUTCHER;
   }

   public static BloodlineType fromOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
   }

   public static BloodlineType fromName(String name) {
      for (BloodlineType type : values()) {
         if (type.name().equalsIgnoreCase(name)) {
            return type;
         }
      }

      return null;
   }
}
