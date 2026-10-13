"""Readable approved type/pose compatibility mapping from production metadata."""
import csv,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text(encoding='utf8'))
def main():
    data=read(ROOT/'src/architecture/resources/bloodborne_dw/imported-assembly-v126.json')
    target=ROOT/'docs/v12.6/ID-MIGRATION-RU.tsv';target.parent.mkdir(parents=True,exist_ok=True)
    with target.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f,delimiter='\t');w.writerow(['Старый ID','Исходное состояние','Основной ID','Цельное состояние','Положение части относительно корня','Исходный SourceShift части','Поворот части относительно корня, шаги по45°','Условие замены','Статус предмета'])
        for recipe in data['recipes']:
            for c in recipe['components']:
                w.writerow([c['mainId'],c['pose'],recipe['primaryId'],recipe['installationPose'],json.dumps(c['relativePosition']),json.dumps(c['sourceShift']),
                            c.get('componentYawDelta',0),
                            'Полная точная группа; UUID, монтаж, SourceShift и NBT сохраняются. Неполные сохранённые группы остаются прежними.',
                            'Один предмет цельного типа; старая деталь скрыта' if c['registryId'] in data['hiddenPartRedirects'] else 'Основной предмет цельного типа' if c['mainId']==recipe['primaryId'] else 'Самостоятельный тип сохранён; объединяется только в точной полной группе'])
        w.writerow(['90479','Любое прежнее','90001','Существующая дверь V12','Прежний корень','SourceShift Z +0.375', 'Поворот сохранён',
                    'Замена каждого сохранённого экземпляра с сохранением UUID/NBT; механизм 90001 прежний','90479 скрыта'])
    print(json.dumps({'status':'PASS_READABLE_MAPPING','recipes':len(data['recipes']),'path':str(target)}))
if __name__=='__main__':main()
