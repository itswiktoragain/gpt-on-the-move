#!/usr/bin/env python3
"""Windows 8 14th anniversary documentary, original edit using archival images."""
from pathlib import Path
import subprocess, math, json, re, time, hashlib, urllib.parse, asyncio, wave
import requests
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
import edge_tts

ROOT=Path(__file__).resolve().parent
OUT=ROOT/"output"
ASSETS=ROOT/"assets"
WORK=ROOT/"build"
for p in [OUT,ASSETS,WORK]: p.mkdir(parents=True,exist_ok=True)
W,H,FPS=1920,1080,30
BG=(9,18,30)
ACC=(0,172,224)
WHITE=(251,252,254)
GRAY=(175,192,208)
fpath="/usr/share/fonts/truetype/dejavu/"
def font(sz,bold=False):
    return ImageFont.truetype(fpath+("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"),sz)
def run(args):
    print("RUN:", " ".join(map(str,args))[:180],flush=True)
    subprocess.run([str(x) for x in args],check=True,stdout=subprocess.DEVNULL)
def duration(file):
    return float(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",str(file)],text=True).strip())
HEADERS={"User-Agent":"Windows8-Anniversary-Documentary/1.0 (historical video; Wikimedia Commons media metadata)"}
sess=requests.Session(); sess.headers.update(HEADERS)
def commons_file(name):
    clean=name.replace(" ","_")
    md=hashlib.md5(clean.encode("utf-8")).hexdigest()
    return "https://upload.wikimedia.org/wikipedia/commons/"+md[0]+"/"+md[:2]+"/"+urllib.parse.quote(clean,safe="()")
media={
 "launch":("Windows 8 Launch (2).jpg","Dell Inc. / CC BY 2.0","https://commons.wikimedia.org/wiki/File:Windows_8_Launch_(2).jpg"),
 "ballmer":("Windows 8 Launch - Steve Ballmer (2).jpg","Dell Inc. / CC BY 2.0","https://commons.wikimedia.org/wiki/File:Windows_8_Launch_-_Steve_Ballmer_(2).jpg"),
 "surface":("Surface RT.jpg","Decade City / CC BY 2.0","https://commons.wikimedia.org/wiki/File:Surface_RT.jpg"),
 "tiles":("Windows 8 Start screen UI.png","Microsoft / public-domain simple geometry (Wikimedia Commons)","https://commons.wikimedia.org/wiki/File:Windows_8_Start_screen_UI.png"),
 "charms":("Windows 8 tutorial post-install.png","Microsoft / public-domain simple graphics (Wikimedia Commons)","https://commons.wikimedia.org/wiki/File:Windows_8_tutorial_post-install.png"),
 "boot":("Windows 8 booting.png","Wikimedia Commons / see file page","https://commons.wikimedia.org/wiki/File:Windows_8_booting.png"),
 "winver":("Windows 8 winver.png","Wikimedia Commons / see file page","https://commons.wikimedia.org/wiki/File:Windows_8_winver.png"),
 "settings":("Windows 8 Settings.png","Wikimedia Commons / see file page","https://commons.wikimedia.org/wiki/File:Windows_8_Settings.png"),
 "win10":("Win10ScreenshotMenu.png","Microsoft / public-domain simple geometry (Wikimedia Commons)","https://commons.wikimedia.org/wiki/File:Win10ScreenshotMenu.png"),
 "start81":("Start81.png","Wikimedia Commons / see file page","https://commons.wikimedia.org/wiki/File:Start81.png"),
 "bsod":("BSOD Windows 8.png","Wikimedia Commons / see file page","https://commons.wikimedia.org/wiki/File:BSOD_Windows_8.png"),
 "consumer":("https://blogs.microsoft.com/wp-content/uploads/2012/02/3426.Windows-8-Start-Screen.png","Microsoft; used as archival illustration in critical historical commentary","https://blogs.microsoft.com/blog/2012/02/29/the-midweek-download-feb-29th-edition-microsoft-unveils-the-windows-8-consumer-preview-craig-mundie-hosts-techforum-2012-plus-microsoft-research-visual-studio-11-beta-windows-phone/"),
 "taskmgr":("https://images.hanselman.com/blog/Windows-Live-Writer/b08027a5172c_9C1C/image_6.png","Scott Hanselman; historical Windows 8 Task Manager screenshot, used for commentary","https://www.hanselman.com/blog/initial-impressions-of-the-3rd-generation-ivy-bridge-intel-ultrabook-reference-hardware-for-developers-on-windows-8")
}
paths={}
sources=[]
for key,(entry,author,page) in media.items():
    url=entry if entry.startswith("http") else commons_file(entry)
    suffix=".jpg" if entry.lower().endswith((".jpg",".jpeg")) else ".png"
    dest=ASSETS/(key+suffix)
    for candidate in [url,"https://commons.wikimedia.org/wiki/Special:Redirect/file/"+urllib.parse.quote(entry.replace(" ","_"))] if not entry.startswith("http") else [url]:
        try:
            if not dest.exists() or dest.stat().st_size<1000:
                resp=sess.get(candidate,timeout=35)
                resp.raise_for_status()
                dest.write_bytes(resp.content)
            with Image.open(dest) as im: im.verify()
            paths[key]=dest
            print("IMAGE OK",key,dest.stat().st_size,flush=True)
            break
        except Exception as e:
            print("Image retry:",key,str(e)[:110],flush=True)
            if dest.exists(): dest.unlink()
            time.sleep(0.6)
    sources.append({"key":key,"name":entry,"download":url,"source":page,"credit":author,"used":key in paths})
if len(paths)<6 or "consumer" not in paths or "launch" not in paths:
    raise RuntimeError("Archival image download unsuccessful: "+repr(list(paths)))

# Each scene is paced to its own neural narration; these are genuine screenshots/photographs.
SCENES=[
 ("ANNIVERSARY","WINDOWS 8","14 YEARS LATER","OCTOBER 26, 2012 — 2026","consumer",
  "October twenty-sixth, twenty twelve. Microsoft released Windows eight, and changed the look of the personal computer overnight. Today, fourteen years later, we revisit the ambitious ideas, genuine improvements, painful missteps, and surprisingly lasting legacy of the most divisive Windows of its era."),
 ("THE LAUNCH","A RADICAL BET","THE PC WAS CHANGING","OCTOBER 2012","launch",
  "At the launch, tablets were booming and touchscreens seemed destined for everything. Windows eight wasn't a small upgrade to Windows seven. It was a deliberate attempt to unite tablets, laptops, and desktops through one design language. The future was colorful, full-screen, and impossible to ignore."),
 ("THE DESIGN","LIVE TILES","THE START SCREEN","A NEW FIRST IMPRESSION","consumer",
  "Gone was the traditional Start menu. In its place stood a vast Start screen of colorful live tiles, surfacing weather, messages, photos, and news without opening each app. On tablets, it could be refreshing. On a mouse-and-keyboard desktop, the jump away from the familiar workspace was jarring."),
 ("THE HARDWARE","TOUCH FIRST","THE SURFACE EXPERIMENT","A TABLET AND A PC?","surface",
  "Windows eight also arrived alongside Microsoft's first Surface tablet. Edge gestures, a touch keyboard, split-screen app views, and the Windows Store suggested a new kind of computing. The idea mattered: PCs were becoming more flexible. But the desktop experience wasn't yet truly unified with this touch-first world."),
 ("THE GOOD","FASTER STARTUP","PRACTICAL IMPROVEMENTS","LESS WAITING, MORE WORKING","boot",
  "Away from the tiles, Windows eight made important technical progress. Fast Startup combined hibernation technology with shutdown to shorten many boot times. It improved memory management, updated support for modern hardware, and added conveniences that made the system feel snappier on many machines."),
 ("THE GOOD","BETTER TOOLS","TASK MANAGER REBORN","THE CHANGES THAT LASTED","taskmgr",
  "The redesigned Task Manager was an instant standout. Clear performance graphs, startup-app controls, and easier process monitoring made it far more useful. File Explorer also gained a ribbon interface, built-in I S O mounting, and improved file-copy dialogs. These were practical improvements that survived for years."),
 ("THE GOOD","SECURITY + BACKUP","DEFENDER, FILE HISTORY","MORE PROTECTION OUT OF THE BOX","settings",
  "Windows Defender became a built-in antivirus. SmartScreen added warnings about suspicious downloads, while Secure Boot on compatible U E F I hardware improved startup integrity. File History, Storage Spaces, and new refresh-and-reset recovery options gave ordinary users better ways to protect and restore their data."),
 ("THE PROBLEM","WHERE IS START?","DESKTOP SHOCK","THE MOST FAMOUS COMPLAINT","winver",
  "The biggest controversy was simple: the Start button and the classic Start menu were gone. The system opened a full-screen launcher even if you owned a normal desktop PC. Some everyday tasks took extra steps, and suddenly familiar habits from decades of Windows no longer worked."),
 ("THE PROBLEM","HIDDEN CONTROLS","THE CHARMS BAR","GOOD IDEA, POOR DISCOVERABILITY","charms",
  "The Charms bar lived behind an edge gesture or a hot corner. Even finding power controls or settings could become a guessing game. Modern full-screen apps and traditional desktop windows behaved differently, creating the impression that two operating systems had been awkwardly joined together."),
 ("THE PROBLEM","THE STORE GAP","WINDOWS RT CONFUSION","SIMILAR LOOK, DIFFERENT RULES","surface",
  "The app store lacked many programs people expected. And Windows RT, built for ARM devices, could not run traditional x eighty-six desktop applications. To shoppers, that difference was far from obvious: some devices looked like full Windows PCs, but couldn't run the same software."),
 ("THE NUMBERS","200 MILLION","LICENSES IN ~15 MONTHS","BUT THE CONTEXT MATTERS","ballmer",
  "In February twenty fourteen, Microsoft reported roughly two hundred million Windows eight-family licenses sold in about fifteen months. That's a huge number, but Windows seven reached two hundred forty million in its first year. These were license sales, not a count of satisfied active users, and the PC market itself was slowing."),
 ("THE RESPONSE","WINDOWS 8.1","OCTOBER 17, 2013","MICROSOFT STARTED LISTENING","start81",
  "Windows eight point one responded in twenty thirteen. The Start button returned, and users could choose to boot directly to the desktop. Search, customization, and multitasking improved. But pressing Start still opened the full-screen interface. It was a welcome correction, not a complete reversal."),
 ("THE AFTERMATH","WINDOWS 10","THE START MENU RETURNED","2015","win10",
  "Then came Windows ten. Its Start menu returned in a familiar desktop-friendly form, while retaining live tiles and many innovations introduced in Windows eight. Windows ten was, in part, an answer to what eight had taught Microsoft: radical ideas need to respect existing workflows."),
 ("THE LEGACY","A TURNING POINT","IDEAS THAT OUTLIVED IT","2012 — 2026","launch",
  "Windows eight pushed touch, convertible hardware, app distribution, and a radically simpler visual language into the PC conversation. Not everything caught on. But its speed, improved system tools, security features, and willingness to experiment genuinely changed what people expected from Windows."),
 ("THE REALITY","END OF SUPPORT","A PIECE OF HISTORY","NOT A SAFE DAILY DRIVER","bsod",
  "A note before revisiting it yourself: support for original Windows eight ended in January twenty sixteen, and Windows eight point one ended in January twenty twenty-three. These systems are still fascinating in a virtual machine, but running an unsupported Windows installation on the open internet is unsafe."),
 ("THE VERDICT","BOLD. FLAWED.","UNFORGETTABLE.","HAPPY 14TH ANNIVERSARY","consumer",
  "Was Windows eight a failure? It's more complicated than that. It was bold, innovative, occasionally beautiful, often frustrating, and influential long after its most controversial features disappeared. Fourteen years later, it deserves more than a joke about the Start screen. Happy birthday, Windows eight!")
]
# Pick a natural online neural voice; NEVER downgrade to eSpeak.
VOICE="en-US-BrianMultilingualNeural"
async def synthesize_all():
    for i,scene in enumerate(SCENES):
        path=WORK/f"narr_{i:02}.mp3"
        if not path.exists() or path.stat().st_size<2000:
            comm=edge_tts.Communicate(scene[5],VOICE,rate="-3%",pitch="+0Hz")
            await comm.save(str(path))
        if duration(path)<4:
            raise RuntimeError("Neural voice incomplete for "+str(i))
        print("NARRATION",i,round(duration(path),1),flush=True)
asyncio.run(synthesize_all())
timings=[];total=0
for i,s in enumerate(SCENES):
    mp3=WORK/f"narr_{i:02}.mp3"
    seconds=math.ceil((duration(mp3)+0.72)*FPS)/FPS
    timings.append({"index":i,"start":total,"end":total+seconds,"duration":seconds,"title":s[1],"speech":s[5]})
    total+=seconds
print("total duration",total,flush=True)

def cover_image(path,w,h):
    im=Image.open(path).convert("RGB")
    iw,ih=im.size; scale=max(w/iw,h/ih)
    im=im.resize((math.ceil(iw*scale),math.ceil(ih*scale)),Image.Resampling.LANCZOS)
    x=(im.width-w)//2;y=(im.height-h)//2
    return im.crop((x,y,x+w,y+h))

def overlay_gradient(base,dark_left=220):
    grad=Image.new("RGBA",(W,H))
    arr=np.zeros((H,W,4),dtype=np.uint8)
    arr[:,:,0]=6;arr[:,:,1]=13;arr[:,:,2]=24
    xx=np.linspace(0,1,W)
    alpha=(210*np.clip(1-(xx/0.70),0,1)+20).astype("uint8")
    arr[:,:,3]=alpha[None,:]
    grad=Image.fromarray(arr,"RGBA")
    return Image.alpha_composite(base.convert("RGBA"),grad)

def fittext(draw,txt,maxwidth,start=100,minsize=56):
    s=start
    while s>minsize and draw.textbbox((0,0),txt,font=font(s,True))[2]>maxwidth: s-=2
    return font(s,True)
def draw_frame(i,scene):
    chapter,major,sub,strap,key,words=scene
    # alternate documentary full-bleed photos and archival screenshot "gallery" frames.
    photo=key in ["launch","ballmer","surface","consumer","boot","bsod"]
    if key not in paths:
        key="consumer"
    if photo:
        art=cover_image(paths[key],W,H)
        art=ImageEnhance.Contrast(art).enhance(1.07)
        canvas=overlay_gradient(art).convert("RGB")
    else:
        canvas=Image.new("RGB",(W,H),BG)
        # Gallery mode with a large, real, unaltered image on the right
        pic=Image.open(paths[key]).convert("RGB")
        maxw,maxh=1050,800
        ratio=min(maxw/pic.width,maxh/pic.height)
        pic=pic.resize((max(1,int(pic.width*ratio)),max(1,int(pic.height*ratio))),Image.Resampling.LANCZOS)
        xx=1850-pic.width;yy=(H-pic.height)//2
        shadow=Image.new("RGBA",(pic.width+50,pic.height+50),(0,0,0,0))
        sd=ImageDraw.Draw(shadow)
        sd.rounded_rectangle((25,25,pic.width+25,pic.height+25),radius=14,fill=(0,0,0,135))
        shadow=shadow.filter(ImageFilter.GaussianBlur(18))
        canvas.paste(shadow.convert("RGB"),(xx-25,yy-22))
        canvas.paste(pic,(xx,yy))
        d0=ImageDraw.Draw(canvas)
        d0.rectangle((xx-2,yy-2,xx+pic.width+1,yy+pic.height+1),outline=(62,78,94),width=3)
        # blur-free UI screenshots: preserve their original crisp pixels
    d=ImageDraw.Draw(canvas)
    d.rectangle((0,0,17,H),fill=ACC)
    d.text((99,97),"WINDOWS 8  /  14TH ANNIVERSARY",font=font(27,True),fill=(215,230,242))
    d.rectangle((99,151,195,160),fill=ACC)
    # Text matte behind left-aligned title when photo is visually busy
    d.text((96,298),major,font=fittext(d,major,900,104,60),fill=WHITE,stroke_width=1,stroke_fill=(0,12,24))
    d.text((100,427),sub,font=fittext(d,sub,900,47,31),fill=(239,246,250))
    d.rectangle((100,528,176,534),fill=ACC)
    d.text((100,562),strap,font=fittext(d,strap,830,28,21),fill=(222,233,239))
    d.text((100,919),f"{i+1:02d} / {len(SCENES):02d}   •   {chapter}",font=font(23,True),fill=(219,232,242))
    d.text((W-95,922),"2012 — 2026",font=font(22),fill=(220,233,243),anchor="ra")
    # A thin progress strip
    d.rectangle((0,H-9,W,H),fill=(17,48,66))
    d.rectangle((0,H-9,round(W*(i+1)/len(SCENES)),H),fill=ACC)
    out=WORK/f"frame_{i:02}.jpg";canvas.save(out,quality=93,subsampling=0)
    return out

framefiles=[draw_frame(i,s) for i,s in enumerate(SCENES)]
# Create cleanly-cut cinematic shots. Slight pan/zoom adds motion without smearing historic screenshots.
for i,fr in enumerate(framefiles):
    t=timings[i]["duration"]
    clip=WORK/f"v_{i:02}.mp4"
    # Frame-correct audio/video duration; constrain render threads for CI memory.
    vf="zoompan=z='min(zoom+0.00006,1.05)':d=1:x='(iw-iw/zoom)/2':y='(ih-ih/zoom)/2':s=1920x1080:fps=30,format=yuv420p"
    run(["ffmpeg","-y","-loglevel","error","-loop","1","-framerate","30","-i",fr,
         "-t",str(t),"-vf",vf,"-an","-c:v","libx264","-preset","veryfast","-crf","21",
         "-threads","2","-pix_fmt","yuv420p","-movflags","+faststart",clip])
    wav=WORK/f"a_{i:02}.wav"
    run(["ffmpeg","-y","-loglevel","error","-i",WORK/f"narr_{i:02}.mp3",
         "-af","apad","-t",str(t),"-ar","44100","-ac","1","-c:a","pcm_s16le",wav])
    print("RENDER",i+1,"/",len(SCENES),flush=True)
( WORK/"videos.txt").write_text("".join("file '"+str(WORK/f"v_{i:02}.mp4")+"'\n" for i in range(len(SCENES))))
( WORK/"voices.txt").write_text("".join("file '"+str(WORK/f"a_{i:02}.wav")+"'\n" for i in range(len(SCENES))))
run(["ffmpeg","-y","-loglevel","error","-safe","0","-f","concat","-i",WORK/"videos.txt","-c","copy",WORK/"video.mp4"])
run(["ffmpeg","-y","-loglevel","error","-safe","0","-f","concat","-i",WORK/"voices.txt","-c:a","pcm_s16le",WORK/"voice.wav"])

# Original understated electronic bed, ducked far under the voice.
SR=22050
n=int(total*SR)
t=np.arange(n,dtype=np.float32)/SR
music=np.zeros(n,dtype=np.float32)
# Smooth layered chords cycling through simple major-key sequence.
progressions=[(164.81,196.00,246.94),(146.83,185.00,220.00),(130.81,164.81,196.00),(146.83,196.00,246.94)]
for k in range(math.ceil(total/8)):
    a=int(k*8*SR);b=min(n,int((k+8)*8*SR))
    if b<=a:continue
    z=np.arange(b-a,dtype=np.float32)/SR
    env=np.minimum(1,z/1.1)*np.minimum(1,(b-a)/SR-z+0.0001)
    root=progressions[k%4]
    for j,f in enumerate(root):
        music[a:b]+=0.018*env*np.sin(2*np.pi*f*z+(j*1.2))
        music[a:b]+=0.006*env*np.sin(2*np.pi*f*.50*z+(j*0.9))
    # very soft bell on every second beat
    for beat in range(0,8,2):
        lo=int(beat*SR); hi=min(len(z),lo+int(.40*SR))
        if hi>lo:
            q=np.arange(hi-lo,dtype=np.float32)/SR
            music[a+lo:a+hi]+=.016*np.exp(-q*9)*np.sin(2*np.pi*(root[1]*2)*q)
fade=np.minimum(1,t/2)*np.minimum(1,np.maximum(0,total-t)/2)
music*=fade
music=np.clip(music,-.7,.7)
import soundfile as sf
sf.write(WORK/"music.wav",music,SR,subtype="PCM_16")
del music,t
final=OUT/"Windows_8_14th_Anniversary_Professional_2026.mp4"
run(["ffmpeg","-y","-loglevel","error","-i",WORK/"video.mp4","-i",WORK/"voice.wav","-i",WORK/"music.wav",
     "-filter_complex","[1:a]volume=1.05[v];[2:a]volume=0.68[m];[v][m]amix=inputs=2:duration=first:dropout_transition=0,alimiter=limit=0.94[a]",
     "-map","0:v:0","-map","[a]","-c:v","copy","-c:a","aac","-b:a","192k","-ar","44100",
     "-movflags","+faststart","-metadata","title","Windows 8 Turns 14: Bold, Flawed, Unforgettable","-shortest",final])
def fmt(x):
    ms=round(x*1000)
    h,rem=divmod(ms,3600000); m,rem=divmod(rem,60000);s,ms=divmod(rem,1000)
    return f"{h:02}:{m:02}:{s:02},{ms:03}"
captions=[]
for i,scene in enumerate(SCENES):
    words=re.findall(r"\S+",scene[5])
    # Clauses timed proportionally to spoken length
    pieces=[];v=[]
    for word in words:
        v.append(word)
        if len(" ".join(v))>65 and (word.endswith((".",",",";",":")) or len(" ".join(v))>92):
            pieces.append(" ".join(v));v=[]
    if v:pieces.append(" ".join(v))
    start=timings[i]["start"]+0.16
    voiced=duration(WORK/f"narr_{i:02}.mp3")
    total_chars=sum(len(p) for p in pieces)
    for piece in pieces:
        chunk=voiced*len(piece)/total_chars
        captions.append((start,start+chunk,piece))
        start+=chunk
srt="\n\n".join(f"{j}\n{fmt(a)} --> {fmt(b)}\n{text}" for j,(a,b,text) in enumerate(captions,1))+"\n"
(OUT/"Windows_8_14th_Anniversary_Subtitles.srt").write_text(srt,encoding="utf8")
# Export source/rights file and narration
lines=["WINDOWS 8 14TH ANNIVERSARY - ARCHIVAL IMAGE CREDITS","",
       "Scenes: real screenshots, public archival images, and original edit; photos sometimes cropped/color-graded.",
       "No endorsement from Microsoft or original photographers is implied.",""]
for item in sources:
    lines += [f"{item['key']} - {'USED' if item['used'] else 'download failed'}",f"Credit: {item['credit']}",f"Source: {item['source']}",f"Downloaded: {item['download']}",""]
lines+=["Historical source references:","https://news.microsoft.com/source/2012/10/25/windows-8-arrives/",
        "https://news.microsoft.com/source/2013/10/17/windows-8-1-is-available-now-2/",
        "https://arstechnica.com/information-technology/2014/02/windows-8-x-reaches-200-million-licenses-sold/",
        "https://learn.microsoft.com/en-us/lifecycle/faq/windows",
        "https://support.microsoft.com/en-us/windows/windows-8-1-support-ended-on-january-10-2023-3cfd4cde-f611-496a-8057-923fba401e93"]
(OUT/"IMAGE_CREDITS_AND_SOURCES.txt").write_text("\n".join(lines)+"\n",encoding="utf8")
(OUT/"Narration.txt").write_text("\n\n".join(f"{s[1]} — {s[2]}\n{s[5]}" for s in SCENES),encoding="utf8")
(OUT/"timings.json").write_text(json.dumps(timings,indent=2))
# A brighter editorial thumbnail with licensed image
thumb=cover_image(paths["consumer"],1280,720)
dark=Image.new("RGBA",(1280,720),(5,12,20,0))
gr=np.zeros((720,1280,4),dtype=np.uint8)
gr[:,:,0]=5;gr[:,:,1]=12;gr[:,:,2]=22
gr[:,:,3]=(240*np.clip(1-np.linspace(0,1,1280)/.82,0,1)+15).astype("uint8")[None,:]
thumb=Image.alpha_composite(thumb.convert("RGBA"),Image.fromarray(gr,"RGBA")).convert("RGB")
d=ImageDraw.Draw(thumb)
d.rectangle((0,0,12,720),fill=ACC)
d.text((78,94),"FOURTEEN YEARS LATER",font=font(34,True),fill=(227,238,245))
d.text((76,243),"WINDOWS 8",font=font(102,True),fill=WHITE)
d.text((79,383),"BOLD. FLAWED.",font=font(45,True),fill=WHITE)
d.text((79,446),"UNFORGETTABLE.",font=font(45,True),fill=(24,206,238))
d.text((82,602),"2012  —  2026",font=font(31),fill=WHITE)
thumb.save(OUT/"Windows_8_14th_Anniversary_Thumbnail.png",optimize=True)
print("DONE",str(final),final.stat().st_size,"seconds",duration(final),flush=True)
