"""Record the explicit human source-preservation answer, not a world conversion."""
import json,sqlite3
from v125_map_index import ROOT,token
from v127_apply_full_map_poses import write
def main():
    policy={'status':'APPROVED_PENDING_IMPLEMENTATION','sourceStylePolicy':'APPROVED_EXACT_VISIBLE_WITH_ENCLOSED_VANILLA','humanAnswer':'Сохранить вид на карте; каталог не восстанавливать','humanSteering':'В данном случае там где нужно - ставь подходящие блоки из ваниллы.','visibleAuthoredContent':'Existing reserved IDs/hidden exact compatibility poses only; never restore Creative offers','appropriateVanilla':'Conservatively proven enclosed full-unit-cube fillers; exposed custom source surfaces keep exact authored appearance','cells':9481301,'fullWorldConverted':False};write(ROOT/'docs/v12.7/conversion/source-style-policy.json',policy)
    db=sqlite3.connect(ROOT/'build/v127-full-conversion-first.sqlite');summary=json.loads(db.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);summary['sourceStylePolicy']=policy['status'];summary['runtimeExecution']='BLOCKED_UNTIL_APPROVED_SOURCE_STYLE_IMPLEMENTATION_AND_FINAL_PLAN_EXPORT';db.execute("UPDATE metadata SET data=? WHERE key='summary'",(token(summary),));db.commit();db.close();write(ROOT/'docs/v12.7/conversion/full-conversion-plan.json',summary);print(token(policy))
if __name__=='__main__':main()
