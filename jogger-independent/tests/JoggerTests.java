import com.wiktor.jogger.TouchGesture;
import com.wiktor.jogger.agent.MobileAgent;
import java.util.*;
public class JoggerTests {
 static int assertions;
 static void check(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}
 static void near(double a,double b,String s){check(Math.abs(a-b)<1e-6,s+": "+a+" != "+b);}
 static class Sink implements TouchGesture.Sink {List<String> events=new ArrayList<>();float dx,dy;public void look(float x,float y){events.add("look");dx+=x;dy+=y;}public void mine(boolean d,float x,float y){events.add(d?"mine-down":"mine-up");}public void place(float x,float y){events.add("place");}}
 public static void main(String[]args){
  Sink s=new Sink();TouchGesture g=new TouchGesture(9,s);
  g.down(100,100,0);g.up(101,102,80);check(s.events.equals(Arrays.asList("place")),"short tap places once");
  s.events.clear();g.down(10,10,1000);g.tick(1299);check(s.events.isEmpty(),"no early mine");g.tick(1300);g.tick(1500);g.up(10,10,1700);check(s.events.equals(Arrays.asList("mine-down","mine-up")),"hold mines without place");
  s.events.clear();g.down(10,10,2000);g.move(30,10,2050);g.tick(2400);g.up(31,10,2500);check(!s.events.contains("place")&&!s.events.contains("mine-down"),"drag only looks");check(s.dx==21,"initial movement retained");
  s.events.clear();g.down(20,20,3000);g.cancel();g.up(20,20,3100);check(s.events.isEmpty(),"cancel cannot place");
  g.down(10,10,4000);g.tick(4300);g.cancel();g.cancel();check(s.events.equals(Arrays.asList("mine-down","mine-up")),"cancel releases once");
  s.events.clear();g.down(0,0,5000);g.tick(5300);g.move(30,0,5400);check(s.events.equals(Arrays.asList("mine-down","mine-up","look")),"drag cancels mining");g.up(30,0,5500);check(!s.events.contains("place"),"mine-to-look does not place");
  s.events.clear();g.down(0,0,6000);g.up(0,0,6260);check(s.events.isEmpty(),"between tap and hold has no action");
  double[] a=MobileAgent.rayAngles(0,0,.5,.5,70,16.0/9);near(a[0],0,"centre yaw");near(a[1],0,"centre pitch");
  a=MobileAgent.rayAngles(42,31,.5,.5,70,16.0/9);near(a[0],42,"rotated centre yaw");near(a[1],31,"rotated centre pitch");
  a=MobileAgent.rayAngles(0,0,1,.5,70,16.0/9);check(a[0]>0,"right ray points right");near(a[1],0,"right ray level");
  a=MobileAgent.rayAngles(0,0,.5,0,70,16.0/9);near(a[1],-35,"top ray at half vertical fov");
  a=MobileAgent.rayAngles(359,0,.5,.5,70,1);near(a[0],359,"yaw unwrap");
  for(int yaw=-360;yaw<=360;yaw+=30)for(int pitch=-80;pitch<=80;pitch+=20){a=MobileAgent.rayAngles(yaw,pitch,.5,.5,70,2);near(a[0],yaw,"projection centre yaw");near(a[1],pitch,"projection centre pitch");}
  System.out.println("PASS: "+assertions+" gesture and ray projection assertions");
 }
}
