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
    private static final Map<String,BlockType> RP_TYPES=new LinkedHashMap<>();
    private record Match(ServerWorld world,BlockPos pos,Set<Owner> owners,Set<UUID> entities) {}
    private static final ThreadLocal<Match> MATCH=new ThreadLocal<>();
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
        BlockType virtual=RP_TYPES.get(id.toString());
        if(virtual!=null)return virtual.getDefaultState().toBaseBlock();
        throw new InputParseException("BB ID "+entry.temporaryId()+" is not a placeable block/object");
    }
    private static <T extends Comparable<T>> BlockState with(BlockState state,net.minecraft.state.property.Property<T> p,String value){return state.with(p,p.parse(value).orElseThrow(()->new IllegalArgumentException("Invalid BB state "+value)));}
    private static List<Owner> owners(ServerWorld world,BlockPos p) {
        world.getChunk(p.getX()>>4,p.getZ()>>4);
        return CompositeRuntime.contributions(world,p).stream().map(CompositeData.Contribution::owner).distinct().sorted().toList();
    }
    private static List<RpObjectEntity> entities(ServerWorld world,BlockPos p) {
        return world.getEntitiesByClass(RpObjectEntity.class,new Box(p).expand(16),e->BlockPos.ofFloored(e.getPos()).equals(p));
    }
    private static boolean matches(DebugCatalogue.Entry wanted,String registry) {
        var entry=DebugCatalogue.entry(new Identifier(registry));return entry!=null&&entry.temporaryId().equals(wanted.temporaryId());
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
        return result;
    }
    private static NbtCompound snapshot(BaseBlock block) {
        CompoundTag nbt=block.getNbtData();if(nbt==null||!(nbt.getValue().get(SNAPSHOT) instanceof StringTag value))return null;
        return CompositeData.nbt(Base64.getDecoder().decode(value.getValue()));
    }
    private static CompoundTag tag(NbtCompound data){return new CompoundTag(Map.of(SNAPSHOT,new StringTag(Base64.getEncoder().encodeToString(CompositeData.bytes(data)))));}
    public static final class NumericBlocks extends InputParser<BaseBlock> {
        public NumericBlocks(WorldEdit we){super(we);}
        public BaseBlock parseFromInput(String input,ParserContext context)throws InputParseException {
            String first=input.split("\\[",2)[0];if(!first.matches("[0-9]{5}")&&!RP_TYPES.containsKey(first))return null;
            var entry=tokenEntry(input);if(entry==null)throw new InputParseException("Unknown/reserved BB ID "+first);
            BaseBlock value=defaultBlock(entry,context);
            if(input.contains("["))value=parsed(value.getBlockType().getId()+input.substring(input.indexOf('[')),context);
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
                    MATCH.set(new Match(world,p,selected,rp));return nativeMatch||!selected.isEmpty()||!rp.isEmpty();
                }
                public Mask2D toMask2D(){return null;}
            };
        }
    }
    public static final class Listener {
        @Subscribe public void ready(PlatformReadyEvent event) {
            for(var e:DebugCatalogue.entries()) {
                Identifier id=e.registryId();if(!Registries.ENTITY_TYPE.containsId(id)||!ObjectRegistry.TYPES.containsValue(Registries.ENTITY_TYPE.get(id)))continue;
                BlockType type=BlockType.REGISTRY.get(id.toString());
                if(type==null)type=BlockType.REGISTRY.register(id.toString(),new BlockType(id.toString()) {
                    public Map<String,? extends Property<?>> getPropertyMap(){return Map.of();}
                    public BlockMaterial getMaterial(){return BlockTypes.AIR.getMaterial();}
                    public String getName(){return e.name();}
                });
                RP_TYPES.put(id.toString(),type);
            }
        }
        @Subscribe public void editing(EditSessionEvent event) {
            ServerWorld world=nativeWorld(event.getWorld());if(world==null)return;
            // BEFORE_HISTORY is outside reordering/history/masks. BEFORE_CHANGE
            // handles raw changes (undo/redo) through the same logical adapter.
            if(event.getStage()==com.sk89q.worldedit.EditSession.Stage.BEFORE_HISTORY||event.getStage()==com.sk89q.worldedit.EditSession.Stage.BEFORE_CHANGE)
                event.setExtent(new ObjectExtent(event.getExtent(),world));
        }
    }
    public static final class ObjectExtent extends AbstractDelegateExtent {
        private final ServerWorld world;
        public ObjectExtent(Extent extent,ServerWorld world){super(extent);this.world=world;}
        public com.sk89q.worldedit.world.block.BlockState getBlock(BlockVector3 v){return getFullBlock(v).toImmutableState();}
        public BaseBlock getFullBlock(BlockVector3 v) {
            BlockPos p=pos(v);List<Owner> os=owners(world,p);List<RpObjectEntity> es=entities(world,p);if(os.isEmpty()&&es.isEmpty())return super.getFullBlock(v);
            BaseBlock block;
            try {
                if(!os.isEmpty()){BlockPos root=CompositeData.pos(os.get(0).root());world.getChunk(root.getX()>>4,root.getZ()>>4);block=parsed(stateToken(world.getBlockState(root)),new ParserContext());}
                else block=RP_TYPES.get(Registries.ENTITY_TYPE.getId(es.get(0).getType()).toString()).getDefaultState().toBaseBlock();
                return block.toBaseBlock(tag(snapshot(world,p,os,es)));
            }catch(InputParseException failure){throw new IllegalStateException("Cannot expose BB object to WorldEdit",failure);}
        }
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 v,T value)throws WorldEditException {
            if(!world.getServer().isOnThread())throw new InputParseException("BB edits require the server thread");
            BlockPos p=pos(v);world.getChunk(p.getX()>>4,p.getZ()>>4);
            String id=value.getBlockType().getId();BaseBlock block=value.toBaseBlock();NbtCompound old=snapshot(block);
            List<Owner> remove=new ArrayList<>(owners(world,p));List<RpObjectEntity> rp=new ArrayList<>(entities(world,p));
            Match match=MATCH.get();MATCH.remove();if(match!=null&&match.world==world&&match.pos.equals(p)){remove.removeIf(o->!match.owners.contains(o));rp.removeIf(e->!match.entities.contains(e.getUuid()));}
            if(old==null&&!own(id)&&remove.isEmpty()&&rp.isEmpty())return super.setBlock(v,value);
            List<ObjectInstance> add=new ArrayList<>();List<NbtCompound> rpAdd=new ArrayList<>();
            if(old!=null) {
                int[] anchor=old.getIntArray("Anchor");if(anchor.length!=3)throw new InputParseException("Invalid BB clipboard anchor");
                BlockPos delta=p.subtract(new BlockPos(anchor[0],anchor[1],anchor[2]));
                for(NbtElement raw:old.getList("Roots",10)){NbtCompound row=(NbtCompound)raw;Owner owner=CompositeData.owner(row.getCompound("Owner"));var data=CompositeData.data(row.getCompound("Data"));BlockPos root=CompositeData.pos(owner.root()).add(delta);UUID uuid=delta.equals(BlockPos.ORIGIN)?owner.instanceId():UUID.randomUUID();add.add(CompositeRuntime.instance(world,root,CompositeData.state(data),uuid,CompositeData.nbt(data.blockEntityNbt())));}
                for(NbtElement raw:old.getList("Rp",10)){NbtCompound copy=((NbtCompound)raw).copy();NbtList at=copy.getList("Pos",6);NbtList shifted=new NbtList();shifted.add(NbtDouble.of(at.getDouble(0)+delta.getX()));shifted.add(NbtDouble.of(at.getDouble(1)+delta.getY()));shifted.add(NbtDouble.of(at.getDouble(2)+delta.getZ()));copy.put("Pos",shifted);if(!delta.equals(BlockPos.ORIGIN))copy.putUuid("UUID",UUID.randomUUID());rpAdd.add(copy);}
            } else if(RP_TYPES.containsKey(id)) {
                NbtCompound data=new NbtCompound();data.putString("id",id);data.putUuid("UUID",UUID.randomUUID());NbtList location=new NbtList();location.add(NbtDouble.of(p.getX()));location.add(NbtDouble.of(p.getY()));location.add(NbtDouble.of(p.getZ()));data.put("Pos",location);rpAdd.add(data);
            } else if(own(id)&&Registries.BLOCK.containsId(new Identifier(id))) {
                BlockState state=Registries.BLOCK.get(new Identifier(id)).getDefaultState();for(var entry:value.getStates().entrySet())state=with(state,state.getBlock().getStateManager().getProperty(entry.getKey().getName()),entry.getValue().toString());
                if(VerticalMount.isArchitecture(state))add.add(CompositeRuntime.instance(world,p,state,UUID.randomUUID(),new NbtCompound()));
                else {for(Owner owner:remove)if(CompositeRuntime.remove(world,owner,null,false).outcome()!=TransactionCore.Outcome.COMMITTED)throw new InputParseException("Cannot remove BB owner");for(RpObjectEntity e:rp)e.removeObject();return super.setBlock(v,value);}
            }
            var result=WorldEditObjectTransactions.replace(world,remove,add);
            if(result.outcome()!=TransactionCore.Outcome.COMMITTED)throw new InputParseException("BB whole-object edit rejected: "+result.reason());
            for(RpObjectEntity entity:rp)entity.removeObject();
            for(NbtCompound data:rpAdd)net.minecraft.entity.EntityType.loadEntityWithPassengers(data,world,e->{world.spawnEntity(e);return e;});
            if(old!=null||!add.isEmpty()||!rpAdd.isEmpty()||id.equals("minecraft:air"))return true;
            return super.setBlock(v,value);
        }
    }
}
