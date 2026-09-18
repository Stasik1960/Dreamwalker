package daot.verification;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Finds Java overrides silently lost during the API downgrade. Does not load game classes. */
public class OverrideAudit {
    static Map<String,ClassNode> read(Path path) throws Exception {
        Map<String,ClassNode> result=new HashMap<>();
        if (Files.isDirectory(path)) {
            try(var files=Files.walk(path)) { for(Path f:files.filter(p->p.toString().endsWith(".class")).toList()) add(result,Files.readAllBytes(f)); }
        } else try(JarFile jar=new JarFile(path.toFile())) {
            for(var entry:Collections.list(jar.entries())) if(entry.getName().endsWith(".class"))
                try(var in=jar.getInputStream(entry)) { add(result,in.readAllBytes()); }
        }
        return result;
    }
    static void add(Map<String,ClassNode> result, byte[] bytes) {
        ClassNode node=new ClassNode(); new ClassReader(bytes).accept(node,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES); result.put(node.name,node);
    }
    static boolean inherited(Map<String,ClassNode> all,String parent,String name,String desc,Set<String> visited) {
        if(parent==null||!visited.add(parent))return false;
        ClassNode c=all.get(parent); if(c==null)return false;
        if(c.name.startsWith("net/minecraft/") && c.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)&&(m.access&(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC))==0))return true;
        if(inherited(all,c.superName,name,desc,visited))return true;
        for(String i:c.interfaces) if(inherited(all,i,name,desc,visited))return true;
        return false;
    }
    public static void main(String[] args)throws Exception {
        var old=read(Path.of(args[0]));old.putAll(read(Path.of(args[1])));
        var now=read(Path.of(args[2]));now.putAll(read(Path.of(args[3])));
        int checked=0,changed=0;
        for(String name:new TreeSet<>(old.keySet())) {
            if(!name.startsWith("daot/")||name.contains("/mixin/"))continue;
            ClassNode a=old.get(name),b=now.get(name);if(b==null)continue;
            for(MethodNode m:a.methods) {
                if((m.access&(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC))!=0||m.name.startsWith("<"))continue;
                boolean was=inherited(old,a.superName,m.name,m.desc,new HashSet<>());
                for(String i:a.interfaces) was|=inherited(old,i,m.name,m.desc,new HashSet<>());
                if(!was)continue;checked++;
                boolean present=false;
                for(MethodNode n:b.methods)if(n.name.equals(m.name)) {
                    present|=inherited(now,b.superName,n.name,n.desc,new HashSet<>());
                    for(String i:b.interfaces)present|=inherited(now,i,n.name,n.desc,new HashSet<>());
                }
                if(!present){changed++;System.out.println(name+" :: "+m.name+m.desc);}
            }
        }
        System.out.println("Original Minecraft overrides="+checked+"; removed/renamed/helper-adapted="+changed);
    }
}
