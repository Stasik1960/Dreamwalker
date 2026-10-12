package dev.dreamwalker.bloodbornedw.compat.worldedit;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.event.platform.PlatformReadyEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extension.factory.parser.DefaultBlockParser;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Mask2D;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornerp.object.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** WorldEdit sees logical objects rather than exposing their helper carriers.
 * Five-digit masks select complete owners, including RP placement entities.
 * Native vanilla/other-mod reads and writes are delegated unchanged. */
public final class WorldEditBridge {
    private static final String SNAPSHOT="DreamwalkerWorldEditSnapshot";
    private static final String RP_REQUEST="DreamwalkerWorldEditRpType";
    private static final String SELECTION="DreamwalkerWorldEditSelection";
    private static final Set<String> RP_TYPES=new LinkedHashSet<>();
    private record Match(ServerWorld world,BlockPos pos,Set<Owner> owners,Set<UUID> entities,boolean nativeMatch) {}
    private static final ThreadLocal<Match> MATCH=new ThreadLocal<>();
    private static final ThreadLocal<Match> HISTORY_SELECTION=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> HISTORY_RP_REQUEST=new ThreadLocal<>();
    private static boolean initialized;
    private WorldEditBridge() {}
    public static void initialize() {
        if(initialized)return;initialized=true;
        WorldEdit we=WorldEdit.getInstance();
        we.getBlockFactory().register(new NumericBlocks(we));
        we.getMaskFactory().register(new NumericMask(we));
        we.getEventBus().register(new Listener());
    }
    public static ServerWorld nativeWorld(com.sk89q.worldedit.world.World world) {
        if(world==null)return null;
        try {Object result=world.getClass().getMethod("getWorld").invoke(world);return result instanceof ServerWorld server?server:null;}
        catch(ReflectiveOperationException ignored){return null;}
    }
    private static BlockPos pos(BlockVector3 p){return new BlockPos(p.getX(),p.getY(),p.getZ());}
    private static BlockVector3 vector(BlockPos p){return BlockVector3.at(p.getX(),p.getY(),p.getZ());}
    private static boolean own(String id){return id.startsWith("bloodborne_dw:")||id.startsWith("bloodborne_rp:");}
    public static DebugCatalogue.Entry number(String number) {
        for(var entry:DebugCatalogue.entries())if(entry.temporaryId().equals(number))return DebugCatalogue.entry(entry.registryId());
        return null;
    }
    private static DebugCatalogue.Entry tokenEntry(String token) {
        String id=token.split("\\[",2)[0];
        return id.matches("[0-9]{5}")?number(id):own(id)?DebugCatalogue.entry(new Identifier(id)):null;
    }
    private static String stateToken(BlockState state) {
        Map<String,String> properties=new TreeMap<>();state.getEntries().forEach((p,v)->properties.put(p.getName(),name(p,v)));
        return Registries.BLOCK.getId(state.getBlock())+"["+String.join(",",properties.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+"]";
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static String name(net.minecraft.state.property.Property p,Comparable value){return p.name(value);}
    private static BaseBlock parsed(String input,ParserContext context)throws InputParseException {
        return new DefaultBlockParser(WorldEdit.getInstance()).parseFromInput(input,context);
    }
    private static BaseBlock defaultBlock(DebugCatalogue.Entry entry,ParserContext context)throws InputParseException {
        Identifier id=entry.registryId();
        if(Registries.BLOCK.containsId(id)&&Registries.BLOCK.get(id)!=Blocks.AIR) {
            BlockState state=Registries.BLOCK.get(id).getDefaultState();
            ItemStack item=ImportedAssemblyV126.canonicalStack(new ItemStack(state.getBlock().asItem()));
            if(item.getItem() instanceof BlockItem nativeItem) {
                state=nativeItem.getBlock().getDefaultState();NbtCompound tag=item.getSubNbt("BlockStateTag");
                if(tag!=null)for(String key:tag.getKeys()) {
                    var property=state.getBlock().getStateManager().getProperty(key);
                    if(property!=null)state=with(state,property,tag.getString(key));
                }
            }
            return parsed(stateToken(state),context);
        }
        if(RP_TYPES.contains(id.toString()))return BlockTypes.BARRIER.getDefaultState().toBaseBlock(new CompoundTag(Map.of(RP_REQUEST,new StringTag(id.toString()))));
        throw new InputParseException("BB ID "+entry.temporaryId()+" is not a placeable block/object");
    }
    private static <T extends Comparable<T>> BlockState with(BlockState state,net.minecraft.state.property.Property<T> p,String value){return state.with(p,p.parse(value).orElseThrow(()->new IllegalArgumentException("Invalid BB state "+value)));}
    private static List<Owner> owners(ServerWorld world,BlockPos p) {
        world.getChunk(p.getX()>>4,p.getZ()>>4);
        return CompositeRuntime.contributions(world,p).stream().map(CompositeData.Contribution::owner).distinct().sorted().toList();
    }
    private static List<RpObjectEntity> entities(ServerWorld world,BlockPos p) {
        return world.getEntitiesByClass(RpObjectEntity.class,new Box(p).expand(32),e->BlockPos.ofFloored(e.getPos()).equals(p)||e.selectionBoxes().stream().anyMatch(b->b.intersects(new Box(p))));
    }
    private static boolean matches(DebugCatalogue.Entry wanted,String registry) {
        var entry=DebugCatalogue.entry(new Identifier(registry));return entry!=null&&entry.temporaryId().equals(wanted.temporaryId());
    }
    private static boolean nativeSettings(ServerWorld world,BlockPos p){
        var state=world.getBlockState(p);if(!(ImportedCompactBlock.compact(state)||dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks.marker(state)))return false;
        var target=new ToolTarget(ToolTarget.Kind.BLOCK,world.getRegistryKey().getValue().toString(),null,p.asLong(),Registries.BLOCK.getId(state.getBlock()).toString());
        return !dev.dreamwalker.bloodbornedw.tool.ToolCommands.view(world.getServer(),target).text().isBlank();
    }
    private static NbtCompound snapshot(ServerWorld world,BlockPos p,List<Owner> owners,List<RpObjectEntity> entities) {
        NbtCompound result=new NbtCompound();result.putIntArray("Anchor",new int[]{p.getX(),p.getY(),p.getZ()});
        NbtList roots=new NbtList();
        for(Owner owner:owners) {
            BlockPos root=CompositeData.pos(owner.root());world.getChunk(root.getX()>>4,root.getZ()>>4);
            if(!(world.getBlockEntity(root) instanceof CompositeBlockEntity be)||!owner.equals(be.resident()))continue;
            NbtCompound row=new NbtCompound();row.put("Owner",CompositeData.owner(owner));row.put("Data",CompositeData.data(CompositeData.data(world.getBlockState(root),be.payload())));roots.add(row);
        }
        result.put("Roots",roots);NbtList rp=new NbtList();for(RpObjectEntity entity:entities){NbtCompound data=new NbtCompound();entity.saveNbt(data);rp.add(data);}result.put("Rp",rp);
        List<MechanismLinks.TargetRef> refs=new ArrayList<>();owners.forEach(o->refs.add(CompositeMechanismBridge.reference(world,o)));entities.forEach(e->refs.add(MechanismLinks.TargetRef.rp(e)));List<ToolTarget> natives=new ArrayList<>();var state=world.getBlockState(p);
        if(ImportedCompactBlock.compact(state)||dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks.marker(state)){natives.add(new ToolTarget(ToolTarget.Kind.BLOCK,world.getRegistryKey().getValue().toString(),null,p.asLong(),Registries.BLOCK.getId(state.getBlock()).toString()));result.put("NativeData",CompositeData.data(CompositeData.data(state,new NbtCompound())));}
        else if(Boolean.TRUE.equals(HISTORY_RP_REQUEST.get())&&owners.isEmpty()){NbtCompound nativeNbt=new NbtCompound();var be=world.getBlockEntity(p);if(be!=null)nativeNbt=be.createNbtWithIdentifyingData();result.put("NativeData",CompositeData.data(CompositeData.data(state,nativeNbt)));}
        result.put("References",WorldEditReferenceSnapshot.capture(world.getServer(),refs,natives));
        Match selection=HISTORY_SELECTION.get();if(selection!=null&&selection.world==world&&selection.pos.equals(p)){NbtList keep=new NbtList();for(Owner owner:owners(world,p))if(!selection.owners.contains(owner))keep.add(NbtHelper.fromUuid(owner.instanceId()));for(RpObjectEntity entity:entities(world,p))if(!selection.entities.contains(entity.getUuid()))keep.add(NbtHelper.fromUuid(entity.getUuid()));result.put("Preserved",keep);}
        return result;
    }
    private static NbtCompound snapshot(BaseBlock block) {
        CompoundTag nbt=block.getNbtData();if(nbt==null||!(nbt.getValue().get(SNAPSHOT) instanceof StringTag value))return null;
        return CompositeData.nbt(Base64.getDecoder().decode(value.getValue()));
    }
    /** EditSession's public reads bypass its event extents and address World
     * directly. Clipboard and ForwardExtentCopy therefore need this narrow
     * read hook as well as the existing transactional write adapters. */
    public static BaseBlock logicalRead(com.sk89q.worldedit.world.World adapter,BlockVector3 position,BaseBlock nativeBlock){
        ServerWorld world=nativeWorld(adapter);if(world==null)return nativeBlock;
        BlockPos p=pos(position);List<Owner> roots=owners(world,p);List<RpObjectEntity> rp=entities(world,p);
        boolean nativeObject=nativeSettings(world,p);
        if(roots.isEmpty()&&rp.isEmpty()&&!nativeObject)return nativeBlock;
        BaseBlock shown=!roots.isEmpty()?adapter.getFullBlock(vector(CompositeData.pos(roots.get(0).root()))):nativeObject?nativeBlock:BlockTypes.BARRIER.getDefaultState().toBaseBlock();
        return shown.toBaseBlock(tag(snapshot(world,p,roots,rp)));
    }
    /** Material-only reads retain logical object identity without serializing
     * settings, counters or clipboard payloads. */
    public static com.sk89q.worldedit.world.block.BlockState logicalState(com.sk89q.worldedit.world.World adapter,BlockVector3 position,com.sk89q.worldedit.world.block.BlockState nativeState){
        ServerWorld world=nativeWorld(adapter);if(world==null)return nativeState;BlockPos p=pos(position);List<Owner> roots=owners(world,p);
        if(!roots.isEmpty())return adapter.getBlock(vector(CompositeData.pos(roots.get(0).root())));
        if(!entities(world,p).isEmpty())return BlockTypes.BARRIER.getDefaultState();return nativeState;
    }
    private static String requestedRp(BaseBlock block){CompoundTag tag=block.getNbtData();return tag!=null&&tag.getValue().get(RP_REQUEST) instanceof StringTag value?value.getValue():null;}
    private static CompoundTag tag(NbtCompound data){return new CompoundTag(Map.of(SNAPSHOT,new StringTag(Base64.getEncoder().encodeToString(CompositeData.bytes(data)))));}
    private static BaseBlock selection(BaseBlock block,Match match){var data=new NbtCompound();NbtList os=new NbtList(),es=new NbtList();match.owners.forEach(o->os.add(NbtHelper.fromUuid(o.instanceId())));match.entities.forEach(id->es.add(NbtHelper.fromUuid(id)));data.put("Owners",os);data.put("Entities",es);data.putBoolean("Native",match.nativeMatch);Map<String,com.sk89q.jnbt.Tag> values=new LinkedHashMap<>();if(block.getNbtData()!=null)values.putAll(block.getNbtData().getValue());values.put(SELECTION,new StringTag(Base64.getEncoder().encodeToString(CompositeData.bytes(data))));return block.toBaseBlock(new CompoundTag(values));}
    private static NbtCompound selection(BaseBlock block){var nbt=block.getNbtData();return nbt!=null&&nbt.getValue().get(SELECTION) instanceof StringTag text?CompositeData.nbt(Base64.getDecoder().decode(text.getValue())):null;}
    private static Set<UUID> uuids(NbtList list){Set<UUID> ids=new HashSet<>();list.forEach(raw->ids.add(NbtHelper.toUuid(raw)));return ids;}
    public static final class NumericBlocks extends InputParser<BaseBlock> {
        public NumericBlocks(WorldEdit we){super(we);}
        public BaseBlock parseFromInput(String input,ParserContext context)throws InputParseException {
            String first=input.split("\\[",2)[0];if(!first.matches("[0-9]{5}")&&!RP_TYPES.contains(first))return null;
            var entry=tokenEntry(input);if(entry==null)throw new InputParseException("Unknown/reserved BB ID "+first);
            BaseBlock value=defaultBlock(entry,context);
            if(input.contains("[")){if(requestedRp(value)!=null)throw new InputParseException("RP objects use entity state; copy the complete object to preserve its settings");value=parsed(value.getBlockType().getId()+input.substring(input.indexOf('[')),context);}
            return value;
        }
        public Stream<String> getSuggestions(String input){return DebugCatalogue.entries().stream().map(DebugCatalogue.Entry::temporaryId).filter(id->id.startsWith(input));}
    }
    public static final class NumericMask extends InputParser<Mask> {
        public NumericMask(WorldEdit we){super(we);}
        public Mask parseFromInput(String input,ParserContext context)throws InputParseException {
            if(!input.matches("[0-9]{5}(,[0-9]{5})*")&&!(own(input)&&!input.contains("[")))return null;
            List<DebugCatalogue.Entry> entries=new ArrayList<>();for(String part:input.split(",")){var entry=tokenEntry(part);if(entry==null)throw new InputParseException("Unknown BB ID "+part);entries.add(entry);}
            ServerWorld world=nativeWorld(context.getWorld());if(world==null)throw new InputParseException("BB object mask requires a native Fabric world");
            return new Mask(){
                public boolean test(BlockVector3 v) {
                    BlockPos p=pos(v);Set<Owner> selected=new LinkedHashSet<>();Set<UUID> rp=new LinkedHashSet<>();
                    for(Owner o:owners(world,p))if(entries.stream().anyMatch(e->matches(e,o.registryId())))selected.add(o);
                    for(RpObjectEntity e:entities(world,p))if(entries.stream().anyMatch(w->matches(w,Registries.ENTITY_TYPE.getId(e.getType()).toString())))rp.add(e.getUuid());
                    boolean nativeMatch=entries.stream().anyMatch(e->matches(e,Registries.BLOCK.getId(world.getBlockState(p).getBlock()).toString()));
                    MATCH.set(new Match(world,p,selected,rp,nativeMatch));return nativeMatch||!selected.isEmpty()||!rp.isEmpty();
                }
                public Mask2D toMask2D(){return null;}
            };
        }
    }
    public static final class Listener {
        @Subscribe public void ready(PlatformReadyEvent event) {
            for(var e:DebugCatalogue.entries()) {
                Identifier id=e.registryId();if(!Registries.ENTITY_TYPE.containsId(id)||!ObjectRegistry.TYPES.containsValue(Registries.ENTITY_TYPE.get(id)))continue;
                // Do not register fictitious BlockTypes: Fabric assigns native
                // block state IDs, and every unknown RP entity ID maps to AIR0.
                // A real barrier state plus private NBT is the parser carrier.
                RP_TYPES.add(id.toString());
            }
        }
        @Subscribe public void editing(EditSessionEvent event) {
            ServerWorld world=nativeWorld(event.getWorld());if(world==null)return;
            // BEFORE_HISTORY is outside reordering/history/masks. BEFORE_CHANGE
            // handles raw changes (undo/redo) through the same logical adapter.
            if(event.getStage()==com.sk89q.worldedit.EditSession.Stage.BEFORE_HISTORY||event.getStage()==com.sk89q.worldedit.EditSession.Stage.BEFORE_CHANGE)
                event.setExtent(new ObjectExtent(event.getExtent(),world,event.getStage()==com.sk89q.worldedit.EditSession.Stage.BEFORE_HISTORY));
        }
    }
    public static final class ObjectExtent extends AbstractDelegateExtent {
        private final ServerWorld world;private final boolean outsideHistory;
        private record CopyKey(String family,UUID source,BlockPos delta) {}
        private final Map<CopyKey,UUID> copiedIds=new HashMap<>();
        private final Set<String> restoredCopies=new HashSet<>();
        private UUID copied(String family,UUID source,BlockPos delta){return copiedIds.computeIfAbsent(new CopyKey(family,source,delta.toImmutable()),key->UUID.randomUUID());}
        public ObjectExtent(Extent extent,ServerWorld world){this(extent,world,false);}
        public ObjectExtent(Extent extent,ServerWorld world,boolean outsideHistory){super(extent);this.world=world;this.outsideHistory=outsideHistory;}
        public com.sk89q.worldedit.world.block.BlockState getBlock(BlockVector3 v){return getFullBlock(v).toImmutableState();}
        public BaseBlock getFullBlock(BlockVector3 v) {
            BlockPos p=pos(v);List<Owner> os=new ArrayList<>(owners(world,p));List<RpObjectEntity> es=new ArrayList<>(entities(world,p));Match selected=HISTORY_SELECTION.get();if(selected!=null&&selected.world==world&&selected.pos.equals(p)){os.removeIf(o->!selected.owners.contains(o));es.removeIf(e->!selected.entities.contains(e.getUuid()));}
            boolean nativeObject=nativeSettings(world,p);if(os.isEmpty()&&es.isEmpty()&&!nativeObject&&!Boolean.TRUE.equals(HISTORY_RP_REQUEST.get()))return super.getFullBlock(v);
            BaseBlock block;
            try {
                if(!os.isEmpty()){BlockPos root=CompositeData.pos(os.get(0).root());world.getChunk(root.getX()>>4,root.getZ()>>4);block=super.getFullBlock(vector(root));}
                else block=nativeObject||es.isEmpty()?super.getFullBlock(v):BlockTypes.BARRIER.getDefaultState().toBaseBlock();
                return block.toBaseBlock(tag(snapshot(world,p,os,es)));
            }catch(RuntimeException failure){throw new IllegalStateException("Cannot expose BB object to WorldEdit",failure);}
        }
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 v,T value)throws WorldEditException {
            // The outer adapter supplies logical reads to history/masks, while
            // writes pass through WorldEdit history and reach the inner adapter.
            if(outsideHistory){Match match=MATCH.get();MATCH.remove();BlockPos p=pos(v);BaseBlock request=value.toBaseBlock();if(match!=null&&match.world==world&&match.pos.equals(p))request=selection(request,match);try{if(match!=null&&match.world==world&&match.pos.equals(p))HISTORY_SELECTION.set(match);if(requestedRp(request)!=null)HISTORY_RP_REQUEST.set(true);return super.setBlock(v,request);}finally{HISTORY_SELECTION.remove();HISTORY_RP_REQUEST.remove();}}
            if(!world.getServer().isOnThread())throw new InputParseException("BB edits require the server thread");
            BlockPos p=pos(v);world.getChunk(p.getX()>>4,p.getZ()>>4);
            String id=value.getBlockType().getId();BaseBlock block=value.toBaseBlock();NbtCompound old=snapshot(block);String requestedRp=requestedRp(block);
            List<Owner> remove=new ArrayList<>(owners(world,p));List<RpObjectEntity> rp=new ArrayList<>(entities(world,p));
            NbtCompound selected=selection(block);if(selected!=null){var os=uuids(selected.getList("Owners",11));var es=uuids(selected.getList("Entities",11));remove.removeIf(o->!os.contains(o.instanceId()));rp.removeIf(e->!es.contains(e.getUuid()));}else if(old!=null){var keep=uuids(old.getList("Preserved",11));remove.removeIf(o->keep.contains(o.instanceId()));rp.removeIf(e->keep.contains(e.getUuid()));}
            if(old==null&&requestedRp==null&&selected!=null&&!selected.getBoolean("Native")&&remove.isEmpty()&&rp.isEmpty())return true;
            if(old==null&&requestedRp==null&&!own(id)&&remove.isEmpty()&&rp.isEmpty())return super.setBlock(v,value);
            List<ObjectInstance> add=new ArrayList<>();List<NbtCompound> rpAdd=new ArrayList<>();Map<BlockPos,CellSnapshot.BlockData> nativeChanges=new LinkedHashMap<>();Map<UUID,UUID> objectIds=new LinkedHashMap<>();BlockPos delta=BlockPos.ORIGIN;
            if(old!=null) {
                int[] anchor=old.getIntArray("Anchor");if(anchor.length!=3)throw new InputParseException("Invalid BB clipboard anchor");
                delta=p.subtract(new BlockPos(anchor[0],anchor[1],anchor[2]));
                for(NbtElement raw:old.getList("Roots",10)){NbtCompound row=(NbtCompound)raw;Owner owner=CompositeData.owner(row.getCompound("Owner"));var data=CompositeData.data(row.getCompound("Data"));BlockPos root=CompositeData.pos(owner.root()).add(delta);UUID uuid=delta.equals(BlockPos.ORIGIN)?owner.instanceId():copied("object",owner.instanceId(),delta);objectIds.put(owner.instanceId(),uuid);add.add(CompositeRuntime.instance(world,root,CompositeData.state(data),uuid,CompositeData.nbt(data.blockEntityNbt())));}
                for(NbtElement raw:old.getList("Rp",10)){NbtCompound copy=((NbtCompound)raw).copy();UUID original=copy.getUuid("UUID"),next=delta.equals(BlockPos.ORIGIN)?original:copied("object",original,delta);objectIds.put(original,next);NbtList at=copy.getList("Pos",6);NbtList shifted=new NbtList();shifted.add(NbtDouble.of(at.getDouble(0)+delta.getX()));shifted.add(NbtDouble.of(at.getDouble(1)+delta.getY()));shifted.add(NbtDouble.of(at.getDouble(2)+delta.getZ()));copy.put("Pos",shifted);copy.putUuid("UUID",next);rpAdd.add(copy);}
                if(old.contains("NativeData",10))nativeChanges.put(p,CompositeData.data(old.getCompound("NativeData")));
                if(!delta.equals(BlockPos.ORIGIN)){for(var data:rpAdd){NbtList links=new NbtList();for(var raw:data.getList("Links",11)){UUID original=NbtHelper.toUuid(raw);links.add(NbtHelper.fromUuid(objectIds.getOrDefault(original,original)));}data.put("Links",links);}for(ObjectInstance object:new ArrayList<>(add)){var root=CompositeData.pos(object.owner().root());if(world.getBlockEntity(root) instanceof CompositeBlockEntity present&&object.owner().equals(present.resident())){var current=CompositeData.data(world.getBlockState(root),present.payload());if(current.equals(object.rootData())){add.remove(object);remove.remove(object.owner());}}}rpAdd.removeIf(data->world.getEntity(data.getUuid("UUID")) instanceof RpObjectEntity);rp.removeIf(entity->objectIds.containsValue(entity.getUuid()));}
            } else if(requestedRp!=null) {
                if(!RP_TYPES.contains(requestedRp))throw new InputParseException("Unknown BB RP object carrier");
                NbtCompound data=new NbtCompound();data.putString("id",requestedRp);data.putUuid("UUID",UUID.randomUUID());NbtList location=new NbtList();location.add(NbtDouble.of(p.getX()));location.add(NbtDouble.of(p.getY()));location.add(NbtDouble.of(p.getZ()));data.put("Pos",location);rpAdd.add(data);nativeChanges.put(p,CompositeData.data(Blocks.AIR.getDefaultState(),new NbtCompound()));
            } else if(own(id)&&Registries.BLOCK.containsId(new Identifier(id))) {
                BlockState state=Registries.BLOCK.get(new Identifier(id)).getDefaultState();for(var entry:value.getStates().entrySet())state=with(state,state.getBlock().getStateManager().getProperty(entry.getKey().getName()),entry.getValue().toString());
                if(VerticalMount.isArchitecture(state)&&!ImportedCompactBlock.compact(state)){NbtCompound payload=new NbtCompound();if(id.startsWith("bloodborne_dw:source_"))payload.putInt("ImportedTypeVersion",1);add.add(CompositeRuntime.instance(world,p,state,UUID.randomUUID(),payload));}
                else {for(Owner owner:remove)if(CompositeRuntime.remove(world,owner,null,false).outcome()!=TransactionCore.Outcome.COMMITTED)throw new InputParseException("Cannot remove BB owner");for(RpObjectEntity e:rp)e.removeObject();return super.setBlock(v,value);}
            }
            List<MechanismLinks.TargetRef> removedRefs=new ArrayList<>();remove.forEach(o->removedRefs.add(CompositeMechanismBridge.reference(world,o)));rp.forEach(e->removedRefs.add(MechanismLinks.TargetRef.rp(e)));List<ToolTarget> nativeRefs=new ArrayList<>();var nativeState=world.getBlockState(p);if(ImportedCompactBlock.compact(nativeState)||dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks.marker(nativeState))nativeRefs.add(new ToolTarget(ToolTarget.Kind.BLOCK,world.getRegistryKey().getValue().toString(),null,p.asLong(),Registries.BLOCK.getId(nativeState.getBlock()).toString()));
            WorldEditReferenceSnapshot.Prepared references=null;String referenceCopy=null;if(old!=null&&old.contains("References",10)){BlockPos copiedDelta=delta;referenceCopy=old.getCompound("References").toString()+"|"+delta;if(delta.equals(BlockPos.ORIGIN)||!restoredCopies.contains(referenceCopy))try{references=WorldEditReferenceSnapshot.prepare(world.getServer(),old.getCompound("References"),objectIds,delta,(family,uuid)->copied(family,uuid,copiedDelta),removedRefs,nativeRefs);}catch(IllegalStateException full){throw new InputParseException(full.getMessage());}}
            var result=WorldEditObjectTransactions.replace(world,remove,add,nativeChanges);
            if(result.outcome()!=TransactionCore.Outcome.COMMITTED)throw new InputParseException("BB whole-object edit rejected: "+result.reason());
            for(RpObjectEntity entity:rp)entity.removeObject();
            for(NbtCompound data:rpAdd)net.minecraft.entity.EntityType.loadEntityWithPassengers(data,world,e->{world.spawnEntity(e);return e;});
            if(references!=null){references.apply();if(!delta.equals(BlockPos.ORIGIN))restoredCopies.add(referenceCopy);}
            if(old!=null||!add.isEmpty()||!rpAdd.isEmpty()||id.equals("minecraft:air"))return true;
            return super.setBlock(v,value);
        }
    }
}
