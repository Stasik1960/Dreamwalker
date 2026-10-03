import com.google.gson.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import net.minecraft.client.renderer.block.model.BlockModel;

class MinecraftModelProbe {
  public static void main(String[] args) throws Exception {
    List<Map<String,Object>> output = new ArrayList<>();
    for (String file:args) {
      int count=0; List<String> failures=new ArrayList<>();String zipError=null;ZipFile opened;
      try {opened=new ZipFile(file);}catch(ZipException ex){zipError=ex.toString();opened=new ZipFile(new File(file),Charset.forName("IBM437"));}
      try(ZipFile zip=opened){
        for(Enumeration<? extends ZipEntry> es=zip.entries();es.hasMoreElements();){
          ZipEntry e=es.nextElement();String name=e.getName();
          if(!name.contains("/models/")||!name.endsWith(".json"))continue;
          count++;
          try(Reader r=new InputStreamReader(zip.getInputStream(e),StandardCharsets.UTF_8)){BlockModel.m_111461_(r);}catch(Exception ex){failures.add(name+": "+ex);}
        }
      }
      Map<String,Object> result=new LinkedHashMap<>();result.put("file",file);result.put("defaultJavaZipOpenError",zipError);result.put("modelsParsed",count);result.put("modelFailures",failures);output.add(result);
    }
    System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(output));
  }
}
