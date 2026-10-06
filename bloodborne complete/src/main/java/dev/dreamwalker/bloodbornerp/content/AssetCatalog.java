package dev.dreamwalker.bloodbornerp.content;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
public final class AssetCatalog {
 private static Map<String, AssetSpec> specs;
 private AssetCatalog() {}
 public static void load() {
  if(specs!=null) return;
  var input=AssetCatalog.class.getResourceAsStream("/assets/bloodborne_rp/catalog.json");
  if(input==null) throw new IllegalStateException("Missing RP catalog");
  try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
   Map<String,AssetSpec> parsed=new Gson().fromJson(reader,new TypeToken<Map<String,AssetSpec>>(){}.getType());
   specs=Collections.unmodifiableMap(parsed);
  } catch(Exception e) { throw new IllegalStateException("Invalid RP catalog",e); }
 }
 public static AssetSpec get(String id) { load(); var spec=specs.get(id); if(spec==null) throw new IllegalArgumentException("Unknown RP asset: "+id); return spec; }
 public static Map<String,AssetSpec> all() { load(); return specs; }
}
