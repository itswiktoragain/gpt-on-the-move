package net.kdt.pojavlaunch.jogger.classic;

/** Pixel positions for the contextual Jogger keyboard button. No Android dependencies. */
public final class ClassicTopBarLayout {
    private ClassicTopBarLayout() {}
    public static float keyboardLeft(float screenWidth, float safeRight, float buttonWidth,
                                     float spacing, boolean playing, boolean chat) {
        final float rightButtonLeft = screenWidth - safeRight - buttonWidth;
        // While playing, use the title-screen Menu position. On other screens
        // place the keyboard immediately to the left of Menu or Chat's Send.
        return playing && !chat ? rightButtonLeft : rightButtonLeft - buttonWidth - spacing;
    }
}
