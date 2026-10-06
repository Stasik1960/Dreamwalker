package dev.dreamwalker.bloodbornedw;
import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import dev.dreamwalker.bloodbornedw.visual.VisualService;
import dev.dreamwalker.bloodbornedw.command.VisualCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TallBlockItem;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
public final class BloodborneDw implements ModInitializer {
 public static final String ID="bloodborne_dw";
 @Override public void onInitialize() {
  DwBlocks.initialize();
  for(var entry:DwBlocks.entries()) {
   Item item=entry.block() instanceof DoorBlock?new TallBlockItem(entry.block(),new Item.Settings()):new BlockItem(entry.block(),new Item.Settings());
   Registry.register(Registries.ITEM,entry.identifier(),item);
  }
  VisualService.initialize(); VisualCommands.register();
  Registry.register(Registries.ITEM_GROUP,new Identifier(ID,"complete"),FabricItemGroup.builder()
   .displayName(Text.literal("bloodborne-dw"))
   .icon(()->new ItemStack(DwBlocks.entries().iterator().next().block()))
   .entries((context,entries)->{for(var e:DwBlocks.entries())entries.add(e.block());}).build());
 }
}
