# Команды публикации после финальной приёмки

Это последовательность для родительского исполнителя. Публикация разрешена
владельцем, но команды здесь ещё не выполнялись. Предварительные проверки
не заменяют игровые доказательства и не создают acceptance.

Все команды выполняются из `bloodborne complete`. На этом устройстве процессы
Python идут через `Guard.ps1 -Mode Operation` и журнал `tools/run_v124_stage.py`.
Точные 15 аргументов записи acceptance перечислены в
[FINAL-ACCEPTANCE-INPUTS-RU.md](FINAL-ACCEPTANCE-INPUTS-RU.md).
Сначала полный вызов recorder с `--validate-only`, затем тот же вызов без
этого флага. Пути к настоящим отчётам и финальный checkpoint выбирает Root.

Перед упаковкой зафиксировать просмотренные исходники и финальные docs в Git.
Код после принятого build/native checkpoint не меняется. Новый commit только
доказательств/документов допустим; производственный `src/tools/gradle` обязан
остаться точным. Нельзя автоматически перезаписывать прежний acceptance или
частичный release-каталог.

```text
python tools/package_v127_release.py --acceptance docs/v12.7/acceptance.json --runtime-plan build/v127-runtime-conversion-final2 --source-plan build/v127-full-conversion-first.sqlite --destination ../releases/Dreamwalker-BB/v12.7-final --validate-only
python tools/package_v127_release.py --acceptance docs/v12.7/acceptance.json --runtime-plan build/v127-runtime-conversion-final2 --source-plan build/v127-full-conversion-first.sqlite --destination ../releases/Dreamwalker-BB/v12.7-final
python tools/publish_v127_release.py --package ../releases/Dreamwalker-BB/v12.7-final --tag bb-v12.7-final
```

Последний вызов — **только локальный preflight**. Он проверяет SHA/размер всех
файлов поставки, acceptance, ветку и origin, происхождение packaged commit,
отсутствие изменений кода после него и чистоту tracked-файлов. Он не обращается
к credential helper, не пушит и не создаёт Release.

После просмотра готовых архивов Root выполняет публикацию:

```text
python tools/publish_v127_release.py --package ../releases/Dreamwalker-BB/v12.7-final --tag bb-v12.7-final --publish
```

Закреплённые назначения: репозиторий `Stasik1960/Dreamwalker`, ветка
`codex/bb-v12-tool`, тег `bb-v12.7-final`. Публикация создаёт аннотированный
неизменяемый тег и обычный push ветки/тега. Force push отсутствует. Существующий
тег должен указывать на тот же точный HEAD, иначе выполнение прекращается.
Если удалённая ветка изменилась независимо, обычный push также останавливается;
не обходить это удалением тега или force push.

GitHub Release сначала создаётся draft. Ассеты загружаются потоками; завершённые
загрузки восстанавливаются по имени, размеру и удалённому SHA. Чужой Release,
повреждённый или конфликтующий ассет сохраняется для разбора без замены.
После всех загрузок draft публикуется; каждый публичный файл скачивается
потоком без Authorization и проверяется по SHA/размеру. Итоговый обязательный
статус — `PASS_GITHUB_PUBLISHED_AND_PUBLIC_DOWNLOAD_VERIFIED` в
`build/v127-release-publication.json`. Факт push или открытого Release сам по
себе этого статуса не заменяет.

Credential helper вызывается только при `--publish`. Токен остаётся в памяти
процесса и не выводится, не сохраняется, не передаётся в командной строке/URL.
Значения credential helper не копировать в журнал, документацию или отчёт.

Последняя read-only проверка удалённого состояния: origin указывает на
`https://github.com/Stasik1960/Dreamwalker.git`; удалённая ветка была
`8b770a03de21554a21c91700a1c1c6fe86c894c4`, предок локального checkpoint.
Удалённый и локальный `bb-v12.7-final` в той проверке отсутствовали. Эти значения
могут измениться: Root повторно проверяет их перед фактической публикацией.
