import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'faerie-evidence';r=json.loads((D/'client-1/client-checks.json').read_text());assert r['status']=='PASS';rows=r['performancePeriods'];assert len(rows)==4
groups={}
for animated in (False,True):
 values=[v for v in rows if v['animated']==animated];assert len(values)==2
 fps=sum(v['frames'] for v in values)/sum(v['seconds'] for v in values);individual=[v['fps'] for v in values]
 groups['animated' if animated else 'static']={'fps':fps,'individualFps':individual,'p95FrameMsRange':[min(v['p95FrameMs'] for v in values),max(v['p95FrameMs'] for v in values)],'worstP99FrameMs':max(v['p99FrameMs'] for v in values),'over50ms':sum(v['over50ms'] for v in values),'over100ms':sum(v['over100ms'] for v in values),'heapUsedMiBRange':[min(v['heapUsedMiB'] for v in values),max(v['heapUsedMiB'] for v in values)],'cpuPercentRange':[min(v['processCpuPercent'] for v in values),max(v['processCpuPercent'] for v in values)]}
result={'status':'MEASURED','method':'One JVM, one fixed 512-block wall, same camera/resources, ABBA, 4x20s with first 5s excluded','conditions':{'resolution':[1280,720],'heapGiB':4,'activeProcessorCount':2,'sodium':'0.5.13','indium':'1.0.36','shaders':False,'vsync':False,'fpsSetting':260},'groups':groups,'animationFpsChangePercent':(groups['animated']['fps']/groups['static']['fps']-1)*100,'spriteCount':1,'noPerBlockEntitiesOrTickers':True,'scope':'Incremental animation cost on this hardware/test scene; not city FPS, not proof of zero cost on every PC.'}
(D/'performance.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
lines=['Сравнение анимации, 512 блоков стены, одна JVM (ABBA)','1280×720, Sodium+Indium, без шейдеров, heap 4 ГиБ, CPU cap 2; 60 с полезного замера.']
for key,title in [('static','Статично'),('animated','Анимация')]:
 v=groups[key];lines.append(f"{title}: {v['fps']:.1f} FPS; p95 {v['p95FrameMsRange'][0]:.2f}–{v['p95FrameMsRange'][1]:.2f} мс; пауз >50 мс {v['over50ms']}, >100 мс {v['over100ms']}.")
lines.append(f"Изменение FPS при анимации: {result['animationFpsChangePercent']:+.2f}%. Это замер добавочной анимации, не производительности города.")
lines.append('Первый статичный отрезок медленнее последнего; наблюдался прогрев/разброс JVM. Положительная разница не означает ускорения от анимации. Заметного ухудшения и пауз >50 мс в этом прогоне не обнаружено.')
(D/'PERFORMANCE-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
