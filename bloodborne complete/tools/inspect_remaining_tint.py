"""Read-only primary local Minecraft bytecode/source-pack tint evidence."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile
from collections import Counter
from io import BytesIO
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/remaining-transfer/tint-primary-evidence'
OUT.mkdir(parents=True, exist_ok=True)
JAR = Path.home() / '.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar'
JAVAP = Path.home() / 'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe'
PACK = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
classes = ['net.minecraft.client.render.block.BlockModelRenderer',
           'net.minecraft.client.color.block.BlockColors',
           'net.minecraft.client.color.item.ItemColors',
           'net.minecraft.client.color.world.BiomeColors',
           'net.minecraft.world.biome.Biome',
           'net.minecraft.world.biome.BiomeEffects',
           'net.minecraft.world.biome.BiomeEffects$GrassColorModifier',
           'net.minecraft.world.BlockRenderView',
           'net.minecraft.client.world.ClientWorld',
           'net.minecraft.world.biome.ColorResolver',
           'net.minecraft.registry.entry.RegistryElementCodec',
           'net.minecraft.client.color.world.GrassColors']
class_report = []
with zipfile.ZipFile(JAR) as jar:
    for name in classes:
        process = subprocess.run([str(JAVAP), '-classpath', str(JAR), '-c', '-p', name], capture_output=True, text=True, check=True)
        target = OUT / (name.rsplit('.', 1)[1] + '.javap.txt')
        target.write_text(process.stdout, encoding='utf-8')
        class_report.append({'class': name, 'bytecodeSha256': hashlib.sha256(jar.read(name.replace('.', '/') + '.class')).hexdigest(), 'javap': str(target)})
with zipfile.ZipFile(PACK) as pack:
    paths = [name for name in pack.namelist() if any(part in name.lower() for part in ['colormap', 'color.properties', 'colors.properties']) and not name.endswith('/')]
    resources = []
    for name in paths:
        data = pack.read(name)
        row = {'source': name, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}
        if name.endswith('.properties'):
            row['text'] = data.decode('utf-8-sig')
        else:
            (OUT / Path(name).name).write_bytes(data)
            image = Image.open(BytesIO(data)).convert('RGBA')
            counts = Counter(image.getdata())
            row.update({'dimensions': list(image.size), 'uniqueRgba': len(counts), 'rgbaHistogramTop10': [{'rgba': list(rgba), 'count': count} for rgba, count in counts.most_common(10)], 'componentMin': [min(rgba[k] for rgba in counts) for k in range(4)], 'componentMax': [max(rgba[k] for rgba in counts) for k in range(4)], 'sample127x127': list(image.getpixel((127, 127)))})
        resources.append(row)
report = {'sourcePackSha256': hashlib.sha256(PACK.read_bytes()).hexdigest(), 'classes': class_report, 'colorResources': resources}
inventory = json.loads((ROOT / 'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8'))
manifest = json.loads((ROOT / 'docs/remaining-transfer/source-candidates.json').read_text(encoding='utf-8'))
inventory_models = inventory['models']
if isinstance(inventory_models, list):
    models = {row.get('id', row.get('model')): row for row in inventory_models}
else:
    models = inventory_models
tinted = []
for object_row in manifest['objects']:
    for variant in object_row['variants']:
        for source in variant.get('sourceModels', []):
            row = models.get(source, {})
            elements = row.get('resolved', {}).get('elements', [])
            indices = sorted({face['tintindex'] for element in elements for face in element.get('faces', {}).values() if 'tintindex' in face and face['tintindex'] >= 0})
            if indices:
                tinted.append({'path': object_row['path'], 'variant': variant['variant'], 'sourceModel': source, 'tintIndices': indices, 'usedBy': row.get('used_by'), 'sourceStateRefs': variant.get('sourceStateRefs', variant.get('sourceRefs', []))})
report['modelRowKeys'] = list(next(iter(models.values())).keys())
report['activeTintedVariants'] = tinted
(OUT / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
