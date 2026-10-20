package com.eelauncber.launcher;
import android.app.AlertDialog;
import android.widget.EditText;

final class FolderSheet {
    static void open(LauncherActivity a,HomeState.Item item){
        String[] titles=new String[item.apps.size()];
        for(int i=0;i<titles.length;i++){
            AppCatalog.Entry e=a.catalog.get(item.apps.get(i));
            titles[i]=e==null?"App":e.label;
        }
        new AlertDialog.Builder(a)
            .setTitle(item.name.isEmpty()?"Folder":item.name)
            .setItems(titles,(dialog,index)->LauncherActions.launch(a,item.apps.get(index)))
            .setNeutralButton("Rename",(dialog,index)->rename(a,item))
            .setNegativeButton("Ungroup",(dialog,index)->{
                a.home.pages.remove(item);
                a.home.dock.remove(item);
                for(String key:item.apps)a.home.pages.add(new HomeState.Item(key));
                LauncherActions.save(a);
            })
            .setPositiveButton("Close",null).show();
    }
    static void rename(LauncherActivity a,HomeState.Item item){
        EditText field=new EditText(a);
        field.setSingleLine(true);field.setText(item.name);
        new AlertDialog.Builder(a).setTitle("Folder name").setView(field)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(dialog,index)->{
                item.name=field.getText().toString().trim();
                LauncherActions.save(a);
            }).show();
    }
}
