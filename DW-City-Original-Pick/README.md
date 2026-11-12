# Dreamwalker City Original Pick

Клиентский мод `dw_city_original_pick` выдаёт исходный предмет по J в креативе, когда игрок смотрит на индексированный фрагмент города. Сборка 1.0.1 рассчитана на [DreamWalker v22 Distinct Forms](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-distinct-forms.1) и карту [Bloodborne Bush Restoration](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-bush-restoration.1).

`rebase_index.py` переупаковывает оригинальный JAR 1.0.0 из клиентского профиля. Он проверяет хеши каталогов, физику, нумерацию и сохранённые полигоны, переносит исходные модели для остаточных фрагментов, а для плит использует новые ID точной формы. У фрагментов с удалённой геометрией остаются только модели, подтверждённые сохранившимися полигонами.

Для сборки нужны оригинальный аддон, JAR Seam Repair, JAR Distinct Forms и JSON-отчёт из релиза Bush Restoration. Запустите команду из этого каталога. [Релиз Seam Repair](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-seam-repair-compact-startup.1)

```powershell
python rebase_index.py Dreamwalker-City-Original-Pick-1.0.0.jar DreamWalker-v22-Seam-Repair-Compact-Startup.jar DreamWalker-v22-Bush-Restoration-Distinct-Forms.jar Bloodborne-v22-Bush-Restoration-report.json Dreamwalker-City-Original-Pick-1.0.1.jar
```

Тест использует обработчик индекса из готового аддона. Minecraft запускать не требуется.

```powershell
$jar = (Resolve-Path Dreamwalker-City-Original-Pick-1.0.1.jar).Path
javac -cp $jar -d build/check tests/IndexCompatibilityCheck.java
java -cp "build/check;$jar" dev.dreamwalker.citypick.IndexCompatibilityCheck
```
