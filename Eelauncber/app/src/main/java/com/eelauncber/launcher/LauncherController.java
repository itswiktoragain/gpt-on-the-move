package com.eelauncber.launcher;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LauncherController {
    final LauncherActivity a;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable clockTick=new Runnable(){
        @Override public void run(){
            HomeScreen.updateClock(a);
            handler.postDelayed(this,30000);
        }
    };
    LauncherController(LauncherActivity activity){a=activity;}
    void start(){
        a.prefs=a.getSharedPreferences("eelauncber",0);
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        a.getWindow().setStatusBarColor(Color.TRANSPARENT);
        a.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        a.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        a.widgets=new WidgetHost(a);
        HomeScreen.build(a);
        handler.post(clockTick);
        scan();
    }
    void resume(){
        HomeScreen.updateClock(a);
        if(System.currentTimeMillis()-a.lastScan>20000)scan();
    }
    void scan(){
        if(a.scanning)return;
        a.scanning=true;
        worker.execute(()->{
            AppCatalog fresh=AppCatalog.scan(a.getApplicationContext());
            a.runOnUiThread(()->{
                a.scanning=false;
                if(a.isFinishing()||a.isDestroyed())return;
                a.catalog=fresh;
                a.home=HomeState.read(a.prefs,a.catalog);
                a.lastScan=System.currentTimeMillis();
                HomeScreen.draw(a);
            });
        });
    }
    boolean back(){
        if(a.glassDialog!=null){closeShelf();return true;}
        if(a.shelf!=null){closeShelf();return true;}
        if(a.editing){setEditing(false);return true;}
        if(a.pager!=null&&a.pager.getPage()>0){a.pager.animateTo(0);return true;}
        return false;
    }
    void home(){
        closeShelf();
        if(a.pager!=null)a.pager.animateTo(0);
        if(a.editing)setEditing(false);
    }
    void closeShelf(){
        if(a.glassDialog!=null){
            android.app.Dialog old=a.glassDialog;
            a.glassDialog=null;
            old.dismiss();
        }
        if(a.shelf!=null){a.root.removeView(a.shelf);a.shelf=null;}
    }
    void setEditing(boolean editing){
        a.editing=editing;
        if(a.done!=null)a.done.setVisibility(editing?View.VISIBLE:View.GONE);
        HomeScreen.draw(a);
    }
    void stop(){handler.removeCallbacks(clockTick);worker.shutdownNow();}
}
