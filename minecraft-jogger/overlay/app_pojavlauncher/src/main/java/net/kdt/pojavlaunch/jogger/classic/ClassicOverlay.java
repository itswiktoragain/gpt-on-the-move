package net.kdt.pojavlaunch.jogger.classic;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.SparseArray;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.jogger.JoggerTouchBridge;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import org.lwjgl.glfw.CallbackBridge;
import java.lang.ref.WeakReference;
import java.util.*;

/** Classic mobile controls. One pointer router supports walking, looking and jumping together. */
public final class ClassicOverlay extends View implements GrabListener, MCOptionUtils.MCOptionListener {
    private static WeakReference<ClassicOverlay> live = new WeakReference<>(null);
    private static final int UP=1, LEFT=2, CROUCH=3, RIGHT=4, DOWN=5, UL=6, UR=7,
        JUMP=8, MINE=9, USE=10, CHAT=11, PAUSE=12, MENU=13, KEYBOARD=14, SEND=15, BAG=16, BACK=17;
    private static final int WORLD=100, HOTBAR=101, IGNORED=102;
    private final MainActivity activity;
    private final Runnable appMenu;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final SparseArray<Route> routes = new SparseArray<>();
    private final ClassicLogic.Gesture gesture = new ClassicLogic.Gesture();
    private final ClassicLogic.Sprint sprint = new ClassicLogic.Sprint();
    private final ClassicLogic.Inputs inputs;
    private final Paint paint = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect sourceRect = new Rect();
    private final RectF destinationRect = new RectF(), hotbar = new RectF();
    private final float density;
    private Bitmap[] skin;
    private String mode = "SCREEN";
    private int worldPointer = -1, pulseOwner = -10000;
    private boolean sneaking, sprinting, stopped, raySupported;
    private int forward=87, left=65, back=83, right=68, jump=32, sneak=340, run=341, inventory=69, chat=84;
    private int geometryW=-1, geometryH=-1, geometryGui=-1;
    private float geometryRatio=-1;
    private long gestureGeneration;
    private final Runnable hold = () -> {
        if (!stopped && playing() && worldPointer >= 0 && gesture.hold(SystemClock.uptimeMillis(), holdDelay())) {
            inputs.set(-3, -1); invalidate();
        }
    };
    private static final class Button {
        final int id; final RectF bounds; final String[] icon; final String label;
        Button(int id, float x, float y, float w, float h, String[] icon, String label) {
            this.id=id; bounds=new RectF(x,y,x+w,y+h); this.icon=icon; this.label=label;
        }
    }
    private static final class Route {
        int kind; Button button; boolean valid=true;
        Route(int kind, Button button) { this.kind=kind; this.button=button; }
    }
    private static final String[] ARROW={"0001000","0011100","0111110","1111111","0001000","0001000","0001000"};
    private static final String[] PERSON={"0011100","0011100","0001000","0011100","0111110","0100010","1100011"};
    private static final String[] JUMP_ICON={"0001000","0011100","0111110","0001000","0001000","0001000","1111111"};
    private static final String[] PICK={"1111100","0010110","0010011","0001000","0010000","0100000","1000000"};
    private static final String[] BLOCK={"1111111","1000001","1011101","1011101","1011101","1000001","1111111"};
    private static final String[] CHAT_ICON={"1111111","1000001","1010101","1000001","1111111","0011000","0010000"};
    private static final String[] PAUSE_ICON={"0110110","0110110","0110110","0110110","0110110","0110110","0110110"};
    private static final String[] MENU_ICON={"0000000","1111111","0000000","1111111","0000000","1111111","0000000"};
    private static final String[] TYPE_ICON={"0000000","1111111","1010101","1101011","1010101","1100011","1111111"};
    private static final String[] SEND_ICON={"0001000","0001100","1111110","1111111","1111110","0001100","0001000"};
    private static final String[] DOTS={"0000000","0000000","0000000","1101101","1101101","0000000","0000000"};

    public ClassicOverlay(MainActivity activity, MinecraftProfile profile, Runnable appMenu) {
        super(activity); this.activity=activity; this.appMenu=appMenu;
        density=getResources().getDisplayMetrics().density;
        setFocusable(false); setWillNotDraw(false); setContentDescription("Classic mobile Minecraft controls");
        setFilterTouchesWhenObscured(true);
        inputs=new ClassicLogic.Inputs(new ClassicLogic.Output() {
            @Override public void key(int key, boolean down) { JoggerTouchBridge.sendKey(key, (char)0, 0, CallbackBridge.getCurrentMods(), down); }
            @Override public void mouse(int button, boolean down) { JoggerTouchBridge.sendMouse(button, down); }
        });
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); text.setTextAlign(Paint.Align.CENTER);
        refreshBindings();
        Thread textures=new Thread(() -> {
            Bitmap[] decoded=GameAssets.textures(Tools.getGameDirPath(profile));
            handler.post(() -> { if (!stopped) { skin=decoded; invalidate(); } });
        }, "Jogger game button textures"); textures.setDaemon(true); textures.start();
    }
    public static void report(String state, boolean capability) {
        ClassicOverlay overlay=live.get(); if (overlay==null) return;
        overlay.handler.post(() -> {
            if (overlay.stopped) return;
            overlay.raySupported=capability;
            if (!state.equals(overlay.mode)) { overlay.release(); overlay.mode=state; overlay.invalidate(); }
        });
    }
    public static void physicalKey(int code) {
        ClassicOverlay overlay=live.get(); if (overlay==null) return;
        if (overlay.playing() && (code==KeyEvent.KEYCODE_T || code==KeyEvent.KEYCODE_SLASH)) {
            overlay.mode="CHAT"; overlay.invalidate();
        }
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); stopped=false; live=new WeakReference<>(this);
        CallbackBridge.addGrabListener(this); MCOptionUtils.addMCOptionListener(this);
        mode=CallbackBridge.isGrabbing()?"PLAY":"SCREEN";
    }
    @Override protected void onDetachedFromWindow() {
        stopped=true; release(); handler.removeCallbacksAndMessages(null);
        CallbackBridge.removeGrabListener(this); MCOptionUtils.removeMCOptionListener(this);
        if (live.get()==this) live.clear(); super.onDetachedFromWindow();
    }
    @Override public void onGrabState(boolean grabbed) {
        handler.post(() -> {
            if (stopped) return; release();
            if (grabbed) mode="PLAY"; else if (mode.equals("PLAY")) mode="SCREEN";
            invalidate();
        });
    }
    @Override public void onOptionChanged() { handler.post(() -> { refreshBindings(); geometryGui=-1; invalidate(); }); }
    private boolean playing() { return CallbackBridge.isGrabbing() && mode.equals("PLAY"); }
    private float dp(float value) { return value*density; }
    private int holdDelay() { return Math.max(180,Math.min(700,LauncherPreferences.DEFAULT_PREF.getInt("timeLongPressTrigger",280))); }
    private int binding(String option,int fallback) {
        String value=MCOptionUtils.get("key_key."+option); if (value==null) return fallback;
        if (value.startsWith("key.keyboard.")) {
            String code=value.substring(13);
            if (code.length()==1) return Character.toUpperCase(code.charAt(0));
            if (code.equals("space")) return 32; if(code.equals("left.shift"))return 340;
            if(code.equals("left.control"))return 341; if(code.equals("right.shift"))return 344;
            if(code.equals("right.control"))return 345; if(code.equals("tab"))return 258;
            if(code.equals("up"))return 265; if(code.equals("down"))return 264;
            if(code.equals("left"))return 263; if(code.equals("right"))return 262;
        }
        try { int legacy=Integer.parseInt(value); if (legacy>=0) return fallback; } catch (NumberFormatException ignored) {}
        return fallback;
    }
    private void refreshBindings() {
        forward=binding("forward",87);back=binding("back",83);left=binding("left",65);right=binding("right",68);
        jump=binding("jump",32);sneak=binding("sneak",340);run=binding("sprint",341);inventory=binding("inventory",69);chat=binding("chat",84);
    }
    private void layoutControls() {
        int width=getWidth(),height=getHeight(),gui=1;
        try { gui=MCOptionUtils.getMcScale(); } catch (RuntimeException ignored) {}
        float ratio=CallbackBridge.windowWidth>0 ? (float)width/CallbackBridge.windowWidth : 1f;
        if(width==geometryW && height==geometryH && gui==geometryGui && ratio==geometryRatio)return;
        geometryW=width;geometryH=height;geometryGui=gui;geometryRatio=ratio;buttons.clear();
        float cell=Math.max(dp(30),Math.min(dp(49),height*.145f)), x=dp(12), y=height-3*cell-dp(14), gap=dp(2);
        add(UL,x,y,cell-gap,cell-gap,ARROW,null);add(UP,x+cell,y,cell-gap,cell-gap,ARROW,null);add(UR,x+2*cell,y,cell-gap,cell-gap,ARROW,null);
        add(LEFT,x,y+cell,cell-gap,cell-gap,ARROW,null);add(CROUCH,x+cell,y+cell,cell-gap,cell-gap,PERSON,null);add(RIGHT,x+2*cell,y+cell,cell-gap,cell-gap,ARROW,null);
        add(DOWN,x+cell,y+2*cell,cell-gap,cell-gap,ARROW,null);
        float gameScale=gui*ratio; float hbHeight=20*gameScale;
        hotbar.set(width*.5f-90*gameScale,height-hbHeight,width*.5f+90*gameScale,height);
        add(BAG,width*.5f+92*gameScale,height-22*gameScale,20*gameScale,22*gameScale,DOTS,null);
        float actionY=Math.min(height-2*cell-dp(14),hotbar.top-cell*.25f);
        add(JUMP,width-2*cell-dp(14),actionY,cell*1.5f,cell*1.5f,JUMP_ICON,null);
        add(MINE,width-3.2f*cell-dp(14),actionY,cell,cell,PICK,null);
        add(USE,width-1.5f*cell-dp(14),actionY-cell*1.15f,cell,cell,BLOCK,null);
        float top=Math.max(dp(8),getRootWindowInsets()==null?0:getRootWindowInsets().getStableInsetTop());
        float h=dp(39), w=dp(53), middle=width*.5f;
        add(CHAT,middle-1.5f*w-dp(3),top,w,h,CHAT_ICON,null);add(PAUSE,middle-.5f*w,top,w,h,PAUSE_ICON,null);add(MENU,middle+.5f*w+dp(3),top,w,h,MENU_ICON,null);
        add(BACK,middle-1.5f*w-dp(3),top,w,h,null,"Leave");add(KEYBOARD,middle-.5f*w,top,w,h,TYPE_ICON,null);add(SEND,middle+.5f*w+dp(3),top,w,h,SEND_ICON,null);
    }
    private void add(int id,float x,float y,float w,float h,String[] icon,String label){buttons.add(new Button(id,x,y,w,h,icon,label));}
    private boolean visible(Button b) {
        if (b.id<=USE || b.id==BAG) return playing();
        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==KEYBOARD||b.id==SEND;
        return b.id==BACK||b.id==KEYBOARD||b.id==MENU;
    }
    private Button hit(float x,float y) { for(Button b:buttons)if(visible(b)&&b.bounds.contains(x,y))return b;return null; }
    private boolean directional(Button b) { return b!=null && b.id>=UP&&b.id<=UR&&b.id!=CROUCH; }
    @Override protected void onDraw(Canvas canvas) {
        layoutControls();
        for(Button b:buttons)if(visible(b)) {
            boolean pressed=b.id==CROUCH?sneaking:pressed(b);
            drawButton(canvas,b,pressed);
        }
        if(playing() && raySupported && gesture.active && !gesture.moved && targetEnabled()) {
            paint.setColor(0xffeeeeee);paint.setAlpha(200);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));
            canvas.drawCircle(gesture.startX,gesture.startY,dp(7),paint);paint.setStyle(Paint.Style.FILL);
        }
    }
    private boolean pressed(Button button) {
        for(int i=0;i<routes.size();i++)if(routes.valueAt(i).button==button && routes.valueAt(i).valid)return true;
        return button.id==UP&&sprinting;
    }
    private void drawButton(Canvas canvas,Button button,boolean pressed) {
        RectF r=button.bounds;Bitmap image=skin==null?null:skin[pressed&&skin[1]!=null?1:0];
        paint.setFilterBitmap(false);paint.setAlpha(pressed?245:205);paint.setStyle(Paint.Style.FILL);
        if(image!=null) {
            int sw=image.getWidth(),sh=image.getHeight(),b=Math.max(1,Math.round(sh*.1f));float edge=Math.max(1,Math.min(r.width(),r.height())*.1f);
            for(int iy=0;iy<3;iy++)for(int ix=0;ix<3;ix++) {
                int sx=ix==0?0:ix==1?b:sw-b,sy=iy==0?0:iy==1?b:sh-b;
                int ex=ix==0?b:ix==1?sw-b:sw,ey=iy==0?b:iy==1?sh-b:sh;
                float dx=ix==0?r.left:ix==1?r.left+edge:r.right-edge,dy=iy==0?r.top:iy==1?r.top+edge:r.bottom-edge;
                float de=ix==0?r.left+edge:ix==1?r.right-edge:r.right,be=iy==0?r.top+edge:iy==1?r.bottom-edge:r.bottom;
                sourceRect.set(sx,sy,ex,ey);destinationRect.set(dx,dy,de,be);canvas.drawBitmap(image,sourceRect,destinationRect,paint);
            }
        } else {
            paint.setColor(pressed?0xff444444:0xff777777);canvas.drawRect(r,paint);
            paint.setColor(0xffc7c7c7);canvas.drawRect(r.left,r.top,r.right,r.top+dp(2),paint);canvas.drawRect(r.left,r.top,r.left+dp(2),r.bottom,paint);
            paint.setColor(0xff222222);canvas.drawRect(r.left,r.bottom-dp(2),r.right,r.bottom,paint);canvas.drawRect(r.right-dp(2),r.top,r.right,r.bottom,paint);
        }
        if(button.label!=null) {
            text.setTextSize(Math.min(dp(13),r.height()*.37f));text.setColor(0xff202020);float y=r.centerY()-(text.ascent()+text.descent())*.5f;
            canvas.drawText(mode.equals("CHAT")?"Leave":"Back",r.centerX()+dp(1),y+dp(1),text);text.setColor(Color.WHITE);canvas.drawText(mode.equals("CHAT")?"Leave":"Back",r.centerX(),y,text);
        } else if(button.icon!=null) {
            canvas.save();canvas.translate(r.centerX(),r.centerY());
            if(button.id==LEFT)canvas.rotate(-90);else if(button.id==RIGHT)canvas.rotate(90);else if(button.id==DOWN)canvas.rotate(180);else if(button.id==UL)canvas.rotate(-45);else if(button.id==UR)canvas.rotate(45);
            float pixel=Math.max(1,(float)Math.floor(Math.min(r.width(),r.height())*.075f));
            for(int pass=0;pass<2;pass++) {
                paint.setColor(pass==0?0xff202020:0xfff4f4f4);float offset=pass==0?pixel*.5f:0;
                for(int row=0;row<button.icon.length;row++)for(int col=0;col<button.icon[row].length();col++)if(button.icon[row].charAt(col)=='1') {
                    float px=(col-3.5f)*pixel+offset,py=(row-3.5f)*pixel+offset;canvas.drawRect(px,py,px+pixel,py+pixel,paint);
                }
            }canvas.restore();
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        layoutControls();int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);
        if(action==MotionEvent.ACTION_DOWN && !playing() && hit(event.getX(),event.getY())==null)return false;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            float x=event.getX(index),y=event.getY(index);Button b=hit(x,y);
            if(b!=null) { Route r=new Route(b.id,b);routes.put(id,r);press(id,r); }
            else if(playing()&&hotbar.contains(x,y)) {routes.put(id,new Route(HOTBAR,null));selectSlot(x);}
            else if(playing()&&worldPointer<0) {
                routes.put(id,new Route(WORLD,null));worldPointer=id;gestureGeneration++;
                gesture.begin(x,y,event.getEventTime(),dp(8));target(x,y,true);handler.postDelayed(hold,holdDelay());
            }else routes.put(id,new Route(IGNORED,null));
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE) {
            for(int i=0;i<event.getPointerCount();i++) {
                int pointer=event.getPointerId(i);Route route=routes.get(pointer);if(route==null)continue;
                float x=event.getX(i),y=event.getY(i);
                if(route.kind==WORLD&&pointer==worldPointer&&playing()) {
                    gesture.move(x,y);
                    if(gesture.moved) {
                        handler.removeCallbacks(hold);target(0,0,false);
                        float factor=1800f/Math.max(1,getHeight())*LauncherPreferences.PREF_MOUSESPEED;
                        float dx=(x-gesture.lastX)*factor,dy=(y-gesture.lastY)*factor;
                        CallbackBridge.mouseX+=dx;CallbackBridge.mouseY+=dy;CallbackBridge.deltaX=dx;CallbackBridge.deltaY=dy;
                        CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY);
                    }
                    gesture.lastX=x;gesture.lastY=y;
                }else if(directional(route.button)) {
                    Button next=hit(x,y);
                    if(next!=route.button) {
                        inputs.set(pointer+100);
                        if(directional(next)){route.button=next;route.kind=next.id;press(pointer,route);}
                        else {route.valid=false;}
                        stopSprintWhenIdle();invalidate();
                    }
                }else if(route.kind==HOTBAR&&playing()&&hotbar.contains(x,y))selectSlot(x);
                else if(route.button!=null&&!route.button.bounds.contains(x,y))route.valid=false;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){release();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            Route route=routes.get(id);routes.remove(id);inputs.set(id+100);
            if(route!=null) {
                if(route.kind==UP)sprint.up(event.getEventTime());
                if(route.kind==WORLD&&id==worldPointer) {
                    handler.removeCallbacks(hold);boolean place=gesture.end(event.getEventTime(),holdDelay(),!playing());
                    inputs.set(-3);worldPointer=-1;long generation=gestureGeneration;
                    if(place)pulse(-2);
                    handler.postDelayed(() -> {if(gestureGeneration==generation&&worldPointer<0)target(0,0,false);},150);
                }else if(route.valid&&route.button!=null&&route.button.bounds.contains(event.getX(index),event.getY(index)))click(route.kind);
            }
            stopSprintWhenIdle();invalidate();performClick();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void press(int pointer,Route route) {
        int source=pointer+100;route.valid=true;
        switch(route.kind) {
            case UP: if(sprint.down(SystemClock.uptimeMillis())){sprinting=true;inputs.set(-2,run);} inputs.set(source,forward);break;
            case LEFT:inputs.set(source,left);break;case RIGHT:inputs.set(source,right);break;case DOWN:inputs.set(source,back);break;
            case UL:inputs.set(source,forward,left);break;case UR:inputs.set(source,forward,right);break;
            case JUMP:inputs.set(source,jump);break;case MINE:inputs.set(source,-1);break;case USE:inputs.set(source,-2);break;
        }
    }
    private void stopSprintWhenIdle(){if(!inputs.held(forward)){inputs.set(-2);sprinting=false;}}
    private int lastSlot=-1;
    private void selectSlot(float x) {
        int slot=Math.max(0,Math.min(8,(int)((x-hotbar.left)/hotbar.width()*9)));
        if(slot==lastSlot)return;lastSlot=slot;pulse(binding("hotbar."+(slot+1),49+slot));
    }
    private void pulse(int code){int source=pulseOwner--;inputs.set(source,code);handler.postDelayed(()->inputs.set(source),85);}
    private void click(int id) {
        if(id==CROUCH&&playing()){sneaking=!sneaking;inputs.set(-1,sneaking?sneak:0);return;}
        if(id==BAG&&playing()){release();pulse(inventory);mode="SCREEN";return;}
        if(id==CHAT&&playing()) {
            release();pulse(chat);mode="CHAT";
            handler.postDelayed(()->{if(!stopped&&mode.equals("CHAT")&&MainActivity.touchCharInput!=null&&!MainActivity.touchCharInput.isEnabled())MainActivity.switchKeyboardState();},350);return;
        }
        if(id==PAUSE||id==BACK){release();pulse(256);hideKeyboard();mode="SCREEN";return;}
        if(id==SEND){pulse(257);hideKeyboard();mode="SCREEN";return;}
        if(id==KEYBOARD){MainActivity.switchKeyboardState();return;}
        if(id==MENU){boolean wasPlaying=playing();release();if(wasPlaying)pulse(256);handler.postDelayed(appMenu,100);}
    }
    private void hideKeyboard(){((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindowToken(),0);}
    private boolean targetEnabled(){return LauncherPreferences.DEFAULT_PREF.getBoolean("jogger_touch_target",true);}
    private void target(float x,float y,boolean active) {
        if(active&&!targetEnabled())return;
        JoggerTouchBridge.sendTouchTarget(active?2*x/Math.max(1,getWidth())-1:0,active?1-2*y/Math.max(1,getHeight()):0,(float)getWidth()/Math.max(1,getHeight()),active);
    }
    public void release() {
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(this::release);return;}
        handler.removeCallbacks(hold);gestureGeneration++;gesture.cancel();worldPointer=-1;lastSlot=-1;
        inputs.clear();routes.clear();sneaking=sprinting=false;sprint.reset();target(0,0,false);invalidate();
    }
}
