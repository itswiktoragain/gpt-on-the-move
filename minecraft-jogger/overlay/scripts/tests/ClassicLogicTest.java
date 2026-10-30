import net.kdt.pojavlaunch.jogger.classic.ClassicLogic;
import java.util.*;
public class ClassicLogicTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        ClassicLogic.Inputs keys = new ClassicLogic.Inputs(new ClassicLogic.Output() {
            public void key(int key, boolean down) { events.add(key + ":" + down); }
            public void mouse(int key, boolean down) { events.add("M" + key + ":" + down); }
        });
        keys.set(1,87); keys.set(2,87,65); keys.set(1);
        check(keys.held(87), "another finger keeps forward held");
        check(Collections.frequency(events,"87:true")==1,"one down per key");
        keys.set(2); check(!keys.held(87),"final owner releases");
        keys.set(3,-1); keys.set(4,-1); keys.set(3);
        check(keys.held(-1),"screen and button may both mine"); keys.clear();
        check(events.contains("M0:false"),"clear releases mining");
        ClassicLogic.Gesture g = new ClassicLogic.Gesture();
        g.begin(10,10,1000,8); check(g.end(1100,280,false),"quick tap places");
        g.begin(10,10,1000,8); g.move(30,10); g.move(10,10); check(!g.end(1100,280,false),"drag-return never places");
        g.begin(10,10,1000,8); check(!g.hold(1200,280),"not early mining"); check(g.hold(1280,280),"hold mines"); check(!g.end(1300,280,false),"mine release cannot place");
        g.begin(10,10,1000,8); check(!g.end(1050,280,true),"cancel never places");
        ClassicLogic.Sprint s = new ClassicLogic.Sprint();
        check(!s.down(1000),"first tap walks"); s.up(1090); check(s.down(1200),"double tap sprints"); s.up(1800); check(!s.down(1900),"long hold is not a first tap");
        s.reset(); check(!s.down(2000),"focus reset removes sprint state");
        System.out.println("ClassicLogicTest: all 15 gesture/input checks passed");
    }
}
