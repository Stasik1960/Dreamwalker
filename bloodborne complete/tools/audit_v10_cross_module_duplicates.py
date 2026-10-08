"""Read-only necessary-contract comparison for architecture18 × canonical RP69.

An implementation class/name alone is never a distinction. The strongest
observable interaction contract is ordinary Survival deletion and one art drop
versus RP Creative-only attack deletion with zero drops. Installation is separate
source evidence, never the only class-name-based basis.
Both types may render similar source art and both support offset into terrain;
that does not erase the different ordinary occupied-cell outcome and ownership.
"""
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def proof(path, tokens):
    file = ROOT / path
    raw = file.read_bytes()
    lines = raw.decode("utf8").splitlines()
    return {"path": path, "sha256": hashlib.sha256(raw).hexdigest(),
            "lines": [{"line": next(n for n, s in enumerate(lines, 1) if token in s), "token": token} for token in tokens]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--architecture-report", type=Path, default=ROOT / "reports/V10_ARCHITECTURE_DUPLICATE_AUDIT_CANDIDATE7.json")
    parser.add_argument("--report", type=Path, default=ROOT / "reports/V10_CROSS_MODULE_DUPLICATE_AUDIT_CANDIDATE7.json")
    parser.add_argument("--markdown", type=Path, default=ROOT / "docs/REVIEW_V10_CROSS_MODULE_DUPLICATES.md")
    parser.add_argument("--native-xml", type=Path)
    parser.add_argument("--freeze-report", type=Path)
    args = parser.parse_args()
    assert not args.report.exists() and not args.markdown.exists(), "Preserve old evidence"
    raw = args.architecture_report.read_bytes()
    architecture = json.loads(raw)
    native = {"status": "NOT_CLAIMED_FOR_THIS_STATIC_AUDIT"}
    if args.native_xml or args.freeze_report:
        assert args.native_xml and args.freeze_report, "Bind actual native XML through its frozen artifact manifest"
        frozen = json.loads(args.freeze_report.read_bytes())
        xml_raw = args.native_xml.read_bytes()
        assert frozen["sha256"] == architecture["production"]["sha256"]
        assert Path(frozen["nativeXml"]).resolve() == args.native_xml.resolve()
        assert frozen["nativeXmlSha256"] == hashlib.sha256(xml_raw).hexdigest()
        suite = ET.fromstring(xml_raw)
        cases = list(suite.iter("testcase"))
        assert len(cases) == frozen["nativeTests"] and not list(suite.iter("failure")) and not list(suite.iter("error"))
        native = {"status": "PASS_EXACT_FROZEN_ARTIFACT_NATIVE_XML", "path": str(args.native_xml),
                  "sha256": hashlib.sha256(xml_raw).hexdigest(), "methods": len(cases), "failures": 0,
                  "freezeManifest": str(args.freeze_report), "freezeManifestSha256": hashlib.sha256(args.freeze_report.read_bytes()).hexdigest()}
    arch = architecture["architecture"]
    rp = [row for row in architecture["catalogueRoster"] if row["kind"] == "rp_object" and row["offered"]]
    assert len(arch) == 18 and len(rp) == 69
    base = "src/architecture/java/dev/dreamwalker/bloodbornedw/"
    evidence = [proof(base + "composite/CompositeItem.java", ["extends BlockItem", "CompositeRuntime.place"]),
                proof(base + "architecture/PrototypeLadderItem.java", ["extends BlockItem", "if(!context.canPlace())"]),
                proof(base + "architecture/wall/PrototypeWallItem.java", ["extends BlockItem", "getBlock"]),
                proof(base + "runtime/TransactionCore.java", ["root"]),
                proof("src/rp/java/dev/dreamwalker/bloodbornerp/object/ObjectRegistry.java", ["private static boolean placeAt", "world.spawnEntity(entity)", "origin=surface.subtract"]),
                proof("reports/user-review-v9/request.txt", ["Разреши ставить блоки рядом и внутри", "Это разрешение не означает возможность записать два обычных BlockState"])]
    interaction_sources = [proof(base + "composite/CompositeRootBlock.java", ["strength(.4F)", "getDroppedStacks"]),
        proof(base + "composite/CompositeRuntime.java", ["private static boolean canModify", "if(drop&&!item.isEmpty())"]),
        proof(base + "architecture/PrototypeArchitecture.java", [".strength(.4F)"]),
        proof(base + "architecture/wall/PrototypeWallArchitecture.java", ["strength(2F)"]),
        proof(base + "architecture/wall/PrototypeWallBlock.java", ["getDroppedStacks", "artisticStack"]),
        proof(base + "architecture/PrototypeLadderBlock.java", ["getDroppedStacks"]),
        proof("src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java", ["public boolean damage", "!player.isCreative()", "public void removeByBuilder"]),
        proof("src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/SourceLadderGameTests.java", ["NativeSurvivalBreakPermissionGuardChecksWholePair", "exactly one item"]),
        proof("src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/PrototypeDoorGameTests.java", ["one logical object drops one item"]),
        proof("src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/PrototypeWallGameTests.java", ["one wall yields one registry item"])]
    rp_source = (ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/ObjectRegistry.java").read_text(encoding="utf8")
    section = rp_source.split("private static boolean placeAt", 1)[1].split("private static Map", 1)[0]
    assert "world.spawnEntity(entity)" in section and "world.setBlockState" not in section
    pairs = [{"architectureTemporaryId": a["temporaryId"], "architectureRegistry": a["registryId"],
              "rpTemporaryId": r["temporaryId"], "rpRegistry": r["registryId"],
              "decision": "NOT_FULL_DUPLICATE_DIFFERENT_ORDINARY_REMOVAL_AND_DROP_CONTRACT",
              "witnessId": "SURVIVAL_ARCHITECTURE_ONE_ART_DROP_VS_CREATIVE_ONLY_RP_ZERO_DROPS"} for a in arch for r in rp]
    report = {"schema": "dreamwalker-v10-cross-module-full-type-duplicate-audit-v1",
              "status": "READ_ONLY_ALL_1242_CROSS_PAIRS_REFUTED_BY_REAL_REMOVAL_DROP_CONTRACT",
              "production": architecture["production"], "architectureAudit": {"path": str(args.architecture_report), "sha256": hashlib.sha256(raw).hexdigest()}, "native": native,
              "summary": {"architecture": 18, "canonicalRp": 69, "crossPairs": len(pairs), "newConfirmedCrossGroups": 0,
                          "canonicalTotal": 87, "canonicalAllPairs": 3741,
                          "inFamilyPartition": {"architecturePairs": 153, "rpPairs": 2346, "crossPairs": 1242},
                          "rpInFamilyStatus": "Separate rp_integration report; do not infer completion from cross audit"},
              "witnesses": [{"id": "SURVIVAL_ARCHITECTURE_ONE_ART_DROP_VS_CREATIVE_ONLY_RP_ZERO_DROPS",
                 "architecture": "All18 use positive-hardness .4 or2 native roots. Ordinary permitted Survival break is allowed, cleans exact owned object and returns one canonical artistic item. SourceClone permitted Survival pairbreak has the same oneitem contract; no OP/Creative-only condition in wholeowned remove permissions.",
                 "rp": "All69 use RpObjectEntity.damage: PLAYER_ATTACK from player.isCreative only; Survival player attack returns false. Authorized Creative deletion calls removeByBuilder -> links/lamp cleanup and discard, zero item drops. Builder/admin deletion also has zero drops.",
                 "observableDifference": "For the same permitted emptyhand Survival attacker, architecture supports native break and one own-type item; RP rejects deletion. Creative RP deletion yields no item, unlike architecture Survival drop. This is interaction/function/drop, not Block-versus-entity class identity.",
                 "sources": interaction_sources,
                 "runtimeEvidenceScope": (f"Exact frozen native XML has {native['methods']} passing methods, including SourceLadder Survival pairpermission/oneitem, dooroneitem and wallcanonicaloneitem regressions. " if native.get("methods") else "Native test source paths are evidence of the intended checks, not a new passing runtime claim. ") + "Actual ordinary client/item/attack/pick proof belongs to separate artifact-bound client reports; previous candidates remain historical. No new exhaustive Survival-client enumeration claimed."},
                {"id": "NATIVE_ROOT_SLOT_VS_RP_ENTITY_WITHOUT_NATIVE_SLOT",
                 "architecture": "Ordinary item is native BlockItem/transaction root. It requires an available replaceable BlockState root; committing writes own root/residentBE. Cannot stack a second ordinary root in one occupied native cell or overwrite a foreign native BE.",
                 "rp": "All69 canonical ordinary RP use ObjectRegistry.placeAt: spawn independent EntityType/UUID at calculated surface origin; no root native BlockState/BE reservation/write. Native solids/otherRP do not reject entity construction under authorized V10 permanent overlap policy; living/right/build/chunk guards still apply.",
                 "observableDifference": "Two RP instances may share their calculated root position while preserving the native cell; two ordinary architectural roots cannot occupy one native slot. Subsequent offset overlap in both modules does not make their ordinary installation contract identical.",
                 "notUsedAsProof": ["Java class identity", "Block versus entity name by itself", "different registry/TEMP identifier", "different JSON formats"],
                 "sources": evidence}],
              "potentialSimilarPairs": [
                {"architecture": "90001", "rpAsset": "door_1", "reason": "Architecture central leaf/jamb/header narrow volumes and static side passage; RP legacy square closed body/open-empty with full source bone animation. Shared OPEN/CLOSE policies do not remove different source geometry, physics and native-slot installation."},
                {"architecture": "90005", "rpAssets": ["tree1", "tree2", "tree3"], "reason": "Architecture18authoredparts/acceptedlowerUV/physicalstem+4selectionprisms; RP entity-source geo/native collider dimensions/selection and no native root. Repeated placed trees are instances, never catalogue duplicates by location."},
                {"architecture": ["90006", "90018", "90019"], "rpAsset": "ladder", "reason": "Architecture one-cell section, native side/foundation support and8yaw; RP long extended/collapsed animation/anchors/climb zones/top platform. SourceClone fixed backing is legacyinstallation role, not a new independent type."}],
              "removedIdToKeptId": [], "confirmedGroups": [], "exceptions": architecture["preservedException"],
              "scope": "Static necessary-condition refutation. No new runtime/GUI/compatibility merge run, no world mutation, no original archive edits, no full-city claim."}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    args.markdown.write_text("\n".join([
        "# Архитектура ↔ RP: проверка полных дублей V10", "",
        "Учтены все 1242 пары: 18 самостоятельных архитектурных типов × 69 канонических RP. Новых межмодульных групп полных дублей не подтверждено.", "",
        "Основное основание — реальное различие взаимодействия/выпадения. Все 18 архитектурных корней имеют положительную твёрдость (.4 либо2): разрешённое штатное Survival-разрушение удаляет выбранного владельца и даёт один его художественный предмет. Все 69 RP используют общий damage с обязательным player.isCreative; Survival-атака не удаляет RP. Creative-удаление RP очищает связи и discard без выпадения. Этот контракт различается независимо от схожего рисунка или Java-класса; текущие native-тесты подтверждают соответствующие oneitem/permission ветки, новый exhaustive Survival-клиентский прогон не заявляется.", "",
        "Дополнительное различие обычной установки: архитектурный корень занимает собственную ячейку BlockState и создаёт владельца/BE, тогда как RP создаёт самостоятельную сущность без записи/резервирования такой ячейки. Нельзя заменять этим только названием «block/entity» сравнение функции. По V10 RP может пересекать твёрдые блоки/другие RP с их сохранением; обе реализации сохраняют свои исходные права и границы.", "",
        "Вертикальное смещение архитектуры тоже может погружать её рисунок и физику в соседние блоки, но исходная занятая корневая ячейка остаётся. Общая поддержка GUI/рычагов/имен не отменяет разные монтажные, физические и функциональные контракты двери, дерева и лестниц двух модулей.", "",
        "Внутри архитектуры проверены ещё 153 пары; внутри 69 RP остаются отдельные 2346 пар в отчёте RP-аудитора. Вместе это разбиение всех 3741 пар 87 канонических типов. Семь старых RP-алиасов проверяются дополнительно и сохраняют зарезервированные номера. Этот межмодульный отчёт сам по себе не объявляет завершение всего каталога.", "",
        f"Матрица и точные исходные строки: [{args.report.name}](../reports/{args.report.name}).", "",
        "Никаких registry/предметов/ресурсов, установленных UUID, связей, имён или маршрутов здесь не изменено. Window03 [90020] сохраняется по прямому исключению пользователя. Карта, финальные числовые registry ID и художественная приёмка всего каталога не завершены.", ""
    ]), encoding="utf8")
    print(json.dumps({"status": report["status"], "summary": report["summary"], "report": str(args.report)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
