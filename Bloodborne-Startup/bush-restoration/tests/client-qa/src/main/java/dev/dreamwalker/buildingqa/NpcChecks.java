package dev.dreamwalker.buildingqa;

import dev.dreamwalker.buildingqa.mixin.MouseInputAccessor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

/** Real client mouse clicks, with a disposable arena and server-side observations. */
final class NpcChecks {
    private static final Identifier TOOL = new Identifier("bloodborne_dw", "bb_npc_tool");
    private static final Identifier MOB = new Identifier("bloodborne_rp", "huntsman_a");
    private static final BlockPos START = new BlockPos(439, 293, -832);
    private static final BlockPos A = START.east(4);
    private static final BlockPos B = START.east(8).north(4);
    private static final BlockPos C = START.east(12);
    private static final BlockPos MANUAL = B.north(3);
    private static final BlockPos CAGE = START.south(5);
    private static final BlockPos ACROSS_WALL = CAGE.east(7);
    private static final String MODE = "DwDebugToolMode";
    private static final String STATE = "DreamWalkerPatrol";

    private record Probe(NbtCompound patrol, Vec3d position, int ticks, int mode, boolean ambient,
                         boolean deathSound) {
        boolean idle() { return patrol.getBoolean("IdleSounds"); }
        int index() { return patrol.getInt("Index"); }
        int points() { return patrol.getLongArray("Points").length; }
        boolean reverse() { return patrol.getBoolean("Reverse"); }
        boolean manual() { return patrol.contains("Manual"); }
        int waitTicks() { return patrol.getInt("ReturnWait"); }
        boolean near(BlockPos ground) {
            return position.squaredDistanceTo(Vec3d.ofBottomCenter(ground.up())) < 0.22;
        }
    }

    private interface Step { boolean tick(MinecraftClient game) throws Exception; }
    private record Check(String label, Step action, int timeout) {}

    private final List<Check> checks = new ArrayList<>();
    private CompletableFuture<Void> setup;
    private CompletableFuture<Probe> reading;
    private Probe last;
    private int stage;
    private int stageTicks;
    private int waitBegan;
    private int stalledBegan;
    private UUID actorId;
    private int actorNetworkId;

    NpcChecks() {
        waitFor("idle disabled on a newly spawned actor, death sound available", p -> !p.idle() && !p.ambient() && p.deathSound());
        click("cycle to idle sounds", true, null, 2);
        waitFor("NPC tool RMB selected idle sounds", p -> p.mode() == 3);
        click("enable idle sounds", false, null, 3);
        waitFor("only this NPC restored idle sound", p -> p.idle() && p.ambient() && p.deathSound());
        verifySavedState();
        click("disable idle sounds again", false, null, 3);
        waitFor("NPC idle sound toggled off", p -> !p.idle() && !p.ambient());
        click("select NPC for patrol", false, null, 4);
        click("add first point", false, A, 4);
        waitFor("walk to the first point from spawn", p -> p.points() == 1 && p.near(A));
        click("add second point", false, B, 4);
        waitFor("walk to new second point", p -> p.points() == 2 && p.near(B));
        waitFor("loop advances from second point to first", p -> p.index() == 0);
        waitFor("loop back to the first point", p -> p.near(A));
        click("add third point", false, C, 4);
        waitFor("walk directly to new third point", p -> p.points() == 3 && p.near(C));
        waitFor("loop from third to first with no second-point leg", p -> p.index() == 0);
        click("repeat click toggles reverse", false, null, 4);
        waitFor("reverse patrol selected", Probe::reverse);
        waitFor("reach third point in reverse configuration", p -> p.near(C));
        waitFor("reverse from third to second", p -> p.index() == 1);
        click("select actor in existing special action", false, null, 0);
        click("manually send patrolling actor elsewhere", false, MANUAL, 0);
        waitFor("arrive at manual point and begin waiting", p -> {
            if (!p.manual() || !p.near(MANUAL) || p.waitTicks() <= 0) return false;
            waitBegan = p.ticks() - (200 - p.waitTicks());
            return true;
        });
        waitFor("wait near manual point for ten seconds", p -> {
            if (!p.manual() && p.ticks() - waitBegan < 197) throw new AssertionError("resumed patrol early");
            return p.manual() && p.waitTicks() < 100 && p.near(MANUAL);
        });
        waitFor("resume with nearest waypoint B after full wait", p -> !p.manual() && p.ticks() - waitBegan >= 199 && p.index() == 1);
        waitFor("return to nearest waypoint B", p -> p.near(B));
        click("remove patrol", false, null, 5);
        waitFor("patrol points and current destination removed", p -> p.points() == 0 && !p.manual());
        cage();
        click("select caged NPC for manual movement", false, null, 0);
        click("order impossible manual movement through cage", false, ACROSS_WALL, 0);
        waitFor("manual stuck timer started", p -> {
            if (!p.manual()) return false;
            stalledBegan = p.ticks();
            return true;
        });
        waitFor("manual order teleports only after stall", p -> {
            if (!p.near(ACROSS_WALL)) return false;
            require(p.ticks() - stalledBegan >= 190, "manual fallback fired before 10 seconds");
            return !p.manual();
        });
        cage();
        click("select caged NPC for patrol", false, null, 4);
        click("add patrol point outside cage", false, ACROSS_WALL, 4);
        waitFor("patrol stuck timer started", p -> {
            if (p.points() != 1) return false;
            stalledBegan = p.ticks();
            return true;
        });
        waitFor("patrol fallback teleports to the point after stall", p -> {
            if (!p.near(ACROSS_WALL)) return false;
            require(p.ticks() - stalledBegan >= 190, "patrol fallback fired before 10 seconds");
            return true;
        });
        verifySavedState();
        click("remove stalled-test route", false, null, 5);
        waitFor("route removed and NPC stays at the endpoint", p -> p.points() == 0 && p.near(ACROSS_WALL));
    }

    String tick(MinecraftClient game) throws Exception {
        if (setup == null) {
            setup = game.getServer().submit(() -> prepare(player(game)));
            return null;
        }
        if (!setup.isDone()) return null;
        setup.join();
        if (stage == checks.size()) {
            var replica = game.world.getEntityById(actorNetworkId);
            if (replica == null || replica.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(ACROSS_WALL.up())) > .3) {
                require(++stageTicks < 200, "client did not receive final NPC position");
                return null;
            }
            return "PASS NPC tool on client: per-NPC idle, loop and reverse, manual pause and nearest return, "
                    + "clear patrol, NBT persistence, and both blocked-waypoint fallbacks";
        }
        Check check = checks.get(stage);
        if (++stageTicks > check.timeout()) {
            throw new AssertionError("NPC check timed out: " + check.label() + ", target=" + game.crosshairTarget + ", server=" + last);
        }
        if (check.action().tick(game)) {
            System.out.println("DW NPC QA " + check.label() + " | " + last);
            stage++;
            stageTicks = 0;
            reading = null;
        }
        return null;
    }

    private void waitFor(String label, Predicate<Probe> predicate) {
        checks.add(new Check(label, game -> {
            if (reading == null) {
                reading = game.getServer().submit(() -> observe(game));
                return false;
            }
            if (!reading.isDone()) return false;
            last = reading.join();
            reading = null;
            return predicate.test(last);
        }, 650));
    }

    private void verifySavedState() {
        checks.add(new Check("NPC state survives save/read NBT", new Step() {
            private CompletableFuture<Void> result;

            @Override public boolean tick(MinecraftClient game) {
                if (result == null) {
                    result = game.getServer().submit(() -> {
                        var world = player(game).getServerWorld();
                        var source = world.getEntity(actorId);
                        var saved = source.writeNbt(new NbtCompound());
                        var copy = Registries.ENTITY_TYPE.get(MOB).create(world);
                        require(copy != null, "could not create NPC for NBT reload");
                        copy.readNbt(saved);
                        require(copy.writeNbt(new NbtCompound()).getCompound(STATE).equals(saved.getCompound(STATE)),
                                "NPC state differs after save/read");
                    });
                    return false;
                }
                if (!result.isDone()) return false;
                result.join();
                return true;
            }
        }, 100));
    }

    private void click(String label, boolean right, BlockPos ground, int mode) {
        checks.add(new Check(label, new Step() {
            private CompletableFuture<Void> aimed;

            @Override public boolean tick(MinecraftClient game) {
                if (aimed == null) {
                    aimed = game.getServer().submit(() -> aim(player(game), ground, mode));
                    return false;
                }
                if (!aimed.isDone() || stageTicks < 8 || game.currentScreen != null) return false;
                aimed.join();
                var stack = game.player.getMainHandStack();
                if (!stack.isOf(Registries.ITEM.get(TOOL)) || stack.getOrCreateNbt().getInt(MODE) != mode) return false;
                if (ground == null) {
                    var replica = game.world.getEntityById(actorNetworkId);
                    if (replica == null) return false;
                    Vec3d delta = replica.getBoundingBox().getCenter().subtract(game.player.getEyePos());
                    game.player.setYaw(yaw(delta));
                    game.player.setPitch(pitch(delta));
                }
                game.gameRenderer.updateTargetedEntity(1);
                boolean valid = ground == null
                        ? game.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity().getId() == actorNetworkId
                        : game.crosshairTarget instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                            && blockHit.getBlockPos().equals(ground);
                if (!valid) return false;
                var mouse = (MouseInputAccessor) game.mouse;
                long window = game.getWindow().getHandle();
                int button = right ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_LEFT;
                mouse.dw$onMouseButton(window, button, GLFW.GLFW_PRESS, 0);
                mouse.dw$onMouseButton(window, button, GLFW.GLFW_RELEASE, 0);
                return true;
            }
        }, 210));
    }

    private void cage() {
        checks.add(new Check("move fixture inside a closed cage with a safe destination", new Step() {
            private CompletableFuture<Void> moved;

            @Override public boolean tick(MinecraftClient game) {
                if (moved == null) {
                    moved = game.getServer().submit(() -> {
                        var world = player(game).getServerWorld();
                        var actor = world.getEntity(actorId);
                        Vec3d center = Vec3d.ofBottomCenter(CAGE.up());
                        actor.requestTeleport(center.x, center.y, center.z);
                        for (var direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                            for (int y = 1; y <= 3; y++) {
                                world.setBlockState(CAGE.offset(direction).up(y), Blocks.GLASS.getDefaultState());
                            }
                        }
                    });
                    return false;
                }
                if (!moved.isDone()) return false;
                moved.join();
                return true;
            }
        }, 100));
    }

    private ServerPlayerEntity player(MinecraftClient game) {
        return game.getServer().getPlayerManager().getPlayer(game.player.getUuid());
    }

    private void prepare(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        player.getServer().setDifficulty(Difficulty.NORMAL, true);
        player.changeGameMode(GameMode.CREATIVE);
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        for (BlockPos pos : BlockPos.iterate(START.add(-3, 0, -8), START.add(16, 0, 9))) {
            world.setBlockState(pos, Blocks.SMOOTH_STONE.getDefaultState());
        }
        Entity actor = Registries.ENTITY_TYPE.get(MOB).create(world);
        require(actor != null, "NPC entity type missing");
        Vec3d spawn = Vec3d.ofBottomCenter(START.up());
        actor.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, 0, 0);
        require(world.spawnEntity(actor), "NPC fixture spawn failed");
        actorId = actor.getUuid();
        actorNetworkId = actor.getId();
        aim(player, null, 2);
    }

    private void aim(ServerPlayerEntity player, BlockPos ground, int mode) {
        ServerWorld world = player.getServerWorld();
        Entity actor = world.getEntity(actorId);
        require(actor != null, "NPC fixture disappeared");
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(Registries.ITEM.get(TOOL)) || stack.getOrCreateNbt().getInt(MODE) != mode) {
            stack = new ItemStack(Registries.ITEM.get(TOOL));
            stack.getOrCreateNbt().putInt(MODE, mode);
            player.setStackInHand(Hand.MAIN_HAND, stack);
        }
        Vec3d focus = ground == null ? actor.getBoundingBox().getCenter() : Vec3d.ofBottomCenter(ground.up());
        boolean caged = ground == null && actor.getBlockPos().equals(CAGE.up());
        Vec3d eye = caged ? focus.add(0, 3.0, 0.04)
                : ground == null ? focus.add(0, .08, -2.2) : focus.add(.05, 2.7, .1);
        Vec3d aim = focus.subtract(eye);
        player.teleport(world, eye.x, eye.y - player.getStandingEyeHeight(), eye.z, yaw(aim), pitch(aim));
    }

    private static float yaw(Vec3d aim) {
        return MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90);
    }

    private static float pitch(Vec3d aim) {
        return (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalLength()));
    }

    private Probe observe(MinecraftClient game) {
        var player = player(game);
        var actor = player.getServerWorld().getEntity(actorId);
        require(actor != null, "NPC fixture unloaded");
        try {
            return new Probe(actor.writeNbt(new NbtCompound()).getCompound(STATE), actor.getPos(),
                    game.getServer().getTicks(), player.getMainHandStack().getOrCreateNbt().getInt(MODE),
                    sound(actor, "getAmbientSound", "method_5994") != null,
                    sound(actor, "getDeathSound", "method_6002") != null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to inspect authored NPC sounds", exception);
        }
    }

    private static SoundEvent sound(Entity actor, String named, String intermediary) throws ReflectiveOperationException {
        Method method;
        try {
            method = actor.getClass().getDeclaredMethod(named);
        } catch (NoSuchMethodException missingNamed) {
            method = actor.getClass().getDeclaredMethod(intermediary);
        }
        method.setAccessible(true);
        return (SoundEvent) method.invoke(actor);
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }
}
