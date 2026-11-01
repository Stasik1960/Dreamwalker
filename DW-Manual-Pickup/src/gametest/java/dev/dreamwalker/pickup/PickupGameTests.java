package dev.dreamwalker.pickup;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PickupGameTests implements FabricGameTest {
    private PlayerEntity player(TestContext c) {
        PlayerEntity p = c.createMockSurvivalPlayer();
        Vec3d pos = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3, 2, 3)));
        p.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        return p;
    }

    private ItemEntity item(TestContext c, PlayerEntity p, double x, double y, double z, Item type, int count) {
        ItemEntity item = new ItemEntity(c.getWorld(), p.getX()+x, p.getY()+y, p.getZ()+z, new ItemStack(type, count));
        item.setNoGravity(true);
        item.setVelocity(Vec3d.ZERO);
        item.resetPickupDelay();
        c.getWorld().spawnEntity(item);
        return item;
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void automaticPickupBlockedEvenWhileSneaking(TestContext c) {
        PlayerEntity p = player(c);
        ItemEntity i = item(c,p,0,0,0,Items.DIAMOND,5);
        for (int tick=0; tick<40; tick++) {
            p.setSneaking(tick>10);
            p.tickMovement();
            i.onPlayerCollision(p);
        }
        c.assertTrue(!i.isRemoved() && i.getStack().getCount()==5 && p.getInventory().count(Items.DIAMOND)==0,
                "Standing and holding sneak never pick up items");
        i.discard(); p.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nearestStackOnlyAndSneakPreserved(TestContext c) {
        PlayerEntity p = player(c); p.setSneaking(true);
        ItemEntity far = item(c,p,.8,0,0,Items.EMERALD,7);
        ItemEntity near = item(c,p,.1,0,0,Items.DIAMOND,12);
        c.assertTrue(PickupService.pickUpNearest(p), "One request succeeds");
        c.assertTrue(near.isRemoved() && !far.isRemoved() && p.getInventory().count(Items.DIAMOND)==12
                && p.getInventory().count(Items.EMERALD)==0 && p.isSneaking(), "Only nearest whole stack; sneak is unchanged");
        far.onPlayerCollision(p);
        c.assertTrue(!far.isRemoved(), "Permission is not retained after the request");
        c.assertTrue(PickupService.pickUpNearest(p) && far.isRemoved(), "Second press picks up second stack");
        p.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void vanillaRadiusAndVerticalBoundary(TestContext c) {
        PlayerEntity p = player(c);
        ItemEntity far = item(c,p,1.6,0,0,Items.DIAMOND,1);
        ItemEntity low = item(c,p,0,-.8,0,Items.EMERALD,1);
        c.assertFalse(PickupService.pickUpNearest(p), "Outside horizontal/vertical vanilla box cannot be collected");
        ItemEntity edge = item(c,p,1.35,0,0,Items.GOLD_INGOT,2);
        c.assertTrue(PickupService.pickUpNearest(p) && edge.isRemoved(), "Intersecting item box at vanilla boundary is collected");
        c.assertTrue(!far.isRemoved() && !low.isRemoved(), "No radius extension");
        far.discard(); low.discard(); p.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void delayAndOwnerAreRespected(TestContext c) {
        PlayerEntity p = player(c);
        ItemEntity delayed = item(c,p,0,0,0,Items.DIAMOND,1); delayed.setPickupDelay(20);
        ItemEntity owned = item(c,p,.1,0,0,Items.EMERALD,1); owned.setOwner(UUID.randomUUID());
        ItemEntity allowed = item(c,p,.8,0,0,Items.GOLD_INGOT,3);
        c.assertTrue(PickupService.pickUpNearest(p) && allowed.isRemoved() && !owned.isRemoved() && !delayed.isRemoved(),
                "Skips unavailable drops without overriding vanilla owner/delay checks");
        owned.setOwner(p.getUuid());
        c.assertTrue(PickupService.pickUpNearest(p) && owned.isRemoved(), "Owner can collect own stack");
        delayed.resetPickupDelay();
        delayed.onPlayerCollision(p);
        c.assertTrue(!delayed.isRemoved(), "Delay expiration does not create a pending pickup");
        delayed.discard(); p.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullInventoryDoesNotLoseItems(TestContext c) {
        PlayerEntity p = player(c);
        for (int s=0;s<p.getInventory().main.size();s++) p.getInventory().main.set(s,new ItemStack(Items.STONE,64));
        ItemEntity i = item(c,p,0,0,0,Items.DIAMOND,16);
        c.assertFalse(PickupService.pickUpNearest(p), "Full inventory rejects pickup");
        c.assertTrue(!i.isRemoved() && i.getStack().getCount()==16, "No items lost with full inventory");
        i.discard(); p.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void partialStackConsumesOnlyOneDrop(TestContext c) {
        PlayerEntity p = player(c);
        for (int s=0;s<p.getInventory().main.size();s++) p.getInventory().main.set(s,new ItemStack(Items.STONE,64));
        p.getInventory().main.set(0,new ItemStack(Items.DIAMOND,60));
        ItemEntity near = item(c,p,.1,0,0,Items.DIAMOND,12);
        ItemEntity far = item(c,p,.8,0,0,Items.DIAMOND,6);
        c.assertTrue(PickupService.pickUpNearest(p) && near.getStack().getCount()==8 && far.getStack().getCount()==6,
                "Only four items fit; remainder and second drop stay on floor");
        near.onPlayerCollision(p);
        c.assertTrue(p.getInventory().count(Items.DIAMOND)==64 && near.getStack().getCount()==8, "No duplicates");
        near.discard(); far.discard(); p.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void emptyPressIsNotQueued(TestContext c) {
        PlayerEntity p = player(c);
        c.assertFalse(PickupService.pickUpNearest(p), "Empty press has no effect");
        ItemEntity later = item(c,p,0,0,0,Items.DIAMOND,1);
        later.onPlayerCollision(p);
        c.assertTrue(!later.isRemoved(), "An item arriving after the press is not collected");
        later.discard(); p.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void playersHaveSeparatePermissions(TestContext c) {
        PlayerEntity a = player(c), b = player(c);
        ItemEntity i = item(c,a,0,0,0,Items.DIAMOND,1);
        c.assertTrue(PickupService.pickUpNearest(a) && a.getInventory().count(Items.DIAMOND)==1
                && b.getInventory().count(Items.DIAMOND)==0 && !PickupService.isPermitted(i,b), "No cross-player permission");
        a.discard(); b.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void packetBurstIsLimitedPerPlayer(TestContext c) {
        PlayerEntity a=player(c), b=player(c);
        for(int n=0;n<4;n++) c.assertTrue(PickupService.acceptRequest(a), "Fast taps accepted");
        c.assertFalse(PickupService.acceptRequest(a), "Fifth packet in same tick rejected");
        c.assertTrue(PickupService.acceptRequest(b), "Other player has own limit");
        c.runAtTick(2,()->{
            c.assertTrue(PickupService.acceptRequest(a), "Next tick renews allowance");
            a.discard(); b.discard(); c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void spectatorCannotCollect(TestContext c) {
        // Vanilla's createMockCreativeServerPlayerInWorld hardcodes isSpectator=false,
        // even after changeGameMode. Use an explicit spectator fixture instead.
        PlayerEntity p=new PlayerEntity(c.getWorld(),c.getAbsolutePos(new BlockPos(3,2,3)),0,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"pickup-spectator")) {
            @Override public boolean isSpectator() { return true; }
            @Override public boolean isCreative() { return false; }
        };
        ItemEntity i=item(c,p,0,0,0,Items.DIAMOND,1);
        c.assertFalse(PickupService.pickUpNearest(p), "Spectator cannot request pickup");
        c.assertTrue(!i.isRemoved(), "Spectator leaves the item on floor");
        i.discard(); p.discard(); c.complete();
    }
}
