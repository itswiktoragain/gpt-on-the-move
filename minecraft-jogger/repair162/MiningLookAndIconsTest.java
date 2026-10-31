import net.kdt.pojavlaunch.jogger.classic.MiningLookPolicy;
import net.kdt.pojavlaunch.jogger.classic.PixelDirectionIcons;
import java.nio.file.*;

public final class MiningLookAndIconsTest {
    private static int n;
    private static void ok(boolean test,String name){if(!test)throw new AssertionError(name);n++;}
    private static String[] rot(String[] a){
        String[] b=new String[a.length];
        for(int y=0;y<a.length;y++){StringBuilder s=new StringBuilder();for(int x=0;x<a.length;x++)s.append(a[a.length-1-x].charAt(y));b[y]=s.toString();}
        return b;
    }
    private static void same(String[] a,String[] b){
        ok(a.length==b.length,"rows");
        for(int i=0;i<a.length;i++)ok(a[i].equals(b[i]),"rotated row "+i);
    }
    public static void main(String[] args)throws Exception {
        ok(!MiningLookPolicy.clearTargetOnMove(true),"mining survives drifting finger");
        ok(MiningLookPolicy.clearTargetOnMove(false),"normal swipe clears target");
        ok(!MiningLookPolicy.applyDelayedClassification(true),"late classification cannot swap held mining after camera move");
        ok(MiningLookPolicy.applyDelayedClassification(false),"stationary hold can classify");
        same(PixelDirectionIcons.RIGHT,rot(PixelDirectionIcons.UP));
        same(PixelDirectionIcons.DOWN,rot(PixelDirectionIcons.RIGHT));
        same(PixelDirectionIcons.LEFT,rot(PixelDirectionIcons.DOWN));
        same(PixelDirectionIcons.UP,rot(PixelDirectionIcons.LEFT));
        String[][] icons={PixelDirectionIcons.UP,PixelDirectionIcons.DOWN,PixelDirectionIcons.LEFT,PixelDirectionIcons.RIGHT,PixelDirectionIcons.UP_LEFT,PixelDirectionIcons.UP_RIGHT};
        for(String[] icon:icons){
            ok(icon.length==7,"height");
            for(String row:icon){ok(row.length()==7,"width");ok(row.matches("[01]{7}"),"binary");}
        }
        for(int y=0;y<7;y++)for(int x=0;x<7;x++)
            ok(PixelDirectionIcons.UP_LEFT[y].charAt(x)==PixelDirectionIcons.UP_RIGHT[y].charAt(6-x),"mirror");
        String src=Files.readString(Path.of(args[0]));
        ok(!src.contains("canvas.rotate(-90)")&&!src.contains("canvas.rotate(90)")&&!src.contains("canvas.rotate(180)"),"no rotated shadows");
        ok(src.contains("if(MiningLookPolicy.clearTargetOnMove(gesture.mining))target(0,0,false);"),"only clear target for swipe");
        ok(src.contains("!MiningLookPolicy.applyDelayedClassification(gesture.moved)"),"drop stale classification");
        ok(src.contains("JoggerTouchBridge.forceAttackRelease()"),"1.6.1 release preserved");
        System.out.println("MiningLookAndIconsTest: "+n+" checks passed");
    }
}
