package daot;

public interface GrabbingTitan {
   boolean isEating();

   int getEatingTargetId();

   void cancelEating();

   default void triggerEyeHurt() {
   }
}
