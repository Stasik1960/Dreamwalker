package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.*;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ChunkSerializer.class)
public abstract class NativeVisualSerializationMixin {
    @Inject(method="deserialize",at=@At("HEAD"))
    private static void dw$rejectUnmigratedVisuals(ServerWorld world,PointOfInterestStorage poi,ChunkPos pos,NbtCompound nbt,CallbackInfoReturnable<ProtoChunk> ci){
        var sections=nbt.getList("sections",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for(int i=0;i<sections.size();i++){var palette=sections.getCompound(i).getCompound("block_states").getList("palette",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);for(int j=0;j<palette.size();j++){
            var row=palette.getCompound(j);String name=row.getString("Name"),prefix="bloodborne_dw:native_grid_";if(!name.startsWith(prefix))continue;
            int bank=Integer.parseInt(name.substring(prefix.length())),part=Integer.parseInt(row.getCompound("Properties").getString("part"));int visual=bank*128+part;
            if(visual<0||visual>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(visual)>=0)throw new IllegalStateException("This native-grid map needs the visual/physics palette remap before opening: "+pos);
        }}
    }
    @Inject(method="deserialize",at=@At("RETURN"))
    private static void dw$readVisuals(ServerWorld world,PointOfInterestStorage poi,ChunkPos pos,NbtCompound nbt,CallbackInfoReturnable<ProtoChunk> ci){var chunk=NativeVisuals.unwrap(ci.getReturnValue());NativeVisuals.data(chunk,NativeVisualData.read(nbt.getCompound(NativeVisualData.KEY),chunk));dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.data(chunk,dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.read(nbt.getCompound(dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.KEY),chunk));}
    @Inject(method="serialize",at=@At("RETURN"))
    private static void dw$writeVisuals(ServerWorld world,Chunk chunk,CallbackInfoReturnable<NbtCompound> ci){var data=NativeVisuals.data(chunk);if(!data.empty()){data.validate(NativeVisuals.unwrap(chunk));ci.getReturnValue().put(NativeVisualData.KEY,data.nbt());}var faux=dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.data(chunk);if(!faux.empty())ci.getReturnValue().put(dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.KEY,faux.nbt());}
}
