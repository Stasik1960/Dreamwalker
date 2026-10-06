package dev.dreamwalker.bloodbornerp.weapon;

/** The two stable states of a trick weapon. */
public enum WeaponForm {
  FOLDED(false), EXTENDED(true);

  private final boolean nbtValue;
  WeaponForm(boolean nbtValue) { this.nbtValue=nbtValue; }
  public boolean nbtValue() { return nbtValue; }
  public WeaponForm other() { return this==FOLDED ? EXTENDED : FOLDED; }
  public static WeaponForm fromNbt(boolean value) { return value ? EXTENDED : FOLDED; }
}
