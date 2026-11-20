package com.yharnam.casino;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class ChessTableTests implements FabricGameTest {
    private static Vec3d sq(int index) {
        return new Vec3d(ChessLayout.x(index),ChessLayout.SURFACE,ChessLayout.z(index));
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void anyoneCanMoveButOnlyOnePlayerHoldsThePiece(TestContext t) {
        var w=t.getWorld();BlockPos base=t.getAbsolutePos(new BlockPos(1,1,1));
        var block=YharnamCasino.CHESS_TABLE;var state=block.getDefaultState().with(GamingFurnitureBlock.FACING,Direction.SOUTH);
        var a=t.createMockCreativeServerPlayerInWorld();var b=t.createMockCreativeServerPlayerInWorld();
        w.setBlockState(base,state,Block.NOTIFY_ALL);block.onPlaced(w,base,state,a,new ItemStack(block));
        var chess=(ChessTableEntity)w.getBlockEntity(base);
        Vec3d center=ChessLayout.center(base,Direction.SOUTH);
        a.refreshPositionAndAngles(center.x,base.getY(),center.z+1.65,180,20);
        b.refreshPositionAndAngles(center.x,base.getY(),center.z-1.65,0,20);
        a.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(YharnamCasino.CHESS_SET));
        chess.use(w,a,sq(12));chess.use(w,a,sq(12));chess.use(w,b,sq(28));
        t.assertTrue(chess.selection()==12 && chess.piece(12)=='P',"Another visitor cannot move a held piece");
        chess.use(w,a,sq(36));t.assertTrue(chess.selection()==12 && chess.piece(12)=='P',"Illegal pawn jump leaves the board intact");
        chess.use(w,a,sq(12));t.assertTrue(chess.selection()<0 && chess.releasedSquare()==12,"Repeated click lowers the piece");
        chess.use(w,b,sq(12));chess.use(w,b,sq(28));
        t.assertTrue(chess.piece(28)=='P' && chess.piece(12)=='.' && chess.motion().from()==12,"Either visitor may play either color");
        var save=chess.createNbt();var restored=new ChessTableEntity(base,state);restored.readNbt(save);
        t.assertTrue(restored.installed() && restored.piece(28)=='P' && restored.piece(12)=='.',"Position survives reload");
        t.runAtTick(14,()->{
            chess.use(w,a,sq(52));t.assertTrue(chess.selection()==52,"Black can be played by the first visitor");
            a.refreshPositionAndAngles(center.x,base.getY(),center.z+ChessLayout.BUTTON_Z+7,0,0);
        });
        t.runAtTick(20,()->{
            t.assertTrue(chess.selection()==-1 && chess.piece(52)=='p',"Walking away returns the piece to its square");
            chess.use(w,b,new Vec3d(ChessLayout.BUTTON_X,.76,ChessLayout.BUTTON_Z));
            t.assertTrue(chess.piece(12)=='P' && chess.piece(28)=='.' && chess.resetMotion()!=null,"The physical button restores the set");
            t.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void promotionChoiceAndMateStayOnThePhysicalBoard(TestContext t) {
        var w=t.getWorld();var b=YharnamCasino.CHESS_TABLE;
        var base=t.getAbsolutePos(new BlockPos(1,1,1));var state=b.getDefaultState();
        var p=t.createMockCreativeServerPlayerInWorld();w.setBlockState(base,state);
        b.onPlaced(w,base,state,p,new ItemStack(b));var e=(ChessTableEntity)w.getBlockEntity(base);
        p.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+2,180,0);
        var n=new net.minecraft.nbt.NbtCompound();
        n.put("Set",new ItemStack(YharnamCasino.CHESS_SET).writeNbt(new net.minecraft.nbt.NbtCompound()));
        n.put("Game",ChessRulesTests.position("7k/P7/8/8/8/8/8/7K w - - 0 1").toNbt());
        e.readNbt(n);e.use(w,p,sq(48));e.use(w,p,sq(56));
        t.assertTrue(e.pendingPromotion() && e.piece(48)=='P' && e.piece(56)=='.', "A pending promotion is not committed");
        e.use(w,p,new Vec3d(ChessLayout.choiceX(3),.76,.872));
        t.assertTrue(e.piece(56)=='N' && e.motion().moving()=='P', "Underpromotion uses the actual choice, retaining the pawn for the animation");
        n.put("Game",ChessRulesTests.position("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1").toNbt());
        e.readNbt(n);e.use(w,p,sq(0));e.use(w,p,sq(56));
        t.runAtTick(16,()->{
            var snapshot=e.createNbt();e.readNbt(snapshot);e.use(w,p,sq(62));e.use(w,p,sq(63));
            t.assertTrue(e.piece(56)=='R' && e.piece(62)=='k' && e.selection()==-1, "Mate persists across a reload and further clicks");
            e.use(w,p,new Vec3d(ChessLayout.BUTTON_X,.76,ChessLayout.BUTTON_Z));
            t.assertTrue(e.piece(0)=='R' && e.piece(56)=='r',"Only reset begins the next game");
            t.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void tableCoordinatesRotateWithoutReversingRanks(TestContext t) {
        BlockPos b=t.getAbsolutePos(new BlockPos(1,1,1));
        for(Direction f:Direction.Type.HORIZONTAL) for(int s=0;s<64;s++) {
            Direction right=f.rotateYCounterclockwise();
            Vec3d point=ChessLayout.center(b,f).add(ChessLayout.x(s)*right.getOffsetX()+ChessLayout.z(s)*f.getOffsetX(),.75,
                    ChessLayout.x(s)*right.getOffsetZ()+ChessLayout.z(s)*f.getOffsetZ());
            Vec3d local=ChessLayout.local(b,f,point);
            t.assertTrue(ChessLayout.square(local.x,local.z)==s,"Every rotation must map the ray to the same square");
        }
        t.complete();
    }
}
