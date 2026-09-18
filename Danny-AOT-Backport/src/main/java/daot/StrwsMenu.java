package daot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class StrwsMenu extends ScreenHandler {
   public static final int BUTTON_START = 0;
   public static final int BUTTON_STOP = 1;
   private final Inventory container;
   private final PropertyDelegate data;
   private final StrwsBlockEntity blockEntity;

   public StrwsMenu(int containerId, PlayerInventory playerInventory) {
      this(containerId, playerInventory, new SimpleInventory(1), null, new ArrayPropertyDelegate(2));
   }

   public StrwsMenu(int containerId, PlayerInventory playerInventory, Inventory container, StrwsBlockEntity blockEntity, PropertyDelegate data) {
      super(DannysAot.STRWS_MENU, containerId);
      this.container = container;
      this.data = data;
      this.blockEntity = blockEntity;
      checkSize(container, 1);
      checkDataCount(data, 2);
      this.addSlot(new Slot(container, 0, 80, 35) {
         @Override
         public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof GasCanisterItem;
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

   @Override
   public boolean onButtonClick(PlayerEntity player, int id) {
      if (this.blockEntity == null || this.blockEntity.getWorld() == null) {
         return false;
      } else if (id == 0) {
         this.blockEntity.startRunning(this.blockEntity.getWorld(), this.blockEntity.getPos(), this.blockEntity.getCachedState());
         return true;
      } else if (id == 1) {
         this.blockEntity.stopRunning(this.blockEntity.getWorld(), this.blockEntity.getPos(), this.blockEntity.getCachedState());
         return true;
      } else {
         return false;
      }
   }

   public boolean isRunning() {
      return this.data.get(0) > 0;
   }

   public int getGas() {
      return this.data.get(1);
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
         if (slot == 0) {
            if (!this.insertItem(slotStack, 1, 37, true)) {
               return ItemStack.EMPTY;
            }
         } else {
            if (!(slotStack.getItem() instanceof GasCanisterItem)) {
               return ItemStack.EMPTY;
            }

            if (!this.insertItem(slotStack, 0, 1, false)) {
               return ItemStack.EMPTY;
            }
         }

         if (slotStack.isEmpty()) {
            slotx.setStackNoCallbacks(ItemStack.EMPTY);
         } else {
            slotx.markDirty();
         }
      }

      return result;
   }
}
