package daot;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.Optional;

public final class InternalAccess {
   private static final StackWalker WALKER = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);
   private static final List<String> PLUMBING_PREFIXES = List.of("java.lang.reflect.", "java.lang.invoke.", "jdk.internal.reflect.", "sun.reflect.");
   private static final List<String> TRUSTED_PREFIXES = List.of("daot.");
   private static final List<String> SCRIPT_ENGINE_PREFIXES = List.of(
      "org.openjdk.nashorn.",
      "jdk.nashorn.",
      "org.graalvm.",
      "com.oracle.truffle.",
      "org.mozilla.javascript.",
      "groovy.",
      "org.codehaus.groovy.",
      "luaj.",
      "org.luaj."
   );

   private InternalAccess() {
   }

   private static boolean isPlumbing(String className) {
      return PLUMBING_PREFIXES.stream().anyMatch(className::startsWith);
   }

   private static boolean isTrusted(String className) {
      return TRUSTED_PREFIXES.stream().anyMatch(className::startsWith);
   }

   private static Optional<String> foreignImmediateCaller() {
      return WALKER.walk(stream -> {
         List<String> frames = stream.map(StackFrame::getClassName).filter(namex -> !namex.startsWith("daot.InternalAccess")).toList();

         for (int i = 1; i < frames.size(); i++) {
            String name = frames.get(i);
            if (!isPlumbing(name)) {
               return isTrusted(name) ? Optional.empty() : Optional.of(name);
            }
         }

         return Optional.empty();
      });
   }

   public static boolean verify(String operation) {
      Optional<String> foreign = foreignImmediateCaller();
      if (foreign.isEmpty()) {
         return true;
      } else {
         String caller = foreign.get();
         boolean viaScript = SCRIPT_ENGINE_PREFIXES.stream().anyMatch(caller::startsWith);
         if (viaScript) {
            DannysAot.LOGGER
               .warn(
                  "[dannys-aot] Refused scripted write to {}: called from an in-game script engine ('{}'). Granting Danny's AoT powers or bloodlines from a script is not licensed — check your server's script folders (e.g. CustomNPCs scripts live in the world save, not in mods/). See LICENSE.",
                  operation,
                  caller
               );
         } else {
            DannysAot.LOGGER
               .warn(
                  "[dannys-aot] Refused external write to {}: called by '{}'. Danny's AoT is All Rights Reserved; third-party mods may not grant or modify shifter powers or bloodlines. See LICENSE.",
                  operation,
                  caller
               );
         }

         return false;
      }
   }
}
