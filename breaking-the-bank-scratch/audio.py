from swfparse import SWF
import os,collections,subprocess,tempfile,io,wave,struct,numpy as np,math,time
S=44100
ROOT='/mnt/data/bank_port'
s=SWF('/mnt/data/457209_Bank_NG.swf')

def decode_mp3(data):
 if not data:return np.zeros(0,dtype=np.float32)
 p=subprocess.run(['ffmpeg','-nostdin','-hide_banner','-loglevel','error','-f','mp3','-i','pipe:0','-f','f32le','-ac','1','-ar',str(S),'pipe:1'],input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if p.returncode:print('ffmpeg error',p.stderr[:200])
 return np.frombuffer(p.stdout,dtype='<f4').copy()

streams={}
for fr,ss in s.root.sounds.items():
 for t,raw in ss:
  if t=='stream':streams[fr]=raw
runs=[]
frames=sorted(streams)
start=last=frames[0]
for fr in frames[1:]:
 if fr>last+1:
  runs.append((start,last));start=fr
 last=fr
runs.append((start,last))
print('runs',len(runs),flush=True)
stream_audio=np.zeros(round(s.nframes/S*0)+int((s.nframes+5)*S/s.fps),np.float32)
for i,(start,end) in enumerate(runs):
 data=b''.join(streams[j][4:] for j in range(start,end+1))
 audio=decode_mp3(data)
 samples=sum(int.from_bytes(streams[j][:2],'little') for j in range(start,end+1))
 offset=int(round(start/s.fps*S))
 skip=max(0, int.from_bytes(streams[start][2:4],'little',signed=True))
 if skip<len(audio):audio=audio[skip:]
 n=min(len(audio),len(stream_audio)-offset)
 stream_audio[offset:offset+n]+=audio[:n]
 print(f'run {i+1} frames {start}-{end}, nframes {end-start+1}, audio secs {len(audio)/S:.3f}, expected {(end-start+1)/30:.3f}, samplecount {samples}, injected {n/S:.3f}',flush=True)

for fr,ss in s.root.sounds.items():
 for t,raw in ss:
  if t!='start':continue
  cid=int.from_bytes(raw[:2],'little');fmt,sc,data=s.sounds[cid]
  snd=decode_mp3(data[2:])
  sampleRate=[5512,11025,22050,44100][(fmt>>2)&3]
  skip=int.from_bytes(data[:2],'little',signed=True)
  skip44=int(round(skip*S/sampleRate))
  if skip44>0:snd=snd[skip44:]
  elif skip44<0:snd=np.r_[np.zeros(-skip44,np.float32),snd]
  maxSamples=int(round(sc*S/sampleRate))
  if maxSamples>0:snd=snd[:maxSamples]
  flags=raw[2]
  if flags&1:
   inPt=int.from_bytes(raw[3:7],'little');snd=snd[int(inPt*S/sampleRate):]
  if flags&2:
   outPt=int.from_bytes(raw[7:11],'little');snd=snd[:int(outPt*S/sampleRate)]
  if flags&4:
   loops=int.from_bytes(raw[3:5],'little');snd=np.tile(snd,min(max(1,loops),8))
  if flags&8:
   pos=3
   if flags&1:pos+=4
   if flags&2:pos+=4
   if flags&4:pos+=2
   env_count=raw[pos];pos+=1;env=[]
   for j in range(env_count):
    sample,posL,posR=struct.unpack_from('<IHH',raw,pos);pos+=8
    env.append((sample*S/sampleRate,(posL+posR)/(2*32768)))
   if env:
    xx=np.array([v[0] for v in env]);yy=np.array([v[1] for v in env]);
    snd*=np.interp(np.arange(len(snd)),xx,yy,left=yy[0],right=yy[-1]).astype(np.float32)
  offset=int(round(fr/s.fps*S));n=min(len(snd),len(stream_audio)-offset)
  stream_audio[offset:offset+n]+=snd[:n]
  print('sfx',cid,'@',fr,'duration',round(len(snd)/S,2),'maxamp',round(float(np.max(np.abs(snd))) if len(snd) else 0,2),flush=True)

print('global max',np.max(np.abs(stream_audio)))
scenes=[('Intro',0,153),('Tunnel',161,757),('Explosion',763,1250),('LazerDrill',1251,1640),('WreckingBall',1649,2357),('Teleporter',2358,2734),('Disguise',2735,5394)]
for name,fr0,fr1 in scenes:
 a=int(round(fr0*S/30));b=int(round((fr1+1)*S/30))
 arr=np.copy(stream_audio[a:b]); np.clip(arr,-1,1,out=arr)
 pcm=(arr*32767).astype(np.int16)
 with wave.open(f'{ROOT}/{name}.wav','wb') as wav:
  wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(S);wav.writeframes(pcm.tobytes())
 subprocess.run(['ffmpeg','-nostdin','-y','-hide_banner','-loglevel','error','-i',f'{ROOT}/{name}.wav','-b:a','128k',f'{ROOT}/{name}.mp3'],check=True)
 print('saved',name,'len',len(arr)/S,'size',os.stat(f'{ROOT}/{name}.mp3').st_size,flush=True)
