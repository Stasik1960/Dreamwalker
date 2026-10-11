"""Bind native V12.2 opening/UUID/framebuffer/saving evidence without old edits."""
from pathlib import Path

source=Path(__file__).with_name('collect_flat_gallery_client.py').read_text(encoding='utf8')
source=source.replace("{'v12','new','absent','error'}","{'v12','new','absent','items'}")
source=source.replace('dreamwalker-flat-gallery-native-client-verification-v1','dreamwalker-flat-gallery-v122-native-client-verification')
exec(compile(source,str(Path(__file__).with_name('collect_flat_gallery_client.py')),'exec'),globals())
