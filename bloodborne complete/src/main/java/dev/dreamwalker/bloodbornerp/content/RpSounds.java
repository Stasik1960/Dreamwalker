package dev.dreamwalker.bloodbornerp.content;

import com.google.gson.Gson;
import dev.dreamwalker.bloodbornerp.BloodborneRp;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

public final class RpSounds {
 private static final Map<String,SoundEvent> EVENTS=new HashMap<>();
 private RpSounds(){}
 public static void register(){
  try(var reader=new InputStreamReader(RpSounds.class.getResourceAsStream("/assets/bloodborne_rp/sound-catalog.json"),StandardCharsets.UTF_8)){
   for(String name:new Gson().fromJson(reader,String[].class)){
    var id=BloodborneRp.id(name); EVENTS.put(name,Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id)));
   }
  }catch(Exception e){throw new IllegalStateException("Invalid sound catalog",e);}
 }
 public static SoundEvent mob(String id,String event,SoundEvent fallback){
  String family=id.startsWith("huntsman_")?"huntsman":id.equals("small_rat")?"giant_rat":id;
  return EVENTS.getOrDefault("entity."+family+"_"+event,fallback);
 }
}
