package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.window.WindowPrototypes;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeLedger;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.GlazingTypes;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Technical checks of actual item/block/runtime paths; client visuals remain separate. */
public final class PrototypeWindowGameTests implements FabricGameTest {
    private static final String TEMPLATE = "bloodborne_dw:window_test";
    private static final BlockPos LOCAL_ROOT = new BlockPos(8, 4, 8);

    @GameTest(templateName = TEMPLATE, tickLimit = 260, batchId = "prototype_windows")
    public void ordinaryItemsPlaceOwnDrawingsCardinalGlassAndEightYawWood(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(LOCAL_ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer(); int placed = 0;
        try {
            for (String key : List.of(WindowPrototypes.WOOD_KEY, GlazingTypes.WINDOW01, GlazingTypes.WINDOW02, GlazingTypes.WINDOW03)) {
                CompositeRootBlock block = CompositeArchitecture.kindBlock(key); Item item = CompositeArchitecture.kindItem(key);
                int forms = 1;
                for (int variant = 0; variant < forms; variant++) for (String profile : List.of("base", "alt")) {
                    Set<Integer> rotations = new HashSet<>();
                    for (int direction = 0; direction < 8; direction++) {
                        clear(world, root); world.setBlockState(root.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                        outside(player, root, 180 + direction * 45);
                        ItemStack stack = art(item, variant, profile, false, 2); NbtCompound before = stack.getNbt().copy();
                        ActionResult used = use(item, world, player, stack, root.down(), Direction.UP);
                        BlockState state = world.getBlockState(root);
                        context.assertTrue(used.isAccepted() && state.isOf(block), "ordinary window item must place: " + key + "/" + variant + "/" + profile + "/" + direction + ": " + used);
                        context.assertTrue(state.get(CompositeRootBlock.VARIANT) == variant && state.get(CompositeRootBlock.PROFILE).asString().equals(profile), "placement preserves supplied art and profile");
                        rotations.add(state.get(CompositeRootBlock.ROTATION));
                        context.assertTrue(stack.getCount() == 1 && before.equals(stack.getNbt()), "one logical object consumes one item without rewriting the remaining item NBT");
                        context.assertTrue(world.getBlockState(root.down()).isOf(Blocks.STONE), "source support is preserved");
                        Owner owner = resident(world, root);
                        context.assertTrue(owner != null && owner.registryId().equals("bloodborne_dw:" + key), "one registered owner exists at the actual item root");
                        context.assertTrue(CompositeRuntime.remove(world, owner, player, false).outcome() == Outcome.COMMITTED, "placement fixture cleanup commits");
                        placed++;
                    }
                    context.assertTrue(rotations.size() == (GlazingTypes.isGlazingPath(key)?4:8), "ordinary glass has four cardinal poses and wood retains eight: " + key + "/" + variant + "/" + profile);
                }
            }
            context.assertTrue(placed == 64, "16 wood and48 three-type cardinal glass ordinary item placements checked");
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = TEMPLATE, tickLimit = 160, batchId = "prototype_windows")
    public void bothSidesRaycastAndClickToggleOnlyWoodSidePanels(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(LOCAL_ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer();
        try {
            clear(world, root); world.setBlockState(root.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            CompositeRootBlock block = CompositeArchitecture.kindBlock(WindowPrototypes.WOOD_KEY);
            outside(player, root, 180);
            context.assertTrue(use(CompositeArchitecture.kindItem(WindowPrototypes.WOOD_KEY), world, player,
                    art(CompositeArchitecture.kindItem(WindowPrototypes.WOOD_KEY), 0, "base", false, 1), root.down(), Direction.UP).isAccepted(), "wood item placement succeeds");
            for (Direction side : List.of(Direction.NORTH, Direction.SOUTH)) {
                Vec3d center = Vec3d.of(root).add(.5, .5, .5);
                Vec3d start = center.add(0, 0, side == Direction.NORTH ? -3 : 3);
                lookAt(player, start, center);
                BlockHitResult hit = world.raycast(new RaycastContext(start, center.add(0, 0, side == Direction.NORTH ? 2 : -2), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
                context.assertTrue(hit.getType() == HitResult.Type.BLOCK, "actual outline ray hits the fixed center from " + side);
                BlockState before = world.getBlockState(root);
                var fixedPart = block.spec.pose(before).parts().get(0);
                BlockState clicked = world.getBlockState(hit.getBlockPos());
                context.assertTrue(CompositeRuntime.target(world,hit.getBlockPos(),player)!=null,"click ray resolves owner from "+side+": "+CompositeRuntime.debugTarget(world,hit.getBlockPos(),player));
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                context.assertTrue(!clicked.getBlock().onUse(clicked,world,hit.getBlockPos(),player,Hand.MAIN_HAND,hit).isAccepted()&&world.getBlockState(root).equals(before),"empty hand leaves decorative panels unchanged");
                player.getAbilities().creativeMode=true;
                ItemStack tool=new ItemStack(CompositeArchitecture.BUILDER);tool.getOrCreateNbt().putInt("BuilderAction",dev.dreamwalker.bloodbornedw.architecture.BuildingTool.Action.POSE.ordinal());player.setStackInHand(Hand.MAIN_HAND,tool);
                ActionResult clickedResult=dev.dreamwalker.bloodbornedw.architecture.BuildingTool.applyBlock(player,hit.getBlockPos(),dev.dreamwalker.bloodbornedw.architecture.BuildingTool.Action.POSE);
                context.assertTrue(clickedResult.isAccepted(), "authorised construction tool toggles wood panels from " + side+": "+clickedResult);
                BlockState after = world.getBlockState(root);
                context.assertTrue(after.get(CompositeRootBlock.OPEN) != before.get(CompositeRootBlock.OPEN), "server state changes open/closed");
                context.assertTrue(fixedPart.equals(block.spec.pose(after).parts().get(0)), "central artwork/model/pivot stays fixed");
                context.assertTrue(centerBlocked(world, root), "fixed opaque center remains a physical barrier in both poses");
            }
            context.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN), "two opposite-side clicks return to exact source pose");
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = TEMPLATE, tickLimit = 180, batchId = "prototype_windows")
    public void pickFromRemotePartAndNativeBreakYieldOneObjectAndNoHelpers(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(LOCAL_ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer();
        try {
            clear(world, root); world.setBlockState(root.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL); outside(player, root, 180);
            Item item = CompositeArchitecture.kindItem(WindowPrototypes.WOOD_KEY);
            context.assertTrue(use(item, world, player, art(item, 0, "alt", false, 1), root.down(), Direction.UP).isAccepted(), "wood placement succeeds");
            Owner owner = resident(world, root);
            BlockPos helper = remoteHelper(world, root, owner);
            context.assertTrue(helper != null && !helper.equals(root), "actual sparse service block exists outside root");
            BlockState helperState = world.getBlockState(helper);
            ItemStack picked = helperState.getBlock().getPickStack(world, helper, helperState);
            context.assertTrue(picked.isOf(item) && picked.getCount() == 1 && picked.getSubNbt("BlockStateTag").getString("profile").equals("alt"), "remote pick resolves one root item and declared artistic profile");
            context.assertTrue(breakAimedHelper(world, helper, player), "native survival break removes the selected service block");
            context.assertTrue(world.getBlockState(root).isAir(), "breaking a service part removes its root");
            assertNoOwner(context, world, root, owner);
            List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class, new Box(root).expand(6), entity -> entity.getStack().isOf(item));
            context.assertTrue(drops.size() == 1 && drops.get(0).getStack().getCount() == 1, "one service break yields exactly one object item");
            context.assertTrue(world.getBlockState(root.down()).isOf(Blocks.STONE), "removal preserves native support");
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = TEMPLATE, tickLimit = 200, batchId = "prototype_windows")
    public void actualChestOverlapRefusesNewItemAndInitialSourceKeepsExactForeignNbt(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(LOCAL_ROOT), foreign = root.east();
        PlayerEntity player = context.createMockSurvivalPlayer();
        try {
            clear(world, root); world.setBlockState(root.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL); outside(player, root, 180);
            world.setBlockState(foreign, Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
            ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(foreign); chest.setStack(0, new ItemStack(Items.DIAMOND, 7));
            BlockState original = world.getBlockState(foreign); NbtCompound originalNbt = chest.createNbt();
            Item item = CompositeArchitecture.kindItem(WindowPrototypes.WOOD_KEY);
            ItemStack fresh=art(item,0,"base",false,2);NbtCompound remaining=fresh.getNbt().copy(),ledgerBefore=CompositeLedger.get(world).writeNbt(new NbtCompound());
            context.assertTrue(!use(item,world,player,fresh,root.down(),Direction.UP).isAccepted(),"positive solid intersection with actual chest refuses ordinary item placement");
            context.assertTrue(fresh.getCount()==2&&fresh.getNbt().equals(remaining)&&world.getBlockState(root).isAir()&&ledgerBefore.equals(CompositeLedger.get(world).writeNbt(new NbtCompound())),"rejected placement is atomic and preserves item count/typed NBT/ownership");
            UUID sourceId=UUID.randomUUID();BlockState source=CompositeArchitecture.kindBlock(WindowPrototypes.WOOD_KEY).getDefaultState();
            context.assertTrue(SourceConversionScope.initialInstances(Set.of(sourceId),()->CompositeRuntime.place(world,root,source,sourceId,null)).outcome()==Outcome.COMMITTED,"one explicitly scoped initial source instance retains its historical physical intersection");
            Owner owner = resident(world, root);
            context.assertTrue(world.getBlockState(foreign).equals(original) && world.getBlockEntity(foreign) == chest && chest.createNbt().equals(originalNbt), "native foreign state, original BE instance and all NBT survive placement");
            BlockState next=world.getBlockState(root).with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT);
            context.assertTrue(CompositeRuntime.transition(world, owner, next, player).outcome() == Outcome.COMMITTED, "decorative profile transition keeps unchanged source physical intersection");
            context.assertTrue(world.getBlockState(foreign).equals(original) && world.getBlockEntity(foreign) == chest && chest.createNbt().equals(originalNbt), "native foreign state, BE instance and exact typed data survive profile change");
            context.assertTrue(CompositeRuntime.remove(world, owner, player, false).outcome() == Outcome.COMMITTED, "owner removal commits");
            context.assertTrue(world.getBlockState(foreign).equals(original) && world.getBlockEntity(foreign) == chest && chest.createNbt().equals(originalNbt), "owner removal preserves the foreign chest byte-equivalent NBT");
            assertNoOwner(context, world, root, owner);
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = TEMPLATE, tickLimit = 180, batchId = "prototype_windows")
    public void actualBuilderSteps45AndProfileKeepsPhysicalSemantics(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(LOCAL_ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer();player.getAbilities().creativeMode=true;
        try {
            clear(world, root); world.setBlockState(root.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL); outside(player, root, 180);
            Item item = CompositeArchitecture.kindItem(WindowPrototypes.WOOD_KEY);
            context.assertTrue(use(item, world, player, art(item, 0, "base", false, 1), root.down(), Direction.UP).isAccepted(), "wood placement succeeds");
            Item tool = Registries.ITEM.get(new Identifier("bloodborne_dw", "composite_builder"));
            context.assertTrue(tool != Items.AIR, "ordinary composite builder tool is registered");
            Owner owner = resident(world, root); ItemStack toolStack = new ItemStack(tool);
            player.setStackInHand(Hand.MAIN_HAND,toolStack);int initial = world.getBlockState(root).get(CompositeRootBlock.ROTATION);
            for (int turn = 0; turn < 8; turn++) {
                BlockState before = world.getBlockState(root); lookOutsideAtCenter(player, root);
                context.assertTrue(CompositeRuntime.target(world,root,player)!=null,"builder ray resolves owner at turn"+turn+": "+CompositeRuntime.debugTarget(world,root,player));
                player.setStackInHand(Hand.MAIN_HAND,toolStack);ActionResult toolResult=dev.dreamwalker.bloodbornedw.architecture.BuildingTool.applyBlock(player,root,dev.dreamwalker.bloodbornedw.architecture.BuildingTool.Action.ROTATE);
                context.assertTrue(toolResult.isAccepted(), "builder45 rotation succeeds at turn"+turn+": "+toolResult+" result="+CompositeRuntime.lastResult()+"; "+CompositeRuntime.debugTarget(world,root,player));
                context.assertTrue(world.getBlockState(root).get(CompositeRootBlock.ROTATION) == (before.get(CompositeRootBlock.ROTATION) + 1) % 8, "one builder action changes exactly45 degrees");
                context.assertTrue(CompositeRuntime.target(world, root, player).equals(owner), "rotation retains root UUID and registry identity");
            }
            context.assertTrue(world.getBlockState(root).get(CompositeRootBlock.ROTATION) == initial, "eight45 steps return to initial orientation");
            player.setSneaking(true); BlockState before = world.getBlockState(root); lookOutsideAtCenter(player, root);
            context.assertTrue(dev.dreamwalker.bloodbornedw.architecture.BuildingTool.applyBlock(player,root,dev.dreamwalker.bloodbornedw.architecture.BuildingTool.Action.PROFILE).isAccepted(), "explicit technical profile action succeeds");
            BlockState after = world.getBlockState(root);
            context.assertTrue(after.get(CompositeRootBlock.PROFILE) != before.get(CompositeRootBlock.PROFILE)
                    && after.get(CompositeRootBlock.ROTATION).equals(before.get(CompositeRootBlock.ROTATION))
                    && after.get(CompositeRootBlock.OPEN).equals(before.get(CompositeRootBlock.OPEN)), "BASE/ALT affects artistic profile only");
            context.assertTrue(centerBlocked(world, root) && toolStack.getCount() == 1, "profile preserves fixed central physical barrier and tool count");
            context.complete();
        } finally { player.setSneaking(false); clear(world, root); player.discard(); }
    }

    @GameTest(templateName = TEMPLATE, tickLimit = 140, batchId = "prototype_windows")
    public void nonFullCubeSupportWorksWithoutReplacingItsNativeFence(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(LOCAL_ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer();
        try {
            clear(world, root); world.setBlockState(root.down(), Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_ALL);
            BlockState support = world.getBlockState(root.down()); outside(player, root, 180);
            Item item = CompositeArchitecture.kindItem(WindowPrototypes.WOOD_KEY);
            context.assertTrue(use(item, world, player, art(item, 0, "base", false, 1), root.down(), Direction.UP).isAccepted(), "a native non-full fence provides geometric support without a full-cube-only rule");
            Owner owner = resident(world, root);
            context.assertTrue(owner != null && world.getBlockState(root.down()).equals(support), "non-full support retains its exact native state");
            context.assertTrue(CompositeRuntime.remove(world, owner, player, false).outcome() == Outcome.COMMITTED, "non-full-supported object removal commits");
            context.assertTrue(world.getBlockState(root.down()).equals(support), "removing the window preserves its non-full support");
            assertNoOwner(context, world, root, owner); context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    @GameTest(templateName = TEMPLATE, tickLimit = 180, batchId = "prototype_windows")
    public void minecraftStateAndOwnerPayloadRoundtripThenPickedPlacementGetsNewUuid(TestContext context) {
        ServerWorld world = context.getWorld(); BlockPos root = context.getAbsolutePos(LOCAL_ROOT);
        PlayerEntity player = context.createMockSurvivalPlayer();
        try {
            clear(world, root); world.setBlockState(root.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL); outside(player, root, 225);
            Item item = CompositeArchitecture.kindItem(WindowPrototypes.THIN_KEY);
            context.assertTrue(use(item, world, player, art(item, 2, "alt", false, 1), root.down(), Direction.UP).isAccepted(), "old intrinsic45 item normalizes to independent cardinal window03");
            BlockState before = world.getBlockState(root); Owner originalOwner = resident(world, root);
            NbtCompound stateNbt = NbtHelper.fromBlockState(before);
            context.assertTrue(NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK), stateNbt.copy()).equals(before), "Minecraft palette codec preserves global rotation/profile/art and source form");
            BlockEntity blockEntity = world.getBlockEntity(root); NbtCompound saved = blockEntity.createNbtWithIdentifyingData();
            BlockEntity restored = BlockEntity.createFromNbt(root, before, saved.copy());
            context.assertTrue(restored instanceof CompositeBlockEntity && restored.createNbtWithIdentifyingData().equals(saved), "registered BE NBT codec roundtrips owner and opaque payload");
            world.removeBlockEntity(root); world.addBlockEntity(restored);
            context.assertTrue(resident(world, root).equals(originalOwner), "loaded root BE keeps exact owner UUID");
            ItemStack picked = CompositeRuntime.pick(world, originalOwner);
            Item canonical = CompositeArchitecture.kindItem(GlazingTypes.WINDOW03);
            context.assertTrue(picked.isOf(canonical) && picked.getCount() == 1 && picked.getSubNbt("CompositePayload")==null, "pick serializes one canonical window03 item without legacy angular pose");
            context.assertTrue(CompositeRuntime.remove(world, originalOwner, player, false).outcome() == Outcome.COMMITTED, "original owner removal commits");
            outside(player, root, 180);
            context.assertTrue(use(canonical, world, player, picked, root.down(), Direction.UP).isAccepted(), "picked object can be newly built again");
            Owner next = resident(world, root);
            context.assertTrue(next != null && !next.instanceId().equals(originalOwner.instanceId()) && next.registryId().equals(originalOwner.registryId()), "new placement creates a fresh UUID of the same user type");
            context.assertTrue(world.getBlockState(root).get(CompositeRootBlock.VARIANT) == 0 && world.getBlockState(root).get(CompositeRootBlock.ROTATION)%2==0 && world.getBlockState(root).get(CompositeRootBlock.PROFILE).asString().equals("alt"), "new placement retains canonical drawing and profile with cardinal rotation only");
            context.complete();
        } finally { clear(world, root); player.discard(); }
    }

    private static ItemStack art(Item item, int variant, String profile, boolean open, int count) {
        ItemStack stack = new ItemStack(item, count); NbtCompound tag = stack.getOrCreateSubNbt("BlockStateTag");
        tag.putString("variant", Integer.toString(variant)); tag.putString("profile", profile); tag.putString("open", Boolean.toString(open));
        return stack;
    }
    private static ActionResult use(Item item, ServerWorld world, PlayerEntity player, ItemStack stack, BlockPos clicked, Direction side) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return item.useOnBlock(new ItemUsageContext(world, player, Hand.MAIN_HAND, stack,
                new BlockHitResult(Vec3d.ofCenter(clicked).add(0, .5, 0), side, clicked, false)));
    }
    private static boolean centerBlocked(ServerWorld world, BlockPos root) {
        return world.getBlockState(root).getCollisionShape(world, root).getBoundingBoxes().stream().anyMatch(box -> box.contains(.5, .5, .5));
    }
    private static Owner resident(ServerWorld world, BlockPos root) {
        return world.getBlockEntity(root) instanceof CompositeBlockEntity entity ? entity.resident() : null;
    }
    private static boolean breakAimedHelper(ServerWorld world, BlockPos helper, PlayerEntity player) {
        Box part = world.getBlockState(helper).getOutlineShape(world, helper, ShapeContext.absent()).getBoundingBoxes().get(0);
        Vec3d target = Vec3d.of(helper).add((part.minX + part.maxX) / 2, (part.minY + part.maxY) / 2, (part.minZ + part.maxZ) / 2);
        lookAt(player, target.add(0, 0, -3), target);
        if(CompositeRuntime.target(world,helper,player)==null)throw new AssertionError("native break ray must resolve owner: "+CompositeRuntime.debugTarget(world,helper,player));
        return world.breakBlock(helper, true, player);
    }
    private static BlockPos remoteHelper(ServerWorld world, BlockPos root, Owner owner) {
        for (BlockPos pos : BlockPos.iterate(root.add(-4, -2, -4), root.add(4, 3, 4))) {
            if (pos.equals(root)) continue;
            if (world.getBlockEntity(pos) instanceof CompositeBlockEntity cell
                    && cell.contributions().stream().anyMatch(entry -> entry.owner().equals(owner))) return pos.toImmutable();
        }
        return null;
    }
    private static void assertNoOwner(TestContext context, ServerWorld world, BlockPos root, Owner owner) {
        for (BlockPos pos : BlockPos.iterate(root.add(-4, -2, -4), root.add(4, 3, 4))) {
            if (world.getBlockEntity(pos) instanceof CompositeBlockEntity cell)
                context.assertTrue(!owner.equals(cell.resident()) && cell.contributions().stream().noneMatch(entry -> entry.owner().equals(owner)), "no stale helper/root contribution at " + pos);
        }
        CompositeLedger ledger = CompositeLedger.get(world);
        for (Cell cell : ledger.cells()) context.assertTrue(ledger.at(cell).stream().noneMatch(entry -> entry.owner().equals(owner)), "no stale foreign-carrier ownership in persistent ledger at " + cell);
    }
    private static void outside(PlayerEntity player, BlockPos root, float yaw) {
        double angle = Math.toRadians(yaw), dx = -Math.sin(angle), dz = Math.cos(angle);
        double eyeHeight = player.getEyeY() - player.getY();
        player.refreshPositionAndAngles(root.getX() + .5 - 3 * dx, root.getY() + .5 - eyeHeight, root.getZ() + .5 - 3 * dz, yaw, 0);
        player.setHeadYaw(yaw);
    }
    private static void lookOutsideAtCenter(PlayerEntity player, BlockPos root) {
        Vec3d center = Vec3d.of(root).add(.5, .5, .5);
        player.refreshPositionAndAngles(root.getX() + 3.5, root.getY() + 1.2, root.getZ() + 3.5, 0, 0);
        aim(player, center);
    }
    private static void lookAt(PlayerEntity player, Vec3d eye, Vec3d target) {
        player.refreshPositionAndAngles(eye.x, eye.y - (player.getEyeY() - player.getY()), eye.z, 0, 0); aim(player, target);
    }
    private static void aim(PlayerEntity player, Vec3d target) {
        Vec3d delta = target.subtract(player.getEyePos());
        player.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        player.setHeadYaw(player.getYaw());
    }
    private static void clear(ServerWorld world, BlockPos root) {
        // Only the isolated template volume is touched; no source-map block is involved.
        for (BlockPos pos : BlockPos.iterate(root.add(-5, -3, -5), root.add(5, 4, 5))) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        CompositeRuntime.drain(world);
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(root).expand(6), entity -> true)) item.discard();
    }
}
