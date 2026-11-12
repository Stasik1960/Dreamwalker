package dev.nightshift.skate;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;

public final class SkateWorkbenchScreenHandler extends ScreenHandler {
    public static final int BOARD = 0;
    public static final int OUTPUT = 4;
    private static final int PLAYER_START = 5;
    private static final int HOTBAR_START = PLAYER_START + 27;
    private static final int PLAYER_END = HOTBAR_START + 9;
    private static final SkatePart.Type[] TYPES = SkatePart.Type.values();

    private final ScreenHandlerContext context;
    private final SimpleInventory inputs = new SimpleInventory(OUTPUT) {
        @Override
        public void markDirty() {
            super.markDirty();
            onContentChanged(this);
        }
    };
    private final Inventory result = new SimpleInventory(1);
    private boolean updating;

    public SkateWorkbenchScreenHandler(int syncId, PlayerInventory inventory) {
        this(syncId, inventory, ScreenHandlerContext.EMPTY);
    }

    SkateWorkbenchScreenHandler(int syncId, PlayerInventory inventory, ScreenHandlerContext context) {
        super(SkateParts.SCREEN, syncId);
        this.context = context;
        addSlot(new Slot(inputs, BOARD, 20, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.getItem() instanceof SkateboardItem;
            }
        });
        for (SkatePart.Type type : TYPES) {
            addSlot(new Slot(inputs, slotFor(type), 20, 60 + type.ordinal() * 25) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    SkatePart part = SkateParts.fromStack(stack);
                    return part != null && part.type() == type;
                }
            });
        }
        addSlot(new Slot(result, 0, 149, 114) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }

            @Override
            public boolean canTakeItems(PlayerEntity player) {
                return hasStack() && canUse(player);
            }

            @Override
            public void onTakeItem(PlayerEntity player, ItemStack stack) {
                context.run((world, pos) -> takeAssembly(player));
                super.onTakeItem(player, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 78 + col * 18, 159 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 78 + col * 18, 215));
        }
    }

    private static int slotFor(SkatePart.Type type) {
        return 1 + type.ordinal();
    }

    public @Nullable BoardSetup installed() {
        ItemStack board = inputs.getStack(BOARD);
        return board.getItem() instanceof SkateboardItem ? BoardSetup.fromStack(board) : null;
    }

    public BoardSetup preview(BoardSetup setup) {
        for (SkatePart.Type type : TYPES) {
            SkatePart part = SkateParts.fromStack(inputs.getStack(slotFor(type)));
            if (part != null && part.type() == type) {
                setup = setup.with(part);
            }
        }
        return setup;
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        if (!updating) {
            context.run((world, pos) -> refreshOutput());
        }
    }

    private void refreshOutput() {
        BoardSetup installed = installed();
        ItemStack output = ItemStack.EMPTY;
        if (installed != null) {
            BoardSetup preview = preview(installed);
            if (!preview.equals(installed)) {
                output = preview.applyTo(inputs.getStack(BOARD));
            }
        }
        result.setStack(0, output);
        sendContentUpdates();
    }

    private void takeAssembly(PlayerEntity player) {
        BoardSetup installed = installed();
        if (installed == null) return;
        BoardSetup replacement = preview(installed);
        if (replacement.equals(installed)) return;

        updating = true;
        try {
            inputs.removeStack(BOARD, 1);
            for (SkatePart.Type type : TYPES) {
                SkatePart oldPart = installed.part(type);
                if (replacement.part(type) != oldPart) {
                    inputs.removeStack(slotFor(type), 1);
                    player.getInventory().offerOrDrop(new ItemStack(SkateParts.item(oldPart)));
                }
            }
        } finally {
            updating = false;
            refreshOutput();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack() || !slot.canTakeItems(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index < PLAYER_START) {
            if (!insertItem(stack, PLAYER_START, PLAYER_END, index == OUTPUT)) return ItemStack.EMPTY;
        } else {
            SkatePart part = SkateParts.fromStack(stack);
            int target = stack.getItem() instanceof SkateboardItem ? BOARD : part == null ? -1 : slotFor(part.type());
            if (target >= 0) {
                if (!insertItem(stack, target, target + 1, false)) return ItemStack.EMPTY;
            } else if (index < HOTBAR_START) {
                if (!insertItem(stack, HOTBAR_START, PLAYER_END, false)) return ItemStack.EMPTY;
            } else if (!insertItem(stack, PLAYER_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, stack);
        return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(context, player, SkateParts.WORKBENCH);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        context.run((world, pos) -> dropInventory(player, inputs));
    }
}
