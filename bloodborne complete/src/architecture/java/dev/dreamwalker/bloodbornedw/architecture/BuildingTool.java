package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.architecture.wall.*;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.link.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One offered builder; the historical registry name exists only for saved-stack compatibility. */
public final class BuildingTool extends Item {
    public enum Action { ROTATE("поворот"), VARIANT("исполнение: отдельный предмет"), PROFILE("оформление BASE/ALT"), POSE("положение панелей"), MOUNT("монтаж"), LINK("связать рычаг → объект"), CONNECTIONS("показать связи"), UNLINK("разорвать связь"), CANCEL("отменить выбор рычага"), DOGS("собака в клетке: показать/скрыть"), DIAGNOSTICS("диагностический снимок"), SELECT("выбрать / осмотреть"), UP("поднять"), DOWN("опустить"), RULE_SOURCE("выбрать источники"), RULE_TARGET("добавить цели"), LAMP_SOURCE("выбрать исходный фонарь"), LAMP_TARGET("добавить фонарь назначения");
        public final String label; Action(String label){this.label=label;} }
    public BuildingTool(){super(new Settings().maxCount(1));}
    // Frozen saved v10 ordinal contract. Menu ordering must not change this table.
    private static final Action[] LEGACY_ACTIONS={Action.ROTATE,Action.VARIANT,Action.PROFILE,Action.POSE,Action.MOUNT,Action.LINK,Action.CONNECTIONS,Action.UNLINK,Action.CANCEL,Action.DOGS,Action.DIAGNOSTICS,Action.SELECT,Action.UP,Action.DOWN,Action.RULE_SOURCE,Action.RULE_TARGET,Action.LAMP_SOURCE,Action.LAMP_TARGET};
    public static Action legacyAction(int ordinal){return ordinal>=0&&ordinal<LEGACY_ACTIONS.length?LEGACY_ACTIONS[ordinal]:Action.SELECT;}
    public static Action action(ItemStack stack){
        if(!stack.hasNbt())return Action.SELECT;var tag=stack.getNbt();
        if(tag.contains("BuilderActionName",net.minecraft.nbt.NbtElement.STRING_TYPE))try{return Action.valueOf(tag.getString("BuilderActionName"));}catch(IllegalArgumentException ignored){/* Read the historical ordinal if a future named value is unavailable. */}
        return tag.contains("BuilderAction",net.minecraft.nbt.NbtElement.NUMBER_TYPE)?legacyAction(tag.getInt("BuilderAction")):Action.SELECT;
    }
    public static void setAction(ItemStack stack,Action selected){var tag=stack.getOrCreateNbt();tag.putString("BuilderActionName",selected.name());tag.putInt("BuilderAction",selected.ordinal());}
    public static boolean supports(BlockState state,Action operation){
        if(state.getBlock() instanceof CompositeRootBlock block)return switch(operation){case ROTATE,UP,DOWN,SELECT,DIAGNOSTICS->true;case PROFILE->hasBuiltInAlternateVisual(state);case POSE->block.spec.openable;case MOUNT->state.contains(ThinWindowRootBlock.MOUNT);case LINK,UNLINK,RULE_TARGET->block.spec.openable;case CONNECTIONS->true;default->false;};
        if(PrototypeWallArchitecture.isWall(state)||state.getBlock() instanceof PrototypeLadderBlock)return (operation==Action.PROFILE?hasBuiltInAlternateVisual(state):java.util.Set.of(Action.ROTATE,Action.UP,Action.DOWN,Action.SELECT,Action.DIAGNOSTICS,Action.CONNECTIONS).contains(operation));
        return false;
    }
    /** Bundled v10 ALT models are parent-only aliases to BASE, including native walls/ladders and tree.
     * Keep PROFILE state, old ordinal and commands for optional resource overrides, but do not offer
     * an appearance operation that has no visible result in the self-contained distribution. */
    public static boolean hasBuiltInAlternateVisual(BlockState state){return false;}
    public static int rotationStep(BlockState state){return state.getBlock() instanceof ThinWindowRootBlock?90:45;}
    private static <T extends Comparable<T>> BlockState cycle(BlockState state,net.minecraft.state.property.Property<T> property,boolean reverse){
        var values=new java.util.ArrayList<>(property.getValues());int at=values.indexOf(state.get(property));return state.with(property,values.get(Math.floorMod(at+(reverse?-1:1),values.size())));
    }
    private static void migrateHeld(PlayerEntity player,Hand hand){ItemStack before=player.getStackInHand(hand),after=BuilderToolMigration.normalize(before);if(before!=after)player.setStackInHand(hand,after);}

    public static boolean isHeld(PlayerEntity player){return player!=null&&(player.getMainHandStack().getItem() instanceof BuildingTool||player.getOffHandStack().getItem() instanceof BuildingTool);}
    public static boolean mainHeld(PlayerEntity player){return player!=null&&player.getMainHandStack().getItem() instanceof BuildingTool;}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
        migrateHeld(player,hand);ItemStack stack=player.getStackInHand(hand);if(hand!=Hand.MAIN_HAND)return TypedActionResult.fail(stack);
        if(!world.isClient&&player instanceof net.minecraft.server.network.ServerPlayerEntity server)dev.dreamwalker.bloodbornedw.tool.BuilderServer.open(server);
        return TypedActionResult.success(stack,world.isClient);
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context){
        if(context.getPlayer()!=null)migrateHeld(context.getPlayer(),context.getHand());
        if(context.getHand()!=Hand.MAIN_HAND)return ActionResult.FAIL;
        if(!context.getWorld().isClient&&context.getPlayer() instanceof net.minecraft.server.network.ServerPlayerEntity server)dev.dreamwalker.bloodbornedw.tool.BuilderServer.open(server);
        return ActionResult.success(context.getWorld().isClient);
    }
    public static ActionResult applyBlock(PlayerEntity player,BlockPos pos,Action operation){return applyBlock(player,pos,operation,false);}
    public static ActionResult applyBlock(PlayerEntity player,BlockPos pos,Action operation,boolean reverse){
        World world=player.getWorld();if(!BuildPermissions.canEdit(world,player,pos)||!world.isChunkLoaded(pos))return ActionResult.FAIL;
        if(operation==Action.VARIANT){player.sendMessage(Text.literal("Самостоятельный тип выбирается собственным предметом Creative."),true);return ActionResult.FAIL;}
        // A pinned instance is addressed by its validated root, independently of the current aim.
        // Helper-cell clicks still use the existing candidate ray resolution.
        var owner=world.getBlockEntity(pos) instanceof CompositeBlockEntity root&&root.resident()!=null&&CompositeData.pos(root.resident().root()).equals(pos)?root.resident():CompositeRuntime.target(world,pos,player);
        if(owner!=null&&world.getBlockState(CompositeData.pos(owner.root())).getBlock() instanceof CompositeRootBlock){
            BlockPos root=CompositeData.pos(owner.root());BlockState state=world.getBlockState(root);if(!(state.getBlock() instanceof CompositeRootBlock block)||!BuildPermissions.canEdit(world,player,root))return ActionResult.FAIL;
            if(MechanismBuilder.connectionAction(operation))return world.isClient?ActionResult.SUCCESS:MechanismBuilder.architecture(player,player.getMainHandStack(),CompositeMechanismBridge.reference((ServerWorld)world,owner));
            if(operation==Action.POSE&&!block.spec.openable||operation==Action.MOUNT&&!state.contains(ThinWindowRootBlock.MOUNT)||!java.util.Set.of(Action.ROTATE,Action.PROFILE,Action.POSE,Action.MOUNT).contains(operation)){player.sendMessage(Text.literal("Этот объект не поддерживает действие: "+operation.label),true);return ActionResult.FAIL;}
            BlockState next=switch(operation){
                case ROTATE->state.with(CompositeRootBlock.ROTATION,Math.floorMod((block instanceof ThinWindowRootBlock?Math.round(state.get(CompositeRootBlock.ROTATION)/2.0F)*2:state.get(CompositeRootBlock.ROTATION))+(reverse?-1:1)*(block instanceof ThinWindowRootBlock?2:1),8));
                case VARIANT->block instanceof ThinWindowRootBlock?state:state.with(CompositeRootBlock.VARIANT,(state.get(CompositeRootBlock.VARIANT)+1)%block.spec.variants.size());
                case PROFILE->cycle(state,CompositeRootBlock.PROFILE,reverse);
                case POSE->block.spec.openable?cycle(state,CompositeRootBlock.OPEN,reverse):state;
                case MOUNT->state.contains(ThinWindowRootBlock.MOUNT)?cycle(state,ThinWindowRootBlock.MOUNT,reverse):state;
                default->state;
            };
            if(world.isClient)return ActionResult.SUCCESS;
            var result=CompositeRuntime.transition((ServerWorld)world,owner,next,player);
            if(result.outcome()!=TransactionCore.Outcome.COMMITTED)player.sendMessage(Text.literal("Изменение отклонено: "+result.reason()),true);
            return result.outcome()==TransactionCore.Outcome.COMMITTED?ActionResult.CONSUME:ActionResult.FAIL;
        }
        BlockPos sourceRoot=SourceLadderRuntime.resolveRoot(world,pos);if(sourceRoot!=null)pos=sourceRoot;
        BlockState state=world.getBlockState(pos);
        if(PrototypeWallArchitecture.isWall(state)){
            if(operation!=Action.ROTATE&&operation!=Action.PROFILE){player.sendMessage(Text.literal("Эта ограда не поддерживает действие: "+operation.label),true);return ActionResult.FAIL;}
            BlockState next=switch(operation){case ROTATE->state.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.ROTATION,Math.floorMod(state.get(PrototypeWallBlock.ROTATION)+(reverse?-1:1),8));case VARIANT->state.cycle(PrototypeWallBlock.MATERIAL);case PROFILE->cycle(state,PrototypeWallBlock.PROFILE,reverse);default->state;};
            return PrototypeWallArchitecture.edit(world,pos,state,next,player)?ActionResult.success(world.isClient):ActionResult.FAIL;
        }
        if(state.getBlock() instanceof PrototypeLadderBlock ladder){
            if(operation!=Action.ROTATE&&operation!=Action.PROFILE){player.sendMessage(Text.literal("Эта лестница не поддерживает действие: "+operation.label),true);return ActionResult.FAIL;}
            BlockState next=switch(operation){case ROTATE->PrototypeLadderBlock.withYaw(state,PrototypeLadderBlock.yaw(state)+(reverse?-1:1));case VARIANT->state.cycle(PrototypeLadderBlock.VARIANT);case PROFILE->cycle(state,PrototypeLadderBlock.PROFILE,reverse);default->state;};
            return PrototypeArchitecture.edit(world,pos,state,next,player)?ActionResult.success(world.isClient):ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }
    @Override public Text getName(ItemStack stack){return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.itemName(stack,Text.literal("Инструмент настройки объектов"));}
    @Override public void appendTooltip(ItemStack stack,World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context){
        lines.add(Text.literal("ЛКМ: выбор / действие · Shift+ЛКМ: обратный шаг · ПКМ: настройки"));
        lines.add(Text.literal("Действие: "+action(stack).label+". Только основная рука."));
        lines.add(Text.literal("Ctrl+колесо: действие · Alt+колесо: шаг высоты · Ctrl+Z: отмена"));
        lines.add(Text.literal("Инструмент в любой руке запрещает обычное разрушение."));
        dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.itemTooltip(stack,lines);
    }
}
