"""Update only owned RP prose to the existing completed FULL9 evidence."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def main():
    updates={
        'docs/RP_DIAGNOSTICS_V9.md':{
            'Full109+Kappa9 heap6GiB пока PENDING.':'Full109+Kappa9 heap6GiB завершился normal world-save/exit0/PASS; отдельный строгий stage настроек/counter Iris в этом marker не запрошен.',
            'Full109+Kappa9 heap6GiB ещё PENDING.':'Full109+Kappa9 heap6GiB завершился normal world-save/exit0/PASS. Его связанные diagnostic ZIP проверены отдельно;42 effective Iris settings/counter здесь NOT_PROBED.',
            'Новый summary verifier имеет9 прошедших synthetic tests; это отдельная проверка инструмента, не runtime PASS.':'Diagnostics/export verifier имеет10 прошедших synthetic tests, включая отклонение подмены integrated-server ZIP другой сессией; это отдельная проверка инструмента, не runtime PASS.',
            'Текущий production11 summary исправлен; проверка его настоящих ZIP пока PENDING.':'Текущий production11 summary исправлен; actual client9/reenter9 и independent RELEASE8 уже подтвердили читаемость настоящих ZIP. Исторический production10 summary этим не переименован в PASS.'},
        'docs/RP_REVIEW_V9.md':{
            'Единственный ещё выполняемый общий runtime — full109+Kappa9 с heap6GiB; его результат пока PENDING.':'Full109+Kappa9 с heap6GiB тоже завершился normal world-save/exit0/PASS. Это объединённый full/shader-configured запуск, а не отдельный plain full client; строгий stage42 effective Iris settings/counter в его marker не запрошен.',
            'full109+Kappa9 heap6GiB пока PENDING.':'full109+Kappa9 heap6GiB завершился normal world-save/exit0/PASS; отдельный строгий Iris stage в этом marker отсутствует.',
            'Full109+Kappa9 heap6GiB ещё PENDING; manual visual/gameplay остаётся PENDING_USER_REVIEW.':'Full109+Kappa9 heap6GiB также завершился normal world-save/exit0/PASS и повторил10 loaded RP model/tracker checks. Его диагностические ZIP сохранены отдельной текущей проверкой; strict42 effective Iris settings/counter NOT_PROBED. Manual visual/gameplay остаётся PENDING_USER_REVIEW.',
            'Readable summary gate добавлен после этого исторического verifier PASS; current client/exports ожидают нового прогона.':'Readable summary gate добавлен после этого исторического verifier PASS; результаты production10 не заменяют отдельный actual production11 client/exports RELEASE9.'}}
    for name,replacements in updates.items():
        path=ROOT/name;text=path.read_text(encoding='utf8')
        for old,new in replacements.items():
            if old in text:text=text.replace(old,new)
            elif new not in text:raise ValueError('Owned document literal changed: '+old)
        path.write_text(text,encoding='utf8',newline='\n')
    note='''

## Дополнительный полный клиент RELEASE9

`reports/CLIENT_FULL_KAPPA_V9_RELEASE9.json` завершился стандартным save/exit0 на том же production11 SHA. Это109 выбранных dependency JAR плюс combined production и отдельный QA,111 фактических root JAR, `-Xmx6G`. Kappa_v5.2.zip и пользовательские42 settings скопированы побайтно. Actual `graphics.json.optionalIris` наблюдает enabled Kappa/IrisRenderingPipeline без fallback; marker не содержал `expectedShaderPack`, поэтому `irisRuntime`,42 effective getters и frame-counter stage не выполнялись. Такой отчёт проходит optional full-client load/save gate; строгий `--shader-report` его отклоняет. Дополнительный текущий shader marker/run проверяется отдельно после фактического завершения. Успех полного профиля при4GiB не доказан.

`reports/RP_DIAGNOSTICS_FULL_CLIENT_V9_RELEASE9.json` проверил оба маленьких actual diagnostic ZIP этого процесса: session `247656fb-b353-4aab-8977-47ed6e8f9c41`, current JAR environment, server placement operation и actual renderer ACK, cleanup временного owner, OFF/ON/OFF и читаемый UTF-8 server summary. `package_review_v9.py --full-client-report` автоматически сохраняет их побайтно в primary evidence. Они дополняют обязательную independent RELEASE8 проверку default60/сохранения/повторного входа; её пять исходных runtime gates не заменены.10 loaded RP representatives снова прошли model/tracker/geometry checks; новая RP item placement→server→model цепочка не пробовалась.

|Full109/Kappa9 окно|Выборок|Среднее кадра ms|p95 ms|p99 ms|Среднее render-thread CPU ms|
|---|---:|---:|---:|---:|---:|
|OFF_BEFORE|162|30.4128|45.7197|64.1433|29.5139|
|ON_IDENTICAL_CLEANED_SCENE|38|122.8902|170.8603|181.4169|121.2993|
|OFF_AFTER|157|31.6253|41.8928|51.5871|30.8519|

Полный профиль показал сильное наблюдаемое замедление в ON-окне; оно не скрыто общим runtime PASS. Это последовательные короткие окна одного процесса с восстановленными hand/camera/native scene, а не доказанная точная причинная стоимость каждого RP объекта. GPU time не измерен; submit/thread CPU не заменяют GPU duration. Собственные server hooks остаются inclusive/nested elapsed samples, их нельзя суммировать как независимый CPU расход. Запись выключена по умолчанию; причины ON-замедления требуют отдельного анализа. Ручная визуальная/игровая приёмка и полный город остаются непроверенными.
'''
    for name in updates:
        path=ROOT/name;text=path.read_text(encoding='utf8')
        if '## Дополнительный полный клиент RELEASE9' not in text:path.write_text(text.rstrip()+note+'\n',encoding='utf8',newline='\n')
    print('PASS owned RP FULL9 prose updated; no runtime or historical raw evidence changed.')


if __name__=='__main__':main()
