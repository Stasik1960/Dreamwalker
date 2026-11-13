package dev.dreamwalker.buildingqa;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Runs on the integrated server thread on a disposable copy. */
final class WorldChecks {
    private static final String DW = "bloodborne_dw:";
    private static final String STUDIO_RP = "dev.dreamwalker.bloodbornedw.composite.StudioRpGeometry";
    private static final String RP_TYPE = "dev.dreamwalker.bloodbornerp.object.RpObjectEntity";
    private static final String NEIGHBOR_QA = "dev.dreamwalker.qa.BuildingNeighborsQa";
    private static final BlockPos FIRST = new BlockPos(444, 280, -835);
    private static final BlockPos SECOND = FIRST.add(3, 1, 3);
    private static boolean neighbors;

    private WorldChecks() {}

    static List<String> start(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        return List.of(
                verify("registered fixed model and all slab types", WorldChecks::forms),
                verify("wooden axe WorldEdit selection and suppressed damage/use", () -> axe(player, world)),
                verify("authored 45-degree entity collision and cardinal regression", () -> diagonal(world)),
                verify("screenshot block placement and collisions", () -> ScreenshotBlockChecks.verify(player)),
                verify("926 fixed models and 567 fixed slabs, including upper slab placement", () ->
                        Class.forName(NEIGHBOR_QA).getMethod("verifyDistinctForms", ServerWorld.class, ServerPlayerEntity.class)
                                .invoke(null, world, player)),
                verify("sign/fence fixture setup", () -> {
                    Class.forName(NEIGHBOR_QA).getMethod("start", ServerWorld.class, ServerPlayerEntity.class)
                            .invoke(null, world, player);
                    neighbors = true;
                })
        );
    }

    static String finish(ServerPlayerEntity player) {
        return neighbors ? verify("sign placement and fence adjacency after server updates", () ->
                Class.forName(NEIGHBOR_QA).getMethod("verifyAfterTicks", ServerWorld.class)
                        .invoke(null, player.getServerWorld())) : null;
    }

    private static void forms() throws Exception {
        Identifier oldId = new Identifier(DW + "source_addon_wall_deco_3");
        Identifier formId = new Identifier(DW + "source_addon_wall_deco_3_form_2");
        var old = Registries.BLOCK.get(oldId);
        var form = Registries.BLOCK.get(formId);
        check(old != form && old.getStateManager().getProperty("variant") != null
                        && form.getStateManager().getProperty("variant") == null,
                "Legacy and fixed selectors must both remain");
        check(Registries.ITEM.get(formId) != Items.AIR, "Missing fixed form item");
        var type = Class.forName("dev.dreamwalker.bloodbornedw.composite.DistinctForms");
        String base = DW + "restored_slab_5ff0bf7f58a8b6d4";
        for (SlabType slab : SlabType.values()) {
            String suffix = switch (slab) {
                case BOTTOM -> "_half";
                case TOP -> "_upper";
                case DOUBLE -> "_full";
            };
            Identifier id = new Identifier(base + suffix);
            var block = Registries.BLOCK.get(id);
            check(block instanceof SlabBlock && block.getStateManager().getProperty("type") == null
                            && block.getDefaultState().get(SlabBlock.TYPE) == slab,
                    "Wrong fixed slab state for " + id);
            var legacy = Registries.BLOCK.get(new Identifier(base)).getDefaultState().with(SlabBlock.TYPE, slab);
            ItemStack picked = (ItemStack) type.getMethod("slabPick", net.minecraft.block.BlockState.class)
                    .invoke(null, legacy);
            check(Registries.ITEM.getId(picked.getItem()).equals(id), "Wrong slab pick for " + id);
        }
    }

    private static void axe(ServerPlayerEntity player, ServerWorld world) throws Exception {
        player.changeGameMode(GameMode.CREATIVE);
        player.getServer().getPlayerManager().addToOperators(player.getGameProfile());
        ItemStack before = player.getMainHandStack().copy();
        var firstState = world.getBlockState(FIRST);
        var secondState = world.getBlockState(SECOND);
        world.setBlockState(FIRST, Blocks.OAK_LOG.getDefaultState());
        world.setBlockState(SECOND, Blocks.STONE.getDefaultState());
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WOODEN_AXE));
        try {
            var side = Direction.UP;
            var first = new BlockHitResult(Vec3d.ofCenter(FIRST), side, FIRST, false);
            var second = new BlockHitResult(Vec3d.ofCenter(SECOND), side, SECOND, false);
            check(AttackBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, FIRST, side)
                            != ActionResult.PASS,
                    "Wooden axe fell through to vanilla mining");
            check(UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, second)
                            != ActionResult.PASS,
                    "WorldEdit did not receive the secondary click");
            check(Items.WOODEN_AXE.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, first)) == ActionResult.PASS
                            && world.getBlockState(FIRST).isOf(Blocks.OAK_LOG),
                    "Wooden axe stripped the log");
            Entity pig = EntityType.PIG.create(world);
            check(AttackEntityCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, pig, null)
                            == ActionResult.FAIL,
                    "Wooden axe did not suppress entity damage");

            // Inspect the selection through the stable WorldEdit API (Minecraft mappings differ).
            Object actor = findAdapter("adaptPlayer", player);
            Object weWorld = findAdapter("adapt", world);
            var worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object edit = worldEdit.getMethod("getInstance").invoke(null);
            Object sessions = worldEdit.getMethod("getSessionManager").invoke(edit);
            Class<?> owner = Class.forName("com.sk89q.worldedit.session.SessionOwner");
            Object session = sessions.getClass().getMethod("get", owner).invoke(sessions, actor);
            Class<?> worldClass = Class.forName("com.sk89q.worldedit.world.World");
            Object region = session.getClass().getMethod("getSelection", worldClass).invoke(session, weWorld);
            check(vecEquals(region.getClass().getMethod("getMinimumPoint").invoke(region), FIRST)
                            && vecEquals(region.getClass().getMethod("getMaximumPoint").invoke(region), SECOND),
                    "WorldEdit did not retain both selected corners");
        } finally {
            player.setStackInHand(Hand.MAIN_HAND, before);
            world.setBlockState(FIRST, firstState);
            world.setBlockState(SECOND, secondState);
        }
    }

    private static Object findAdapter(String name, Object argument) throws Exception {
        for (var method : Class.forName("com.sk89q.worldedit.fabric.FabricAdapter").getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(argument)) return method.invoke(null, argument);
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean vecEquals(Object point, BlockPos pos) throws Exception {
        var clazz = point.getClass();
        return clazz.getMethod("getBlockX").invoke(point).equals(pos.getX())
                && clazz.getMethod("getBlockY").invoke(point).equals(pos.getY())
                && clazz.getMethod("getBlockZ").invoke(point).equals(pos.getZ());
    }

    @SuppressWarnings("unchecked")
    private static void diagonal(ServerWorld world) throws Exception {
        var entityType = Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp:door_1"));
        var door = entityType.create(world);
        check(door != null, "Missing reviewed entity door");
        var layout = Class.forName(STUDIO_RP).getMethod("layout", Class.forName(RP_TYPE));
        door.refreshPositionAndAngles(444.5, 279, -835.5, 90, 0);
        Object before = layout.invoke(null, door);
        var players = before.getClass().getMethod("players");
        var blocks = before.getClass().getMethod("blocks");
        List<Box> cardinal = (List<Box>) players.invoke(before);
        check(cardinal.size() == 1, "Reviewed cardinal door no longer has its one exact collider");
        door.setYaw(45);
        Object after = layout.invoke(null, door);
        List<Box> turned = (List<Box>) players.invoke(after);
        check(turned.size() > 1 && ((List<?>) blocks.invoke(after)).size() > 1,
                "Diagonal door still uses one enlarged bounding box");
        Box bounds = turned.stream().reduce(Box::union).orElseThrow();
        double x = bounds.minX + .001, y = (bounds.minY + bounds.maxY) / 2, z = bounds.minZ + .001;
        boolean nearCorner = turned.stream().anyMatch(box -> box.contains(x, y, z));
        boolean farCorner = turned.stream().anyMatch(box -> box.contains(x, y, bounds.maxZ - .001));
        check(!nearCorner || !farCorner, "Both outside corners were filled by diagonal collision");
        door.setYaw(90);
        check(cardinal.equals(players.invoke(layout.invoke(null, door))), "Cardinal geometry changed after rotation");
        door.discard();
    }

    private static String verify(String name, Checked action) {
        try {
            action.run();
            return "PASS " + name;
        } catch (Throwable failure) {
            if (failure instanceof InvocationTargetException reflected) failure = reflected.getCause();
            failure.printStackTrace();
            return "FAIL " + name + ": " + failure;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }
}
