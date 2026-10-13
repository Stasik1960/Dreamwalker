"""Record this observer's explicit review of native capture indices 0..11."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INDEX=ROOT/'build/v126-native-pixel-index-1/index.json'
SUPPLEMENT_INDEX=ROOT/'build/v126-native-pixel-index-2/index.json'
OBSERVATIONS=[
    'Полностью видна закрытая оригинальная дверь 90001: тёмные деревянные панели, центральный орнамент и верхняя перемычка. Нижняя кромка лежит на голубой платформе. Видимых пропавших текстур нет; механизм открытия этот неподвижный кадр не проверяет.',
    'Оригинальные деревянные ворота видны с косого бокового ракурса: высокая тёмная панель, верхнее завершение и толстая боковая стойка. Основание стоит на голубой платформе; текстуры дерева отображаются. Соседнее дерево относится к отдельной платформе.',
    'Нижний настенный орнамент 90423 виден целиком: серый каменный карниз и два нижних выступа. Минимальная нижняя часть доходит до зелёной платформы, модель не спрятана в ней. Каменная фактура и детали читаются, замещающих пурпурных текстур нет.',
    'В центре целиком виден высокий узкий каменный модуль с изогнутой верхней частью, его нижний конец расположен на зелёной платформе. Видны последовательные каменные сегменты без пропавших текстур. Соседние более широкие полу-арки стоят на отдельных платформах.',
    'Светильник 90281 показывает исходную крупную светло-серую кубическую модель с пятнистой каменной фактурой и тёмными краями; видна также верхняя грань. Куб стоит на жёлтой платформе. Форма сопоставлена с исходным six-face кубом 1_spirelamp_001; видимый серый цвет не признан ошибкой текстуры.',
    'Стойка 93151: видны жёлтая платформа и её табличка, но тонкая исходная WEST-грань снята с обратной EAST-стороны и отсекается. Сам объект в этом исходном кадре не виден. Величина его видимых границ — X=.4375, Y=0..2, Z=.4375..5625; крупная каменная арка справа относится к соседнему образцу. Для проверки лицевой стороны потребовался дополнительный западный ракурс; дефект модели не установлен.',
    'Предмет Blood Vial виден как иконка голубоватого пузырька в рамке на каменном демонстрационном кубе. Куб установлен на синей платформе, рядом читается табличка предмета V12. Незаконного размещения предмета как мирового блока и пропавшей иконки не наблюдается.',
    'Инструмент BB виден как диагональная иконка инструмента в рамке на каменном демонстрационном кубе. Куб стоит на синей платформе, табличка показывает инструмент BB. Иконка отображается, отсутствующих текстур нет.',
    'Оригинальная ограда 90002 в состоянии без соседей представлена одним каменным столбиком: видны верхняя и две боковые грани, основание на голубой платформе. Снимок показывает ожидаемый отдельный столбик; соединение с соседями относится к отдельной игровой проверке.',
    'Подвесной фонарь 90595 виден целиком: верхняя подвеска, серый корпус со светлой вставкой и тонкий нижний декор. Минимальные нижние детали касаются уровня зелёной платформы; приподнятый корпус соответствует подвесной модели. Пропавших текстур не видно.',
    'Стопка старых чемоданов 90083 видна целиком на зелёной платформе: три основных предмета с коричневыми крышками и сине-зелёными боковинами, плюс низкая часть справа. Нижние кромки стоят на поверхности; прозрачные промежутки и исходная фактура сохранены.',
    'Цепь 90086 видна как короткая тонкая серая последовательность звеньев над поверхностью зелёной платформы. Весь объект помещается в кадре, табличка показывает вариант 3. Прозрачность вокруг звеньев работает, пустое пространство не заполнено текстурой-заглушкой.'
]


def main():
    index=json.loads(INDEX.read_text(encoding='utf8'))
    supplement=json.loads(SUPPLEMENT_INDEX.read_text(encoding='utf8'))
    assert supplement['productionJarSha256']==index['productionJarSha256']
    additional=supplement['frames'][0]
    assert additional['key']=='SUPPLEMENT-bloodborne_dw:source_ywlloe_stained_glass_pane_noside_alt'
    assert additional['sha256']=='9d323104df2eba54ef1427892ed29d3bbfa4286fde18d7796ec0967839dfd43d'
    assert hashlib.sha256(Path(additional['path']).read_bytes()).hexdigest()==additional['sha256']
    additional_frame={k:additional[k] for k in ('index','fileName','key','sha256')}
    additional_frame.update(status='PASS_OBSERVED_PIXELS',inspection='RAW_PNG',
        observation='В дополнительном западном ракурсе видна сама тонкая WEST-грань 93151: узкая вертикальная последовательность серых сегментов справа от таблички на жёлтой платформе, с прозрачными промежутками. Верх и низ детали помещаются в кадре, нижний видимый сегмент доходит до уровня платформы. Пурпурно-чёрной заглушки текстуры нет. Крупная каменная модель на правом краю принадлежит соседнему образцу, а не этой тонкой детали.')
    supplement_report={'status':'PASS_ASSIGNED_PIXELS','productionJarSha256':supplement['productionJarSha256'],
        'captureSha256':supplement['captureSha256'],'index':str(SUPPLEMENT_INDEX),'frames':[additional_frame],
        'coveredOriginalKey':'bloodborne_dw:source_ywlloe_stained_glass_pane_noside_alt',
        'rawPngsInspected':1,'physicalInteractionAndCollisionChecks':'NOT_RUN_BY_PIXEL_REVIEW'}
    frames=[]
    for source,observation in zip(index['frames'][:12],OBSERVATIONS):
        assert hashlib.sha256(Path(source['path']).read_bytes()).hexdigest()==source['sha256']
        frame={k:source[k] for k in ('index','fileName','key','sha256')}
        frame.update(status='PASS_OBSERVED_PIXELS',observation=observation,inspection='RAW_PNG_AND_CONTACT_SHEET')
        if source['index']==5:
            frame['observation']+=' Ограничение этого исходного ракурса закрыто отдельно просмотренным западным кадром v126s-001; он показывает всю тонкую грань с её фактурой и основанием.'
            frame['coverage']='ORIGINAL_BACKFACE_LIMITATION_COVERED_BY_OBSERVED_WEST_SIDE_SUPPLEMENT'
            frame['supplementalEvidence']={'captureSha256':supplement['captureSha256'],
                'fileName':additional['fileName'],'sha256':additional['sha256'],'review':'review-supplement-000.json'}
        frames.append(frame)
    report={'status':'PASS_ASSIGNED_PIXELS',
            'productionJarSha256':index['productionJarSha256'],'captureSha256':index['captureSha256'],
            'index':str(INDEX),'assignedIndices':[0,11],'rawPngsInspected':12,'frames':frames,
            'pendingSupplementalKeys':[], 'supplementalFramesInspected':1,
            'confirmedNewModelDefects':[], 'physicalInteractionAndCollisionChecks':'NOT_RUN_BY_PIXEL_REVIEW',
            'originalV12Modified':False}
    destination=ROOT/'docs/v12.6/native/review-000-011.json'
    destination.parent.mkdir(parents=True,exist_ok=True)
    (destination.parent/'review-supplement-000.json').write_text(json.dumps(supplement_report,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    destination.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps({'status':report['status'],'rawPngsInspected':13,'pendingSupplemental':0}))


if __name__=='__main__':main()
