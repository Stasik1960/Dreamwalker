from pathlib import Path

path = Path('src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java')
source = path.read_text(encoding='utf-8')
replacements = {
    'try{validatePersistentData(nbt);sourceLegacyPayload=':
        'try{validatePersistentData(nbt);setAlternateVisual(nbt.contains("BbAltVisual",NbtElement.BYTE_TYPE)&&nbt.getBoolean("BbAltVisual"));sourceLegacyPayload=',
    'writeCustomDataToNbt(NbtCompound nbt){try{nbt.putBoolean("Open",isOpen());':
        'writeCustomDataToNbt(NbtCompound nbt){try{if(alternateVisual())nbt.putBoolean("BbAltVisual",true);nbt.putBoolean("Open",isOpen());',
}
for old, new in replacements.items():
    if new in source:
        continue
    assert source.count(old) == 1, old
    source = source.replace(old, new)
path.write_text(source, encoding='utf-8')
print('RP alternate visual persistence is additive; unchanged BASE omits the new optional key.')
