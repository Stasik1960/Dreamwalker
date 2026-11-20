package com.yharnam.casino;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.List;

/** Regression coverage for the orthodox chess position used by the physical table. */
public final class ChessRulesTests implements FabricGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void startingPositionHasExpectedPerft(TestContext context) {
        ChessGame game = new ChessGame();
        context.assertTrue(game.legalMoves().size() == 20, "Initial position must have 20 legal moves");
        context.assertTrue(perft(game, 2) == 400, "Perft depth 2 must be 400");
        context.assertTrue(perft(game, 3) == 8902, "Perft depth 3 must be 8902");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void specialMovesAndPromotionStayLegal(TestContext context) {
        ChessGame game = position("4k3/7P/8/8/8/8/8/4K3 w - - 0 1");
        context.assertTrue(game.legalMoves(square("h7")).size() == 4, "A pawn on h7 must have four promotions");
        context.assertTrue(game.play(square("h7"), square("h8"), 'N'), "Knight promotion must be accepted");
        context.assertTrue(game.piece(square("h8")) == 'N', "Promotion piece must be placed on the destination");

        game = position("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        context.assertTrue(game.legalMoves(square("e1")).stream().anyMatch(m -> m.castle() && m.to() == square("g1")),
                "Kingside castling must be available");
        context.assertTrue(game.play(square("e1"), square("g1"), ' '), "Castling must move the king");
        context.assertTrue(game.piece(square("f1")) == 'R', "Castling must move the rook");

        game = position("4r1k1/8/8/8/8/8/4R3/4K3 w - - 0 1");
        context.assertTrue(!game.play(square("e2"), square("a2"), ' '), "A pinned rook cannot expose its king");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void checkMateStaleMateAndEnPassantAreRecognized(TestContext context) {
        ChessGame mate = position("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1");
        context.assertTrue(mate.result() == ChessGame.Result.CHECKMATE, "The boxed king must be checkmate");
        ChessGame stale = position("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        context.assertTrue(stale.result() == ChessGame.Result.STALEMATE, "A checked-free boxed king must be stalemate");

        ChessGame ep = position("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        context.assertTrue(ep.legalMoves(square("e5")).stream().anyMatch(m -> m.enPassant() && m.to() == square("d6")),
                "The en passant target must be playable");
        context.assertTrue(ep.play(square("e5"), square("d6"), ' '), "En passant must be accepted");
        context.assertTrue(ep.piece(square("d5")) == '.', "En passant must remove the bypassed pawn");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void drawCountersAndPositionHistorySurviveNbt(TestContext context) {
        ChessGame game = position("7k/8/8/8/8/8/8/K6R w - - 99 50");
        context.assertTrue(!game.canClaimFifty(), "A current-position claim is premature at 99 half-moves");
        context.assertTrue(game.canClaimAfter(new ChessGame.Move(square("a1"), square("a2"))),
                "A legal move can be claimed when the fifty-move threshold is reached");

        ChessGame roundTrip = new ChessGame();
        roundTrip.readNbt(game.toNbt());
        context.assertTrue(roundTrip.piece(square("h1")) == 'R' && roundTrip.halfmove() == game.halfmove()
                        && roundTrip.fullmove() == game.fullmove(), "Chess state must survive NBT round-trip");

        ChessGame repetition = new ChessGame();
        for (int i = 0; i < 4; i++) {
            move(repetition, "g1", "f3"); move(repetition, "g8", "f6");
            move(repetition, "f3", "g1"); move(repetition, "f6", "g8");
        }
        context.assertTrue(repetition.result() == ChessGame.Result.DRAW_REPETITION,
                "Repeated positions must be recorded");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void DeadMaterialUsesBishopSquareColors(TestContext context) {
        ChessGame same = position("7k/8/8/8/8/8/1B6/K5b1 w - - 0 1");
        context.assertTrue(same.result() == ChessGame.Result.DRAW_MATERIAL, "Same-colored bishops are dead material");
        ChessGame opposite = position("7k/8/8/8/8/8/2B5/K5b1 w - - 0 1");
        context.assertTrue(opposite.result() != ChessGame.Result.DRAW_MATERIAL, "Opposite-colored bishops can mate");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rightsAndRareKingSafetyCases(TestContext t) {
        var castle = position("4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        t.assertTrue(!castle.play(square("e1"),square("g1"),' '), "Cannot cross attacked f1");
        var ep = position("4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1");
        t.assertTrue(!ep.play(square("g5"),square("f6"),' '), "En passant would expose the king to a rook");
        var seventyFive = position("7k/8/8/8/8/8/8/K6R w - - 149 75");
        t.assertTrue(seventyFive.canClaimFifty() && seventyFive.play(square("a1"),square("a2"),' ')
                && seventyFive.result()==ChessGame.Result.DRAW_SEVENTY_FIVE, "Automatic draw after 150 half-moves");
        var fooled = new ChessGame();
        move(fooled,"f2","f3");move(fooled,"e7","e5");move(fooled,"g2","g4");move(fooled,"d8","h4");
        t.assertTrue(fooled.result()==ChessGame.Result.CHECKMATE && !fooled.play(square("a2"),square("a3"),' '), "Mate ends play");
        var kiwipete = position("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        t.assertTrue(perft(kiwipete,2)==2039, "Castling and pins perft position");
        var wall = position("6k1/8/8/p1p1p1p1/P1P1P1P1/8/8/1K6 w - - 0 1");
        t.assertTrue(wall.result()==ChessGame.Result.DRAW_MATERIAL, "Neither king can cross the locked pawn wall");
        var open = position("6k1/8/8/p1p1p3/P1P1P3/8/8/1K6 w - - 0 1");
        t.assertTrue(!open.finished(), "The king can walk round the end of the wall");
        var pinnedEp = position("4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1");
        var noEp = position("4k3/8/8/r4pPK/8/8/8/8 w - - 0 1");
        t.assertTrue(pinnedEp.toNbt().getCompound("History").equals(noEp.toNbt().getCompound("History")),
                "An illegal en passant opportunity does not distinguish repeated positions");
        t.complete();
    }
    private static long perft(ChessGame game, int depth) {
        if (depth == 0) return 1;
        long count = 0;
        for (ChessGame.Move move : game.legalMoves()) {
            ChessGame next = new ChessGame();
            next.readNbt(game.toNbt());
            if (next.play(move)) count += perft(next, depth - 1);
        }
        return count;
    }

    private static void move(ChessGame game, String from, String to) {
        if (!game.play(square(from), square(to), ' ')) throw new AssertionError("Illegal test move " + from + to);
    }

    static ChessGame position(String fen) {
        String[] fields = fen.split(" ");
        String[] ranks = fields[0].split("/");
        StringBuilder board = new StringBuilder(64);
        for (int i = 7; i >= 0; i--) {
            String rank = ranks[i];
            for (char ch : rank.toCharArray()) {
                if (Character.isDigit(ch)) board.append(".".repeat(ch - '0'));
                else board.append(ch);
            }
        }
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Board", board.toString());
        nbt.putBoolean("White", fields[1].equals("w"));
        nbt.putBoolean("WK", fields[2].contains("K")); nbt.putBoolean("WQ", fields[2].contains("Q"));
        nbt.putBoolean("BK", fields[2].contains("k")); nbt.putBoolean("BQ", fields[2].contains("q"));
        nbt.putInt("EP", fields[3].equals("-") ? -1 : square(fields[3]));
        nbt.putInt("Half", Integer.parseInt(fields[4])); nbt.putInt("Full", Integer.parseInt(fields[5]));
        ChessGame game = new ChessGame(); game.readNbt(nbt); return game;
    }

    private static int square(String algebraic) {
        return (algebraic.charAt(0) - 'a') + 8 * (algebraic.charAt(1) - '1');
    }
}
