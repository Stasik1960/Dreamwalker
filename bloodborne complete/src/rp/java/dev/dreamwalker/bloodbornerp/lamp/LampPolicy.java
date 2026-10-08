package dev.dreamwalker.bloodbornerp.lamp;

/** Pure bounds checks shared by persisted lamp data and request handling. */
public final class LampPolicy {
 private LampPolicy(){}
 public static boolean validName(String name){return name!=null&&!name.isBlank()&&name.length()<=64&&name.codePoints().noneMatch(Character::isISOControl);}
 public static boolean finite(double x,double y,double z){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30000000&&Math.abs(z)<=30000000;}
 public static boolean validRoute(int currentRoutes,int limit,boolean alreadyLinked){return currentRoutes>=0&&currentRoutes<limit&&!alreadyLinked;}
 public static boolean validContext(long expected,long supplied,long expiresAt,long now){return expected==supplied&&expiresAt>=now;}
}
