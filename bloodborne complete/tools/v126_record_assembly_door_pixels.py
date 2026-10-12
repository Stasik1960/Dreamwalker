"""Record actual observed native frames 28..49; does not modify images/world."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INDEX=ROOT/'build/v126-native-pixel-index-1/index.json'
NOTES={
28:'Цельное каменное окно: видны верхний орнамент, рама и нижняя решётчатая часть; текстуры присутствуют.',
29:'Цельная каменная пилястра: непрерывный высокий профиль, завершение и основание на платформе.',
30:'Цельное узкое арочное окно: видны острое завершение и прозрачный решётчатый проём.',
31:'Высокая декоративная панель стоит цельной на платформе; показана преимущественно ребром, лицевая поверхность ограниченно видна.',
32:'Высокое арочное украшение: цельный контур, розетка и вытянутые нижние стеклянные секции.',
33:'Большое витражное окно: видны розетка, нижние секции и объединённое верхнее завершение.',
34:'90031 CLOSED: деревянные створки заполняют каменный арочный проём; верх конструкции присутствует.',
35:'90031 OPEN: проём свободен, каменная арка неподвижна; одна створка видна под прямым углом, другая заслонена стойкой/показана ребром.',
36:'93160 CLOSED: узкая панель видна ребром в центре; рядом отдельно видна широкая поверхность открытой копии.',
37:'93160 OPEN: широкая текстурированная поверхность панели видна в центре; её ориентация отличается от закрытой копии.',
38:'90266 CLOSED: видны две центральные арочные створки, наружные секции и верхние кованые орнаменты.',
39:'90266 OPEN: центральный проход свободен; наружные секции и верхние орнаменты остались; противоположная створка заслонена.',
40:'93152 CLOSED: нижние деревянные створки закрыты, верхняя застеклённая фрамуга и каменный фронтон присутствуют.',
41:'93152 OPEN: нижний проход свободен; фрамуга и каменное оформление неподвижны; одна створка в ракурсе скрыта стойкой.',
42:'90458 CLOSED: деревянное полотно внутри небольшого каменного арочного обрамления.',
43:'90458 OPEN: полотно повернуто и видно узким боком, арка сохраняется, проход открыт.',
44:'93153 CLOSED: две нижние створки закрыты; боковые деревянные стойки и декоративный верх присутствуют.',
45:'93153 OPEN: нижний проход свободен, верх и стойки сохраняются; видна одна повёрнутая створка, другая заслонена.',
46:'90923 pose1 CLOSED: деревянное полотно в нижней арке; верхнее каменное завершение находится в прямом монтаже.',
47:'90923 pose1 OPEN: нижний проход свободен, полотно видно узким боком, обе каменные арки остаются.',
48:'90923 pose2 CLOSED: цельная высокая дверь с верхней каменной аркой, установленной на один блок в глубину.',
49:'90923 pose2 OPEN: нижний проход свободен, полотно видно узким боком; верхняя арка сохраняет монтаж в глубину.'}
RAW={35,36,37,39,41,43,45,47,49}
SUPPLEMENT={31:'Лицевой ракурс высокой декоративной панели.',35:'Противоположный боковой ракурс обеих открытых створок.',
39:'Противоположный боковой ракурс обеих открытых створок.',41:'Противоположный боковой ракурс обеих открытых створок.',
43:'Боковой ракурс широкой поверхности открытой деревянной створки.',45:'Противоположный боковой ракурс обеих открытых створок.',
47:'Боковой ракурс широкой поверхности открытой створки в прямом монтаже.',49:'Боковой ракурс широкой поверхности открытой створки в глубинном монтаже.'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    data=json.loads(INDEX.read_text(encoding='utf8'));frames=[]
    for entry in data['frames']:
        index=entry['index']
        if index not in NOTES:continue
        assert sha(Path(entry['path']))==entry['sha256']
        group='contact-024-035.png' if index<=35 else 'contact-036-047.png' if index<=47 else 'contact-048-056.png'
        record={**entry,'status':'PASS_OBSERVED_PIXELS','observation':NOTES[index],
                'observedBasis':['Native contact sheet '+group]+(['Native full-resolution source PNG'] if index in RAW else []),
                'missingTextureFallbackObserved':False,'modelDefectConfirmed':False}
        if index in SUPPLEMENT:
            record['visibilityLimitation']=SUPPLEMENT[index]
            record['fullLeafFaceOrPanelSurfaceVisibility']='SUPPLEMENT_REQUESTED_NOT_CLAIMED_FROM_THIS_ANGLE'
        frames.append(record)
    assert len(frames)==22 and {r['index'] for r in frames}==set(range(28,50))
    out={'schema':'dreamwalker-v126-native-pixel-review','status':'PASS_ASSIGNED_PIXELS',
         'productionJarSha256':data['productionJarSha256'],'captureSha256':data['captureSha256'],
         'pixelIndexSha256':sha(INDEX),'assignedIndices':[28,49],'observedFrames':22,
         'reviewScope':'Observed assembled geometry and CLOSED/OPEN visual state; full surfaces hidden by the angle explicitly require supplementary views.',
         'contactSheetsActuallyObserved':['contact-024-035.png','contact-036-047.png','contact-048-056.png'],
         'confirmedModelDefects':[],'supplementRequestedIndices':sorted(SUPPLEMENT),'frames':frames,
         'nativeInteractions':'SEPARATE_NATIVE_GAMETEST_EVIDENCE','worldOrImagesModified':False}
    target=ROOT/'docs/v12.6/native/review-028-049.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps({'status':out['status'],'observedFrames':22,'supplementRequestedIndices':sorted(SUPPLEMENT)}))
if __name__=='__main__':main()
