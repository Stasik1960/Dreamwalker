package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.parser.DefaultBlockParser;
import com.sk89q.worldedit.extension.input.*;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** The default hand parser otherwise accepts a bare physical state first. */
@Mixin(value=DefaultBlockParser.class,remap=false)
public abstract class WorldEditHandMixin {
 @Inject(method="parseFromInput(Ljava/lang/String;Lcom/sk89q/worldedit/extension/input/ParserContext;)Lcom/sk89q/worldedit/world/block/BaseBlock;",at=@At("HEAD"),cancellable=true)
 private void dw$handArtwork(String input,ParserContext context,CallbackInfoReturnable<BaseBlock> ci)throws InputParseException{
  if(!input.equalsIgnoreCase("hand")&&!input.equalsIgnoreCase("offhand"))return;
  BaseBlock result=new WorldEditBridge.NumericBlocks(WorldEdit.getInstance()).parseFromInput(input,context);if(result!=null)ci.setReturnValue(result);
 }
}
