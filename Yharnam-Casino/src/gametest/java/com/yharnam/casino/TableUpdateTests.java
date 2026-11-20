package com.yharnam.casino;

import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Regression coverage for the physical-table changes.  These tests deliberately
 * use the entity APIs that the block interaction path calls.
 */
public final class TableUpdateTests implements FabricGameTest {
    private static int[] deck(int... prefix) {
        int[] result = Arrays.copyOf(prefix, 52);
        boolean[] used = new boolean[52];
        for (int card : prefix) used[card] = true;
        int next = prefix.length;
        for (int card = 0; card < 52; card++) if (!used[card]) result[next++] = card;
        return result;
    }

    private static long chips(ServerPlayerEntity p) {
        long total = 0;
        for (var stack : p.getInventory().main)
            if (stack.getItem() instanceof ChipItem chip) total += (long) chip.value() * stack.getCount();
        return total;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blackjackDealerStopsAtEighteen(TestContext t) {
        long[] bets = {10, 0, 0, 0, 0, 0, 0};
        BlackjackHand hand = BlackjackHand.start(bets, 1000, deck(0, 8, 4, 6, 9, 10));
        while (hand.phase().equals("players")) hand.act("stand");
        t.assertTrue(hand.phase().equals("dealer"), "The dealer phase starts after the player stands");
        hand.dealerAdvance();
        int total = CardRules.blackjackTotal(hand.dealerCards());
        t.assertTrue(total >= 18 || hand.phase().equals("showdown"),
            "Dealer draws at most until 18");
        t.assertTrue(hand.dealerCards().length==2 && total==18,"Exactly 18 must stand before any third card");
        int before = hand.dealerCards().length;
        hand.dealerAdvance();
        t.assertTrue(hand.dealerCards().length == before,
            "Dealer cannot draw another card once the visible total is 18 or higher");
        t.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rouletteAbortReturnsEveryUnresolvedBetOnce(TestContext t) {
        RouletteRound round = RouletteRound.create();
        round.depositHouse(1000);
        round.deposit(0, 50);
        round.addBet(0, RouletteRound.straight(17), 20);
        round.addBet(0, RouletteRound.straight(18), 10);
        round.closeBets();
        round.abort();
        t.assertTrue(round.bankroll(0) == 50 && round.house() == 1000
            && round.bets().isEmpty() && !round.spinning(),
            "Aborting a roulette round restores all stakes exactly once");
        round.abort();
        t.assertTrue(round.bankroll(0) == 50 && round.house() == 1000,
            "A second reset does not duplicate the refund");
        t.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blackjackSeatLeavingReturnsWaitingDeposit(TestContext t) {
        var w = t.getWorld();
        BlockPos pos = t.getAbsolutePos(new BlockPos(1, 1, 1));
        var block = YharnamCasino.BLACK_JACK_TABLE;
        var state = block.getDefaultState();
        w.setBlockState(pos, state, Block.NOTIFY_ALL);
        var dealer = t.createMockCreativeServerPlayerInWorld();
        var player = t.createMockCreativeServerPlayerInWorld();
        block.onPlaced(w, pos, state, dealer, new ItemStack(block));
        var table = (CardTableEntity) w.getBlockEntity(pos);
        dealer.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()-1,0,0);
        dealer.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.CARD_DECK));
        table.interact(dealer, Hand.MAIN_HAND, -1);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.chip(10)));
        table.interact(player, Hand.MAIN_HAND, 0);
        t.assertTrue(table.display().getLong("Bet0") == 10, "Seat holds its physical deposit");
        player.refreshPositionAndAngles(pos.getX() + 20, pos.getY(), pos.getZ() + 20, 0, 0);
        table.watch(w);
        t.runAtTick(25, () -> {
            table.watch(w);
            CasinoMailbox.get(w.getServer()).deliver(player, false);
            t.assertTrue(table.display().getLong("Bet0") == 0 && chips(player) == 10,
                "Leaving an idle seat returns its deposit");
            CasinoMailbox.get(w.getServer()).deliver(player, false);
            t.assertTrue(chips(player) == 10, "Repeated delivery cannot duplicate the return");
            t.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void activeHandReturnsBankDeckAndStakesOnDealerDeparture(TestContext t) {
        var w=t.getWorld();var base=t.getAbsolutePos(new BlockPos(1,1,1));
        var block=YharnamCasino.BLACK_JACK_TABLE;
        w.setBlockState(base,block.getDefaultState(),Block.NOTIFY_ALL);
        var table=(CardTableEntity)w.getBlockEntity(base);
        var dealer=t.createMockCreativeServerPlayerInWorld();var player=t.createMockCreativeServerPlayerInWorld();
        dealer.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()-1,0,0);
        player.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+2,180,0);
        dealer.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.CARD_DECK));table.interact(dealer,Hand.MAIN_HAND,-1);
        dealer.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(100)));table.interact(dealer,Hand.MAIN_HAND,-1);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10)));table.interact(player,Hand.MAIN_HAND,0);
        table.interact(dealer,Hand.MAIN_HAND,-1);
        t.assertTrue(table.playing(),"Active blackjack round");
        table.readNbt(table.createNbt());
        dealer.refreshPositionAndAngles(base.getX()+12,base.getY(),base.getZ(),0,0);
        table.watch(w); table.watch(w);
        t.assertTrue(!table.playing() && chips(player)==10 && chips(dealer)==100 &&
            dealer.getInventory().contains(new ItemStack(YharnamCasino.CARD_DECK)),"Dealer, player and deck paid exactly once");
        t.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fiveBlocksMeasuredFromEdgeInAllFacings(TestContext t) {
        var w=t.getWorld();var base=t.getAbsolutePos(new BlockPos(1,1,1));
        var block=YharnamCasino.ROULETTE_TABLE;var p=t.createMockCreativeServerPlayerInWorld();
        for(var facing:net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            w.setBlockState(base,block.getDefaultState().with(GamingFurnitureBlock.FACING,facing));
            var right=facing.rotateYCounterclockwise();
            Vec3d center=Vec3d.ofCenter(base).add((right.getOffsetX()+facing.getOffsetX())*.5,0,(right.getOffsetZ()+facing.getOffsetZ())*.5);
            for(double gap:new double[]{4.99,5.01}) {
                var at=center.add((gap+1.85)*right.getOffsetX(),0,(gap+1.85)*right.getOffsetZ());
                p.refreshPositionAndAngles(at.x,at.y,at.z,0,0);
                t.assertTrue(GamingFurnitureBlock.inRange(w,base,p.getUuid())==(gap<5),"Range from rotated long edge");
            }
        }
        t.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void leavingDuringRouletteReturnsOnlyOnceAndKeepsDealer(TestContext t) {
        var w=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(1,1,1));
        w.setBlockState(pos,YharnamCasino.ROULETTE_TABLE.getDefaultState());
        var game=(AttractionEntity)w.getBlockEntity(pos);
        var house=t.createMockCreativeServerPlayerInWorld();var gambler=t.createMockCreativeServerPlayerInWorld();
        house.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()-1,0,0);
        gambler.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+2,180,0);
        game.use(house,-1.52,-.69);house.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(1000)));
        game.use(house,-1.52,-.69);game.use(gambler,-.24,.72);
        gambler.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10),3));gambler.setSneaking(true);
        game.use(gambler,-.24,.72);gambler.setSneaking(false);
        var n=RouletteRound.straight(17);game.use(gambler,n.x(),n.z());game.use(house,RouletteSpin.CX,RouletteSpin.CZ);
        t.assertTrue(game.moving() && game.ledger().bankroll(0)==20,"Escrow and unused balance both present");
        game.readNbt(game.createNbt());gambler.refreshPositionAndAngles(pos.getX()+14,pos.getY(),pos.getZ(),0,0);
        game.advanceClock(w);game.advanceClock(w);
        t.assertTrue(!game.moving() && game.role(house.getUuid())==AttractionEntity.DEALER && game.role(gambler.getUuid())<0
                && chips(gambler)==30 && game.ledger().house()==1000,"All 30 returned exactly once and the dealer keeps the bank");
        t.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void derbyResetReturnsAllLanesToStartAfterReload(TestContext t) {
        var w=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(1,1,1));
        w.setBlockState(pos,YharnamCasino.RAT_DERBY.getDefaultState());
        var game=(AttractionEntity)w.getBlockEntity(pos);var p=t.createMockCreativeServerPlayerInWorld();
        game.use(p,1.12,.70);t.assertTrue(game.moving(),"Race started");
        game.use(p,-1.23,.70);game.readNbt(game.createNbt());
        for(int lane=0;lane<4;lane++) t.assertTrue(game.ratProgress(lane,0)==0,"Racer reset at its gate");
        t.assertTrue(!game.moving() && game.lastLane()<0,"Old winner and motion are cleared");t.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void resetKeepsRouletteRefundBalanced(TestContext t) {
        var w = t.getWorld();
        BlockPos pos = t.getAbsolutePos(new BlockPos(1, 1, 1));
        var block = YharnamCasino.ROULETTE_TABLE;
        var state = block.getDefaultState();
        w.setBlockState(pos, state, Block.NOTIFY_ALL);
        var dealer = t.createMockCreativeServerPlayerInWorld();
        var player = t.createMockCreativeServerPlayerInWorld();
        block.onPlaced(w, pos, state, dealer, new ItemStack(block));
        var game = (AttractionEntity) w.getBlockEntity(pos);
        dealer.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() - 1, 0, 0);
        player.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + 2, 0, 0);
        game.use(dealer, -1.5, -.7);
        dealer.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(1000)));
        game.use(dealer,-1.5,-.7);
        game.use(player, -.24, .72);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(YharnamCasino.chip(10)));
        game.use(player, -.24, .72);
        var number=RouletteRound.straight(17);game.use(player,number.x(),number.z());
        t.assertTrue(game.ledger().bets().size() == 1, "A physical chip creates one roulette bet");
        game.use(player,-.85,-.70);
        t.assertTrue(game.ledger().bets().size()==1,"A guest cannot reset the funded table");
        game.use(dealer,RouletteSpin.CX,RouletteSpin.CZ);
        t.assertTrue(game.moving(),"Round locked before croupier reset");
        game.use(dealer, -.85, -.70);
        t.assertTrue(game.ledger().bets().isEmpty() && game.ledger().bankroll(0) == 0,
            "Reset clears the unresolved bet");
        t.assertTrue(chips(dealer)==1000 && chips(player)==10,"Every escrowed chip is returned");
        game.readNbt(game.createNbt());
        game.use(dealer,-.85,-.70);
        t.assertTrue(chips(dealer)==1000 && chips(player)==10 && !game.moving() && game.ledger().result()<0,
                "Reloading and repeated reset preserve total and cleared outcome");
        t.complete();
    }
}
