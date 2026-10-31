import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import com.minecraftjogger.touch.TouchRay;

/** Regression: tapping/holding never changes camera rotation; legacy rays are scoped. */
public final class LegacyTouchRegression {
    private static int checks;
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
        checks++;
    }
    private static int count(String src,String needle){
        int n=0,p=0;while((p=src.indexOf(needle,p))>=0){n++;p+=needle.length();}return n;
    }
    public static final class Vec {
        public final double x,y,z;
        public Vec(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    public static void main(String[] args)throws Exception {
        check(args.length==1,"overlay path");
        String src=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        check(!src.contains("aimLegacy("),"No legacy camera steering");
        check(!src.contains("rotated?85:0"),"No camera-steering delay");
        check(count(src,"CallbackBridge.mouseX+=")==1,"Camera changes only through drag-to-look");
        check(count(src,"CallbackBridge.sendCursorPos(")==1,"No synthetic cursor warp on tap/hold");
        check(src.contains("if(!gesture.mining)gesture.move(x,y);"),"Finger motion locked when holding to mine");
        check(src.contains("if(gesture.moved&&!gesture.mining)"),"Look stops after hold begins");
        check(count(src,"finishHold(request,0)")==2,"Unknown hold response mines, not places");
        check(src.contains("inputs.set(-3);worldPointer=-1;"),"Releases held mouse on finger-up");
        Properties bad=new Properties();bad.setProperty("adapter.legacy","true");
        TouchRay.fovSeen=false;TouchRay.configure(bad);
        check(!TouchRay.fovSeen,"No provisional FOV without complete legacy mapping");
        Properties p=new Properties();
        p.setProperty("adapter.legacy","true");
        p.setProperty("class.renderer","test.Renderer");
        p.setProperty("class.entity","test.Entity");
        p.setProperty("class.vec",Vec.class.getName());
        p.setProperty("method.pick","pick");
        p.setProperty("method.view","view");
        p.setProperty("field.x","x");p.setProperty("field.y","y");p.setProperty("field.z","z");
        TouchRay.configure(p);
        check(TouchRay.fovSeen,"Provisional 70-degree ray only with complete mapping");
        Vec original=new Vec(0,0,1);
        TouchRay.target(.5,.4,1.6,true);
        check(TouchRay.adjust(original)==original,"Adjust only during game picking");
        TouchRay.enter();
        Object adjusted=TouchRay.adjust(original);
        TouchRay.leave();
        check(adjusted instanceof Vec,"Adjusted ray preserves vector type");
        Vec ray=(Vec)adjusted;
        check(Math.abs(ray.x-original.x)>.01,"Tap changes ray direction");
        check(original.x==0&&original.y==0&&original.z==1,"Never mutate original camera vector");
        check(Math.abs(Math.sqrt(ray.x*ray.x+ray.y*ray.y+ray.z*ray.z)-1)<1e-6,"Unit-length touch ray");
        check(TouchRay.adjust(original)==original,"Adjustment ends after pick");
        TouchRay.clear();TouchRay.enter();
        check(TouchRay.adjust(original)==original,"Inactive touch leaves view untouched");
        TouchRay.leave();
        System.out.println("LegacyTouchRegression PASS: "+checks+" checks");
    }
}
