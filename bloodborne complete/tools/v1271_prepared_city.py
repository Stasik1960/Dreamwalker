"""Read-only bounded access to reused full-city decisions for gallery/converter.

Positions are ORIGINAL source XYZ, not translated gallery coordinates. This
reader never opens the original world, recomputes weights or mutates preparation.
"""
import copy, json, sqlite3, zlib
from collections import OrderedDict
from pathlib import Path
import numpy as np

class PreparedCity:
    def __init__(self,path=None):
        path=Path(path) if path else Path(__file__).resolve().parents[1]/'build/v1271-city-preparation/source-plan.sqlite'
        self.connection=sqlite3.connect(f'file:{path.absolute()}?mode=ro',uri=True)
        self.decisions={i:json.loads(data) for i,data in self.connection.execute('SELECT id,data FROM decisions')}
        self.summary=json.loads(self.connection.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0])
        self.sections=OrderedDict()
    def decision_at(self,dimension,x,y,z):
        key=(dimension,x>>4,z>>4,y>>4)
        if key not in self.sections:
            row=self.connection.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',key).fetchone()
            self.sections[key]=None if row is None else np.frombuffer(zlib.decompress(row[0]),dtype='<u2')
            while len(self.sections)>32:self.sections.popitem(last=False)
        index=self.sections[key]
        if index is None:return None
        self.sections.move_to_end(key)
        return copy.deepcopy(self.decisions[int(index[((y&15)<<8)|((z&15)<<4)|(x&15)])])
    def close(self):self.connection.close()
    def __enter__(self):return self
    def __exit__(self,*args):self.close()
