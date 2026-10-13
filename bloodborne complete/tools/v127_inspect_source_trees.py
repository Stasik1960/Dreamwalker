import sqlite3,json
from v125_map_index import ROOT,CACHE,dump
def main():
    con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True)
    roots=con.execute("select dimension,x,y,z,a.apps from cells c join applications a on c.applications=a.id where a.apps like '%block/white_wool%' limit 4").fetchall();result=[]
    for dimension,x,y,z,apps in roots:
        rows=con.execute("select x,y,z,a.apps from cells c join applications a on c.applications=a.id where dimension=? and x between ? and ? and y between ? and ? and z between ? and ? and (a.apps like '%_log%' or a.apps like '%_wool%') order by y,z,x",(dimension,x-8,x+8,y-2,y+20,z-8,z+8)).fetchall()
        result.append({'root':[x,y,z],'dimension':dimension,'applications':json.loads(apps),'neighbors':[{'position':[a,b,c],'applications':json.loads(d)} for a,b,c,d in rows]})
    dump(ROOT/'docs/v12.7/conversion/source-tree-inspection.json',{'status':'READONLY_ADJACENCY_EVIDENCE','rows':result});print(json.dumps(result[:1]))
if __name__=='__main__':main()
