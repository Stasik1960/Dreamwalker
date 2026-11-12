"""Print exact saved mod blocks from Bloodborne-block-coordinates.sqlite as JSON Lines."""
import argparse
import json
from pathlib import Path
import sqlite3
import struct
import zlib


def palette_index(packed, size, index):
    if size==1:
        return 0
    bits=max(4,(size-1).bit_length())
    width=64//bits
    word,=struct.unpack_from('>Q',packed,8*(index//width))
    return (word>>(bits*(index%width)))&((1<<bits)-1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('db', type=Path)
    parser.add_argument('--label', default='pre_conversion')
    parser.add_argument('--dimension', default='minecraft:overworld')
    parser.add_argument('--limit', type=int)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--at', nargs=3, type=int, metavar=('X','Y','Z'))
    mode.add_argument('--state', help='Exact registry ID, with optional complete [properties]')
    mode.add_argument('--bush-at', nargs=3, type=int, metavar=('X','Y','Z'),
                      help='Find historical bush roots that occupied the given cell')
    args = parser.parse_args()
    with sqlite3.connect(args.db.resolve().as_uri()+'?mode=ro', uri=True) as db:
        if args.bush_at is not None:
            if args.label=='bush_restoration':
                rows=db.execute('SELECT x,y,z,anchor_x,anchor_y,anchor_z,state,status,reason '
                                'FROM bush_release_anchors WHERE dimension=? AND '
                                '((x,y,z)=(?,?,?) OR (anchor_x,anchor_y,anchor_z)=(?,?,?))',
                                (args.dimension,*args.bush_at,*args.bush_at))
                for x,y,z,ax,ay,az,state,status,reason in rows:
                    print(json.dumps(dict(x=x,y=y,z=z,anchor=[ax,ay,az],state=state,status=status,reason=reason)))
                return
            rows=db.execute('SELECT r.x,r.y,r.z,r.state,r.status,r.reason FROM bush_cells c '
                            'JOIN bush_roots r ON r.x=c.root_x AND r.y=c.root_y AND r.z=c.root_z '
                            'WHERE c.x=? AND c.y=? AND c.z=?',args.bush_at)
            for x,y,z,state,status,reason in rows:
                print(json.dumps(dict(x=x,y=y,z=z,state=state,status=status,reason=reason)))
            return
        parameters = (args.label,args.dimension)
        sql = ('SELECT cx,sy,cz,palette,packed,visuals FROM sections '
               'WHERE label=? AND dimension=?')
        if args.at is not None:
            x,y,z = args.at
            rows = db.execute(sql+' AND cx=? AND cz=? AND sy=?', (*parameters,x//16,z//16,y//16))
        elif '[' in args.state:
            rows = db.execute(sql+' AND id IN (SELECT section_id FROM section_states WHERE state=?)',
                              (*parameters,args.state))
        else:
            rows = db.execute(sql+' AND id IN (SELECT section_id FROM section_states '
                              'WHERE state=? OR (state>=? AND state<?))',
                              (*parameters,args.state,args.state+'[',args.state+'\\'))
        count=0
        for cx,sy,cz,raw_palette,packed,visuals in rows:
            palette = json.loads(raw_palette)
            packed=zlib.decompress(packed)
            overlay = json.loads(zlib.decompress(visuals)) if visuals else {}
            tests = ([(x&15)+((z&15)<<4)+((y&15)<<8)] if args.at is not None else range(4096))
            for index in tests:
                state=palette[palette_index(packed,len(palette),index)]
                if state is None or (args.state and not (state == args.state or state.startswith(args.state+'['))):
                    continue
                print(json.dumps({'x': cx*16+(index&15), 'y': sy*16+(index>>8),
                                  'z': cz*16+((index>>4)&15), 'state': state,
                                  'visual_id': overlay.get(str(index))}, ensure_ascii=False))
                count+=1
                if args.limit is not None and count>=args.limit:
                    return


if __name__ == '__main__':
    main()
