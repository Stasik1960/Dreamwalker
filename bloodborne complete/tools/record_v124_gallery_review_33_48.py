"""Bind sixteen actually viewed original gallery PNGs to the complete final capture."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'build/runtime-client-v124-flatgallery-client-final-2'
DOC = ROOT / 'docs/v12.4-dedup'
DIGEST = '4c9e202cff6ae5acd17386a30547726948a0d0d9babf4e529c84ec5c51b0c640'
OBSERVATIONS = {
    33: 'Отдельный исходный столб ограды виден на синей платформе; текстура камня и подпись присутствуют.',
    34: 'Исходная деревянная приставная лестница видна целиком над собственной синей платформой; обе опоры и верхняя перекладина отображаются.',
    35: 'Крупный исходный лестничный марш полностью помещается в кадре; нижние опоры стоят над выделенной платформой, верхняя площадка и ограждение видны.',
    36: 'Высокая исходная лестница RP видна от нижнего основания на платформе до верхнего выступа; отсутствующего энтити или обрезанного верха нет.',
    37: 'Колёсная клетка RP видна целиком над своей платформой; колёса, решётки и табличка отображаются.',
    38: 'Двухъярусная клетка RP видна целиком; нижнее основание расположено на платформе, оба яруса и решётки отображаются.',
    39: 'Низкая клетка RP видна целиком на собственной платформе; нижняя рама, вертикальные прутья и подпись отображаются.',
    40: 'Малая люстра RP полностью видна вместе с цепью, верхним креплением и нижними свечами над выделенной платформой; пропавших текстур нет.',
    41: 'Большая люстра RP полностью видна вместе с длинной цепью и всем ярусом свечей над выделенной платформой; соседние станции не перекрывают объект.',
    42: 'Boom Hammer ясно отображается в рамке на каменном носителе в секции предметов; носитель стоит на отдельной фиолетовой платформе.',
    43: 'Яйцо Cleric Beast ясно отображается в рамке на каменном носителе в секции предметов; рядом есть подпись, моб на платформе не создан.',
    44: 'Гробница Одеона видна целиком от основания на собственной синей платформе до шпилей; орнамент, проёмы и текстуры отображаются.',
    45: 'Катафалк виден целиком над собственной платформой; кузов, рама и колёса отображаются, соседние ворота его не перекрывают.',
    46: 'Исходное составное дерево полностью видно на своей синей платформе; ствол до основания и вся крона находятся в кадре.',
    47: 'Дерево RP первого варианта полностью видно на собственной платформе вместе с каменным основанием; соседние дверь и панель не перекрывают его.',
    48: 'Дерево RP третьего варианта полностью видно вместе с каменным основанием на собственной платформе; соседнее дерево расположено на отдельной платформе.'
}


def main():
    capture_path = RUN / 'client-capture-output.json'
    capture = json.loads(capture_path.read_text(encoding='utf8'))
    assert capture['status'] == 'CAPTURE_COMPLETE' and capture['captureComplete']
    assert capture['expectedFrames'] == capture['capturedFrames'] == len(capture['frames']) == 48
    assert capture['productionJarSha256'] == DIGEST
    frames = []
    for index, row in enumerate(capture['frames'], 1):
        if index not in OBSERVATIONS:
            continue
        image = RUN / 'screenshots' / row['fileName']
        assert hashlib.sha256(image.read_bytes()).hexdigest() == row['sha256']
        assert image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
        assert row['renderedCameraVerified']
        assert all(check['status'] == 'PASS' for check in row['actualEntityChecks'])
        assert all(check['status'] == 'PASS' for check in row['actualRootChecks'])
        frames.append({'fileName': row['fileName'], 'key': row['key'], 'sha256': row['sha256'],
                       'status': 'PASS_OBSERVED_PIXELS', 'observation': OBSERVATIONS[index]})
    assert len(frames) == 16
    report = {'schemaVersion': 1, 'status': 'PASS_ASSIGNED_PIXELS',
              'productionJarSha256': DIGEST,
              'captureSha256': hashlib.sha256(capture_path.read_bytes()).hexdigest(),
              'rawGameDirectory': str(RUN), 'reviewer': 'v124_stair_types',
              'reviewMethod': 'All sixteen original unmodified native PNGs opened with view_image; no synthetic screenshots.',
              'reviewedFrames': 16, 'assignedFrameRange': [33, 48], 'rawImagesModified': False,
              'frames': frames, 'physicalMouseKeyboard': 'NOT_RUN'}
    (DOC / 'gallery-native-review-33-48.json').write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({key: value for key, value in report.items() if key in {
        'status', 'productionJarSha256', 'captureSha256', 'reviewedFrames'}}))


if __name__ == '__main__':
    main()
