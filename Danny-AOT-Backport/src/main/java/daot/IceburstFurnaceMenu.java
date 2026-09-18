package daot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class IceburstFurnaceMenu extends ScreenHandler {
   private final Inventory container;
   private final PropertyDelegate data;

   public IceburstFurnaceMenu(int containerId, PlayerInventory playerInventory) {
      this(containerId, playerInventory, new SimpleInventory(4), new ArrayPropertyDelegate(4));
   }

   public IceburstFurnaceMenu(int containerId, PlayerInventory playerInventory, Inventory container, PropertyDelegate data) {
      super(DannysAot.ICEBURST_FURNACE_MENU, containerId);
      this.container = container;
      this.data = data;
      checkSize(container, 4);
      checkDataCount(data, 4);
      this.addSlot(new Slot(container, 0, 44, 17) {
         @Override
         public boolean canInsert(ItemStack stack) {
            return stack.isOf(DannysAot.IRON_BAMBOO_ITEM) || stack.isOf(DannysAot.HARDENED_IRON_BAMBOO) || stack.isOf(DannysAot.ICE_BURST_CLUSTER);
         }
      });
      this.addSlot(new Slot(container, 1, 68, 17) {
         @Override
         public boolean canInsert(ItemStack stack) {
            return stack.isOf(Items.RAW_IRON) || stack.isOf(Items.IRON_INGOT) || stack.getItem() instanceof GasCanisterItem;
         }
      });
      this.addSlot(new Slot(container, 2, 56, 53) {
         @Override
         public boolean canInsert(ItemStack stack) {
            return IceburstFurnaceMenu.isFuel(stack);
         }
      });
      this.addSlot(new Slot(container, 3, 116, 35) {
         @Override
         public boolean canInsert(ItemStack stack) {
            return false;
         }
      });

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
      }

      this.addProperties(data);
   }

   private static boolean isFuel(ItemStack stack) {
      return stack.isEmpty()
         ? false
         : stack.isOf(Items.COAL)
            || stack.isOf(Items.CHARCOAL)
            || stack.isOf(Items.COAL_BLOCK)
            || stack.isOf(Items.BLAZE_ROD)
            || stack.isOf(Items.LAVA_BUCKET)
            || stack.isOf(Items.OAK_LOG)
            || stack.isOf(Items.SPRUCE_LOG)
            || stack.isOf(Items.BIRCH_LOG)
            || stack.isOf(Items.JUNGLE_LOG)
            || stack.isOf(Items.ACACIA_LOG)
            || stack.isOf(Items.DARK_OAK_LOG)
            || stack.isOf(Items.MANGROVE_LOG)
            || stack.isOf(Items.CHERRY_LOG)
            || stack.isOf(Items.OAK_PLANKS)
            || stack.isOf(Items.SPRUCE_PLANKS)
            || stack.isOf(Items.BIRCH_PLANKS)
            || stack.isOf(Items.JUNGLE_PLANKS)
            || stack.isOf(Items.ACACIA_PLANKS)
            || stack.isOf(Items.DARK_OAK_PLANKS)
            || stack.isOf(Items.MANGROVE_PLANKS)
            || stack.isOf(Items.CHERRY_PLANKS)
            || stack.isOf(Items.BAMBOO_PLANKS)
            || stack.isOf(Items.STICK)
            || stack.isOf(DannysAot.IRON_BAMBOO_ITEM);
   }

   @Override
   public boolean canUse(PlayerEntity player) {
      return this.container.canPlayerUse(player);
   }

   @Override
   public ItemStack quickMove(PlayerEntity player, int slot) {
      ItemStack result = ItemStack.EMPTY;
      Slot slotx = this.slots.get(slot);
      if (slotx != null && slotx.hasStack()) {
         ItemStack slotStack = slotx.getStack();
         result = slotStack.copy();
         if (slot == 3) {
            if (!this.insertItem(slotStack, 4, 40, true)) {
               return ItemStack.EMPTY;
            }

            slotx.onQuickTransfer(slotStack, result);
         } else if (slot < 4) {
            if (!this.insertItem(slotStack, 4, 40, false)) {
               return ItemStack.EMPTY;
            }
         } else if (!slotStack.isOf(DannysAot.IRON_BAMBOO_ITEM)
            && !slotStack.isOf(DannysAot.HARDENED_IRON_BAMBOO)
            && !slotStack.isOf(DannysAot.ICE_BURST_CLUSTER)) {
            if (!slotStack.isOf(Items.RAW_IRON) && !slotStack.isOf(Items.IRON_INGOT) && !(slotStack.getItem() instanceof GasCanisterItem)) {
               if (isFuel(slotStack)) {
                  if (!this.insertItem(slotStack, 2, 3, false)) {
                     return ItemStack.EMPTY;
                  }
               } else if (slot < 31) {
                  if (!this.insertItem(slotStack, 31, 40, false)) {
                     return ItemStack.EMPTY;
                  }
               } else if (!this.insertItem(slotStack, 4, 31, false)) {
                  return ItemStack.EMPTY;
               }
            } else if (!this.insertItem(slotStack, 1, 2, false)) {
               return ItemStack.EMPTY;
            }
         } else if (!this.insertItem(slotStack, 0, 1, false)) {
            return ItemStack.EMPTY;
         }

         if (slotStack.isEmpty()) {
            slotx.setStackNoCallbacks(ItemStack.EMPTY);
         } else {
            slotx.markDirty();
         }

         if (slotStack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
         }

         slotx.onTakeItem(player, slotStack);
      }

      return result;
   }

   public int getLitProgress() {
      int litDuration = this.data.get(1);
      if (litDuration == 0) {
         litDuration = 200;
      }

      return this.data.get(0) * 13 / litDuration;
   }

   public int getSmeltProgress() {
      int progress = this.data.get(2);
      int total = this.data.get(3);
      if (total == 0) {
         total = 200;
      }

      return progress * 24 / total;
   }

   public boolean isLit() {
      return this.data.get(0) > 0;
   }
}
