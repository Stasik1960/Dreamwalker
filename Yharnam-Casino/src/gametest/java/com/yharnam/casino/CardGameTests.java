package com.yharnam.casino;

import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class CardGameTests implements FabricGameTest {
    private static int[] deck(int... prefix) {
        int[] result = Arrays.copyOf(prefix, 52);
        boolean[] used = new boolean[52];
        for (int card : prefix) {
            if (used[card])
                throw new IllegalArgumentException("Duplicate fixture card");
            used[card] = true;
        }
        int next = prefix.length;
        for (int i = 0; i < 52; i++)
            if (!used[i])
                result[next++] = i;
        return result;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pokerRanksAndFold(TestContext t) {
        long wheel = PokerHand.best(12, 13, new int[] {14, 15, 16, 27, 41});
        long sixHigh = PokerHand.best(13, 14, new int[] {2, 3, 4, 31, 49});
        long straightFlush = PokerHand.best(12, 11, new int[] {10, 9, 8, 27, 43});
        t.assertTrue(
            wheel >>> 24 == 4 && sixHigh > wheel && straightFlush >>> 24 == 8, "Five-high straight and straight flush");
        PokerHand h = PokerHand.start(new long[] {100, 100}, 0, deck());
        h.act(h.turn(), 0, true);
        t.assertTrue(h.phase().equals("showdown") && h.cards(1, false)[0] < 0,
            "An uncontested pot keeps the winner's cards private");
        h.dealerAdvance();
        t.assertTrue(h.balance(0) == 99 && h.balance(1) == 101, "Heads-up fold awards just the small blind");
        t.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pokerAllInSidePotsAndReload(TestContext t) {
        // Button has AA, small blind KK, big blind QQ. Contributions are 10/30/30.
        PokerHand h =
            PokerHand.start(new long[] {10, 30, 50}, 0, deck(11, 10, 12, 24, 23, 25, 4, 0, 16, 33, 2, 43, 1, 8));
        h.act(0, 100, false);
        h.act(1, 100, false);
        h.act(2, 0, false);
        t.assertTrue(
            h.turn() == -1 && h.committed(2) == 30 && h.balance(2) == 20, "Uncalled chips remain at the third seat");
        h.dealerAdvance();
        t.assertTrue(Arrays.equals(h.board(), new int[] {0, 16, 33}) && h.cards(0, false)[0] == -1,
            "Burn and concealed hole cards");
        h = PokerHand.fromNbt(h.toNbt());
        h.dealerAdvance();
        h.dealerAdvance();
        t.assertTrue(h.board().length == 5 && Arrays.equals(h.cards(0, false), new int[] {12, 25}),
            "Five-card board and public showdown");
        h.dealerAdvance();
        t.assertTrue(
            h.balance(0) == 30 && h.balance(1) == 40 && h.balance(2) == 20, "Main pot goes to AA and side pot to KK");
        t.complete();
    }

    private static void settle(BlackjackHand hand) {
        while (hand.phase().equals("players"))
            hand.act("stand");
        while (hand.phase().equals("dealer") || hand.phase().equals("showdown"))
            hand.dealerAdvance();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blackjackNaturalsPushAndSplitDouble(TestContext t) {
        long[] bets = {20, 0, 0, 0, 0, 0, 0};
        BlackjackHand natural = BlackjackHand.start(bets, 1000, deck(12, 8, 11, 6));
        settle(natural);
        t.assertTrue(natural.payout(0) == 50 && natural.bankAfter() == 970, "Natural pays the stake plus 3:2");
        BlackjackHand push = BlackjackHand.start(bets, 1000, deck(12, 25, 8, 21));
        settle(push);
        t.assertTrue(push.payout(0) == 20 && push.bankAfter() == 1000, "Two naturals push");
        int[] pair = deck(6, 7, 19, 5, 2, 3, 20, 8);
        BlackjackHand h = BlackjackHand.start(bets, 1000, pair);
        t.assertTrue(h.dealerCards()[1] == -1 && h.act("split"), "Dealer's second card hidden, pair of eights splits");
        h = BlackjackHand.fromNbt(h.toNbt());
        t.assertTrue(
            h.extraStake("double") == 20 && h.act("double") && h.splitIndex() == 1, "Double on first split hand");
        settle(h);
        t.assertTrue(h.committed(0) == 60 && h.payout(0) == 120 && h.bankAfter() == 940,
            "Both hands beat a dealer bust; all funds accounted for");
        BlackjackHand smallBank = BlackjackHand.start(bets, 30, pair);
        t.assertFalse(smallBank.act("split"), "Cannot split beyond dealer's reserve");
        t.complete();
    }

    private static void click(TestContext context, GamingFurnitureBlock block, BlockPos base, Direction facing,
        ServerPlayerEntity player, double x, double z) {
        Direction right = facing.rotateYCounterclockwise();
        Vec3d hit = Vec3d.ofCenter(base).add(right.getOffsetX() * x + facing.getOffsetX() * (z + .5), .397,
            right.getOffsetZ() * x + facing.getOffsetZ() * (z + .5));
        BlockPos part = BlockPos.ofFloored(hit);
        var world = context.getWorld();
        block.onUse(world.getBlockState(part), world, part, player, Hand.MAIN_HAND,
            new BlockHitResult(hit, Direction.UP, part, false));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rotatedTableSeatsDeckPrivacyAndPayout(TestContext t) {
        var w = t.getWorld();
        var block = YharnamCasino.POKER_TABLE;
        var base = t.getAbsolutePos(new BlockPos(1, 2, 1));
        var f = Direction.EAST;
        var state = block.getDefaultState().with(GamingFurnitureBlock.FACING, f);
        w.setBlockState(base, state, Block.NOTIFY_ALL);
        var dealer = t.createMockCreativeServerPlayerInWorld();
        var a = t.createMockCreativeServerPlayerInWorld();
        var b = t.createMockCreativeServerPlayerInWorld();
        block.onPlaced(w, base, state, dealer, new ItemStack(block));
        var table = (CardTableEntity) w.getBlockEntity(base);
        dealer.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.CARD_DECK));
        click(t, block, base, f, dealer, 0, -.36);
        t.assertTrue(dealer.getMainHandStack().isEmpty() && table.toInitialChunkDataNbt().contains("Back"),
            "Deck installed at dealer position");
        for (int i = 0; i < 2; i++) {
            var player = i == 0 ? a : b;
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.chip(10), 2));
            click(
                t, block, base, f, player, GamingFurnitureBlock.seatX(false, i), GamingFurnitureBlock.seatZ(false, i));
            t.assertTrue(player.getMainHandStack().getCount() == 1, "Right click spends exactly one chip");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
        click(t, block, base, f, dealer, 0, -.36);
        t.assertTrue(table.playing(), "Human dealer started round from its own side");
        NbtCompound snapshot = table.toInitialChunkDataNbt();
        t.assertTrue(Arrays.equals(snapshot.getIntArray("Cards0"), new int[] {-1, -1}) && !snapshot.contains("Poker")
                && !snapshot.contains("Deck"),
            "Network snapshot contains only card backs, with no deck or engine state");
        a.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(1),3));
        a.setSneaking(true);
        click(t,block,base,f,a,0,0);
        t.assertTrue(table.toInitialChunkDataNbt().getLong("DraftRaise")==3 && a.getMainHandStack().getCount()==3,
                "Compose an arbitrary raise without spending the selector chips");
        table.readNbt(table.createNbt());
        t.assertTrue(table.toInitialChunkDataNbt().getLong("DraftRaise")==3,"Raise selection survives reload");
        a.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        click(t,block,base,f,a,0,0);
        t.assertTrue(table.toInitialChunkDataNbt().getLong("DraftRaise")==0,"Cancel draft before folding");
        click(t, block, base, f, a, GamingFurnitureBlock.seatX(false, 0), GamingFurnitureBlock.seatZ(false, 0));
        click(t, block, base, f, dealer, 0, -.36);
        t.assertFalse(table.playing(), "Dealer settled after fold following reload");
        var mail = CasinoMailbox.get(w.getServer());
        mail.deliver(a, false);
        mail.deliver(b, false);
        t.assertTrue(value(a) == 9 && value(b) == 11, "Bettors receive the exact balances from the shared pot");
        BlockPos proxy = block.cell(base, f, 5);
        w.breakBlock(proxy, false);
        for (int i = 0; i < block.parts(); i++)
            t.assertFalse(
                w.getBlockState(block.cell(base, f, i)).isOf(block), "Breaking an outer cell removes all parts");
        t.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blackjackDealerReserveAndChipChange(TestContext t) {
        var w=t.getWorld();
        var block=YharnamCasino.BLACK_JACK_TABLE;
        var base=t.getAbsolutePos(new BlockPos(1,1,1));
        w.setBlockState(base,block.getDefaultState(),Block.NOTIFY_ALL);
        var dealer=t.createMockCreativeServerPlayerInWorld();
        var player=t.createMockCreativeServerPlayerInWorld();
        var table=(CardTableEntity)w.getBlockEntity(base);
        dealer.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.CARD_DECK));
        table.interact(dealer,Hand.MAIN_HAND,-1);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10)));
        table.interact(player,Hand.MAIN_HAND,0);
        table.interact(dealer,Hand.MAIN_HAND,-1);
        t.assertFalse(table.playing(),"Cannot deal an unfunded blackjack hand");
        dealer.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(100)));
        table.interact(dealer,Hand.MAIN_HAND,-1);
        table.interact(dealer,Hand.MAIN_HAND,-1);
        t.assertTrue(table.playing(),"Physical bank funded; round can start");
        NbtCompound saved=table.createNbt();
        long[] bets={10,0,0,0,0,0,0};
        saved.put("BJ",BlackjackHand.start(bets,100,deck(3,8,4,6,9)).toNbt()); // 5+6; double draws J, dealer stands on 18
        table.readNbt(saved);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(50)));
        table.interact(player,Hand.MAIN_HAND,0);
        CasinoMailbox.get(w.getServer()).deliver(player,false);
        t.assertTrue(table.toInitialChunkDataNbt().getLong("Bet0")==20 && value(player)==40,"Double consumes 10, returns 40 change");
        table.interact(dealer,Hand.MAIN_HAND,-1);table.interact(dealer,Hand.MAIN_HAND,-1);
        CasinoMailbox.get(w.getServer()).deliver(player,false);
        t.assertTrue(!table.playing() && value(player)==80 && table.toInitialChunkDataNbt().getLong("Bank")==80,
                "Double paid twice the total wager from the dealer's bank");
        dealer.setSneaking(true);table.interact(dealer,Hand.MAIN_HAND,-1);CasinoMailbox.get(w.getServer()).deliver(dealer,false);
        t.assertTrue(value(dealer)==80 && !table.toInitialChunkDataNbt().contains("Back"),"Leaving returns remaining bank and removes deck");
        t.complete();
    }

    private static long value(ServerPlayerEntity player) {
        long amount = 0;
        for (var stack : player.getInventory().main)
            if (stack.getItem() instanceof ChipItem chip)
                amount += (long) chip.value() * stack.getCount();
        return amount;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void wheelChargesForOnePlayerAndPreservesResult(TestContext t) {
        var world = t.getWorld();
        var pos = t.getAbsolutePos(new BlockPos(1, 1, 1));
        var state = YharnamCasino.FORTUNE_WHEEL.getDefaultState();
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        var wheel = (FortuneWheelEntity) world.getBlockEntity(pos);
        var player = t.createMockCreativeServerPlayerInWorld();
        var other = t.createMockCreativeServerPlayerInWorld();
        t.assertTrue(wheel.use(player) && !wheel.use(other), "Only the initial player can commit power");
        long start = world.getTime();
        t.runAtTick(20, () -> {
            t.assertTrue(wheel.strength(0) > 0 && wheel.use(player), "A second physical click starts the rotor");
            var copy = new FortuneWheelEntity(pos, state);
            copy.setWorld(world);
            copy.readNbt(wheel.toInitialChunkDataNbt());
            t.assertTrue(copy.active() && Math.abs(copy.angle(.5f) - wheel.angle(.5f)) < .0001,
                "Tracking clients reconstruct the same rotation");
        });
        t.runAtTick(211, () -> {
            t.assertFalse(wheel.active(), "Rotor stopped");
            int sector = Integer.parseInt(wheel.result());
            t.assertTrue(sector == 1 + Math.floorMod(Math.round(wheel.angle(0) * 16f / (float) (2 * Math.PI)), 16),
                "Sector matches pointer");
            t.assertTrue(value(player) == 0 && world.getTime() > start, "RP spin does not issue chips");
            t.complete();
        });
    }
}
