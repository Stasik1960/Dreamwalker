package dev.dreamwalker.bloodbornedw.visual;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Offline packed-color contracts for the two quad paths. */
public final class RgbTintAuditTest {
    public static void main(String[] args) throws Exception {
        check(FacadeMaterials.tint(0x80ffffff,0x123456,false),0x80123456);
        check(FacadeMaterials.tint(0x80ffffff,0x123456,true),0x80563412);
        check(FacadeMaterials.tint(0x6f172339,0xffffff,false),0x6f172339);
        check(FacadeMaterials.tint(0x6f172339,0x000000,true),0x6f000000);
        check(FacadeMaterials.tint(0x807f80ff,0xff0000,false),0x807f0000);
        check(FacadeMaterials.tint(0x807f80ff,0xff0000,true),0x800000ff);
        checkResourceLimit();
        System.out.println("RGB tint and resource audit PASS");
    }

    private static void checkResourceLimit() throws Exception {
        byte[] wall = new byte[1_753_039];
        wall[wall.length-1] = 42;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expected = HexFormat.of().formatHex(digest.digest(wall));
        byte[] buffer = new byte[8192];
        if (!RgbResourceGuard.matches(new ByteArrayInputStream(wall),expected,digest,buffer,2 << 20))
            throw new AssertionError("Verified large wall blockstate was rejected");
        if (RgbResourceGuard.matches(new ByteArrayInputStream(wall),expected,digest,buffer,1 << 20))
            throw new AssertionError("Oversized asset passed the smaller resource limit");
        wall[wall.length-1] = 0;
        if (RgbResourceGuard.matches(new ByteArrayInputStream(wall),expected,digest,buffer,2 << 20))
            throw new AssertionError("Mutated blockstate passed the SHA-256 check");
    }

    private static void check(int actual,int expected) {
        if (actual != expected) throw new AssertionError(Integer.toHexString(actual) + " != " + Integer.toHexString(expected));
    }
}
