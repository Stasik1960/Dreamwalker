package dev.dreamwalker.bloodbornedw.architecture;

import java.util.List;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import static net.minecraft.server.command.CommandManager.literal;

/** Isolated checkpoint registration; does not assign final catalog numbers. */
public final class PrototypeArchitecture {
    public static PrototypeLadderBlock LADDER;
    public static PrototypeLadderItem LADDER_ITEM;
    public static final java.util.List<PrototypeLadderBlock> LADDERS=new java.util.ArrayList<>();
    public static final java.util.List<PrototypeLadderItem> LADDER_ITEMS=new java.util.ArrayList<>();
    public static Item BUILDER_TOOL;
    private static boolean initialized;
    private PrototypeArchitecture() {}
    public static void initialize() {
        if (initialized) return;
        LADDER = Registry.register(Registries.BLOCK, id("prototype_ladder"), new PrototypeLadderBlock(AbstractBlock.Settings.create()
                .strength(.4F).sounds(BlockSoundGroup.LADDER).nonOpaque().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));
        LADDER_ITEM = Registry.register(Registries.ITEM, id("prototype_ladder"), new PrototypeLadderItem(LADDER, new Item.Settings()));
        LADDERS.add(LADDER);LADDER_ITEMS.add(LADDER_ITEM);
        for(int art=1;art<=2;art++){
            PrototypeLadderBlock block=Registry.register(Registries.BLOCK,id("prototype_ladder_art_"+art),new PrototypeLadderBlock(AbstractBlock.Settings.create()
                    .strength(.4F).sounds(BlockSoundGroup.LADDER).nonOpaque().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK),art));
            LADDERS.add(block);LADDER_ITEMS.add(Registry.register(Registries.ITEM,id("prototype_ladder_art_"+art),new PrototypeLadderItem(block,new Item.Settings())));
        }
        SourceLadderRuntime.initialize();
        dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.initialize();
        dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureMetrics.initialize();
        dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.registerMetrics("composite",world->CompositeRuntime.diagnosticsMetrics(world));
        dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.registerMetrics("native_architecture",dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureMetrics::loadedNative);
        dev.dreamwalker.bloodbornedw.tool.BuilderServer.initialize();
        dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.registerMetrics("mechanism_rules",world->dev.dreamwalker.bloodbornedw.link.MechanismRules.metrics(world.getServer()));
        // 90008 remains registered solely to deserialize old saves.  It is not a
        // second offered tool: LegacyBuildingTool immediately upgrades stacks to
        // the canonical 90009 item while preserving the per-stack menu state.
        BUILDER_TOOL = Registry.register(Registries.ITEM, id("builder_tool"), new LegacyBuildingTool());
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Loaded legacy drops migrate without a chunk scan.  Container
            // stacks remain readable under their old id and are converted once
            // transferred to a player's inventory (inventoryTick above).
            if (entity instanceof net.minecraft.entity.ItemEntity drop
                    && drop.getStack().getItem() instanceof LegacyBuildingTool)
                drop.setStack(LegacyBuildingTool.canonical(drop.getStack()));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (String alias : List.of("bb", "bloodborne")) dispatcher.register(literal(alias).requires(source -> source.hasPermissionLevel(2))
                    .then(literal("rotate").executes(context -> targetCommand(context.getSource(), "rotate")))
                    .then(literal("debug").executes(context -> targetCommand(context.getSource(), "debug"))
                            .then(net.minecraft.server.command.CommandManager.argument("note",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context->{DebugCatalogue.debug(context.getSource());return dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.snapshotSelected(context.getSource(),com.mojang.brigadier.arguments.StringArgumentType.getString(context,"note"));})))
                    .then(literal("visual")
                            .then(literal("get").executes(context -> targetCommand(context.getSource(), "get")))
                            .then(literal("base").executes(context -> targetCommand(context.getSource(), "base")))
                            .then(literal("alt").executes(context -> targetCommand(context.getSource(), "alt")))
                            .then(literal("toggle").executes(context -> targetCommand(context.getSource(), "toggle"))))
                    .then(dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.commandTree()));
        });
        initialized = true;
    }
    public static void initializeClient() { PrototypeArchitectureClient.initialize(); }
    public static Identifier id(String path) { return new Identifier("bloodborne_dw", path); }
    public static PrototypeLadderItem ladderItem(int art){return LADDER_ITEMS.get(Math.max(0,Math.min(2,art)));}
    public static PrototypeLadderBlock ladderBlock(int art){return LADDERS.get(Math.max(0,Math.min(2,art)));}

    private static int targetCommand(ServerCommandSource source, String operation) {
        if(operation.equals("debug")){int result=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.debug(source);dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.snapshotSelected(source,"/bb debug");return result;}
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) { source.sendError(Text.literal("Нужен игрок и объект под прицелом.")); return 0; }
        HitResult ray = player.raycast(6, 0, false);
        if (!(ray instanceof BlockHitResult hit) || ray.getType() != HitResult.Type.BLOCK || !player.getWorld().isChunkLoaded(hit.getBlockPos())) return 0;
        BlockPos sourceLadderRoot = SourceLadderRuntime.resolveRoot(player.getWorld(), hit.getBlockPos());
        BlockPos pos = sourceLadderRoot != null ? sourceLadderRoot : hit.getBlockPos();
        BlockState state = player.getWorld().getBlockState(pos);
        Owner owner = CompositeRuntime.target(player.getWorld(), pos, player);
        if (owner != null) {
            BlockPos root = CompositeData.pos(owner.root()); BlockState rootState = player.getWorld().getBlockState(root);
            if (!(rootState.getBlock() instanceof CompositeRootBlock block)) return 0;
            if (operation.equals("get") || operation.equals("debug")) {
                source.sendFeedback(() -> Text.literal(owner.registryId() + "; final ID: PENDING; root=" + root.toShortString()
                        + "; instance=" + owner.instanceId() + "; yaw=" + rootState.get(CompositeRootBlock.ROTATION) * 45
                        + "; variant=" + rootState.get(CompositeRootBlock.VARIANT) + "; open=" + rootState.get(CompositeRootBlock.OPEN)
                        + "; selected=" + rootState.get(CompositeRootBlock.PROFILE).asString()
                        + "; ALT fallback: BASE wrapper; essential cells=" + block.spec.essential
                        + "; collision and selection are independent; source transforms=" + block.spec.pose(rootState).parts()), false);
                return 1;
            }
            BlockState next = compositeNext(rootState, operation);
            boolean changed = CompositeRuntime.transition(player.getServerWorld(), owner, next, player).outcome() == TransactionCore.Outcome.COMMITTED;
            if (!changed) source.sendError(Text.literal("Изменение отклонено: опора, занятая основная ячейка или права."));
            return changed ? 1 : 0;
        }
        if (PrototypeWallArchitecture.isWall(state)) {
            if (operation.equals("get") || operation.equals("debug")) {
                source.sendFeedback(() -> Text.literal(dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.prefix(state)+" "+Registries.BLOCK.getId(state.getBlock())+"; final ID: PENDING; root=" + pos.toShortString() + "; " + state), false);
                return 1;
            }
            BlockState next = operation.equals("rotate") ? PrototypeWallArchitecture.WALL.rotate45(state)
                    : state.with(PrototypeWallBlock.PROFILE, operation.equals("toggle") ? (state.get(PrototypeWallBlock.PROFILE) == PrototypeWallBlock.Profile.BASE
                    ? PrototypeWallBlock.Profile.ALT : PrototypeWallBlock.Profile.BASE) : operation.equals("alt") ? PrototypeWallBlock.Profile.ALT : PrototypeWallBlock.Profile.BASE);
            return PrototypeWallArchitecture.edit(player.getWorld(), pos, state, next, player) ? 1 : 0;
        }
        if (!(state.getBlock() instanceof PrototypeLadderBlock)) { source.sendError(Text.literal("Под прицелом нет строительного прототипа.")); return 0; }
        if (operation.equals("get") || operation.equals("debug")) {
            String ownership = player.getWorld().getBlockEntity(pos) instanceof SourceLadderBlockEntity installation
                    ? "; source installation; instance=" + installation.owner() + "; fixed backing=" + installation.backing().toShortString()
                        + "; owned cells=[root, fixed backing]; physical section plus retained source cube"
                        + "; source art correction=180 degrees and rotated anchor shift"
                    : "; mask=[0,0,0]; helper=none";
            source.sendFeedback(() -> Text.literal(dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.prefix(state)+" "+Registries.BLOCK.getId(state.getBlock())+"; final ID: PENDING; root=" + pos.toShortString()
                    + "; yaw=" + PrototypeLadderBlock.yaw(state) * 45 + "; art=" + PrototypeLadderBlock.artVariant(state)
                    + "; selected=" + state.get(PrototypeLadderBlock.PROFILE).asString() + "; ALT fallback: BASE wrapper unless an ALT pack overrides it"
                    + "; source=minecraft:beehive[honey_level=1], minecraft:block/hold/wood_ladder_0" + (PrototypeLadderBlock.artVariant(state) + 2)
                    + ownership + "; root collision=" + state.getCollisionShape(player.getWorld(), pos).getBoundingBox()), false);
            return 1;
        }
        BlockState next = operation.equals("rotate") ? LADDER.step45(state)
                : state.with(PrototypeLadderBlock.PROFILE, operation.equals("toggle")
                    ? (state.get(PrototypeLadderBlock.PROFILE) == PrototypeLadderBlock.Profile.BASE ? PrototypeLadderBlock.Profile.ALT : PrototypeLadderBlock.Profile.BASE)
                    : operation.equals("alt") ? PrototypeLadderBlock.Profile.ALT : PrototypeLadderBlock.Profile.BASE);
        boolean changed = edit(player.getWorld(), pos, state, next, player);
        if (!changed) source.sendError(Text.literal("Изменение отклонено: опора, препятствие или права."));
        return changed ? 1 : 0;
    }

    public static boolean edit(World world, BlockPos pos, BlockState before, BlockState next, net.minecraft.entity.player.PlayerEntity player) {
        if(!dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.enabled(world))return editInternal(world,pos,before,next,player);
        long started=dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.begin(world,false);
        try{boolean result=editInternal(world,pos,before,next,player);dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.event(world,next,dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.rootId(pos),pos,
                PrototypeLadderBlock.yaw(before)!=PrototypeLadderBlock.yaw(next)?"rotate":"change_model",dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.state(before),dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.state(world,pos),result?"COMMITTED":"REJECTED",result?"":"native_ladder_edit_rejected; rights_support_obstacle_or_stale_state",player);return result;}
        finally{dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.finish(world,next,pos,"architecture.native_edit",started);}
    }
    private static boolean editInternal(World world, BlockPos pos, BlockState before, BlockState next, net.minecraft.entity.player.PlayerEntity player) {
        if(!BuildPermissions.canEdit(world,player,pos))return false;
        if(!before.isOf(next.getBlock())||!world.isChunkLoaded(pos)||!world.getBlockState(pos).equals(before))return false;
        if(world.getBlockEntity(pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity mounted&&!mounted.contributions().isEmpty())return world.isClient||CompositeRuntime.transition((ServerWorld)world,mounted.resident(),next,player).outcome()==TransactionCore.Outcome.COMMITTED;
        if (before.isOf(LADDER) && before.get(PrototypeLadderBlock.SOURCE_CLONE))
            return SourceLadderRuntime.edit(world, pos, before, next, player);
        if (!(before.getBlock() instanceof PrototypeLadderBlock) || !before.isOf(next.getBlock()) || !world.isChunkLoaded(pos) || !world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)
                || player == null || !player.getAbilities().allowModifyWorld || !world.canPlayerModifyAt(player, pos) || !world.getBlockState(pos).equals(before)) return false;
        boolean rotation = PrototypeLadderBlock.yaw(before) != PrototypeLadderBlock.yaw(next);
        if (rotation) {
            for (var direction : PrototypeLadderBlock.backingDirections(next))
                if (!world.isChunkLoaded(pos.offset(direction))) return false;
            if (!next.canPlaceAt(world, pos)) return false;
            var shape = next.getCollisionShape(world, pos);
            if(PlacementPhysics.conflict(world,shape.getBoundingBoxes().stream().map(box->box.offset(pos)).toList(),null,null,pos)!=null)return false;
        }
        if (world.isClient) return true;
        return before.equals(next) || world.setBlockState(pos, next, rotation ? Block.NOTIFY_ALL : Block.NOTIFY_LISTENERS);
    }

    private static BlockState compositeNext(BlockState state, String operation) {
        return operation.equals("rotate") ? (state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.ThinWindowRootBlock
                ?dev.dreamwalker.bloodbornedw.composite.GlazingTypes.step90(state)
                :state.with(CompositeRootBlock.ROTATION,(state.get(CompositeRootBlock.ROTATION)+1)%8))
                : state.with(CompositeRootBlock.PROFILE, operation.equals("toggle") ? (state.get(CompositeRootBlock.PROFILE) == CompositeRootBlock.Profile.BASE
                ? CompositeRootBlock.Profile.ALT : CompositeRootBlock.Profile.BASE) : operation.equals("alt") ? CompositeRootBlock.Profile.ALT : CompositeRootBlock.Profile.BASE);
    }

    private static final class BuilderTool extends Item {
        private BuilderTool() { super(new Settings().maxCount(1)); }
        @Override public ActionResult useOnBlock(ItemUsageContext context) {
            var player = context.getPlayer(); World world = context.getWorld(); BlockPos pos = context.getBlockPos();
            if (player == null || !world.isChunkLoaded(pos)) return ActionResult.FAIL;
            if (context.getHand() == net.minecraft.util.Hand.OFF_HAND) {
                if (world.isClient && !CompositeRuntime.targets(world, pos, player).isEmpty()) return ActionResult.SUCCESS;
                Owner selected = world.isClient ? null : CompositeRuntime.cycleTarget(world, pos, player);
                if (selected != null) {
                    if (!world.isClient) player.sendMessage(Text.literal(selected.registryId() + "; " + selected.instanceId()), true);
                    return ActionResult.success(world.isClient);
                }
            }
            Owner owner = CompositeRuntime.target(world, pos, player);
            if (owner != null) {
                BlockState state = world.getBlockState(CompositeData.pos(owner.root()));
                if (!(state.getBlock() instanceof CompositeRootBlock)) return ActionResult.FAIL;
                if (world.isClient) return ActionResult.SUCCESS;
                return CompositeRuntime.transition((ServerWorld) world, owner, compositeNext(state, player.isSneaking() ? "toggle" : "rotate"), player)
                        .outcome() == TransactionCore.Outcome.COMMITTED ? ActionResult.CONSUME : ActionResult.FAIL;
            }
            BlockPos sourceLadderRoot = SourceLadderRuntime.resolveRoot(world, pos);
            if (sourceLadderRoot != null) pos = sourceLadderRoot;
            BlockState state = world.getBlockState(pos);
            if (PrototypeWallArchitecture.isWall(state)) {
                BlockState next = player.isSneaking() ? state.cycle(PrototypeWallBlock.PROFILE) : PrototypeWallArchitecture.WALL.rotate45(state);
                return PrototypeWallArchitecture.edit(world, pos, state, next, player) ? ActionResult.success(world.isClient) : ActionResult.FAIL;
            }
            if (!(state.getBlock() instanceof PrototypeLadderBlock)) return ActionResult.PASS;
            BlockState next = player.isSneaking() ? state.cycle(PrototypeLadderBlock.PROFILE) : LADDER.step45(state);
            return edit(world, pos, state, next, player) ? ActionResult.success(world.isClient) : ActionResult.FAIL;
        }
        @Override public Text getName(ItemStack stack) { return Text.literal("Инструмент строителя · прототип"); }
    }
}
