package com.eelauncber.launcher;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;
final class CupertinoNotice {
 static void show(LauncherActivity a,String text){
  Toast toast=new Toast(a);
  TextView v=LauncherUi.text(a,text,14,0xffffffff,true);
  v.setPadding(LauncherUi.dp(a,22),LauncherUi.dp(a,14),LauncherUi.dp(a,22),LauncherUi.dp(a,14));
  v.setBackground(LauncherUi.bg(a,0xee27354a,19));
  toast.setView(v);toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,LauncherUi.dp(a,130));
  toast.setDuration(Toast.LENGTH_SHORT);toast.show();
 }
}
