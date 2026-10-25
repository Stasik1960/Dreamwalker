package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Translate old ordinary native items on read, including nested inventories.
 * Caller NBT and retained whole-object items remain untouched. */
@Mixin(ItemStack.class)
public abstract class NativeVisualStackMigrationMixin {
    @ModifyVariable(method="fromNbt",at=@At("HEAD"),argsOnly=true)
    private static NbtCompound dw$oldVisualStack(NbtCompound tag){
        if(tag==null)return null;
        String id=tag.getString("id"),prefix="bloodborne_dw:native_grid_";
        if(!id.startsWith(prefix))return tag;
        int bank,part=0;try{bank=Integer.parseInt(id.substring(prefix.length()));String p=tag.getCompound("tag").getCompound("BlockStateTag").getString("part");if(!p.isEmpty())part=Integer.parseInt(p);}catch(NumberFormatException malformed){return tag;}
        int visual=bank*128+part;if(bank<0||part<0||part>127||visual<0||visual>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(visual)<0)return tag;
        var next=tag.copy();int physics=NativeVisualLayout.physics(visual);next.putString("id","bloodborne_dw:native_physics_"+(physics/128));
        var n=next.getCompound("tag");n.putInt(NativeVisuals.ITEM_KEY,visual);var state=n.getCompound("BlockStateTag");state.putString("part",Integer.toString(physics%128));n.put("BlockStateTag",state);next.put("tag",n);return next;
    }
}
