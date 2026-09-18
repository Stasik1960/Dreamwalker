package daot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class ShiftParticleHelper {
   private static boolean aaaParticlesAvailable = false;
   private static boolean checkedAvailability = false;
   private static Object shiftParticleEmitter = null;
   private static Object preshiftParticleEmitter = null;
   private static Object smokeParticleEmitter = null;
   private static Object dustAmbientParticleEmitter = null;
   private static Object pathsParticleEmitter = null;
   private static Object bloodParticleEmitter = null;
   private static Object roarParticleEmitter = null;
   private static Object windParticleEmitter = null;
   private static Object teleportParticleEmitter = null;
   private static Object spikeEmergeParticleEmitter = null;
   private static Object hammerSummonParticleEmitter = null;
   private static Object hammerSummon2ParticleEmitter = null;
   private static Object auraParticleEmitter = null;
   private static Object colossalNukeParticleEmitter = null;
   private static Object titanSurgeParticleEmitter = null;
   private static Object activeHammerSummonHandle = null;
   private static Object activeHammerSummon2Handle = null;
   private static Object activePreshiftHandle = null;
   private static Object activeShiftHandle = null;
   private static Object activePathsHandle = null;
   private static Object activeDannyModeRoarHandle = null;
   private static Object activeAuraHandle = null;
   private static final Map<Integer, Object> activeWindHandles = new HashMap<>();
   private static final Map<Integer, Object> activeTitanSurgeHandles = new HashMap<>();
   private static boolean loggedHandleType = false;
   private static final Identifier PATHS_EMITTER_NAME = new Identifier("dannys-aot", "paths_effect_emitter");
   private static final Identifier PATHS_EFFEK_ID = new Identifier("dannys-aot", "paths");

   public static boolean isAAAParticlesAvailable() {
      if (!checkedAvailability) {
         checkedAvailability = true;
         aaaParticlesAvailable = FabricLoader.getInstance().isModLoaded("aaa_particles");
         if (aaaParticlesAvailable) {
            DannysAot.LOGGER.info("AAA Particles detected - shift particle effects enabled");
            initializeParticleEmitter();
         } else {
            DannysAot.LOGGER.info("AAA Particles not found - shift particle effects disabled");
         }
      }

      return aaaParticlesAvailable;
   }

   private static void initializeParticleEmitter() {
      try {
         Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
         Constructor<?> constructor = emitterInfoClass.getConstructor(Identifier.class);
         shiftParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "shift"));
         DannysAot.LOGGER.info("Initialized shift particle emitter");
         preshiftParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "preshift"));
         DannysAot.LOGGER.info("Initialized preshift particle emitter");
         smokeParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "smoke"));
         DannysAot.LOGGER.info("Initialized smoke particle emitter");
         dustAmbientParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "dustambient"));
         DannysAot.LOGGER.info("Initialized dustambient particle emitter");
         pathsParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "paths"));
         DannysAot.LOGGER.info("Initialized paths particle emitter");
         bloodParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "blood"));
         DannysAot.LOGGER.info("Initialized blood particle emitter");
         roarParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "roar"));
         DannysAot.LOGGER.info("Initialized roar particle emitter");
         windParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "wind"));
         DannysAot.LOGGER.info("Initialized wind particle emitter");
         teleportParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "teleport"));
         DannysAot.LOGGER.info("Initialized teleport particle emitter");
         spikeEmergeParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "spike_emerge"));
         DannysAot.LOGGER.info("Initialized spike_emerge particle emitter");
         hammerSummonParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "hammer_summon"));
         DannysAot.LOGGER.info("Initialized hammer_summon particle emitter");
         hammerSummon2ParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "hammer_summon2"));
         DannysAot.LOGGER.info("Initialized hammer_summon2 particle emitter");
         auraParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "aura"));
         DannysAot.LOGGER.info("Initialized aura particle emitter");
         colossalNukeParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "colossal_nuke"));
         DannysAot.LOGGER.info("Initialized colossal_nuke particle emitter");
         titanSurgeParticleEmitter = constructor.newInstance(new Identifier("dannys-aot", "titan_surge"));
         DannysAot.LOGGER.info("Initialized titan_surge particle emitter");
         DannysAot.LOGGER.info("ParticleEmitterInfo methods:");

         for (Method method : emitterInfoClass.getMethods()) {
            if (method.getDeclaringClass() == emitterInfoClass) {
               StringBuilder sb = new StringBuilder();
               sb.append("  - ").append(method.getName()).append("(");
               Class<?>[] params = method.getParameterTypes();

               for (int i = 0; i < params.length; i++) {
                  if (i > 0) {
                     sb.append(", ");
                  }

                  sb.append(params[i].getSimpleName());
               }

               sb.append(") -> ").append(method.getReturnType().getSimpleName());
               DannysAot.LOGGER.info(sb.toString());
            }
         }
      } catch (Exception var9) {
         DannysAot.LOGGER.error("Failed to initialize particle emitters", var9);
         aaaParticlesAvailable = false;
      }
   }

   public static void spawnShiftParticle(World level, double x, double y, double z) {
      spawnShiftParticle(level, x, y, z, 0.5F);
   }

   public static void spawnPreshiftParticleBoundToEntity(World level, Entity entity) {
      if (isAAAParticlesAvailable() && preshiftParticleEmitter != null) {
         try {
            Method cloneMethod = preshiftParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(preshiftParticleEmitter);
            Method scaleMethod = cloned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(cloned, 0.4F);
            Method bindMethod = scaled.getClass().getMethod("bindOnEntity", Entity.class);
            Object bound = bindMethod.invoke(scaled, entity);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               Object handle;
               if (levelFirst) {
                  handle = addParticleMethod.invoke(null, level, bound);
               } else {
                  handle = addParticleMethod.invoke(null, bound, level);
               }

               activePreshiftHandle = handle;
               DannysAot.LOGGER.debug("Spawned preshift particle bound to entity {}, handle: {}", entity.getId(), handle);
            }
         } catch (Exception var17) {
            DannysAot.LOGGER.error("Failed to spawn preshift particle bound to entity", var17);
         }
      }
   }

   public static void spawnTitanSurgeBoundToEntity(World level, Entity entity) {
      if (isAAAParticlesAvailable() && titanSurgeParticleEmitter != null) {
         stopTitanSurgeEffect(entity.getId());
         Object handle = spawnBoundEffect(level, entity, titanSurgeParticleEmitter, 0.4F);
         if (handle != null) {
            activeTitanSurgeHandles.put(entity.getId(), handle);
            DannysAot.LOGGER.debug("Spawned titan_surge particle bound to entity {}", entity.getId());
         }
      }
   }

   public static void stopTitanSurgeEffect(int entityId) {
      Object handle = activeTitanSurgeHandles.remove(entityId);
      if (handle != null) {
         stopEffectHandle(handle);
      }
   }

   public static void stopAllTitanSurgeEffects() {
      for (Object handle : activeTitanSurgeHandles.values()) {
         stopEffectHandle(handle);
      }

      activeTitanSurgeHandles.clear();
   }

   public static void stopPreshiftEffect() {
      if (activePreshiftHandle != null) {
         try {
            Method stopMethod = activePreshiftHandle.getClass().getMethod("stop");
            stopMethod.invoke(activePreshiftHandle);
            DannysAot.LOGGER.debug("Stopped preshift effect");
         } catch (NoSuchMethodException var8) {
            try {
               Method stopRootMethod = activePreshiftHandle.getClass().getMethod("stopRoot");
               stopRootMethod.invoke(activePreshiftHandle);
               DannysAot.LOGGER.debug("Stopped preshift effect via stopRoot");
            } catch (Exception var7) {
               DannysAot.LOGGER.debug("Could not stop preshift effect: no stop method found");
            }
         } catch (Exception var9) {
            DannysAot.LOGGER.debug("Failed to stop preshift effect: {}", var9.getMessage());
         } finally {
            activePreshiftHandle = null;
         }
      }
   }

   public static void spawnShiftParticleBoundToEntity(World level, Entity entity) {
      if (isAAAParticlesAvailable() && shiftParticleEmitter != null) {
         try {
            Method cloneMethod = shiftParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(shiftParticleEmitter);
            Method scaleMethod = cloned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(cloned, 0.5F);
            Method bindMethod = scaled.getClass().getMethod("bindOnEntity", Entity.class);
            Object bound = bindMethod.invoke(scaled, entity);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               Object handle;
               if (levelFirst) {
                  handle = addParticleMethod.invoke(null, level, bound);
               } else {
                  handle = addParticleMethod.invoke(null, bound, level);
               }

               activeShiftHandle = handle;
               DannysAot.LOGGER.debug("Spawned shift particle bound to entity {}, handle: {}", entity.getId(), handle);
            }
         } catch (Exception var17) {
            DannysAot.LOGGER.error("Failed to spawn shift particle bound to entity", var17);
         }
      }
   }

   public static void stopShiftEffect() {
      if (activeShiftHandle != null) {
         try {
            Method stopMethod = activeShiftHandle.getClass().getMethod("stop");
            stopMethod.invoke(activeShiftHandle);
            DannysAot.LOGGER.debug("Stopped shift effect");
         } catch (NoSuchMethodException var8) {
            try {
               Method stopRootMethod = activeShiftHandle.getClass().getMethod("stopRoot");
               stopRootMethod.invoke(activeShiftHandle);
               DannysAot.LOGGER.debug("Stopped shift effect via stopRoot");
            } catch (Exception var7) {
               DannysAot.LOGGER.debug("Could not stop shift effect: no stop method found");
            }
         } catch (Exception var9) {
            DannysAot.LOGGER.debug("Failed to stop shift effect: {}", var9.getMessage());
         } finally {
            activeShiftHandle = null;
         }
      }
   }

   @Deprecated
   public static void spawnPreshiftParticle(World level, double x, double y, double z) {
      if (isAAAParticlesAvailable() && preshiftParticleEmitter != null) {
         try {
            Method cloneMethod = preshiftParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(preshiftParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, 0.4F);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, scaled);
               } else {
                  addParticleMethod.invoke(null, scaled, level);
               }

               DannysAot.LOGGER.debug("Spawned preshift particle at ({}, {}, {})", new Object[]{x, y, z});
            }
         } catch (Exception var22) {
            DannysAot.LOGGER.error("Failed to spawn preshift particle", var22);
         }
      }
   }

   public static void spawnShiftParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable() && shiftParticleEmitter != null) {
         try {
            Method cloneMethod = shiftParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(shiftParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, scale);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, scaled);
               } else {
                  addParticleMethod.invoke(null, scaled, level);
               }

               DannysAot.LOGGER.debug("Spawned shift particle at ({}, {}, {}) with scale {}", new Object[]{x, y, z, scale});
            } else {
               DannysAot.LOGGER.error("Could not find AAALevel.addParticle method");
            }
         } catch (Exception var23) {
            DannysAot.LOGGER.error("Failed to spawn shift particle", var23);
         }
      }
   }

   public static void spawnColossalNukeParticle(World level, double x, double y, double z) {
      spawnColossalNukeParticle(level, x, y, z, 1.0F);
   }

   public static void spawnColossalNukeParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable() && colossalNukeParticleEmitter != null) {
         spawnEffectAtPosition(level, colossalNukeParticleEmitter, scale, x, y, z);
      }
   }

   public static void spawnHammerSummonEffects(World level, double loc1X, double loc1Y, double loc1Z, double loc2X, double loc2Y, double loc2Z) {
      if (isAAAParticlesAvailable()) {
         try {
            if (hammerSummonParticleEmitter != null) {
               spawnEffectAtPosition(level, hammerSummonParticleEmitter, 1.0F, loc1X, loc1Y, loc1Z);
            }

            if (hammerSummon2ParticleEmitter != null) {
               spawnEffectAtPosition(level, hammerSummon2ParticleEmitter, 1.0F, loc2X, loc2Y, loc2Z);
            }
         } catch (Exception var14) {
            DannysAot.LOGGER.error("Failed to spawn hammer summon effects", var14);
         }
      }
   }

   private static Object spawnEffectAtPosition(World level, Object emitter, float scale, double x, double y, double z) {
      try {
         Method cloneMethod = emitter.getClass().getMethod("clone");
         Object cloned = cloneMethod.invoke(emitter);
         Method scaleMethod = cloned.getClass().getMethod("scale", float.class);
         Object scaled = scaleMethod.invoke(cloned, scale);
         Method posMethod = scaled.getClass().getMethod("position", double.class, double.class, double.class);
         Object positioned = posMethod.invoke(scaled, x, y, z);
         Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
         Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
         Method addParticleMethod = null;
         boolean levelFirst = true;

         for (Method method : aaaLevelClass.getMethods()) {
            if (method.getName().equals("addParticle")) {
               Class<?>[] params = method.getParameterTypes();
               if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                  addParticleMethod = method;
                  levelFirst = true;
               } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                  addParticleMethod = method;
                  levelFirst = false;
               }
            }
         }

         if (addParticleMethod != null) {
            Object handle;
            if (levelFirst) {
               handle = addParticleMethod.invoke(null, level, positioned);
            } else {
               handle = addParticleMethod.invoke(null, positioned, level);
            }

            return handle;
         }
      } catch (Exception var24) {
         DannysAot.LOGGER.error("Failed to spawn effect at position", var24);
      }

      return null;
   }

   public static void updateHammerSummonPositions(double loc1X, double loc1Y, double loc1Z, double loc2X, double loc2Y, double loc2Z) {
      try {
         if (activeHammerSummonHandle != null) {
            if (!loggedHandleType) {
               loggedHandleType = true;
               DannysAot.LOGGER.info("Hammer summon handle type: " + activeHammerSummonHandle.getClass().getName());

               for (Method m : activeHammerSummonHandle.getClass().getMethods()) {
                  if (m.getName().contains("osition") || m.getName().contains("move") || m.getName().contains("set")) {
                     DannysAot.LOGGER
                        .info(
                           "  Handle method: "
                              + m.getName()
                              + "("
                              + Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "))
                              + ")"
                        );
                  }
               }
            }

            try {
               Method setPos = activeHammerSummonHandle.getClass().getMethod("setPosition", float.class, float.class, float.class);
               setPos.invoke(activeHammerSummonHandle, (float)loc1X, (float)loc1Y, (float)loc1Z);
            } catch (NoSuchMethodException var17) {
               DannysAot.LOGGER.warn("No setPosition(float,float,float) on handle of type: " + activeHammerSummonHandle.getClass().getName());
            }
         }

         if (activeHammerSummon2Handle != null) {
            try {
               Method setPos = activeHammerSummon2Handle.getClass().getMethod("setPosition", float.class, float.class, float.class);
               setPos.invoke(activeHammerSummon2Handle, (float)loc2X, (float)loc2Y, (float)loc2Z);
            } catch (NoSuchMethodException var16) {
            }
         }
      } catch (Exception var18) {
         DannysAot.LOGGER.error("Failed to update hammer summon positions", var18);
      }
   }

   public static void stopHammerSummonEffects() {
      if (activeHammerSummonHandle != null) {
         stopEffectHandle(activeHammerSummonHandle);
         activeHammerSummonHandle = null;
      }

      if (activeHammerSummon2Handle != null) {
         stopEffectHandle(activeHammerSummon2Handle);
         activeHammerSummon2Handle = null;
      }
   }

   private static Object spawnBoundEffectWithOffset(World level, Entity entity, Object emitter, float scale, double offX, double offY, double offZ) {
      try {
         Method cloneMethod = emitter.getClass().getMethod("clone");
         Object cloned = cloneMethod.invoke(emitter);
         Method scaleMethod = cloned.getClass().getMethod("scale", float.class);
         Object scaled = scaleMethod.invoke(cloned, scale);
         Method bindMethod = scaled.getClass().getMethod("bindOnEntity", Entity.class);
         Object bound = bindMethod.invoke(scaled, entity);
         Method relPosMethod = bound.getClass().getMethod("entitySpaceRelativePosition", double.class, double.class, double.class);
         Object positioned = relPosMethod.invoke(bound, offX, offY, offZ);
         Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
         Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
         Method addParticleMethod = null;
         boolean levelFirst = true;

         for (Method method : aaaLevelClass.getMethods()) {
            if (method.getName().equals("addParticle")) {
               Class<?>[] params = method.getParameterTypes();
               if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                  addParticleMethod = method;
                  levelFirst = true;
               } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                  addParticleMethod = method;
                  levelFirst = false;
               }
            }
         }

         if (addParticleMethod != null) {
            Object handle;
            if (levelFirst) {
               handle = addParticleMethod.invoke(null, level, positioned);
            } else {
               handle = addParticleMethod.invoke(null, positioned, level);
            }

            return handle;
         }
      } catch (Exception var27) {
         DannysAot.LOGGER.error("Failed to spawn bound effect with offset", var27);
      }

      return null;
   }

   public static void spawnSpikeEmergeParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable() && spikeEmergeParticleEmitter != null) {
         try {
            Method cloneMethod = spikeEmergeParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(spikeEmergeParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, scale);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, scaled);
               } else {
                  addParticleMethod.invoke(null, scaled, level);
               }
            }
         } catch (Exception var23) {
            DannysAot.LOGGER.error("Failed to spawn spike_emerge particle", var23);
         }
      }
   }

   public static void spawnSmokeParticle(World level, double x, double y, double z) {
      spawnSmokeParticle(level, x, y, z, 1.0F);
   }

   public static void spawnSmokeParticleBoundToEntity(World level, Entity entity) {
      spawnSmokeParticleBoundToEntity(level, entity, 1.0F);
   }

   public static void spawnSmokeParticleBoundToEntity(World level, Entity entity, float scale) {
      spawnSmokeParticleBoundToEntity(level, entity, scale, 60.0);
   }

   public static void spawnSmokeParticleBoundToEntity(World level, Entity entity, float scale, double yOffset) {
      if (isAAAParticlesAvailable() && smokeParticleEmitter != null) {
         try {
            Method cloneMethod = smokeParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(smokeParticleEmitter);
            Method scaleMethod = cloned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(cloned, scale);
            Method bindMethod = scaled.getClass().getMethod("bindOnEntity", Entity.class);
            Object bound = bindMethod.invoke(scaled, entity);
            Method relPosMethod = bound.getClass().getMethod("entitySpaceRelativePosition", double.class, double.class, double.class);
            Object positioned = relPosMethod.invoke(bound, 0.0, yOffset, 0.0);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, positioned);
               } else {
                  addParticleMethod.invoke(null, positioned, level);
               }

               DannysAot.LOGGER.debug("Spawned smoke particle bound to entity {} at Y offset {}", entity.getId(), yOffset);
            }
         } catch (Exception var22) {
            DannysAot.LOGGER.error("Failed to spawn smoke particle bound to entity", var22);
         }
      }
   }

   public static void spawnSmokeParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable() && smokeParticleEmitter != null) {
         try {
            Method cloneMethod = smokeParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(smokeParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, scale);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, scaled);
               } else {
                  addParticleMethod.invoke(null, scaled, level);
               }

               DannysAot.LOGGER.debug("Spawned smoke particle at ({}, {}, {}) with scale {}", new Object[]{x, y, z, scale});
            } else {
               DannysAot.LOGGER.error("Could not find AAALevel.addParticle method");
            }
         } catch (Exception var23) {
            DannysAot.LOGGER.error("Failed to spawn smoke particle", var23);
         }
      }
   }

   public static void spawnDustAmbientParticle(World level, double x, double y, double z) {
      spawnDustAmbientParticle(level, x, y, z, 1.0F);
   }

   public static void spawnDustAmbientParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable() && dustAmbientParticleEmitter != null) {
         try {
            Method cloneMethod = dustAmbientParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(dustAmbientParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, scale);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, scaled);
               } else {
                  addParticleMethod.invoke(null, scaled, level);
               }
            }
         } catch (Exception var23) {
            DannysAot.LOGGER.error("Failed to spawn dust ambient particle", var23);
         }
      }
   }

   public static void spawnPathsParticle(World level, double x, double y, double z) {
      spawnPathsParticle(level, x, y, z, 1.0F);
   }

   public static void spawnPathsParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable()) {
         try {
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method createMethod = emitterInfoClass.getMethod("create", World.class, Identifier.class, Identifier.class);
            Object emitter = createMethod.invoke(null, level, PATHS_EFFEK_ID, PATHS_EMITTER_NAME);
            Method positionMethod = emitter.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(emitter, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, scale);
            Method spawnInWorldMethod = scaled.getClass().getMethod("spawnInWorld", World.class, PlayerEntity.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
               spawnInWorldMethod.invoke(scaled, level, mc.player);
               DannysAot.LOGGER.info("Spawned named paths particle at ({}, {}, {}) with scale {}", new Object[]{x, y, z, scale});
            }
         } catch (Exception var17) {
            DannysAot.LOGGER.error("Failed to spawn paths particle", var17);
         }
      }
   }

   public static void stopPathsEffect() {
      if (isAAAParticlesAvailable()) {
         try {
            Class<?> effectRegistryClass = Class.forName("mod.chloeprime.aaaparticles.client.registry.EffectRegistry");
            Method getMethod = effectRegistryClass.getMethod("get", Identifier.class);
            Object effect = getMethod.invoke(null, PATHS_EFFEK_ID);
            if (effect == null) {
               DannysAot.LOGGER.warn("stopPathsEffect: effect not found in registry for {}", PATHS_EFFEK_ID);
               return;
            }

            Class<?> emitterTypeClass = Class.forName("mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter$Type");
            Method getNamedEmitterMethod = effect.getClass().getMethod("getNamedEmitter", emitterTypeClass, Identifier.class);
            Object[] enumValues = emitterTypeClass.getEnumConstants();
            DannysAot.LOGGER.info("ParticleEmitter.Type enum values:");

            for (Object enumValue : enumValues) {
               DannysAot.LOGGER.info("  - {}", enumValue);
            }

            for (Object emitterType : enumValues) {
               Object optionalEmitter = getNamedEmitterMethod.invoke(effect, emitterType, PATHS_EMITTER_NAME);
               Method isPresentMethod = optionalEmitter.getClass().getMethod("isPresent");
               boolean isPresent = (Boolean)isPresentMethod.invoke(optionalEmitter);
               if (isPresent) {
                  Method getEmitterMethod = optionalEmitter.getClass().getMethod("get");
                  Object emitter = getEmitterMethod.invoke(optionalEmitter);
                  DannysAot.LOGGER.info("Found emitter with type {}: {}", emitterType, emitter.getClass().getName());

                  try {
                     Method stopMethod = emitter.getClass().getMethod("stop");
                     stopMethod.invoke(emitter);
                     DannysAot.LOGGER.info("Called stop() on emitter!");
                  } catch (NoSuchMethodException var20) {
                     DannysAot.LOGGER.warn("No stop() method on emitter, trying other methods...");

                     for (Method m : emitter.getClass().getMethods()) {
                        if (m.getDeclaringClass() != Object.class && m.getParameterCount() == 0) {
                           DannysAot.LOGGER.info("  - {}()", m.getName());
                        }
                     }
                  }
               }
            }
         } catch (Exception var21) {
            DannysAot.LOGGER.error("Failed to stop paths effect", var21);
            var21.printStackTrace();
         }

         activePathsHandle = null;
      }
   }

   public static void spawnBloodParticle(World level, double x, double y, double z) {
      spawnBloodParticle(level, x, y, z, 1.0F);
   }

   public static void spawnBloodParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable() && bloodParticleEmitter != null) {
         try {
            Method cloneMethod = bloodParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(bloodParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, scale);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, scaled);
               } else {
                  addParticleMethod.invoke(null, scaled, level);
               }

               DannysAot.LOGGER.debug("Spawned blood particle at ({}, {}, {}) with scale {}", new Object[]{x, y, z, scale});
            }
         } catch (Exception var23) {
            DannysAot.LOGGER.error("Failed to spawn blood particle", var23);
         }
      }
   }

   public static void spawnRoarParticle(World level, double x, double y, double z) {
      spawnRoarParticle(level, x, y, z, 1.0F);
   }

   public static void spawnRoarParticle(World level, double x, double y, double z, float scale) {
      if (isAAAParticlesAvailable() && roarParticleEmitter != null) {
         try {
            Method cloneMethod = roarParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(roarParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, scale);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               if (levelFirst) {
                  addParticleMethod.invoke(null, level, scaled);
               } else {
                  addParticleMethod.invoke(null, scaled, level);
               }

               DannysAot.LOGGER.debug("Spawned roar particle at ({}, {}, {}) with scale {}", new Object[]{x, y, z, scale});
            }
         } catch (Exception var23) {
            DannysAot.LOGGER.error("Failed to spawn roar particle", var23);
         }
      }
   }

   public static void spawnDannyModeRoarBoundToEntity(World level, Entity entity) {
      if (isAAAParticlesAvailable() && roarParticleEmitter != null) {
         stopDannyModeRoarEffect();

         try {
            Method cloneMethod = roarParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(roarParticleEmitter);
            Method scaleMethod = cloned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(cloned, 0.3F);
            Method bindMethod = scaled.getClass().getMethod("bindOnEntity", Entity.class);
            Object bound = bindMethod.invoke(scaled, entity);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               Object handle;
               if (levelFirst) {
                  handle = addParticleMethod.invoke(null, level, bound);
               } else {
                  handle = addParticleMethod.invoke(null, bound, level);
               }

               activeDannyModeRoarHandle = handle;
            }
         } catch (Exception var17) {
            DannysAot.LOGGER.error("Failed to spawn Danny Mode roar effect", var17);
         }
      }
   }

   public static void stopDannyModeRoarEffect() {
      if (activeDannyModeRoarHandle != null) {
         try {
            Method stopMethod = activeDannyModeRoarHandle.getClass().getMethod("stop");
            stopMethod.invoke(activeDannyModeRoarHandle);
         } catch (NoSuchMethodException var8) {
            try {
               Method stopRootMethod = activeDannyModeRoarHandle.getClass().getMethod("stopRoot");
               stopRootMethod.invoke(activeDannyModeRoarHandle);
            } catch (Exception var7) {
            }
         } catch (Exception var9) {
         } finally {
            activeDannyModeRoarHandle = null;
         }
      }
   }

   public static boolean isDannyModeRoarActive() {
      return activeDannyModeRoarHandle != null;
   }

   public static void spawnAuraAtPosition(World level, double x, double y, double z) {
      if (isAAAParticlesAvailable() && auraParticleEmitter != null) {
         try {
            Method cloneMethod = auraParticleEmitter.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(auraParticleEmitter);
            Method positionMethod = cloned.getClass().getMethod("position", double.class, double.class, double.class);
            Object positioned = positionMethod.invoke(cloned, x, y, z);
            Method scaleMethod = positioned.getClass().getMethod("scale", float.class);
            Object scaled = scaleMethod.invoke(positioned, 0.5F);
            Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            Method addParticleMethod = null;
            boolean levelFirst = true;

            for (Method method : aaaLevelClass.getMethods()) {
               if (method.getName().equals("addParticle")) {
                  Class<?>[] params = method.getParameterTypes();
                  if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = true;
                  } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                     addParticleMethod = method;
                     levelFirst = false;
                  }
               }
            }

            if (addParticleMethod != null) {
               Object handle;
               if (levelFirst) {
                  handle = addParticleMethod.invoke(null, level, scaled);
               } else {
                  handle = addParticleMethod.invoke(null, scaled, level);
               }

               activeAuraHandle = handle;
            }
         } catch (Exception var22) {
            DannysAot.LOGGER.error("Failed to spawn aura effect", var22);
         }
      }
   }

   public static void stopAuraEffect() {
      if (activeAuraHandle != null) {
         try {
            Method stopMethod = activeAuraHandle.getClass().getMethod("stop");
            stopMethod.invoke(activeAuraHandle);
         } catch (NoSuchMethodException var8) {
            try {
               Method stopRootMethod = activeAuraHandle.getClass().getMethod("stopRoot");
               stopRootMethod.invoke(activeAuraHandle);
            } catch (Exception var7) {
            }
         } catch (Exception var9) {
         } finally {
            activeAuraHandle = null;
         }
      }
   }

   public static boolean isAuraActive() {
      return activeAuraHandle != null;
   }

   public static void spawnTeleportEffect(World level, Entity entity) {
      if (isAAAParticlesAvailable() && teleportParticleEmitter != null) {
         try {
            spawnBoundEffect(level, entity, teleportParticleEmitter, 1.0F);
         } catch (Exception var3) {
            DannysAot.LOGGER.error("Failed to spawn teleport effect", var3);
         }
      }
   }

   private static Object spawnBoundEffect(World level, Entity entity, Object emitter, float scale) {
      try {
         Method cloneMethod = emitter.getClass().getMethod("clone");
         Object cloned = cloneMethod.invoke(emitter);
         Method scaleMethod = cloned.getClass().getMethod("scale", float.class);
         Object scaled = scaleMethod.invoke(cloned, scale);
         Method bindMethod = scaled.getClass().getMethod("bindOnEntity", Entity.class);
         Object bound = bindMethod.invoke(scaled, entity);
         Class<?> aaaLevelClass = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
         Class<?> emitterInfoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
         Method addParticleMethod = null;
         boolean levelFirst = true;

         for (Method method : aaaLevelClass.getMethods()) {
            if (method.getName().equals("addParticle")) {
               Class<?>[] params = method.getParameterTypes();
               if (params.length >= 2 && World.class.isAssignableFrom(params[0]) && emitterInfoClass.isAssignableFrom(params[1])) {
                  addParticleMethod = method;
                  levelFirst = true;
               } else if (params.length >= 2 && emitterInfoClass.isAssignableFrom(params[0]) && World.class.isAssignableFrom(params[1])) {
                  addParticleMethod = method;
                  levelFirst = false;
               }
            }
         }

         if (addParticleMethod != null) {
            Object handle;
            if (levelFirst) {
               handle = addParticleMethod.invoke(null, level, bound);
            } else {
               handle = addParticleMethod.invoke(null, bound, level);
            }

            return handle;
         }
      } catch (Exception var19) {
         DannysAot.LOGGER.error("Failed to spawn bound effect", var19);
      }

      return null;
   }

   private static void stopEffectHandle(Object handle) {
      try {
         Method stopMethod = handle.getClass().getMethod("stop");
         stopMethod.invoke(handle);
      } catch (NoSuchMethodException var4) {
         try {
            Method stopRootMethod = handle.getClass().getMethod("stopRoot");
            stopRootMethod.invoke(handle);
         } catch (Exception var3) {
         }
      } catch (Exception var5) {
      }
   }
}
