"""Real periodic texture studies, not registered blocks. Opaque, looped, 8-block composition."""
import json,math,zipfile
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFilter
P=Path(__file__).resolve().parents[1];D=P/'utility-evidence/portal-preview';D.mkdir(parents=True,exist_ok=True)
S=256;N=32;rng=np.random.default_rng(20261025);y,x=np.mgrid[0:S,0:S].astype(np.float32);u=x/S*math.tau;v=y/S*math.tau
seeds=rng.random((90,5));palette=Image.new('P',(1,1));colors=[]
for ratio in ((.13,.37),(.20,.50),(.30,.66),(.44,.53),(.59,.49),(.67,.65),(.40,.32),(.73,.30)):
 for g in np.linspace(8,248,32):colors +=[int(g*ratio[0]),int(g),int(g*ratio[1])]
palette.putpalette(colors)
rows=[]
for style,name,title in [(0,'emerald-streams','Изумрудные потоки'),(1,'living-vines','Живые узоры'),(2,'faerie-mist','Туман и светлячки')]:
 frames=[];wall=[]
 for f in range(N):
  t=math.tau*f/N
  a=u+.22*np.sin(2*v+t)+.12*np.cos(3*u-v-t);b=v+.16*np.sin(2*u-t)
  cloud=(.50+.20*np.sin(a+b-t)+.12*np.cos(3*a-2*b+t)+.08*np.sin(5*a+3*b-2*t))
  if style==0:
   ribbon=np.exp(-(np.sin(2*a+.45*np.sin(b-t))*4.5)**2);filament=np.exp(-(np.sin(4*a+.9*np.cos(b+t))*18)**2)
   lum=26+cloud*35+ribbon*(28+20*np.sin(b-t)**2)+filament*20
   red=lum*(.15+.12*ribbon);blue=lum*(.39+.19*ribbon)
  elif style==1:
   stem=np.exp(-(np.sin(3*a+.45*np.sin(2*b))*22)**2);leaf=(np.exp(-(np.sin(3*a+.45*np.sin(2*b)+.23*np.sin(5*b))*7)**2)*np.maximum(0,np.cos(6*b+.6*np.sin(a)))**5)
   curls=np.exp(-(np.sin(2*a+2*b+.7*np.sin(3*b-a))*25)**2)
   lum=22+cloud*30+stem*44+leaf*65+curls*12;red=lum*(.23+.22*leaf);blue=lum*(.35+.17*stem)
  else:
   wisp=np.exp(-(np.sin(a+2*b+.65*np.sin(2*a-b-t))*3.1)**2)
   lum=19+cloud*39+wisp*22;red=lum*.23;blue=lum*(.52+.12*wisp)
  rgb=np.stack([red,lum,blue],axis=2)
  if style==1:
   foliage=Image.new('RGBA',(S,S));fd=ImageDraw.Draw(foliage)
   for lane in range(4):
    def stem(yy):return lane*64+30+13*math.sin(yy/S*math.tau+.22*math.sin(t)+lane)+5*math.sin(yy/S*math.tau*3+t)
    for wrapx in (-S,0,S):
     fd.line([(stem(yy)+wrapx,yy) for yy in range(-2,S+3,2)],fill=(21,92,46,190),width=2)
     for leaf in range(7):
      yy=leaf*S/7+lane*9+3*math.sin(t+leaf);xx=stem(yy)+wrapx;side=-1 if leaf%2 else 1
      for wrapy in (-S,0,S):
       q=yy+wrapy;points=[(xx,q),(xx+side*6,q-15),(xx+side*23,q-19),(xx+side*17,q-3)]
       fd.polygon(points,fill=(35,113+leaf*3,54,210));fd.line([(xx,q),(xx+side*20,q-16)],fill=(88,160,76,230),width=1)
   layer=np.asarray(foliage,dtype=np.float32);alpha=layer[:,:,3:4]/255;rgb=rgb*(1-alpha)+layer[:,:,:3]*alpha
  glow=Image.new('L',(S,S));dots=Image.new('L',(S,S));gd=ImageDraw.Draw(glow);dd=ImageDraw.Draw(dots)
  count=54 if style!=2 else 90
  for q in seeds[:count]:
   px=(q[0]*S+12*np.sin(t+q[2]*math.tau)+6*np.sin(2*t+q[3]*math.tau))%S
   py=(q[1]*S+19*np.cos(t+q[3]*math.tau)+7*np.sin(3*t+q[2]*math.tau))%S
   power=int((100+110*q[4])*(.65+.35*np.sin(t+q[2]*math.tau)**2));radius=1 if q[4]<.82 else 2
   for dx in (-S,0,S):
    for dy in (-S,0,S):
     p=(px+dx,py+dy);gd.ellipse((p[0]-3,p[1]-3,p[0]+3,p[1]+3),fill=power);dd.ellipse((p[0]-radius,p[1]-radius,p[0]+radius,p[1]+radius),fill=power)
  light=np.asarray(glow.filter(ImageFilter.GaussianBlur(2)),dtype=np.float32)*.18+np.asarray(dots,dtype=np.float32)*.70
  rgb += light[:,:,None]*np.array([.67,1,.60],dtype=np.float32)
  im=Image.fromarray(np.clip(rgb,0,255).astype(np.uint8),'RGB');frames.append(im)
  wall.append(im.crop((0,0,S,192)).resize((512,384),Image.Resampling.NEAREST).quantize(palette=palette,dither=Image.Dither.NONE))
 sheet=Image.new('RGB',(S,S*N))
 for n,im in enumerate(frames):sheet.paste(im,(0,n*S))
 sheet.save(D/(name+'-study.png'),optimize=True)
 wall[0].save(D/(name+'.gif'),save_all=True,append_images=wall[1:],duration=100,loop=0,optimize=False,disposal=2)
 contact=Image.new('RGB',(512,512))
 for n,at in enumerate((0,8,16,24)):contact.paste(frames[at],((n%2)*256,(n//2)*256))
 contact.save(D/(name+'-frames.png'))
 rows.append({'id':style+1,'name':name,'title':title,'previewBytes':(D/(name+'.gif')).stat().st_size,'studyPngBytes':(D/(name+'-study.png')).stat().st_size,'frames':N,'wallBlocks':[8,6],'pixelsPerBlock':32,'periodBlocks':8,'opaque':True,'loopSeconds':3.2,'runtimeBlockImplemented':False,'candidateFrameMemoryMiB':S*S*N*4/1048576})
(D/'OPTIONS.json').write_text(json.dumps({'status':'AWAITING_USER_CHOICE','variants':rows,'commonRenderingPlan':'Shared atlas animation, world-position tiles, no per-block entities or ticks. Performance estimates are not Minecraft measurements.'},ensure_ascii=False,indent=2),encoding='utf-8')
with zipfile.ZipFile(D/'portal-animation-options.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in D.iterdir():
  if p.suffix in ('.gif','.png','.json'):z.write(p,p.name)
print('Rendered three seamless animated texture studies',flush=True)
