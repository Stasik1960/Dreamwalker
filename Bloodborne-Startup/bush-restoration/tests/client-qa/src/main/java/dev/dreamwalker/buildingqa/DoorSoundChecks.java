package dev.dreamwalker.buildingqa;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

/** Observes sound packets sent to the player who used each door, even with audio muted. */
public final class DoorSoundChecks {
    private static final List<String> EXPECTED = List.of("block.wooden_door.open", "block.wooden_door.close");
    private static final String NAMESPACE = "bloodborne_dw";
    private static final BlockPos CARVED = new BlockPos(425, 276, -847);
    private static final BlockPos NATIVE = CARVED.west(6);
    private static final BlockPos RP = CARVED.west(12);
    private static DoorSoundChecks capturing;

    private final List<String> events = new ArrayList<>();
    private CompletableFuture<Void> prepared;
    private int fixture, wait;
    private int stage;

    public String tick(MinecraftClient game) {
        if (prepared == null) {
            var server = game.getServer();
            var playerId = game.player.getUuid();
            BlockPos pos = position();
            prepared = server.submit(() -> prepare(server.getPlayerManager().getPlayer(playerId), pos));
            return null;
        }
        if (!prepared.isDone()) return null;
        prepared.join();
        wait++;
        if (stage == 0) {
            if (wait < 10 || !inPosition(game)) return await();
            capturing = this;
            if (!use(game)) return await();
            stage++;
            wait = 0;
            return null;
        }
        if (stage == 1) {
            if (events.isEmpty() || wait < (fixture == 2 ? 75 : 6)) return await();
            require(events.equals(EXPECTED.subList(0, 1)), "expected one open packet, got " + events);
            if (!use(game)) return await();
            stage++;
            wait = 0;
            return null;
        }
        if (events.size() < 2 || wait < 10) return await();
        require(events.equals(EXPECTED), "expected one open and one close packet, got " + events);
        cleanup();
        if (fixture == 0 && !use(game)) return await();
        if (++fixture == 3) return "PASS carved, native and RP door interactions each sent one open and one close sound to player";
        prepared = null;
        events.clear();
        stage = 0;
        wait = 0;
        return null;
    }

    private String await() {
        require(wait < 160, "door fixture " + fixture + " did not reach stage " + stage + ", sounds=" + events);
        return null;
    }

    private boolean inPosition(MinecraftClient game) {
        BlockPos pos = position();
        return game.currentScreen == null && game.player.getMainHandStack().isEmpty()
                && game.world.isChunkLoaded(pos) && Math.abs(game.player.getX() - (pos.getX() + .5)) < .1;
    }

    private boolean use(MinecraftClient game) {
        BlockPos root = position();
        if (fixture == 2) {
            Entity entity = game.world.getOtherEntities(game.player, new net.minecraft.util.math.Box(root).expand(3),
                    candidate -> Registries.ENTITY_TYPE.getId(candidate.getType()).equals(new Identifier("bloodborne_rp", "door_1")))
                    .stream().findFirst().orElse(null);
            if (entity == null) return false;
            game.interactionManager.interactEntity(game.player, entity, Hand.MAIN_HAND);
            return true;
        }
        for (double y : new double[]{1.5, .5, 2.5}) {
            for (double x : new double[]{.5, -.25, 1.25, -.75, 1.75, -1.25, 2.25}) {
                Vec3d target = new Vec3d(root.getX() + x, root.getY() + y, root.getZ() + 1.75);
                var ray = new RaycastContext(game.player.getEyePos(), target, RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE, game.player);
                BlockHitResult hit = game.world.raycast(ray);
                if (hit.getType() != HitResult.Type.BLOCK) continue;
                var state = game.world.getBlockState(hit.getBlockPos());
                if (!state.getBlock().getPickStack(game.world, hit.getBlockPos(), state).isOf(item(root))) continue;
                lookAt(game, target);
                game.interactionManager.interactBlock(game.player, Hand.MAIN_HAND, hit);
                return true;
            }
        }
        return false;
    }

    private static void lookAt(MinecraftClient game, Vec3d target) {
        Vec3d aim = target.subtract(game.player.getEyePos());
        float yaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z)));
        game.player.setYaw(yaw);
        game.player.setPitch(pitch);
        game.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, game.player.isOnGround()));
    }

    public void cleanup() {
        if (capturing == this) capturing = null;
    }

    public static void record(PlaySoundS2CPacket packet) {
        var check = capturing;
        if (check == null || packet.getCategory() != SoundCategory.BLOCKS) return;
        var origin = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
        if (origin.squaredDistanceTo(Vec3d.ofCenter(check.position())) > 9) return;
        var sound = packet.getSound().value().getId();
        if (sound.getNamespace().equals("minecraft") && sound.getPath().contains("door")) {
            check.events.add(sound.getPath());
        }
    }

    private BlockPos position() {
        return switch (fixture) { case 0 -> CARVED; case 1 -> NATIVE; default -> RP; };
    }

    private static net.minecraft.item.Item item(BlockPos pos) {
        return Registries.ITEM.get(new Identifier(NAMESPACE, pos.equals(CARVED) ? "prototype_double_door" : "door_90960_plain"));
    }

    private static void prepare(ServerPlayerEntity player, BlockPos pos) {
        var world = player.getServerWorld();
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-4, 4}) world.getChunk(pos.add(x, 0, z));
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        world.setBlockState(pos.north(4).down(), Blocks.STONE.getDefaultState());
        player.changeGameMode(GameMode.CREATIVE);
        player.teleport(world, pos.getX() + .5, pos.getY(), pos.getZ() - 3.5, 0, 0);
        if (pos.equals(RP)) {
            var type = Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp", "door_1"));
            Entity door = type.create(world);
            require(door != null, "RP door fixture type unavailable");
            door.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            require(world.spawnEntity(door), "could not spawn RP door fixture");
        } else {
            var stack = new ItemStack(item(pos));
            player.setStackInHand(Hand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3d.ofBottomCenter(pos), Direction.UP, pos.down(), false);
            require(stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(),
                    "could not place door fixture " + stack);
        }
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
