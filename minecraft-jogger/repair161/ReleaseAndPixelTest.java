import net.kdt.pojavlaunch.jogger.MouseAckPolicy;
import net.kdt.pojavlaunch.jogger.classic.ClassicLogic;
import net.kdt.pojavlaunch.jogger.classic.PixelDiagonalIcons;
import java.util.*;

public final class ReleaseAndPixelTest {
    private static int checks;
    private static void ok(boolean good, String msg) {
        if (!good) throw new AssertionError(msg);
        checks++;
    }
    static final class Events implements ClassicLogic.Output {
        final List<String> sent=new ArrayList<>();
        public void key(int key, boolean down){sent.add("key"+key+":"+down);}
        public void mouse(int button, boolean down){sent.add("mouse"+button+":"+down);}
    }
    public static void main(String[] args) {
        ok(MouseAckPolicy.shouldForward(0,true,true),"Current mining DOWN allowed");
        ok(MouseAckPolicy.shouldForward(0,false,false),"Current mining UP allowed");
        ok(!MouseAckPolicy.shouldForward(0,true,false),"Late DOWN cannot restart stopped mining");
        ok(!MouseAckPolicy.shouldForward(0,false,true),"Late UP cannot interrupt a new hold");
        ok(MouseAckPolicy.shouldForward(1,true,false),"Unrelated item button unchanged");
        Events e=new Events();
        ClassicLogic.Inputs inputs=new ClassicLogic.Inputs(e);
        inputs.set(-3,-1);
        ok(inputs.held(-1),"Hold owns attack key");
        inputs.set(-3);
        ok(!inputs.held(-1),"Lift removes attack ownership");
        ok(e.sent.equals(Arrays.asList("mouse0:true","mouse0:false")),"Exact press and release events");
        verifyIcons();
        System.out.println("ReleaseAndPixelTest: "+checks+" checks passed");
    }
    private static void verifyIcons() {
        String[] left=PixelDiagonalIcons.UP_LEFT, right=PixelDiagonalIcons.UP_RIGHT;
        ok(left.length==9&&right.length==9,"9x9 pixel icons");
        int leftFilled=0,rightFilled=0;
        for(int y=0;y<9;y++){
            ok(left[y].length()==9&&right[y].length()==9,"All rows square");
            for(int x=0;x<9;x++){
                char a=left[y].charAt(x),b=right[y].charAt(8-x);
                ok(a=='0'||a=='1',"Only square pixel cells");
                ok(a==b,"Right arrow is an unrotated mirror");
                if(a=='1')leftFilled++;
                if(right[y].charAt(x)=='1')rightFilled++;
            }
        }
        ok(leftFilled==rightFilled&&leftFilled>15,"Mirrored arrows have visible solid shapes");
        ok(left[0].charAt(0)=='1'&&left[8].charAt(8)=='1',"UL points NW with stepped shaft");
        ok(right[0].charAt(8)=='1'&&right[8].charAt(0)=='1',"UR points NE with stepped shaft");
    }
}
