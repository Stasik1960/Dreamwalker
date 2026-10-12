"""Mark a concrete unresolved source preservation policy; never writes a world."""
import json,sqlite3
from v125_map_index import ROOT,token
from v127_apply_full_map_poses import write
def main():
    path=ROOT/'build/v127-full-conversion-first.sqlite';db=sqlite3.connect(path);row=db.execute("SELECT data FROM metadata WHERE key='summary'").fetchone();summary=json.loads(row[0]);summary['sourceStylePolicy']='PENDING_USER_DECISION';summary['runtimeExecution']='BLOCKED_PENDING_SOURCE_STYLE_POLICY_AND_EXPLICIT_FULL_CONVERSION_COMMAND';summary['pendingSourceStyleCells']=9481300;db.execute("UPDATE metadata SET data=? WHERE key='summary'",(token(summary),));db.commit();db.close();write(ROOT/'docs/v12.7/conversion/full-conversion-plan.json',summary);write(ROOT/'docs/v12.7/conversion/source-style-policy.json',{'status':'PENDING_USER_DECISION','cells':9481300,'question':'Preserve exact visible authored content through hidden compatible states, without restoring Creative items, or remove it?','evidence':'retired-source-vanilla-equivalence.json','exactVanillaKeepCases':0,'fullWorldConverted':False});print(token({'status':'PASS_PENDING_POLICY_GUARD','cells':9481300,'worldWritten':False}))
if __name__=='__main__':main()
