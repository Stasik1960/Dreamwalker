package daot.verification;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.logging.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.ReEntranceLock;

/** Bytecode-only host: never starts Minecraft or defines transformed game classes. */
public final class OfflineMixinService implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    private static final Map<String,byte[]> resources=new HashMap<>();
    public static void readArchive(Path path) throws IOException { readArchive(Files.readAllBytes(path)); }
    private static void readArchive(byte[] jar) throws IOException {
        List<byte[]> nested=new ArrayList<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(jar))) {
            for(ZipEntry e;(e=zip.getNextEntry())!=null;) {
                String name=e.getName();
                if(name.endsWith(".class")||name.endsWith(".json")) resources.putIfAbsent(name,zip.readAllBytes());
                else if(name.startsWith("META-INF/jars/")&&name.endsWith(".jar")) nested.add(zip.readAllBytes());
            }
        }
        for(byte[] child:nested)readArchive(child);
    }
    private final ReEntranceLock lock = new ReEntranceLock(1);
    public IMixinTransformerFactory factory;
    public String getName() { return "AoT offline bytecode verification"; }
    public boolean isValid() { return Boolean.getBoolean("daot.offline.mixincheck"); }
    public void prepare() {}
    public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.PREINIT; }
    public void offer(IMixinInternal item) { if (item instanceof IMixinTransformerFactory f) factory=f; }
    public void init() {}
    public void beginPhase() {}
    public void checkEnv(Object boot) {}
    public ReEntranceLock getReEntranceLock() { return lock; }
    public IClassProvider getClassProvider() { return this; }
    public IClassBytecodeProvider getBytecodeProvider() { return this; }
    public ITransformerProvider getTransformerProvider() { return this; }
    public IClassTracker getClassTracker() { return this; }
    public IMixinAuditTrail getAuditTrail() { return null; }
    public Collection<String> getPlatformAgents() { return List.of(); }
    public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("offline-verification"); }
    public Collection<IContainerHandle> getMixinContainers() { return List.of(); }
    public InputStream getResourceAsStream(String name) {
        byte[] bytes=resources.get(name);
        return bytes==null?getClass().getClassLoader().getResourceAsStream(name):new ByteArrayInputStream(bytes);
    }
    public String getSideName() { return "CLIENT"; }
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() { return MixinEnvironment.CompatibilityLevel.JAVA_17; }
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() { return MixinEnvironment.CompatibilityLevel.JAVA_21; }
    public ILogger getLogger(String name) { return new LoggerAdapterConsole(name); }
    public URL[] getClassPath() { return new URL[0]; }
    public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name,false); }
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        if (name.startsWith("net.minecraft.")) throw new ClassNotFoundException("Game class loading disabled in bytecode test: "+name);
        return Class.forName(name,initialize,getClass().getClassLoader());
    }
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name,initialize); }
    public ClassNode getClassNode(String name) throws ClassNotFoundException,IOException { return getClassNode(name,true); }
    public ClassNode getClassNode(String name,boolean transform) throws ClassNotFoundException,IOException { return getClassNode(name,transform,0); }
    public ClassNode getClassNode(String name,boolean transform,int flags) throws ClassNotFoundException,IOException {
        try(InputStream in=getResourceAsStream(name.replace('.','/')+".class")) {
            if(in==null)throw new ClassNotFoundException(name);
            ClassNode node=new ClassNode(); new ClassReader(in).accept(node,flags);return node;
        }
    }
    public Collection<ITransformer> getTransformers() { return List.of(); }
    public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
    public void addTransformerExclusion(String name) {}
    public void registerInvalidClass(String name) {}
    public boolean isClassLoaded(String name) { return false; }
    public String getClassRestrictions(String name) { return ""; }
}
