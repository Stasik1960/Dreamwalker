"""Isolated Java17 compile of the optional RP attack reach adapter and its real native guard case."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    cached = (ROOT / "build/review-client-api-check/compile.args").read_text(encoding="utf-8").splitlines()
    cp = cached[cached.index('"-cp"') + 1].strip('"')
    mixin = sorted(Path("C:/Users/vakir/.gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin").rglob("*.jar"))
    if not mixin:
        raise ValueError("Cached Fabric Mixin compile API is absent")
    cp += ";" + str(mixin[-1])
    sources = [
        ROOT / "src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/RpCreativeAttackReach.java",
        ROOT / "src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/RpReachAttributesCompatibilityMixin.java",
        ROOT / "src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/RpAttackReachCompatibilityGameTests.java",
    ]
    output = ROOT / "build/v10-rp-attack-reach-api-check"
    output.mkdir(parents=True, exist_ok=True)
    args = ["--release", "17", "-encoding", "UTF-8", "-proc:none", "-sourcepath", "", "-cp", cp, "-d", str(output)] + [str(p) for p in sources]
    argfile = output / "compile.args"
    argfile.write_text("\n".join('"' + s.replace("\\", "/") + '"' for s in args) + "\n", encoding="utf-8")
    javac = Path("C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javac.exe")
    process = subprocess.run([str(javac), "@" + str(argfile)], capture_output=True)
    log = process.stdout + process.stderr
    primary = ROOT / "reports/runtime/V10_RP_ATTACK_REACH_ISOLATED_COMPILE.log"
    primary.parent.mkdir(parents=True, exist_ok=True)
    primary.write_bytes(log)
    report = {
        "schema": "dw-rp-attack-reach-isolated-compile-v1",
        "status": "PASS_ISOLATED_JAVA17_API" if process.returncode == 0 else "FAIL_ISOLATED_JAVA17_API",
        "sources": [{"path": p.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources],
        "mixinConfiguration": {"path": "src/architecture/resources/bloodborne_dw.composite.mixins.json", "sha256": hashlib.sha256((ROOT / "src/architecture/resources/bloodborne_dw.composite.mixins.json").read_bytes()).hexdigest()},
        "compileLog": str(primary), "compileLogSha256": hashlib.sha256(log).hexdigest(),
        "exitCode": process.returncode, "productionClassesModified": False,
        "gradleOrGameStarted": False,
        "nativeCaseCount": 1, "native": "NOT_RUN", "optionalApiAbsentBoot": "NOT_RUN", "fullProfileActualPacket": "NOT_RUN",
        "remappedFinalJarDescriptorCheck": "REQUIRED_AFTER_PARENT_BUILD",
    }
    target = ROOT / "reports/V10_RP_ATTACK_REACH_ISOLATED_COMPILE.json"
    target.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(report["status"])
    if log:
        print(log.decode("utf-8", errors="replace"))
    raise SystemExit(process.returncode)


if __name__ == "__main__":
    main()
