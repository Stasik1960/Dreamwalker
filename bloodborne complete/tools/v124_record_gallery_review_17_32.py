"""Transcribe completed manual inspection of native gallery frames17–32."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'docs/v12.4-dedup'
RUN=ROOT/'build/runtime-client-v124-flatgallery-client-final-2'
OBSERVATIONS={
17:'Прямая серая ступень с выраженными вертикальными полосами видна на поверхности зелёной платформы; подпись90523 соответствует экспонату.',
18:'Серая двухуровневая ступень с каменным орнаментом полностью видна над зелёной поверхностью, без пропавших граней; рядом подпись90550.',
19:'Красноватая мангровая ступень с горизонтальным древесным рисунком опирается на зелёную платформу; модель и подпись90686 видимы.',
20:'Коричневая каменная ступень имеет цельные верх, переднюю и боковую поверхности; находится на своей зелёной площадке с подписью90692.',
21:'Ступень из тёплых коричневых кирпичей с крупными швами стоит на зелёной площадке; подпись90701 читается рядом.',
22:'Серая ступень с тесаной каменной отделкой и обрамлением видна целиком на зелёной платформе; подпись90760 совпадает.',
23:'Тёмная зелёно-серая ступень с крупными каменными швами имеет видимые цельные грани, стоит на зелёной платформе; подпись90804.',
24:'Серая ступень с зеленоватыми полосами швов видна на зелёной площадке; контур прямой лестницы и подпись93004 соответствуют экспонату.',
25:'Светлая почти гладкая каменная ступень полностью видна и касается зелёной поверхности; подпись93028 находится справа, отсутствующих граней не видно.',
26:'Светлая ступень из широких каменных блоков с тёмной рамкой швов видна на зелёной площадке; подпись93081 соответствует.',
27:'Серая ступень с мелкозернистой поверхностью видна со стороны верха и передней/боковой граней, находится на зелёной площадке; подпись93094.',
28:'Прямая серая ступень из глубинного сланца видна целиком на зелёной площадке; подпись90423 соответствует, поверхность без видимых отверстий.',
29:'Приставная каменная лестница показана как низкая открытая рамная секция с двумя стойками, доходящими до зелёной платформы; подпись90586. Возможность подъёма этим кадром не проверяется.',
30:'Коричневая деревянная ступень стоит на отдельной жёлтой площадке раздела отсутствующих на карте объектов; подпись90862 и отметка «Нет на исходной» видимы.',
31:'Исходная художественная ограда90002 показана отдельной тёмной каменной стойкой на синей платформе V12; материал и подпись видимы. Соединение с соседями этим кадром не проверяется.',
32:'Исходный материал ограды90011 виден отдельной каменной стойкой на синей платформе V12 с собственной подписью; исходная станция сохранена, не обозначена новым типом.',
}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    path=RUN/'client-capture-output.json';capture=json.loads(path.read_text(encoding='utf-8-sig'))
    assert capture['status']=='CAPTURE_COMPLETE' and capture['capturedFrames']==capture['expectedFrames']==48
    audit=json.loads((D/'resource-audit.json').read_text(encoding='utf-8'))
    assert audit['status']=='PASS' and audit['productionJarSha256']==capture['productionJarSha256']
    frames=[]
    for index,frame in enumerate(capture['frames'],1):
        if index not in OBSERVATIONS:continue
        file=RUN/'screenshots'/frame['fileName']
        assert sha(file)==frame['sha256'] and frame['status']=='PASS' and frame['renderedCameraVerified']
        assert frame['width']==1280 and frame['height']==720
        assert all(r['status']=='PASS' for r in frame['actualRootChecks'])
        frames.append({'fileName':frame['fileName'],'key':frame['key'],'sha256':frame['sha256'],
                       'status':'PASS_OBSERVED_PIXELS','observation':OBSERVATIONS[index]})
    assert len(frames)==16
    report={'status':'PASS_ASSIGNED_PIXELS','productionJarSha256':capture['productionJarSha256'],
      'captureSha256':sha(path),'rawCaptureDirectory':str(RUN),'assignedFrames':'17–32',
      'method':'Manual view_image inspection of every original full native Minecraft PNG; no edited images or substitute renderings.',
      'unverifiedByPixels':'Interaction, climbing, physical collision and neighbor updates are checked by separate native tests, not inferred from screenshots.',
      'visibleFailures':[],'frames':frames}
    (D/'gallery-native-review-17-32.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
