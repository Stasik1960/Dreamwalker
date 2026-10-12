package dev.dreamwalker.bloodbornedw.composite;

import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public final class CompositeItem extends BlockItem {
    private final CompositeRootBlock root;
    public CompositeItem(CompositeRootBlock block){super(block,new Settings());root=block;}
    @Override public ActionResult place(ItemPlacementContext context){
        return placeInternal(context);
    }
    private ActionResult placeInternal(ItemPlacementContext context){
        if(context.getPlayer()==null||!context.getWorld().isChunkLoaded(context.getBlockPos()))return ActionResult.FAIL;
        CompositeRootBlock canonical=GlazingTypes.itemTarget(root,context.getStack());
        if(canonical!=root){
            ItemStack converted=new ItemStack(canonical.asItem(),context.getStack().getCount());
            NbtCompound copied=context.getStack().getNbt();if(copied!=null)converted.setNbt(copied.copy());
            converted.getOrCreateSubNbt("BlockStateTag").putString("variant","0");converted.removeSubNbt("CompositePayload");converted.removeSubNbt("BlockEntityTag");
            ItemPlacementContext redirected=new ItemPlacementContext(context){@Override public ItemStack getStack(){return converted;}};
            ActionResult result=((CompositeItem)canonical.asItem()).place(redirected);
            if(result.isAccepted())context.getStack().decrement(Math.max(0,context.getStack().getCount()-converted.getCount()));
            return result;
        }
        ItemStack stack=context.getStack();NbtCompound art=stack.getSubNbt("BlockStateTag");int variant=-1;
        if(art!=null&&art.contains("variant",8))try{variant=Integer.parseInt(art.getString("variant"));}catch(NumberFormatException ignored){/* Normalize malformed art without rewriting the remaining stack. */}
        if(root instanceof ThinWindowRootBlock||root.spec.id.getPath().equals("prototype_tree")||variant<0||variant>=root.spec.variants.size())variant=0;
        BlockState state=root.getDefaultState().with(CompositeRootBlock.ROTATION,Math.floorMod(Math.round(context.getPlayer().getYaw()/45),8)).with(CompositeRootBlock.VARIANT,variant)
                .with(CompositeRootBlock.PROFILE,art!=null&&"alt".equals(art.getString("profile"))?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE)
                .with(CompositeRootBlock.OPEN,root.spec.openable&&art!=null&&"true".equals(art.getString("open")));
        // Ordinary items carry art only; never restore owner, links, height or source privileges.
        NbtCompound payload=null;
        if(root instanceof ThinWindowRootBlock){state=GlazingMount.placementState(state,context);payload=GlazingMount.placementPayload(context,payload);}
        if(context.getWorld().isClient)return ActionResult.SUCCESS;
        var result=CompositeRuntime.place((ServerWorld)context.getWorld(),context.getBlockPos(),state,UUID.randomUUID(),context.getPlayer(),payload);
        if(result.outcome()!=dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED){context.getPlayer().sendMessage(Text.literal("Установка отклонена: "+result.reason()),true);return ActionResult.FAIL;}
        if(!context.getPlayer().getAbilities().creativeMode)stack.decrement(1);
        return ActionResult.CONSUME;
    }
    @Override public Text getName(ItemStack stack){if(root instanceof ThinWindowRootBlock){var selected=GlazingTypes.itemTarget(root,stack);return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.name(selected.spec.id,Text.literal(selected.spec.displayName));}NbtCompound art=stack.getSubNbt("BlockStateTag");String v=art==null?"0":art.getString("variant");return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.name(root.spec.id,Text.literal(root.spec.displayName+(root.spec.variants.size()>1?" · вариант "+v:"")));}
    @Override public void appendTooltip(ItemStack stack,net.minecraft.world.World world,java.util.List<Text> tooltip,net.minecraft.client.item.TooltipContext context){
        dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.appendTooltip(root instanceof ThinWindowRootBlock?GlazingTypes.itemTarget(root,stack).spec.id:root.spec.id,tooltip);
        if(root instanceof ThinWindowRootBlock)tooltip.add(Text.literal("Стена: ПКМ сбоку; пол/потолок: сверху/снизу; Shift+пол: вертикально"));
    }
}
