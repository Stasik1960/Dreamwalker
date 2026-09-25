# Bloodborne Blocks 2.1.0-beta.1

Fabric 1.20.1, Java 17, Fabric API. 49 production families, 1644 states, 722 meshes.

- `bloodborne-blocks-2.1.0-beta.1-mc1.20.1.jar` — beta.
- `production-beta-gallery-20260925.zip` — 49 families / 77 specimens, checked helper ownership.
- `release-proof.json` / `.sha256` — checksums and verification evidence.

GRAPHICAL_CLIENT_ACCEPTANCE_NOT_RUN. This beta is for the production gallery. The old modular 2.0.2 city contains IDs outside this palette; conversion reports must be reviewed before attempting to use that city with beta.

## Карта

[Две копии и отчёты конвертации](WORLDS.md): **UNRESOLVED / NOT BETA READY**. Проверки NBT и идемпотентности пройдены; старые архитектурные ID остаются несовместимыми с beta.

## ALT для художника

- [ALT Art Kit](Bloodborne-Blocks-2.1.0-beta.1-ALT-Art-Kit.zip): папки по 49 production-предметам, BASE_REFERENCE, ALT_WORK, PNG и manifest с runtime_path/SHA256.
- [Готовый шаблон ресурс-пака](ALT-ResourcePack-Template.zip): Minecraft 1.20.1. Разместите выше ресурсов мода; оформление предмета — visual=alt.
- [Проверка комплекта](alt-proof.json): 3618 хешей файлов и 1214 PNG проверены; Java resolver загрузил 822 модели и 1142 текстуры.

Имена в Art Kit удобны для редактирования; переносите результаты в точные runtime_path из manifest внутри шаблона. Не копируйте by-block непосредственно в assets. ALT меняет только визуал, не коллизию, anchor или игровой контракт.
