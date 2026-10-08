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

/** Both historical tools share the menu and per-stack action state. */
public final class BuildingTool extends Item {
    public enum Action { ROTATE("поворот"), VARIANT("исполнение: отдельный предмет"), PROFILE("BASE/ALT"), POSE("положение панелей"), MOUNT("режим остекления"), LINK("связать рычаг → объект"), CONNECTIONS("показать связи"), UNLINK("разорвать связь"), CANCEL("отменить выбор рычага"), DOGS("собака в клетке: показать/скрыть"), DIAGNOSTICS("диагностический снимок"), SELECT("выбрать / осмотреть"), UP("поднять"), DOWN("опустить"), RULE_SOURCE("выбрать источники"), RULE_TARGET("добавить цели"), LAMP_SOURCE("выбрать исходный фонарь"), LAMP_TARGET("добавить фонарь назначения");
        public final String label; Action(String label){this.label=label;} }
    public BuildingTool(){super(new Settings().maxCount(1));}
    public static Action action(ItemStack stack){if(!stack.hasNbt()||!stack.getNbt().contains("BuilderAction"))return Action.SELECT;int n=stack.getOrCreateNbt().getInt("BuilderAction");return Action.values()[Math.floorMod(n,Action.values().length)];}
    public static boolean isHeld(PlayerEntity player){return player!=null&&(player.getMainHandStack().getItem() instanceof BuildingTool||player.getOffHandStack().getItem() instanceof BuildingTool);}
    public static boolean mainHeld(PlayerEntity player){return player!=null&&player.getMainHandStack().getItem() instanceof BuildingTool;}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
        ItemStack stack=player.getStackInHand(hand);if(hand!=Hand.MAIN_HAND)return TypedActionResult.fail(stack);
        if(!world.isClient&&player instanceof net.minecraft.server.network.ServerPlayerEntity server)dev.dreamwalker.bloodbornedw.tool.BuilderServer.open(server);
        return TypedActionResult.success(stack,world.isClient);
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context){
        if(context.getHand()!=Hand.MAIN_HAND)return ActionResult.FAIL;
        if(!context.getWorld().isClient&&context.getPlayer() instanceof net.minecraft.server.network.ServerPlayerEntity server)dev.dreamwalker.bloodbornedw.tool.BuilderServer.open(server);
        return ActionResult.success(context.getWorld().isClient);
    }
    public static ActionResult applyBlock(PlayerEntity player,BlockPos pos,Action operation){
        World world=player.getWorld();if(!BuildPermissions.canEdit(world,player,pos)||!world.isChunkLoaded(pos))return ActionResult.FAIL;
        if(operation==Action.VARIANT){player.sendMessage(Text.literal("Самостоятельный тип выбирается собственным предметом Creative."),true);return ActionResult.FAIL;}
        var owner=CompositeRuntime.target(world,pos,player);
        if(owner!=null&&world.getBlockState(CompositeData.pos(owner.root())).getBlock() instanceof CompositeRootBlock){
            BlockPos root=CompositeData.pos(owner.root());BlockState state=world.getBlockState(root);if(!(state.getBlock() instanceof CompositeRootBlock block)||!BuildPermissions.canEdit(world,player,root))return ActionResult.FAIL;
            if(MechanismBuilder.connectionAction(operation))return world.isClient?ActionResult.SUCCESS:MechanismBuilder.architecture(player,player.getMainHandStack(),CompositeMechanismBridge.reference((ServerWorld)world,owner));
            if(operation==Action.POSE&&!block.spec.openable||operation==Action.MOUNT&&!state.contains(ThinWindowRootBlock.MOUNT)||!java.util.Set.of(Action.ROTATE,Action.PROFILE,Action.POSE,Action.MOUNT).contains(operation)){player.sendMessage(Text.literal("Этот объект не поддерживает действие: "+operation.label),true);return ActionResult.FAIL;}
            BlockState next=switch(operation){
                case ROTATE->block instanceof ThinWindowRootBlock?GlazingTypes.step90(state):state.with(CompositeRootBlock.ROTATION,(state.get(CompositeRootBlock.ROTATION)+1)%8);
                case VARIANT->block instanceof ThinWindowRootBlock?state:state.with(CompositeRootBlock.VARIANT,(state.get(CompositeRootBlock.VARIANT)+1)%block.spec.variants.size());
                case PROFILE->state.cycle(CompositeRootBlock.PROFILE);
                case POSE->block.spec.openable?state.cycle(CompositeRootBlock.OPEN):state;
                case MOUNT->state.contains(ThinWindowRootBlock.MOUNT)?state.cycle(ThinWindowRootBlock.MOUNT):state;
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
            BlockState next=switch(operation){case ROTATE->PrototypeWallArchitecture.WALL.rotate45(state);case VARIANT->state.cycle(PrototypeWallBlock.MATERIAL);case PROFILE->state.cycle(PrototypeWallBlock.PROFILE);default->state;};
            return PrototypeWallArchitecture.edit(world,pos,state,next,player)?ActionResult.success(world.isClient):ActionResult.FAIL;
        }
        if(state.getBlock() instanceof PrototypeLadderBlock ladder){
            if(operation!=Action.ROTATE&&operation!=Action.PROFILE){player.sendMessage(Text.literal("Эта лестница не поддерживает действие: "+operation.label),true);return ActionResult.FAIL;}
            BlockState next=switch(operation){case ROTATE->ladder.step45(state);case VARIANT->state.cycle(PrototypeLadderBlock.VARIANT);case PROFILE->state.cycle(PrototypeLadderBlock.PROFILE);default->state;};
            return PrototypeArchitecture.edit(world,pos,state,next,player)?ActionResult.success(world.isClient):ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }
    @Override public Text getName(ItemStack stack){return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.itemName(stack,Text.literal("Строительный инструмент"));}
    @Override public void appendTooltip(ItemStack stack,World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context){
        lines.add(Text.literal("ПКМ: меню · ЛКМ: выбранное действие · Esc: закрыть"));
        lines.add(Text.literal("Действие: "+action(stack).label+". Только основная рука."));
        lines.add(Text.literal("Инструмент в любой руке запрещает обычное разрушение."));
        dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.itemTooltip(stack,lines);
    }
}
