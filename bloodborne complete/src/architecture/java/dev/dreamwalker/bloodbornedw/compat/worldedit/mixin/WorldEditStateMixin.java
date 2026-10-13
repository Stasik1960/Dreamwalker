package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;

import com.google.common.collect.Table;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditStateTransitions;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BlockState.class,remap=false)
public abstract class WorldEditStateMixin {
    @Shadow @Final private BlockType blockType;
    @Shadow @Final private Map<Property<?>,Object> values;
    @Shadow private Table<Property<?>,Object,BlockState> states;
    @Inject(method="populate",at=@At("HEAD"),cancellable=true,remap=false)
    private void bloodborne_dw$sharedWorldEditTransitions(Map<Map<Property<?>,Object>,BlockState> map,CallbackInfo ci){
        if(WorldEditStateTransitions.own(blockType)){states=WorldEditStateTransitions.create(blockType,values,map);ci.cancel();}
    }
}
