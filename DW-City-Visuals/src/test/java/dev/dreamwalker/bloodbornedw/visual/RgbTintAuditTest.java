package dev.dreamwalker.bloodbornedw.visual;

/** Offline packed-color contracts for the two quad paths. */
public final class RgbTintAuditTest {
    public static void main(String[] args) {
        check(FacadeMaterials.tint(0x80ffffff,0x123456,false),0x80123456);
        check(FacadeMaterials.tint(0x80ffffff,0x123456,true),0x80563412);
        check(FacadeMaterials.tint(0x6f172339,0xffffff,false),0x6f172339);
        check(FacadeMaterials.tint(0x6f172339,0x000000,true),0x6f000000);
        check(FacadeMaterials.tint(0x807f80ff,0xff0000,false),0x807f0000);
        check(FacadeMaterials.tint(0x807f80ff,0xff0000,true),0x800000ff);
        System.out.println("RGB tint audit PASS");
    }

    private static void check(int actual,int expected) {
        if (actual != expected) throw new AssertionError(Integer.toHexString(actual) + " != " + Integer.toHexString(expected));
    }
}
