from common import *
log_to('map-check-retry')
scope=(OUT/'saved-map').resolve();assert scope.is_relative_to(ROOT.resolve())
for name in ['server','first']:
 old=scope/name;new=scope/(name+'-full-pack-3g-oom')
 assert old.resolve().is_relative_to(scope) and new.parent.resolve()==scope and not new.exists()
 if old.exists():ordinary(old);old.rename(new)
d=read(OUT/'world-audit.json');namespaces=sorted({id.split(':',1)[0] for id in d['blockCounts']});assert set(namespaces)<={'minecraft','bloodborne_dw'}
write(scope/'registry-scope.json',{'status':'PASS','savedBlockNamespaces':namespaces,'validationMods':'Production Dreamwalker BB + its declared Fabric API/GeckoLib dependencies; unrelated RP pack mods are not loaded for this map/physics validation.'});print('PASS',namespaces)
