package dev.dreamwalker.bloodbornedw.composite;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Offline checks against the installed Sodium shader and representative structural changes. */
public final class SodiumShaderPatchTest {
    private static final String VERSIONS = "Sodium 0.5.13+mc1.20.1, Indium 1.0.36+mc1.20.1, Iris 1.7.6+mc1.20.1";
    private static final String SAMPLE = "vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);";
    private static final String GUARD = "#ifdef DW_TRANSLUCENT_PASS\n    if (diffuseColor.a <= 0.0) discard;\n#endif";

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(String source, String expected) {
        try {
            SodiumShaderPatch.apply(source, VERSIONS);
            throw new AssertionError("Accepted " + expected);
        } catch (IllegalStateException failure) {
            check(failure.getMessage().contains(expected), failure.getMessage());
            check(failure.getMessage().contains(VERSIONS), "Diagnostic omitted installed versions");
        }
    }

    public static void main(String[] args) throws Exception {
        String shader;
        try (ZipFile archive = new ZipFile(Path.of(args[0]).toFile());
             InputStream stream = archive.getInputStream(archive.getEntry("assets/sodium/shaders/blocks/block_layer_opaque.fsh"))) {
            shader = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String expected = shader.replace("    " + SAMPLE, "    " + SAMPLE + "\n" + GUARD);
        check(SodiumShaderPatch.apply(shader, VERSIONS).equals(expected), "Current Sodium output changed");
        String flexible = "void main() {\n\tvec4\tdiffuseColor=texture ( u_BlockTex ,\n v_TexCoord, v_MaterialMipBias );\n}";
        check(SodiumShaderPatch.apply(flexible, VERSIONS).contains(");\n" + GUARD), "Whitespace variant rejected");
        String comments = "/*\n" + SAMPLE + "\n*/\n// " + SAMPLE + "\n" + SAMPLE;
        String patched = SodiumShaderPatch.apply(comments, VERSIONS);
        check(patched.endsWith(SAMPLE + "\n" + GUARD), "Commented samples were treated as live code");
        String inline = " fragColor = diffuseColor;";
        check(SodiumShaderPatch.apply(SAMPLE + inline, VERSIONS).endsWith(GUARD + "\n" + inline),
                "Inline statement was appended to the closing directive");
        rejects("// " + SAMPLE, "was not found");
        rejects(SAMPLE + "\n" + SAMPLE, "ambiguous");
        rejects("vec4 diffuseColor = texture(u_Other, v_TexCoord, v_MaterialMipBias);", "was not found");
        System.out.println("PASS: installed Sodium 0.5.13 shader, whitespace, comments, inline statements, missing and duplicate samples");
    }
}
