package net.kdt.pojavlaunch.jogger.classic;

/**
 * Centers the VISIBLE ink plus one-cell down/right drop shadow, rather than
 * centering the nominal bitmap grid. All positions are snapped to device pixels
 * so neither top-row utility icons nor game D-pad icons have half-cell drift.
 */
public final class PixelIconLayout {
    private PixelIconLayout() {}

    public static final class Placement {
        public final int pixel, x, y, minCol, minRow, inkWidth, inkHeight;
        Placement(int pixel,int x,int y,int minCol,int minRow,int inkWidth,int inkHeight) {
            this.pixel=pixel;this.x=x;this.y=y;this.minCol=minCol;this.minRow=minRow;
            this.inkWidth=inkWidth;this.inkHeight=inkHeight;
        }
        public float visualCenterX(){return x+(inkWidth+1)*pixel*.5f;}
        public float visualCenterY(){return y+(inkHeight+1)*pixel*.5f;}
    }

    public static Placement place(String[] icon,float left,float top,float width,float height) {
        if(icon==null||icon.length==0)throw new IllegalArgumentException("empty sprite");
        int cols=0,minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=-1,maxY=-1;
        for(int y=0;y<icon.length;y++) {
            String row=icon[y];
            cols=Math.max(cols,row.length());
            for(int x=0;x<row.length();x++)if(row.charAt(x)=='1') {
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
        }
        int pixel=Math.max(1,(int)Math.floor(Math.min(width/(cols+3f),height/(icon.length+3f))));
        if(maxX<0)return new Placement(pixel,Math.round(left+width*.5f),Math.round(top+height*.5f),0,0,0,0);
        int inkW=maxX-minX+1,inkH=maxY-minY+1;
        // The last +pixel is the dark drop shadow to the right/bottom.
        int x=Math.round(left+width*.5f-(inkW+1)*pixel*.5f);
        int y=Math.round(top+height*.5f-(inkH+1)*pixel*.5f);
        return new Placement(pixel,x,y,minX,minY,inkW,inkH);
    }
}
