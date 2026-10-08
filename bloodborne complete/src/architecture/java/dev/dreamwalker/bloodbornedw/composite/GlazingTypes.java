package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Explicit V8 item compatibility. Installed legacy geometry is never rewritten here. */
public final class GlazingTypes {
    public static final String WINDOW01="prototype_thin_window";
    public static final String WINDOW02="prototype_glass_window_02";
    public static final String WINDOW03="prototype_glass_window_03";
    private GlazingTypes() {}
    public static boolean isGlazingPath(String path) { return WINDOW01.equals(path)||WINDOW02.equals(path)||WINDOW03.equals(path); }
    public static int oldVariant(ItemStack stack) {
        NbtCompound tag=stack.getSubNbt("BlockStateTag");
        if(tag!=null)try{return Integer.parseInt(tag.getString("variant"));}catch(NumberFormatException ignored){}
        return 0;
    }
    public static CompositeRootBlock itemTarget(CompositeRootBlock offered,ItemStack stack) {
        return offered instanceof ThinWindowRootBlock&&WINDOW01.equals(offered.spec.id.getPath())&&(oldVariant(stack)==1||oldVariant(stack)==2)
                ?CompositeArchitecture.kindBlock(oldVariant(stack)==2?WINDOW03:WINDOW02):offered;
    }
    public static CompositeRootBlock itemTarget(BlockState state) {
        CompositeRootBlock block=(CompositeRootBlock)state.getBlock();
        return block instanceof ThinWindowRootBlock&&WINDOW01.equals(block.spec.id.getPath())&&(state.get(CompositeRootBlock.VARIANT)==1||state.get(CompositeRootBlock.VARIANT)==2)
                ?CompositeArchitecture.kindBlock(state.get(CompositeRootBlock.VARIANT)==2?WINDOW03:WINDOW02):block;
    }
    public static int placementRotation(float playerYaw) { return Math.floorMod(Math.round(playerYaw/90)*2,8); }
    public static BlockState step90(BlockState state) {
        int cardinal=Math.floorMod(Math.round(state.get(CompositeRootBlock.ROTATION)/2.0F)*2,8);
        return state.with(CompositeRootBlock.ROTATION,(cardinal+2)%8);
    }
    public static BlockState stepRotation(BlockState state) { return step90(state); }
    public static BlockState normalizePlacement(BlockState state,ItemStack item) {
        return state.with(CompositeRootBlock.VARIANT,0).with(CompositeRootBlock.ROTATION,
                Math.floorMod(Math.round(state.get(CompositeRootBlock.ROTATION)/2.0F)*2,8));
    }
    public static net.minecraft.util.Identifier logicalDebugId(BlockState state) { return itemTarget(state).spec.id; }
    public static ItemStack canonicalArt(BlockState state,NbtCompound payload) { return picked(state,payload); }
    public static boolean legacyAngular(BlockState state) {
        return state.getBlock() instanceof ThinWindowRootBlock&&((state.get(CompositeRootBlock.ROTATION)&1)!=0
                ||WINDOW01.equals(((CompositeRootBlock)state.getBlock()).spec.id.getPath())&&state.get(CompositeRootBlock.VARIANT)==2);
    }
    public static BlockState itemState(CompositeRootBlock offered,ItemStack stack) {
        CompositeRootBlock selected=itemTarget(offered,stack);NbtCompound tag=stack.getSubNbt("BlockStateTag");
        return selected.getDefaultState().with(CompositeRootBlock.VARIANT,0).with(CompositeRootBlock.PROFILE,
                tag!=null&&"alt".equals(tag.getString("profile"))?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE);
    }
    /** No source pose, fixed mount, instance ownership or conversion exception enters a new item. */
    public static ItemStack picked(BlockState legacy,NbtCompound ignoredInstancePayload) {
        CompositeRootBlock target=itemTarget(legacy);ItemStack item=new ItemStack(target.asItem());
        NbtCompound tag=item.getOrCreateSubNbt("BlockStateTag");tag.putString("variant","0");
        tag.putString("profile",legacy.get(CompositeRootBlock.PROFILE).asString());tag.putString("open","false");
        return item;
    }
}
