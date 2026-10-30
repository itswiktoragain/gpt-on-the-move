import com.minecraftjogger.touch.TouchRay;
public class TouchRayTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static boolean near(double a,double b){return Math.abs(a-b)<1e-8;}
 public static final class Vec {public final double x,y,z;public Vec(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}
 public static void main(String[] a){
  double[] c=TouchRay.project(0,0,1,0,0,2,70);check(near(c[2],1),"centre unchanged");
  double[] r=TouchRay.project(0,0,1,1,0,2,70);check(r[0]<0&&r[2]>0,"screen right maps to camera right");
  double[] u=TouchRay.project(0,0,1,0,1,2,70);check(u[1]>0,"screen top aims upwards");
  check(near(r[0]*r[0]+r[1]*r[1]+r[2]*r[2],1),"pick direction remains normalized, no reach extension");
  Vec v=new Vec(0,0,1);TouchRay.rememberFov(70,true);TouchRay.target(1,0,2,true);
  check(TouchRay.adjust(v)==v,"non-picking entity operations unaffected");TouchRay.enter();Vec adjusted=(Vec)TouchRay.adjust(v);check(adjusted.x<0,"pick ray offset active");TouchRay.leave();
  check(TouchRay.adjust(v)==v,"scope exits correctly");TouchRay.clear();TouchRay.enter();check(TouchRay.adjust(v)==v,"cancel restores normal targeting");TouchRay.leave();
  TouchRay.target(Double.NaN,0,2,true);TouchRay.enter();check(TouchRay.adjust(v)==v,"reject invalid coordinates");TouchRay.leave();
  System.out.println("TouchRayTest: all 9 projection/scope checks passed");
 }
}
