package daot.verification;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Mirrors Fabric's package-access fix for a standalone, named-namespace test JVM. */
public final class DevelopmentAccessAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> redef,
                    ProtectionDomain domain, byte[] bytes) {
                if (name == null || !name.startsWith("net/minecraft/")) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    private int access(int flags) {
                        return (flags & Opcodes.ACC_PRIVATE) == 0
                                ? (flags & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC : flags;
                    }
                    @Override public void visit(int v, int a, String n, String s, String p, String[] i) {
                        super.visit(v, access(a), n, s, p, i);
                    }
                    @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) {
                        return super.visitField(access(a), n, d, s, v);
                    }
                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                        MethodVisitor method = super.visitMethod(access(a), n, d, s, e);
                        if (!name.equals("net/minecraft/Bootstrap") || !n.equals("initialize")) return method;
                        return new MethodVisitor(Opcodes.ASM9, method) {
                            @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                                if (owner.equals("net/minecraft/registry/Registries") && method.equals("bootstrap"))
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "daot/verification/BackportVerification", "registerFixtures", "()V", false);
                                super.visitMethodInsn(opcode, owner, method, desc, itf);
                            }
                        };
                    }
                    @Override public void visitInnerClass(String n, String o, String i, int a) {
                        super.visitInnerClass(n, o, i, access(a));
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }
}
