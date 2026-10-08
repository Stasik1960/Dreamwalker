"""Hand-authored16px hunting tools. Literal grids are the editable pixel source; no AI art."""
import json, struct, zlib, hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources/assets/bloodborne_dw'
PALETTE={'.':(0,0,0,0),'o':(27,30,35,255),'s':(74,84,96,255),'h':(157,169,177,255),
         'g':(145,111,56,255),'b':(216,187,117,255),'r':(98,39,43,255),'l':(155,70,66,255)}
GRIDS={
 'builder_tool':[
 '................','..........ooo...','.........ogbgo..','........og..go..',
 '.......osg..go..','......oshoggo...','.....osh..oo....','....osh.........',
 '...osh.oo.......','..osh.osgo......','.osh.ooogo......','.oso...oo.......',
 '..oro...........','..orlo..........','...oo...........','................'],
 'composite_builder':[
 '................','......oo...oo...','.....osh..ohso..','.....osh..ohso..',
 '.....osh.ohso...','......osohso....','.......osso.....','......ohso......',
 '.....ohso.......','....ohso........','...ohso.........','..oggo..........',
 '.ogbgo..........','.ogrgo..........','..ooo...........','................']}
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
def png(rows,scale=1):
    width=len(rows[0])*scale;height=len(rows)*scale
    raw=b''.join(b'\0'+b''.join(bytes(PALETTE[c])*scale for c in row) for row in rows for _ in range(scale))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
def main():
    source=ROOT/'artwork/v10-builder-tools.pixel-grid.json';source.parent.mkdir(parents=True,exist_ok=True)
    source.write_text(json.dumps({'authorship':'Literal hand-authored pixel grids; no image generation, rescaling, antialiasing or supplied texture edits','grid':16,'palette':PALETTE,'grids':GRIDS},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    rows=[]
    for name,grid in GRIDS.items():
        assert len(grid)==16 and all(len(row)==16 for row in grid)
        dest=RES/f'textures/item/{name}.png';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(png(grid))
        (RES/f'models/item/{name}.json').write_text(json.dumps({'parent':'minecraft:item/handheld','textures':{'layer0':f'bloodborne_dw:item/{name}'}},indent=2)+'\n',encoding='utf8')
        preview=ROOT/f'build/v10-icon-preview/{name}.png';preview.parent.mkdir(parents=True,exist_ok=True);preview.write_bytes(png(grid,16))
        rows.append({'item':f'bloodborne_dw:{name}','grid':[16,16],'source':'artwork/v10-builder-tools.pixel-grid.json','sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'preview':preview.relative_to(ROOT).as_posix()})
    assert rows[0]['sha256']!=rows[1]['sha256']
    (ROOT/'reports/V10_BUILDER_TOOL_ICONS.json').write_text(json.dumps({'status':'HAND_AUTHORED_STATIC_PNG_PASS_CLIENT_PENDING','items':rows,'suppliedArchitectureTexturesChanged':False},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print('PASS two distinct literal16x16 tool icons; preview only nearest-neighbor enlargement.')
if __name__=='__main__':main()
