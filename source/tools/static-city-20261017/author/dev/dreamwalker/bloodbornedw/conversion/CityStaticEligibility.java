package dev.dreamwalker.bloodbornedw.conversion;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;

/** Author-only preflight. Never saved in the game; never stages rollback or writes. */
final class CityStaticEligibility {
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final Set<String> EXCLUDED_FUNCTIONAL_INPUT_FAMILIES=loadExcludedFunctionalInputFamilies();
    private static Set<String> loadExcludedFunctionalInputFamilies(){
        String assemblyName="/bloodborne_dw/imported-assembly-v126.json";
        String aliasesName="/bloodborne_dw/imported-type-aliases-v127.json";
        try(InputStream assemblyInput=CityStaticEligibility.class.getResourceAsStream(assemblyName);
            InputStream aliasesInput=CityStaticEligibility.class.getResourceAsStream(aliasesName)){
            need(assemblyInput!=null&&aliasesInput!=null,"Missing static source-scope metadata");
            JsonObject assembly=JsonParser.parseReader(new InputStreamReader(assemblyInput,StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject aliases=JsonParser.parseReader(new InputStreamReader(aliasesInput,StandardCharsets.UTF_8)).getAsJsonObject();
            need(assembly.get("schemaVersion").getAsInt()==1&&aliases.get("schemaVersion").getAsInt()==1,"Static source-scope metadata schema changed");
            TreeSet<String> result=new TreeSet<>();
            JsonArray recipes=assembly.getAsJsonArray("recipes");
            need(recipes!=null&&!recipes.isEmpty(),"Empty imported assembly source scope");
            for(var raw:recipes){
                JsonObject recipe=raw.getAsJsonObject();
                JsonArray components=recipe.getAsJsonArray("components");
                need(components!=null&&!components.isEmpty(),"Assembly recipe has no source components");
                for(var part:components)result.add(inputFamily(part.getAsJsonObject().get("registryId").getAsString()));
            }
            JsonObject explicit=assembly.getAsJsonObject("explicitDoorReplacement");
            need(explicit!=null&&explicit.has("oldRegistryId"),"Missing explicit assembly replacement metadata");
            result.add(inputFamily(explicit.get("oldRegistryId").getAsString()));
            JsonArray mappings=aliases.getAsJsonArray("mappings");
            need(mappings!=null&&!mappings.isEmpty(),"Empty imported type-alias source scope");
            for(var raw:mappings)result.add(inputFamily(raw.getAsJsonObject().get("oldRegistryId").getAsString()));
            need(!result.isEmpty(),"Empty functional input-family source scope");
            return Set.copyOf(result);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    private static String inputFamily(String registry){
        need(registry!=null&&registry.startsWith("bloodborne_dw:source_")&&net.minecraft.util.Identifier.tryParse(registry)!=null,"Invalid functional input registry family");
        return registry;
    }
    private static Cell cell(JsonArray a){need(a.size()==3,"Static source coordinate");return new Cell(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static String sha(Path p)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream stream=Files.newInputStream(p)){byte[] bytes=new byte[1024*1024];for(int n;(n=stream.read(bytes))>=0;)hash.update(bytes,0,n);}
        return HexFormat.of().formatHex(hash.digest());
    }
    private static JsonObject json(Path p)throws IOException {
        need(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Files.size(p)<=4*1024*1024,"Invalid completed conversion receipt");
        try(Reader reader=Files.newBufferedReader(p,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    static void validateCompletedSource(Path target,Path plan,JsonObject manifest,JsonObject marker,String production)throws Exception {
        need(manifest.has("staticArchitecturePostConversion"),"No explicit city static post-conversion provenance");
        JsonObject proof=manifest.getAsJsonObject("staticArchitecturePostConversion");
        need(proof.get("schemaVersion").getAsInt()==1,"City static provenance schema");
        String sourceGame=proof.get("sourceGameSha256").getAsString();
        String operationCatalog=proof.get("operationCatalogSha256").getAsString();
        String sourceCatalog=proof.get("sourceCatalogSha256").getAsString();
        need(!sourceGame.equals(production),"Static data migration must distinguish source and production game artifacts");
        need(!sourceCatalog.equals(operationCatalog),"Static operation must distinguish source and expanded catalogs");
        need(marker.get("sourceGameSha256").getAsString().equals(sourceGame)&&marker.get("productionGameSha256").getAsString().equals(production),"Static marker game binding changed");
        need(marker.get("sourceCatalogSha256").getAsString().equals(sourceCatalog)&&marker.get("operationCatalogSha256").getAsString().equals(operationCatalog),"Static marker catalog binding changed");
        Path approval=Path.of(marker.get("staticArchitectureDataMigrationAuthorizationPath").getAsString()).toRealPath();
        need(sha(approval).equals(marker.get("staticArchitectureDataMigrationAuthorizationSha256").getAsString()),"Static data-migration authorization changed");
        JsonObject approved=json(approval);
        need(approved.get("status").getAsString().equals("AUTHORIZED_EXPERIMENTAL_STATIC_DATA_MIGRATION")&&approved.get("allowCityStaticArchitecture").getAsBoolean()&&approved.get("explicitDataMigrationAuthorized").getAsBoolean(),"Explicit experimental static data-migration authorization missing");
        need(!approved.get("gameplayVerified").getAsBoolean()&&(!approved.has("edgeAppearanceResolved")||!approved.get("edgeAppearanceResolved").getAsBoolean()),"Experimental migration must not claim gameplay or resolved edge appearance");
        need(approved.has("knownUnresolvedEdgeCaveat")&&!approved.get("knownUnresolvedEdgeCaveat").getAsString().isBlank(),"Known unresolved edge caveat must be recorded");
        need(approved.get("sourceGameSha256").getAsString().equals(sourceGame)&&approved.get("productionGameSha256").getAsString().equals(production),"Static authorization game binding changed");
        need(approved.get("sourceCatalogSha256").getAsString().equals(sourceCatalog)&&approved.get("operationCatalogSha256").getAsString().equals(operationCatalog),"Static authorization catalog binding changed");
        need(approved.get("operationId").getAsString().equals(marker.get("operationId").getAsString()),"Static authorization operation binding changed");
        need(Path.of(approved.get("targetWorldPath").getAsString()).toRealPath().equals(target)&&approved.get("runtimeManifestSha256").getAsString().equals(sha(plan.resolve("manifest.json"))),"Static approval belongs to another target/plan");
        var origin=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("dreamwalker_full_conversion_author").orElseThrow().getOrigin().getPaths();
        need(origin.size()==1&&sha(origin.get(0)).equals(marker.get("cityStaticAuthorJarSha256").getAsString())&&sha(origin.get(0)).equals(approved.get("authorJarSha256").getAsString()),"Static approval/loaded author checksum mismatch");
        need(approved.getAsJsonArray("protectedWorldPaths").size()>0,"Static approval must identify preserved source/backup worlds");
        for(var raw:approved.getAsJsonArray("protectedWorldPaths")){Path protectedWorld=Path.of(raw.getAsString()).toAbsolutePath().normalize();need(!target.startsWith(protectedWorld)&&!protectedWorld.startsWith(target),"Static target overlaps a protected source/backup world");}
        Path cp=target.resolve("dw-conversion-checkpoint.json"),result=target.resolve("dw-conversion-result.json");
        need(sha(cp).equals(proof.get("sourceCheckpointSha256").getAsString())&&sha(result).equals(proof.get("sourceResultSha256").getAsString()),"Completed original conversion receipt changed");
        need(marker.get("staticArchitectureSourceCheckpointSha256").getAsString().equals(sha(cp)),"Static marker/source checkpoint mismatch");
        JsonObject checkpoint=json(cp),completed=json(result);
        for(JsonObject receipt:List.of(checkpoint,completed)) {
            need(receipt.get("status").getAsString().equals("PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED")&&receipt.get("phase").getAsInt()==3&&receipt.get("line").getAsLong()==0,"Static city phase requires completed original conversion");
            need(receipt.get("phaseCount").getAsInt()==3&&!receipt.get("staticArchitecture").getAsBoolean()&&!receipt.get("staticGrassMasonry").getAsBoolean(),"Wrong original conversion phase family");
            need(receipt.get("productionJarSha256").getAsString().equals(sourceGame),"Original conversion source game changed");
            need(receipt.get("operationId").getAsString().equals(proof.get("sourceOperationId").getAsString()),"Original conversion operation changed");
            need(receipt.get("runtimeManifestSha256").getAsString().equals(proof.get("sourceRuntimeManifestSha256").getAsString()),"Original conversion plan changed");
            need(receipt.get("committed").getAsLong()+receipt.get("alreadyPresent").getAsLong()==proof.get("sourceConvertedRows").getAsLong(),"Original completed source count changed");
        }
        Path original=Path.of(proof.get("sourceRuntimePlanPath").getAsString()).toRealPath();
        need(sha(original.resolve("manifest.json")).equals(proof.get("sourceRuntimeManifestSha256").getAsString()),"Pinned original runtime manifest changed");
        JsonObject originalManifest=json(original.resolve("manifest.json"));
        need(originalManifest.get("sourcePlanSha256").equals(manifest.get("sourcePlanSha256"))&&originalManifest.getAsJsonObject("sourceHashes").equals(manifest.getAsJsonObject("sourceHashes")),"Static operation source identity differs");
        need(operationCatalog.equals(StaticArchitectureCatalog.resourceSha256()),"Expanded static operation catalog changed");
        // These streams are separate operation files, never a replacement of original prepared data.
        for(String name:List.of("owners.jsonl.gz","whole-objects.jsonl.gz","entities.jsonl.gz"))
            try(InputStream input=new GZIPInputStream(Files.newInputStream(plan.resolve(name)))){need(input.read()==-1,"City static operation must not replay original conversion streams");}
    }
    /** null = eligible/exact replay; explicit reason = unsupported complete component, no writes. */
    static String inspect(ServerWorld world,JsonObject row) {
        return inspect(world,row,scope->new StaticArchitectureWorldAccess(world,scope,true,0));
    }
    /** Package-local native test seam; production always supplies the unchanged scoped adapter. */
    static String inspect(ServerWorld world,JsonObject row,java.util.function.Function<Set<Cell>,StaticArchitectureWorldAccess> factory) {
        need(world.getServer().isOnThread(),"Static city preflight thread");
        TreeMap<Cell,Integer> placements=new TreeMap<>();
        for(var raw:row.getAsJsonArray("cells")){
            JsonObject item=raw.getAsJsonObject();Cell at=cell(item.getAsJsonArray("position"));int recipe=item.get("recipe").getAsInt();
            need(placements.put(at,recipe)==null,"Repeated static city cell");StaticArchitectureCatalog.state(recipe);
        }
        need(!placements.isEmpty()&&placements.size()<=8192,"Static city component cell limit");
        var access=factory.apply(Set.copyOf(placements.keySet()));
        boolean all=true,any=false;
        for(var item:placements.entrySet()){
            need(access.isLoaded(item.getKey())&&access.inBounds(item.getKey()),"Static preflight would load an unbounded chunk");
            boolean match=world.getBlockState(CompositeData.pos(item.getKey())).equals(StaticArchitectureCatalog.state(item.getValue()));all&=match;any|=match;
        }
        need(!any||all,"Partially migrated city component must not be skipped or guessed");
        if(all)return null; // unchanged strict prepare validates replay has no BE/ledger.
        need(row.getAsJsonArray("owners").size()>0&&row.getAsJsonArray("owners").size()<=4096,"Static city owner limit");
        for(var raw:row.getAsJsonArray("owners")){
            JsonObject source=raw.getAsJsonObject();
            if(EXCLUDED_FUNCTIONAL_INPUT_FAMILIES.contains(source.get("registryId").getAsString()))return "EXCLUDED_FUNCTIONAL_INPUT_FAMILY";
        }
        Map<Owner,StaticArchitectureCatalog.Pose> selected=new HashMap<>();Map<Cell,Set<Owner>> expected=new TreeMap<>();
        // HARD VALIDATION OF ALL KNOWN ROOTS COMES BEFORE ANY UNSUPPORTED SKIP.
        for(var raw:row.getAsJsonArray("owners")){
            JsonObject source=raw.getAsJsonObject();var pose=StaticArchitectureCatalog.pose(source.get("poseKey").getAsString());Cell root=cell(source.getAsJsonArray("position"));
            Owner owner=new Owner(UUID.fromString(source.get("instanceUuid").getAsString()),source.get("registryId").getAsString(),root);
            need(selected.put(owner,pose)==null&&placements.containsKey(root),"Duplicate or omitted static city root");
            var pos=CompositeData.pos(root);need(world.getBlockState(pos).equals(pose.state()),"Known static city source pose changed: "+root);
            need(world.getBlockEntity(pos) instanceof CompositeBlockEntity,"Known static city source entity missing: "+root);
            var entity=(CompositeBlockEntity)world.getBlockEntity(pos);need(owner.equals(entity.resident()),"Known static city source UUID/type/root changed: "+root);
            NbtCompound payload=entity.payload();
            need(Set.of("ImportedTypeVersion","SourceShift","MountY").containsAll(payload.getKeys()),"Known static city payload has unreviewed keys: "+root);
            need(payload.get("ImportedTypeVersion") instanceof NbtInt&&payload.getInt("ImportedTypeVersion")==ImportedTypeAliases.VERSION,"Known static city payload version/type changed: "+root);
            if(payload.contains("SourceShift")){
                need(payload.get("SourceShift") instanceof NbtList,"Known source shift NBT type changed");var list=(NbtList)payload.get("SourceShift");
                need(list.size()==3&&list.get(0) instanceof NbtDouble&&list.get(1) instanceof NbtDouble&&list.get(2) instanceof NbtDouble,"Known source shift must keep three typed doubles");
            }
            need(!payload.contains("MountY")||payload.get("MountY") instanceof NbtDouble,"Known source mount NBT type changed");
            var shift=CompositeSourceShift.local(payload);var wanted=pose.shift();
            need(shift.x()==wanted.x()&&shift.y()==wanted.y()&&shift.z()==wanted.z()&&payload.getDouble("MountY")==pose.mountY(),"Known source shift/mount changed: "+root);
            for(Cell relative:pose.footprint().keySet()){
                Cell at=root.add(relative);need(placements.containsKey(at),"Static city operation omitted source helper");expected.computeIfAbsent(at,k->new HashSet<>()).add(owner);
            }
        }
        need(expected.keySet().equals(placements.keySet()),"Static city operation added unrelated cells");
        String unsupported=null;
        for(Cell at:placements.keySet()){
            CellSnapshot before=access.read(at);Set<Owner> actual=new HashSet<>(before.guests());if(before.resident()!=null)actual.add(before.resident());
            need(actual.containsAll(expected.get(at)),"Known static city source membership missing: "+at);
            if(!actual.equals(expected.get(at)))unsupported="UNSUPPORTED_UNSELECTED_OWNER_MEMBERSHIP";
            if(before.kind()==CellSnapshot.Kind.FOREIGN)unsupported="UNSUPPORTED_NATIVE_FOREIGN_CELL";
            if(before.kind()==CellSnapshot.Kind.HELPER)need(CompositeData.nbt(before.data().blockEntityNbt()).isEmpty(),"Known helper payload changed");
            for(var contribution:CompositeRuntime.contributions(world,CompositeData.pos(at))) {
                var pose=selected.get(contribution.owner());if(pose==null)continue;
                need(Objects.equals(contribution.shape(),pose.footprint().get(at.subtract(contribution.owner().root()))),"Known static city helper geometry changed: "+at);
            }
        }
        return unsupported;
    }
    private CityStaticEligibility(){}
}
