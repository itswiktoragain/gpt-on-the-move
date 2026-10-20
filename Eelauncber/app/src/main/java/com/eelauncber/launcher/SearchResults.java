package com.eelauncber.launcher;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

final class SearchResults {
    static void render(LauncherActivity a,LinearLayout list,String query){
        list.removeAllViews();
        String term=query.trim().toLowerCase(Locale.ROOT);
        int count=0;
        for(AppCatalog.Entry app:a.catalog.all){
            if(!app.label.toLowerCase(Locale.ROOT).contains(term)&&
                !app.packageName.toLowerCase(Locale.ROOT).contains(term))continue;
            LinearLayout row=new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(LauncherUi.dp(a,8),0,0,0);
            ImageView image=LauncherUi.icon(a,app.key,41);
            row.addView(image,new LinearLayout.LayoutParams(LauncherUi.dp(a,41),LauncherUi.dp(a,41)));
            TextView name=LauncherUi.text(a,app.label,16,0xFF293750,false);
            name.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            name.setPadding(LauncherUi.dp(a,14),0,0,0);
            row.addView(name,new LinearLayout.LayoutParams(-1,-1));
            list.addView(row,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,61)));
            row.setOnClickListener(v->{a.controller.closeShelf();LauncherActions.launch(a,app.key);});
            row.setOnLongClickListener(v->{AppOptions.open(a,app);return true;});
            count++;
        }
        if(count==0){
            TextView empty=LauncherUi.text(a,"No matching apps",16,0xFF69748A,false);
            list.addView(empty,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,90)));
        }
    }
}
