package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class CombatModeState {
   private static boolean enabled = false;
   private static Perspective savedCameraType = null;
   private static float cursorX = 0.0F;
   private static float cursorY = 0.0F;
   private static float zoomDistance = 4.0F;
   private static float prevZoomDistance = 4.0F;
   private static float targetZoomDistance = 4.0F;
   private static final float MIN_ZOOM = 1.0F;
   private static final float MAX_ZOOM = 16.0F;
   private static final float ZOOM_STEP = 0.5F;
   private static final float CURSOR_SENSITIVITY = 0.35F;
   private static boolean shiftLockEnabled = false;
   private static final float SHIFT_LOCK_PAN = 2.0F;
   public static final String KEYBIND_CATEGORY = "category.dannys-aot.combat_mode";

   public static boolean isEnabled() {
      return enabled;
   }

   public static void toggle() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         if (enabled) {
            disable();
         } else if (DannysAot.isODMGear(mc.player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
            if (mc.player.getVehicle() == null) {
               savedCameraType = mc.options.getPerspective();
               mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
               cursorX = 0.0F;
               cursorY = 0.0F;
               zoomDistance = 4.0F;
               prevZoomDistance = 4.0F;
               targetZoomDistance = 4.0F;
               enabled = true;
            }
         }
      }
   }

   public static void disable() {
      if (enabled) {
         enabled = false;
         shiftLockEnabled = false;
         MinecraftClient mc = MinecraftClient.getInstance();
         if (savedCameraType != null) {
            mc.options.setPerspective(savedCameraType);
            savedCameraType = null;
         }

         cursorX = 0.0F;
         cursorY = 0.0F;
         zoomDistance = 4.0F;
         prevZoomDistance = 4.0F;
         targetZoomDistance = 4.0F;
      }
   }

   public static void toggleShiftLock() {
      if (enabled) {
         shiftLockEnabled = !shiftLockEnabled;
         if (shiftLockEnabled) {
            cursorX = 0.0F;
            cursorY = 0.0F;
         }
      }
   }

   public static boolean isShiftLockEnabled() {
      return enabled && shiftLockEnabled;
   }

   public static float getShiftLockPan() {
      return 2.0F;
   }

   public static boolean isBlockKeyDown() {
      return !enabled ? false : isRawKeyDown(DannysAotClient.COMBAT_BLOCK_KEY);
   }

   public static void moveCursor(double dx, double dy) {
      MinecraftClient mc = MinecraftClient.getInstance();
      int guiW = mc.getWindow().getScaledWidth();
      int guiH = mc.getWindow().getScaledHeight();
      float maxX = guiW * 0.45F;
      float maxY = guiH * 0.45F;
      cursorX = (float)Math.max((double)(-maxX), Math.min((double)maxX, cursorX + dx * 0.35F));
      cursorY = (float)Math.max((double)(-maxY), Math.min((double)maxY, cursorY + dy * 0.35F));
   }

   public static void adjustZoom(int direction) {
      targetZoomDistance = Math.max(1.0F, Math.min(16.0F, targetZoomDistance - direction * 0.5F));
   }

   public static void tickZoom() {
      prevZoomDistance = zoomDistance;
      zoomDistance = zoomDistance + (targetZoomDistance - zoomDistance) * 0.45F;
      if (Math.abs(zoomDistance - targetZoomDistance) < 0.01F) {
         zoomDistance = targetZoomDistance;
      }
   }

   public static float getInterpolatedZoom(float partialTick) {
      return prevZoomDistance + (zoomDistance - prevZoomDistance) * partialTick;
   }

   public static float getCursorScreenX() {
      return cursorX;
   }

   public static float getCursorScreenY() {
      return cursorY;
   }

   public static boolean isRightMouseHeld() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.getWindow() == null ? false : GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 1) == 1;
   }

   public static boolean isLeftHookDown() {
      if (DannysAotClient.LEFT_HOOK_KEY.isPressed()) {
         return true;
      } else {
         return !enabled ? false : isRawKeyDown(DannysAotClient.COMBAT_LEFT_HOOK_KEY);
      }
   }

   public static boolean isRightHookDown() {
      if (DannysAotClient.RIGHT_HOOK_KEY.isPressed()) {
         return true;
      } else {
         return !enabled ? false : isRawKeyDown(DannysAotClient.COMBAT_RIGHT_HOOK_KEY);
      }
   }

   private static boolean isRawKeyDown(KeyBinding key) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.getWindow() == null) {
         return false;
      } else if (mc.currentScreen != null) {
         return false;
      } else {
         Key boundKey = KeyBindingHelper.getBoundKeyOf(key);
         int keyCode = boundKey.getCode();
         return keyCode == InputUtil.UNKNOWN_KEY.getCode() ? false : InputUtil.isKeyPressed(mc.getWindow().getHandle(), keyCode);
      }
   }

   public static Vec3d getCursorWorldRay() {
      if (!enabled) {
         return null;
      } else if (shiftLockEnabled) {
         return null;
      } else if (isRightMouseHeld()) {
         return null;
      } else if (Math.abs(cursorX) < 0.5F && Math.abs(cursorY) < 0.5F) {
         return null;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         Camera camera = mc.gameRenderer.getCamera();
         double fovDeg = mc.options.getFov().getValue().intValue();
         double fovRad = Math.toRadians(fovDeg);
         int screenH = mc.getWindow().getHeight();
         double guiScale = (double)screenH / mc.getWindow().getScaledHeight();
         double pixelOffsetX = cursorX * guiScale;
         double pixelOffsetY = cursorY * guiScale;
         double focalLength = screenH / 2.0 / Math.tan(fovRad / 2.0);
         double angularX = Math.atan2(pixelOffsetX, focalLength);
         double angularY = Math.atan2(pixelOffsetY, focalLength);
         Quaternionf cameraRot = new Quaternionf(camera.getRotation());
         Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
         Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
         Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
         cameraRot.transform(forward);
         cameraRot.transform(right);
         cameraRot.transform(up);
         Vec3d dir = new Vec3d(forward).add(new Vec3d(right).multiply(Math.tan(angularX))).add(new Vec3d(up).multiply(-Math.tan(angularY)));
         return dir.normalize();
      }
   }
}
