package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Approved complete NEW assemblies; old fragment indices and saves stay intact. */
public final class ImportedAssemblyV126 {
    public static final String PAYLOAD_KEY="ImportedAssemblyV126";
    public static final String PARTS_KEY="ImportedAssemblyV126Parts";
    public static final String ITEM_VERSION_KEY="ImportedAssemblyItemVersion";
    public static final int VERSION=1;
    public record Component(String registryId,int pose,int componentYawDelta,Cell relativePosition,CompositeSourceShift.Shift sourceShift) {}
    public record Recipe(String key,String primaryRegistryId,int installationPose,List<Component> components) {
        public Recipe{components=List.copyOf(components);}
    }
    private record Catalogue(List<Recipe> recipes,Map<String,Recipe> primary,Map<String,String> redirects) {}
    private static final Catalogue CATALOGUE=load();
    private static final Map<ServerWorld,Map<Long,Set<BlockPos>>> DEFERRED=new WeakHashMap<>();
    private static final Map<ServerWorld,Set<String>> WARNED=new WeakHashMap<>();
    private ImportedAssemblyV126() {}
    private static Catalogue load(){
        var input=ImportedAssemblyV126.class.getResourceAsStream("/bloodborne_dw/imported-assembly-v126.json");
        if(input==null)return new Catalogue(List.of(),Map.of(),Map.of());
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Assembly recipe schema");
            List<Recipe> recipes=new ArrayList<>();Map<String,Recipe> primary=new LinkedHashMap<>();Map<String,String> redirects=new LinkedHashMap<>();Set<String> poses=new HashSet<>();
            for(JsonElement raw:root.getAsJsonArray("recipes")){
                JsonObject row=raw.getAsJsonObject();String registry=checked(row.get("primaryRegistryId").getAsString());List<Component> parts=new ArrayList<>();
                for(JsonElement element:row.getAsJsonArray("components")){JsonObject part=element.getAsJsonObject();JsonArray xyz=part.getAsJsonArray("relativePosition"),shift=part.getAsJsonArray("sourceShift");parts.add(new Component(checked(part.get("registryId").getAsString()),part.get("pose").getAsInt(),part.has("componentYawDelta")?part.get("componentYawDelta").getAsInt():0,new Cell(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt()),new CompositeSourceShift.Shift(shift.get(0).getAsDouble(),shift.get(1).getAsDouble(),shift.get(2).getAsDouble())));}
                Recipe recipe=new Recipe(row.get("key").getAsString(),registry,row.get("installationPose").getAsInt(),parts);
                if(parts.size()<2||parts.size()>8||recipe.installationPose()<1||recipe.installationPose()>143||!parts.get(0).relativePosition().equals(Cell.ORIGIN)||!poses.add(registry+"|"+recipe.installationPose()))throw new IllegalArgumentException("Invalid/repeated assembly recipe");primary.putIfAbsent(registry,recipe);recipes.add(recipe);
            }
            root.getAsJsonObject("hiddenPartRedirects").entrySet().forEach(entry->{String old=checked(entry.getKey()),next=entry.getValue().getAsString();if(!next.equals("bloodborne_dw:prototype_double_door"))checked(next);redirects.put(old,next);});
            return new Catalogue(List.copyOf(recipes),Map.copyOf(primary),Map.copyOf(redirects));
        }catch(Exception failure){throw new IllegalStateException("Invalid approved assembly recipes",failure);}
    }
    private static String checked(String registry){if(registry==null||!registry.startsWith("bloodborne_dw:source_")||Identifier.tryParse(registry)==null)throw new IllegalArgumentException("Assembly cannot change original V12: "+registry);return registry;}
    private static String registry(String path){return path.indexOf(':')>=0?path:"bloodborne_dw:"+path;}
    public static List<Recipe> recipes(){return CATALOGUE.recipes;}
    public static boolean hidden(String path){String id=ImportedTypeAliases.canonicalRegistry(registry(path));return CATALOGUE.redirects.containsKey(id);}
    public static int installationPose(String path){Recipe recipe=CATALOGUE.primary.get(registry(path));return recipe==null?0:recipe.installationPose;}
    public static List<Integer> activePoses(String path){String id=registry(path);return CATALOGUE.recipes.stream().filter(recipe->recipe.primaryRegistryId.equals(id)).map(Recipe::installationPose).toList();}
    public static boolean complete(BlockState state){return state.getBlock() instanceof CompositeRootBlock block&&activePoses(block.spec.id.toString()).contains(CompositeSpec.poseVariant(state));}
    /** Ordinary new/legacy items select the completed type, without copying UUIDs. */
    public static ItemStack canonicalStack(ItemStack old){
        if(old.isEmpty()||!(old.getItem() instanceof BlockItem item))return old;
        String source=Registries.BLOCK.getId(item.getBlock()).toString();if(!source.startsWith("bloodborne_dw:source_"))return old;
        String canonical=ImportedTypeAliases.canonicalRegistry(source),target=CATALOGUE.redirects.getOrDefault(canonical,canonical);
        Recipe recipe=CATALOGUE.primary.get(target);if(recipe==null&&target.equals(canonical))return old;
        Identifier id=new Identifier(target);if(!Registries.ITEM.containsId(id)||!(Registries.ITEM.get(id) instanceof BlockItem))throw new IllegalStateException("Missing completed assembly item "+id);
        ItemStack next=new ItemStack(Registries.ITEM.get(id),old.getCount());if(old.hasNbt())next.setNbt(old.getNbt().copy());
        int pose=recipe==null?0:recipe.installationPose;NbtCompound art=next.getOrCreateSubNbt("BlockStateTag");
        if(target.equals(source))try{int selected=Integer.parseInt(art.getString("variant"));if(art.contains("art_bank",8))selected+=16*Integer.parseInt(art.getString("art_bank"));if(activePoses(target).contains(selected))pose=selected;}catch(NumberFormatException ignored){}
        art.putString("variant",Integer.toString(pose%16));
        if(pose>=16)art.putString("art_bank",Integer.toString(pose/16));else art.remove("art_bank");
        next.getOrCreateNbt().putInt(ITEM_VERSION_KEY,VERSION);
        return ItemStack.areEqual(old,next)?old:next;
    }
    /** Deferred chunk hook; no reads occur in the CHUNK_LOAD callback itself. */
    public static void loadedChunk(ServerWorld world,ChunkPos chunk){
        var loaded=world.getChunkManager().getWorldChunk(chunk.x,chunk.z);if(loaded==null)return;
        Set<BlockPos> roots=new LinkedHashSet<>();for(var entity:List.copyOf(loaded.getBlockEntities().values()))if(entity instanceof CompositeBlockEntity root&&root.resident()!=null)roots.add(entity.getPos().toImmutable());
        roots.addAll(DEFERRED.computeIfAbsent(world,w->new HashMap<>()).getOrDefault(chunk.toLong(),Set.of()));
        for(BlockPos pos:roots)retryNearby(world,pos);
    }
    public static void installed(ServerWorld world,Owner owner){retryNearby(world,CompositeData.pos(owner.root()));}
    public static void loadedRoot(ServerWorld world,Owner owner){retryNearby(world,CompositeData.pos(owner.root()));}
    private static void retryNearby(ServerWorld world,BlockPos root){
        if(!loaded(world,root))return;
        if(world.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()!=null)migrateLoadedRoot(world,own.resident());
        if(!(world.getBlockEntity(root) instanceof CompositeBlockEntity current)||current.resident()==null||!(world.getBlockState(root).getBlock() instanceof CompositeRootBlock))return;
        BlockState state=world.getBlockState(root);
        for(Recipe recipe:CATALOGUE.recipes)for(Component component:recipe.components){
            if(!component.registryId.equals(current.resident().registryId())||component.pose!=CompositeSpec.poseVariant(state))continue;
            int anchorYaw=(state.get(CompositeRootBlock.ROTATION)-component.componentYawDelta)&7;
            var relative=new CompositeSourceShift.Shift(component.relativePosition.x(),component.relativePosition.y(),component.relativePosition.z()).rotate(anchorYaw);
            if(Math.abs(relative.x()-Math.rint(relative.x()))>1e-7||Math.abs(relative.z()-Math.rint(relative.z()))>1e-7)continue;
            BlockPos anchor=root.add(-(int)Math.rint(relative.x()),-component.relativePosition.y(),-(int)Math.rint(relative.z()));if(!loaded(world,anchor))continue;if(world.getBlockEntity(anchor) instanceof CompositeBlockEntity own&&own.resident()!=null)migrateLoadedRoot(world,own.resident());
        }
    }
    private static boolean loaded(ServerWorld world,BlockPos pos){return world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)!=null;}
    private static TransactionCore.Result rejected(String reason){return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,reason);}
    public static TransactionCore.Result migrateLoadedRoot(ServerWorld world,Owner owner){
        BlockPos root=CompositeData.pos(owner.root());if(!loaded(world,root))return rejected("unloaded_assembly_anchor");
        if(!(world.getBlockEntity(root) instanceof CompositeBlockEntity own)||!owner.equals(own.resident()))return rejected("stale_assembly_anchor");
        BlockState state=world.getBlockState(root);if(!(state.getBlock() instanceof CompositeRootBlock block)||!owner.registryId().equals(block.spec.id.toString()))return rejected("not_current_assembly_anchor");
        if(owner.registryId().equals("bloodborne_dw:source_dark_oak_trapdoor_top"))return replace90479(world,owner,state,own.payload());
        if(complete(state))return rejected("already_complete_assembly");
        if(ImportedTypeAliases.needsMigration(state,own.payload()))return rejected("prior_type_migration_pending");
        for(Recipe recipe:CATALOGUE.recipes){Component anchor=recipe.components.get(0);if(!anchor.registryId.equals(owner.registryId())||anchor.pose!=CompositeSpec.poseVariant(state))continue;
            List<ObjectInstance> parts=match(world,owner,state,own.payload(),recipe);if(parts==null)continue;
            BlockState next=Registries.BLOCK.get(new Identifier(recipe.primaryRegistryId)).getDefaultState();next=copyCommon(state,next);next=setPose(next,recipe.installationPose);
            NbtCompound payload=own.payload();NbtList provenance=new NbtList();for(ObjectInstance part:parts){NbtCompound saved=new NbtCompound();saved.put("Owner",CompositeData.owner(part.owner()));saved.put("RootData",CompositeData.data(part.rootData()));provenance.add(saved);}payload.put(PARTS_KEY,provenance);
            NbtCompound marker=new NbtCompound();marker.putInt("Version",VERSION);marker.putString("Recipe",recipe.key);payload.put(PAYLOAD_KEY,marker);
            ObjectInstance after=CompositeRuntime.instance(world,root,next,owner.instanceId(),payload);
            return migrate(world,parts,after,root,recipe.key);
        }
        return rejected("incomplete_or_unmatched_approved_assembly");
    }
    private static List<ObjectInstance> match(ServerWorld world,Owner anchor,BlockState state,NbtCompound payload,Recipe recipe){
        int yaw=state.get(CompositeRootBlock.ROTATION);CompositeSourceShift.Shift actual=CompositeSourceShift.world(state,payload);
        double vertical=payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);
        Component first=recipe.components.get(0);List<ObjectInstance> parts=new ArrayList<>();Set<UUID> unique=new HashSet<>();
        for(Component component:recipe.components){
            // Approved relative roots are vertical; keeping this exact also
            // protects a future non-cardinal recipe from integer rounding.
            var relative=new CompositeSourceShift.Shift(component.relativePosition.x(),component.relativePosition.y(),component.relativePosition.z()).rotate(yaw);
            if(Math.abs(relative.x()-Math.rint(relative.x()))>1e-7||Math.abs(relative.z()-Math.rint(relative.z()))>1e-7)return null;
            BlockPos pos=CompositeData.pos(anchor.root()).add((int)Math.rint(relative.x()),component.relativePosition.y(),(int)Math.rint(relative.z()));
            if(!loaded(world,pos)){defer(world,CompositeData.pos(anchor.root()),pos);return null;}
            if(!(world.getBlockEntity(pos) instanceof CompositeBlockEntity own)||own.resident()==null||!own.resident().registryId().equals(component.registryId))return null;
            int partYaw=(yaw+component.componentYawDelta)&7;
            BlockState found=world.getBlockState(pos);if(!(found.getBlock() instanceof CompositeRootBlock)||CompositeSpec.poseVariant(found)!=component.pose||found.get(CompositeRootBlock.ROTATION)!=partYaw||found.get(CompositeRootBlock.PROFILE)!=state.get(CompositeRootBlock.PROFILE)||found.get(CompositeRootBlock.OPEN)!=state.get(CompositeRootBlock.OPEN)||!unique.add(own.resident().instanceId()))return null;
            NbtCompound partPayload=own.payload();if(ImportedTypeAliases.needsMigration(found,partPayload))return null;
            var shift=CompositeSourceShift.world(found,partPayload);var partRecipeShift=component.sourceShift.rotate(component.componentYawDelta);var expected=new CompositeSourceShift.Shift(partRecipeShift.x()-first.sourceShift.x(),partRecipeShift.y()-first.sourceShift.y(),partRecipeShift.z()-first.sourceShift.z()).rotate(yaw);
            double partVertical=partPayload.getDouble("MountY")+partPayload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);
            if(Math.abs(shift.x()-actual.x()-expected.x())>1e-6||Math.abs(shift.z()-actual.z()-expected.z())>1e-6||Math.abs(shift.y()+partVertical-actual.y()-vertical-expected.y())>1e-6)return null;
            parts.add(CompositeRuntime.instance(world,pos,found,own.resident().instanceId(),partPayload));
        }
        return List.copyOf(parts);
    }
    private static TransactionCore.Result replace90479(ServerWorld world,Owner owner,BlockState state,NbtCompound old){
        if(ImportedTypeAliases.needsMigration(state,old))return rejected("prior_type_migration_pending");
        BlockState next=copyCommon(state,CompositeArchitecture.kindBlock("prototype_double_door").getDefaultState()).with(CompositeRootBlock.VARIANT,0);
        NbtCompound payload=old.copy();var shift=CompositeSourceShift.local(payload);NbtList xyz=new NbtList();xyz.add(NbtDouble.of(shift.x()));xyz.add(NbtDouble.of(shift.y()));xyz.add(NbtDouble.of(shift.z()+.375));payload.put("SourceShift",xyz);
        NbtCompound marker=new NbtCompound();marker.putInt("Version",VERSION);marker.putString("Recipe","EXPLICIT_90479_TO_90001");payload.put(PAYLOAD_KEY,marker);
        ObjectInstance before=CompositeRuntime.instance(world,CompositeData.pos(owner.root()),state,owner.instanceId(),old),after=CompositeRuntime.instance(world,CompositeData.pos(owner.root()),next,owner.instanceId(),payload);
        NbtList originals=new NbtList();NbtCompound saved=new NbtCompound();saved.put("Owner",CompositeData.owner(before.owner()));saved.put("RootData",CompositeData.data(before.rootData()));originals.add(saved);payload.put(PARTS_KEY,originals);after=CompositeRuntime.instance(world,CompositeData.pos(owner.root()),next,owner.instanceId(),payload);
        return migrate(world,List.of(before),after,CompositeData.pos(owner.root()),"EXPLICIT_90479_TO_90001");
    }
    private static TransactionCore.Result migrate(ServerWorld world,List<ObjectInstance> parts,ObjectInstance after,BlockPos anchor,String key){
        TreeSet<Cell> required=new TreeSet<>();for(ObjectInstance part:parts)part.cells().keySet().forEach(offset->required.add(part.owner().root().add(offset)));after.cells().keySet().forEach(offset->required.add(after.owner().root().add(offset)));
        boolean waiting=false;for(Cell cell:List.copyOf(required)){if(!loaded(world,CompositeData.pos(cell))){defer(world,anchor,CompositeData.pos(cell));waiting=true;}for(var contribution:CompositeLedger.get(world).at(cell))required.add(contribution.owner().root());}
        for(Cell cell:required)if(!loaded(world,CompositeData.pos(cell))){defer(world,anchor,CompositeData.pos(cell));waiting=true;}if(waiting)return rejected("unloaded_assembly_owned_cell");
        var result=CompositeRuntime.migrateApprovedAssembly(world,parts,after);
        if(result.outcome()==TransactionCore.Outcome.COMMITTED){for(ObjectInstance part:parts)ImportedCollisionMigration.discardOwner(world,part.owner());}
        else{String warning=key+"|"+after.owner().instanceId()+"|"+result.reason();var warned=WARNED.computeIfAbsent(world,w->new HashSet<>());if(warned.size()<4096&&warned.add(warning))dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Approved assembly migration deferred {} / {}: {}",key,after.owner().instanceId(),result.reason());}
        return result;
    }
    private static void defer(ServerWorld world,BlockPos anchor,BlockPos missing){DEFERRED.computeIfAbsent(world,w->new HashMap<>()).computeIfAbsent(ChunkPos.toLong(missing.getX()>>4,missing.getZ()>>4),chunk->new LinkedHashSet<>()).add(anchor.toImmutable());}
    public static void forget(ServerWorld world){DEFERRED.remove(world);WARNED.remove(world);}
    private static BlockState copyCommon(BlockState old,BlockState next){for(var entry:old.getEntries().entrySet()){Property<?> property=next.getBlock().getStateManager().getProperty(entry.getKey().getName());if(property!=null)next=parsed(next,property,name(entry.getKey(),entry.getValue()));}return next;}
    private static BlockState setPose(BlockState state,int pose){state=state.with(CompositeRootBlock.VARIANT,pose%16);if(state.getBlock() instanceof ImportedArtworkBlock)state=state.with(ImportedArtworkBlock.bankProperty(state),pose/16);else if(pose>=16)throw new IllegalStateException("Unbanked complete assembly pose");return state;}
    @SuppressWarnings({"rawtypes","unchecked"})private static String name(Property property,Comparable value){return property.name(value);}
    private static <T extends Comparable<T>> BlockState parsed(BlockState state,Property<T> property,String value){return property.parse(value).map(parsed->state.with(property,parsed)).orElse(state);}
}
