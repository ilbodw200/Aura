package app.aura.android;

import android.app.Dialog;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Android's quiet home screen. Connection state always comes from the VPN service. */
final class MinimalDashboard {
    interface Actions {
        void connect(); void subscriptions(); void tools(); void ping(); void add();
        void select(JSONObject profile); void options(JSONObject profile); void measure(List<JSONObject> profiles);
    }
    private final MainActivity activity;
    private final Ui ui;
    private final JSONObject state;
    private final Actions actions;
    private final String group;
    private final JSONObject selected;
    final TextView timer;
    final LinearLayout view;
    private final boolean motion;

    MinimalDashboard(MainActivity activity, Ui ui, JSONObject state, String group, String status, boolean active, Actions actions) throws Exception {
        this.activity=activity;this.ui=ui;this.state=state;this.group=group;this.actions=actions;
        motion=!state.optBoolean("reduceMotion") && (Build.VERSION.SDK_INT<26 || android.animation.ValueAnimator.areAnimatorsEnabled());
        view=ui.column();view.setPadding(ui.dp(18),ui.dp(12),ui.dp(18),ui.dp(12));
        LinearLayout top=ui.line();
        JSONObject subscription=Core.find(state.getJSONArray("subscriptions"),group);
        TextView title=chip(subscription==null?"Все подключения  ⌄":subscription.optString("name")+"  ⌄",actions::subscriptions);
        title.setGravity(Gravity.CENTER_VERTICAL);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setContentDescription("Выбрать подписку");
        top.addView(title,new LinearLayout.LayoutParams(0,ui.dp(50),1));
        TextView ping=chip("◴",actions::ping);ping.setContentDescription("Настройки пинга");addTool(top,ping);
        TextView settings=chip("⚙",actions::tools);settings.setContentDescription("Меню AURA");addTool(top,settings);ui.add(view,top);

        LinearLayout centre=ui.column();centre.setGravity(Gravity.CENTER);
        PowerBadge badge=new PowerBadge(ui,active,AuraVpnService.status.equals("connecting"));
        badge.setContentDescription(active?"Отключить VPN":"Подключить VPN");badge.setFocusable(true);
        badge.setOnClickListener(v->{if(motion){v.animate().scaleX(.94f).scaleY(.94f).setDuration(90).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(160).start()).start();}actions.connect();});
        int badgeSize=activity.getResources().getConfiguration().screenHeightDp<520?120:200;
        centre.addView(badge,new LinearLayout.LayoutParams(ui.dp(badgeSize),ui.dp(badgeSize)));
        ui.space(centre,24);TextView statusLabel=ui.title(status,22);statusLabel.setGravity(Gravity.CENTER);statusLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);ui.add(centre,statusLabel);
        timer=ui.text(active?"":"Нажмите для подключения",13,ui.muted);timer.setGravity(Gravity.CENTER);ui.space(centre,8);ui.add(centre,timer);
        view.addView(centre,new LinearLayout.LayoutParams(-1,0,1));
        selected=Core.selected(state);
        View server=selected==null?chip("＋ Добавить подключение",actions::add):serverTile(selected,false,()->showServers());
        server.setContentDescription("Открыть серверы");ui.add(view,server);
        ui.space(view,10);TextView brand=ui.text("AURA",10,ui.muted);brand.setLetterSpacing(.24f);brand.setGravity(Gravity.CENTER);ui.add(view,brand);
    }
    private TextView chip(String text,Runnable action){TextView v=ui.button(text,action);v.setTextColor(ui.fg);v.setBackground(ui.rounded(ui.light?0xeaffffff:0xff242832,0,24));return v;}
    private void addTool(LinearLayout row,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(50),ui.dp(50));lp.leftMargin=ui.dp(8);row.addView(v,lp);}
    private List<JSONObject> profiles(){List<JSONObject> result=new ArrayList<>();JSONArray all=state.optJSONArray("profiles");if(all!=null)for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null&&(group.isEmpty()||group.equals(p.optString("subscription"))))result.add(p);}return result;}
    private String protocol(JSONObject p){JSONObject q=p.optJSONObject("query");String transport=q==null?"TCP":q.optString("type","tcp").toUpperCase(Locale.ROOT);return q!=null&&q.optString("security").equals("reality")?"REALITY":p.optString("type").toUpperCase(Locale.ROOT)+" · "+transport;}
    private String latency(JSONObject p){if(p.optBoolean("pingError"))return "Нет ответа";if(!p.has("latency"))return "—";JSONObject settings=state.optJSONObject("ping");return settings!=null&&settings.optBoolean("displayStatus")&&p.optInt("pingStatus")>0?"HTTP "+p.optInt("pingStatus"):p.optInt("latency")+" мс";}
    private String flag(String name){int[] points=name.codePoints().toArray();for(int i=0;i+1<points.length;i++)if(points[i]>=0x1f1e6&&points[i]<=0x1f1ff&&points[i+1]>=0x1f1e6&&points[i+1]<=0x1f1ff)return new String(points,i,2);return "◎";}
    private View serverTile(JSONObject p,boolean small,Runnable click){
        LinearLayout card=ui.line();card.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));
        boolean selected=p.optString("id").equals(state.optString("selected"));card.setBackground(ui.rounded(selected?ui.alpha(ui.accent,ui.light?40:48):ui.light?0xffe0e4ed:0xff242832,selected?ui.alpha(ui.accent,80):0,22));
        TextView icon=ui.text(flag(p.optString("name")),small?25:30,ui.accent);icon.setGravity(Gravity.CENTER);card.addView(icon,new LinearLayout.LayoutParams(ui.dp(small?34:44),ui.dp(42)));
        LinearLayout labels=ui.column();labels.setPadding(ui.dp(8),0,0,0);
        TextView title=ui.title(p.optString("name"),small?13:15);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);ui.add(labels,title);
        LinearLayout detail=ui.line();TextView protocol=ui.text(protocol(p),11,ui.muted);protocol.setSingleLine(true);protocol.setEllipsize(TextUtils.TruncateAt.END);detail.addView(protocol,new LinearLayout.LayoutParams(0,-2,1));
        TextView ping=ui.text(latency(p),11,p.optBoolean("pingError")?ui.muted:ui.accent);detail.addView(ping);ui.add(labels,detail);
        card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));card.setOnClickListener(v->click.run());card.setOnLongClickListener(v->{actions.options(p);return true;});card.setFocusable(true);card.setContentDescription(p.optString("name")+", "+protocol(p)+", "+latency(p)+(selected?", выбран":""));card.setTag("server."+p.optString("id"));return card;
    }
    void showServers(){
        Dialog dialog=new Dialog(activity);LinearLayout sheet=ui.column();sheet.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));sheet.setBackground(ui.rounded(ui.light?0xfff0f2f7:0xff171b24,0,28));
        View handle=new View(activity);handle.setBackground(ui.rounded(ui.alpha(ui.muted,110),0,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(ui.dp(36),ui.dp(4));hp.gravity=Gravity.CENTER;hp.bottomMargin=ui.dp(12);sheet.addView(handle,hp);
        LinearLayout header=ui.line();TextView heading=ui.title("Серверы",20);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));header.addView(chip("Готово",dialog::dismiss));ui.add(sheet,header);ui.space(sheet,10);
        EditText search=ui.input("Поиск серверов","");search.setSingleLine(true);search.setContentDescription("Поиск серверов");search.setBackground(ui.rounded(ui.alpha(ui.muted,24),0,22));search.setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10));ui.add(sheet,search);ui.space(sheet,12);
        if(selected!=null){View current=serverTile(selected,false,()->{dialog.dismiss();actions.select(selected);});current.setOnLongClickListener(v->{dialog.dismiss();actions.options(selected);return true;});ui.add(sheet,current);ui.space(sheet,12);}
        List<JSONObject> all=profiles();TextView count=ui.text("Основные · "+all.size(),12,ui.muted);ui.add(sheet,count);ui.space(sheet,10);
        GridView grid=new GridView(activity);grid.setTag("server.grid");grid.setNumColumns(activity.getResources().getConfiguration().screenWidthDp<350||activity.getResources().getConfiguration().fontScale>1.25f?1:2);grid.setHorizontalSpacing(ui.dp(8));grid.setVerticalSpacing(ui.dp(8));grid.setClipToPadding(false);grid.setPadding(0,0,0,ui.dp(12));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        List<JSONObject> visible=new ArrayList<>(all);
        BaseAdapter adapter=new BaseAdapter(){public int getCount(){return visible.size();}public JSONObject getItem(int position){return visible.get(position);}public long getItemId(int position){return position;}public View getView(int position,View convert,ViewGroup parent){JSONObject p=getItem(position);View tile=serverTile(p,true,()->{dialog.dismiss();actions.select(p);});tile.setOnLongClickListener(v->{dialog.dismiss();actions.options(p);return true;});return tile;}};
        grid.setAdapter(adapter);sheet.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        TextView empty=ui.text(all.isEmpty()?"Добавьте подписку или сервер":"Ничего не найдено",14,ui.muted);empty.setGravity(Gravity.CENTER);ui.add(sheet,empty);empty.setVisibility(all.isEmpty()?View.VISIBLE:View.GONE);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int after){String q=s.toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(JSONObject p:all)if((p.optString("name")+" "+protocol(p)+" "+p.optString("host")).toLowerCase(Locale.ROOT).contains(q))visible.add(p);adapter.notifyDataSetChanged();count.setText("Основные · "+visible.size());empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);}});
        LinearLayout tools=ui.line();tools.addView(chip("＋ Добавить",()->{dialog.dismiss();actions.add();}),new LinearLayout.LayoutParams(0,-2,1));tools.addView(chip("◴ Проверить пинг",()->{dialog.dismiss();actions.measure(new ArrayList<>(visible));}),new LinearLayout.LayoutParams(0,-2,1));ui.add(sheet,tools);
        dialog.setContentView(sheet);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.35f);window.setGravity(Gravity.BOTTOM);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(motion)window.setWindowAnimations(R.style.AuraSheetAnimation);}
        dialog.show();if(window!=null){int height=activity.getResources().getDisplayMetrics().heightPixels;window.setLayout(-1,(int)(height*.86));}sheet.setFocusableInTouchMode(true);sheet.requestFocus();
    }
    private static final class PowerBadge extends View {
        private final Ui ui;private final boolean active,connecting;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
        PowerBadge(Ui ui,boolean active,boolean connecting){super(ui.context);this.ui=ui;this.active=active;this.connecting=connecting;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.76f;path.reset();for(int i=0;i<=160;i++){double angle=2*Math.PI*i/160;float radius=r*(1+.045f*(float)Math.cos(angle*8));float px=x+radius*(float)Math.cos(angle),py=y+radius*(float)Math.sin(angle);if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();paint.setStyle(Paint.Style.FILL);paint.setColor(ui.alpha(ui.accent,active?90:42));c.drawPath(path,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ui.dp(1));paint.setColor(ui.alpha(ui.accent,active?150:50));c.drawPath(path,paint);paint.setColor(ui.accent);paint.setStrokeWidth(ui.dp(3));paint.setStrokeCap(Paint.Cap.ROUND);float s=r*.28f;c.drawArc(new RectF(x-s,y-s,x+s,y+s),-48,276,false,paint);c.drawLine(x,y-s*1.28f,x,y-s*.15f,paint);if(connecting){paint.setStrokeWidth(ui.dp(2));paint.setColor(ui.alpha(ui.accent,140));c.drawArc(new RectF(x-r*1.15f,y-r*1.15f,x+r*1.15f,y+r*1.15f),-90,100,false,paint);}}
    }
}
