package daot;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FlareCartridgeItem extends Item {
   private final FlareCartridgeItem.FlareColor color;

   public FlareCartridgeItem(Settings properties, FlareCartridgeItem.FlareColor color) {
      super(properties);
      this.color = color;
   }

   public FlareCartridgeItem.FlareColor getColor() {
      return this.color;
   }

   public static FlareCartridgeItem getItemForColor(FlareCartridgeItem.FlareColor color) {
      return switch (color) {
         case RED -> DannysAot.RED_FLARE_CARTRIDGE;
         case BLACK -> DannysAot.BLACK_FLARE_CARTRIDGE;
         case PURPLE -> DannysAot.PURPLE_FLARE_CARTRIDGE;
         case BLUE -> DannysAot.BLUE_FLARE_CARTRIDGE;
         case GREEN -> DannysAot.GREEN_FLARE_CARTRIDGE;
         case YELLOW -> DannysAot.YELLOW_FLARE_CARTRIDGE;
      };
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      MutableText tooltipx;
      switch (this.color) {
         case RED:
            tooltipx = Text.literal("Titan")
               .formatted(Formatting.RED, Formatting.BOLD, Formatting.ITALIC)
               .append(Text.literal(" Spotted").formatted(Formatting.WHITE, Formatting.BOLD));
            break;
         case BLACK:
            tooltipx = Text.literal("Abnormal Titan")
               .formatted(Formatting.DARK_RED, Formatting.BOLD, Formatting.ITALIC)
               .append(Text.literal(" Spotted").formatted(Formatting.WHITE, Formatting.BOLD));
            break;
         case PURPLE:
            tooltipx = Text.literal("Emergency")
               .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD, Formatting.ITALIC)
               .append(Text.literal(" at this location").formatted(Formatting.WHITE, Formatting.BOLD));
            break;
         case BLUE:
            tooltipx = Text.literal("Order to ")
               .formatted(Formatting.WHITE, Formatting.BOLD)
               .append(Text.literal("retreat").formatted(Formatting.BLUE, Formatting.BOLD, Formatting.ITALIC));
            break;
         case GREEN:
            tooltipx = Text.literal("Change ")
               .formatted(Formatting.WHITE, Formatting.BOLD)
               .append(Text.literal("Direction").formatted(Formatting.GREEN, Formatting.BOLD, Formatting.ITALIC));
            break;
         case YELLOW:
            tooltipx = Text.literal("Mission ")
               .formatted(Formatting.WHITE, Formatting.BOLD)
               .append(Text.literal("Terminated").formatted(Formatting.YELLOW, Formatting.BOLD, Formatting.ITALIC));
            break;
         default:
            return;
      }

      tooltip.add(tooltipx);
      super.appendTooltip(stack, context, tooltip, type);
   }

   public static enum FlareColor {
      RED,
      BLACK,
      PURPLE,
      BLUE,
      GREEN,
      YELLOW;
   }
}
