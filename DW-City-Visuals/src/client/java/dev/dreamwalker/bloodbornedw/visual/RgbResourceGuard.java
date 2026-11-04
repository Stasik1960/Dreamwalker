package dev.dreamwalker.bloodbornedw.visual;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Reload-time content checks for RGB profile assets selected by ResourceManager. */
public final class RgbResourceGuard {
    public record Generation(long id, Set<Identifier> verifiedAssets) {
        public Generation {
            verifiedAssets = Set.copyOf(verifiedAssets);
        }

        public boolean verifiesAll(Iterable<Identifier> assets) {
            for (Identifier asset : assets) {
                if (!verifiedAssets.contains(asset)) return false;
            }
            return true;
        }
    }

    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static volatile Generation current = new Generation(0, Set.of());

    private RgbResourceGuard() {}

    /**
     * Compare resolved texture, model, and blockstate assets with hashes from
     * generated internal profile metadata. Missing or changed resources fail closed.
     */
    public static Generation reload(ResourceManager manager, Map<Identifier, String> expectedSha256) {
        Set<Identifier> verified = new HashSet<>();
        for (var entry : expectedSha256.entrySet()) {
            if (matches(manager, entry.getKey(), entry.getValue())) verified.add(entry.getKey());
        }
        Generation next = new Generation(NEXT_GENERATION.incrementAndGet(), verified);
        current = next;
        return next;
    }

    public static Generation current() {
        return current;
    }

    public static boolean verified(Identifier asset) {
        return current.verifiedAssets().contains(asset);
    }

    private static boolean matches(ResourceManager manager, Identifier asset, String expected) {
        if (expected == null) return false;
        try {
            if (expected.isEmpty()) return manager.getResource(asset).isEmpty();
            if (expected.length() != 64) return false;
            Resource resource = manager.getResource(asset).orElse(null);
            if (resource == null) return false;
            byte[] data;
            try (var stream = resource.getInputStream()) {
                data = stream.readNBytes(1_048_577);
                if (data.length > 1_048_576) return false;
            }
            return hex(MessageDigest.getInstance("SHA-256").digest(data)).equalsIgnoreCase(expected);
        } catch (IOException | NoSuchAlgorithmException failure) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 15];
        }
        return new String(result);
    }
}
