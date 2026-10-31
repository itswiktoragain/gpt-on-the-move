import net.kdt.pojavlaunch.jogger.mobile.GameTouchMappings;
import net.kdt.pojavlaunch.jogger.mobile.UiMappingFilter;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public final class MiningMappingTest {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Pass exact 1.21.4 properties path");
        Properties verified=new Properties();
        try(InputStream in=Files.newInputStream(Paths.get(args[0]))){verified.load(in);}
        Properties original=new Properties();
        original.setProperty("method.pick","oldIncorrect");
        original.setProperty("method.view","oldIncorrect");
        original.setProperty("field.hitResult","oldIncorrect");
        original.setProperty("class.hud","unmapped");
        Properties ui=UiMappingFilter.merge(original,verified,"1.21.4");
        Properties merged=GameTouchMappings.complete(ui,verified,"1.21.4");
        check(GameTouchMappings.ready(merged),"All real ray and hit fields present");
        for(String name:GameTouchMappings.REQUIRED) {
            check(verified.getProperty(name).equals(merged.getProperty(name)),name+" must exactly match game JAR");
        }
        check("glq".equals(merged.getProperty("class.renderer")),"real renderer mapping");
        check("bum".equals(merged.getProperty("class.entity")),"real entity mapping");
        check("fbb".equals(merged.getProperty("class.vec")),"real vector mapping");
        check("a".equals(merged.getProperty("method.pick")),"real pick method");
        check("g".equals(merged.getProperty("method.view")),"real view-vector method");
        check("j".equals(merged.getProperty("field.renderer")),"real renderer field");
        check("w".equals(merged.getProperty("field.hitResult")),"real block-hit field");
        check("foe".equals(merged.getProperty("class.hud")),"HUD preserved");
        check("b".equals(merged.getProperty("method.hud.health")),"HUD health preserved");
        check("fpd".equals(merged.getProperty("class.edit")),"search keyboard mapping preserved");
        Properties old=GameTouchMappings.complete(original,verified,"1.8");
        check("oldIncorrect".equals(old.getProperty("method.pick")),"Other version mappings unmodified");
        check("oldIncorrect".equals(old.getProperty("field.hitResult")),"Other version hit fields unmodified");
        Properties incomplete=new Properties(); incomplete.setProperty("method.pick","a");
        check(!GameTouchMappings.ready(incomplete),"Never claim ray support on incomplete map");
        System.out.println("MiningMappingTest: "+checks+" checks passed");
    }
}
