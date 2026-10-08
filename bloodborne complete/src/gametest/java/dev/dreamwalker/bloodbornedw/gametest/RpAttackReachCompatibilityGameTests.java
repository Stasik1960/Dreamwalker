package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.RpCreativeAttackReach;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectSelection;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public final class RpAttackReachCompatibilityGameTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100, batchId = "rp_v10_reach_compat")
    public void farOriginUpperPartEditingRetainsExactRayAndEveryOrdinaryGuard(TestContext c) {
        var world = c.getWorld();
        BlockPos anchor = c.getAbsolutePos(new BlockPos(3, 3, 3));
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.getChunk(anchor.add(x * 16, 0, z * 16));
        var gate = (RpObjectEntity) Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp:main_gate")).create(world);
        gate.refreshPositionAndAngles(anchor.getX() + .5, 150, anchor.getZ() + .5, -180, 0);
        world.spawnEntity(gate);
        var player = new ServerPlayerEntity(world.getServer(), world, new GameProfile(UUID.randomUUID(), "RpPartReach"));
        var connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketCallbacks callbacks) {}
        };
        player.networkHandler = new ServerPlayNetworkHandler(world.getServer(), connection, player);
        player.changeGameMode(GameMode.CREATIVE);
        player.getAbilities().allowModifyWorld = true;
        world.spawnEntity(player);
        var part = gate.selectionBoxes().get(0);
        Vec3d aim = new Vec3d((part.minX + part.maxX) / 2, (part.minY + part.maxY) / 2, part.minZ + .25);
        Vec3d eye = new Vec3d(aim.x, aim.y, part.minZ - 2);
        player.setPosition(eye.x, eye.y - player.getStandingEyeHeight(), eye.z);
        look(player, aim);
        NbtCompound original = gate.writeNbt(new NbtCompound());
        c.assertTrue(player.squaredDistanceTo(gate) > 64, "actual upper source part is near the ray but beyond the library's origin threshold");
        var actualHit = RpObjectSelection.playerTarget(player, 6);
        c.assertTrue(actualHit != null && actualHit.getEntity() == gate && RpCreativeAttackReach.permitsPartEditing(player, gate, 64), "exact unobstructed nearby upper part qualifies without changing reach/origin");
        double actualDistanceSquared = player.getEyePos().squaredDistanceTo(actualHit.getPos());
        c.assertTrue(actualDistanceSquared > 1 && !RpCreativeAttackReach.permitsPartEditing(player, gate, actualDistanceSquared - .001), "reduced installed library cap rejects the actual selected hit rather than borrowing an unrelated box");
        c.assertTrue(RpCreativeAttackReach.permitsPartEditing(player, gate, actualDistanceSquared), "installed library's inclusive distance boundary is retained for the exact hit");
        var other = (RpObjectEntity) Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp:npc_window")).create(world);
        other.refreshPositionAndAngles(gate.getX() + 40, gate.getY(), gate.getZ(), 0, 0);
        world.spawnEntity(other);
        c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, other, 64), "forged/wrong target UUID cannot borrow another object's selected part");
        c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, EntityType.PIG.create(world), 64), "ordinary living target preserves original reach handling");
        var otherWorld = world.getServer().getWorld(World.NETHER);
        c.assertTrue(otherWorld != null, "guard control has a genuine separate server dimension");
        var otherDimension = Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp:main_gate")).create(otherWorld);
        c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, otherDimension, 64), "a target from another dimension cannot borrow a local selected UUID");
        for (Hand hand : Hand.values()) {
            player.setStackInHand(hand, CompositeArchitecture.BUILDER.getDefaultStack());
            c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, gate, 64), "building tool in either hand never authorizes ordinary attack: " + hand);
            player.setStackInHand(hand, ItemStack.EMPTY);
        }
        player.getAbilities().allowModifyWorld = false;
        c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, gate, 64), "native edit ability remains mandatory");
        player.getAbilities().allowModifyWorld = true;
        player.changeGameMode(GameMode.SURVIVAL);
        c.assertTrue(!player.isCreative() && !RpCreativeAttackReach.permitsPartEditing(player, gate, 64), "real Survival mode does not use Creative part override");
        player.changeGameMode(GameMode.CREATIVE);
        BlockPos occluder = BlockPos.ofFloored(eye.lerp(aim, .5));
        var originalBlock = world.getBlockState(occluder);
        world.setBlockState(occluder, Blocks.STONE.getDefaultState());
        c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, gate, 64), "real native ray occlusion prevents the optional override");
        world.setBlockState(occluder, originalBlock);
        player.setPosition(eye.x, eye.y - player.getStandingEyeHeight(), part.minZ - 7);
        look(player, aim);
        c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, gate, 1000000), "even an enlarged library cap cannot extend the source-part ray beyond six metres");
        player.setPosition(eye.x, eye.y - player.getStandingEyeHeight(), eye.z);
        look(player, aim);
        c.assertTrue(gate.writeNbt(new NbtCompound()).equals(original), "read-only policy checks retain exact typed RP UUID/payload/pose");
        c.assertTrue(RpCreativeAttackReach.permitsPartEditing(player, gate, 64), "same real nearby upper-part ray qualifies after all guard controls");
        player.attack(gate);
        c.assertTrue(gate.isRemoved(), "existing PlayerEntity.attack and RP damage guard perform the actual deletion");
        c.assertTrue(!RpCreativeAttackReach.permitsPartEditing(player, gate, 64), "removed UUID cannot qualify again");
        other.removeByBuilder(); player.discard(); c.complete();
    }

    private static void look(ServerPlayerEntity player, Vec3d target) {
        Vec3d d = target.subtract(player.getEyePos());
        player.setYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        player.setPitch((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }
}
