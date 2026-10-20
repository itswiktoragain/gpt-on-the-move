package com.eelauncber.launcher;
import android.app.Dialog;
import android.appwidget.AppWidgetProviderInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native widget gallery with real provider preview assets and size options. */
final class WidgetGallery {
 static final int WHITE=0xFFF8F8FA,DIM=0xFFA9ABB6,BLUE=0xFF0A84FF;
 static class Size {
  final String name;final int x,y;
  Size(String n,int x,int y){name=n;this.x=x;this.y=y;}
  String grid(){return x+" × "+y;}
 }
 static String title(LauncherActivity a,AppWidgetProviderInfo info){
  String n=info.loadLabel(a.getPackageManager());
  return n==null||n.isEmpty()?info.provider.getShortClassName():n;
 }
 static String owner(LauncherActivity a,AppWidgetProviderInfo info){
  try{
   PackageManager pm=a.getPackageManager();
   return pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(),0)).toString();
  }catch(Exception ex){return info.provider.getPackageName();}
 }
 static Drawable preview(LauncherActivity a,AppWidgetProviderInfo info){
  try{return info.loadPreviewImage(a.getPackageManager());}catch(Exception ignored){return null;}
 }
 static Drawable icon(LauncherActivity a,AppWidgetProviderInfo info){
  try{return a.getPackageManager().getApplicationIcon(info.provider.getPackageName());}
  catch(Exception e){return a.getDrawable(android.R.drawable.sym_def_app_icon);}
 }
 static List<Size> sizes(LauncherActivity a,AppWidgetProviderInfo info){
  ArrayList<Size> list=new ArrayList<>();
  int cellW=Math.max(1,a.getResources().getDisplayMetrics().widthPixels/4);
  int cellH=Math.max(1,(a.getResources().getDisplayMetrics().heightPixels-LauncherUi.dp(a,336))/5);
  int baseX=clamp((int)Math.ceil((double)Math.max(1,info.minWidth)/cellW),1,4);
  int baseY=clamp((int)Math.ceil((double)Math.max(1,info.minHeight)/cellH),1,5);
  list.add(new Size("Default",baseX,baseY));
  if(info.resizeMode!=AppWidgetProviderInfo.RESIZE_NONE){
   int minW=(info.resizeMode&AppWidgetProviderInfo.RESIZE_HORIZONTAL)!=0&&info.minResizeWidth>0?
      Math.min(info.minResizeWidth,info.minWidth):info.minWidth;
   int minH=(info.resizeMode&AppWidgetProviderInfo.RESIZE_VERTICAL)!=0&&info.minResizeHeight>0?
      Math.min(info.minResizeHeight,info.minHeight):info.minHeight;
   int minX=clamp((int)Math.ceil((double)Math.max(1,minW)/cellW),1,4);
   int minY=clamp((int)Math.ceil((double)Math.max(1,minH)/cellH),1,5);
   int[][] options={{2,2},{4,2},{4,4}};
   String[] names={"Small","Medium","Large"};
   for(int i=0;i<options.length;i++){
    int x=options[i][0],y=options[i][1];
    if(x<minX||y<minY)continue;
    if((info.resizeMode&AppWidgetProviderInfo.RESIZE_HORIZONTAL)==0&&x!=baseX)continue;
    if((info.resizeMode&AppWidgetProviderInfo.RESIZE_VERTICAL)==0&&y!=baseY)continue;
    boolean exists=false;
    for(Size sz:list)if(sz.x==x&&sz.y==y)exists=true;
    if(!exists)list.add(new Size(names[i],x,y));
   }
  }
  return list;
 }
 static int clamp(int v,int l,int h){return Math.max(l,Math.min(h,v));}
 static void show(LauncherActivity a){
  a.controller.closeShelf();
  FrameLayout overlay=new FrameLayout(a);
  overlay.setBackgroundColor(0xE5101115);
  overlay.setOnClickListener(v->a.controller.closeShelf());
  LinearLayout panel=new LinearLayout(a);
  panel.setOrientation(LinearLayout.VERTICAL);panel.setClickable(true);
  panel.setBackground(LauncherUi.bg(a,0xFC1C1D23,31));
  FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-1);
  pp.topMargin=LauncherUi.dp(a,56);overlay.addView(panel,pp);

  View handle=new View(a);handle.setBackground(LauncherUi.bg(a,0xAC9C9DA4,5));
  LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(LauncherUi.dp(a,38),LauncherUi.dp(a,5));
  hp.gravity=Gravity.CENTER_HORIZONTAL;hp.topMargin=LauncherUi.dp(a,11);panel.addView(handle,hp);
  LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);
  bar.setPadding(LauncherUi.dp(a,23),LauncherUi.dp(a,18),LauncherUi.dp(a,21),LauncherUi.dp(a,9));
  TextView heading=LauncherUi.text(a,"Widgets",30,WHITE,true);
  heading.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  bar.addView(heading,new LinearLayout.LayoutParams(0,LauncherUi.dp(a,51),1f));
  TextView done=LauncherUi.text(a,"Done",16,BLUE,true);
  bar.addView(done,new LinearLayout.LayoutParams(LauncherUi.dp(a,65),LauncherUi.dp(a,49)));
  done.setOnClickListener(v->a.controller.closeShelf());
  panel.addView(bar);
  TextView explanation=LauncherUi.text(a,"Choose a widget and its size",14,DIM,false);
  explanation.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  explanation.setPadding(LauncherUi.dp(a,25),0,0,0);
  panel.addView(explanation,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,37)));

  EditText search=new EditText(a);
  search.setSingleLine(true);search.setTextSize(16);search.setTextColor(WHITE);
  search.setHintTextColor(DIM);search.setHint("Search Widgets");
  search.setPadding(LauncherUi.dp(a,15),0,LauncherUi.dp(a,15),0);
  search.setBackground(LauncherUi.bg(a,0xFF35363E,14));
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,49));
  sp.setMargins(LauncherUi.dp(a,21),LauncherUi.dp(a,6),LauncherUi.dp(a,21),LauncherUi.dp(a,14));
  panel.addView(search,sp);

  GalleryAdapter adapter=new GalleryAdapter(a);
  ListView list=new ListView(a);
  list.setBackgroundColor(Color.TRANSPARENT);list.setDivider(null);
  list.setSelector(new ColorDrawable(Color.TRANSPARENT));
  list.setVerticalScrollBarEnabled(false);
  list.setPadding(LauncherUi.dp(a,11),0,LauncherUi.dp(a,11),LauncherUi.dp(a,27));
  list.setClipToPadding(false);list.setAdapter(adapter);
  panel.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){
    adapter.filter(s.toString());list.setSelection(0);
   }
   public void afterTextChanged(Editable s){}
  });
  a.root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
  a.shelf=overlay;
  panel.setTranslationY(LauncherUi.dp(a,95));panel.setAlpha(.6f);
  panel.animate().translationY(0).alpha(1f).setDuration(280).start();
 }
 static class GalleryAdapter extends BaseAdapter {
  final LauncherActivity a;
  final ArrayList<AppWidgetProviderInfo> all=new ArrayList<>(),shown=new ArrayList<>();
  GalleryAdapter(LauncherActivity a){
   this.a=a;
   try{all.addAll(a.widgets.manager.getInstalledProviders());}catch(Exception ignored){}
   all.sort((l,r)->title(a,l).compareToIgnoreCase(title(a,r)));
   filter("");
  }
  void filter(String q){
   shown.clear();String key=q.trim().toLowerCase(Locale.getDefault());
   for(AppWidgetProviderInfo w:all){
    if(key.isEmpty()||title(a,w).toLowerCase(Locale.getDefault()).contains(key)||
      owner(a,w).toLowerCase(Locale.getDefault()).contains(key))shown.add(w);
   }notifyDataSetChanged();
  }
  public int getCount(){return shown.size();}
  public AppWidgetProviderInfo getItem(int p){return shown.get(p);}
  public long getItemId(int p){return getItem(p).provider.hashCode();}
  public View getView(int position,View old,android.view.ViewGroup parent){
   AppWidgetProviderInfo w=getItem(position);
   LinearLayout outer=new LinearLayout(a);outer.setOrientation(LinearLayout.VERTICAL);
   outer.setPadding(LauncherUi.dp(a,9),LauncherUi.dp(a,5),LauncherUi.dp(a,9),LauncherUi.dp(a,5));
   LinearLayout card=new LinearLayout(a);card.setGravity(Gravity.CENTER_VERTICAL);
   card.setPadding(LauncherUi.dp(a,12),LauncherUi.dp(a,10),LauncherUi.dp(a,13),LauncherUi.dp(a,10));
   card.setBackground(LauncherUi.bg(a,0xFF32333B,20));
   outer.addView(card,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,115)));
   FrameLayout previewBox=new FrameLayout(a);
   previewBox.setBackground(LauncherUi.bg(a,0xFF23242B,12));
   LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(LauncherUi.dp(a,115),LauncherUi.dp(a,89));
   pb.rightMargin=LauncherUi.dp(a,14);card.addView(previewBox,pb);
   ImageView graphic=new ImageView(a);
   Drawable real=preview(a,w);
   graphic.setImageDrawable(real!=null?real:icon(a,w));
   graphic.setScaleType(ImageView.ScaleType.FIT_CENTER);
   FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(-1,-1);
   int inset=LauncherUi.dp(a,real!=null?6:24);
   gp.setMargins(inset,inset,inset,inset);previewBox.addView(graphic,gp);
   LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
   body.setGravity(Gravity.CENTER_VERTICAL);card.addView(body,new LinearLayout.LayoutParams(0,-1,1f));
   TextView name=LauncherUi.text(a,title(a,w),16,WHITE,true);name.setGravity(Gravity.LEFT);
   name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
   body.addView(name);
   TextView owner=LauncherUi.text(a,owner(a,w),12,DIM,false);owner.setGravity(Gravity.LEFT);
   LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.topMargin=LauncherUi.dp(a,5);
   body.addView(owner,op);
   List<Size> opts=sizes(a,w);
   TextView sizes=LauncherUi.text(a,opts.get(0).grid()+
       (opts.size()==1?"":"  •  "+opts.size()+" sizes"),12,0xFF91B9FF,false);
   sizes.setGravity(Gravity.LEFT);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
   np.topMargin=LauncherUi.dp(a,5);body.addView(sizes,np);
   ImageView arrow=new ImageView(a);arrow.setImageResource(R.drawable.f7_chevron);
   arrow.setColorFilter(DIM);LinearLayout.LayoutParams arrowp=new LinearLayout.LayoutParams(
      LauncherUi.dp(a,15),LauncherUi.dp(a,15));arrowp.leftMargin=LauncherUi.dp(a,5);
   card.addView(arrow,arrowp);
   card.setOnClickListener(v->details(a,w));
   return outer;
  }
 }
 static void details(LauncherActivity a,AppWidgetProviderInfo info){
  List<Size> options=sizes(a,info);
  final int[] selected={0};
  Dialog dialog=new Dialog(a,android.R.style.Theme_Translucent_NoTitleBar);
  LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(LauncherUi.dp(a,20),LauncherUi.dp(a,18),
      LauncherUi.dp(a,20),LauncherUi.dp(a,24));
  panel.setBackground(LauncherUi.bg(a,0xFF272830,30));
  LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);
  TextView back=LauncherUi.text(a,"‹  Widgets",16,0xFF91B9FF,false);
  back.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  bar.addView(back,new LinearLayout.LayoutParams(0,LauncherUi.dp(a,45),1));
  TextView name=LauncherUi.text(a,title(a,info),18,WHITE,true);
  name.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
  bar.addView(name,new LinearLayout.LayoutParams(0,LauncherUi.dp(a,45),2));
  panel.addView(bar);back.setOnClickListener(v->dialog.dismiss());

  FrameLayout previewBox=new FrameLayout(a);
  previewBox.setBackground(LauncherUi.bg(a,0xFF373840,23));
  LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,222));
  pp.topMargin=LauncherUi.dp(a,13);panel.addView(previewBox,pp);
  TextView sizeInfo=LauncherUi.text(a,"",15,DIM,false);
  panel.addView(sizeInfo,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,50)));
  LinearLayout choiceBar=new LinearLayout(a);
  choiceBar.setGravity(Gravity.CENTER);
  panel.addView(choiceBar,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,63)));
  final Runnable[] refresh=new Runnable[1];
  refresh[0]=()->{
   Size sz=options.get(selected[0]);
   sizeInfo.setText(sz.name+"    "+sz.grid());
   previewBox.removeAllViews();
   ImageView previewImage=new ImageView(a);
   Drawable real=preview(a,info);
   previewImage.setImageDrawable(real!=null?real:icon(a,info));
   previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
   int width=LauncherUi.dp(a,Math.min(295,85*sz.x));
   int height=LauncherUi.dp(a,Math.min(188,44*sz.y));
   if(real==null){width=Math.min(width,LauncherUi.dp(a,88));height=Math.min(height,LauncherUi.dp(a,88));}
   previewBox.addView(previewImage,new FrameLayout.LayoutParams(width,height,Gravity.CENTER));
   choiceBar.removeAllViews();
   for(int i=0;i<options.size();i++){
    final int id=i;Size option=options.get(i);
    TextView chip=LauncherUi.text(a,option.name+"\n"+option.grid(),13,
          i==selected[0]?WHITE:DIM,i==selected[0]);
    chip.setGravity(Gravity.CENTER);
    chip.setBackground(LauncherUi.bg(a,i==selected[0]?BLUE:0xFF41424C,12));
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,LauncherUi.dp(a,57),1);
    if(i>0)cp.leftMargin=LauncherUi.dp(a,6);
    choiceBar.addView(chip,cp);
    chip.setOnClickListener(v->{selected[0]=id;refresh[0].run();});
   }
  };
  refresh[0].run();

  TextView add=LauncherUi.text(a,"Add Widget",17,WHITE,true);
  add.setGravity(Gravity.CENTER);
  add.setBackground(LauncherUi.bg(a,BLUE,15));
  LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,55));
  ap.topMargin=LauncherUi.dp(a,18);panel.addView(add,ap);
  add.setOnClickListener(v->{
   Size chosen=options.get(selected[0]);
   dialog.dismiss();a.controller.closeShelf();
   a.widgets.beginAdd(info,chosen.x,chosen.y);
  });
  dialog.setContentView(panel);
  Window window=dialog.getWindow();
  if(window!=null){
   window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
   window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   window.setDimAmount(.6f);window.setGravity(Gravity.BOTTOM);
  }
  dialog.show();if(window!=null)window.setLayout(-1,-2);
 }
}
