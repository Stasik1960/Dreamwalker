package dev.dreamwalker.bloodbornerp.lamp;

/** Pure bounds checks shared by persisted lamp data and request handling. */
public final class LampPolicy {
 private LampPolicy(){}
 public static boolean validName(String name){return name!=null&&!name.isBlank()&&name.length()<=64;}
 public static boolean validRoute(int currentRoutes,int limit,boolean alreadyLinked){return currentRoutes>=0&&currentRoutes<limit&&!alreadyLinked;}
 public static boolean validContext(long expected,long supplied,long expiresAt,long now){return expected==supplied&&expiresAt>=now;}
}
