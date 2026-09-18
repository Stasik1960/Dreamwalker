# Локальные зависимости сборки

JAR сохранены в версиях, использованных для опубликованного backport.2.
Контрольные суммы: [SHA256SUMS](SHA256SUMS). Авторство и лицензии внутри JAR
сохранены. Сведения о загрузках находятся в `../reports/dependencies-1.20.1.json`
и `../reports/optional-dependencies.json`.

GeckoLib и AAA Particles включаются в готовый мод. Jade, Xaero и JourneyMap API
служат только для компиляции интеграций и в итоговый JAR не включаются.
JourneyMap API представлен в Yarn named namespace 1.20.1.
Player Animation Library собирается из соседнего `../player-animation-backport/`.
