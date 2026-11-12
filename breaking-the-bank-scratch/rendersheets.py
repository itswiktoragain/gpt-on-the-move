from swfparse import SWF
from swfsvg import Converter
import io,os,sys,time,hashlib,math,traceback,multiprocessing
from PIL import Image
import cairosvg

W,H=480,360;COLS=3;ROWS=2;CELLS=COLS*ROWS
OUTPUT='/mnt/data/bank_port/assets'
os.makedirs(OUTPUT,exist_ok=True)

def process(task):
 idx,svgs=task
 try:
  imgs=[];uniq={}
  for svg in svgs:
   key=hashlib.md5(svg.encode()).digest()
   if key not in uniq:
    b=cairosvg.svg2png(bytestring=svg.encode('utf-8'),output_width=480,output_height=349)
    im=Image.open(io.BytesIO(b)).convert('RGB')
    stage=Image.new('RGB',(480,360))
    stage.paste(im.crop((0,0,480,1)).resize((480,5)),(0,0))
    stage.paste(im,(0,5))
    stage.paste(im.crop((0,348,480,349)).resize((480,6)),(0,354))
    uniq[key]=stage
   imgs.append(uniq[key])
  sheet=Image.new('RGB',(1440,720),(255,255,255))
  for j,img in enumerate(imgs):sheet.paste(img,((j%3)*480,(j//3)*360))
  pal=sheet.quantize(colors=256, method=Image.Quantize.MEDIANCUT,dither=Image.Dither.NONE)
  fname=f'sheet_{idx:04}.png';path=os.path.join(OUTPUT,fname)
  pal.save(path,'PNG',optimize=False,compress_level=6)
  return idx,os.path.getsize(path),len(uniq),None
 except Exception:
  return idx,0,0,traceback.format_exc()

def jobs(converter,number):
 for idx in range(number):
  svgs=[converter.frame_svg(min(idx*6+j,5394)) for j in range(6)]
  yield idx,svgs

if __name__=='__main__':
 s=SWF('/mnt/data/457209_Bank_NG.swf');v=Converter(s)
 if len(sys.argv)>1:
  indices=[int(x) for x in sys.argv[1:]]
  t=time.time()
  for idx in indices:
   out=process((idx,[v.frame_svg(min(idx*6+j,5394)) for j in range(6)]))
   print('preview sheet',out, 'seconds',round(time.time()-t,1),flush=True)
 else:
  total=(5395+CELLS-1)//CELLS;t=time.time();n=0;bytes_=0;uniq=0
  workers=4
  with multiprocessing.Pool(workers) as pool:
   for idx,size,used,error in pool.imap_unordered(process,jobs(v,total),chunksize=1):
    if error:
     print('RENDER ERROR',idx,error,flush=True);raise RuntimeError(error)
    n+=1;bytes_+=size;uniq+=used
    if n%30==0 or n==total:
     print('PROGRESS',n,'/',total, 'MIB',round(bytes_/1048576,1),'unique frames',uniq,'elapsed',round(time.time()-t,1),flush=True)
  print('FINISHED',total,'sheets',round(bytes_/1048576,2),'MiB', 'sec',round(time.time()-t,1))
