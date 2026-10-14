package dev.dreamwalker.bloodbornedw.conversionqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;

/** Extra entrypoint in a separately named QA author only, never production. */
public final class ConversionResumeProbe implements ModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public void onInitialize(){ServerLifecycleEvents.SERVER_STARTED.register(server->{
        Path marker=Path.of("dw-conversion-resume-probe.json");if(!Files.isRegularFile(marker))return;
        try { run(server,JsonParser.parseString(Files.readString(marker)).getAsJsonObject()); }
        catch(Throwable failure){try{Files.writeString(Path.of("dw-conversion-resume-proof-error.txt"),failure.toString());}catch(Exception ignored){}throw new IllegalStateException("Bounded converter resume proof failed",failure);}
    });}
    private static void run(MinecraftServer server,JsonObject input)throws Exception{
        check(input.get("fixtureOnly").getAsBoolean(),"The resume probe requires a bounded fixture");
        Class<?> author=Class.forName("dev.dreamwalker.bloodbornedw.conversion.FullMapConversionAuthor");
        Field jobs=author.getDeclaredField("JOBS");jobs.setAccessible(true);
        Object job=((Map<?,?>)jobs.get(null)).get(server);check(job!=null,"Real author did not initialize a fixture job");
        Class<?> type=job.getClass();check((boolean)get(type,job,"fixture"),"Only the real fixture mode is allowed");
        check((int)get(type,job,"phase")==0&&(long)get(type,job,"line")==0L,"Durable replay cursor must start at phase0/line0");
        Path checkpoint=(Path)get(type,job,"checkpoint");var cursor=JsonParser.parseString(Files.readString(checkpoint)).getAsJsonObject();
        check(cursor.get("phase").getAsInt()==0&&cursor.get("line").getAsLong()==0,"Actual persisted checkpoint must be zero");
        Method owner=method(type,"owner",JsonObject.class);Method release=method(type,"releaseTickets");
        var rows=input.getAsJsonArray("owners");check(rows.size()==2,"Exactly alias and banked source rows are required");
        ServerWorld world=server.getOverworld();var outcomes=new JsonArray();long installed=0,replayed=0;
        for(int index=0;index<rows.size();index++){
            JsonObject row=rows.get(index).getAsJsonObject();BlockPos pos=position(row);
            check(!invokeOwner(owner,job,row),"First real owner installation was not fresh");installed++;
            BlockState state=world.getBlockState(pos);CompositeBlockEntity saved=entity(world,pos);
            check(saved.resident().instanceId().toString().equals(row.get("instanceUuid").getAsString()),"Fresh UUID was changed");
            if(index==0){
                check(saved.resident().registryId().equals("bloodborne_dw:source_bricks"),"Fresh alias did not canonicalize");
                check(state.get(CompositeRootBlock.VARIANT)==1,"Canonical alias must select its exact appended pose1");
                check(!saved.payload().contains("MountY"),"Fresh source placement must remove obsolete non-window MountY");
                check(saved.payload().getCompound("AuditTyped").getLong("OpaqueLong")==Long.MIN_VALUE,"Typed long was changed");
                check(saved.payload().getCompound("AuditTyped").contains("OpaqueByte",NbtElement.BYTE_TYPE),"Typed byte was changed");
            }else check(property(state,"art_bank").equals("7")&&state.get(CompositeRootBlock.VARIANT)==2,"Banked source pose114 was reinterpreted");
            NbtCompound before=saved.createNbtWithIdentifyingData();release.invoke(job);server.save(false,true,true);
            check(JsonParser.parseString(Files.readString(checkpoint)).getAsJsonObject().get("line").getAsLong()==0,"Native save unexpectedly advanced durable cursor");
            // Actual Minecraft binary NBT codec and registered BE factory.
            // This is a native BE reload; it is not a second server process.
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write(before,new DataOutputStream(bytes));
            NbtCompound decoded=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            check(before.equals(decoded),"Native binary NBT changed typed saved data");
            BlockEntity restored=BlockEntity.createFromNbt(pos,state,decoded);check(restored instanceof CompositeBlockEntity,"Native factory did not rebuild the owner BE");
            world.removeBlockEntity(pos);world.addBlockEntity(restored);
            check(entity(world,pos).createNbtWithIdentifyingData().equals(before),"Native owner reload changed registry/UUID/root/typed NBT");
            check(invokeOwner(owner,job,row),"Exact raw input alias/banked row failed replay after native reload");replayed++;
            var outcome=new JsonObject();outcome.addProperty("registryId",row.get("registryId").getAsString());outcome.addProperty("savedRegistryId",entity(world,pos).resident().registryId());
            if(index==0){
                var current=entity(world,pos);NbtCompound original=current.payload(),changed=original.copy();
                NbtList shift=changed.getList("SourceShift",NbtElement.DOUBLE_TYPE);shift.set(0,NbtDouble.of(shift.getDouble(0)+.25));
                changed.put("SourceShift",shift);current.set(current.resident(),current.contributions(),changed);
                String rejected=reject(owner,job,row);check(rejected.contains("typed payload changed: SourceShift"),"Altered SourceShift was not precisely rejected: "+rejected);
                current.set(current.resident(),current.contributions(),original);outcome.addProperty("alteredSourceShiftRejected",true);
            }else{
                BlockState changed=with(state,"art_bank","6");world.setBlockState(pos,changed,2);
                String rejected=reject(owner,job,row);check(rejected.contains("source state changed before resume"),"Altered bank was not precisely rejected: "+rejected);
                world.setBlockState(pos,state,2);outcome.addProperty("alteredArtBankRejected",true);
            }
            check(invokeOwner(owner,job,row),"Restoring exact input did not restore idempotent replay");replayed++;outcome.addProperty("status","PASS");outcomes.add(outcome);
        }
        JsonObject report=new JsonObject();report.addProperty("status","PASS_NATIVE_CONVERTER_ALIAS_REPLAY_TYPED_NBT_BANK_AND_SHIFT_GUARDS");
        report.addProperty("productionJarSha256",(String)get(type,job,"productionSHA"));report.addProperty("fixtureOnly",true);
        report.addProperty("freshInstallations",installed);report.addProperty("exactReplays",replayed);report.addProperty("durableCursorPhase0Line0Verified",true);
        report.addProperty("reloadScope","Minecraft binary NBT codec + registered BlockEntity factory in one native server; separate process/disk chunk reload is not claimed");
        report.addProperty("ownerApiScope","Actual private FullMapConversionAuthor.Job.owner called against a real initialized fixture job");
        report.addProperty("originalCityOpened",false);report.addProperty("originalCityConverted",false);report.add("cases",outcomes);
        Files.writeString(Path.of("dw-conversion-resume-proof.json"),JSON.toJson(report));
        set(type,job,"committed",installed);set(type,job,"alreadyPresent",replayed);set(type,job,"phase",3);set(type,job,"line",0L);
        method(type,"durable",String.class).invoke(job,"PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED");
        method(type,"finish",String.class).invoke(job,"PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED");
    }
    private static boolean invokeOwner(Method method,Object job,JsonObject row)throws Exception{return (boolean)method.invoke(job,row);}
    private static String reject(Method method,Object job,JsonObject row)throws Exception{
        try{invokeOwner(method,job,row);throw new IllegalStateException("Changed saved owner was incorrectly accepted");}
        catch(InvocationTargetException error){check(error.getCause() instanceof IllegalStateException,"Unexpected rejection exception");return error.getCause().getMessage();}
    }
    private static Method method(Class<?> type,String name,Class<?>...args)throws Exception{Method result=type.getDeclaredMethod(name,args);result.setAccessible(true);return result;}
    private static Object get(Class<?> type,Object object,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void set(Class<?> type,Object object,String name,Object value)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static CompositeBlockEntity entity(ServerWorld world,BlockPos pos){check(world.getBlockEntity(pos) instanceof CompositeBlockEntity,"Owner BE missing");return (CompositeBlockEntity)world.getBlockEntity(pos);}
    private static BlockPos position(JsonObject row){var p=row.getAsJsonArray("position");return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
    @SuppressWarnings({"rawtypes","unchecked"})private static String property(BlockState state,String name){Property p=state.getBlock().getStateManager().getProperty(name);check(p!=null,"Missing state property "+name);return p.name(state.get(p));}
    @SuppressWarnings({"rawtypes","unchecked"})private static BlockState with(BlockState state,String name,String value){Property p=state.getBlock().getStateManager().getProperty(name);return state.with(p,(Comparable)p.parse(value).orElseThrow());}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
