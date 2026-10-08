"""Check the actual remapped optional adapter descriptor against the installed external API."""
import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    actual_sha = digest(args.jar)
    assert actual_sha == args.expected_sha256.lower(), "actual remapped artifact hash mismatch"
    with zipfile.ZipFile(args.jar) as archive:
        configuration = json.loads(archive.read("bloodborne_dw.composite.mixins.json"))
        assert configuration["mixins"].count("RpReachAttributesCompatibilityMixin") == 1
        assert "dev/dreamwalker/bloodbornedw/architecture/RpCreativeAttackReach.class" in archive.namelist()
    javap = Path("C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe")
    result = subprocess.run([str(javap), "-classpath", str(args.jar), "-p", "-c", "-v", "dev.dreamwalker.bloodbornedw.composite.mixin.RpReachAttributesCompatibilityMixin"], capture_output=True, check=True)
    text = result.stdout.decode("cp1251").replace("\r\r\n", "\n").replace("\r\n", "\n")
    assert "org.spongepowered.asm.mixin.Pseudo" in text
    assert 'targets=["com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes"]' in text
    assert 'method=["isWithinAttackRange"]' in text and "remap=false" in text
    assert "getSquaredAttackRange" in text and "(Lnet/minecraft/class_1309;D)D" in text
    assert "(Lnet/minecraft/class_1657;Lnet/minecraft/class_1297;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V" in text
    assert "RpCreativeAttackReach.editablePartHit" in text
    assert "class_243.method_1025" in text, "actual eye-to-hit squared distance must remain in remapped adapter"
    assert "double 64.0d" in text
    primary = ROOT / "reports/runtime" / (args.output.stem + "_BYTECODE.txt")
    primary.parent.mkdir(parents=True, exist_ok=True)
    primary.write_text(text, encoding="utf-8")
    report = {
        "schema": "dw-v10-rp-reach-remapped-descriptor-v1",
        "status": "PASS_ACTUAL_REMAPPED_OPTIONAL_EXTERNAL_API_DESCRIPTOR",
        "artifact": str(args.jar.resolve()), "artifactSha256": actual_sha,
        "externalClassUnmapped": "com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes",
        "externalMethodUnmapped": "isWithinAttackRange",
        "externalShadowUnmapped": "getSquaredAttackRange",
        "shadowDescriptor": "(Lnet/minecraft/class_1309;D)D",
        "handlerDescriptor": "(Lnet/minecraft/class_1657;Lnet/minecraft/class_1297;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
        "pseudoOptionalTarget": True, "libraryBaseSquared": 64.0,
        "primaryBytecode": {"path": str(primary), "sha256": digest(primary)},
        "scope": "Static final-JAR descriptor/annotation/instruction check only; no game launch and no runtime compatibility claim",
        "optionalApiAbsentBoot": "NOT_PROVED_BY_STATIC_CHECK",
        "fullProvidedProfileUpperPartAttack": "NOT_PROVED_BY_STATIC_CHECK",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(report["status"])


if __name__ == "__main__":
    main()
