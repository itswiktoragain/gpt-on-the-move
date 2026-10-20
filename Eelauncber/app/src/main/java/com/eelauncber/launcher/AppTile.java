package com.eelauncber.launcher;
import android.graphics.Color;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppTile {
    static View create(LauncherActivity a,HomeState.Item item,boolean dock,int index){
        FrameLayout cell=new FrameLayout(a);
        if(item!=null){
            int size=Math.max(48,Math.min(68,a.prefs.getInt("icon_size",58)));
            LinearLayout col=new LinearLayout(a);
            col.setGravity(dock?Gravity.CENTER:Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(0,dock?0:LauncherUi.dp(a,8),0,0);
            View image=item.isFolder()?FolderIcon.create(a,item,size):
                    LauncherUi.icon(a,item.apps.get(0),size);
            col.addView(image,new LinearLayout.LayoutParams(LauncherUi.dp(a,size),LauncherUi.dp(a,size)));
            if(!dock&&a.prefs.getBoolean("labels",true)){
                AppCatalog.Entry app=a.catalog.get(item.apps.get(0));
                String name=item.isFolder()?(item.name.isEmpty()?"Folder":item.name):
                        app==null?"App":app.label;
                TextView text=LauncherUi.text(a,name,11,Color.WHITE,false);
                text.setSingleLine(true);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.setShadowLayer(3,0,2,0xA0000000);
                LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,20));
                label.topMargin=LauncherUi.dp(a,5);
                col.addView(text,label);
            }
            cell.addView(col,new FrameLayout.LayoutParams(-1,-1));
            cell.setOnClickListener(v->LauncherActions.open(a,item));
            cell.setOnLongClickListener(v->{HomeDrag.start(a,cell,item,dock,index);return true;});
            if(a.editing){
                final ObjectAnimator wobble=ObjectAnimator.ofFloat(col,"rotation",
                    index%2==0?-2.2f:2.2f,index%2==0?2.2f:-2.2f);
                wobble.setDuration(155+index%4*13);
                wobble.setRepeatCount(ValueAnimator.INFINITE);
                wobble.setRepeatMode(ValueAnimator.REVERSE);
                wobble.setInterpolator(new LinearInterpolator());
                col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    @Override public void onViewAttachedToWindow(View v){wobble.start();}
                    @Override public void onViewDetachedFromWindow(View v){wobble.cancel();col.setRotation(0);}
                });
                if(col.isAttachedToWindow())wobble.start();
                TextView minus=LauncherUi.text(a,"−",17,0xFF29344B,true);
                minus.setBackground(LauncherUi.bg(a,0xFFF4F6FC,12));
                FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(
                        LauncherUi.dp(a,22),LauncherUi.dp(a,22),Gravity.TOP|Gravity.LEFT);
                bp.leftMargin=LauncherUi.dp(a,10);
                cell.addView(minus,bp);
                minus.setOnClickListener(v->LauncherActions.remove(a,item,dock));
            }
        } else cell.setOnLongClickListener(v->{if(dock)AppDrawer.show(a,"");else GridPlacement.choose(a,index);return true;});
        cell.setOnDragListener((v,event)->{
            if(!(event.getLocalState() instanceof HomeDrag.Ref))return false;
            if(event.getAction()==DragEvent.ACTION_DROP)
                HomeDrag.drop(a,(HomeDrag.Ref)event.getLocalState(),dock,index,item);
            if(event.getAction()==DragEvent.ACTION_DRAG_ENDED)
                HomeDrag.end(a,(HomeDrag.Ref)event.getLocalState());
            return true;
        });
        return cell;
    }
}
