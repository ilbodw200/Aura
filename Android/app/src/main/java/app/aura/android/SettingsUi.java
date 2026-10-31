package app.aura.android;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

final class SettingsUi {
    final Ui ui;
    SettingsUi(Ui ui){this.ui=ui;}
    int surface(){return ui.light?0xffffffff:0xff242832;}
    LinearLayout group(LinearLayout parent,String title){ui.space(parent,12);TextView label=ui.caption(title);label.setPadding(ui.dp(10),0,0,ui.dp(10));ui.add(parent,label);LinearLayout group=ui.column();group.setBackground(ui.rounded(surface(),0,24));ui.add(parent,group);ui.space(parent,10);return group;}
    void note(LinearLayout parent,String text){TextView v=ui.text(text,12,ui.muted);v.setPadding(ui.dp(10),ui.dp(2),ui.dp(10),ui.dp(10));v.setLineSpacing(ui.dp(3),1);ui.add(parent,v);}
    void divider(LinearLayout group){View line=new View(ui.context);line.setBackgroundColor(ui.alpha(ui.muted,30));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(1));lp.setMargins(ui.dp(16),0,ui.dp(16),0);group.addView(line,lp);}
    View row(LinearLayout group,String icon,String title,String detail,String value,Runnable action){
        LinearLayout row=ui.line();row.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));row.setMinimumHeight(ui.dp(64));
        if(icon!=null){ImageView image=new ImageView(ui.context);image.setImageDrawable(new AuraIcon(icon,ui.accent));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24));ip.rightMargin=ui.dp(14);row.addView(image,ip);}
        LinearLayout labels=ui.column();ui.add(labels,ui.title(title,15));if(detail!=null&&!detail.isEmpty()){TextView description=ui.text(detail,12,ui.muted);description.setPadding(0,ui.dp(4),0,0);ui.add(labels,description);}row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(value!=null){TextView current=ui.text(value,13,ui.accent);current.setPadding(ui.dp(10),0,ui.dp(8),0);row.addView(current);}
        if(action!=null){ImageView arrow=new ImageView(ui.context);arrow.setImageDrawable(new AuraIcon("chevron",ui.muted));row.addView(arrow,new LinearLayout.LayoutParams(ui.dp(18),ui.dp(18)));row.setBackground(new RippleDrawable(ColorStateList.valueOf(ui.alpha(ui.accent,35)),null,ui.rounded(Color.WHITE,0,24)));row.setOnClickListener(v->action.run());row.setFocusable(true);row.setContentDescription(title+(value==null?"":", "+value));}
        ui.add(group,row);return row;
    }
    Switch toggle(LinearLayout group,String label,boolean checked,Consumer<Boolean> change){Switch v=ui.toggle(label,checked,change);v.setPadding(ui.dp(16),ui.dp(6),ui.dp(16),ui.dp(6));ui.add(group,v);return v;}
    Choice choices(LinearLayout group,String tag,String[] values,String[] labels,String[] details,String selected,Consumer<String> change){return new Choice(group,tag,values,labels,details,selected,change);}
    final class Choice {
        String selected;final ImageView[] checks;final LinearLayout group;
        Choice(LinearLayout group,String tag,String[] values,String[] labels,String[] details,String selected,Consumer<String> change){this.selected=selected;this.group=group;checks=new ImageView[values.length];
            for(int i=0;i<values.length;i++){final int index=i;if(i>0)divider(group);LinearLayout row=ui.line();row.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));row.setMinimumHeight(ui.dp(56));LinearLayout text=ui.column();ui.add(text,ui.title(labels[i],15));if(details!=null){TextView detail=ui.text(details[i],12,ui.muted);detail.setPadding(0,ui.dp(4),ui.dp(10),0);ui.add(text,detail);}row.addView(text,new LinearLayout.LayoutParams(0,-2,1));ImageView check=new ImageView(ui.context);check.setImageDrawable(new AuraIcon("check",ui.accent));check.setVisibility(values[i].equals(selected)?View.VISIBLE:View.INVISIBLE);checks[i]=check;row.addView(check,new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));row.setTag(tag+"."+values[i]);row.setContentDescription(labels[i]);row.setFocusable(true);row.setSelected(values[i].equals(selected));row.setBackground(new RippleDrawable(ColorStateList.valueOf(ui.alpha(ui.accent,30)),null,ui.rounded(Color.WHITE,0,20)));row.setOnClickListener(v->{this.selected=values[index];for(int j=0;j<checks.length;j++){checks[j].setVisibility(j==index?View.VISIBLE:View.INVISIBLE);((View)checks[j].getParent()).setSelected(j==index);}change.accept(this.selected);});ui.add(group,row);}
        }
        void enabled(boolean enabled){group.setAlpha(enabled?1:.4f);for(int i=0;i<group.getChildCount();i++)group.getChildAt(i).setEnabled(enabled);}
    }
    final class Page {
        final Dialog dialog;final LinearLayout body;final TextView save,error;
        Page(String title){dialog=new Dialog(ui.context,android.R.style.Theme_Material_NoActionBar);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout root=ui.column();root.setBackgroundColor(ui.bg);root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
            LinearLayout header=ui.line();header.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));header.addView(ui.iconButton("back","Назад",dialog::dismiss),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));TextView heading=ui.title(title,19);heading.setPadding(ui.dp(12),0,ui.dp(4),0);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));save=ui.button("Сохранить",()->{});save.setTextSize(13);save.setTag("ping.save");header.addView(save);ui.add(root,header);
            error=ui.text("",13,ui.light?0xffa02d30:0xffffa5a5);error.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),ui.dp(8));error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);ui.add(root,error);
            ScrollView scroll=new ScrollView(ui.context);scroll.setFillViewport(true);body=ui.column();body.setPadding(ui.dp(18),ui.dp(6),ui.dp(18),ui.dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(root);
        }
        void show(){dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setStatusBarColor(ui.bg);w.setNavigationBarColor(ui.bg);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.getDecorView().setSystemUiVisibility(ui.light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);w.getDecorView().requestApplyInsets();}}
    }
}
