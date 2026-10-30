import net.kdt.pojavlaunch.jogger.classic.ClassicTopBarLayout;

public final class KeyboardButtonLayoutTest {
    private static int checks;
    private static void check(boolean condition, String caseName) {
        if (!condition) throw new AssertionError(caseName);
        checks++;
    }
    public static void main(String[] args) {
        final float screen=1000f, right=10f, width=53f, gap=6f;
        final float menu=screen-right-width;
        final float title=ClassicTopBarLayout.keyboardLeft(screen,right,width,gap,false,false);
        final float play=ClassicTopBarLayout.keyboardLeft(screen,right,width,gap,true,false);
        final float chat=ClassicTopBarLayout.keyboardLeft(screen,right,width,gap,false,true);
        check(title+width+gap==menu, "outside: keyboard immediately left of Menu");
        check(play==menu, "in game: keyboard occupies title-screen Menu position");
        check(chat+width+gap==menu, "chat: keyboard left of Send, no overlap");
        check(title>=0&&play+width<=screen-right, "within right insets");
        check(ClassicTopBarLayout.keyboardLeft(800,30,100,4,true,false)==670, "right inset respected");
        check(ClassicTopBarLayout.keyboardLeft(800,30,100,4,false,false)==566, "larger screen geometry");
        System.out.println("KeyboardButtonLayoutTest: "+checks+" checks passed");
    }
}
