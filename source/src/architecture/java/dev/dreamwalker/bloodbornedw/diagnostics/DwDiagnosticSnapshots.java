package dev.dreamwalker.bloodbornedw.diagnostics;

import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornedw.debug.*;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;

/** Read only, only already-loaded targets, with explicit fragment truncation. */
final class DwDiagnosticSnapshots {
    private DwDiagnosticSnapshots(){}
    static Map<String,Object> block(ServerWorld world,PlayerEntity player,BlockPos hit){
        Map<String,Object> out=new LinkedHashMap<>();out.put("side","server");out.put("dimension",world.getRegistryKey().getValue().toString());out.put("hit",DwDiagnostics.position(hit));out.put("fullNbt","NOT_CAPTURED");
        if(hit==null||!world.isChunkLoaded(hit)){out.put("result","NOT_MEASURED_UNLOADED_TARGET");return out;}
        var owner=player==null?CompositeRuntime.soleNativeOwnerReadOnly(world,hit):CompositeRuntime.targetReadOnly(world,hit,player);
        BlockPos root=owner==null?hit:CompositeData.pos(owner.root());BlockPos source=SourceLadderRuntime.resolveRoot(world,hit);if(source!=null&&owner==null)root=source;
        if(!world.isChunkLoaded(root)){out.put("result","NOT_MEASURED_UNLOADED_ROOT");return out;}
        BlockState state=world.getBlockState(root);out.put("root",DwDiagnostics.position(root));out.put("registry",Registries.BLOCK.getId(state.getBlock()).toString());var entry=DebugCatalogue.entry(state);out.put("typeId",entry==null?"UNASSIGNED":entry.temporaryId());out.put("states",properties(state));out.put("heldItem",player==null?"NO_PLAYER":Registries.ITEM.getId(player.getMainHandStack().getItem()).toString());out.put("heldTypeId",player==null?"NO_PLAYER":type(player.getMainHandStack()));
        if(owner==null)owner=VerticalMount.owner(world,root);
        if(owner!=null&&VerticalMount.isArchitecture(state)&&world.getBlockEntity(root) instanceof CompositeBlockEntity be){
            CompositeRootBlock block=state.getBlock() instanceof CompositeRootBlock c?c:null;
            var ref=new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),root.asLong(),owner.registryId());out.put("leversOnly",ObjectPolicies.leversOnly(world.getServer(),ref));out.put("pendingMechanismEffect",MechanismLinks.pending(world.getServer(),ref));
            out.put("instanceId",owner.instanceId().toString());var payload=be.payload();Map<String,Object> offsets=new LinkedHashMap<>();for(String key:List.of("MountY","GlazingSurfaceY",VerticalMount.KEY))if(payload.contains(key))offsets.put(key,payload.getDouble(key));if(payload.contains("SourceShift",9)){var list=payload.getList("SourceShift",6);List<Double> v=new ArrayList<>();for(int n=0;n<Math.min(3,list.size());n++)v.add(list.getDouble(n));offsets.put("SourceShift",v);}out.put("offsets",offsets);
            var shift=block==null?new CompositeSourceShift.Shift(0,0,0):CompositeSourceShift.world(state,payload);out.put("rootCellOrigin",List.of(root.getX(),root.getY(),root.getZ()));out.put("visualTransformOrigin",List.of(root.getX()+shift.x(),root.getY()+payload.getDouble("MountY")+payload.getDouble(VerticalMount.KEY)+shift.y(),root.getZ()+shift.z()));out.put("rotationPivot",List.of(root.getX()+.5+shift.x(),root.getY()+payload.getDouble("MountY")+payload.getDouble(VerticalMount.KEY)+shift.y(),root.getZ()+.5+shift.z()));out.put("anchorScope","Root offset summary; mounting/part transforms additionally described by states/modelParts/effective world boxes");
            var instance=CompositeRuntime.instance(world,root,state,owner.instanceId(),payload);int collisions=0,selections=0;List<Map<String,Object>> cells=new ArrayList<>(),physical=new ArrayList<>(),selection=new ArrayList<>();
            for(var cell:instance.cells().entrySet()){collisions+=cell.getValue().collision().size();selections+=cell.getValue().selection().size();if(cells.size()<64)cells.add(Map.of("relative",List.of(cell.getKey().x(),cell.getKey().y(),cell.getKey().z()),"collisionFragments",cell.getValue().collision().size(),"selectionFragments",cell.getValue().selection().size(),"canonicalEssential",(block==null?cell.getKey().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN):block.spec.essential.contains(cell.getKey()))));for(var box:cell.getValue().collision())if(physical.size()<32)physical.add(fragment(root,cell.getKey(),box));for(var box:cell.getValue().selection())if(selection.size()<32)selection.add(fragment(root,cell.getKey(),box));}
            BlockPos fixedRoot=root;out.put("ownedCells",instance.cells().size());out.put("helperCells",instance.cells().keySet().stream().map(c->fixedRoot.add(c.x(),c.y(),c.z())).filter(world::isChunkLoaded).filter(c->world.getBlockState(c).isOf(CompositeArchitecture.CELL)).count());out.put("cellSummary",cells);out.put("cellSummaryTruncated",instance.cells().size()>64);out.put("collisionFragments",collisions);out.put("selectionFragments",selections);
            out.put("collision",Map.of("count",collisions,"retained",physical.size(),"truncated",collisions>physical.size(),"units","blocks","fragments",physical));out.put("selection",Map.of("count",selections,"retained",selection.size(),"truncated",selections>selection.size(),"units","blocks","fragments",selection));
            if(block!=null){var pose=block.spec.pose(state);List<String> models=pose.parts().stream().map(part->part.toString()).limit(32).toList();out.put("modelParts",models);}else{out.put("modelParts",List.of(Registries.BLOCK.getId(state.getBlock()).toString()));if(be instanceof SourceLadderBlockEntity sourceBe){out.put("sourceBacking",DwDiagnostics.position(sourceBe.backing()));out.put("sourceClone",state.contains(dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock.SOURCE_CLONE)&&state.get(dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock.SOURCE_CLONE));}}out.put("modelScope","SERVER_DESCRIPTOR_ONLY; actual rendered model is client telemetry");
        }else{
            out.put("instanceId","root:"+root.getX()+","+root.getY()+","+root.getZ());out.put("collision",boxes(state.getCollisionShape(world,root).getBoundingBoxes()));out.put("selection",boxes(state.getOutlineShape(world,root).getBoundingBoxes()));out.put("helperCells",0);
            if(world.getBlockEntity(root) instanceof SourceLadderBlockEntity sourceBe){out.put("instanceId",sourceBe.owner().toString());out.put("sourceBacking",DwDiagnostics.position(sourceBe.backing()));out.put("sourceClone",true);}
            out.put("modelScope","SERVER_BLOCKSTATE_ONLY; actual rendered model is client telemetry");
        }
        out.put("result","SNAPSHOT_READ_ONLY");return out;
    }
    static Map<String,Object> entity(ServerWorld world,PlayerEntity player,RpObjectEntity target){
        Map<String,Object> out=new LinkedHashMap<>();var id=Registries.ENTITY_TYPE.getId(target.getType());var entry=DebugCatalogue.entry(id);out.put("typeId",entry==null?"UNASSIGNED":entry.temporaryId());out.put("registry",id.toString());out.put("instanceId",target.getUuid().toString());out.put("dimension",world.getRegistryKey().getValue().toString());out.put("root",DwDiagnostics.position(target.getBlockPos()));out.put("anchor",List.of(target.getX(),target.getY(),target.getZ()));out.put("verticalOffset",target.verticalOffset());out.put("installationAnchor",List.of(target.getX(),target.getY()-target.verticalOffset(),target.getZ()));out.put("leversOnly",ObjectPolicies.leversOnly(world.getServer(),MechanismLinks.TargetRef.rp(target)));out.put("pendingMechanismEffect",MechanismLinks.pending(world.getServer(),MechanismLinks.TargetRef.rp(target)));out.put("yaw",target.getYaw());out.put("pitch",target.getPitch());out.put("model",target.assetId());out.put("modelScope","SERVER_ACCEPTED_ASSET; actual selected client model is separate telemetry");out.put("states",Map.of("open",target.isOpen(),"locked",target.isLocked(),"scale",target.objectScale(),"dogsVisible",target.dogsVisible(),"pulseOnly",target.isPulseOnlyMechanism(),"woodPulseTicks",target.woodGatePulseTicks()));out.put("selection",boxes(target.selectionBoxes()));out.put("collision",boxes(target.activePhysicalBoxes()));out.put("isCollidable",target.isCollidable());out.put("helperCells",0);out.put("heldItem",player==null?"NO_PLAYER":Registries.ITEM.getId(player.getMainHandStack().getItem()).toString());out.put("heldTypeId",player==null?"NO_PLAYER":type(player.getMainHandStack()));out.put("fullNbt","NOT_CAPTURED");out.put("result","SNAPSHOT_READ_ONLY");return out;
    }
    static String type(net.minecraft.item.ItemStack stack){var entry=DebugCatalogue.entry(stack);return entry==null?"UNASSIGNED":entry.temporaryId();}
    static Map<String,String> properties(BlockState state){Map<String,String> out=new LinkedHashMap<>();state.getEntries().forEach((p,v)->out.put(p.getName(),v.toString()));return out;}
    static Map<String,Object> boxes(List<Box> boxes){return Map.of("count",boxes.size(),"retained",Math.min(32,boxes.size()),"truncated",boxes.size()>32,"units","blocks","boxes",boxes.stream().limit(32).map(b->List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList());}
    private static Map<String,Object> fragment(BlockPos root,dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell cell,dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box box){return Map.of("relativeCell",List.of(cell.x(),cell.y(),cell.z()),"local",List.of(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()),"world",List.of(root.getX()+cell.x()+box.minX(),root.getY()+cell.y()+box.minY(),root.getZ()+cell.z()+box.minZ(),root.getX()+cell.x()+box.maxX(),root.getY()+cell.y()+box.maxY(),root.getZ()+cell.z()+box.maxZ()));}
    static Map<String,Object> selected(ServerCommandSource source,String note){
        if(!(source.getEntity() instanceof ServerPlayerEntity player))return Map.of();
        HitResult ray=player.raycast(6,0,false);boolean block=ray instanceof BlockHitResult&&ray.getType()==HitResult.Type.BLOCK;var entity=CatalogueDebug.entityTarget(player,block?ray.getPos().squaredDistanceTo(player.getEyePos()):36);
        if(entity instanceof RpObjectEntity rp)return DwDiagnostics.snapshot(player.getServerWorld(),player,rp,Map.of("note",note));if(block)return DwDiagnostics.snapshot(player.getServerWorld(),player,((BlockHitResult)ray).getBlockPos(),Map.of("note",note));return Map.of();
    }
    static int command(ServerCommandSource source,String note){
        Map<String,Object> snapshot=selected(source,note);if(snapshot.isEmpty()){source.sendError(Text.literal("Снимок требует игрока и загруженный объект в пределах6 блоков."));return 0;}
        source.sendFeedback(()->Text.literal("Диагностический снимок: "+snapshot.get("typeId")+" · "+snapshot.get("instanceId")+" · "+snapshot.get("result")+". /bb diagnostics export сохраняет ZIP."),false);return 1;
    }
}
