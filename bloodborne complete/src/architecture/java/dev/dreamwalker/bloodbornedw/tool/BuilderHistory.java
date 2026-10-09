package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Session-only history. Never restores whole entity NBT, policies, commands or live open states. */
final class BuilderHistory {
    static final int LIMIT = 20;
    record State(BlockState block, double offset, float yaw, boolean dogs) {}
    record Edit(MechanismLinks.TargetRef target, State before, State after, String label) {}
    private final ArrayDeque<Edit> edits = new ArrayDeque<>();
    static State capture(ServerPlayerEntity p, MechanismLinks.TargetRef t) {
        if (t.kind() == MechanismLinks.Kind.RP) {
            if (!(p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)) return null;
            return new State(null, rp.verticalOffset(), rp.getYaw(), rp.dogsVisible());
        }
        BlockPos root = BlockPos.fromLong(t.root());
        if (!p.getWorld().isChunkLoaded(root)) return null;
        var owner = VerticalMount.owner(p.getWorld(), root);
        if (owner == null || !owner.instanceId().equals(t.instance())) return null;
        BlockState state=p.getWorld().getBlockState(root);if(state.contains(CompositeRootBlock.OPEN))state=state.with(CompositeRootBlock.OPEN,false);return new State(state, VerticalMount.offset(p.getWorld(), root), 0, false);
    }
    void record(ServerPlayerEntity p, MechanismLinks.TargetRef t, State before, String label) {
        State after = capture(p, t);
        if (before == null || after == null || before.equals(after)) return;
        if (edits.size() == LIMIT) edits.removeFirst();
        edits.addLast(new Edit(t, before, after, label));
    }
    static boolean restore(ServerPlayerEntity p,MechanismLinks.TargetRef t,State wanted) {
        State current=capture(p,t);if(current==null||wanted==null)return false;
        if(t.kind()==MechanismLinks.Kind.RP){
            RpObjectEntity rp=(RpObjectEntity)p.getServerWorld().getEntity(t.instance());
            if(current.offset()!=wanted.offset()&&!rp.setBuilderGeometry(wanted.offset(),null,p))return false;
            if(current.yaw()!=wanted.yaw()&&!rp.rotateByBuilder(p,wanted.yaw())){rp.setVerticalOffset(current.offset(),p);return false;}
            if(current.dogs()!=wanted.dogs()&&!rp.setDogsVisible(wanted.dogs())){rp.rotateByBuilder(p,current.yaw());rp.setVerticalOffset(current.offset(),p);return false;}
            return true;
        }
        BlockPos root=BlockPos.fromLong(t.root());
        if(!(p.getWorld().getBlockEntity(root) instanceof CompositeBlockEntity own))return false;
        BlockState live=p.getWorld().getBlockState(root), next=wanted.block();
        if(next.contains(CompositeRootBlock.OPEN)&&live.contains(CompositeRootBlock.OPEN))next=next.with(CompositeRootBlock.OPEN,live.get(CompositeRootBlock.OPEN));
        return VerticalMount.setGeometry(p.getServerWorld(),root,next,wanted.offset(),p);
    }
    int size() { return edits.size(); }
    Edit last() { return edits.peekLast(); }
    String undo(ServerPlayerEntity p) {
        Edit edit = last();
        if (edit == null) return "Нет поддерживаемого изменения для отмены.";
        State current = capture(p, edit.target());
        if (!Objects.equals(current, edit.after())) return "Отмена отклонена: экземпляр недоступен или его параметры уже изменились. Чужая работа сохранена.";
        boolean accepted = restore(p,edit.target(),edit.before());
        if (!accepted) return "Отмена отклонена: права, живое препятствие или границы мира.";
        edits.removeLast();
        return "Отменено: " + edit.label() + ". Осталось: " + edits.size();
    }
}
