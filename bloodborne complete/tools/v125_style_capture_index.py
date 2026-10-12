"""Bind every working primary to its original native installed-model PNG."""
from collections import defaultdict
import json
from v125_style_inventory import ROOT,OUT,read

def main():
    catalogue=read(OUT/'style-inventory.json')['rows']
    wanted={r['id'] for r in catalogue}
    capture=read(ROOT/'build/runtime-client-v124-comparison-final-2/v124-client-comparison-output.json')
    index={};pages=defaultdict(list)
    for frame in capture['frames']:
        for position,row in enumerate(frame['pairs']):
            if row['oldId'] in wanted and row['oldVariant']==0 and row.get('kind') in (None,'SOURCE_CATALOGUE_REVIEW'):
                if row['oldId'] in index:continue
                bound={'fileName':frame['fileName'],'sha256':frame['sha256'],'row':position//4+1,'column':position%4+1,
                  'productionJarSha256':capture['productionJarSha256'],'oldVariant':0,'pair':row}
                index[row['oldId']]=bound
                pages[frame['fileName']].append(row['oldId'])
    assert set(index)==wanted,(len(index),wanted-set(index))
    (OUT/'style-native-index.json').write_text(json.dumps({'scope':'ALL572_WORKING_NEW_DEFAULTS','productionJarSha256':capture['productionJarSha256'],
      'capturePath':str(ROOT/'build/runtime-client-v124-comparison-final-2/v124-client-comparison-output.json'),
      'rows':index},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    names={r['id']:r['name'] for r in catalogue}
    (OUT/'style-native-pages.txt').write_text('\n'.join(f+' '+', '.join(i+' '+names[i] for i in ids) for f,ids in pages.items())+'\n',encoding='utf-8')
if __name__=='__main__':main()
