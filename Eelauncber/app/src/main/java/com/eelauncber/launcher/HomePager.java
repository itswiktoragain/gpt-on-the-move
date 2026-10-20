package com.eelauncber.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import java.util.List;

final class HomePager extends ViewGroup {
    interface Listener {
        void onPageChanged(int page, int total);
        void onVerticalSwipe(boolean upward);
    }

    private Listener listener;
    private final int slop;
    private final float flingThreshold;
    private final int maximumVelocity;
    private int page;
    private int pageAtDown;
    private int offsetAtDown;
    private int pointer;
    private float downX, downY;
    private boolean horizontal, vertical;
    private VelocityTracker velocity;
    private ValueAnimator animation;

    HomePager(Context context) {
        super(context);
        ViewConfiguration config = ViewConfiguration.get(context);
        slop = config.getScaledTouchSlop();
        maximumVelocity = config.getScaledMaximumFlingVelocity();
        flingThreshold = Math.max(config.getScaledMinimumFlingVelocity() * 2f,
                280f * getResources().getDisplayMetrics().density);
        setWillNotDraw(true);
        setClipToPadding(true);
    }

    void setListener(Listener l) { listener = l; }
    int getPage() { return page; }
    int getCount() { return getChildCount(); }

    void replacePages(List<View> pages) {
        if (animation != null) animation.cancel();
        removeAllViews();
        for (View child : pages) addView(child);
        page = 0;
        scrollTo(0, 0);
        notifyPage();
        requestLayout();
    }

    private void notifyPage() {
        if (listener != null) listener.onPageChanged(page, getChildCount());
    }

    void animateTo(int requestedPage) {
        int next = Math.max(0, Math.min(Math.max(0, getChildCount() - 1), requestedPage));
        if (animation != null) animation.cancel();
        boolean changed = next != page;
        page = next;
        if (changed) notifyPage();
        int destination = page * getWidth();
        if (destination == getScrollX()) return;
        animation = ValueAnimator.ofInt(getScrollX(), destination);
        float fraction = (float) Math.abs(destination - getScrollX()) / Math.max(1, getWidth());
        animation.setDuration((long) (185 + 95 * Math.min(1f, fraction)));
        animation.setInterpolator(new DecelerateInterpolator(1.7f));
        animation.addUpdateListener(value -> scrollTo((Integer) value.getAnimatedValue(), 0));
        animation.start();
    }

    private void begin(MotionEvent e) {
        if (animation != null) animation.cancel();
        downX = e.getX();
        downY = e.getY();
        pointer = e.getPointerId(0);
        offsetAtDown = getScrollX();
        pageAtDown = page;
        horizontal = false;
        vertical = false;
        if (velocity != null) velocity.recycle();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(e);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (horizontal || vertical) return true;
                if (velocity != null) velocity.addMovement(e);
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (dy < -slop * 2 && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    vertical = true;
                    return true;
                }
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.1f) {
                    horizontal = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                disposeTracker();
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            begin(e);
            return true;
        }
        if (velocity != null) velocity.addMovement(e);
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            if (!horizontal && !vertical) {
                if (dy < -slop * 2 && Math.abs(dy) > Math.abs(dx) * 1.2f) vertical = true;
                else if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.1f)
                    horizontal = true;
            }
            if (horizontal) {
                float desired = offsetAtDown - dx;
                float max = Math.max(0, getChildCount() - 1) * getWidth();
                if (desired < 0) desired *= 0.22f;
                if (desired > max) desired = max + (desired - max) * 0.22f;
                scrollTo(Math.round(desired), 0);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            if (vertical && dy < -slop * 3f) {
                animateTo(page);
                if (listener != null) listener.onVerticalSwipe(true);
            } else if (horizontal) {
                float speed = 0f;
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000, maximumVelocity);
                    speed = velocity.getXVelocity(pointer);
                }
                int next = pageAtDown;
                if (Math.abs(speed) >= flingThreshold && Math.abs(dx) > slop) {
                    next += speed < 0f ? 1 : -1;
                } else if (Math.abs(dx) >= getWidth() * 0.21f) {
                    next += dx < 0f ? 1 : -1;
                } else {
                    next = Math.round((float) getScrollX() / Math.max(1, getWidth()));
                }
                animateTo(next);
            } else {
                animateTo(page);
            }
            disposeTracker();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            disposeTracker();
            animateTo(page);
            return true;
        }
        return true;
    }

    private void disposeTracker() {
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int childW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int childH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(childW, childH);
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++)
            getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        if (changed) scrollTo(page * w, 0);
    }

    @Override protected void onDetachedFromWindow() {
        if (animation != null) animation.cancel();
        disposeTracker();
        super.onDetachedFromWindow();
    }
}
