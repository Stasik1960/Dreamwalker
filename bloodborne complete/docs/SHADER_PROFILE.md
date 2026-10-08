# Изолированный профиль шейдера

`tools/prepare_shader_profile.py` предоставляет `prepare_shader_profile(run_dir, shaderPackPath, settingsPath=None) -> provenance`. Он пишет только в существующий `build/runtime-client-*` этого проекта. Выбранный Iris должен уже находиться в mods этого профиля с точным SHA из MODSET_PROFILE.json. Запуск игры выполняется отдельно.

Проверен предоставленный Iris 1.7.6+mc1.20.1, SHA256 `9eb15e563e0c9ae6eff15b7863f8432dd290cf9f724c7373e9b15b14aa829ed5`. Java17 javap подтверждает чтение/запись shaderPack и enableShaders, config path config/iris.properties, sidecar path shaderpacks/<выбранное имя ZIP>.txt. Дизассемблирование и SHA class-файлов записаны в shader-audit собственного профиля; неподтверждённые ключи в конфигурацию не добавляются.

Подготовлен `build/runtime-client-shader-profile-prepared-v5/shader-profile.json`. Kappa_v5.2.zip скопирован побайтно, SHA256 `382fadd7d389260522099b25f9dc693bbc81b89220ec5b55459cce74e6c264d0`. Присланный Kappa_BB.zip (1).txt сохранён побайтно под требуемым Iris именем Kappa_v5.2.zip.txt, SHA256 `32e38781a3ddd9340ff499edeea2833eb90ac5a6ae4ba670c890f6ac23c5fe0f`. Различие исходного имени BB и выбранного ZIP v5.2 явно фиксируется; значения не менялись.

Все 42 параметра пресета найдены в исходных декларациях Kappa5.2; значения совпадают с объявленными boolean/range tokens. Это статическая проверка предложенного применения пресета BB к присланному ZIP v5.2. Реальная регистрация Iris options, компиляция GLSL, рендер, производительность и совместимость полной сборки пока NOT_RUN. Они подтверждаются только отдельным клиентским логом и визуальным результатом. При неизвестных параметрах helper сохраняет исходные bytes и указывает статическое расхождение, без скрытой корректировки.

В изолированный config/iris.properties записаны только shaderPack=Kappa_v5.2.zip и enableShaders=true. Исходные ZIP/TXT и установленная сборка пользователя не изменены. Первый полный клиентский контроль без шейдеров остаётся отдельным профилем от последующего Kappa-контроля.
