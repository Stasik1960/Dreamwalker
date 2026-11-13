package dev.dreamwalker.buildingqa;

import dev.dreamwalker.buildingqa.mixin.MouseInputAccessor;
import dev.dreamwalker.buildingqa.mixin.OverlayMessageAccessor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/** Uses the regular client mouse bindings and checks both the server and rendered result. */
final class ModelShiftChecks {
    private static final Identifier TOOL = new Identifier("bloodborne_dw", "bb_tool");
    private static final String MODE_TAG = "DwDebugToolMode";
    private static final String[] MODES = {"up", "down", "left", "right", "forward", "back"};
    private static final List<Fixture> FIXTURES = List.of(
            Fixture.place("compact block", new BlockPos(438, 285, -841), "source_bricks_stone_2"),
            Fixture.place("fixed-ID composite", new BlockPos(445, 285, -841), "source_light_blue_stained_glass_form_0"),
            new Fixture("baked city fragment", new BlockPos(93, 124, -708), new Vec3d(97.484, 122.90780, -707.696),
                    88.4f, 5.2f, "native_physics_2", false, GameMode.CREATIVE),
            Fixture.place("OP2 survival compact block", new BlockPos(451, 285, -841), "source_bricks_stone_2", GameMode.SURVIVAL));

    private enum Stage { PREPARE, READY, SHIFT, CYCLE, ADVANCE }

    private record Fixture(String label, BlockPos pos, Vec3d player, float yaw, float pitch, String block, boolean place, GameMode gameMode) {
        static Fixture place(String label, BlockPos pos, String block) {
            return place(label, pos, block, GameMode.CREATIVE);
        }

        static Fixture place(String label, BlockPos pos, String block, GameMode gameMode) {
            return new Fixture(label, pos, new Vec3d(pos.getX() + .5, pos.getY() - 1, pos.getZ() - 2.4),
                    0, 0, block, true, gameMode);
        }

        Identifier id() { return new Identifier("bloodborne_dw", block); }
        boolean checkPixels() { return gameMode == GameMode.CREATIVE; }
        String screenshotName() { return label.replace(' ', '-'); }
    }

    private record Probe(int value, Vec3d offset, Identifier block, List<Box> collision, int mode, int permission) {}

    private final Method renderPose;
    private final Method x;
    private final Method y;
    private final Method z;
    private final List<String> failures = new ArrayList<>();
    private int fixtureIndex, mode, wait;
    private Stage stage = Stage.PREPARE;
    private CompletableFuture<Probe> serverProbe;
    private Probe origin;
    private Vec3d expected;
    private int[] baseline;
    private int[] still;
    private int stableFrames;

    ModelShiftChecks() throws ReflectiveOperationException {
        Class<?> type = Class.forName("dev.dreamwalker.bloodbornedw.tool.DebugAdjustments");
        renderPose = type.getMethod("renderPose", World.class, BlockPos.class, BlockState.class);
        x = type.getMethod("x", int.class);
        y = type.getMethod("y", int.class);
        z = type.getMethod("z", int.class);
    }

    String tick(MinecraftClient game) throws Exception {
        if (fixtureIndex == FIXTURES.size()) {
            return failures.isEmpty() ? "PASS BB model tool: all six mouse-selected directions on compact, fixed-ID and baked blocks, including OP2 survival and visible shifts on all creative fixtures"
                    : "FAIL BB model tool: " + String.join(" | ", failures);
        }
        Fixture fixture = FIXTURES.get(fixtureIndex);
        wait++;
        switch (stage) {
            case PREPARE -> {
                prepare(game, fixture);
                advance(Stage.READY);
            }
            case READY -> {
                if (wait > 560) {
                    if (fixture.checkPixels()) capture(game, "qa-shift-" + fixture.screenshotName() + "-not-ready.png");
                    fail(fixture, readiness(game, fixture));
                    break;
                }
                if (wait < 18 || !aiming(game, fixture) || !serverProbe.isDone()) break;
                origin = serverProbe.join();
                if (origin.mode() != 0 || !origin.block().equals(fixture.id())) {
                    fail(fixture, "expected fresh BB tool and target, got " + origin);
                    break;
                }
                if (fixture.gameMode() == GameMode.SURVIVAL && origin.permission() != 2) {
                    fail(fixture, "expected OP2 permission, got " + origin.permission());
                    break;
                }
                if (fixture.checkPixels()) {
                    if (!game.worldRenderer.isRenderingReady(fixture.pos()) || wait % 6 != 0) break;
                    int[] frame = capture(game, null);
                    if (!hasVisibleDetail(frame, fixture.place() ? 12 : 24)) break;
                    stableFrames = baseline != null && difference(baseline, frame) <= 4 ? stableFrames + 1 : 0;
                    baseline = frame;
                    if (stableFrames < 2) break;
                    still = capture(game, "qa-shift-" + fixture.screenshotName() + "-before.png");
                }
                expected = origin.offset();
                shift(game);
            }
            case SHIFT -> {
                if (wait == 5 || serverProbe == null) serverProbe = readServer(game, fixture);
                if (wait < 8 || !serverProbe.isDone()) break;
                Probe server = serverProbe.join();
                Probe client = read(game.world, fixture.pos(), game.player.getMainHandStack(), -1);
                if (!expected.equals(server.offset()) || !expected.equals(client.offset())) {
                    if (wait >= 55) fail(fixture, MODES[mode] + " expected " + expected + ", server="
                            + server.offset() + ", client=" + client.offset() + ", mode=" + server.mode()
                            + ", label=" + game.player.getMainHandStack().getName().getString()
                            + ", action bar=" + actionBar(game));
                    else serverProbe = readServer(game, fixture);
                    break;
                }
                if (!origin.block().equals(server.block()) || !origin.collision().equals(server.collision())) {
                    fail(fixture, MODES[mode] + " changed the block ID or collision");
                    break;
                }
                if (wait == 8 && game.player.getMainHandStack().getName().getString().matches(".*0[,.]5.*")) {
                    failures.add(fixture.label() + " " + MODES[mode] + " still shows a half-block step");
                }
                if (fixture.checkPixels() && mode == 0) {
                    if (wait < 24 || !game.worldRenderer.isRenderingReady(fixture.pos())) {
                        if (wait >= 260) fail(fixture, "shifted terrain did not become ready");
                        break;
                    }
                    int noise = difference(baseline, still);
                    int visible = difference(still, capture(game, "qa-shift-" + fixture.screenshotName() + "-up.png"));
                    System.out.println("DW SHIFT QA " + fixture.label() + " crop changed=" + visible + " idle=" + noise);
                    if (visible < Math.max(35, 3 * noise)) {
                        failures.add(fixture.label() + " pose synchronized but model did not visibly move (pixels="
                                + visible + ", idle=" + noise + ")");
                    }
                }
                System.out.println("DW SHIFT QA " + fixture.label() + " " + MODES[mode] + " -> " + expected);
                if (mode + 1 == MODES.length) {
                    advance(Stage.ADVANCE);
                    break;
                }
                if (!aiming(game, fixture)) {
                    fail(fixture, "crosshair no longer hits physical fixture after " + MODES[mode]);
                    break;
                }
                click(game, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                advance(Stage.CYCLE);
            }
            case CYCLE -> {
                if (wait == 5 || serverProbe == null) serverProbe = readServer(game, fixture);
                if (wait < 6 || !serverProbe.isDone()) break;
                Probe server = serverProbe.join();
                if (server.mode() == mode + 1 && itemMode(game.player.getMainHandStack()) == mode + 1) {
                    mode++;
                    shift(game);
                } else if (wait >= 55) {
                    fail(fixture, "right click did not select " + MODES[mode + 1] + ", server=" + server.mode()
                            + ", client=" + itemMode(game.player.getMainHandStack()) + ", action bar=" + actionBar(game));
                } else serverProbe = readServer(game, fixture);
            }
            case ADVANCE -> {
                fixtureIndex++;
                mode = 0;
                baseline = null;
                still = null;
                stableFrames = 0;
                advance(Stage.PREPARE);
            }
        }
        return null;
    }

    private void prepare(MinecraftClient game, Fixture fixture) {
        game.options.hudHidden = true;
        var server = game.getServer();
        var id = game.player.getUuid();
        serverProbe = server.submit(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            var world = player.getServerWorld();
            player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
            player.teleport(world, fixture.player().x, fixture.player().y, fixture.player().z, fixture.yaw(), fixture.pitch());
            world.getChunk(fixture.pos());
            if (fixture.place()) {
                var support = fixture.pos().down();
                if (fixture.checkPixels()) {
                    for (int dy = -1; dy <= 2; dy++) {
                        world.setBlockState(fixture.pos().add(0, dy, 2), Blocks.WHITE_CONCRETE.getDefaultState());
                    }
                }
                world.setBlockState(support, Blocks.STONE.getDefaultState());
                var item = (BlockItem) Registries.ITEM.get(fixture.id());
                var stack = new ItemStack(item);
                if (fixture.block().equals("source_bricks_stone_2")) {
                    stack.getOrCreateSubNbt("BlockStateTag").putString("compact", "true");
                }
                var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, .5, 0), Direction.UP, support, false);
                if (!item.place(new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit)).isAccepted()) {
                    throw new IllegalStateException("Could not place " + fixture.id());
                }
            }
            if (fixture.gameMode() == GameMode.SURVIVAL) {
                var players = server.getPlayerManager();
                players.getOpList().add(new OperatorEntry(player.getGameProfile(), 2, false));
                players.sendCommandTree(player);
                player.changeGameMode(GameMode.SURVIVAL);
                world.setBlockState(BlockPos.ofFloored(fixture.player()).down(), Blocks.STONE.getDefaultState());
            }
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Registries.ITEM.get(TOOL)));
            player.playerScreenHandler.sendContentUpdates();
            return read(world, fixture.pos(), player.getMainHandStack(), server.getPermissionLevel(player.getGameProfile()));
        });
    }

    private static boolean aiming(MinecraftClient game, Fixture fixture) {
        if (game.currentScreen != null || !game.world.isChunkLoaded(fixture.pos())
                || game.player.getAbilities().creativeMode != (fixture.gameMode() == GameMode.CREATIVE)
                || fixture.gameMode() == GameMode.SURVIVAL
                        && (!game.player.hasPermissionLevel(2) || game.player.hasPermissionLevel(3))
                || !game.player.getMainHandStack().isOf(Registries.ITEM.get(TOOL))
                || game.player.getPos().squaredDistanceTo(fixture.player()) > .01) return false;
        game.gameRenderer.updateTargetedEntity(1);
        return game.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(fixture.pos());
    }

    private String readiness(MinecraftClient game, Fixture fixture) {
        var state = game.world.getBlockState(fixture.pos());
        String hit = game.crosshairTarget instanceof BlockHitResult blockHit
                ? blockHit.getType() + " " + blockHit.getBlockPos().toShortString() + " @ " + blockHit.getPos()
                : String.valueOf(game.crosshairTarget);
        return "target, visible terrain, or tool not ready: hit=" + hit + ", player=" + game.player.getPos()
                + ", wanted=" + fixture.player() + ", camera=" + game.player.getYaw() + "/" + game.player.getPitch()
                + ", creative=" + game.player.getAbilities().creativeMode + ", OP2=" + game.player.hasPermissionLevel(2)
                + ", OP3=" + game.player.hasPermissionLevel(3) + ", held=" + game.player.getMainHandStack()
                + ", rendered=" + game.worldRenderer.isRenderingReady(fixture.pos()) + ", stable frames=" + stableFrames
                + ", target state=" + state + ", outline=" + state.getOutlineShape(game.world, fixture.pos()).getBoundingBoxes();
    }

    private void shift(MinecraftClient game) {
        Direction facing = game.player.getHorizontalFacing();
        Direction direction = switch (mode) {
            case 0 -> Direction.UP;
            case 1 -> Direction.DOWN;
            case 2 -> facing.rotateYCounterclockwise();
            case 3 -> facing.rotateYClockwise();
            case 4 -> facing;
            case 5 -> facing.getOpposite();
            default -> throw new IllegalStateException("Unknown shift mode " + mode);
        };
        expected = expected.add(direction.getOffsetX() * .25, direction.getOffsetY() * .25, direction.getOffsetZ() * .25);
        click(game, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        advance(Stage.SHIFT);
    }

    private static void click(MinecraftClient game, int button) {
        var mouse = (MouseInputAccessor) game.mouse;
        long window = game.getWindow().getHandle();
        mouse.dw$onMouseButton(window, button, GLFW.GLFW_PRESS, 0);
        mouse.dw$onMouseButton(window, button, GLFW.GLFW_RELEASE, 0);
    }

    private CompletableFuture<Probe> readServer(MinecraftClient game, Fixture fixture) {
        var server = game.getServer();
        var id = game.player.getUuid();
        return server.submit(() -> {
            var player = server.getPlayerManager().getPlayer(id);
            return read(player.getServerWorld(), fixture.pos(), player.getMainHandStack(), server.getPermissionLevel(player.getGameProfile()));
        });
    }

    private Probe read(World world, BlockPos pos, ItemStack stack, int permission) {
        try {
            var state = world.getBlockState(pos);
            int value = (int) renderPose.invoke(null, world, pos, state);
            return new Probe(value, new Vec3d((double) x.invoke(null, value), (double) y.invoke(null, value),
                    (double) z.invoke(null, value)), Registries.BLOCK.getId(state.getBlock()),
                    state.getCollisionShape(world, pos).getBoundingBoxes(), itemMode(stack), permission);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read DreamWalker pose", failure);
        }
    }

    private static String actionBar(MinecraftClient game) {
        var message = ((OverlayMessageAccessor) game.inGameHud).dw$getOverlayMessage();
        return message == null ? "none" : message.getString();
    }

    private static int itemMode(ItemStack stack) {
        return stack.getNbt() == null ? 0 : stack.getNbt().getInt(MODE_TAG);
    }

    private static int[] capture(MinecraftClient game, String filename) throws Exception {
        try (NativeImage image = ScreenshotRecorder.takeScreenshot(game.getFramebuffer())) {
            if (filename != null) image.writeTo(Path.of(game.runDirectory.getPath(), filename));
            int radius = Math.max(16, image.getHeight() / 14);
            int[] pixels = new int[4 * radius * radius];
            int i = 0;
            for (int dy = -radius; dy < radius; dy++) {
                for (int dx = -radius; dx < radius; dx++) {
                    pixels[i++] = image.getColor(image.getWidth() / 2 + dx, image.getHeight() / 2 + dy);
                }
            }
            return pixels;
        }
    }

    private static boolean hasVisibleDetail(int[] pixels, int minimumShades) {
        var shades = new HashSet<Integer>();
        for (int pixel : pixels) {
            shades.add(pixel & 0x00FCFCFC);
            if (shades.size() >= minimumShades) return true;
        }
        return false;
    }

    private static int difference(int[] first, int[] second) {
        int changed = 0;
        for (int i = 0; i < first.length; i++) {
            int distance = 0;
            for (int shift = 0; shift < 24; shift += 8) {
                distance += Math.abs((first[i] >>> shift & 255) - (second[i] >>> shift & 255));
            }
            if (distance > 35) changed++;
        }
        return changed;
    }

    private void fail(Fixture fixture, String detail) {
        String message = fixture.label() + " " + detail;
        System.out.println("DW SHIFT QA FAIL " + message);
        failures.add(message);
        advance(Stage.ADVANCE);
    }

    private void advance(Stage next) {
        stage = next;
        wait = 0;
        if (next != Stage.READY) serverProbe = null;
    }
}
