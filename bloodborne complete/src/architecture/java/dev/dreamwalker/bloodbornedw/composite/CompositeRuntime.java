package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public final class CompositeRuntime {
    private static final ThreadLocal<Integer> WRITING=ThreadLocal.withInitial(()->0);
    private static final ThreadLocal<Boolean> BASE_SHAPE=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<TransactionCore.Result> LAST_RESULT=new ThreadLocal<>();
    private static final Map<ServerWorld,Map<String,Runnable>> PENDING=new WeakHashMap<>();
    private static TransactionCore core;
    public static TransactionCore core(){if(core==null)core=new TransactionCore(new CellSnapshot(Kind.AIR,CompositeData.data(Blocks.AIR.getDefaultState(),new NbtCompound()),null,List.of()),CompositeData.data(CompositeArchitecture.CELL.getDefaultState(),new NbtCompound()),16);return core;}
    public static boolean writing(){return WRITING.get()>0;}
    public static boolean baseShape(){return BASE_SHAPE.get();}
    /** Native collision may call the state's outline; suppress owner selection during that call. */
    public static VoxelShape nativeCollision(BlockView view,BlockPos pos,ShapeContext context){return nativeCollision(view.getBlockState(pos),view,pos,context);}
    /** The state-shape hook already has this carrier. Do not re-read its chunk. */
    public static VoxelShape nativeCollision(BlockState state,BlockView view,BlockPos pos,ShapeContext context){boolean previous=BASE_SHAPE.get();BASE_SHAPE.set(true);try{return state.getBlock().getCollisionShape(state,view,pos,context);}finally{BASE_SHAPE.set(previous);}}
    public static ObjectInstance instance(BlockPos pos,BlockState state,UUID uuid,NbtCompound payload){return instance(null,pos,state,uuid,payload);}
    public static ObjectInstance instance(BlockView world,BlockPos pos,BlockState state,UUID uuid,NbtCompound payload){Owner owner=new Owner(uuid,net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString(),CompositeData.cell(pos));var footprint=state.getBlock() instanceof CompositeRootBlock block?CompositeSourceShift.footprint(block.spec,state,payload):dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.nativeFootprint(world,pos,state,payload);return new ObjectInstance(owner,CompositeData.data(state,payload),footprint);}
    public static TransactionCore.Result place(ServerWorld world,BlockPos pos,BlockState state,UUID uuid,PlayerEntity player){return place(world,pos,state,uuid,player,null);}
    public static TransactionCore.Result place(ServerWorld world,BlockPos pos,BlockState state,UUID uuid,PlayerEntity player,NbtCompound supplied){
        return placeInternal(world,pos,state,uuid,player,supplied);
    }
    private static TransactionCore.Result placeInternal(ServerWorld world,BlockPos pos,BlockState state,UUID uuid,PlayerEntity player,NbtCompound supplied){
        if(!(state.getBlock() instanceof CompositeRootBlock block))return rejected("not_a_composite_root");
        if(block.spec.id.getPath().equals("prototype_tree"))state=state.with(CompositeRootBlock.VARIANT,0);
        NbtCompound payload=supplied==null?new NbtCompound():supplied.copy();
        if(block.spec.requiredSupport){Double height=topHeight(world,pos.add(block.spec.supportOffset.x(),block.spec.supportOffset.y(),block.spec.supportOffset.z()),null);if(height==null)return rejected("missing_adequate_support");payload.putDouble("MountY",height-1);}
        else if(!(block instanceof ThinWindowRootBlock))payload.remove("MountY");
        if(block instanceof ThinWindowRootBlock&&!GlazingMount.seat(world,pos,state,payload))return rejected("missing_glazing_support_on_clicked_face");
        ObjectInstance object=instance(world,pos,state,uuid,payload);FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of(object.owner(),object));
        return execute(access,core().placement(access,object,checks(world,player,object,block)));
    }
    public static TransactionCore.Result transition(ServerWorld world,Owner owner,BlockState next,PlayerEntity player){
        BlockPos pos=CompositeData.pos(owner.root());
        BlockState previous=world.isChunkLoaded(pos)?world.getBlockState(pos):next;
        var result=transitionInternal(world,owner,next,player);
        notifyOpenTransition(world,owner,previous,next,player,result);
        return result;
    }
    private static TransactionCore.Result transitionInternal(ServerWorld world,Owner owner,BlockState next,PlayerEntity player){
        if(!world.isChunkLoaded(CompositeData.pos(owner.root())))return rejected("unloaded_root");FabricCompositeWorld beforeAccess=new FabricCompositeWorld(world,Map.of());CellSnapshot root=beforeAccess.read(owner.root());
        if(!owner.equals(root.resident())||!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(next)||!net.minecraft.registry.Registries.BLOCK.getId(next.getBlock()).toString().equals(owner.registryId()))return rejected("stale_owner_or_wrong_kind");
        CompositeRootBlock block=next.getBlock() instanceof CompositeRootBlock own?own:null;
        NbtCompound payload=CompositeData.nbt(root.data().blockEntityNbt());
        if(block instanceof ThinWindowRootBlock)GlazingMount.rememberAnchor(CompositeData.state(root.data()),payload);
        if(block instanceof ThinWindowRootBlock&&!GlazingMount.seat(world,CompositeData.pos(owner.root()),next,payload))return rejected("missing_glazing_support_for_mount_mode");
        payload=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.nativePayload(world,CompositeData.pos(owner.root()),next,payload);ObjectInstance nextObject=instance(world,CompositeData.pos(owner.root()),next,owner.instanceId(),payload);FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of(owner,nextObject));
        return execute(access,core().transitionPayload(access,owner,nextObject,checks(world,player,nextObject,block)));
    }
    /** Typed instance payload edit; actual geometry uses the same atomic owner transaction. */
    public static TransactionCore.Result transitionPayload(ServerWorld world,Owner owner,BlockState next,NbtCompound payload,PlayerEntity player){
        BlockPos root=CompositeData.pos(owner.root());if(!world.isChunkLoaded(root))return rejected("unloaded_root");
        FabricCompositeWorld before=new FabricCompositeWorld(world,Map.of());CellSnapshot snapshot=before.read(owner.root());
        if(!owner.equals(snapshot.resident())||!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(next)||!net.minecraft.registry.Registries.BLOCK.getId(next.getBlock()).toString().equals(owner.registryId()))return rejected("stale_owner_or_wrong_kind");
        payload=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.nativePayload(world,root,next,payload);ObjectInstance object=instance(world,root,next,owner.instanceId(),payload);FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of(owner,object));
        return execute(access,core().transitionPayload(access,owner,object,checks(world,player,object,next.getBlock() instanceof CompositeRootBlock block?block:null)));
    }
    public static TransactionCore.Result migrateCatalogue(ServerWorld world,Owner owner,BlockState next,NbtCompound payload) {
        BlockPos pos=CompositeData.pos(owner.root());
        if(!world.isChunkLoaded(pos))return rejected("unloaded_migration_root");
        BlockState old=world.getBlockState(pos);
        if(!dev.dreamwalker.bloodbornedw.architecture.CatalogueMigration.target(old).equals(next))return rejected("unreviewed_alias_migration");
        ObjectInstance object=instance(world,pos,next,owner.instanceId(),payload);
        FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of(object.owner(),object));
        return execute(access,core().migrateAlias(access,owner,object,List.of()));
    }
    private static void notifyOpenTransition(ServerWorld world,Owner owner,BlockState previous,BlockState next,PlayerEntity player,TransactionCore.Result result){
        if(result.outcome()!=TransactionCore.Outcome.COMMITTED||!(next.getBlock() instanceof CompositeRootBlock block)||!block.spec.id.getPath().equals("prototype_double_door")||!block.spec.openable||previous.get(CompositeRootBlock.OPEN)==next.get(CompositeRootBlock.OPEN))return;
        var ref=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.reference(world,CompositeData.pos(owner.root()));
        if(ref==null)return;if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)dev.dreamwalker.bloodbornedw.link.ObjectPolicies.noteInitiator(world,ref,serverPlayer);
        dev.dreamwalker.bloodbornedw.link.ObjectPolicies.changed(world,ref,previous.get(CompositeRootBlock.OPEN),next.get(CompositeRootBlock.OPEN));
    }
    public static TransactionCore.Result remove(ServerWorld world,Owner owner,PlayerEntity player,boolean drop){
        return removeInternal(world,owner,player,drop);
    }
    private static TransactionCore.Result removeInternal(ServerWorld world,Owner owner,PlayerEntity player,boolean drop){
        BlockPos sourcePos=CompositeData.pos(owner.root());if(world.isChunkLoaded(sourcePos)&&world.getBlockEntity(sourcePos) instanceof dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity source&&owner.equals(source.resident()))return dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime.remove(world,sourcePos,player,drop)?new TransactionCore.Result(TransactionCore.Outcome.COMMITTED,0,""):rejected("source_pair_removal_refused");
        return removeOwnedContributions(world,owner,player,drop);
    }
    public static TransactionCore.Result removeOwnedContributions(ServerWorld world,Owner owner,PlayerEntity player,boolean drop){
        if(!world.isChunkLoaded(CompositeData.pos(owner.root())))return rejected("unloaded_root");ItemStack item=pick(world,owner);FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of());
        var preparation=core().removal(access,owner);
        if(preparation.accepted()&&player!=null)for(Cell cell:preparation.plan().writes().keySet())if(!canModify(world,player,CompositeData.pos(cell)))return rejected("protected_owned_cell");
        var result=execute(access,preparation);
        if(result.outcome()==TransactionCore.Outcome.COMMITTED){
            // Transaction callbacks are suppressed while writing. Retire only
            // this committed owner UUID, regardless of whether an item drops.
            dev.dreamwalker.bloodbornedw.link.MechanismLinks.removedArchitecture(world.getServer(),new dev.dreamwalker.bloodbornedw.link.MechanismLinks.TargetRef(dev.dreamwalker.bloodbornedw.link.MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),CompositeData.pos(owner.root()).asLong(),owner.registryId()));
            if(drop&&!item.isEmpty())Block.dropStack(world,CompositeData.pos(owner.root()),item);
        }return result;
    }
    public static TransactionCore.Result execute(FabricCompositeWorld access,TransactionCore.Preparation preparation){CompositeShapeSnapshots.beginBatch(access.world);WRITING.set(WRITING.get()+1);try{var result=core().execute(access,preparation);LAST_RESULT.set(result);return result;}finally{WRITING.set(WRITING.get()-1);CompositeShapeSnapshots.endBatch(access.world);}}
    public static TransactionCore.Result lastResult(){return LAST_RESULT.get();}

    private static TransactionCore.Result rejected(String reason){var result=new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,reason);LAST_RESULT.set(result);return result;}
    private static List<TransactionCore.PlacementCheck> checks(ServerWorld world,PlayerEntity player,ObjectInstance object,CompositeRootBlock block){return List.of((proposed,reads)->{
        for(Cell offset:proposed.cells().keySet())if(player!=null&&!canModify(world,player,CompositeData.pos(proposed.owner().root().add(offset))))return "protected_cell";
        if(player!=null&&world.getBlockEntity(CompositeData.pos(proposed.owner().root())) instanceof CompositeBlockEntity previous&&proposed.owner().equals(previous.resident()))for(Cell old:instance(world,CompositeData.pos(proposed.owner().root()),world.getBlockState(CompositeData.pos(proposed.owner().root())),proposed.owner().instanceId(),previous.payload()).cells().keySet())if(!canModify(world,player,CompositeData.pos(proposed.owner().root().add(old))))return "protected_previous_owned_cell";
        // Decorations may intersect. New active physical intersections are checked independently.
        int rotation=Integer.parseInt(proposed.rootData().properties().getOrDefault("rotation","0"));
        boolean heightEdit=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.heightEdit();
        if(block!=null&&!heightEdit)for(Cell offset:block.spec.essential){if(rotation%2!=0&&!offset.equals(Cell.ORIGIN))return "non_root_essential_reservation_requires_cardinal_rotation";Cell rotated=offset.rotate(rotation/2);CellSnapshot cell=reads.require(proposed.owner().root().add(rotated));if(!offset.equals(Cell.ORIGIN)&&cell.kind()==Kind.FOREIGN)return "occupied_essential_cell";}
        if(block!=null&&block.spec.requiredSupport&&!heightEdit&&CompositeData.nbt(proposed.rootData().blockEntityNbt()).getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY)==0){Cell support=proposed.owner().root().add(block.spec.supportOffset);reads.require(support);Double height=topHeight(world,CompositeData.pos(support),object.owner());double mount=CompositeData.nbt(proposed.rootData().blockEntityNbt()).getDouble("MountY");if(height==null||Math.abs(height-1-mount)>1e-5)return "inadequate_support";}
        if(!heightEdit&&!dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstance(proposed.owner().instanceId())&&!unchangedPhysical(world,proposed)) {
            String conflict=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.conflict(world,
                    dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.physicalBoxes(proposed),proposed.owner(),null);
            if(conflict!=null)return conflict;
        }
        return null;
    });}
    private static boolean unchangedPhysical(ServerWorld world,ObjectInstance proposed){
        var old=world.getBlockEntity(CompositeData.pos(proposed.owner().root()));
        if(!(old instanceof CompositeBlockEntity root)||!proposed.owner().equals(root.resident()))return false;
        BlockState state=world.getBlockState(CompositeData.pos(proposed.owner().root()));
        if(!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(state))return false;
        ObjectInstance previous=instance(world,CompositeData.pos(proposed.owner().root()),state,proposed.owner().instanceId(),root.payload());
        return previous.cells().entrySet().stream().allMatch(entry->proposed.cells().containsKey(entry.getKey())&&entry.getValue().collision().equals(proposed.cells().get(entry.getKey()).collision()))
                &&proposed.cells().entrySet().stream().allMatch(entry->previous.cells().containsKey(entry.getKey())&&entry.getValue().collision().equals(previous.cells().get(entry.getKey()).collision()));
    }
    private static boolean canModify(ServerWorld world,PlayerEntity player,BlockPos pos){return player.getAbilities().allowModifyWorld&&world.canPlayerModifyAt(player,pos);}
    /** Adequate top contact can be a slab/fence top; full-cube support is not required. */
    public static Double topHeight(World world,BlockPos support,Owner exclude){
        if(!world.isChunkLoaded(support))return null;BlockState state=world.getBlockState(support);VoxelShape shape;
        BASE_SHAPE.set(true);try{shape=state.getBlock() instanceof CompositeRootBlock||state.isOf(CompositeArchitecture.CELL)?VoxelShapes.empty():state.getCollisionShape(world,support);}finally{BASE_SHAPE.set(false);}
        for(var contribution:contributions(world,support))if(!contribution.owner().equals(exclude))shape=VoxelShapes.union(shape,shape(contribution.shape().collision()));
        double max=Double.NEGATIVE_INFINITY;for(var box:shape.getBoundingBoxes())if(box.minX<=.5&&box.maxX>=.5&&box.minZ<=.5&&box.maxZ>=.5)max=Math.max(max,box.maxY);
        return Double.isFinite(max)&&max>0?max:null;
    }
    public static Double bottomHeight(World world,BlockPos support){
        if(!world.isChunkLoaded(support))return null;VoxelShape shape=nativeCollision(world,support,ShapeContext.absent());
        for(var contribution:contributions(world,support))shape=VoxelShapes.union(shape,shape(contribution.shape().collision()));
        double min=Double.POSITIVE_INFINITY;for(var b:shape.getBoundingBoxes())if(b.minX<=.5&&b.maxX>=.5&&b.minZ<=.5&&b.maxZ>=.5)min=Math.min(min,b.minY);
        return Double.isFinite(min)?min:null;
    }
    public static List<CompositeData.Contribution> contributions(BlockView view,BlockPos pos){
        if(CompositeShapeSnapshots.worker(view))return CompositeShapeSnapshots.contributions(view,pos);
        if(view instanceof World world){List<CompositeData.Contribution> ledger=CompositeLedger.get(world).at(CompositeData.cell(pos));if(!ledger.isEmpty())return ledger;}
        // A shape query must not promote a not-yet-FULL chunk, even on the server.
        return dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.loadedEntity(view,pos) instanceof CompositeBlockEntity entity?entity.contributions():List.of();
    }
    public static VoxelShape shape(List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> boxes){return CompositeShapes.of(boxes);}
    public static VoxelShape cellShape(BlockView world,BlockPos pos,boolean collision,BlockState fallback){
        return cellShape(world,pos,collision,fallback,ShapeContext.absent());
    }
    public static VoxelShape cellShape(BlockView world,BlockPos pos,boolean collision,BlockState fallback,ShapeContext context){
        List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> boxes=new ArrayList<>();List<CompositeData.Contribution> entries=contributions(world,pos);for(var entry:entries)if(validOrDeferred(world,entry.owner()))boxes.addAll(collision?contextCollision(world,entry,pos,context):contextSelection(world,entry,pos,context));if(entries.isEmpty()&&fallback.getBlock() instanceof CompositeRootBlock block){Footprint f=block.spec.footprint(fallback,0).get(Cell.ORIGIN);boxes.addAll(collision||!f.collision().isEmpty()?f.collision():f.selection());}return shape(boxes);
    }
    private static List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> contextSelection(BlockView world,CompositeData.Contribution entry,BlockPos cell,ShapeContext context) {
        var physical=contextCollision(world,entry,cell,context);
        if(!physical.isEmpty())return physical;
        BlockState rootState=CompositeData.state(entry.rootData());
        if(rootState.getBlock() instanceof CompositeRootBlock block&&block.spec.footprint(rootState,0).values().stream().anyMatch(part->!part.collision().isEmpty()))return List.of();
        return entry.shape().selection();
    }
    private static List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> contextCollision(BlockView world,CompositeData.Contribution entry,BlockPos cell,ShapeContext context){
        boolean player=context instanceof EntityShapeContext actor&&actor.getEntity() instanceof PlayerEntity;
        if(!player||!entry.owner().registryId().startsWith("bloodborne_dw:prototype_ladder")||!"true".equals(entry.rootData().properties().get("diagonal")))return entry.shape().collision();
        // Preserve native diagonal ladder's player-pass-through context. The
        // source pair's separately owned original beehive cube remains solid.
        if(!"true".equals(entry.rootData().properties().get("source_clone"))) {
            BlockPos root=CompositeData.pos(entry.owner().root());
            BlockState state=CompositeData.state(entry.rootData());
            NbtCompound mount=CompositeData.nbt(entry.rootData().blockEntityNbt());
            return dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock.hasBackingMount(state,world,root,mount.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY))?List.of():entry.shape().collision();
        }
        NbtCompound payload=CompositeData.nbt(entry.rootData().blockEntityNbt());double x=entry.owner().root().x()+payload.getInt("NativeBackingX")-cell.getX(),y=entry.owner().root().y()+payload.getInt("NativeBackingY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY)-cell.getY(),z=entry.owner().root().z()+payload.getInt("NativeBackingZ")-cell.getZ();
        double a=Math.max(0,x),b=Math.max(0,y),c=Math.max(0,z),d=Math.min(1,x+1),e=Math.min(1,y+1),f=Math.min(1,z+1);
        return d>a&&e>b&&f>c?List.of(new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(a,b,c,d,e,f)):List.of();
    }
    public static VoxelShape overlay(BlockView view,BlockPos pos,boolean collision){return overlay(view,pos,collision,ShapeContext.absent());}
    public static VoxelShape overlay(BlockView view,BlockPos pos,boolean collision,ShapeContext context){if(view==null||pos==null||baseShape()||contributions(view,pos).isEmpty())return VoxelShapes.empty();return overlay(CompositeShapeSnapshots.worker(view)?Blocks.AIR.getDefaultState():view.getBlockState(pos),view,pos,collision,context);}
    public static VoxelShape overlay(BlockState carrier,BlockView view,BlockPos pos,boolean collision){return overlay(carrier,view,pos,collision,ShapeContext.absent());}
    public static VoxelShape overlay(BlockState carrier,BlockView view,BlockPos pos,boolean collision,ShapeContext context){if(view==null||pos==null||baseShape()||carrier.getBlock() instanceof CompositeRootBlock||carrier.isOf(CompositeArchitecture.CELL))return VoxelShapes.empty();if(view instanceof World&&CompositeShapeSnapshots.contributions(view,pos).isEmpty())return VoxelShapes.empty();return cellShape(view,pos,collision,carrier,context);}
    private static boolean validOrDeferred(BlockView view,Owner owner){return validOrDeferred(view,owner,true);}
    private static boolean validOrDeferred(BlockView view,Owner owner,boolean cleanup){if(CompositeShapeSnapshots.worker(view))return CompositeShapeSnapshots.validOrDeferred(view,owner);if(!(view instanceof World world)||!world.isChunkLoaded(CompositeData.pos(owner.root())))return true;var entity=world.getBlockEntity(CompositeData.pos(owner.root()));boolean valid=entity instanceof CompositeBlockEntity root&&owner.equals(root.resident());if(cleanup&&!valid&&world instanceof ServerWorld server)scheduleCleanup(server,CompositeData.pos(owner.root()));return valid;}
    public static List<Owner> targets(World world,BlockPos pos,PlayerEntity player){
        return targetsInternal(world,pos,player,true);
    }
    private static List<Owner> targetsInternal(World world,BlockPos pos,PlayerEntity player,boolean cleanup){
        if(player==null||!world.isChunkLoaded(pos))return List.of();Vec3d eye=player.getEyePos(),end=eye.add(player.getRotationVec(1).multiply(player.isCreative()?5:4.5));
        record Hit(Owner owner,double distance){} List<Hit> hits=new ArrayList<>();
        for(var contribution:contributions(world,pos)){if(!validOrDeferred(world,contribution.owner(),cleanup))continue;var hit=shape(contextSelection(world,contribution,pos,ShapeContext.of(player))).raycast(eye,end,pos);if(hit!=null)hits.add(new Hit(contribution.owner(),hit.getPos().squaredDistanceTo(eye)));}
        hits.sort(Comparator.comparingDouble(Hit::distance).thenComparing(Hit::owner));
        BlockState carrier=world.getBlockState(pos);if(!(carrier.getBlock() instanceof CompositeRootBlock)&&!carrier.isOf(CompositeArchitecture.CELL)&&!(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(carrier)&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(world,pos)!=null)){
            BASE_SHAPE.set(true);try{var foreign=carrier.getOutlineShape(world,pos).raycast(eye,end,pos);if(foreign!=null){double distance=foreign.getPos().squaredDistanceTo(eye);hits.removeIf(hit->hit.distance>=distance-1e-8);}}finally{BASE_SHAPE.set(false);}
        }
        return hits.stream().map(Hit::owner).distinct().toList();
    }
    public static Owner target(World world,BlockPos pos,PlayerEntity player){
        List<Owner> hits=targets(world,pos,player);return hits.isEmpty()?null:hits.get(0);
    }
    /** Diagnostic inspection must not queue stale-owner cleanup or alter selected owners. */

    public static Owner soleNativeOwner(World world,BlockPos pos){BlockState state=world.getBlockState(pos);if(!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(state)&&!state.isOf(CompositeArchitecture.CELL))return null;List<Owner> owners=contributions(world,pos).stream().map(CompositeData.Contribution::owner).filter(owner->validOrDeferred(world,owner)).distinct().toList();if(owners.size()!=1)return null;Owner owner=owners.get(0);if(!world.isChunkLoaded(CompositeData.pos(owner.root())))return null;var entity=world.getBlockEntity(CompositeData.pos(owner.root()));return entity instanceof CompositeBlockEntity root&&owner.equals(root.resident())?owner:null;}

    public static ItemStack pick(World world,Owner owner){
        if(owner==null)return ItemStack.EMPTY;
        for(var contribution:contributions(world,CompositeData.pos(owner.root())))if(contribution.owner().equals(owner)){BlockState state=CompositeData.state(contribution.rootData());return dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.item(state,CompositeData.nbt(contribution.rootData().blockEntityNbt()));}
        var root=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.loadedEntity(world,CompositeData.pos(owner.root()));return root instanceof CompositeBlockEntity cached&&owner.equals(cached.resident())?dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.item(world.getBlockState(CompositeData.pos(owner.root())),cached.payload()):ItemStack.EMPTY;
    }
    public static ItemStack defaultPick(BlockView world,BlockPos pos,BlockState state){List<CompositeData.Contribution> entries=contributions(world,pos);if(entries.size()==1){var entry=entries.get(0);BlockState root=CompositeData.state(entry.rootData());return dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.item(root,CompositeData.nbt(entry.rootData().blockEntityNbt()));}return dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.item(state,new NbtCompound());}
    public static ActionResult use(World world,BlockPos pos,PlayerEntity player){
        if(player==null)return ActionResult.PASS;
        Owner owner=target(world,pos,player);if(owner==null)return ActionResult.PASS;
        BlockState state=CompositeData.state(contributions(world,pos).stream().filter(entry->entry.owner().equals(owner)).findFirst().orElseThrow().rootData());
        if(!(state.getBlock() instanceof CompositeRootBlock block)||!block.spec.id.getPath().equals("prototype_double_door"))return ActionResult.PASS;
        if(world.isClient)return ActionResult.SUCCESS;
        var ref=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.reference((ServerWorld)world,CompositeData.pos(owner.root()));
        if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer&&ref!=null&&!dev.dreamwalker.bloodbornedw.link.ObjectPolicies.allowManual(serverPlayer,ref))return ActionResult.FAIL;
        return transition((ServerWorld)world,owner,state.cycle(CompositeRootBlock.OPEN),player).outcome()==TransactionCore.Outcome.COMMITTED?ActionResult.CONSUME:ActionResult.FAIL;
    }
    private static void queue(ServerWorld world,String key,Runnable action){PENDING.computeIfAbsent(world,unused->new LinkedHashMap<>()).putIfAbsent(key,action);}
    public static void cleanupNow(ServerWorld world,BlockPos pos){for(var entry:List.copyOf(contributions(world,pos))){BlockState state=CompositeData.state(entry.rootData());if(!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(state))continue;NbtCompound payload=CompositeData.nbt(entry.rootData().blockEntityNbt());for(Cell offset:instance(world,CompositeData.pos(entry.owner().root()),state,entry.owner().instanceId(),payload).cells().keySet()){Cell cell=entry.owner().root().add(offset);if(world.isChunkLoaded(CompositeData.pos(cell))){FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of());execute(access,core().pruneStale(access,cell));}}}}
    public static void refreshNative(ServerWorld world,BlockPos pos){queue(world,"native-mounted-refresh:"+pos.asLong(),()->{if(!world.isChunkLoaded(pos)||!(world.getBlockEntity(pos) instanceof CompositeBlockEntity own)||own.resident()==null||own.contributions().isEmpty()||!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(world.getBlockState(pos)))return;BlockState state=world.getBlockState(pos);NbtCompound next=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.nativePayload(world,pos,state,own.payload());if(next.equals(own.payload())&&own.contributions().stream().filter(entry->entry.owner().equals(own.resident())).allMatch(entry->entry.rootData().properties().equals(CompositeData.data(state,next).properties())))return;transitionPayload(world,own.resident(),state,next,null);});}
    public static void scheduleCleanup(ServerWorld world,BlockPos pos){
        List<CompositeData.Contribution> entries=List.copyOf(contributions(world,pos));for(var entry:entries)queue(world,"cleanup:"+entry.owner().instanceId(),()->{
            BlockState rootState=CompositeData.state(entry.rootData());if(!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(rootState))return;
            NbtCompound payload=CompositeData.nbt(entry.rootData().blockEntityNbt());
            for(Cell offset:instance(world,CompositeData.pos(entry.owner().root()),rootState,entry.owner().instanceId(),payload).cells().keySet()){Cell cell=entry.owner().root().add(offset);if(!world.isChunkLoaded(CompositeData.pos(cell)))continue;FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of());execute(access,core().pruneStale(access,cell));}
        });
    }
    public static void scheduleSupport(ServerWorld world,BlockPos root){queue(world,"support:"+root.asLong(),()->{
        if(!world.isChunkLoaded(root))return;var entity=world.getBlockEntity(root);if(!(entity instanceof CompositeBlockEntity cached)||cached.resident()==null||!(world.getBlockState(root).getBlock() instanceof CompositeRootBlock block))return;
        // Glass records its initial clicked surface; it remains in air after neighbors are removed.
        if(block instanceof ThinWindowRootBlock||cached.payload().getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY)!=0)return;
        if(!block.spec.requiredSupport)return;BlockPos below=root.add(block.spec.supportOffset.x(),block.spec.supportOffset.y(),block.spec.supportOffset.z());if(world.isChunkLoaded(below)){Double height=topHeight(world,below,cached.resident());if(height==null||Math.abs(height-1-cached.mountY())>1e-5)remove(world,cached.resident(),null,true);}
    });}
    public static void drain(ServerWorld world){
        Map<String,Runnable> tasks=PENDING.remove(world);if(tasks!=null)for(Runnable task:tasks.values())task.run();
    }
    public static void chunkLoaded(ServerWorld world,ChunkPos chunk){
        queue(world,"catalogue-migration-chunk:"+chunk.toLong(),()->dev.dreamwalker.bloodbornedw.architecture.CatalogueMigration.loadedChunk(world,chunk));
        CompositeLedger ledger=CompositeLedger.get(world);for(Cell cell:ledger.cells())if((cell.x()>>4)==chunk.x&&(cell.z()>>4)==chunk.z){
        BlockPos pos=CompositeData.pos(cell);scheduleCleanup(world,pos);
        // CHUNK_LOAD runs before this chunk's FULL future has completed. Never
        // ask World for a BE here: that would wait recursively for the same
        // future. Helper chunks retry the same owner when the assembly loads.
        for(var contribution:ledger.at(cell)){Owner owner=contribution.owner();
            if(owner.registryId().startsWith("bloodborne_dw:prototype_wall")||owner.registryId().startsWith("bloodborne_dw:prototype_ladder"))
                queue(world,"catalogue-migration-root:"+owner.instanceId(),()->dev.dreamwalker.bloodbornedw.architecture.CatalogueMigration.loadedRoot(world,CompositeData.pos(owner.root())));
            if(!owner.registryId().equals("bloodborne_dw:prototype_tree"))continue;
            queue(world,"tree-accepted-normalize:"+owner.instanceId(),()->{
                BlockPos root=CompositeData.pos(owner.root());var loaded=world.getChunkManager().getWorldChunk(root.getX()>>4,root.getZ()>>4);
                if(loaded==null||!(loaded.getBlockEntity(root) instanceof CompositeBlockEntity cached)||!owner.equals(cached.resident()))return;
                BlockState current=loaded.getBlockState(root);
                if(!(current.getBlock() instanceof CompositeRootBlock block)||!block.spec.id.getPath().equals("prototype_tree")||current.get(CompositeRootBlock.VARIANT)==0)return;
                BlockState next=current.with(CompositeRootBlock.VARIANT,0);
                for(Cell offset:CompositeSourceShift.footprint(block.spec,next,cached.payload()).keySet()){
                    Cell required=owner.root().add(offset);if(world.getChunkManager().getWorldChunk(required.x()>>4,required.z()>>4)==null)return;
                }
                transition(world,owner,next,null);
            });
        }
    }}
    private CompositeRuntime(){}
}
