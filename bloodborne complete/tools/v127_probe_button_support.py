import json,sqlite3,zlib
import numpy as np
from v125_map_index import ROOT,token
def main():
    p=[147,46,-937];db=sqlite3.connect(f'file:{ROOT/"build/v127-full-conversion-first.sqlite"}?mode=ro',uri=True);r=db.execute('SELECT source_palette,source_indices,decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',('minecraft:overworld',p[0]>>4,p[2]>>4,p[1]>>4)).fetchone();i=((p[1]&15)<<8)|((p[2]&15)<<4)|(p[0]&15);source=json.loads(r[0])[int(np.frombuffer(zlib.decompress(r[1]),dtype='<u2')[i])];did=int(np.frombuffer(zlib.decompress(r[2]),dtype='<u2')[i]);decision=json.loads(db.execute('SELECT data FROM decisions WHERE id=?',(did,)).fetchone()[0]);print(token({'belowPosition':p,'source':source,'decision':decision}))
if __name__=='__main__':main()
