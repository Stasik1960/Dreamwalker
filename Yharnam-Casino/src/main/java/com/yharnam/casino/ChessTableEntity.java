package com.yharnam.casino;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class ChessTableEntity extends BlockEntity {
    public static final int MOVE_TICKS=10, RESET_TICKS=16;
    public record Motion(int from,int to,int capturedSquare,int rookFrom,int rookTo,int rackSlot,char moving,char captured,long start,double lift) {}
    public record ResetMotion(long start,String origins,String pieces,int lifted,double lift) {}
    private static final String EMPTY=".".repeat(64);
    private final ChessGame game=new ChessGame();
    private final VoxelShape[] shapes=new VoxelShape[6];
    private Direction shapeFacing;
    private ItemStack set=ItemStack.EMPTY;
    private String board=EMPTY,captures="",ending="";
    private UUID owner,drawOffer;
    private String ownerName="";
    private int selected=-1,promotionTo=-1,released=-1;
    private boolean claimByMove;
    private long selectedAt,releasedAt,targets;
    private double releasedLift, initialLift;
    private Motion move;
    private ResetMotion resetMove;

    public ChessTableEntity(BlockPos pos,BlockState state) { super(YharnamCasino.CHESS_ENTITY,pos,state); }
    public boolean installed() { return !set.isEmpty(); }
    public char piece(int square) { return square>=0 && square<64 ? board.charAt(square) : '.'; }
    public String captures() { return captures; }
    public int selection() { return selected; }
    public boolean pendingPromotion() { return promotionTo>=0; }
    public int releasedSquare() { return released; }
    public long releasedAt() { return releasedAt; }
    public long targets() { return targets; }
    public Motion motion() { return move; }
    public ResetMotion resetMotion() { return resetMove; }
    public double selectedLift(double time) {
        double t=MathHelper.clamp((time-selectedAt)/4,0,1);
        return initialLift+(ChessLayout.LIFT-initialLift)*t*t*(3-2*t)+.004*t*Math.sin((time-selectedAt)*.18);
    }
    public double loweringHeight(double time) {
        double t=MathHelper.clamp((time-releasedAt)/4,0,1);
        return releasedLift*(1-t*t*(3-2*t));
    }
    private void refreshBoard() {
        StringBuilder s=new StringBuilder(64);
        for(int i=0;i<64;i++) s.append(game.piece(i));
        board=s.toString();
    }
    private void changed(ServerWorld world) {
        Arrays.fill(shapes,null);markDirty();
        world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);
        if(selected>=0) world.scheduleBlockTick(pos,getCachedState().getBlock(),2);
    }
    private Vec3d local(Vec3d p) { return ChessLayout.local(pos,getCachedState().get(GamingFurnitureBlock.FACING),p); }
    private boolean inReach(ServerPlayerEntity p) {
        if(p==null || !p.isAlive() || p.isSpectator() || p.getWorld()!=world) return false;
        Vec3d at=local(p.getPos());
        double dx=Math.max(0,Math.abs(at.x)-1.44),dz=Math.max(0,Math.abs(at.z)-.97);
        double dy=at.y-MathHelper.clamp(at.y,0,ChessLayout.SURFACE);
        return dx*dx+dy*dy+dz*dz<25;
    }
    private void clearSelection(long now,boolean lower) {
        if(lower && selected>=0) { released=selected;releasedAt=now;releasedLift=selectedLift(now); }
        else if(!lower) released=-1;
        selected=-1;promotionTo=-1;owner=null;ownerName="";targets=0;claimByMove=false;
    }
    void watch(ServerWorld w) {
        if(selected<0) return;
        if(!inReach(w.getServer().getPlayerManager().getPlayer(owner))) {
            clearSelection(w.getTime(),true);changed(w);
        } else w.scheduleBlockTick(pos,getCachedState().getBlock(),2);
    }
    private static Text msg(String key,Object... args) { return Text.translatable("chess.yharnam_casino."+key,args); }
    private static Text side(boolean white) { return msg(white?"white":"black"); }
    private static Text pieceName(char piece) { return msg("piece."+Character.toLowerCase(piece)); }
    private void announce(ServerWorld w,Text text) {
        Vec3d center=ChessLayout.center(pos,getCachedState().get(GamingFurnitureBlock.FACING));
        for(var p:w.getPlayers(player->player.getPos().squaredDistanceTo(center)<100)) p.sendMessage(text,true);
    }
    private void tell(ServerPlayerEntity p,String key,Object... args) { p.sendMessage(msg(key,args),true); }
    private void clickSound(ServerWorld w,float pitch) {
        w.playSound(null,pos,SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON,SoundCategory.BLOCKS,.38f,pitch);
    }
    private boolean moving(long now) { return move!=null && now<move.start+MOVE_TICKS || resetMove!=null && now<resetMove.start+RESET_TICKS; }
    private boolean over() { return !ending.isEmpty() || game.finished(); }
    private Text status() {
        if(!ending.isEmpty()) return msg(ending);
        return switch(game.result()) {
            case CHECKMATE -> msg("mate",side(!game.whiteTurn()));
            case STALEMATE -> msg("stalemate");
            case DRAW_MATERIAL -> msg("dead");
            case DRAW_REPETITION -> msg("fivefold");
            case DRAW_SEVENTY_FIVE -> msg("seventyfive");
            case CHECK -> msg("check",side(game.whiteTurn()));
            default -> msg("turn",side(game.whiteTurn()));
        };
    }
    void use(ServerWorld w,ServerPlayerEntity p,Vec3d local) {
        if(!inReach(p)) return;
        var held=p.getMainHandStack();
        if(!installed()) {
            if(!held.isOf(YharnamCasino.CHESS_SET)) held=p.getOffHandStack();
            if(!held.isOf(YharnamCasino.CHESS_SET)) { tell(p,"need_set");return; }
            set=held.copyWithCount(1);if(!p.getAbilities().creativeMode) held.decrement(1);
            game.reset();refreshBoard();captures="";ending="";drawOffer=null;changed(w);
            announce(w,msg("installed",p.getName()));clickSound(w,1);return;
        }
        if(selected>=0 && !inReach(w.getServer().getPlayerManager().getPlayer(owner))) {
            clearSelection(w.getTime(),true);changed(w);
        }
        if(local.y<.70 || moving(w.getTime())) return;
        double x=local.x,z=local.z;
        if(ChessLayout.button(x,z,1)) {
            if(p.isSneaking() && held.isEmpty()) {
                var removed=set;set=ItemStack.EMPTY;board=EMPTY;captures="";ending="";move=null;resetMove=null;drawOffer=null;
                clearSelection(w.getTime(),false);p.getInventory().offerOrDrop(removed);changed(w);tell(p,"removed");
            } else reset(w,p);
            return;
        }
        if(over()) { p.sendMessage(status(),true);return; }
        if(selected>=0 && !p.getUuid().equals(owner)) { tell(p,"busy",ownerName);return; }
        if(ChessLayout.button(x,z,-1)) { drawControl(w,p);return; }
        if(pendingPromotion()) {
            int choice=ChessLayout.choice(x,z);
            if(choice>=0) {
                char type=ChessLayout.PROMOTIONS.charAt(choice);
                for(var m:game.legalMoves(selected)) if(m.to()==promotionTo && Character.toLowerCase(m.promotion())==type) { finish(w,p,m);return; }
            }
            if(ChessLayout.square(x,z)==selected) { clearSelection(w.getTime(),true);changed(w);tell(p,"cancel"); }
            else tell(p,"promote");
            return;
        }
        int square=ChessLayout.square(x,z);
        if(square<0) {
            if(selected>=0) { clearSelection(w.getTime(),true);changed(w);tell(p,"cancel"); }
            else p.sendMessage(status(),true);
            return;
        }
        if(selected==square) { clearSelection(w.getTime(),true);changed(w);tell(p,"cancel");return; }
        if(selected<0) {
            char piece=game.piece(square);
            if(piece=='.' || Character.isUpperCase(piece)!=game.whiteTurn()) { p.sendMessage(status(),true);return; }
            selected=square;owner=p.getUuid();ownerName=p.getGameProfile().getName();selectedAt=w.getTime();
            initialLift=released==square?loweringHeight(selectedAt):0;
            if(released==square) released=-1;
            targets=0;
            for(var m:game.legalMoves(square)) targets|=1L<<m.to();
            clickSound(w,1.4f);changed(w);
            announce(w,msg("selected",p.getName(),pieceName(piece),ChessLayout.name(square)));return;
        }
        for(var m:game.legalMoves(selected)) if(m.to()==square) {
            if(m.promotion()!=' ') { promotionTo=square;changed(w);tell(p,"promote"); }
            else finish(w,p,m);
            return;
        }
        tell(p,"illegal");
    }
    private void drawControl(ServerWorld w,ServerPlayerEntity p) {
        if(p.isSneaking()) {
            String reason=game.hasMatingMaterial(!game.whiteTurn()) ? (game.whiteTurn()?"white_resigned":"black_resigned") : "resign_draw";
            end(w,p,reason);return;
        }
        if(game.canClaimThreefold() || game.canClaimFifty()) { end(w,p,game.canClaimThreefold()?"threefold":"fifty");return; }
        if(selected>=0) { claimByMove=!claimByMove;tell(p,claimByMove?"claim_move":"claim_off");return; }
        if(game.fullmove()<2) { tell(p,"draw_too_early");return; }
        if(drawOffer!=null && !drawOffer.equals(p.getUuid())) { end(w,p,"agreement");return; }
        if(p.getUuid().equals(drawOffer)) { drawOffer=null;tell(p,"withdraw"); }
        else { drawOffer=p.getUuid();announce(w,msg("offer",p.getName())); }
        changed(w);
    }
    private void end(ServerWorld w,ServerPlayerEntity p,String reason) {
        ending=reason;clearSelection(w.getTime(),true);changed(w);announce(w,msg("ended",p.getName(),status()));
    }
    private void finish(ServerWorld w,ServerPlayerEntity p,ChessGame.Move m) {
        if(claimByMove) {
            if(game.canClaimAfter(m)) { end(w,p,"claimed");return; }
            claimByMove=false;tell(p,"no_claim");
        }
        int cap=ChessGame.captureSquare(m);
        char captured=game.piece(cap),moving=game.piece(m.from());double lift=selectedLift(w.getTime());
        if(!game.play(m)) { tell(p,"illegal");return; }
        int slot=-1;
        if(captured!='.') {
            slot=(int)captures.chars().filter(c->Character.isUpperCase(c)==Character.isUpperCase(captured)).count();
            captures+=captured;
        }
        move=new Motion(m.from(),m.to(),cap,m.castle()?ChessGame.rookFrom(m):-1,m.castle()?ChessGame.rookTo(m):-1,
            slot,moving,captured,w.getTime(),lift);
        resetMove=null;refreshBoard();clearSelection(w.getTime(),false);drawOffer=null;changed(w);clickSound(w,.95f);
        String notation=ChessLayout.name(m.from())+(captured!='.'?"×":"–")+ChessLayout.name(m.to());
        if(m.castle()) notation=m.to()>m.from()?"O-O":"O-O-O";
        if(m.promotion()!=' ') notation+="="+Character.toUpperCase(m.promotion());
        announce(w,msg("moved",p.getName(),pieceName(moving),notation,status()));
    }
    private void reset(ServerWorld w,ServerPlayerEntity p) {
        record Old(int origin,char type) {}
        var old=new ArrayList<Old>();
        for(int sq=0;sq<64;sq++) if(piece(sq)!='.') old.add(new Old(sq,piece(sq)));
        int white=0,black=0;
        for(char c:captures.toCharArray()) old.add(new Old(64+(Character.isUpperCase(c)?white++:black++),c));
        game.reset();refreshBoard();
        char[] origins=new char[64],types=new char[64];Arrays.fill(origins,(char)255);Arrays.fill(types,'.');
        // Keep pieces already at home, then match exact types; promoted pieces return as pawns.
        for(int pass=0;pass<3;pass++) for(int sq=0;sq<64;sq++) {
            char target=piece(sq);
            if(target=='.' || origins[sq]!=255) continue;
            for(int j=0;j<old.size();j++) {
                Old previous=old.get(j);
                if(Character.isUpperCase(previous.type)!=Character.isUpperCase(target)) continue;
                if(pass<2 && previous.type!=target || pass==0 && previous.origin!=sq) continue;
                origins[sq]=(char)previous.origin;types[sq]=previous.type;old.remove(j);break;
            }
        }
        resetMove=new ResetMotion(w.getTime(),new String(origins),new String(types),selected,selected>=0?selectedLift(w.getTime()):0);
        clearSelection(w.getTime(),false);ending="";captures="";move=null;drawOffer=null;changed(w);
        clickSound(w,.75f);announce(w,msg("reset",p.getName()));
    }
    void removed(ServerWorld w) {
        if(installed()) {
            w.spawnEntity(new ItemEntity(w,pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,set));set=ItemStack.EMPTY;
        }
    }
    VoxelShape shape(int part,Direction facing) {
        if(shapeFacing!=facing) { Arrays.fill(shapes,null);shapeFacing=facing; }
        if(shapes[part]!=null) return shapes[part];
        VoxelShape shape=ChessLayout.tabletop(part,facing);
        for(int s=0;s<64;s++) {
            char type=piece(s);if(type=='.') continue;
            double x=ChessLayout.x(s),z=ChessLayout.z(s),lift=s==selected?ChessLayout.LIFT:0;
            shape=VoxelShapes.union(shape,ChessLayout.box(part,facing,x-.068,z-.068,ChessLayout.SURFACE,x+.068,z+.068,ChessLayout.SURFACE+.04+lift),
                ChessLayout.box(part,facing,x-.046,z-.046,ChessLayout.SURFACE+.039,x+.046,z+.046,ChessLayout.SURFACE+ChessLayout.height(type)+lift));
        }
        for(int side:new int[]{-1,1}) {
            double x=side*ChessLayout.BUTTON_X,z=ChessLayout.BUTTON_Z;
            shape=VoxelShapes.union(shape,ChessLayout.box(part,facing,x-.11,z-.075,.722,x+.11,z+.075,.761));
        }
        if(pendingPromotion()) for(int i=0;i<4;i++) {
            double x=ChessLayout.choiceX(i);
            shape=VoxelShapes.union(shape,ChessLayout.box(part,facing,x-.064,.807,.722,x+.064,.937,.888));
        }
        return shapes[part]=shape;
    }
    @Override protected void writeNbt(NbtCompound n) {
        super.writeNbt(n);
        if(installed()) {
            n.put("Set",set.writeNbt(new NbtCompound()));
            n.put("Game",game.toNbt());n.putString("Taken",captures);
        }
        n.putString("Ending",ending);if(drawOffer!=null)n.putUuid("Offer",drawOffer);
    }
    private void writeView(NbtCompound n) {
        if(installed())n.put("Set",set.writeNbt(new NbtCompound()));
        n.putString("Pieces",board);n.putString("Taken",captures);n.putInt("Selected",selected);
        n.putInt("Promote",promotionTo);n.putLong("At",selectedAt);n.putDouble("InitialLift",initialLift);n.putLong("Targets",targets);
        n.putInt("Released",released);n.putLong("ReleaseAt",releasedAt);n.putDouble("ReleaseLift",releasedLift);
        if(move!=null) { n.putIntArray("Motion",new int[]{move.from,move.to,move.capturedSquare,move.rookFrom,move.rookTo,move.rackSlot,move.moving,move.captured});
            n.putLong("MoveAt",move.start);n.putDouble("Lift",move.lift); }
        if(resetMove!=null) { n.putString("Origins",resetMove.origins);n.putString("Previous",resetMove.pieces);n.putLong("ResetAt",resetMove.start);
            n.putInt("ResetLifted",resetMove.lifted);n.putDouble("ResetLift",resetMove.lift); }
    }
    @Override public void readNbt(NbtCompound n) {
        super.readNbt(n);Arrays.fill(shapes,null);set=ItemStack.fromNbt(n.getCompound("Set"));captures=n.getString("Taken");
        board=n.getString("Pieces");if(board.length()!=64)board=EMPTY;
        move=null;resetMove=null;owner=null;
        if(n.contains("Game") || !n.getBoolean("ClientView")) {
            if(installed()){game.readNbt(n.getCompound("Game"));refreshBoard();}
            ending=n.getString("Ending");drawOffer=n.containsUuid("Offer")?n.getUuid("Offer"):null;
            clearSelection(0,false);return;
        }
        selected=n.getInt("Selected");selectedAt=n.getLong("At");initialLift=n.getDouble("InitialLift");targets=n.getLong("Targets");promotionTo=n.getInt("Promote");
        released=n.getInt("Released");releasedAt=n.getLong("ReleaseAt");releasedLift=n.getDouble("ReleaseLift");
        int[] a=n.getIntArray("Motion");
        if(a.length==8)move=new Motion(a[0],a[1],a[2],a[3],a[4],a[5],(char)a[6],(char)a[7],n.getLong("MoveAt"),n.getDouble("Lift"));
        if(n.getString("Origins").length()==64 && n.getString("Previous").length()==64)
            resetMove=new ResetMotion(n.getLong("ResetAt"),n.getString("Origins"),n.getString("Previous"),n.getInt("ResetLifted"),n.getDouble("ResetLift"));
    }
    @Override public NbtCompound toInitialChunkDataNbt() { var n=new NbtCompound();writeView(n);n.putBoolean("ClientView",true);return n; }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
}
