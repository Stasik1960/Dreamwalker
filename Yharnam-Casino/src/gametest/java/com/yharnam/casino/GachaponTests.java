package com.yharnam.casino;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Server checks for the physical capsule dispenser. The tests use the block entity API
 * so they do not depend on a particular camera ray or model hitbox. */
public final class GachaponTests implements FabricGameTest {
    private static final Vec3d INTAKE = new Vec3d(-.20, .78, .25);
    private static final Vec3d CRANK = new Vec3d(.17, .78, .25);
    private static final Vec3d TRAY = new Vec3d(0, .30, .25);

    private static GachaponEntity place(TestContext context, BlockPos pos, ServerPlayerEntity owner) {
        ServerWorld world = context.getWorld();
        var block = YharnamCasino.GACHAPON;
        var state = block.getDefaultState();
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        block.onPlaced(world, pos, state, owner, new ItemStack(block));
        return (GachaponEntity) world.getBlockEntity(pos);
    }

    private static ItemStack capsule(GachaRarity rarity, String marker) {
        ItemStack shell = new ItemStack(YharnamCasino.capsule(rarity));
        ItemStack loot = new ItemStack(Items.PAPER);
        loot.setCustomName(net.minecraft.text.Text.literal(marker));
        CapsuleItem.seal(shell, loot);
        return shell;
    }

    private static void loadAll(GachaponEntity machine, ServerPlayerEntity owner, String marker) {
        for (GachaRarity rarity : GachaRarity.values()) {
            ItemStack shell = capsule(rarity, marker + ":" + rarity.id());
            owner.setStackInHand(Hand.MAIN_HAND,shell);
            machine.interact(owner,new Vec3d(0,1.5,.3));
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rarityWeightsAreExactAndExclusive(TestContext context) {
        int[] counts = new int[4];
        for (int roll = 0; roll < 100; roll++) counts[GachaRarity.roll(roll).ordinal()]++;
        context.assertTrue(counts[GachaRarity.BLUE.ordinal()] == 74, "blue fallback is 74 percent");
        context.assertTrue(counts[GachaRarity.PURPLE.ordinal()] == 20, "purple is 20 percent");
        context.assertTrue(counts[GachaRarity.ORANGE.ordinal()] == 5, "orange is 5 percent");
        context.assertTrue(counts[GachaRarity.RED.ordinal()] == 1, "red is 1 percent");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void capsuleLootSurvivesPackingAndOpening(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ItemStack loot = new ItemStack(Items.DIAMOND, 3);
        loot.setCustomName(net.minecraft.text.Text.literal("named prize"));
        ItemStack original=loot.copy();
        var item=YharnamCasino.capsule(GachaRarity.BLUE);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item));
        player.setStackInHand(Hand.OFF_HAND,loot);
        player.setSneaking(true);
        player.interactionManager.interactItem(player,context.getWorld(),player.getMainHandStack(),Hand.MAIN_HAND);
        context.assertTrue(CapsuleItem.sealed(player.getMainHandStack()) && player.getOffHandStack().isEmpty(),"Seal one prize");
        player.setSneaking(false);
        player.interactionManager.interactItem(player,context.getWorld(),player.getMainHandStack(),Hand.MAIN_HAND);
        context.assertTrue(ItemStack.areEqual(original,player.getMainHandStack()),"Return entire named prize unchanged");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void oneChipAndOneCrankProduceOneCapsule(TestContext context) {
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity guest = context.createMockCreativeServerPlayerInWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(1, 1, 1));
        GachaponEntity machine = place(context, pos, owner);
        loadAll(machine, owner, "sale");
        guest.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        guest.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10),2));
        machine.interact(guest,INTAKE);
        context.assertTrue(machine.phase()==GachaponEntity.Phase.PAID && guest.getMainHandStack().getCount()==1,"Guest pays only one chip");
        machine.interact(owner,CRANK);
        context.assertTrue(machine.phase()==GachaponEntity.Phase.PAID,"Only the paying guest can turn");
        machine.interact(guest,CRANK);
        var won=machine.result();
        context.assertTrue(machine.phase()==GachaponEntity.Phase.TURNING && guest.getMainHandStack().getCount()==1,"Crank charges once");
        context.assertTrue(machine.stockCount(won)==1,"Winning template remains after drawing");
        machine.readNbt(machine.createNbt());
        context.runAtTick(40,()->{
            context.assertTrue(machine.phase()==GachaponEntity.Phase.TRAY,"Result rests in tray");
            machine.interact(owner,TRAY);
            context.assertTrue(machine.phase()==GachaponEntity.Phase.TRAY,"Cannot steal the reserved prize");
            machine.interact(guest,TRAY);
            long copies=guest.getInventory().main.stream().filter(st->st.isOf(YharnamCasino.capsule(won)) &&
                CapsuleItem.contents(st).getName().getString().equals("sale:"+won.id())).mapToLong(ItemStack::getCount).sum();
            context.assertTrue(copies==1 && machine.phase()==GachaponEntity.Phase.IDLE,"One copy of the correct template after reload");
            machine.onRemoved(context.getWorld());
            context.assertTrue(owner.getInventory().main.stream().filter(st->st.getItem() instanceof CapsuleItem)
                .mapToInt(ItemStack::getCount).sum()==4,"All original samples returned to owner");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void paidGameCanBeCancelledWithoutLoss(TestContext context) {
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(1, 1, 1));
        GachaponEntity machine = place(context, pos, owner);
        loadAll(machine, owner, "refund");
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.chip(50), 1));
        machine.interact(owner, INTAKE);
        owner.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        owner.setSneaking(true);
        machine.interact(owner, CRANK);
        context.assertTrue(machine.phase() == GachaponEntity.Phase.IDLE &&
                owner.getInventory().contains(new ItemStack(YharnamCasino.chip(50))), "cancel returns payment");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void publicTrackingStateContainsNoLootOrPaymentNbt(TestContext context) {
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        GachaponEntity machine = place(context, context.getAbsolutePos(new BlockPos(1, 1, 1)), owner);
        loadAll(machine, owner, "private");
        NbtCompound publicState = machine.toInitialChunkDataNbt();
        context.assertTrue(!publicState.contains("Stock_blue") && !publicState.contains("Drawer") &&
                !publicState.contains("Payment"), "tracking packets do not expose private item NBT");
        NbtCompound full = machine.createNbt();
        context.assertTrue(full.contains("Stock_blue") && full.getList("Stock_blue", 10).size() == 1,
                "full saved state retains stock");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=70)
    public void noSamplesStillDeliversSealedCorrectColour(TestContext t) {
        var owner=t.createMockCreativeServerPlayerInWorld();
        var p=t.createMockCreativeServerPlayerInWorld();
        var base=t.getAbsolutePos(new BlockPos(1,2,1));
        var g=place(t,base,owner);
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(5)));
        g.interact(p,INTAKE);
        g.interact(p,CRANK);
        var color=g.result();
        t.assertTrue(color!=null,"No samples required to draw");
        t.getWorld().breakBlock(base,false);
        var delivered=p.getInventory().main.stream().filter(s->s.isOf(YharnamCasino.capsule(color))).findFirst().orElse(ItemStack.EMPTY);
        t.assertTrue(CapsuleItem.sealed(delivered) && CapsuleItem.contents(delivered).isEmpty(),"Removal pays committed empty capsule");
        t.assertTrue(owner.getInventory().contains(new ItemStack(YharnamCasino.chip(5))),"Owner gets exactly the paid chip");
        t.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void abandonedPaymentIsRefundedOnReload(TestContext t) {
        var p=t.createMockCreativeServerPlayerInWorld();
        var g=place(t,t.getAbsolutePos(new BlockPos(2,2,1)),p);
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(500),2));
        g.interact(p,INTAKE);
        var saved=g.createNbt();
        saved.putLong("Since",t.getWorld().getTime()-GachaponEntity.HOLD_TICKS-1);
        g.readNbt(saved);g.advanceClock(t.getWorld());g.advanceClock(t.getWorld());
        t.assertTrue(g.phase()==GachaponEntity.Phase.IDLE && p.getMainHandStack().getCount()==2,"Refund same chip once");
        t.complete();
    }
}
