package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.block.Visual;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisualServiceTest {
    @Test void orderedSnapshotUsesLatestMatchingRuleWithoutCrossDimensionState() {
        NbtCompound snapshot = new NbtCompound(); NbtList rules = new NbtList();
        rules.add(rule("ALL", 0, 0, 0, 0, 0, 0, "", "alt"));
        rules.add(rule("AREA", 2, 1, 2, 4, 3, 4, "00001", "base"));
        rules.add(rule("POINT", 3, 2, 3, 3, 2, 3, "00001", "alt"));
        snapshot.put("rules", rules);
        VisualService.RuleSet compiled = VisualService.compile(snapshot.copy()); // same NBT shape after PersistentState restart.
        assertEquals(Visual.BASE, compiled.effective(new BlockPos(2, 2, 2), "00001", Visual.BASE));
        assertEquals(Visual.ALT, compiled.effective(new BlockPos(3, 2, 3), "00001", Visual.BASE));
        assertEquals(Visual.ALT, compiled.effective(new BlockPos(2, 2, 2), "00002", Visual.BASE));
        assertEquals(Visual.ALT, compiled.effective(new BlockPos(99, 2, 99), "00001", Visual.BASE));
    }
    private static NbtCompound rule(String kind, int x1, int y1, int z1, int x2, int y2, int z2, String ids, String visual) {
        NbtCompound n = new NbtCompound(); n.putString("kind",kind); n.putInt("x1",x1); n.putInt("y1",y1); n.putInt("z1",z1); n.putInt("x2",x2); n.putInt("y2",y2); n.putInt("z2",z2); n.putString("ids",ids); n.putString("visual",visual); return n;
    }
}
