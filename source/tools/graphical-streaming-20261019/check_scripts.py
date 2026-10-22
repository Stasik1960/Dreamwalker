from common import *
names=['package_final.py','analyze.py','verify.py','run.py']
for name in names:compile((TOOLS/name).read_text(encoding='utf-8'),str(TOOLS/name),'exec')
write(OUT/'script-syntax.json',{'status':'PASS','files':names})
