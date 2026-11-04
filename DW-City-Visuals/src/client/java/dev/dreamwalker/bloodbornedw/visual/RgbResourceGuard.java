package dev.dreamwalker.bloodbornedw.visual;

import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
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

    private static final int MAX_ASSET_BYTES = 1 << 20;
    private static final int MAX_BLOCKSTATE_BYTES = 2 << 20;
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static volatile Generation current = new Generation(0, Set.of());

    private RgbResourceGuard() {}

    /**
     * Compare resolved texture, model, and blockstate assets with hashes from
     * generated internal profile metadata. Missing or changed resources fail closed.
     */
    public static Generation reload(ResourceManager manager, Map<Identifier, String> expectedSha256) {
        Set<Identifier> verified = new HashSet<>();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        byte[] buffer = new byte[8192];
        for (var entry : expectedSha256.entrySet()) {
            if (matches(manager, entry.getKey(), entry.getValue(), digest, buffer)) verified.add(entry.getKey());
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

    private static boolean matches(ResourceManager manager, Identifier asset, String expected,
                                   MessageDigest digest, byte[] buffer) {
        if (expected == null) return false;
        try {
            if (expected.isEmpty()) return manager.getResource(asset).isEmpty();
            if (expected.length() != 64) return false;
            Resource resource = manager.getResource(asset).orElse(null);
            if (resource == null) return false;
            // Imported walls can carry thousands of exact blockstate variants.
            int limit = asset.getPath().startsWith("blockstates/") ? MAX_BLOCKSTATE_BYTES : MAX_ASSET_BYTES;
            try (var stream = resource.getInputStream()) {
                return matches(stream,expected,digest,buffer,limit);
            }
        } catch (IOException failure) {
            return false;
        }
    }

    static boolean matches(InputStream stream, String expected, MessageDigest digest, byte[] buffer,
                           int limit) throws IOException {
        digest.reset();
        int remaining = limit + 1;
        while (remaining > 0) {
            int read = stream.read(buffer,0,Math.min(buffer.length,remaining));
            if (read < 0) return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
            digest.update(buffer,0,read);
            remaining -= read;
        }
        return false;
    }
}
