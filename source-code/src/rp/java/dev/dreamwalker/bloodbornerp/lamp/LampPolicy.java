package dev.dreamwalker.bloodbornerp.lamp;

/** Bounds check for persisted lamp coordinates. */
public final class LampPolicy {
 private LampPolicy(){}
 public static boolean finite(double x,double y,double z){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30000000&&Math.abs(z)<=30000000;}
}
