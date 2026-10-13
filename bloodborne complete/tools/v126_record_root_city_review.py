"""Record root's explicit observations after viewing all13 original native PNGs."""
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    notes=[
      'Фронтальный фасад, окна, кладка, кровля и высокий шпиль текстурированы; нет пурпурного fallback. Дальний край скрывает туман; полный контекст подтверждён дополнительным обзором1.',
      'Боковой вид шпиля, мощёной площадки и среза здания читается; рядом жёлтые платформы. Дальний край в тумане, оценивается повторным обзором2.',
      'Обратный вид двух башен и каменного коридора; дальние поверхности в тумане. Дополнительный обзор3 показывает их без этого ограничения.',
      'Вид противоположного фасада с мощением, парапетами и окнами; текстуры читаются. Дальний туман устранён для оценки повторным обзором4.',
      'Крупный план цветной каменной кладки, колонны и соседнего карниза; текстуры читаются без fallback. Целевой узел встроен в соседние поверхности, его отдельная полная форма этим кадром не оценивается; внешний контекст показан дополнительным фасадом1.',
      'Второй крупный план той же исходной кладки и выступающих деталей, без отсутствующих текстур. Изолированная форма встроенного узла не оценивается; внешний контекст показан дополнительным фасадом2.',
      'Синее остекление, каменная рама, балконная решётка и оригинальная стена V12 отображаются; исходное тесное соседство сохранено, текстурного fallback нет.'
    ]
    second=[
      'При дальности10 весь каменный фасад, высокий шпиль, кровля и боковая линия читаются; дальние поверхности больше не теряются в тумане. Срез зданий и исходные висящие детали не интерпретируются как новая ошибка модели.',
      'Чёткий боковой профиль здания и шпиля, оконные рамы и мощёная полоса; виден близкий ряд жёлтых/зелёных платформ. Пурпурного texture fallback нет.',
      'Противоположный обзор показывает башни, окна, коридор и границу исходного среза; часть пустой серой площадки соответствует выбранному фрагменту, а не претензии на цельное здание.',
      'Обратный фасад, парапеты, каменная площадка и оконные секции видны вместе с соседней галереей; дальний край участка читается.',
      'Наружный ракурс показывает кирпичную стену, площадку, парапет, окна и шпили в исходном соседстве. Это проверка контекста встроенной кладки, не изолированного каждого городского экземпляра.',
      'Второй наружный ракурс ясно показывает рисунок мощения и стены, парапет, декоративные окна и шпили; отсутствие отдельных исходных/обрезанных продолжений не замаскировано новой геометрией.'
    ]
    for which,start,texts,name in [(1,50,notes,'review-050-056.json'),(2,13,second,'review-supplement-013-018.json')]:
        index=read(ROOT/f'build/v126-native-pixel-index-{which}/index.json');rows=index['frames'][start:start+len(texts)];assert len(rows)==len(texts)
        output=[]
        for row,note in zip(rows,texts):
            assert sha(Path(row['path']))==row['sha256'];output.append({k:row[k] for k in ['fileName','key','sha256']}|{'status':'PASS_OBSERVED_PIXELS','observation':note})
        report={'status':'PASS_ASSIGNED_PIXELS','productionJarSha256':index['productionJarSha256'],'captureSha256':index['captureSha256'],
                'reviewer':'root','reviewScope':'Actual original1280x720 PNGs and labelled contact sheets; city context/textures. Every city instance individually viewed: NOT_RUN.','frames':output,'confirmedModelDefects':[]}
        target=ROOT/'docs/v12.6/native'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS_EXPLICIT_ROOT_CITY_OBSERVATIONS','frames':13}))
if __name__=='__main__':main()
