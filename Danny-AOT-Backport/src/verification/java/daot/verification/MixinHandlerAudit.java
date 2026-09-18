package daot.verification;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Offline descriptor audit for Mixin injector handlers. Does not load Minecraft classes. */
public final class MixinHandlerAudit {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
    private static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String CIR = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
    private static final String ARGS = "Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;";

    private final Map<String, ClassNode> mixins = new HashMap<>(), targets = new HashMap<>();
    private int mixinClasses, handlers, targetMethods, injects, redirects, modifiers, capturedLocals, errors;

    private static void add(Map<String, ClassNode> out, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        out.put(node.name, node);
    }

    private static void read(Map<String, ClassNode> out, Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var files = Files.walk(path)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) add(out, Files.readAllBytes(file));
            }
        } else {
            try (JarFile jar = new JarFile(path.toFile())) {
                for (var entry : Collections.list(jar.entries())) if (entry.getName().endsWith(".class")) {
                    try (InputStream in = jar.getInputStream(entry)) { add(out, in.readAllBytes()); }
                }
            }
        }
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations != null) for (AnnotationNode annotation : annotations) if (descriptor.equals(annotation.desc)) return annotation;
        return null;
    }

    private static AnnotationNode annotation(ClassNode owner, String descriptor) {
        AnnotationNode result = annotation(owner.visibleAnnotations, descriptor);
        return result != null ? result : annotation(owner.invisibleAnnotations, descriptor);
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        AnnotationNode result = annotation(method.visibleAnnotations, descriptor);
        return result != null ? result : annotation(method.invisibleAnnotations, descriptor);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static AnnotationNode firstAnnotation(Object value) {
        if (value instanceof AnnotationNode annotation) return annotation;
        if (value instanceof List<?> list && !list.isEmpty()) return (AnnotationNode) list.get(0);
        return null;
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (value instanceof String string) return List.of(string);
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) result.add((String) item);
        return result;
    }

    private static List<String> targetNames(AnnotationNode mixin) {
        List<String> result = new ArrayList<>();
        Object classes = value(mixin, "value");
        if (classes instanceof List<?> list) for (Object entry : list) result.add(((Type) entry).getInternalName());
        else if (classes instanceof Type type) result.add(type.getInternalName());
        for (String target : strings(value(mixin, "targets"))) result.add(target.replace('.', '/'));
        return result;
    }

    private static List<MethodNode> select(ClassNode target, String selector) {
        int descriptorStart = selector.indexOf('(');
        String name = descriptorStart < 0 ? selector : selector.substring(0, descriptorStart);
        String descriptor = descriptorStart < 0 ? null : selector.substring(descriptorStart);
        return target.methods.stream().filter(method -> method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor))).toList();
    }

    private void fail(ClassNode mixin, MethodNode handler, String message) {
        errors++;
        System.out.println("ERROR " + mixin.name + "." + handler.name + handler.desc + " :: " + message);
    }

    private static boolean same(Type[] left, Type[] right) { return Arrays.equals(left, right); }

    private static boolean validInjectPrefix(String targetDescriptor, String handlerDescriptor) {
        Type[] target = Type.getArgumentTypes(targetDescriptor), handler = Type.getArgumentTypes(handlerDescriptor);
        int callback = -1;
        for (int i = 0; i < handler.length; i++) if (handler[i].getDescriptor().equals(CI) || handler[i].getDescriptor().equals(CIR)) { callback = i; break; }
        if (callback < 0) return false;
        Type[] prefix = Arrays.copyOf(handler, callback);
        return prefix.length == 0 || same(prefix, target);
    }

    private static boolean injectCandidate(MethodNode target, MethodNode handler) {
        if (!validInjectPrefix(target.desc, handler.desc)) return false;
        Type[] arguments = Type.getArgumentTypes(handler.desc);
        String callback = Type.getReturnType(target.desc).getSort() == Type.VOID ? CI : CIR;
        return Arrays.stream(arguments).anyMatch(type -> type.getDescriptor().equals(callback));
    }

    private void checkInject(ClassNode mixin, MethodNode handler, AnnotationNode inject, ClassNode target, MethodNode method) {
        injects++;
        String instructionTarget = atTarget(inject);
        if (instructionTarget != null && instructionTarget.startsWith("L") && operation(method, instructionTarget) == null) fail(mixin, handler, "@Inject instruction target is absent from " + method.name + method.desc + ": " + instructionTarget);
        Type[] handlerArguments = Type.getArgumentTypes(handler.desc), methodArguments = Type.getArgumentTypes(method.desc);
        if (Type.getReturnType(handler.desc).getSort() != Type.VOID) { fail(mixin, handler, "@Inject handler must return void"); return; }
        int callbackIndex = -1;
        for (int i = 0; i < handlerArguments.length; i++) {
            String descriptor = handlerArguments[i].getDescriptor();
            if (descriptor.equals(CI) || descriptor.equals(CIR)) { callbackIndex = i; break; }
        }
        if (callbackIndex < 0) { fail(mixin, handler, "missing CallbackInfo parameter for " + method.name + method.desc); return; }
        String expectedCallback = Type.getReturnType(method.desc).getSort() == Type.VOID ? CI : CIR;
        if (!handlerArguments[callbackIndex].getDescriptor().equals(expectedCallback)) fail(mixin, handler, "expected " + expectedCallback + " for target return " + Type.getReturnType(method.desc));
        Type[] prefix = Arrays.copyOf(handlerArguments, callbackIndex);
        if (prefix.length != 0 && !same(prefix, methodArguments)) fail(mixin, handler, "arguments before CallbackInfo must be empty or exactly " + Arrays.toString(methodArguments));
        Type[] locals = Arrays.copyOfRange(handlerArguments, callbackIndex + 1, handlerArguments.length);
        Object localCapture = value(inject, "locals");
        boolean captures = localCapture instanceof String[] enumeration && !"NO_CAPTURE".equals(enumeration[1]);
        if (locals.length > 0 && !captures) fail(mixin, handler, "handler declares captured locals without locals=CAPTURE_*");
        if (captures) {
            capturedLocals++;
            AnnotationNode at = firstAnnotation(value(inject, "at"));
            String atValue = (String) value(at, "value");
            if (!"TAIL".equals(atValue)) fail(mixin, handler, "local audit currently requires a TAIL injection point, found " + atValue);
            else checkTailLocals(mixin, handler, target, method, locals);
        }
        if ((method.access & Opcodes.ACC_STATIC) != 0 && (handler.access & Opcodes.ACC_STATIC) == 0) fail(mixin, handler, "instance handler targets a static method");
    }

    private void checkTailLocals(ClassNode mixin, MethodNode handler, ClassNode target, MethodNode method, Type[] expected) {
        try {
            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
            Frame<BasicValue>[] frames = analyzer.analyze(target.name, method);
            int firstLocal = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
            for (Type argument : Type.getArgumentTypes(method.desc)) firstLocal += argument.getSize();
            int lastReturn = -1;
            for (int instruction = 0; instruction < method.instructions.size(); instruction++) {
                int opcode = method.instructions.get(instruction).getOpcode();
                if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) lastReturn = instruction;
            }
            if (lastReturn < 0) { fail(mixin, handler, "TAIL target has no return instruction"); return; }
            for (int instruction = lastReturn; instruction <= lastReturn; instruction++) {
                Frame<BasicValue> frame = frames[instruction];
                List<Type> actual = new ArrayList<>();
                for (int slot = firstLocal; slot < frame.getLocals(); slot++) {
                    BasicValue basic = frame.getLocal(slot);
                    if (basic == null || basic == BasicValue.UNINITIALIZED_VALUE || basic.getType() == null) continue;
                    Type type = localVariableType(method, slot, instruction);
                    if (type == null) type = basic.getType();
                    actual.add(type);
                    if (type.getSize() == 2) slot++;
                }
                if (actual.size() < expected.length || !same(expected, actual.subList(0, expected.length).toArray(Type[]::new))) {
                    fail(mixin, handler, "captured TAIL locals " + Arrays.toString(expected) + " do not match frame " + actual + " in " + method.name + method.desc);
                }
            }
        } catch (AnalyzerException exception) {
            fail(mixin, handler, "could not analyze captured locals: " + exception.getMessage());
        }
    }

    private static Type localVariableType(MethodNode method, int slot, int instruction) {
        if (method.localVariables == null) return null;
        for (LocalVariableNode local : method.localVariables) {
            if (local.index != slot) continue;
            int start = method.instructions.indexOf(local.start), end = method.instructions.indexOf(local.end);
            if (start <= instruction && instruction < end) return Type.getType(local.desc);
        }
        return null;
    }

    private record Operation(Type[] arguments, Type result) {}

    private static String atTarget(AnnotationNode injector) {
        AnnotationNode at = firstAnnotation(value(injector, "at"));
        Object target = value(at, "target");
        return target instanceof String string ? string : null;
    }

    private static Operation operation(MethodNode method, String targetReference) {
        if (targetReference == null || !targetReference.startsWith("L")) return null;
        int ownerEnd = targetReference.indexOf(';');
        String owner = targetReference.substring(1, ownerEnd), member = targetReference.substring(ownerEnd + 1);
        int methodDescriptor = member.indexOf('(');
        if (methodDescriptor >= 0) {
            String name = member.substring(0, methodDescriptor), descriptor = member.substring(methodDescriptor);
            for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) {
                List<Type> arguments = new ArrayList<>();
                if (call.getOpcode() != Opcodes.INVOKESTATIC) arguments.add(Type.getObjectType(owner));
                arguments.addAll(Arrays.asList(Type.getArgumentTypes(descriptor)));
                return new Operation(arguments.toArray(Type[]::new), Type.getReturnType(descriptor));
            }
        } else {
            int colon = member.indexOf(':'); if (colon < 0) return null;
            String name = member.substring(0, colon), descriptor = member.substring(colon + 1);
            for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof FieldInsnNode field && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor)) {
                Type fieldType = Type.getType(descriptor), ownerType = Type.getObjectType(owner);
                return switch (field.getOpcode()) {
                    case Opcodes.GETFIELD -> new Operation(new Type[]{ownerType}, fieldType);
                    case Opcodes.PUTFIELD -> new Operation(new Type[]{ownerType, fieldType}, Type.VOID_TYPE);
                    case Opcodes.GETSTATIC -> new Operation(new Type[0], fieldType);
                    case Opcodes.PUTSTATIC -> new Operation(new Type[]{fieldType}, Type.VOID_TYPE);
                    default -> null;
                };
            }
        }
        return null;
    }

    private void checkRedirect(ClassNode mixin, MethodNode handler, AnnotationNode redirect, MethodNode targetMethod) {
        redirects++;
        Operation operation = operation(targetMethod, atTarget(redirect));
        if (operation == null) { fail(mixin, handler, "cannot resolve redirected operation in " + targetMethod.name + targetMethod.desc); return; }
        Type[] actual = Type.getArgumentTypes(handler.desc), enclosing = Type.getArgumentTypes(targetMethod.desc);
        Type[] extended = Arrays.copyOf(operation.arguments, operation.arguments.length + enclosing.length);
        System.arraycopy(enclosing, 0, extended, operation.arguments.length, enclosing.length);
        if (!same(actual, operation.arguments) && !same(actual, extended)) fail(mixin, handler, "redirect parameters " + Arrays.toString(actual) + " expected " + Arrays.toString(operation.arguments) + " (optionally followed by target arguments)");
        if (!Type.getReturnType(handler.desc).equals(operation.result)) fail(mixin, handler, "redirect return " + Type.getReturnType(handler.desc) + " expected " + operation.result);
        if ((targetMethod.access & Opcodes.ACC_STATIC) != 0 && (handler.access & Opcodes.ACC_STATIC) == 0) fail(mixin, handler, "instance redirect targets a static method");
    }

    private void checkModifyArg(ClassNode mixin, MethodNode handler, AnnotationNode annotation, MethodNode targetMethod) {
        modifiers++;
        Operation operation = operation(targetMethod, atTarget(annotation));
        if (operation == null) { fail(mixin, handler, "cannot resolve @ModifyArg invocation"); return; }
        // Invocation arguments exclude the receiver used by Redirect.
        String reference = atTarget(annotation); int descriptorStart = reference.indexOf('(');
        Type[] invocationArguments = Type.getArgumentTypes(reference.substring(descriptorStart));
        Type[] actual = Type.getArgumentTypes(handler.desc);
        int index = value(annotation, "index") instanceof Integer integer ? integer : -1;
        Type selected = null;
        if (index >= 0 && index < invocationArguments.length) selected = invocationArguments[index];
        else if (actual.length == 1) for (Type candidate : invocationArguments) if (candidate.equals(actual[0])) { if (selected != null) { selected = null; break; } else selected = candidate; }
        if (selected == null) fail(mixin, handler, "cannot uniquely determine modified argument index");
        else if (actual.length != 1 || !actual[0].equals(selected) || !Type.getReturnType(handler.desc).equals(selected)) fail(mixin, handler, "@ModifyArg must be (" + selected + ")" + selected);
    }

    private void checkModifyArgs(ClassNode mixin, MethodNode handler, AnnotationNode annotation, MethodNode targetMethod) {
        modifiers++;
        if (operation(targetMethod, atTarget(annotation)) == null) fail(mixin, handler, "cannot resolve @ModifyArgs invocation");
        if (!handler.desc.equals("(" + ARGS + ")V")) fail(mixin, handler, "@ModifyArgs handler must be (Args)void");
    }

    private void checkValueModifier(ClassNode mixin, MethodNode handler, MethodNode targetMethod, String kind) {
        modifiers++;
        Type result = Type.getReturnType(handler.desc); Type[] arguments = Type.getArgumentTypes(handler.desc), targetArguments = Type.getArgumentTypes(targetMethod.desc);
        if (arguments.length == 0 || !arguments[0].equals(result)) { fail(mixin, handler, "@" + kind + " first parameter must equal its return type"); return; }
        Type[] context = Arrays.copyOfRange(arguments, 1, arguments.length);
        if (context.length > targetArguments.length || !same(context, Arrays.copyOf(targetArguments, context.length))) fail(mixin, handler, "@" + kind + " context must be a prefix of target arguments " + Arrays.toString(targetArguments));
    }

    private void auditHandler(ClassNode mixin, MethodNode handler, AnnotationNode injector, String kind, ClassNode target) {
        List<String> selectors = strings(value(injector, "method"));
        if (selectors.isEmpty()) { fail(mixin, handler, kind + " has no method selector"); return; }
        for (String selector : selectors) {
            List<MethodNode> methods = select(target, selector);
            if (methods.isEmpty()) { fail(mixin, handler, "target method not found: " + target.name + "." + selector); continue; }
            if (selector.indexOf('(') < 0 && methods.size() > 1 && kind.equals("Inject")) {
                List<MethodNode> compatible = methods.stream().filter(method -> injectCandidate(method, handler)).toList();
                if (compatible.size() == 1) methods = compatible;
                else fail(mixin, handler, "ambiguous overloaded selector; use an exact descriptor: " + selector);
            }
            for (MethodNode method : methods) {
                targetMethods++;
                switch (kind) {
                    case "Inject" -> checkInject(mixin, handler, injector, target, method);
                    case "Redirect" -> checkRedirect(mixin, handler, injector, method);
                    case "ModifyArg" -> checkModifyArg(mixin, handler, injector, method);
                    case "ModifyArgs" -> checkModifyArgs(mixin, handler, injector, method);
                    case "ModifyVariable", "ModifyConstant" -> checkValueModifier(mixin, handler, method, kind);
                }
            }
        }
    }

    private void run() {
        for (ClassNode mixin : new TreeMap<>(mixins).values()) {
            AnnotationNode mixinAnnotation = annotation(mixin, MIXIN);
            if (mixinAnnotation == null) continue;
            mixinClasses++;
            for (String targetName : targetNames(mixinAnnotation)) {
                ClassNode target = targets.get(targetName);
                if (target == null) { errors++; System.out.println("ERROR " + mixin.name + " :: target class unavailable: " + targetName); continue; }
                for (MethodNode handler : mixin.methods) {
                    AnnotationNode injector;
                    if ((injector = annotation(handler, INJECT)) != null) { handlers++; auditHandler(mixin, handler, injector, "Inject", target); }
                    if ((injector = annotation(handler, REDIRECT)) != null) { handlers++; auditHandler(mixin, handler, injector, "Redirect", target); }
                    if ((injector = annotation(handler, MODIFY_ARG)) != null) { handlers++; auditHandler(mixin, handler, injector, "ModifyArg", target); }
                    if ((injector = annotation(handler, MODIFY_ARGS)) != null) { handlers++; auditHandler(mixin, handler, injector, "ModifyArgs", target); }
                    if ((injector = annotation(handler, MODIFY_VARIABLE)) != null) { handlers++; auditHandler(mixin, handler, injector, "ModifyVariable", target); }
                    if ((injector = annotation(handler, MODIFY_CONSTANT)) != null) { handlers++; auditHandler(mixin, handler, injector, "ModifyConstant", target); }
                }
            }
        }
        String oldGeass = "(DDDFFI" + CI + ")V";
        if (validInjectPrefix("(DDDFFIZ)V", oldGeass)) { errors++; System.out.println("ERROR regression guard accepted the old six-argument Geass handler"); }
        else System.out.println("OK regression old Geass DDDFFI handler rejected against DDDFFIZ target");
        System.out.printf("mixin_classes=%d handlers=%d target_methods=%d inject=%d redirect=%d modifiers=%d local_capture=%d errors=%d%n", mixinClasses, handlers, targetMethods, injects, redirects, modifiers, capturedLocals, errors);
        if (errors != 0) System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("usage: MixinHandlerAudit <mixin classes dir/jar> <target dir/jar>...");
        MixinHandlerAudit audit = new MixinHandlerAudit();
        read(audit.mixins, Path.of(args[0]));
        for (int i = 1; i < args.length; i++) read(audit.targets, Path.of(args[i]));
        audit.run();
    }
}
