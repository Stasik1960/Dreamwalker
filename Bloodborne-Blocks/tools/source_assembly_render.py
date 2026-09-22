"""Offline evidence sheets for bounded source-world assembly candidates.

This is intentionally separate from logical-family Catalog A: a candidate is
drawn from its exact source-cell applications and never claims world-wide
coverage or creates runtime data.
"""
from __future__ import annotations

import html
import json
import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

BACKGROUND = (27, 30, 36)
ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault("BLOODBORNE_VANILLA_JAR", str(Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"))
SOURCE_PACK = ROOT / "reference-inputs/source-resource-pack.zip"


def _deps():
    # Kept lazy: manifest-only/test use does not require the model resolver.
    from source_assembly_visuals import source_polys
    from catalog_geometry import rotation
    import render_modular_preview
    return source_polys, rotation, render_modular_preview


def _font(size):
    path = Path(r"C:\Windows\Fonts\arial.ttf")
    return ImageFont.truetype(str(path), size) if path.is_file() else ImageFont.load_default()


def _render(polys, matrix, size=260):
    _object_polys, _rotation, raster = _deps()
    old = raster.CAM
    old_texture = raster.texture
    from source_assembly_visuals import source_texture
    try:
        raster.CAM = matrix
        raster.texture = source_texture
        return raster.draw_polys(polys, size=size).convert("RGB")
    finally:
        raster.CAM = old
        raster.texture = old_texture


def _polys(apps):
    object_polys, _rotation, _raster = _deps()
    return object_polys(apps)[0]


def _views(polys, size=270):
    _object_polys, rotation, _raster = _deps()
    # Rows are screen x/y/depth.  Top and side are deliberate orthographic
    # evidence, unlike the perspective preview which is only a convenience.
    return [
        ("Перспектива", _render(polys, rotation(0, 20) @ rotation(1, -25), size)),
        ("Сверху (X/Z)", _render(polys, np.array([[1, 0, 0], [0, 0, 1], [0, 1, 0]], float), size)),
        ("Сбоку (Z/Y)", _render(polys, np.array([[0, 0, 1], [0, 1, 0], [1, 0, 0]], float), size)),
    ]


def _perspective(polys,size):
    _source, rotation, _raster = _deps()
    return _render(polys,rotation(0,20) @ rotation(1,-25),size)


def _caption(image, text, number=None):
    canvas = Image.new("RGB", (image.width, image.height + 29), BACKGROUND)
    canvas.paste(image, (0, 0)); draw = ImageDraw.Draw(canvas)
    if number is not None:
        draw.rounded_rectangle((6, image.height + 4, 32, image.height + 26), radius=5, fill=(230, 174, 52))
        draw.text((14, image.height + 6), str(number), font=_font(15), fill=(20, 20, 20))
        draw.text((39, image.height + 7), text, font=_font(13), fill="white")
    else: draw.text((7, image.height + 7), text, font=_font(14), fill="white")
    return canvas


def _grid(cards, columns, title):
    columns = max(1, columns); width = max(card.width for card in cards); height = max(card.height for card in cards)
    result = Image.new("RGB", (width * columns, 34 + height * ((len(cards) + columns - 1) // columns)), BACKGROUND)
    draw = ImageDraw.Draw(result); draw.text((7, 8), title, font=_font(17), fill="white")
    for index, card in enumerate(cards):
        x, y = index % columns * width, 34 + index // columns * height
        result.paste(card, (x + (width - card.width) // 2, y))
    return result


def _context(candidate, path):
    cells_by_relative = {tuple(cell["relative"]): cell for cell in candidate.get("context", [])}
    for component in candidate["components"]:
        cells_by_relative.setdefault(tuple(component["relative"]), {"relative": component["relative"], "source": component["source"]})
    cells = list(cells_by_relative.values())
    components = {tuple(row["relative"]): row["number"] for row in candidate["components"]}
    positions = [tuple(cell["relative"]) for cell in cells] + list(components)
    lo = np.min(np.asarray(positions, int), axis=0); hi = np.max(np.asarray(positions, int), axis=0)
    # Top-down source occupancy.  Stacked cells are denoted with the highest
    # y source label; this remains context, never inferred assembly geometry.
    scale, margin = 58, 42
    image = Image.new("RGB", ((hi[0]-lo[0]+1)*scale + margin*2, (hi[2]-lo[2]+1)*scale + margin*2), BACKGROUND)
    draw = ImageDraw.Draw(image); draw.text((6, 6), "Контекст источника: X/Z, номера — source cells", font=_font(14), fill="white")
    for cell in cells:
        x, _y, z = cell["relative"]; px, py = margin + (x-lo[0])*scale, margin + (hi[2]-z)*scale
        stacked = [(component["number"], component["relative"][1]) for component in candidate["components"] if component["relative"][0] == x and component["relative"][2] == z]
        number = components.get(tuple(cell["relative"])); color = (136, 101, 37) if stacked else (66, 72, 82)
        draw.rectangle((px, py, px+scale-3, py+scale-3), fill=color, outline=(178, 184, 194))
        label = "/".join(f"{n}@{y}" for n, y in stacked) if stacked else cell.get("source", {}).get("id", "?").split(":")[-1][:8]
        draw.text((px+4, py+5), label, font=_font(14 if number else 10), fill="white")
    image.save(path)


def _candidate(candidate, images):
    ident = candidate["review_id"]
    source_apps = [app for component in candidate["components"] for app in component.get("apps", [])]
    assembled = _polys(source_apps)
    approximate = bool(candidate.get("preview_alternatives")) or any("weight" in app or app.get('preview_alternatives', 0) > 1 for app in source_apps) or any(len(g)>1 for p in _patterns(candidate) for c in p['components'] for g in c.get('model_choices',[]))
    primary = _grid([_caption(image, label) for label, image in _views(assembled)], 3,
                    f"{ident} — schematic combination of default source apps" + ("; alternatives shown separately" if approximate else ""))
    primary_path = images / f"{ident}-assembled.png"; primary.save(primary_path)
    cards = []
    for component in sorted(candidate["components"], key=lambda row: row["number"]):
        apps = component.get("apps", [])
        label = component.get("source", {}).get("id", "unknown")
        cards.append(_caption(_perspective(_polys(apps), 210), label, component["number"]))
    exploded_path = images / f"{ident}-exploded.png"
    _grid(cards, min(4, len(cards)), "Разобранные source cells — номера как в решении").save(exploded_path)
    context_path = images / f"{ident}-context.png"; _context(candidate, context_path)
    return {"assembled": str(primary_path), "exploded": str(exploded_path), "context": str(context_path), "approximate": approximate}


def _patterns(candidate):
    """Normalise v1's exemplar into v2's exact pattern list."""
    return candidate.get('source_patterns') or [{
        'pattern_id': 'A', 'components': candidate['components'], 'example': candidate.get('example', {}),
        'similar_count': candidate.get('similar_count', 0), 'rotations': candidate.get('rotations', []),
        'exact_source_signature': 'v1-exemplar',
    }]


def _choice_images(candidate, pattern, images):
    """Render every alternative independently; never form a cartesian product."""
    result = []
    for component in pattern['components']:
        for group_index, choices in enumerate(component.get('model_choices', []), 1):
            for choice_index, apps in enumerate(choices, 1):
                image = _caption(_perspective(_polys(apps), 180),
                                 f'group {group_index}, alt {choice_index}; weights: {[a.get("weight", 1) for a in apps]}', component['number'])
                path = images / f"{candidate['review_id']}-{pattern['pattern_id']}-cell{component['number']}-g{group_index}-a{choice_index}.png"
                image.save(path); result.append((component['number'], group_index, choice_index, path))
    return result


def _pattern_preview(candidate, pattern, images):
    apps = [app for component in pattern['components'] for app in component.get('apps', [])]
    path = images / f"{candidate['review_id']}-{pattern['pattern_id']}-pattern.png"
    _caption(_perspective(_polys(apps), 180), f"Pattern {pattern['pattern_id']} — schematic").save(path)
    return path


def render_catalog(manifest, output: Path):
    """Emit one portable Catalog B folder and return its index path."""
    output = Path(output).resolve(); images = output / "images"; images.mkdir(parents=True, exist_ok=True)
    batch_id = manifest.get('batch_id', 'batch-01')
    selected = set(manifest.get('batch_review_ids', []))
    candidates = [row for row in manifest.get("candidates", []) if row.get('status') != 'VARIANT_OF' and (not selected or row['review_id'] in selected)]
    rendered = {candidate["review_id"]: _candidate(candidate, images) for candidate in candidates}
    # A copied input is part of the audit trail: it captures bounded scope and
    # source checksum without modifying the user-supplied manifest.
    snapshot = manifest if not selected else {**manifest,
        'snapshot_scope':'Selected batch + their aliases only; full inventory: docs/manual-source-assemblies.json',
        'candidates':[r for r in manifest['candidates'] if r['review_id'] in selected or r.get('variant_of') in selected]}
    (output / f"{batch_id}-manifest.json").write_text(json.dumps(snapshot, ensure_ascii=False, indent=2)+"\n", encoding="utf8")
    thumbs = []
    for candidate in candidates:
        strip = Image.open(rendered[candidate["review_id"]]["assembled"]).convert("RGB")
        im = strip.crop((0, 34, 270, 304)).resize((240, 240))
        title = candidate['hypothesis'][:30] + ('…' if len(candidate['hypothesis']) > 30 else '')
        short_title = f"{candidate['review_id']} · {len(candidate['components'])} source cells"
        thumbs.append(_caption(im, short_title))
    _grid(thumbs, 4, "Catalog B — bounded source assemblies").save(output / f"{batch_id}-contact.png")
    cards = []
    for candidate in candidates:
        rid, paths = candidate["review_id"], rendered[candidate["review_id"]]
        pictures = " ".join(f'<a href="{html.escape(Path(p).relative_to(output).as_posix())}"><img src="{html.escape(Path(p).relative_to(output).as_posix())}" alt="{rid}"></a>' for kind, p in paths.items() if kind != 'approximate')
        answer = f"{rid} OBJECT: 1+2; 3=CONTEXT / SPLIT:1+2 /3+4 / CONNECTED / NOT_OBJECT / NEEDS_REVIEW"
        component_rows = ''.join(f"<tr><td>{c['number']}</td><td>{html.escape(str(c['relative']))}</td><td>{html.escape(c['source']['id'])} {html.escape(str(c['source'].get('properties', {})))}</td><td>{html.escape(str(c.get('apps', [])))}</td><td>{len(c.get('model_choices', []))} independent choice groups</td></tr>" for c in candidate['components'])
        context_rows = ''.join(f"<tr><td>{html.escape(str(c['relative']))}</td><td>{html.escape(c['source']['id'])} {html.escape(str(c['source'].get('properties', {})))}</td></tr>" for c in candidate.get('context', []))
        example = candidate.get('example', {})
        facts = f"Пример: {html.escape(str(example.get('dimension')))} {html.escape(str(example.get('anchor')))}; rotations: {html.escape(str(candidate.get('rotations', [])))}"
        approx = '<p><b>Схема default apps, не утверждение точной resolved-комбинации.</b> Все варианты показаны отдельно; RNG не используется.</p>' if paths['approximate'] else '<p>Схема source apps для визуальной проверки.</p>'
        pattern_html=[]
        for pattern in _patterns(candidate):
            preview=_pattern_preview(candidate,pattern,images).relative_to(output).as_posix()
            rows=''.join(f"<tr><td>{c['number']}</td><td>{html.escape(str(c['relative']))}</td><td>{html.escape(c['source']['id'])} {html.escape(str(c['source'].get('properties', {})))}</td><td>{html.escape(str(c.get('apps', [])))}</td></tr>" for c in pattern['components'])
            flags = pattern.get('boundary_flags', candidate.get('boundary_flags', [])); flag_text = html.escape(', '.join(flags) if isinstance(flags, list) else str(flags))
            warning = '<b class="warning">POSSIBLY_INCOMPLETE</b>' if 'POSSIBLY_INCOMPLETE' in flags else ''
            pattern_html.append(f"<section><h3>Exact pattern {html.escape(pattern['pattern_id'])} {warning}</h3><img src='{preview}'><p>signature: {html.escape(str(pattern.get('exact_source_signature')))}; example: {html.escape(str(pattern.get('example')))}; count: {pattern.get('similar_count')}; rotations: {html.escape(str(pattern.get('rotations')))}; boundary flags: {flag_text}</p><table><tr><th>№</th><th>relative</th><th>source/properties</th><th>apps</th></tr>{rows}</table></section>")
        choices=[choice for pattern in _patterns(candidate) for choice in _choice_images(candidate, pattern, images)]
        choice_html=''.join(f"<figure style='display:inline-block'><img style='max-width:180px' src='{path.relative_to(output).as_posix()}' alt='cell {number} group {group} alternative {alt}'><figcaption>{html.escape(path.stem)}</figcaption></figure>" for number,group,alt,path in choices)
        aliases=[row['review_id'] for row in manifest.get('candidates',[]) if row.get('variant_of') == rid]
        alias_html=f"<p>Aliases / VARIANT_OF: {html.escape(', '.join(aliases) or '—')}</p>"
        cards.append(f"<article id='{rid}'><h2>{rid} · {html.escape(candidate['hypothesis'])}</h2><p>{html.escape(candidate.get('count_scope',''))}; coverage: {html.escape(str(manifest.get('coverage', {})))}; {facts}</p>{alias_html}{approx}{pictures}<h3>Exemplar source cells</h3><table><tr><th>№</th><th>relative XYZ</th><th>source/properties</th><th>apps/transforms</th><th>choice groups</th></tr>{component_rows}</table><h3>All independent alternatives (not cartesian assembly)</h3>{choice_html}<h3>Full context source cells</h3><table><tr><th>relative XYZ</th><th>source/properties</th></tr>{context_rows}</table>{''.join(pattern_html)}<pre>{html.escape(answer)}</pre></article>")
    scope = html.escape(json.dumps({"source": manifest.get("source"), "scan_scope": manifest.get("scan_scope")}, ensure_ascii=False))
    (output / "index.html").write_text(f"<!doctype html><meta charset='utf-8'><title>Catalog B</title><style>body{{background:#1b1e24;color:#eee;font:16px sans-serif;margin:20px}}article{{border-top:1px solid #555;padding:15px 0}}img{{max-width:32%;vertical-align:top}}table,td,th{{border:1px solid #667;border-collapse:collapse;padding:4px}}.warning{{color:#ffd35a;background:#5e3c16;padding:3px}}pre{{white-space:pre-wrap;background:#292d35;padding:10px}}</style><h1>Catalog B — source assemblies</h1><p>{scope}</p>{''.join(cards)}", encoding="utf8")
    return output / "index.html"
