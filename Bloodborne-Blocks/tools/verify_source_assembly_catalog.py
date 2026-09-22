"""Read-only verification of the portable Catalog B evidence package."""
import argparse
import json
from html.parser import HTMLParser
from pathlib import Path


def verify(root, manifest):
    root=Path(root).resolve()
    snapshot=json.loads((root/(manifest['batch_id']+'-manifest.json')).read_text(encoding='utf8'))
    selected={r['review_id']:r for r in manifest['candidates'] if r['review_id'] in manifest['batch_review_ids']}
    assert snapshot['coverage']==manifest['coverage']
    assert all(row==next(r for r in snapshot['candidates'] if r['review_id']==row['review_id']) for row in selected.values())
    class Links(HTMLParser):
        articles=[]
        def handle_starttag(self,tag,attrs):
            for key,value in attrs:
                if tag=='article' and key=='id': self.articles.append(value)
                if key in ('src','href'):
                    target=(root/value).resolve()
                    assert target.is_relative_to(root) and target.is_file(), value
    page=Links(); page.feed((root/'index.html').read_text(encoding='utf8'))
    assert set(page.articles)==set(selected) and len(page.articles)==len(selected)
    alternatives=0
    for row in selected.values():
        for pattern in row['source_patterns']:
            prefix=row['review_id']+'-'+pattern['pattern_id']
            assert (root/'images'/(prefix+'-pattern.png')).is_file()
            for cell in pattern['components']:
                for group,choices in enumerate(cell['model_choices'],1):
                    for option,_apps in enumerate(choices,1):
                        filename=f'{prefix}-cell{cell["number"]}-g{group}-a{option}.png'
                        assert (root/'images'/filename).is_file(),filename
                        alternatives+=1
    return {'portable_links':'OK','snapshot_matches_registry':True,'cards':len(selected),
            'all_independent_choices_rendered':alternatives,'package_files':sum(p.is_file() for p in root.rglob('*'))}


if __name__=='__main__':
    project=Path(__file__).resolve().parents[1]
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=project/'docs/manual-review/source-assemblies/batch-02')
    parser.add_argument('--manifest',type=Path,default=project/'docs/manual-source-assemblies.json')
    args=parser.parse_args()
    print(json.dumps(verify(args.root,json.loads(args.manifest.read_text(encoding='utf8'))),ensure_ascii=False))
