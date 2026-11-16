package com.yharnam.casino;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class CasinoGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void paytableHasExpectedReturnAndOneTicketOutcome(TestContext context) {
        int returnUnits = 0;
        int withTicket = 0;
        int netWins = 0;
        for (int left = 0; left < 8; left++) {
            for (int middle = 0; middle < 8; middle++) {
                for (int right = 0; right < 8; right++) {
                    int payout = SlotMath.multiplier(left, middle, right);
                    returnUnits += payout;
                    netWins += payout > 1 ? 1 : 0;
                    withTicket += SlotMath.winsTicket(left, middle, right) ? 1 : 0;
                }
            }
        }
        context.assertTrue(returnUnits == 488 && withTicket == 1 && netWins == 71,
                "Expected 488/512 RTP, one ticket outcome and 71 net wins");
        for (int value : YharnamCasino.CHIP_VALUES) {
            context.assertTrue(YharnamCasino.chip(value).getMaxCount() == 64, "All chips must stack up to 64");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void oneChipStartsOneSpinAndSavedJackpotPaysExactlyOnce(TestContext context) {
        BlockPos base = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ServerWorld world = context.getWorld();
        SlotMachineEntity machine = place(context, base, SlotStyle.ASTRAL_CHOIR);
        allowAllChips(machine);
        fillSafe(machine,new ItemStack(YharnamCasino.chip(1000),4));
        ServerPlayerEntity bettor = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity bystander = context.createMockCreativeServerPlayerInWorld();
        bettor.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.chip(50), 64));
        bystander.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.chip(100), 7));
        click(world, base, bettor, false);
        context.assertTrue(bettor.getMainHandStack().getCount() == 63 && machine.spinStarted() < 0,
                "Inserting a stake consumes exactly one chip and waits for the lever");
        click(world, base, bystander, true);
        context.assertTrue(bystander.getMainHandStack().getCount() == 7 && machine.spinStarted() < 0,
                "Other players cannot start or change an armed bet");
        click(world, base.up(), bettor, true);
        context.assertTrue(machine.spinStarted() >= 0 && bettor.getMainHandStack().getCount() == 63,
                "The upper half routes the lever click without charging another chip");
        click(world, base, bettor, true);
        context.assertTrue(bettor.getMainHandStack().getCount() == 63, "A second click cannot charge a running spin");

        NbtCompound saved = machine.createNbt();
        saved.putIntArray("Target", new int[]{0, 0, 0});
        machine.readNbt(saved);
        context.assertTrue(machine.reelAngle(0, 45) == machine.reelAngle(0, 50)
                        && machine.reelAngle(1, 45) != machine.reelAngle(1, 50)
                        && machine.reelAngle(2, 55) != machine.reelAngle(2, 63),
                "The reels must stop independently, left to right");
        context.assertTrue(!machine.toInitialChunkDataNbt().contains("Bettor"), "Client sync must omit the bettor UUID");

        context.runAtTick(76, () -> {
            context.assertTrue(machine.spinStarted() < 0 && machine.resultIndex(0) == 0 && machine.resultIndex(2) == 0,
                    "Server settles the saved result and leaves the matching symbols visible");
            CasinoMailbox mailbox = CasinoMailbox.get(world.getServer());
            mailbox.deliver(bettor, false);
            context.assertTrue(chipValue(bettor) == 7150, "Expected 63×50 plus jackpot payout 80×50");
            context.assertTrue(bettor.getInventory().count(YharnamCasino.PRIZE_TICKET) == 1, "Expected one ticket");
            context.assertTrue(!mailbox.deliver(bettor, false), "A second claim cannot duplicate a payout");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void upperBreakDropsOneCabinetAndRefundSurvivesFullInventory(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(1, 1, 1));
        SlotMachineEntity machine = place(context, base, SlotStyle.CRIMSON_OATH);
        allowAllChips(machine);
        ServerPlayerEntity bettor = context.createMockCreativeServerPlayerInWorld();
        bettor.setStackInHand(Hand.OFF_HAND, new ItemStack(YharnamCasino.chip(500), 2));
        click(world, base.up(), bettor, false);
        context.assertTrue(bettor.getOffHandStack().getCount() == 1 && machine.spinStarted() < 0,
                "The top half accepts exactly one chip from the offhand");
        bettor.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        for (int slot = 0; slot < bettor.getInventory().main.size(); slot++) {
            bettor.getInventory().setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        SlotMachineBlock block = YharnamCasino.cabinet(SlotStyle.CRIMSON_OATH);
        block.onBreak(world, base.up(), world.getBlockState(base.up()), context.createMockSurvivalPlayer());
        context.assertTrue(world.getBlockState(base).isAir() && world.getBlockState(base.up()).isAir(),
                "Breaking the top should remove both halves");
        int cabinetDrops = world.getEntitiesByClass(ItemEntity.class, new Box(base).expand(2),
                drop -> drop.getStack().isOf(block.asItem())).stream().mapToInt(drop -> drop.getStack().getCount()).sum();
        context.assertTrue(cabinetDrops == 1, "The two-block cabinet must drop exactly one item");

        CasinoMailbox mailbox = CasinoMailbox.get(world.getServer());
        context.assertTrue(!mailbox.deliver(bettor, false), "A full inventory leaves the refund saved");
        bettor.getInventory().setStack(0, ItemStack.EMPTY);
        context.assertTrue(mailbox.deliver(bettor, true) && chipValue(bettor) == 500,
                "One freed slot is enough to claim a saved refund");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 105)
    public void sharedKeyOpensCashboxAndOwnersCanTakeLosingStakes(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(1, 1, 1));
        SlotMachineBlock block = YharnamCasino.cabinet(SlotStyle.HUNTERS_WAGER);
        ServerPlayerEntity founder = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity first = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity second = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity guest = context.createMockCreativeServerPlayerInWorld();
        BlockState state = block.getDefaultState();
        world.setBlockState(base,state,Block.NOTIFY_ALL);
        block.onPlaced(world,base,state,founder,new ItemStack(block));
        SlotMachineEntity machine = (SlotMachineEntity)world.getBlockEntity(base);
        allowAllChips(machine);
        founder.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+2.5,180,0);
        first.refreshPositionAndAngles(base.getX()+1,base.getY(),base.getZ()+2.5,180,0);
        second.refreshPositionAndAngles(base.getX()+2,base.getY(),base.getZ()+2.5,180,0);
        guest.refreshPositionAndAngles(base.getX()-1,base.getY(),base.getZ()+2.5,180,0);
        ItemStack ownerKey = new ItemStack(YharnamCasino.CABINET_KEY);
        useKey(founder,base.up(),ownerKey);
        context.assertTrue(founder.currentScreenHandler instanceof GenericContainerScreenHandler,
                "The founder can open the safe through the upper half");
        founder.closeHandledScreen();
        ownerKey.useOnEntity(founder,first,Hand.MAIN_HAND);
        ownerKey.useOnEntity(founder,second,Hand.MAIN_HAND);
        context.assertTrue(machine.hasAccess(first.getUuid()) && machine.hasAccess(second.getUuid()),
                "Two co-owners can be assigned with the same key");

        useKey(guest,base,ownerKey.copy());
        context.assertTrue(guest.currentScreenHandler == guest.playerScreenHandler,
                "Holding a copied key does not give a guest access");
        context.assertTrue(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                        world,guest,base.up(),world.getBlockState(base.up()),null),
                "An unauthorized user cannot bypass the safe by mining its upper half");

        ItemStack firstKey = new ItemStack(YharnamCasino.CABINET_KEY);
        useKey(first,base,firstKey);
        GenericContainerScreenHandler opened = (GenericContainerScreenHandler)first.currentScreenHandler;
        ItemStack kept = new ItemStack(YharnamCasino.chip(100),7);
        kept.setCustomName(Text.literal("Saved chips"));
        opened.getInventory().setStack(8,kept);
        firstKey.useOnEntity(first,guest,Hand.MAIN_HAND);
        context.assertTrue(!machine.hasAccess(guest.getUuid()),"Co-owners cannot delegate membership");
        founder.setSneaking(true);
        ownerKey.useOnEntity(founder,first,Hand.MAIN_HAND);
        founder.setSneaking(false);
        context.assertTrue(first.currentScreenHandler == first.playerScreenHandler && !opened.canUse(first)
                        && machine.hasAccess(second.getUuid()),
                "Revocation closes the member's open safe and keeps other owners");

        guest.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(50)));
        click(world,base,guest,false);
        click(world,base.up(),guest,true);
        NbtCompound saved = machine.createNbt();
        saved.putIntArray("Target",new int[]{0,1,2});
        machine.readNbt(saved);
        context.assertTrue(machine.hasAccess(second.getUuid()) && !machine.hasAccess(first.getUuid())
                        && !machine.toInitialChunkDataNbt().contains("Safe"),
                "NBT reload retains permissions and client sync omits the vault");

        context.runAtTick(79, () -> {
            ItemStack key = new ItemStack(YharnamCasino.CABINET_KEY);
            useKey(second,base,key);
            GenericContainerScreenHandler shared = (GenericContainerScreenHandler)second.currentScreenHandler;
            int stakeSlot = -1;
            for (int slot=0; slot<shared.getInventory().size(); slot++) {
                if (shared.getInventory().getStack(slot).isOf(YharnamCasino.chip(50))) {
                    stakeSlot = slot;
                    break;
                }
            }
            context.assertTrue(stakeSlot >= 0,"The losing stake is a real chip in the shared safe");
            shared.quickMove(second,stakeSlot);
            context.assertTrue(chipValue(second)==50 && shared.getInventory().getStack(stakeSlot).isEmpty(),
                    "A co-owner can withdraw the real chip through the safe");
            context.assertTrue(ItemStack.areEqual(kept,shared.getInventory().getStack(8)),
                    "Taking the actual stake does not alter the other stacks");
            second.closeHandledScreen();
            world.breakBlock(base,true,founder);
            context.assertTrue(founder.getInventory().main.stream().anyMatch(stack -> ItemStack.areEqual(kept,stack)),
                    "On dismantling, the exact safe stack goes back to the founder");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void exactChangeConservesValueEvenInAFullCashbox(TestContext context) {
        SimpleInventory inventory = new SimpleInventory(2);
        inventory.setStack(0,new ItemStack(YharnamCasino.chip(1000),64));
        inventory.setStack(1,new ItemStack(Items.COBBLESTONE,64));
        ChipPool pool = new ChipPool(inventory);
        context.assertTrue(pool.pay(1)==1 && pool.balance()==63999,
                "Breaking a chip in a full cashbox keeps the exact value of the change");
        pool.fillSlotsWithChange();
        context.assertTrue(pool.savedChange()==999 && inventory.getStack(1).isOf(Items.COBBLESTONE),
                "Unrelated items cannot be replaced by chip change");
        inventory.removeStack(1);
        pool.fillSlotsWithChange();
        context.assertTrue(inventory.getStack(1).isOf(YharnamCasino.chip(500))
                        && pool.savedChange()==499 && pool.balance()==63999,
                "Freed space makes the outstanding change physically withdrawable without increasing the balance");
        context.assertTrue(pool.pay(70000)==63999 && pool.balance()==0,
                "A request larger than the reserve receives only available chips");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 105)
    public void underfundedWinCreatesRoleplayReceiptWithoutPayingFromIt(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(1,1,1));
        SlotMachineEntity machine = place(context,base,SlotStyle.ASTRAL_CHOIR);
        ServerPlayerEntity bettor = context.createMockCreativeServerPlayerInWorld();
        bettor.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10)));
        click(world,base,bettor,false);
        click(world,base.up(),bettor,true);
        NbtCompound saved = machine.createNbt();
        saved.putIntArray("Target",new int[]{1,1,1});
        machine.readNbt(saved);

        context.runAtTick(78,() -> {
            ItemStack note = bettor.getInventory().main.stream()
                    .filter(item -> item.isOf(YharnamCasino.CHIP_IOU)).findFirst().orElse(ItemStack.EMPTY);
            context.assertTrue(chipValue(bettor)==10 && receiptDue(note)==490,
                    "A 500-unit win pays 10 from the pool and records the 490-unit shortfall");
            NbtCompound receipt = note.getSubNbt("YharnamCasinoIou");
            context.assertTrue(receipt != null && receipt.contains("IssuerName") && receipt.contains("IssuerPosition"),
                    "The receipt identifies the cabinet for roleplay");
            fillSafe(machine,new ItemStack(YharnamCasino.chip(500)));
            ItemStack legacy = note.copy();
            NbtCompound legacyTag = legacy.getOrCreateSubNbt("YharnamCasinoIou");
            legacyTag.putUuid("Claim",UUID.randomUUID());
            legacyTag.putUuid("Machine",machine.machineId());
            useCabinetItem(bettor,base,note);
            useCabinetItem(bettor,base.up(),legacy);
            context.assertTrue(chipValue(bettor)==10 && receiptDue(note)==490 && receiptDue(legacy)==490
                    && !note.isEmpty() && !legacy.isEmpty(), "New and legacy receipts are reference documents");
            context.assertTrue(machine.payFromPool(1000)==500 && machine.spinStarted()<0,
                    "Showing a receipt leaves the funded cashbox and machine untouched");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void staffCanOpenAnyCashboxWithoutBecomingOwners(TestContext context) {
        ServerWorld world = context.getWorld();
        SlotMachineBlock block = YharnamCasino.cabinet(SlotStyle.HUNTERS_WAGER);
        BlockState state = block.getDefaultState();
        BlockPos ownedPos = context.getAbsolutePos(new BlockPos(1,1,1));
        BlockPos unownedPos = ownedPos.east(2);
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity staff = context.createMockCreativeServerPlayerInWorld();
        world.setBlockState(ownedPos,state,Block.NOTIFY_ALL);
        block.onPlaced(world,ownedPos,state,owner,new ItemStack(block));
        world.setBlockState(unownedPos,state,Block.NOTIFY_ALL);
        block.onPlaced(world,unownedPos,state,null,new ItemStack(block));
        SlotMachineEntity owned = SlotMachineBlock.findMachine(world,ownedPos);
        SlotMachineEntity unowned = SlotMachineBlock.findMachine(world,unownedPos);
        staff.refreshPositionAndAngles(ownedPos.getX()+.5,ownedPos.getY(),ownedPos.getZ()+2,180,0);
        ItemStack gmKey = new ItemStack(YharnamCasino.GM_KEY);
        useCabinetItem(staff,ownedPos,gmKey);
        context.assertTrue(staff.currentScreenHandler==staff.playerScreenHandler,
                "Mere possession of the GM key grants no permissions");
        for (String role : new String[]{"rpchat_admin","rpchat_gm","profile_admin","profile_gm"}) {
            staff.addCommandTag(role);
            useCabinetItem(staff,ownedPos.up(),gmKey);
            context.assertTrue(staff.currentScreenHandler instanceof GenericContainerScreenHandler screen
                    && screen.canUse(staff),"A staff tag grants cashbox access: "+role);
            var opened = staff.currentScreenHandler;
            staff.getCommandTags().remove(role);
            context.assertTrue(!opened.canUse(staff),"The cashbox rechecks staff rights when a role is revoked");
            staff.closeHandledScreen();
        }
        staff.addCommandTag("rpchat_gm");
        useCabinetItem(staff,unownedPos,gmKey);
        context.assertTrue(staff.currentScreenHandler instanceof GenericContainerScreenHandler
                && unowned.founderId()==null && gmKey.getNbt()==null,
                "The GM key opens an unowned machine without claiming or linking to it");
        staff.closeHandledScreen();
        ItemStack stewardKey = new ItemStack(YharnamCasino.CABINET_KEY);
        useCabinetItem(staff,ownedPos,stewardKey);
        context.assertTrue(staff.currentScreenHandler instanceof GenericContainerScreenHandler
                && owner.getUuid().equals(owned.founderId()) && stewardKey.getNbt()==null,
                "Staff opening an owned cabinet with an ordinary key leaves its link and owner alone");
        staff.closeHandledScreen();
        staff.setSneaking(true);
        click(world,ownedPos,staff,false);
        context.assertTrue(staff.currentScreenHandler instanceof GenericContainerScreenHandler
                && owner.getUuid().equals(owned.founderId()) && !owned.hasAccess(staff.getUuid()),
                "A staff player can open directly with empty hands and leaves ownership unchanged");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void keySetsAcceptedChipsAndKeepsAtLeastOne(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(1,1,1));
        SlotMachineEntity machine = place(context,base,SlotStyle.CRIMSON_OATH);
        ServerPlayerEntity guest = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity gm = context.createMockCreativeServerPlayerInWorld();
        gm.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+2,180,0);
        ItemStack fifty = new ItemStack(YharnamCasino.chip(50),4);
        guest.setStackInHand(Hand.MAIN_HAND,fifty);
        click(world,base,guest,false);
        context.assertTrue(fifty.getCount()==4 && machine.acceptedChipMask()==4,
                "A default machine rejects 50 and only accepts 10");
        gm.addCommandTag("rpchat_gm");
        gm.setSneaking(true);
        useCabinetItem(gm,base.up(),new ItemStack(YharnamCasino.GM_KEY));
        context.assertTrue(gm.currentScreenHandler instanceof DenominationMenu,"Sneak-use opens the wager settings");
        DenominationMenu menu = (DenominationMenu)gm.currentScreenHandler;
        context.assertTrue(!menu.onButtonClick(guest,3) && !menu.onButtonClick(gm,2),
                "A guest cannot change the settings or disable the last denomination");
        context.assertTrue(menu.onButtonClick(gm,3) && menu.accepts(2) && menu.accepts(3),
                "A second denomination can be enabled independently");
        context.assertTrue(menu.onButtonClick(gm,2) && !menu.accepts(2),"One denomination can also be selected");
        NbtCompound saved = machine.createNbt();
        machine.readNbt(saved);
        context.assertTrue(machine.acceptedChipMask()==8,"The selected denomination survives a save and reload");
        click(world,base,guest,false);
        context.assertTrue(fifty.getCount()==3,"The newly accepted denomination is deducted once");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chipCabinetStoresAndShowsAllSevenDenominations(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(1,1,1));
        ChipCabinetBlock block = YharnamCasino.CHIP_CABINET;
        BlockState state = block.getDefaultState();
        world.setBlockState(base,state,Block.NOTIFY_ALL);
        block.onPlaced(world,base,state,null,new ItemStack(block));
        ChipCabinetEntity cabinet = (ChipCabinetEntity)world.getBlockEntity(base);
        ServerPlayerEntity cashier = context.createMockCreativeServerPlayerInWorld();
        cashier.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+2,180,0);
        BlockPos top = base.up();
        world.getBlockState(top).onUse(world,cashier,Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(top),Direction.SOUTH,top,false));
        context.assertTrue(cashier.currentScreenHandler instanceof GenericContainerScreenHandler,
                "The upper half opens the cabinet's shared inventory");
        var menu = cashier.currentScreenHandler;
        context.assertTrue(!menu.getSlot(0).canInsert(new ItemStack(YharnamCasino.chip(100)))
                && !menu.getSlot(7).canInsert(new ItemStack(Items.COBBLESTONE)),
                "Each active column accepts one denomination and spare slots are disabled");
        for (int row=0; row<6; row++) {
            for (int column=0; column<7; column++) {
                cabinet.setStack(row*9+column,new ItemStack(YharnamCasino.chip(YharnamCasino.CHIP_VALUES[column]),64));
            }
        }
        NbtCompound saved = cabinet.createNbt();
        cabinet.clear();
        cabinet.readNbt(saved);
        int[] counts = cabinet.toInitialChunkDataNbt().getIntArray("VisibleChips");
        context.assertTrue(counts.length==7 && java.util.Arrays.stream(counts).allMatch(count -> count==384)
                && !cabinet.toInitialChunkDataNbt().contains("Items"),
                "All 2688 physical chips survive storage, and clients receive just seven counts");
        menu.quickMove(cashier,0);
        context.assertTrue(cabinet.visibleCount(0)==320 && chipValue(cashier)==64,
                "Removing a full stack updates the corresponding display count");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outOfOrderCabinetHasNoBlockEntityOrGameplay(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(1,1,1));
        Block block = net.minecraft.registry.Registries.BLOCK.get(YharnamCasino.id("astral_choir_broken"));
        BlockState state = block.getDefaultState();
        world.setBlockState(base,state,Block.NOTIFY_ALL);
        block.onPlaced(world,base,state,null,new ItemStack(block));
        ServerPlayerEntity guest = context.createMockCreativeServerPlayerInWorld();
        ItemStack chips = new ItemStack(YharnamCasino.chip(10),2);
        guest.setStackInHand(Hand.MAIN_HAND,chips);
        click(world,base.up(),guest,false);
        context.assertTrue(world.getBlockState(base.up()).isOf(block)
                && world.getBlockEntity(base)==null && world.getBlockEntity(base.up())==null
                && chips.getCount()==2 && guest.currentScreenHandler==guest.playerScreenHandler,
                "The decorative version occupies two blocks without creating a machine or taking a bet");
        context.complete();
    }

    private static void allowAllChips(SlotMachineEntity machine) {
        NbtCompound configured = machine.createNbt();
        configured.putInt("AcceptedChips",127);
        machine.readNbt(configured);
    }

    private static void useKey(ServerPlayerEntity player, BlockPos pos, ItemStack key) {
        useCabinetItem(player,pos,key);
    }

    private static void useCabinetItem(ServerPlayerEntity player, BlockPos pos, ItemStack stack) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos),Direction.SOUTH,pos,false);
        ItemUsageContext use = new ItemUsageContext(player.getWorld(),player,Hand.MAIN_HAND,stack,hit) {};
        stack.useOnBlock(use);
    }

    private static void fillSafe(SlotMachineEntity machine,ItemStack... contents) {
        DefaultedList<ItemStack> slots = DefaultedList.ofSize(27,ItemStack.EMPTY);
        for (int slot=0; slot<contents.length; slot++) {
            slots.set(slot,contents[slot]);
        }
        NbtCompound snapshot = machine.createNbt();
        snapshot.put("Safe",Inventories.writeNbt(new NbtCompound(),slots));
        machine.readNbt(snapshot);
    }

    private static long receiptDue(ItemStack note) {
        NbtCompound receipt = note.getSubNbt("YharnamCasinoIou");
        return receipt==null ? 0 : receipt.getLong("Amount");
    }

    private static SlotMachineEntity place(TestContext context, BlockPos base, SlotStyle style) {
        ServerWorld world = context.getWorld();
        SlotMachineBlock block = YharnamCasino.cabinet(style);
        PlayerEntity placer = context.createMockSurvivalPlayer();
        placer.setYaw(180);
        ItemStack stack = new ItemStack(block);
        placer.setStackInHand(Hand.MAIN_HAND, stack);
        BlockPos support = base.down();
        world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofBottomCenter(base), Direction.UP, support, false);
        ItemUsageContext use = new ItemUsageContext(placer, Hand.MAIN_HAND, hit);
        world.setBlockState(base.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(!stack.useOnBlock(use).isAccepted() && world.getBlockState(base).isAir()
                        && stack.getCount() == 1,
                "Placement with no space for the upper half must preserve the cabinet item");
        world.setBlockState(base.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(stack.useOnBlock(use).isAccepted() && stack.isEmpty(), "Cabinet item can be placed normally");
        context.assertTrue(world.getBlockState(base).get(SlotMachineBlock.FACING) == Direction.SOUTH
                        && world.getBlockState(base.up()).get(SlotMachineBlock.HALF) == DoubleBlockHalf.UPPER,
                "Placed cabinet faces the player and occupies two linked blocks");
        return (SlotMachineEntity)world.getBlockEntity(base);
    }

    private static void click(ServerWorld world, BlockPos pos, ServerPlayerEntity player, boolean lever) {
        BlockState state = world.getBlockState(pos);
        BlockPos base = state.get(SlotMachineBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
        Vec3d hitPoint = lever ? Vec3d.ofBottomCenter(base).add(.43,1.08,.06) : Vec3d.ofCenter(pos);
        state.onUse(world, player, Hand.MAIN_HAND, new BlockHitResult(hitPoint, Direction.SOUTH, pos, false));
    }

    private static long chipValue(ServerPlayerEntity player) {
        return player.getInventory().main.stream()
                .filter(stack -> stack.getItem() instanceof ChipItem)
                .mapToLong(stack -> (long)((ChipItem)stack.getItem()).value() * stack.getCount())
                .sum();
    }
}
