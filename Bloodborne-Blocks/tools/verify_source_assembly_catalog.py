"""Read-only verification of the portable Catalog B evidence package."""
import argparse
import json
from html.parser import HTMLParser
from pathlib import Path

REVIEW_MUTABLE_FIELDS = frozenset(("status", "user_decision", "history"))


def assert_snapshot_registry(snapshot, manifest):
    """Keep snapshot evidence immutable while allowing later batch overlays."""
    snapshot_rows = {row['review_id']: row for row in snapshot['candidates']}
    registry_rows = {row['review_id']: row for row in manifest['candidates']}
    assert set(snapshot_rows) <= set(registry_rows)
    for review_id, frozen in snapshot_rows.items():
        current = registry_rows[review_id]
        frozen_immutable = {key: value for key, value in frozen.items() if key not in REVIEW_MUTABLE_FIELDS}
        current_immutable = {key: value for key, value in current.items() if key not in REVIEW_MUTABLE_FIELDS}
        assert current_immutable == frozen_immutable, review_id
        frozen_history = frozen.get("history", [])
        current_history = current.get("history", [])
        assert current_history[:len(frozen_history)] == frozen_history, review_id
        appended = current_history[len(frozen_history):]
        assert all("preserved_exact_source_signature" in entry and
                   entry["preserved_exact_source_signature"] == frozen.get("exact_source_signature")
                   for entry in appended), review_id
        if any(current.get(field) != frozen.get(field) for field in ("status", "user_decision")):
            assert appended, review_id


def assert_selected_package_decisions(snapshot, manifest, selected_ids, decision_map, batch_id):
    """Validate this package only; unrelated later batches remain valid overlays."""
    snapshot_rows = {row['review_id']: row for row in snapshot['candidates']}
    registry_rows = {row['review_id']: row for row in manifest['candidates']}
    assert set(selected_ids) == set(decision_map)
    for review_id in selected_ids:
        current, frozen, decision = registry_rows[review_id], snapshot_rows[review_id], decision_map[review_id]
        assert current.get("status") == "REVIEWED", review_id
        assert current.get("user_decision") == decision, review_id
        appended = current.get("history", [])[len(frozen.get("history", [])):]
        assert {"event": "manual_review", "batch": batch_id, "decision": decision,
                "preserved_exact_source_signature": frozen.get("exact_source_signature")} in appended, review_id


def verify(root, manifest, *, decision_map):
    root=Path(root).resolve()
    snapshot=json.loads((root/(manifest['batch_id']+'-manifest.json')).read_text(encoding='utf8'))
    selected={r['review_id']:r for r in manifest['candidates'] if r['review_id'] in manifest['batch_review_ids']}
    assert snapshot['coverage']==manifest['coverage']
    assert_snapshot_registry(snapshot, manifest)
    assert_selected_package_decisions(snapshot, manifest, selected, decision_map, manifest['batch_id'])
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
    return {'portable_links':'OK','snapshot_immutable_and_review_overlay_verified':True,'cards':len(selected),
            'all_independent_choices_rendered':alternatives,'package_files':sum(p.is_file() for p in root.rglob('*'))}


if __name__=='__main__':
    project=Path(__file__).resolve().parents[1]
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=project/'docs/manual-review/source-assemblies/batch-02')
    parser.add_argument('--manifest',type=Path,default=project/'docs/manual-source-assemblies.json')
    args=parser.parse_args()
    from source_review_decisions import DECISIONS
    print(json.dumps(verify(args.root,json.loads(args.manifest.read_text(encoding='utf8')),decision_map=DECISIONS),ensure_ascii=False))
