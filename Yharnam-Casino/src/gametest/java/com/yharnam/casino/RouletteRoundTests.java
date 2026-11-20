package com.yharnam.casino;

import java.util.HashSet;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class RouletteRoundTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyPrintedTargetAndAllSettlements(TestContext t) {
        for (var field:RouletteRound.targets()) {
            t.assertTrue(RouletteRound.targetAt(field.x(),field.z())==field,"Hit disagrees with printed "+field.label());
            int covered=Long.bitCount(field.mask());
            t.assertTrue((field.payout()+1)*covered==36,"Incorrect odds "+field.label());
            for (int n=0;n<=36;n++) {
                var book=RouletteRound.create();
                book.deposit(0,10); book.depositHouse(350);
                book.addBet(0,field,10); book.closeBets();
                book=RouletteRound.fromNbt(book.toNbt());
                book.settle(n);
                t.assertTrue(book.bankroll(0)==(field.covers(n)?10*(1+field.payout()):0)
                    && book.bankroll(0)+book.house()==360 && book.bets().isEmpty(),"Settlement "+field.label()+"/"+n);
            }
        }
        t.assertTrue(RouletteRound.targets().size()==157,"European inside and outside catalog");
        t.assertTrue(RouletteRound.targetAt(2,-.5)==null && RouletteRound.targetAt(-1,0)==null,"Outside cloth");
        t.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exposureAndLastBetCancellation(TestContext t) {
        var r=RouletteRound.create();
        r.depositHouse(350); r.deposit(0,100); r.deposit(1,100);
        r.addBet(0,RouletteRound.straight(1),10);
        r.addBet(1,RouletteRound.straight(2),10);
        boolean refused=false;
        try { r.addBet(1,RouletteRound.straight(1),1); } catch(IllegalStateException e) { refused=true; }
        t.assertTrue(refused && r.bankroll(1)==90 && r.betCount(1)==1,"No borrowing another player's losing stake");
        r.addBet(0,RouletteRound.straight(3),1);
        r.cancelBet(0);
        t.assertTrue(r.bankroll(0)==90 && r.betCount(0)==1,"Undo one wager only");
        r.cancelBet(1);
        r.closeBets();
        try { r.cancelBet(0); throw new AssertionError("cancel after lock"); } catch(IllegalStateException expected) {}
        r.settle(1);
        t.assertTrue(r.house()==0 && r.bankroll(0)==450 && r.bankroll(1)==100,"Fully funded payout");
        t.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wheelPhysicsAndRaceFinishAgree(TestContext t) {
        var random=new Random(417592);
        var pockets=new HashSet<Integer>();
        var lanes=new HashSet<Integer>();
        var order=RouletteRound.wheelOrder();
        for (int i=0;i<420;i++) {
            long seed=random.nextLong();
            double rotor=random.nextDouble()*Math.PI*2;
            var wheel=new RouletteSpin(seed,rotor);
            var end=wheel.at(wheel.duration());
            int pocket=Math.floorMod((int)Math.round((end.angle()-end.rotor())*37/(Math.PI*2)),37);
            t.assertTrue(wheel.number()==order[pocket] && Math.abs(end.radius()-.319)<.001,
                "Result matches settled ball inside visible pocket");
            t.assertTrue(wheel.at(0).radius()>.58 && wheel.at(0).height()>end.height()+.08,"Ball descends into wheel");
            if (i<5) {
                var replay=new RouletteSpin(seed,rotor);
                for (double time=.5;time<wheel.duration();time+=1)
                    t.assertTrue(wheel.at(time).equals(replay.at(time)),"Replay divergence");
            }
            pockets.add(wheel.number());
            var race=new RatRace(seed);
            double first=Double.MAX_VALUE;
            int winner=-1;
            for (int lane=0;lane<4;lane++) {
                double from=0,to=race.duration();
                for (int k=0;k<32;k++) {
                    double mid=(from+to)/2;
                    if (race.progress(lane,mid)<1) from=mid; else to=mid;
                }
                if (to<first) { first=to; winner=lane; }
            }
            t.assertTrue(winner==race.winner(),"First physical crossing wins");
            lanes.add(winner);
        }
        t.assertTrue(pockets.size()==37 && lanes.size()==4,"All pockets and lanes reachable");
        t.complete();
    }

    private static void click(TestContext t,GamingFurnitureBlock b,BlockPos base,Direction f,ServerPlayerEntity p,double x,double z) {
        Direction right=f.rotateYCounterclockwise();
        Vec3d spot=Vec3d.ofCenter(base).add(right.getOffsetX()*(x+.5)+f.getOffsetX()*(z+.5),.3516,
            right.getOffsetZ()*(x+.5)+f.getOffsetZ()*(z+.5));
        BlockPos part=BlockPos.ofFloored(spot);
        b.onUse(t.getWorld().getBlockState(part),t.getWorld(),part,p,Hand.MAIN_HAND,new BlockHitResult(spot,Direction.UP,part,false));
    }

    private static void stand(BlockPos base,Direction f,ServerPlayerEntity player,int seat) {
        var right=f.rotateYCounterclockwise();
        double x=AttractionEntity.seatX(seat)*(seat>=4?1.3:1)+.5;
        double z=AttractionEntity.seatZ(seat)*1.6+.5;
        player.refreshPositionAndAngles(base.getX()+.5+right.getOffsetX()*x+f.getOffsetX()*z,base.getY()+1,
            base.getZ()+.5+right.getOffsetZ()*x+f.getOffsetZ()*z,0,0);
    }
    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=330)
    public void completeMultiplayerTableAcrossReload(TestContext t) {
        var w=t.getWorld(); var b=YharnamCasino.ROULETTE_TABLE;
        var base=t.getAbsolutePos(new BlockPos(2,2,1)); var f=Direction.WEST;
        var state=b.getDefaultState().with(GamingFurnitureBlock.FACING,f);
        w.setBlockState(base,state,Block.NOTIFY_ALL);
        var dealer=t.createMockCreativeServerPlayerInWorld();
        stand(base,f,dealer,5);
        b.onPlaced(w,base,state,dealer,new ItemStack(b));
        var game=(AttractionEntity)w.getBlockEntity(base);
        var players=new ServerPlayerEntity[5];
        for (int i=0;i<5;i++) {
            var p=players[i]=t.createMockCreativeServerPlayerInWorld();
            stand(base,f,p,i);
            click(t,b,base,f,p,AttractionEntity.seatX(i),AttractionEntity.seatZ(i));
            p.setSneaking(true);
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10),2));
            click(t,b,base,f,p,AttractionEntity.seatX(i),AttractionEntity.seatZ(i));
            p.setSneaking(false);
            t.assertTrue(game.role(p.getUuid())==i && p.getMainHandStack().isEmpty() && game.ledger().bankroll(i)==20,"Seat deposit "+i);
        }
        var zero=RouletteRound.straight(0);
        var first=players[0];
        long initial=game.ledger().bankroll(0);
        click(t,b,base,f,first,AttractionEntity.seatX(0),AttractionEntity.seatZ(0));
        t.assertTrue(game.stake(0)==50 && game.ledger().bankroll(0)==initial,"Cycle a denomination without buying chips");
        first.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10)));
        click(t,b,base,f,players[0],zero.x(),zero.z());
        t.assertTrue(game.ledger().bets().isEmpty(),"Bets require dealer");
        click(t,b,base,f,dealer,AttractionEntity.seatX(0),AttractionEntity.seatZ(0));
        t.assertTrue(game.role(dealer.getUuid())==-1,"Occupied seat protected");
        click(t,b,base,f,dealer,AttractionEntity.seatX(5),AttractionEntity.seatZ(5));
        dealer.setSneaking(true);
        dealer.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(1000),2));
        click(t,b,base,f,dealer,AttractionEntity.seatX(5),AttractionEntity.seatZ(5));
        for (int i=0;i<5;i++) click(t,b,base,f,players[i],zero.x(),zero.z());
        t.assertTrue(game.ledger().bets().size()==5 && game.ledger().house()==2000,"Five funded wagers");
        click(t,b,base,f,players[1],RouletteSpin.CX,RouletteSpin.CZ);
        t.assertFalse(game.moving(),"Only dealer can spin");
        click(t,b,base,f,dealer,RouletteSpin.CX,RouletteSpin.CZ);
        t.assertTrue(game.moving(),"Started by dealer");
        game.readNbt(game.createNbt());
        var copy=new AttractionEntity(base,state); copy.setWorld(w); copy.readNbt(game.toInitialChunkDataNbt());
        t.assertTrue(copy.ball(.5f).equals(game.ball(.5f)) && copy.piles().size()==5,"Synced motion and physical stacks");
        t.runAtTick(310,()->{
            t.assertTrue(!game.moving() && game.ledger().result()>=0,"Scheduled settlement");
            long cash=game.ledger().house();
            for(int i=0;i<5;i++) cash+=game.ledger().bankroll(i);
            t.assertTrue(cash==2100,"Total escrow conserved");
            game.onRemoved(w);
            t.assertTrue(game.ledger().house()==0 && game.role(dealer.getUuid())==-1,"Removal returns deposits");
            t.complete();
        });
    }

    private static long inventoryValue(ServerPlayerEntity player) {
        long value=0;
        for (var stack:player.getInventory().main) if (stack.getItem() instanceof ChipItem chip)
            value+=(long)stack.getCount()*chip.value();
        return value;
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removalDuringSpinPaysExactlyOnce(TestContext t) {
        var w=t.getWorld();
        var b=YharnamCasino.ROULETTE_TABLE;
        var base=t.getAbsolutePos(new BlockPos(2,2,2));
        var f=Direction.EAST;
        var state=b.getDefaultState().with(GamingFurnitureBlock.FACING,f);
        w.setBlockState(base,state,Block.NOTIFY_ALL);
        var dealer=t.createMockCreativeServerPlayerInWorld();
        var player=t.createMockCreativeServerPlayerInWorld();
        stand(base,f,dealer,5);
        stand(base,f,player,2);
        b.onPlaced(w,base,state,dealer,new ItemStack(b));
        var game=(AttractionEntity)w.getBlockEntity(base);
        click(t,b,base,f,player,AttractionEntity.seatX(0),AttractionEntity.seatZ(0));
        t.assertTrue(game.role(player.getUuid())==-1,"Cannot reserve the opposite side");
        stand(base,f,player,0);
        click(t,b,base,f,dealer,AttractionEntity.seatX(5),AttractionEntity.seatZ(5));
        dealer.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(1000)));
        click(t,b,base,f,dealer,AttractionEntity.seatX(5),AttractionEntity.seatZ(5));
        click(t,b,base,f,player,AttractionEntity.seatX(0),AttractionEntity.seatZ(0));
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(10),2));
        click(t,b,base,f,player,AttractionEntity.seatX(0),AttractionEntity.seatZ(0));
        t.assertTrue(player.getMainHandStack().getCount()==1,"Ordinary deposit takes one chip");
        var red=RouletteRound.targetAt(.515,.407);
        click(t,b,base,f,player,red.x(),red.z());
        click(t,b,base,f,dealer,RouletteSpin.CX,RouletteSpin.CZ);
        var n=game.createNbt();
        var spin=new RouletteSpin(n.getLong("Seed"),n.getDouble("Rotor"));
        boolean wins=red.covers(spin.number());
        w.removeBlock(b.cell(base,f,4),false);
        t.assertTrue(w.getBlockEntity(base)==null && inventoryValue(dealer)==(wins?990:1010)
            && inventoryValue(player)==(wins?30:10),"Breaking a proxy settles the committed outcome");
        game.onRemoved(w);
        t.assertTrue(inventoryValue(dealer)+inventoryValue(player)==1020,"No double payment");
        t.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=234)
    public void oneButtonDerbyKeepsChips(TestContext t) {
        var w=t.getWorld(); var b=YharnamCasino.RAT_DERBY;
        var base=t.getAbsolutePos(new BlockPos(1,2,1)); var f=Direction.NORTH;
        var state=b.getDefaultState().with(GamingFurnitureBlock.FACING,f);
        w.setBlockState(base,state,Block.NOTIFY_ALL);
        var p=t.createMockCreativeServerPlayerInWorld();
        b.onPlaced(w,base,state,p,new ItemStack(b));
        var game=(AttractionEntity)w.getBlockEntity(base);
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(YharnamCasino.chip(1000),64));
        click(t,b,base,f,p,0,0); t.assertFalse(game.moving(),"Only the starter launches");
        click(t,b,base,f,p,1.4,.69); var snapshot=game.createNbt();
        click(t,b,base,f,p,1.4,.69); t.assertTrue(snapshot.equals(game.createNbt()),"No restart mid-race");
        game.readNbt(snapshot);
        t.runAtTick(30,()->t.assertTrue(game.moving() && game.ratProgress(0,0)>0,"Visible race"));
        t.runAtTick(231,()->{
            t.assertTrue(!game.moving() && game.lastLane()>=0 && game.lastLane()<4
                && p.getMainHandStack().getCount()==64,"RP result, no inventory change");
            t.complete();
        });
    }
}
