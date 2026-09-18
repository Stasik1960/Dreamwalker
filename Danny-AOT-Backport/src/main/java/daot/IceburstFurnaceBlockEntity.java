package daot;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class IceburstFurnaceBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
   public static final int SLOT_INPUT_PRIMARY = 0;
   public static final int SLOT_INPUT_SECONDARY = 1;
   public static final int SLOT_FUEL = 2;
   public static final int SLOT_OUTPUT = 3;
   public static final int INVENTORY_SIZE = 4;
   public static final int SMELT_TIME_TOTAL = 200;
   public static final int CANISTER_SMELT_TIME = 80;
   private DefaultedList<ItemStack> items = DefaultedList.ofSize(4, ItemStack.EMPTY);
   private int litTime = 0;
   private int litDuration = 0;
   private int smeltProgress = 0;
   protected final PropertyDelegate dataAccess = new PropertyDelegate() {
      @Override
      public int get(int index) {
         return switch (index) {
            case 0 -> IceburstFurnaceBlockEntity.this.litTime;
            case 1 -> IceburstFurnaceBlockEntity.this.litDuration;
            case 2 -> IceburstFurnaceBlockEntity.this.smeltProgress;
            case 3 -> IceburstFurnaceBlockEntity.this.isCanisterRecipe() ? 80 : 200;
            default -> 0;
         };
      }

      @Override
      public void set(int index, int value) {
         switch (index) {
            case 0:
               IceburstFurnaceBlockEntity.this.litTime = value;
               break;
            case 1:
               IceburstFurnaceBlockEntity.this.litDuration = value;
               break;
            case 2:
               IceburstFurnaceBlockEntity.this.smeltProgress = value;
         }
      }

      @Override
      public int size() {
         return 4;
      }
   };

   public IceburstFurnaceBlockEntity(BlockPos pos, BlockState state) {
      super(DannysAot.ICEBURST_FURNACE_BLOCK_ENTITY, pos, state);
   }

   @Override
   public Text getDisplayName() {
      return Text.translatable("block.dannys-aot.iceburst_furnace");
   }

   @Nullable
   @Override
   public ScreenHandler createMenu(int containerId, PlayerInventory playerInventory, PlayerEntity player) {
      return new IceburstFurnaceMenu(containerId, playerInventory, this, this.dataAccess);
   }

   public static void serverTick(World level, BlockPos pos, BlockState state, IceburstFurnaceBlockEntity blockEntity) {
      boolean wasLit = blockEntity.isLit();
      boolean changed = false;
      boolean isCanisterRecipe = blockEntity.isCanisterRecipe();
      if (blockEntity.isLit() && !isCanisterRecipe) {
         blockEntity.litTime--;
      }

      boolean canSmelt = blockEntity.canSmelt();
      if (canSmelt) {
         if (isCanisterRecipe) {
            blockEntity.litTime = 1;
            blockEntity.litDuration = 1;
            blockEntity.smeltProgress++;
            if (blockEntity.smeltProgress >= 80) {
               blockEntity.smeltProgress = 0;
               blockEntity.smeltItem();
               level.playSound(null, pos, ModSounds.STEAM_POOF, SoundCategory.BLOCKS, 1.0F, 2.0F);
               changed = true;
            }
         } else {
            if (!blockEntity.isLit()) {
               ItemStack fuelStack = blockEntity.items.get(2);
               if (!fuelStack.isEmpty()) {
                  int burnTime = getBurnTime(fuelStack);
                  if (burnTime > 0) {
                     blockEntity.litTime = burnTime;
                     blockEntity.litDuration = burnTime;
                     fuelStack.decrement(1);
                     changed = true;
                  }
               }
            }

            if (blockEntity.isLit()) {
               blockEntity.smeltProgress++;
               if (blockEntity.smeltProgress >= 200) {
                  blockEntity.smeltProgress = 0;
                  blockEntity.smeltItem();
                  changed = true;
               }
            }
         }
      } else {
         blockEntity.smeltProgress = 0;
      }

      if (wasLit != blockEntity.isLit()) {
         changed = true;
         level.setBlockState(pos, state.with(IceburstFurnaceBlock.LIT, blockEntity.isLit()), 3);
      }

      if (changed) {
         blockEntity.markDirty();
      }
   }

   private boolean isCanisterRecipe() {
      ItemStack primary = this.items.get(0);
      ItemStack secondary = this.items.get(1);
      if (primary.isEmpty() || secondary.isEmpty()) {
         return false;
      } else if (!primary.isOf(DannysAot.ICE_BURST_CLUSTER)) {
         return false;
      } else {
         return !(secondary.getItem() instanceof GasCanisterItem) ? false : GasCanisterItem.getGas(secondary) < 500;
      }
   }

   private boolean isLit() {
      return this.litTime > 0;
   }

   private boolean canSmelt() {
      ItemStack primary = this.items.get(0);
      ItemStack secondary = this.items.get(1);
      ItemStack output = this.items.get(3);
      if (primary.isEmpty() || secondary.isEmpty()) {
         return false;
      } else if (this.isCanisterRecipe()) {
         return true;
      } else {
         if (primary.isOf(DannysAot.IRON_BAMBOO_ITEM) && secondary.isOf(Items.RAW_IRON)) {
            if (primary.getCount() < 2 || secondary.getCount() < 2) {
               return false;
            }

            if (output.isEmpty()) {
               return true;
            }

            if (output.isOf(DannysAot.HARDENED_IRON_BAMBOO) && output.getCount() < output.getMaxCount()) {
               return true;
            }
         }

         if (primary.isOf(DannysAot.HARDENED_IRON_BAMBOO) && secondary.isOf(Items.IRON_INGOT)) {
            if (output.isEmpty()) {
               return true;
            }

            if (output.isOf(DannysAot.ULTRAHARD_STEEL_INGOT) && output.getCount() < output.getMaxCount()) {
               return true;
            }
         }

         return false;
      }
   }

   private void smeltItem() {
      ItemStack primary = this.items.get(0);
      ItemStack secondary = this.items.get(1);
      ItemStack output = this.items.get(3);
      if (primary.isOf(DannysAot.ICE_BURST_CLUSTER) && secondary.getItem() instanceof GasCanisterItem) {
         int gas = GasCanisterItem.getGas(secondary);
         GasCanisterItem.setGas(secondary, Math.min(gas + 25, 500));
         primary.decrement(1);
      } else if (primary.isOf(DannysAot.IRON_BAMBOO_ITEM) && secondary.isOf(Items.RAW_IRON)) {
         ItemStack result = new ItemStack(DannysAot.HARDENED_IRON_BAMBOO);
         if (output.isEmpty()) {
            this.items.set(3, result.copy());
         } else {
            output.increment(1);
         }

         primary.decrement(2);
         secondary.decrement(2);
      } else {
         if (primary.isOf(DannysAot.HARDENED_IRON_BAMBOO) && secondary.isOf(Items.IRON_INGOT)) {
            ItemStack result = new ItemStack(DannysAot.ULTRAHARD_STEEL_INGOT);
            if (output.isEmpty()) {
               this.items.set(3, result.copy());
            } else {
               output.increment(1);
            }

            primary.decrement(1);
            secondary.decrement(1);
         }
      }
   }

   private static int getBurnTime(ItemStack fuel) {
      if (fuel.isEmpty()) {
         return 0;
      } else if (fuel.isOf(Items.COAL) || fuel.isOf(Items.CHARCOAL)) {
         return 1600;
      } else if (fuel.isOf(Items.COAL_BLOCK)) {
         return 16000;
      } else if (fuel.isOf(Items.BLAZE_ROD)) {
         return 2400;
      } else if (fuel.isOf(Items.LAVA_BUCKET)) {
         return 20000;
      } else if (fuel.isOf(Items.OAK_LOG)
         || fuel.isOf(Items.SPRUCE_LOG)
         || fuel.isOf(Items.BIRCH_LOG)
         || fuel.isOf(Items.JUNGLE_LOG)
         || fuel.isOf(Items.ACACIA_LOG)
         || fuel.isOf(Items.DARK_OAK_LOG)
         || fuel.isOf(Items.MANGROVE_LOG)
         || fuel.isOf(Items.CHERRY_LOG)) {
         return 300;
      } else if (fuel.isOf(Items.OAK_PLANKS)
         || fuel.isOf(Items.SPRUCE_PLANKS)
         || fuel.isOf(Items.BIRCH_PLANKS)
         || fuel.isOf(Items.JUNGLE_PLANKS)
         || fuel.isOf(Items.ACACIA_PLANKS)
         || fuel.isOf(Items.DARK_OAK_PLANKS)
         || fuel.isOf(Items.MANGROVE_PLANKS)
         || fuel.isOf(Items.CHERRY_PLANKS)
         || fuel.isOf(Items.BAMBOO_PLANKS)) {
         return 300;
      } else if (fuel.isOf(Items.STICK)) {
         return 100;
      } else {
         return fuel.isOf(DannysAot.IRON_BAMBOO_ITEM) ? 50 : 0;
      }
   }

   @Override
   public int size() {
      return 4;
   }

   @Override
   public boolean isEmpty() {
      for (ItemStack stack : this.items) {
         if (!stack.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   @Override
   public ItemStack getStack(int slot) {
      return this.items.get(slot);
   }

   @Override
   public ItemStack removeStack(int slot, int amount) {
      return Inventories.splitStack(this.items, slot, amount);
   }

   @Override
   public ItemStack removeStack(int slot) {
      return Inventories.removeStack(this.items, slot);
   }

   @Override
   public void setStack(int slot, ItemStack stack) {
      this.items.set(slot, stack);
      if (stack.getCount() > this.getMaxCountPerStack()) {
         stack.setCount(this.getMaxCountPerStack());
      }

      this.markDirty();
   }

   @Override
   public boolean canPlayerUse(PlayerEntity player) {
      return Inventory.canPlayerUse(this, player);
   }

   @Override
   public void clear() {
      this.items.clear();
   }

   @Override
   protected void writeNbt(NbtCompound nbt) {
      super.writeNbt(nbt);
      Inventories.writeNbt(nbt, this.items);
      nbt.putInt("LitTime", this.litTime);
      nbt.putInt("LitDuration", this.litDuration);
      nbt.putInt("SmeltProgress", this.smeltProgress);
   }

   @Override
   public void readNbt(NbtCompound nbt) {
      super.readNbt(nbt);
      this.items = DefaultedList.ofSize(4, ItemStack.EMPTY);
      Inventories.readNbt(nbt, this.items);
      this.litTime = nbt.getInt("LitTime");
      this.litDuration = nbt.getInt("LitDuration");
      this.smeltProgress = nbt.getInt("SmeltProgress");
   }
}
