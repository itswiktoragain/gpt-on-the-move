package com.minecraftjogger.touch;

import java.util.*;

/** Test real UI tap location, not the globally focused search field. */
public final class WorldSearchFocusTest {
    private static int checks;
    private static void check(boolean good,String message) {
        if(!good)throw new AssertionError(message);
        checks++;
    }
    static final class FakeEdit {
        // Obfuscated Minecraft 1.21.4 EditBox fields
        int c=190,d=32,g=180,h=22;
        boolean focused;
        public boolean aM_(){return focused;}
        public boolean i(){return true;}
        public boolean l(){return true;}
    }
    static final class FakeWorldSelect {
        // Obfuscated Minecraft 1.21.4 Screen width/height
        int n=560,o=315;
        FakeEdit search=new FakeEdit();
        public List<Object> aH_(){return Arrays.<Object>asList(search);}
        public Object aL_(){return search.focused?search:null;}
    }
    public static void main(String[] args) {
        Properties p=new Properties();
        p.setProperty("class.edit",FakeEdit.class.getName());
        p.setProperty("method.gui.focused","aL_");
        p.setProperty("method.gui.children","aH_");
        p.setProperty("method.widget.focused","aM_");
        p.setProperty("method.edit.visible","i");
        p.setProperty("method.edit.editable","l");
        p.setProperty("field.screen.width","n");
        p.setProperty("field.screen.height","o");
        p.setProperty("field.widget.x","c");
        p.setProperty("field.widget.y","d");
        p.setProperty("field.widget.width","g");
        p.setProperty("field.widget.height","h");
        FakeWorldSelect screen=new FakeWorldSelect();
        TextInputProbe.observe(screen,"SCREEN",p);
        check(!TextInputProbe.state().active,"No keyboard before a tap");
        // Even if the game's focus callback has not fired, a tap on the
        // world-select search rectangle opens the existing Jogger keyboard.
        TextInputProbe.request(240f/560f,40f/315f);
        TextInputProbe.observe(screen,"SCREEN",p);
        check(TextInputProbe.state().active,"Tapping search rectangle activates keyboard");
        check(TextInputProbe.state().token>0,"Focus token reported");
        TextInputProbe.request(12f/560f,160f/315f);
        TextInputProbe.observe(screen,"SCREEN",p);
        check(!TextInputProbe.state().active,"Tapping outside search does not activate keyboard");
        // Focus propagation alone does not pop a keyboard in a random menu.
        screen.search.focused=true;
        TextInputProbe.observe(screen,"SCREEN",p);
        check(!TextInputProbe.state().active,"No automatic keyboard on unrelated changes");
        TextInputProbe.request(240f/560f,40f/315f);
        TextInputProbe.observe(screen,"SCREEN",p);
        check(TextInputProbe.state().active,"Focused world-search tap activates keyboard");
        TextInputProbe.observe(null,"SCREEN",p);
        check(!TextInputProbe.state().active,"Leaving world select dismisses keyboard");
        System.out.println("WorldSearchFocusTest: "+checks+" checks passed");
    }
}
