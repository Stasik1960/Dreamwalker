package com.yharnam.casino;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/** Orthodox chess. A1 is square zero; upper case denotes white. */
public final class ChessGame {
    public enum Result { PLAYING, CHECK, CHECKMATE, STALEMATE, DRAW_REPETITION, DRAW_FIFTY, DRAW_SEVENTY_FIVE, DRAW_MATERIAL }
    public record Move(int from, int to, char promotion, boolean castle, boolean enPassant) {
        public Move(int from, int to) { this(from, to, ' ', false, false); }
    }
    private static final int[][] KNIGHTS={{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};
    private static final int[][] RAYS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}};
    private static final String INITIAL="RNBQKBNRPPPPPPPP................................pppppppprnbqkbnr";
    private final char[] board=new char[64];
    private final Map<String,Integer> repetitions=new HashMap<>();
    private boolean white;
    private int rights, ep, half, full;
    private Result cached;

    public ChessGame() { reset(); }
    public void reset() {
        INITIAL.getChars(0,64,board,0);
        white=true;rights=15;ep=-1;half=0;full=1;cached=null;
        repetitions.clear();repetitions.put(key(),1);
    }
    public char piece(int square) { return square>=0 && square<64 ? board[square] : '.'; }
    public boolean whiteTurn() { return white; }
    public int enPassant() { return ep; }
    public int halfmove() { return half; }
    public int fullmove() { return full; }
    private static boolean inside(int x,int y) { return x>=0 && x<8 && y>=0 && y<8; }
    private static boolean is(char piece,boolean white,char type) {
        return piece==(white ? Character.toUpperCase(type) : type);
    }
    private static int king(char[] position, boolean white) {
        char k=white?'K':'k';
        for(int i=0;i<64;i++) if(position[i]==k) return i;
        return -1;
    }
    public boolean inCheck(boolean side) { return attacked(board,king(board,side),!side); }
    private static boolean attacked(char[] position,int square,boolean byWhite) {
        if(square<0) return true;
        int x=square&7,y=square>>3;
        for(int dx : new int[]{-1,1}) {
            int sx=x+dx, sy=y+(byWhite?-1:1);
            if(inside(sx,sy) && is(position[sy*8+sx],byWhite,'p')) return true;
        }
        for(int[] d:KNIGHTS) {
            int sx=x+d[0],sy=y+d[1];
            if(inside(sx,sy) && is(position[sy*8+sx],byWhite,'n')) return true;
        }
        for(int i=0;i<RAYS.length;i++) {
            int[] d=RAYS[i];int sx=x+d[0],sy=y+d[1];boolean adjacent=true;
            while(inside(sx,sy)) {
                char p=position[sy*8+sx];
                if(p!='.') {
                    if(is(p,byWhite,'q') || is(p,byWhite,i<4?'r':'b') || (adjacent && is(p,byWhite,'k'))) return true;
                    break;
                }
                sx+=d[0];sy+=d[1];adjacent=false;
            }
        }
        return false;
    }
    private void pawnMove(int from,int to,boolean passant,List<Move> moves) {
        if(to/8==0 || to/8==7) {
            for(char type : new char[]{'q','r','b','n'})
                moves.add(new Move(from,to,white?Character.toUpperCase(type):type,false,false));
        } else moves.add(new Move(from,to,' ',false,passant));
    }
    private boolean enemy(int square) {
        char p=board[square];
        return p!='.' && Character.isUpperCase(p)!=white && Character.toLowerCase(p)!='k';
    }
    private void pawnMoves(int from,List<Move> moves) {
        int delta=white?8:-8, next=from+delta;
        if(next<0 || next>=64) return;
        if(board[next]=='.') {
            pawnMove(from,next,false,moves);
            if(from/8==(white?1:6) && board[from+2*delta]=='.') moves.add(new Move(from,from+2*delta));
        }
        for(int dx:new int[]{-1,1}) {
            if(!inside((from&7)+dx,next>>3)) continue;
            int to=next+dx;
            boolean passant=to==ep && board[to]=='.' && is(board[to-delta],!white,'p');
            if(enemy(to) || passant) pawnMove(from,to,passant,moves);
        }
    }
    private void castle(int from,boolean shortSide,List<Move> moves) {
        int home=white?4:60,step=shortSide?1:-1,rook=home+(shortSide?3:-4);
        int bit=(white?1:4)*(shortSide?1:2);
        if(from!=home || (rights&bit)==0 || !is(board[rook],white,'r') || attacked(board,home,!white)) return;
        for(int i=home+step;i!=rook;i+=step) if(board[i]!='.') return;
        char[] transit=board.clone();transit[home]='.';transit[home+step]=board[home];
        if(!attacked(transit,home+step,!white)) moves.add(new Move(home,home+2*step,' ',true,false));
    }
    private List<Move> pseudo(int from) {
        List<Move> moves=new ArrayList<>();
        char p=piece(from);
        if(p=='.' || Character.isUpperCase(p)!=white) return moves;
        char type=Character.toLowerCase(p);
        if(type=='p') { pawnMoves(from,moves);return moves; }
        int[][] directions=type=='n'?KNIGHTS:RAYS;
        for(int i=0;i<directions.length;i++) {
            if((type=='r' && i>=4)||(type=='b' && i<4)) continue;
            int[] d=directions[i];int x=(from&7)+d[0],y=(from>>3)+d[1];
            while(inside(x,y)) {
                int to=y*8+x;
                if(board[to]=='.' || enemy(to)) moves.add(new Move(from,to));
                if(board[to]!='.' || type=='n' || type=='k') break;
                x+=d[0];y+=d[1];
            }
        }
        if(type=='k') { castle(from,true,moves);castle(from,false,moves); }
        return moves;
    }
    static int captureSquare(Move m) { return m.enPassant() ? (m.from()&56)+(m.to()&7) : m.to(); }
    static int rookFrom(Move m) { return (m.from()&56)+(m.to()>m.from()?7:0); }
    static int rookTo(Move m) { return (m.from()+m.to())/2; }
    private static void put(char[] p,Move move) {
        char moving=p[move.from()];p[move.from()]='.';
        if(move.enPassant()) p[captureSquare(move)]='.';
        if(move.castle()) {
            p[rookTo(move)]=p[rookFrom(move)];p[rookFrom(move)]='.';
        }
        p[move.to()]=move.promotion()==' '?moving:move.promotion();
    }
    private boolean safe(Move move) {
        char[] after=board.clone();put(after,move);
        return !attacked(after,king(after,white),!white);
    }
    public List<Move> legalMoves(int from) {
        var moves=pseudo(from);moves.removeIf(m->!safe(m));return moves;
    }
    public List<Move> legalMoves() {
        var moves=new ArrayList<Move>();
        for(int i=0;i<64;i++) if(board[i]!='.' && Character.isUpperCase(board[i])==white) moves.addAll(legalMoves(i));
        return moves;
    }
    private static int rookRight(int square,char type) {
        return switch(square) {
            case 0 -> type=='R'?2:0; case 7 -> type=='R'?1:0;
            case 56 -> type=='r'?8:0; case 63 -> type=='r'?4:0;
            default -> 0;
        };
    }
    private void apply(Move m) {
        char moving=piece(m.from()),captured=piece(captureSquare(m));
        boolean pawn=Character.toLowerCase(moving)=='p';
        int before=rights;
        if(moving=='K') rights&=~3;
        if(moving=='k') rights&=~12;
        rights&=~(rookRight(m.from(),moving)|rookRight(m.to(),captured));
        put(board,m);
        ep=pawn && Math.abs(m.to()-m.from())==16 ? (m.to()+m.from())/2 : -1;
        half=pawn || captured!='.'?0:half+1;
        if(!white) full++;
        white=!white;cached=null;
        if(pawn || captured!='.' || before!=rights) repetitions.clear();
        repetitions.merge(key(),1,Integer::sum);
    }
    public boolean play(Move move) {
        if(finished() || !legalMoves(move.from()).contains(move)) return false;
        apply(move);return true;
    }
    public boolean play(int from,int to,char promotion) {
        for(Move m:legalMoves(from)) if(m.to()==to && Character.toLowerCase(m.promotion())==Character.toLowerCase(promotion)) return play(m);
        return false;
    }
    private String key() {
        int effective=-1;
        if(ep>=0) {
            int behind=ep+(white?-8:8);
            for(int dx:new int[]{-1,1}) {
                if(!inside((behind&7)+dx,behind>>3)) continue;
                int from=behind+dx;
                if(!is(board[from],white,'p') || !is(board[behind],!white,'p')) continue;
                if(safe(new Move(from,ep,' ',false,true))) { effective=ep;break; }
            }
        }
        return new String(board)+(white?"w":"b")+":"+rights+":"+effective;
    }
    private boolean deadMaterial() {
        int minors=0,knights=0,bishopColor=-1;
        for(int s=0;s<64;s++) switch(Character.toLowerCase(board[s])) {
            case 'q','r','p' -> { return false; }
            case 'n' -> { knights++;minors++; }
            case 'b' -> {
                int color=((s&7)+(s>>3))&1;
                if(bishopColor>=0 && bishopColor!=color) return false;
                bishopColor=color;minors++;
            }
        }
        return minors<=1 || knights==0;
    }

    /** Prove a closed pawn wall is permanent. Ignore the other king when looking
     * for an escape, so the reachable set is a superset of every legal king route. */
    private boolean frozenPawnWall() {
        if(ep>=0) return false;
        for(int s=0;s<64;s++) {
            char p=board[s];
            if(p=='.' || Character.toLowerCase(p)=='k') continue;
            if(Character.toLowerCase(p)!='p') return false;
            boolean light=Character.isUpperCase(p);
            int next=s+(light?8:-8);
            if(Character.toLowerCase(piece(next))!='p') return false;
            for(int dx:new int[]{-1,1})
                if(inside((s&7)+dx,next>>3) && is(board[next+dx],!light,'p')) return false;
        }
        for(boolean light:new boolean[]{true,false}) {
            int start=king(board,light),read=0,count=1;
            int[] queue=new int[64];queue[0]=start;long seen=1L<<start;
            while(read<count) {
                int s=queue[read++];
                for(int[] d:RAYS) {
                    int x=(s&7)+d[0],y=(s>>3)+d[1],dest=8*y+x;
                    if(!inside(x,y) || (seen&(1L<<dest))!=0) continue;
                    seen|=1L<<dest;
                    if(is(board[dest],light,'p')) continue;
                    int enemyRank=y+(light?1:-1);
                    if((inside(x-1,enemyRank) && is(board[enemyRank*8+x-1],!light,'p'))
                            || (inside(x+1,enemyRank) && is(board[enemyRank*8+x+1],!light,'p'))) continue;
                    if(is(board[dest],!light,'p')) return false;
                    queue[count++]=dest;
                }
            }
        }
        return true;
    }

    public boolean hasMatingMaterial(boolean side) {
        int ownKnights=0,ownBishops=0,bishopColors=0;boolean pawns=false,knights=false,enemyHelper=false;
        for(int sq=0;sq<64;sq++) {
            char p=board[sq];if(p=='.')continue;
            boolean own=Character.isUpperCase(p)==side;char type=Character.toLowerCase(p);
            if(own && (type=='p' || type=='r' || type=='q')) return true;
            if(!own && type!='k' && type!='q') enemyHelper=true;
            if(type=='p') pawns=true;
            if(type=='n') { knights=true;if(own)ownKnights++; }
            if(type=='b') { bishopColors|=1<<(((sq&7)+(sq>>3))&1);if(own)ownBishops++; }
        }
        if(ownKnights>0) return ownKnights+ownBishops>1 || enemyHelper;
        return ownBishops>0 && (bishopColors==3 || pawns || knights);
    }
    private ChessGame copyPosition() {
        ChessGame copy=new ChessGame();
        System.arraycopy(board,0,copy.board,0,64);
        copy.white=white;copy.rights=rights;copy.ep=ep;copy.half=half;copy.full=full;
        copy.repetitions.clear();
        return copy;
    }

    /** Some positions are already dead because every available continuation
     * forces a draw. Only adjudicate when the entire reachable graph is proven. */
    private boolean forcedDeadEnd(List<Move> first) {
        if(first.size()>2) return false;
        var pending=new ArrayDeque<ChessGame>();var seen=new HashSet<String>();
        pending.add(this);seen.add(key());
        while(!pending.isEmpty()) {
            ChessGame p=pending.remove();
            if(p!=this && (p.deadMaterial() || p.frozenPawnWall())) continue;
            List<Move> moves=p==this ? first : p.legalMoves();
            if(moves.isEmpty() && p.inCheck(p.white)) return false;
            if(moves.size()>3) return false;
            for(Move move:moves) {
                ChessGame next=p.copyPosition();next.apply(move);
                if(seen.add(next.key())) {
                    if(seen.size()>96) return false;
                    pending.add(next);
                }
            }
        }
        return true;
    }

    public Result result() {
        if(cached!=null) return cached;
        boolean check=inCheck(white);
        List<Move> moves=legalMoves();
        if(moves.isEmpty()) cached=check?Result.CHECKMATE:Result.STALEMATE;
        else if(deadMaterial() || frozenPawnWall() || forcedDeadEnd(moves)) cached=Result.DRAW_MATERIAL;
        else if(half>=150) cached=Result.DRAW_SEVENTY_FIVE;
        else if(repetitions.getOrDefault(key(),0)>=5) cached=Result.DRAW_REPETITION;
        else cached=check?Result.CHECK:Result.PLAYING;
        return cached;
    }
    public boolean finished() { return result()!=Result.PLAYING && result()!=Result.CHECK; }
    public boolean canClaimThreefold() { return repetitions.getOrDefault(key(),0)>=3; }
    public boolean canClaimFifty() { return half>=100; }
    public boolean canClaimAfter(Move move) {
        if(finished() || !legalMoves(move.from()).contains(move)) return false;
        ChessGame copy=copyPosition();
        copy.repetitions.putAll(repetitions);
        copy.apply(move);return copy.canClaimThreefold() || copy.canClaimFifty();
    }
    public NbtCompound toNbt() {
        NbtCompound n=new NbtCompound();
        n.putString("Board",new String(board));n.putBoolean("White",white);
        n.putBoolean("WK",(rights&1)!=0);n.putBoolean("WQ",(rights&2)!=0);
        n.putBoolean("BK",(rights&4)!=0);n.putBoolean("BQ",(rights&8)!=0);
        n.putInt("EP",ep);n.putInt("Half",half);n.putInt("Full",full);
        NbtCompound history=new NbtCompound();repetitions.forEach(history::putInt);n.put("History",history);
        return n;
    }
    public void readNbt(NbtCompound n) {
        String pos=n.getString("Board");
        if(pos.length()!=64 || pos.chars().anyMatch(c->".rnbqkpRNBQKP".indexOf(c)<0)
                || pos.chars().filter(c->c=='K').count()!=1 || pos.chars().filter(c->c=='k').count()!=1) { reset();return; }
        pos.getChars(0,64,board,0);white=n.getBoolean("White");
        rights=(n.getBoolean("WK")?1:0)|(n.getBoolean("WQ")?2:0)|(n.getBoolean("BK")?4:0)|(n.getBoolean("BQ")?8:0);
        ep=n.contains("EP")?n.getInt("EP"):-1;
        if(ep<0 || ep>=64 || ep/8!=(white?5:2) || board[ep]!='.') ep=-1;
        half=Math.max(0,n.getInt("Half"));full=Math.max(1,n.getInt("Full"));cached=null;
        repetitions.clear();NbtCompound history=n.getCompound("History");
        for(String k:history.getKeys()) if(history.contains(k,NbtElement.INT_TYPE)) repetitions.put(k,Math.max(1,history.getInt(k)));
        repetitions.putIfAbsent(key(),1);
    }
}
