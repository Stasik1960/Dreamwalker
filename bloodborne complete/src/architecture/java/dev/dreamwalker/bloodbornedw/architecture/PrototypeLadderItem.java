package dev.dreamwalker.bloodbornedw.architecture;

import java.util.List;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Freeze the art variant once per new placement; failed placement never changes the source stack. */
public final class PrototypeLadderItem extends BlockItem {
    public PrototypeLadderItem(PrototypeLadderBlock block, Settings settings) { super(block, settings); }
    @Override public ActionResult place(ItemPlacementContext context) {
        if(context.getPlayer()!=null&&(!context.getPlayer().getAbilities().allowModifyWorld||!context.getWorld().canPlayerModifyAt(context.getPlayer(),context.getBlockPos())))
            return fail(context,"нет права изменять этот участок");
        if(!context.getWorld().isChunkLoaded(context.getBlockPos()))return fail(context,"участок не загружен");
        if(!context.canPlace())return fail(context,"место установки занято");
        ItemStack working = context.getStack().copy();
        NbtCompound stateTag = working.getOrCreateSubNbt("BlockStateTag");
        if (!validVariant(stateTag)) stateTag.putString("variant", Integer.toString(Math.max(0,((PrototypeLadderBlock)getBlock()).fixedVariant())));
        stateTag.putString("variant","0");
        if(getBlock()!=PrototypeArchitecture.ladderItem(0).getBlock()) {
            ItemStack canonical=new ItemStack(PrototypeArchitecture.ladderItem(0),working.getCount());
            canonical.setNbt(working.getNbt().copy());
            ItemPlacementContext redirected=new PreparedContext(context,canonical);
            ActionResult result=((PrototypeLadderItem)canonical.getItem()).place(redirected);
            if(result.isAccepted())context.getStack().decrement(Math.max(0,context.getStack().getCount()-canonical.getCount()));
            return result;
        }
        if (!stateTag.contains("profile", 8) || !List.of("base", "alt").contains(stateTag.getString("profile"))) stateTag.putString("profile", "base");
        // Placement-facing and waterlogging are always taken from the new world location.
        stateTag.remove("facing"); stateTag.remove("diagonal"); stateTag.remove("waterlogged");stateTag.remove("freestanding"); stateTag.putString("source_clone","false");
        working.removeSubNbt("BlockEntityTag"); working.removeSubNbt("SourceLadder");
        PreparedContext prepared = new PreparedContext(context,working);
        ActionResult result = super.place(prepared);
        if (result.isAccepted()) context.getStack().decrement(Math.max(0, context.getStack().getCount() - working.getCount()));
        else if(context.getPlayer()!=null&&!context.getWorld().isClient)context.getPlayer().sendMessage(Text.literal("Лестница не установлена: "+prepared.reason+"."),true);
        return result;
    }
    private static final class PreparedContext extends ItemPlacementContext{
        private final ItemStack working;private String reason="место занято или пересекается с игроком";
        private PreparedContext(ItemPlacementContext context,ItemStack working){super(context);this.working=working;}
        // ItemPlacementContext's constructor asks the clicked block whether it
        // can be replaced. SlabBlock/replaceable AIR call getStack at that point,
        // before this subclass field has been initialized.
        @Override public ItemStack getStack(){return working==null?super.getStack():working;}
    }
    private static ActionResult fail(ItemPlacementContext context,String reason){
        if(context.getPlayer()!=null&&!context.getWorld().isClient)context.getPlayer().sendMessage(Text.literal("Лестница не установлена: "+reason+"."),true);
        return ActionResult.FAIL;
    }
    private static void reason(ItemPlacementContext context,String reason){if(context instanceof PreparedContext prepared)prepared.reason=reason;}
    @Override protected BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        if (!context.getWorld().isChunkLoaded(pos)){reason(context,"участок не загружен");return null;}
        BlockState state = getBlock().getPlacementState(context);
        if (state == null){reason(context,context.getSide()==net.minecraft.util.math.Direction.UP?"основание должно касаться нижнего края секции на границе блока; подходят верхняя плита и предыдущая секция":"нужна боковая опора с настоящей контактной площадкой; для диагонали нужны две стены угла");return null;}
        state = state.with(PrototypeLadderBlock.VARIANT, variant(context.getStack())).with(PrototypeLadderBlock.PROFILE, profile(context.getStack())).with(PrototypeLadderBlock.SOURCE_CLONE,false);
        BlockPos support = pos.offset(state.get(PrototypeLadderBlock.FACING).getOpposite());
        if (!state.get(PrototypeLadderBlock.FREESTANDING)&&!context.getWorld().isChunkLoaded(support) || !state.canPlaceAt(context.getWorld(), pos)){reason(context,"боковая опора не касается места крепления");return null;}
        if (PlacementPhysics.entityConflict(context.getWorld(),state.getCollisionShape(context.getWorld(),pos).getBoundingBoxes().stream().map(box->box.offset(pos)).toList(),null,false)!=null){reason(context,"в месте лестницы находится игрок или другое существо");return null;}
        if(!canPlace(context,state)){reason(context,"место установки занято или не разрешено");return null;}
        String conflict=PlacementPhysics.ordinaryPlacementConflict(context.getWorld(),state.getCollisionShape(context.getWorld(),pos).getBoundingBoxes().stream().map(b->b.offset(pos)).toList(),null);
        if(conflict!=null){reason(context,"твёрдое пересечение: "+conflict);return null;}
        return state;
    }
    public static int variant(ItemStack stack) {
        if(stack.getItem() instanceof PrototypeLadderItem item&&((PrototypeLadderBlock)item.getBlock()).fixedVariant()>=0)return ((PrototypeLadderBlock)item.getBlock()).fixedVariant();
        NbtCompound tag = stack.getSubNbt("BlockStateTag");
        return tag != null && validVariant(tag) ? Integer.parseInt(tag.getString("variant")) : 0;
    }
    private static boolean validVariant(NbtCompound tag) {
        return tag.contains("variant", 8) && List.of("0", "1", "2").contains(tag.getString("variant"));
    }
    public static PrototypeLadderBlock.Profile profile(ItemStack stack) {
        NbtCompound tag = stack.getSubNbt("BlockStateTag");
        return tag != null && "alt".equals(tag.getString("profile")) ? PrototypeLadderBlock.Profile.ALT : PrototypeLadderBlock.Profile.BASE;
    }
    @Override public Text getName(ItemStack stack) { return DebugCatalogue.itemName(stack,Text.translatable("block.bloodborne_dw.prototype_ladder").copy().append(" · " + (variant(stack) + 1))); }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        super.appendTooltip(stack, world, lines, context);
        DebugCatalogue.itemTooltip(stack,lines);
        lines.add(Text.literal("Профиль: " + profile(stack).asString().toUpperCase()).formatted(Formatting.GRAY));
        lines.add(Text.literal("Боковая грань: пристенно/в угол; верхняя: самостоятельная секция и вертикальная сборка.").formatted(Formatting.GRAY));
        lines.add(Text.literal("Диагональ без двух боковых опор имеет коллизию для игрока.").formatted(Formatting.GRAY));
    }
}
