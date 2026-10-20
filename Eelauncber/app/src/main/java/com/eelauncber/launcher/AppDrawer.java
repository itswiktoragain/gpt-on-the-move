package com.eelauncber.launcher;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
/** OneUI-style swipe-up drawer: same wallpaper and paged home-grid layout. */
final class AppDrawer {
 static void show(LauncherActivity a,String initial){
  a.controller.closeShelf();
  FrameLayout overlay=new FrameLayout(a);
  overlay.setBackgroundColor(0x10304059);
  LinearLayout surface=new LinearLayout(a);surface.setOrientation(LinearLayout.VERTICAL);
  surface.setBackground(GlassUi.sheet(a,30));surface.setClickable(true);
  FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-1);
  sp.topMargin=LauncherUi.dp(a,45);overlay.addView(surface,sp);
  LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
  header.setPadding(LauncherUi.dp(a,20),LauncherUi.dp(a,17),LauncherUi.dp(a,19),LauncherUi.dp(a,13));
  ImageView glass=new ImageView(a);glass.setImageResource(R.drawable.f7_search);
  glass.setColorFilter(0xFFEEF3FF);
  header.addView(glass,new LinearLayout.LayoutParams(LauncherUi.dp(a,23),LauncherUi.dp(a,23)));
  EditText search=new EditText(a);search.setSingleLine(true);search.setTextSize(17);
  search.setTextColor(Color.WHITE);search.setHintTextColor(0xFFD3DBE6);search.setHint("Search apps");
  search.setPadding(LauncherUi.dp(a,13),0,0,0);search.setBackgroundColor(Color.TRANSPARENT);
  header.setBackground(GlassUi.input(a,23));
  LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,53));
  h.setMargins(LauncherUi.dp(a,23),LauncherUi.dp(a,21),LauncherUi.dp(a,23),LauncherUi.dp(a,9));
  header.addView(search,new LinearLayout.LayoutParams(0,-1,1));
  ImageView close=new ImageView(a);close.setImageResource(R.drawable.f7_close);
  close.setColorFilter(Color.WHITE);close.setPadding(LauncherUi.dp(a,9),LauncherUi.dp(a,9),LauncherUi.dp(a,9),LauncherUi.dp(a,9));
  header.addView(close,new LinearLayout.LayoutParams(LauncherUi.dp(a,39),LauncherUi.dp(a,39)));
  close.setOnClickListener(v->a.controller.closeShelf());
  surface.addView(header,h);
  HomePager pager=new HomePager(a);
  LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1);
  pp.topMargin=LauncherUi.dp(a,10);surface.addView(pager,pp);
  LinearLayout dots=new LinearLayout(a);dots.setGravity(Gravity.CENTER);
  surface.addView(dots,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,48)));
  pager.setListener(new HomePager.Listener(){
   public void onPageChanged(int p,int count){
    dots.removeAllViews();
    for(int i=0;i<count;i++){View dot=new View(a);dot.setBackground(LauncherUi.bg(a,i==p?0xFFFFFFFF:0x88FFFFFF,6));
      LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(LauncherUi.dp(a,7),LauncherUi.dp(a,7));
      dp.setMargins(LauncherUi.dp(a,4),0,LauncherUi.dp(a,4),0);dots.addView(dot,dp);}
   }
   public void onVerticalSwipe(boolean upward){if(!upward)a.controller.closeShelf();}
  });
  Runnable[] populate=new Runnable[1];
  populate[0]=()->{
   String term=search.getText().toString().toLowerCase(Locale.getDefault()).trim();
   ArrayList<AppCatalog.Entry> result=new ArrayList<>();
   for(AppCatalog.Entry entry:a.catalog.all)if(term.isEmpty()||entry.label.toLowerCase(Locale.getDefault()).contains(term))result.add(entry);
   ArrayList<View> pages=new ArrayList<>();
   int count=Math.max(1,(result.size()+19)/20);
   for(int page=0;page<count;page++){
    HomeGrid grid=new HomeGrid(a);
    for(int j=0;j<20;j++){
     int k=page*20+j;
     FrameLayout cell=new FrameLayout(a);
     if(k<result.size()){
      AppCatalog.Entry entry=result.get(k);
      LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);
      col.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
      col.setPadding(0,LauncherUi.dp(a,9),0,0);
      int size=a.prefs.getInt("icon_size",58);
      ImageView icon=LauncherUi.icon(a,entry.key,size);
      col.addView(icon,new LinearLayout.LayoutParams(LauncherUi.dp(a,size),LauncherUi.dp(a,size)));
      TextView label=LauncherUi.text(a,entry.label,11,Color.WHITE,false);
      label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
      label.setShadowLayer(LauncherUi.dp(a,2),0,1,0xAA000000);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,32));
      lp.topMargin=LauncherUi.dp(a,5);col.addView(label,lp);
      cell.addView(col,new FrameLayout.LayoutParams(-1,-1));
      cell.setOnClickListener(v->{a.controller.closeShelf();LauncherActions.launch(a,entry.key);});
      cell.setOnLongClickListener(v->{AppOptions.open(a,entry);return true;});
     }
     grid.addView(cell,new HomeGrid.Params(j,1,1));
    }
    pages.add(grid);
   }
   pager.replacePages(pages);
  };
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int st,int count,int after){}
   public void onTextChanged(CharSequence s,int st,int before,int count){populate[0].run();}
   public void afterTextChanged(Editable e){}
  });
  search.setText(initial);populate[0].run();
  GlassUi.show(a,overlay,surface);
 }
}
