"""Extract the finite material table from the already verified source audit."""
import hashlib,json
from pathlib import Path
P=Path(__file__).absolute().parents[2];R=P.parent/'research/city-streaming-and-materials-20261018'
source=R/'static-state-material-mapping.json';doc=json.loads(source.read_text(encoding='utf8'))
catalog=P/'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json'
out=Path(__file__).absolute().parent/'static-pose-materials.json';assert not out.exists()
out.write_text(json.dumps({'catalogSha256':hashlib.sha256(catalog.read_bytes()).hexdigest(),'sourceAuditSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'scope':'Finite source art and textures; no world positions or owner UUIDs','poses':doc['poses']},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
