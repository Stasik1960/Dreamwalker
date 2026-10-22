import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
import java.util.Arrays;
public final class PaletteLinkProbe {
 public static void main(String[] args){try {
  ClassLoader loader=new Knot(EnvType.SERVER).init(new String[]{"nogui"});
  Class<?> type=Class.forName("me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette",false,loader);
  var ctor=Arrays.stream(type.getConstructors()).filter(c->c.getParameterCount()==3).findFirst().orElseThrow();Object palette=ctor.newInstance(null,21,null);
  var entries=type.getDeclaredField("entries");entries.setAccessible(true);var bits=type.getDeclaredField("indexBits");bits.setAccessible(true);
  if(((Object[])entries.get(palette)).length!=4096||bits.getInt(palette)!=21)throw new IllegalStateException("Capacity or ID width mismatch");
  var index=type.getMethod("method_12291",Object.class);var get=Arrays.stream(type.getMethods()).filter(m->m.getParameterCount()==1&&m.getParameterTypes()[0]==int.class&&m.getReturnType()==Object.class).findFirst().orElseThrow();Object[] values=new Object[5000];
  for(int i=0;i<values.length;i++){values[i]=new Object();if((int)index.invoke(palette,values[i])!=i)throw new IllegalStateException("Insertion mismatch "+i);}
  for(int i=0;i<values.length;i++)if(get.invoke(palette,i)!=values[i])throw new IllegalStateException("Lookup mismatch "+i);
  if(((Object[])entries.get(ctor.newInstance(null,4,null))).length!=16)throw new IllegalStateException("Small palette changed");
  Class.forName("net.minecraft.class_3898",false,loader);
  System.out.println("PALETTE_LINK_PASS capacity=4096 bits=21 growth=5000 small=16");System.exit(0);
 }catch(Throwable error){error.printStackTrace();System.exit(1);}}
}
