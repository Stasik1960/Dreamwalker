package dev.dreamwalker.bloodbornedw.composite;

import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class CompositeItem extends BlockItem {
    private final CompositeRootBlock root;
    public CompositeItem(CompositeRootBlock block){super(block,new Settings());root=block;}
    /** Invoke vanilla placement on this registered item; never allocate an unregistered Item helper. */
    protected final ActionResult placeNative(ItemPlacementContext context){return super.place(context);}
    @Override public ActionResult place(ItemPlacementContext context){
        return dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement.place(context,()->placeInternal(context));
    }
    private ActionResult placeInternal(ItemPlacementContext context){
        if(context.getPlayer()==null||!context.getWorld().isChunkLoaded(context.getBlockPos()))return ActionResult.FAIL;
        if(root.spec.simplePhysicalCollision){
            ItemStack converted=ImportedAssemblyV126.canonicalStack(ImportedTypeAliases.canonicalStack(context.getStack()));
            if(converted!=context.getStack()){
                ItemPlacementContext redirected=new ItemPlacementContext(context){@Override public ItemStack getStack(){return converted;}};
                ActionResult result=((CompositeItem)converted.getItem()).place(redirected);
                if(result.isAccepted())context.getStack().decrement(Math.max(0,context.getStack().getCount()-converted.getCount()));
                return result;
            }
        }
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
        if(root.spec.simplePhysicalCollision){
            if(art!=null){
                try{state=state.with(CompositeRootBlock.ROTATION,Math.floorMod(state.get(CompositeRootBlock.ROTATION)+Integer.parseInt(art.getString("imported_rotation_delta")),8));}catch(NumberFormatException ignored){}
                for(var property:state.getBlock().getStateManager().getProperties())if(!java.util.Set.of("rotation","variant","profile","open").contains(property.getName())&&art.contains(property.getName(),8))state=applyArt(state,property,art.getString(property.getName()));
            }
            if(root instanceof ImportedStairBlock)state=ImportedStairBlock.placementState(state,context);
            if(root instanceof ImportedLadderBlock)state=ImportedLadderBlock.placementState(state,context);
            if(root instanceof ImportedStairBlock&&art!=null&&art.contains("imported_rotation_delta",8)){
                try{state=state.with(CompositeRootBlock.ROTATION,Math.floorMod(Math.round(context.getPlayer().getYaw()/45)+Integer.parseInt(art.getString("imported_rotation_delta")),8));}catch(NumberFormatException ignored){}
                state=ImportedStairShapes.fromVariant(state,variant);
                for(var property:state.getBlock().getStateManager().getProperties())if(property.getName().equals("trim")&&art.contains(property.getName(),8))state=applyArt(state,property,art.getString(property.getName()));
                state=ImportedStairBlock.connected(state.with(ImportedStairBlock.CONNECTIONS,ImportedStairBlock.Connections.AUTO),context.getWorld(),context.getBlockPos());
            }
            payload=ImportedTypeAliases.markPayload(payload);
            if(root instanceof ImportedStairBlock){
                String legacyArt=ImportedTypeAliases.readLegacyStairArt(stack.getNbt()).orElse(null);
                if(legacyArt!=null)payload.putString(ImportedStairLegacyArt.KEY,legacyArt);
            }
            if(stack.getNbt()!=null&&stack.getNbt().contains("ImportedArtShift",9)){
                payload.put("SourceShift",stack.getNbt().get("ImportedArtShift").copy());CompositeSourceShift.local(payload);
            }
        }
        var surface=CompositeSurfacePlacement.align(root.spec,state,payload,context);state=surface.state();payload=surface.payload();
        if(context.getWorld().isClient)return ActionResult.SUCCESS;
        UUID placementId=UUID.randomUUID();final BlockState placedState=state;final NbtCompound placedPayload=payload;
        var result=dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement.forOwner(placementId,()->CompositeRuntime.place((ServerWorld)context.getWorld(),context.getBlockPos(),placedState,placementId,context.getPlayer(),placedPayload));
        if(result.outcome()!=dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED){context.getPlayer().sendMessage(Text.literal("Установка отклонена: "+result.reason()),true);return ActionResult.FAIL;}
        if(!context.getPlayer().getAbilities().creativeMode)stack.decrement(1);
        return ActionResult.CONSUME;
    }
    private static <T extends Comparable<T>> BlockState applyArt(BlockState state,net.minecraft.state.property.Property<T> property,String value){return property.parse(value).map(parsed->state.with(property,parsed)).orElse(state);}
    @Override public Text getName(ItemStack stack){if(root instanceof ThinWindowRootBlock){var selected=GlazingTypes.itemTarget(root,stack);return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.name(selected.spec.id,Text.literal(selected.spec.displayName));}NbtCompound art=stack.getSubNbt("BlockStateTag");String v=art==null?"0":art.getString("variant");
        if(root.spec.id.getPath().startsWith("source_")){int variant=0;try{variant=Integer.parseInt(v);}catch(NumberFormatException ignored){}
            if(root.spec.simplePhysicalCollision&&art!=null){
                if(root instanceof ImportedArtworkBlock){try{int bank=Integer.parseInt(art.getString("art_bank"));if(bank>=0&&bank*16<root.spec.variants.size())variant+=bank*16;}catch(NumberFormatException ignored){}}
                else if(root instanceof ImportedStairBlock&&root.getDefaultState().contains(ImportedStairBlock.TRIM)&&"true".equals(art.getString("trim")))variant+=10;
            }
            String name=dev.dreamwalker.bloodbornedw.architecture.RemainingArchitecture.variantName(root.spec.id.getPath(),variant);if(name!=null){if(root.spec.id.getPath().startsWith("source_assembly_")&&!name.startsWith(root.spec.displayName))name=root.spec.displayName+" · "+name;return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.name(root.spec.id,Text.literal(name));}}
        return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.name(root.spec.id,Text.literal(root.spec.displayName+(root.spec.variants.size()>1?" · вариант "+v:"")));}
    @Override public void appendTooltip(ItemStack stack,net.minecraft.world.World world,java.util.List<Text> tooltip,net.minecraft.client.item.TooltipContext context){
        dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.appendTooltip(root instanceof ThinWindowRootBlock?GlazingTypes.itemTarget(root,stack).spec.id:root.spec.id,tooltip);
        if(root instanceof ThinWindowRootBlock)tooltip.add(Text.literal("Стена: ПКМ сбоку; пол/потолок: сверху/снизу; Shift+пол: вертикально"));
        if(root.spec.simplePhysicalCollision&&root.spec.variants.size()>1&&!(root instanceof ImportedStairBlock))tooltip.add(Text.literal("Shift+ПКМ пустой рукой: следующий вид / монтаж"));
    }
}
