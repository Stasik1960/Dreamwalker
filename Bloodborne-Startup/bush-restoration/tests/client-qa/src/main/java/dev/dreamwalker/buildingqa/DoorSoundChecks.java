package dev.dreamwalker.buildingqa;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
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
    private CompletableFuture<Void> interaction;
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
            use(game, true);
            stage++;
            wait = 0;
            return null;
        }
        if (stage == 1) {
            if (!confirmed()) return await();
            if (events.isEmpty() || wait < (fixture == 2 ? 75 : 6)) return await();
            require(events.equals(EXPECTED.subList(0, 1)), "expected one open packet, got " + events);
            use(game, false);
            stage++;
            wait = 0;
            return null;
        }
        if (stage == 2) {
            if (!confirmed() || events.size() < 2 || wait < 10) return await();
            require(events.equals(EXPECTED), "expected one open and one close packet, got " + events);
            cleanup();
            if (fixture == 0) {
                use(game, true);
                stage++;
                return null;
            }
        } else if (!confirmed()) return await();
        if (++fixture == 3) return "PASS carved, native and RP door interactions each sent one open and one close sound to player";
        prepared = null;
        interaction = null;
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

    private void use(MinecraftClient game, boolean open) {
        var server = game.getServer();
        var playerId = game.player.getUuid();
        BlockPos root = position();
        interaction = server.submit(() -> click(server.getPlayerManager().getPlayer(playerId), root, open));
    }

    private boolean confirmed() {
        if (!interaction.isDone()) return false;
        interaction.join();
        return true;
    }

    private static void click(ServerPlayerEntity player, BlockPos root, boolean open) {
        var world = player.getServerWorld();
        if (root.equals(RP)) {
            Entity entity = world.getOtherEntities(player, new net.minecraft.util.math.Box(root).expand(3),
                    candidate -> Registries.ENTITY_TYPE.getId(candidate.getType()).equals(new Identifier("bloodborne_rp", "door_1")))
                    .stream().findFirst().orElseThrow(() -> new AssertionError("RP door is outside player reach"));
            lookAt(player, entity.getPos().add(0, 1.25, 0));
            var result = player.interact(entity, Hand.MAIN_HAND);
            require(result.isAccepted(), "player failed to " + (open ? "open" : "close") + " RP door: " + result);
            return;
        }
        if (!open) {
            Vec3d nearHinge = Vec3d.of(root).add(2.75, 0, 1.5);
            player.teleport(world, nearHinge.x, nearHinge.y, nearHinge.z, 0, 0);
        }
        var item = item(root);
        var attempts = new ArrayList<String>();
        for (BlockPos cell : BlockPos.iterate(root.add(-1, 0, -1), root.add(2, 3, 1))) {
            var local = world.getBlockState(cell);
            if (!local.getBlock().getPickStack(world, cell, local).isOf(item)) continue;
            for (var box : local.getOutlineShape(world, cell, net.minecraft.block.ShapeContext.of(player)).getBoundingBoxes()) {
                Vec3d target = box.getCenter().add(cell.getX(), cell.getY(), cell.getZ());
                lookAt(player, target);
                var hit = (BlockHitResult) player.raycast(5, 1, false);
                if (hit.getType() != HitResult.Type.BLOCK) continue;
                var state = world.getBlockState(hit.getBlockPos());
                if (!state.getBlock().getPickStack(world, hit.getBlockPos(), state).isOf(item)) continue;
                var result = player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, Hand.MAIN_HAND, hit);
                var rootState = world.getBlockState(root);
                if (result.isAccepted() && Boolean.valueOf(open).equals(property(rootState))) return;
                attempts.add(hit.getBlockPos().toShortString() + " " + result + " state=" + rootState);
            }
        }
        throw new AssertionError("player could not " + (open ? "open " : "close ") + item + " at " + root + ": " + attempts
                + ", eye=" + player.getEyePos() + ", lastTransaction=" + lastTransaction());
    }

    private static Boolean property(net.minecraft.block.BlockState state) {
        var open = state.getBlock().getStateManager().getProperty(Properties.OPEN.getName());
        return open != null && state.get(open) instanceof Boolean value ? value : null;
    }

    private static String lastTransaction() {
        try {
            Class<?> runtime = Class.forName("dev.dreamwalker.bloodbornedw.composite.CompositeRuntime");
            return String.valueOf(runtime.getMethod("lastResult").invoke(null));
        } catch (ReflectiveOperationException e) {
            return e.toString();
        }
    }

    private static void lookAt(ServerPlayerEntity player, Vec3d target) {
        Vec3d aim = target.subtract(player.getEyePos());
        float yaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z)));
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.prevYaw = yaw;
        player.prevPitch = pitch;
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
        for (int z = 0; z <= 5; z++) world.setBlockState(pos.north(z).down(), Blocks.STONE.getDefaultState());
        player.changeGameMode(GameMode.CREATIVE);
        player.setSneaking(false);
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
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
