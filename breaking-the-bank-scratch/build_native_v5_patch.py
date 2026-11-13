"""Quality v5: preserve Flash affine transforms and clipDepth in vanilla Scratch."""
from pathlib import Path
import sys
p=Path(sys.argv[1] if len(sys.argv)>1 else Path(__file__).with_name("build_native.py"))
s=p.read_text()

def swap(a,b):
    global s
    assert s.count(a)==1,(a[:110],s.count(a))
    s=s.replace(a,b,1)

swap("self.maxdim=0","self.maxdim=0\n        self.baked_crops=collections.Counter()")
i=s.index("\n\nclass BlockBuilder:")
method=r'''
    def png_world(self,cid,age,M,button_state=1,rgb=None,alpha=1):
        """Rasterize each independent SWF object with its entire affine transform.
        Costume center matches pixel-grid center; Scratch never rotates it again.
        """
        # Fade values retain 1/128 precision. Near-identical frames reuse
        # a single centered costume, avoiding GPU memory exhaustion.
        def qfade(x):
            return round(x*128)/128 if 0<=x<=1 else x
        alpha=qfade(alpha)
        if rgb is not None:
            mul,add=rgb
            rgb=(tuple(qfade(v) for v in mul),tuple(add))
        body,used,localbounds=self.contents(cid,age,button_state)
        if not body or not localbounds:return None
        # Render affine rotation/shear into the costume, but let Scratch
        # handle translations so moving sprites can reuse the same bitmap.
        tx,ty=M[4],M[5]
        L=(M[0],M[1],M[2],M[3],0,0)
        relative=compose_bounds(localbounds,L)
        world=compose_bounds(relative,(1,0,0,1,tx,ty))
        if intersect(world,(-6,-6,556,406)) is None:return None
        excessive=(relative[2]-relative[0]>795 or relative[3]-relative[1]>570)
        view=intersect(relative,(-6-tx,-6-ty,556-tx,406-ty)) if excessive else relative
        if view is None:return None
        bx0,by0,bx1,by1=view
        pad=3
        minx=math.floor(bx0-pad);miny=math.floor(by0-pad)
        maxx=math.ceil(bx1+pad);maxy=math.ceil(by1+pad)
        if maxx<=minx or maxy<=miny:return None
        max_extent=max(maxx-minx,maxy-miny)
        rs=0.95 if max_extent>250 else (1.10 if max_extent>145 else PIXEL_SCALE)
        w=max(1,math.ceil((maxx-minx)*rs));h=max(1,math.ceil((maxy-miny)*rs))
        hsh=hashlib.sha1(body.encode("utf-8")).hexdigest()[:20]
        key=(hsh,tuple(round(t,5) for t in L),button_state,
             minx,miny,maxx,maxy,rs,rgb,round(alpha,5))
        if key in self.render_cache:
            saved=self.render_cache[key]
            return (*saved[:7],saved[7]+tx*STAGE_SCALE,saved[8]-ty*STAGE_SCALE,saved[9])
        defs="".join(self.converter.all_defs[x] for x in sorted(used))
        gradients={}
        for shid in used:
            for ix,fill in enumerate(self.swf.shapes[shid]["fills"],1):
                if fill[0]=="gradient":
                    k=f"g{shid}_{ix}"
                    if k in self.converter.grad_defs:gradients[k]=self.converter.grad_defs[k]
        defs+="".join(gradients.values())
        needed=set(re.findall(r"url\(#(clip_[^)]*)\)",body))
        seen=set()
        while needed:
            k=needed.pop()
            if k in seen or k not in self.mask_defs:continue
            seen.add(k);v=self.mask_defs[k]
            defs+=v
            needed.update(re.findall(r"url\(#(clip_[^)]*)\)",v))
        svg=(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
             f'viewBox="{fmt(minx)} {fmt(miny)} {fmt(maxx-minx)} {fmt(maxy-miny)}">'
             f'<defs>{defs}</defs><g transform="{svg_matrix(L)}">{body}</g></svg>')
        basekey=(hsh,tuple(round(t,5) for t in L),button_state,minx,miny,maxx,maxy,rs)
        raw=self.base_png_cache.get(basekey)
        if raw is None:
            raw=cairosvg.svg2png(bytestring=svg.encode(),output_width=w,output_height=h)
            self.base_png_cache[basekey]=raw
            if len(self.base_png_cache)>30:self.base_png_cache.popitem(last=False)
        else:self.base_png_cache.move_to_end(basekey)
        im=Image.open(io.BytesIO(raw)).convert("RGBA")
        if rgb is not None or alpha!=1:
            import numpy as np
            pixels=np.asarray(im).copy()
            if rgb is not None:
                mul,off=rgb
                if mul!=(1,1,1) or off!=(0,0,0):
                    c=pixels[:,:,:3].astype("float32")
                    pixels[:,:,:3]=np.clip(c*np.asarray(mul,dtype="float32")+
                                              np.asarray(off,dtype="float32"),0,255).astype("uint8")
            if alpha!=1:
                pixels[:,:,3]=np.clip(pixels[:,:,3].astype("float32")*alpha,0,255).astype("uint8")
            im=Image.fromarray(pixels,"RGBA")
        bbox=im.getchannel("A").getbbox()
        if bbox is None:return None
        x0,y0,x1,y1=bbox
        x0=max(0,x0-2);y0=max(0,y0-2);x1=min(w,x1+2);y1=min(h,y1+2)
        im=im.crop((x0,y0,x1,y1))
        min_skin=math.ceil(5/max(STAGE_SCALE/rs,0.001))+2
        if im.width<min_skin or im.height<min_skin:
            W=max(im.width,min_skin);H=max(im.height,min_skin)
            padded=Image.new("RGBA",(W,H),(0,0,0,0))
            px=(W-im.width)//2;py=(H-im.height)//2
            padded.paste(im,(px,py));im=padded
            x0-=px;y0-=py
        out=io.BytesIO();im.save(out,"PNG",compress_level=6,optimize=True)
        aid,filename=self.assets.add(out.getvalue(),"png")
        cx=im.width/2;cy=im.height/2
        worldcx=minx+(x0+cx)/rs;worldcy=miny+(y0+cy)/rs
        posx=(worldcx-275)*STAGE_SCALE;posy=(200-worldcy)*STAGE_SCALE
        size=100*STAGE_SCALE/rs
        result=(aid,filename,cx,cy,im.width,im.height,rs,posx,posy,size)
        self.render_cache[key]=result
        result=(*result[:7],result[7]+tx*STAGE_SCALE,result[8]-ty*STAGE_SCALE,result[9])
        self.render_count+=1;self.svgtotal+=len(svg);self.maxdim=max(self.maxdim,w,h)
        if self.render_count%400==0:
            print("baked",self.render_count,"art assets",flush=True)
        return result
'''
s=s[:i]+"\n"+method+s[i:]

start=s.index("                cid,age,M,alpha,rgb=state",s.index("def sprite_script("))
end=s.index("            for key,val in zip(EVENT_KEYS[1:],value)",start)
s=s[:start]+'''                cid,age,M,alpha,rgb=state
                png=art.png_world(cid,age,M,button_state=1,rgb=rgb,alpha=alpha)
                if png is None:
                    value=(0,0,0,90,100,0,'',0)
                else:
                    coskey=png[:4]
                    if coskey not in costume_ids:
                        costume_ids[coskey]=len(costumes)+1
                        aid,fname,cx,cy,width,height,skin_scale,x,y,size=png
                        costumes.append({'assetId':aid,'md5ext':fname,'name':f'Object {len(costumes)+1}',
                                         'dataFormat':'png','bitmapResolution':1,'rotationCenterX':cx,'rotationCenterY':cy})
                    target=art.button_target(cid)
                    hoverid=0
                    if target:
                        hover=art.png_world(cid,age,M,button_state=2,rgb=rgb,alpha=alpha)
                        if hover:
                            hk=hover[:4]
                            if hk not in costume_ids:
                                costume_ids[hk]=len(costumes)+1
                                aid,fname,cx,cy,width,height,hr,hx,hy,hs=hover
                                costumes.append({'assetId':aid,'md5ext':fname,'name':f'Object {len(costumes)+1}',
                                                 'dataFormat':'png','bitmapResolution':1,'rotationCenterX':cx,'rotationCenterY':cy})
                            hoverid=costume_ids[hk]
                    _,_,_,_,_,_,_,x,y,size=png
                    value=(costume_ids[coskey],round(x,4),round(y,4),90,round(size,5),0,target,hoverid)
'''+s[end:]

needle='''        if place.char in s.sprites:
            tl=s.sprites[place.char]
            if not tl.frames:continue
            i=art.index(place.char,frame-place.start)
            for innerdepth,child in sorted(tl.frames[i].items()):'''
replacement='''        if place.char in s.sprites:
            tl=s.sprites[place.char]
            if not tl.frames:continue
            i=art.index(place.char,frame-place.start)
            if any(p.clip>dep for dep,p in tl.frames[i].items()):
                rgb=(tuple(place.cx[0][:3]),tuple(place.cx[1][:3]))
                groups[(rootdepth,0)]=(place.char,frame-place.start,place.mat,place.cx[0][3],rgb)
                continue
            for innerdepth,child in sorted(tl.frames[i].items()):'''
swap(needle,replacement)

needle='''    direction,scale,residual=split_matrix(M)
    return (hashlib.sha1(body.encode()).hexdigest()[:16],residual,
            round(M[4],2),round(M[5],2),round(direction,5),round(scale,6),round(alpha,5),rgb,cid if cid in art.swf.buttons else 0)'''
replacement='''    return (hashlib.sha1(body.encode()).hexdigest()[:16],tuple(round(v,5) for v in M),
            round(alpha,5),rgb,cid if cid in art.swf.buttons else 0)'''
swap(needle,replacement)

start=s.index("        splitneeded=any(",s.index("def build("))
end=s.index("        for part in range(nparts):",start)
s=s[:start]+"        nparts=1\n"+s[end:]
s=s.replace("'agent':'Breaking the Bank native objects Quality v4 - synchronized transforms, clipping and color (no extensions)'",
            "'agent':'Breaking the Bank native objects Quality v5 - baked affine transforms and exact pivots (no extensions)'")
p.write_text(s)
print("Applied Quality v5 centered-costume / clipping / alpha patches to",p)
