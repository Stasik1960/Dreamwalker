package com.yharnam.casino;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.yharnamdice.DiceMod;
import dev.yharnamdice.block.DiceTableEntity;
import dev.yharnamdice.item.DicePouchItem;
import dev.yharnamdice.item.RuleDiskItem;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class DiceIntegrationTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void loadsStandalonePouchAndTable(TestContext t) throws CommandSyntaxException {
        // These literal saves use the IDs and NBT layout written by standalone 0.1.3.
        var pouch = ItemStack.fromNbt(StringNbtReader.parse("""
            {id:"yharnam_dice:dice_pouch",Count:1b,tag:{PouchId:[I;0,42,0,9],Dice:[
              {Slot:0b,id:"yharnam_dice:chapel_ivory",Count:1b},
              {Slot:19b,id:"yharnam_dice:gutter_scrap_die",Count:1b}]}}
            """));
        t.assertTrue(pouch.isOf(DiceMod.POUCH), "Legacy pouch resolves to the registered item");
        t.assertTrue(new UUID(42, 9).equals(DicePouchItem.key(pouch)), "Pouch identity survives");
        var slots = DicePouchItem.contents(pouch);
        t.assertTrue(DiceMod.skin(slots[0]) == 0 && DiceMod.skin(slots[19]) == 17 && slots[1].isEmpty(),
            "Legacy die IDs and occupied slots survive");
        var pos = t.getAbsolutePos(new BlockPos(1, 1, 1));
        var block = Registries.BLOCK.get(new Identifier("yharnam_dice", "dice_table"));
        t.getWorld().setBlockState(pos, block.getDefaultState());
        var table = (DiceTableEntity) t.getWorld().getBlockEntity(pos);
        var save = StringNbtReader.parse("""
            {RuleDisk:{id:"yharnam_dice:rule_disk",Count:1b,tag:{
              RuleJSON:'{"id":"old_rules","name":"Legacy","game":"FARKLE","target":1750}'}}}
            """);
        table.readNbt(save);
        t.assertTrue(table.disk().isOf(DiceMod.DISK) && RuleDiskItem.rules(table.disk()).target == 1750,
            "The old table reads its custom disk");
        t.assertTrue(save.getCompound("RuleDisk").equals(table.createNbt().getCompound("RuleDisk")),
            "Saving the table preserves the complete disk");
        t.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diceRecipesAndRuleDisksLoadInCasino(TestContext t) {
        var recipes = t.getWorld().getRecipeManager();
        for (String path : List.of("dice_table", "rule_disk", "dice_pouch", "chapel_ivory")) {
            t.assertTrue(recipes.get(new Identifier("yharnam_dice", path)).isPresent(), "Missing recipe " + path);
        }
        for (String id : List.of("kcd_farkle", "witcher_poker", "twenty_one")) {
            t.assertTrue(id.equals(RuleDiskItem.rules(DiceMod.DISK.builtin(id)).id), "Missing preset " + id);
        }
        t.complete();
    }
}
