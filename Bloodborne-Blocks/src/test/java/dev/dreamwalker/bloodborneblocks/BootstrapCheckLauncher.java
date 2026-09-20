package dev.dreamwalker.bloodborneblocks;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Runs data-only bootstrap checks with Fabric's development access fix applied
 * to named Minecraft classes, without starting a client or dedicated server.
 */
public final class BootstrapCheckLauncher {
 private BootstrapCheckLauncher() {}

 /**
  * Test-only equivalent of Fabric Registry Sync 2.4.1's BootstrapMixin
  * redirect: run Registries.init() without the later vanilla freeze phase.
  */
 public static void initializeRegistryFixture() {
  try {
   Class<?> registries=Class.forName("net.minecraft.registry.Registries",true,BootstrapCheckLauncher.class.getClassLoader());
   Method init=registries.getDeclaredMethod("init");
   init.setAccessible(true);
   init.invoke(null);
  } catch(ReflectiveOperationException exception) {
   throw new IllegalStateException("Unable to initialize the Fabric registry fixture",exception);
  }
 }

 public static void main(String[] args) throws Throwable {
  if(args.length!=1)throw new IllegalArgumentException("Expected one check main class");
  try(var loader=new BootstrapClassLoader(classpathUrls(),ClassLoader.getSystemClassLoader())) {
   Class<?> check=loader.loadClass(args[0]);
   Method main=check.getMethod("main",String[].class);
   try { main.invoke(null,(Object)new String[0]); }
   catch(InvocationTargetException exception) { throw exception.getCause(); }
  }
 }

 private static URL[] classpathUrls() {
  return java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
   .map(Path::of).map(path->{try{return path.toUri().toURL();}catch(IOException exception){throw new IllegalArgumentException(exception);}})
   .toArray(URL[]::new);
 }

 private static final class BootstrapClassLoader extends URLClassLoader {
  BootstrapClassLoader(URL[] urls,ClassLoader parent) { super(urls,parent); }

  @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
   if(!name.startsWith("net.minecraft.")&&!name.startsWith("dev.dreamwalker.bloodborneblocks."))return super.loadClass(name,resolve);
   Class<?> loaded=findLoadedClass(name);
   if(loaded==null)loaded=findClass(name);
   if(resolve)resolveClass(loaded);
   return loaded;
  }

  @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
   if(!name.startsWith("net.minecraft."))return super.findClass(name);
   String resource=name.replace('.','/')+".class";
   URL location=findResource(resource);
   if(location==null)throw new ClassNotFoundException(name);
   try(InputStream input=location.openStream()) {
    byte[] transformed=fixPackageAccess(input.readAllBytes());
    if(name.equals("net.minecraft.Bootstrap"))transformed=redirectBootstrapFreeze(transformed);
    if(name.equals("net.minecraft.registry.SimpleRegistry"))transformed=bindUnfrozenReferences(transformed);
    return defineClass(name,transformed,0,transformed.length);
   } catch(IOException exception) { throw new ClassNotFoundException(name,exception); }
  }

  private byte[] fixPackageAccess(byte[] bytes) throws ClassNotFoundException {
   try {
    Class<?> visitor=loadClass("org.objectweb.asm.ClassVisitor");
    Class<?> reader=loadClass("org.objectweb.asm.ClassReader");
    Class<?> writer=loadClass("org.objectweb.asm.ClassWriter");
    Object output=writer.getConstructor(int.class).newInstance(0);
    int api=loadClass("org.objectweb.asm.Opcodes").getField("ASM9").getInt(null);
    Object fixer=loadClass("net.fabricmc.loader.impl.transformer.PackageAccessFixer")
     .getConstructor(int.class,visitor).newInstance(api,output);
    Object input=reader.getConstructor(byte[].class).newInstance((Object)bytes);
    reader.getMethod("accept",visitor,int.class).invoke(input,fixer,0);
    return (byte[])writer.getMethod("toByteArray").invoke(output);
   } catch(ReflectiveOperationException exception) {
    throw new ClassNotFoundException("Unable to apply Fabric package access fix",exception);
   }
  }

  private byte[] redirectBootstrapFreeze(byte[] bytes) throws ClassNotFoundException {
   try {
    Class<?> visitor=loadClass("org.objectweb.asm.ClassVisitor");
    Class<?> reader=loadClass("org.objectweb.asm.ClassReader");
    Class<?> writer=loadClass("org.objectweb.asm.ClassWriter");
    Class<?> node=loadClass("org.objectweb.asm.tree.ClassNode");
    Object classNode=node.getConstructor().newInstance();
    Object input=reader.getConstructor(byte[].class).newInstance((Object)bytes);
    reader.getMethod("accept",visitor,int.class).invoke(input,classNode,0);
    int redirects=0;
    for(Object method:(Iterable<?>)node.getField("methods").get(classNode)) {
     Class<?> methodType=method.getClass();
     if(!methodType.getField("name").get(method).equals("initialize")
      ||!methodType.getField("desc").get(method).equals("()V"))continue;
     Object instructions=method.getClass().getField("instructions").get(method);
     Method next=loadClass("org.objectweb.asm.tree.AbstractInsnNode").getMethod("getNext");
     for(Object instruction=instructions.getClass().getMethod("getFirst").invoke(instructions);instruction!=null;instruction=next.invoke(instruction)) {
      if((int)instruction.getClass().getMethod("getOpcode").invoke(instruction)!=184)continue;
      Class<?> type=instruction.getClass();
      if(!type.getName().equals("org.objectweb.asm.tree.MethodInsnNode"))continue;
      if(type.getField("owner").get(instruction).equals("net/minecraft/registry/Registries")
       &&type.getField("name").get(instruction).equals("bootstrap")
       &&type.getField("desc").get(instruction).equals("()V")) {
       type.getField("owner").set(instruction,"dev/dreamwalker/bloodborneblocks/BootstrapCheckLauncher");
       type.getField("name").set(instruction,"initializeRegistryFixture");
       redirects++;
      }
     }
    }
    if(redirects!=1)throw new IllegalStateException("Expected one Registries.bootstrap call, found "+redirects);
    Object output=writer.getConstructor(int.class).newInstance(0);
    node.getMethod("accept",visitor).invoke(classNode,output);
    return (byte[])writer.getMethod("toByteArray").invoke(output);
   } catch(ReflectiveOperationException exception) {
    throw new ClassNotFoundException("Unable to apply Fabric bootstrap registry redirect",exception);
   }
  }

  private byte[] bindUnfrozenReferences(byte[] bytes) throws ClassNotFoundException {
   try {
    Class<?> visitor=loadClass("org.objectweb.asm.ClassVisitor");
    Class<?> reader=loadClass("org.objectweb.asm.ClassReader");
    Class<?> writer=loadClass("org.objectweb.asm.ClassWriter");
    Class<?> node=loadClass("org.objectweb.asm.tree.ClassNode");
    Class<?> abstractInsn=loadClass("org.objectweb.asm.tree.AbstractInsnNode");
    Class<?> insnList=loadClass("org.objectweb.asm.tree.InsnList");
    Object classNode=node.getConstructor().newInstance();
    Object input=reader.getConstructor(byte[].class).newInstance((Object)bytes);
    reader.getMethod("accept",visitor,int.class).invoke(input,classNode,0);
    int injections=0;
    for(Object method:(Iterable<?>)node.getField("methods").get(classNode)) {
     Class<?> methodType=method.getClass();
     if(!methodType.getField("name").get(method).equals("set")
      ||!methodType.getField("desc").get(method).equals("(ILnet/minecraft/registry/RegistryKey;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/registry/entry/RegistryEntry$Reference;"))continue;
     Object instructions=methodType.getField("instructions").get(method);
     Method next=abstractInsn.getMethod("getNext");
     for(Object instruction=instructions.getClass().getMethod("getFirst").invoke(instructions);instruction!=null;instruction=next.invoke(instruction)) {
      if((int)instruction.getClass().getMethod("getOpcode").invoke(instruction)!=176)continue;
      Object injected=insnList.getConstructor().newInstance();
      insnList.getMethod("add",abstractInsn).invoke(injected,loadClass("org.objectweb.asm.tree.InsnNode").getConstructor(int.class).newInstance(89));
      insnList.getMethod("add",abstractInsn).invoke(injected,loadClass("org.objectweb.asm.tree.VarInsnNode").getConstructor(int.class,int.class).newInstance(25,3));
      insnList.getMethod("add",abstractInsn).invoke(injected,loadClass("org.objectweb.asm.tree.MethodInsnNode").getConstructor(int.class,String.class,String.class,String.class,boolean.class).newInstance(182,"net/minecraft/registry/entry/RegistryEntry$Reference","setValue","(Ljava/lang/Object;)V",false));
      instructions.getClass().getMethod("insertBefore",abstractInsn,insnList).invoke(instructions,instruction,injected);
      injections++;
     }
    }
    if(injections!=1)throw new IllegalStateException("Expected one SimpleRegistry.set return, found "+injections);
    Object output=writer.getConstructor(int.class).newInstance(0);
    node.getMethod("accept",visitor).invoke(classNode,output);
    return (byte[])writer.getMethod("toByteArray").invoke(output);
   } catch(ReflectiveOperationException exception) {
    throw new ClassNotFoundException("Unable to apply Fabric SimpleRegistry binding",exception);
   }
  }
 }
}
