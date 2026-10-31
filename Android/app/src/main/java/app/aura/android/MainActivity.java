package app.aura.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.integration.android.*;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private String homeGroup="";
    private JSONObject state;private Vault vault;private boolean storageReady,busy;private int tab;private Ui ui;private LinearLayout body;private TextView statusText,timerText;
    private final Set<String> collapsedGroups=new HashSet<>();private boolean collapseAll=false,batchProbing=false;private volatile boolean cancelBatch=false;
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();private final Handler handler=new Handler(Looper.getMainLooper());private byte[] exportBytes;private String restorePassword="";
    private final SharedPreferences.OnSharedPreferenceChangeListener runtimeListener=(p,key)->{if(key.equals("revision"))runOnUiThread(()->{render();if(!AuraVpnService.lastError.isEmpty()){String error=AuraVpnService.lastError;AuraVpnService.lastError="";message(error);}});};
    interface Action {void run()throws Exception;}
    private void safe(Action action){try{action.run();}catch(Exception e){if(body!=null)render();message(e.getMessage()==null?"Не удалось выполнить действие.":e.getMessage());}}
    private boolean active(){return !AuraVpnService.status.equals("disconnected");}
    private void requireOff()throws Exception{if(active())throw new Exception("Сначала отключите VPN, чтобы изменить конфигурацию.");}
    private void save()throws Exception{if(!storageReady)throw new Exception("Хранилище недоступно; существующие данные не перезаписаны.");vault.save(state);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);vault=new Vault(this);try{state=vault.load();storageReady=true;}catch(Exception e){try{state=Core.defaults();}catch(Exception ignored){}message("Не удалось открыть хранилище. Существующие данные сохранены без изменений.");}
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::navigateBack);
        getSharedPreferences("runtime",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(runtimeListener);render();handler.post(tick);
    }
    private void navigateBack(){if(tab!=0){tab=0;render();}else finish();}
    @Override public void onBackPressed(){navigateBack();}
    private final Runnable tick=new Runnable(){public void run(){if(timerText!=null&&AuraVpnService.status.equals("connected")){long seconds=Math.max(0,(System.currentTimeMillis()-AuraVpnService.connectedAt)/1000);timerText.setText(String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));}handler.postDelayed(this,1000);}};
    @Override protected void onDestroy(){getSharedPreferences("runtime",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(runtimeListener);handler.removeCallbacks(tick);tasks.shutdownNow();restorePassword="";exportBytes=null;super.onDestroy();}
    private int accent(){switch(state.optString("accent")){case "blue":return 0xff75a7ff;case "violet":return 0xffbd90ff;case "rose":return 0xffff8bb2;case "amber":return 0xffffca75;default:return 0xff6eefc5;}}
    private boolean light(){String theme=state.optString("theme","dark");return theme.equals("light")||(theme.equals("system")&&(getResources().getConfiguration().uiMode&48)==16);}
    private void render(){if(isDestroyed()||state==null)return;ui=new Ui(this,accent(),light(),state.optInt("radius",28),state.optBoolean("compact"));timerText=null;
        if(Build.VERSION.SDK_INT>=33)setRecentsScreenshotEnabled(!state.optBoolean("hideRecents",true));else if(state.optBoolean("hideRecents",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=ui.column();int second=state.optString("backdrop").equals("dusk")?0xff312039:state.optString("backdrop").equals("ocean")?0xff102f42:0xff14263b;
        root.setBackground(state.optString("backdrop").equals("minimal")?ui.rounded(ui.bg,0,0):new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{ui.light?0xffd9f0e7:0xff0b2c2c,ui.bg,ui.light?0xffe7e5f4:second}));
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.bg);getWindow().getDecorView().setSystemUiVisibility(ui.light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        if(tab==0&&!state.optBoolean("classicHome")){
            body=root;
            try{
                if(!homeGroup.isEmpty()&&Core.find(state.getJSONArray("subscriptions"),homeGroup)==null)homeGroup="";
                MinimalDashboard dashboard=new MinimalDashboard(this,ui,state,homeGroup,statusTitle(),active(),new MinimalDashboard.Actions(){
                    public void connect(){safe(MainActivity.this::toggleVPN);}
                    public void subscriptions(){safe(MainActivity.this::homeSubscriptions);}
                    public void tools(){homeTools();}
                    public void ping(){safe(MainActivity.this::pingSettings);}
                    public void add(){importDialog("");}
                    public void select(JSONObject profile){safe(()->MainActivity.this.select(profile));}
                    public void options(JSONObject profile){profileMenu(profile);}
                    public void measure(List<JSONObject> profiles){safe(()->measureGroup(profiles));}
                });
                timerText=dashboard.timer;root.addView(dashboard.view,new LinearLayout.LayoutParams(-1,0,1));
            }catch(Exception e){ui.add(root,button("Открыть подключения",()->{tab=1;render();}));}
            setContentView(root);root.requestApplyInsets();return;
        }
        if(tab==4){
            root.setBackgroundColor(ui.bg);LinearLayout header=ui.line();header.setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10));header.addView(ui.iconButton("back","На главный экран",()->{tab=0;render();}),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));TextView title=ui.title("Настройки",22);title.setPadding(ui.dp(14),0,0,0);header.addView(title);ui.add(root,header);
            ScrollView settingsScroll=new ScrollView(this);body=ui.column();body.setPadding(ui.dp(18),0,ui.dp(18),ui.dp(24));settingsScroll.addView(body);root.addView(settingsScroll,new LinearLayout.LayoutParams(-1,0,1));try{settings();}catch(Exception e){ui.add(body,ui.text("Не удалось открыть настройки",14,ui.muted));}setContentView(root);root.requestApplyInsets();return;
        }
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);body=ui.column();body.setPadding(ui.dp(22),ui.dp(18),ui.dp(22),ui.dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout brand=ui.line();TextView logo=ui.title("AURA",18);logo.setLetterSpacing(.15f);brand.addView(logo,new LinearLayout.LayoutParams(0,-2,1));brand.addView(ui.button("QR",this::scan),new LinearLayout.LayoutParams(ui.dp(52),ui.dp(46)));ui.add(body,brand);ui.space(body,24);
        try{switch(tab){case 0:home();break;case 1:profiles();break;case 2:rules();break;case 3:studio();break;default:settings();}}catch(Exception e){ui.add(body,ui.text("Не удалось отобразить данные. Откройте настройки и проверьте резервную копию.",14,ui.muted));}
        LinearLayout nav=ui.line();nav.setPadding(ui.dp(5),ui.dp(9),ui.dp(5),ui.dp(9));nav.setBackground(ui.rounded(ui.light?0xeaffffff:0xcc10222d,0,24));String[] names={"Обзор","Серверы","Правила","Студия","Ещё"},icons={"◉","▤","⑂","◈","⚙"};
        for(int i=0;i<5;i++){final int index=i;TextView b=ui.text(icons[i]+"\n"+names[i],12,i==tab?ui.accent:ui.muted);b.setGravity(Gravity.CENTER);b.setMinHeight(ui.dp(54));b.setContentDescription(names[i]);b.setFocusable(true);if(i==tab)b.setBackground(ui.rounded(ui.alpha(ui.accent,20),0,18));b.setOnClickListener(v->{tab=index;render();});nav.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.setMargins(ui.dp(12),0,ui.dp(12),ui.dp(8));root.addView(nav,np);setContentView(root);root.requestApplyInsets();
    }
    private TextView button(String title,Action action){return ui.button(title,()->safe(action));}
    private void homeTools(){tab=4;render();}
    private void homeSubscriptions()throws Exception{
        JSONArray subs=state.getJSONArray("subscriptions");String[] labels=new String[subs.length()+3];labels[0]="Все подключения";
        for(int i=0;i<subs.length();i++)labels[i+1]=subs.getJSONObject(i).optString("name");labels[labels.length-2]="＋ Добавить подписку";labels[labels.length-1]="Управление подписками";
        new AlertDialog.Builder(this).setTitle("Подписка").setItems(labels,(d,index)->safe(()->{if(index==labels.length-2){importDialog("");return;}if(index==labels.length-1){tab=1;render();return;}homeGroup=index==0?"":subs.getJSONObject(index-1).getString("id");render();})).show();
    }
    private void heading(String kicker,String title){ui.add(body,ui.caption(kicker.toUpperCase(Locale.ROOT)));ui.space(body,8);ui.add(body,ui.title(title,29));ui.space(body,22);}
    private LinearLayout info(LinearLayout parent,String title,String detail){LinearLayout card=ui.card(parent);ui.add(card,ui.title(title,16));if(!detail.isEmpty()){ui.space(card,7);ui.add(card,ui.text(detail,12,ui.muted));}return card;}
    private void home()throws Exception{
        LinearLayout tools=ui.line();tools.addView(button("⚙ Пинг",this::pingSettings),new LinearLayout.LayoutParams(0,-2,1));tools.addView(button("＋ Добавить",()->importDialog("")),new LinearLayout.LayoutParams(0,-2,1));ui.add(body,tools);
        FrameLayout orbFrame=new FrameLayout(this);Ui.Orb orb=new Ui.Orb(ui,active());orbFrame.addView(orb,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ui.dp(268),ui.dp(268));op.gravity=Gravity.CENTER;body.addView(orbFrame,op);orb.setOnClickListener(v->safe(this::toggleVPN));
        LinearLayout labels=ui.column();statusText=ui.title(statusTitle(),13);statusText.setGravity(Gravity.CENTER);ui.add(labels,statusText);timerText=ui.text(active()?"":"AURA 1.0",12,ui.muted);timerText.setGravity(Gravity.CENTER);ui.space(labels,5);ui.add(labels,timerText);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(ui.dp(150),-2,Gravity.CENTER);labels.setTranslationY(ui.dp(34));orbFrame.addView(labels,lp);ui.space(body,12);
        LinearLayout quick=ui.line();quick.addView(button(busy?"Проверяем…":"Проверить соединение",()->{JSONObject profile=Core.selected(state);if(profile==null)throw new Exception("Выберите сервер.");measure(profile);}),new LinearLayout.LayoutParams(0,-2,1));quick.addView(button(collapseAll?"Показать все":"Скрыть все",()->{collapseAll=!collapseAll;collapsedGroups.clear();if(collapseAll){collapsedGroups.add("local");JSONArray subs=state.getJSONArray("subscriptions");for(int i=0;i<subs.length();i++)collapsedGroups.add(subs.getJSONObject(i).getString("id"));}render();}),new LinearLayout.LayoutParams(0,-2,1));ui.add(body,quick);ui.space(body,12);
        JSONObject ping=Ping.settings(state);ui.add(body,ui.text("Пинг: "+ping.getString("method")+" · "+ping.getInt("timeout")+" с",12,ui.muted));ui.space(body,12);
        if(batchProbing)ui.add(body,button(cancelBatch?"Останавливаем…":"Остановить после текущего теста",()->cancelBatch=true));
        JSONArray subs=state.getJSONArray("subscriptions");if(state.getJSONArray("profiles").length()==0&&subs.length()==0)info(body,"Добавьте первое подключение","Нажмите + для импорта подписки, ссылки сервера или QR-кода.");
        JSONArray widgets=state.getJSONArray("widgets"),hidden=state.getJSONArray("hiddenWidgets");for(int i=0;i<widgets.length();i++){String widget=widgets.getString(i);if(contains(hidden,widget))continue;
            if(widget.equals("server")){serverGroup("local","Мои серверы",null);for(int j=0;j<subs.length();j++){JSONObject sub=subs.getJSONObject(j);serverGroup(sub.getString("id"),sub.getString("name"),sub);}}
            if(widget.equals("routing"))info(body,"Маршрутизация",modeTitle()+" · "+state.getJSONArray("rules").length()+" правил");
            if(widget.equals("favorites")){JSONArray ps=state.getJSONArray("profiles");LinearLayout c=null;for(int j=0;j<ps.length();j++){JSONObject profile=ps.getJSONObject(j);if(profile.optBoolean("favorite")){if(c==null)c=info(body,"Избранное","");ui.add(c,button("★ "+profile.getString("name"),()->select(profile)));}}}
        }
    }
    private void serverGroup(String id,String title,JSONObject sub)throws Exception{
        List<JSONObject> profiles=new ArrayList<>();JSONArray ps=state.getJSONArray("profiles");for(int i=0;i<ps.length();i++){JSONObject p=ps.getJSONObject(i);if((id.equals("local")&&p.optString("subscription").isEmpty())||p.optString("subscription").equals(id))profiles.add(p);}
        if(sub==null&&profiles.isEmpty())return;
        LinearLayout c=ui.card(body),header=ui.line();header.setBackground(ui.rounded(ui.alpha(ui.accent,25),0,16));
        header.addView(button((collapsedGroups.contains(id)?"›  ":"⌄  ")+title+" · "+profiles.size(),()->{if(!collapsedGroups.add(id))collapsedGroups.remove(id);render();}),new LinearLayout.LayoutParams(0,-2,1));
        if(sub!=null)header.addView(button("↻",()->refresh(sub)),new LinearLayout.LayoutParams(ui.dp(48),-2));
        header.addView(button("◴",()->measureGroup(profiles)),new LinearLayout.LayoutParams(ui.dp(48),-2));ui.add(c,header);
        if(collapsedGroups.contains(id))return;
        if(sub!=null){ui.space(c,10);ui.add(c,ui.text((sub.getString("url").startsWith("https://")?"HTTPS · 🔒":"HTTP · без шифрования")+(sub.has("updated")?" · "+new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.ROOT).format(new Date(sub.optLong("updated"))):""),11,ui.muted));if(sub.has("lastError"))ui.add(c,ui.text(sub.optString("lastError"),12,0xffffbb75));String usage=SubscriptionUsage.summary(sub.optJSONObject("usage"));if(!usage.isEmpty())ui.add(c,ui.text(usage,12,ui.muted));}
        for(JSONObject p:profiles){ui.space(c,10);LinearLayout row=ui.line();String name=(p.getString("id").equals(state.optString("selected"))?"✓ ":"")+p.getString("name");LinearLayout label=ui.column();ui.add(label,ui.title(name,16));ui.add(label,ui.text(p.getString("type").toUpperCase(Locale.ROOT)+" | "+p.optJSONObject("query").optString("type","tcp").toUpperCase(Locale.ROOT),12,ui.muted));label.setOnClickListener(v->safe(()->select(p)));label.setOnLongClickListener(v->{profileMenu(p);return true;});row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            String result=p.has("pingError")?"Нет ответа":p.has("latency")?((Ping.settings(state).optBoolean("displayStatus")&&p.optInt("pingStatus")>0)?"HTTP "+p.optInt("pingStatus"):p.optInt("latency")+" мс")+"\n"+p.optString("pingMethod","TCP"):"Пинг";
            row.addView(button(result,()->measure(p)),new LinearLayout.LayoutParams(ui.dp(92),-2));ui.add(c,row);}
        if(profiles.isEmpty()){ui.space(c,12);ui.add(c,ui.text("Список пуст. Обновите подписку.",12,ui.muted));}
    }
    private String statusTitle(){switch(AuraVpnService.status){case "connected":return "Подключено";case "connecting":return "Подключение…";case "disconnecting":return "Отключение…";default:return "Не подключено";}}
    private void toggleVPN()throws Exception{
        if(active()){startService(new Intent(this,AuraVpnService.class).setAction("STOP"));return;}
        Core.config(state);save();Intent consent=android.net.VpnService.prepare(this);if(consent!=null)startActivityForResult(consent,10);else startVPN();
    }
    private void startVPN(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);startForegroundService(new Intent(this,AuraVpnService.class));}
    private String subtitle(JSONObject p)throws Exception{return p.getString("type").toUpperCase(Locale.ROOT)+(state.optBoolean("showHost",true)?" · "+p.getString("host")+":"+p.getInt("port"):"");}
    private void select(JSONObject p)throws Exception{requireOff();state.put("selected",p.getString("id"));save();render();}
    private void profiles()throws Exception{
        heading("Все ваши подключения","Серверы");ui.add(body,button("＋ Добавить подключение",()->importDialog("")));ui.space(body,18);
        JSONArray subscriptions=state.getJSONArray("subscriptions");for(int i=0;i<subscriptions.length();i++){JSONObject sub=subscriptions.getJSONObject(i);LinearLayout c=info(body,sub.getString("name"),"Подписка · HWID "+(sub.optBoolean("sendHWID",state.optBoolean("sendHWID"))?"включён":"выключен"));ui.add(c,ui.text(sub.getString("url").toLowerCase(Locale.ROOT).startsWith("https://")?"HTTPS · TLS при загрузке":"HTTP · без шифрования",12,ui.muted));if(sub.has("lastError"))ui.add(c,ui.text(sub.optString("lastError"),12,0xffffbb75));ui.add(c,button("Обновить",()->refresh(sub)));ui.space(c,8);ui.add(c,button("Настройки подписки",()->editSubscription(sub)));}
        EditText search=ui.input("Поиск по названию или протоколу","");ui.add(body,search);ui.space(body,14);LinearLayout list=ui.column();ui.add(body,list);renderProfiles(list,"");search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){safe(()->renderProfiles(list,s.toString()));}public void afterTextChanged(Editable e){}});
    }
    private void renderProfiles(LinearLayout list,String query)throws Exception{list.removeAllViews();JSONArray profiles=state.getJSONArray("profiles");boolean any=false;for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);if(!p.getString("name").toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))&&!p.getString("type").contains(query.toLowerCase(Locale.ROOT)))continue;any=true;
            LinearLayout c=info(list,(p.getString("id").equals(state.optString("selected"))?"✓  ":"")+(p.optBoolean("favorite")?"★  ":"")+p.getString("name"),subtitle(p)+(p.has("latency")?" · "+p.getInt("latency")+" ms":""));c.setOnClickListener(v->safe(()->select(p)));c.setOnLongClickListener(v->{profileMenu(p);return true;});ui.add(c,button("Действия",()->profileMenu(p)));}
        if(!any)info(list,"Подключений пока нет","Импортируйте свои серверы. AURA не предоставляет VPN-подписки.");
    }
    private void profileMenu(JSONObject p){new AlertDialog.Builder(this).setTitle(p.optString("name")).setItems(new String[]{"Выбрать","Избранное","Переименовать","Проверить пинг","Показать QR-код","Удалить"},(d,index)->safe(()->{switch(index){case 0:select(p);break;case 1:p.put("favorite",!p.optBoolean("favorite"));save();render();break;case 2:EditText name=ui.input("Название",p.getString("name"));new AlertDialog.Builder(this).setTitle("Название сервера").setView(name).setPositiveButton("Сохранить",(x,y)->safe(()->{if(name.getText().toString().trim().isEmpty())throw new Exception("Введите название.");p.put("name",name.getText().toString().trim());save();render();})).setNegativeButton("Отмена",null).show();break;case 3:measure(p);break;case 4:ImageView image=new ImageView(this);image.setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(20));image.setImageBitmap(new BarcodeEncoder().encodeBitmap(p.getString("source"),BarcodeFormat.QR_CODE,700,700));new AlertDialog.Builder(this).setTitle("Конфигурация сервера").setMessage("QR-код содержит ключ подключения.").setView(image).setPositiveButton("Закрыть",null).show();break;default:requireOff();remove(state.getJSONArray("profiles"),p.getString("id"));ensureSelection();save();render();}})).show();}
    private void scan(){new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt("Наведите камеру на QR-код конфигурации").setBeepEnabled(false).initiateScan();}
    private void importDialog(String initial) {
        LinearLayout form=ui.column(); form.setPadding(ui.dp(20),0,ui.dp(20),0);
        EditText input=ui.input("Ссылка или список конфигураций",initial);
        input.setMinLines(3);input.setMaxLines(7);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);ui.add(form,input);
        EditText name=ui.input("Название подписки","Моя подписка");ui.add(form,name);
        TextView transport=ui.text("",13,ui.muted);ui.add(form,transport);
        Switch approval=ui.toggle("Разрешаю HTTP без шифрования",false,value->{});ui.add(form,approval);
        Switch hwid=ui.toggle("Отправлять HWID провайдеру",state.optBoolean("sendHWID"),value->{});ui.add(form,hwid);
        TextView explanation=ui.text("",12,ui.muted);ui.add(form,explanation);
        java.util.concurrent.atomic.AtomicReference<List<JSONObject>> checked=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> verified=new java.util.concurrent.atomic.AtomicReference<>();
        TextView check=button("Проверить серверы (необязательно)",()->{
            String url=input.getText().toString().trim();boolean plain=url.toLowerCase(Locale.ROOT).startsWith("http://");boolean permit=approval.isChecked();boolean send=hwid.isChecked()&&!plain;
            Core.validateSubscriptionURL(url,permit);
            background(()->Vault.fetch(url,send?DeviceInfo.headers(this):null,permit),profiles->{
                if(!url.equals(input.getText().toString().trim())||permit!=approval.isChecked()||send!=(hwid.isChecked()&&!plain))return;
                checked.set(profiles);verified.set(url);
                explanation.setText("Загружено серверов: "+profiles.size()+(plain?". HTTP остаётся незашифрованным.":". Можно сохранить подписку."));
            });
        });ui.space(form,10);ui.add(form,check);
        ui.space(form,8);ui.add(form,button("Вставить из буфера",()->{android.content.ClipboardManager c=getSystemService(android.content.ClipboardManager.class);if(c.hasPrimaryClip())input.setText(c.getPrimaryClip().getItemAt(0).coerceToText(this));}));
        ui.space(form,8);ui.add(form,button("Открыть текстовый файл",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE),12)));
        Runnable update=()->{
            checked.set(null);verified.set(null);String value=input.getText().toString().trim().toLowerCase(Locale.ROOT);
            boolean secure=value.startsWith("https://")&&!value.contains("\n"),plain=value.startsWith("http://")&&!value.contains("\n");
            transport.setText(secure?"🔒 HTTPS · TLS при загрузке":plain?"HTTP · без шифрования":"Конфигурация сервера");transport.setTextColor(plain?0xffffbb75:ui.accent);
            approval.setVisibility(plain?View.VISIBLE:View.GONE);hwid.setVisibility(secure||plain?View.VISIBLE:View.GONE);hwid.setEnabled(secure);
            check.setVisibility(secure||plain?View.VISIBLE:View.GONE);check.setText(plain?"Обязательная проверка доступности и формата":"Проверить серверы (необязательно)");
            explanation.setText(secure?"HTTPS-подписку можно сохранить сразу. Серверы загрузятся после добавления; сертификат проверяется при загрузке.":plain?"Ссылка и конфигурации передаются без шифрования. Подтвердите HTTP и проверьте ссылку до сохранения. HWID по HTTP не отправляется.":"Ссылки серверов проверяются локально при добавлении.");
        };
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){update.run();}public void afterTextChanged(Editable e){}});
        approval.setOnCheckedChangeListener((b,value)->{checked.set(null);verified.set(null);});hwid.setOnCheckedChangeListener((b,value)->{checked.set(null);verified.set(null);});update.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Добавить подключение").setView(form).setPositiveButton("Добавить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->safe(()->{
            requireOff();String value=input.getText().toString().trim();boolean secure=value.toLowerCase(Locale.ROOT).startsWith("https://")&&!value.contains("\n"),plain=value.toLowerCase(Locale.ROOT).startsWith("http://")&&!value.contains("\n");
            if(secure||plain){
                List<JSONObject> profiles=checked.get();Core.validateSubscriptionForSaving(value,approval.isChecked(),verified.get(),profiles==null?0:profiles.size());
                JSONArray subscriptions=state.getJSONArray("subscriptions");for(int i=0;i<subscriptions.length();i++)if(subscriptions.getJSONObject(i).getString("url").equals(value))throw new Exception("Эта подписка уже добавлена. Обновите её во вкладке «Серверы».");
                if(state.getJSONArray("profiles").length()+(profiles==null?0:profiles.size())>1000)throw new Exception("Максимум 1000 профилей.");
                String title=name.getText().toString().trim();if(title.isEmpty())throw new Exception("Введите название.");
                JSONObject sub=Core.obj("id",UUID.randomUUID().toString(),"name",title,"url",value,"sendHWID",secure&&hwid.isChecked(),"allowHTTP",plain&&approval.isChecked());
                subscriptions.put(sub);if(profiles!=null)for(JSONObject p:profiles){p.put("subscription",sub.getString("id"));state.getJSONArray("profiles").put(p);}ensureSelection();save();tab=1;dialog.dismiss();render();
                if(profiles==null&&!busy)refresh(sub,true);return;
            }
            List<JSONObject> profiles=Core.parse(value);if(state.getJSONArray("profiles").length()+profiles.size()>1000)throw new Exception("Максимум 1000 профилей.");for(JSONObject p:profiles)state.getJSONArray("profiles").put(p);ensureSelection();save();tab=1;dialog.dismiss();render();
        })));dialog.show();
    }

    private void ensureSelection()throws Exception{JSONArray ps=state.getJSONArray("profiles");if(Core.selected(state)==null)state.put("selected",ps.length()==0?"":ps.getJSONObject(0).getString("id"));}
    private static void remove(JSONArray items,String id)throws Exception{for(int i=items.length()-1;i>=0;i--)if(items.getJSONObject(i).getString("id").equals(id))items.remove(i);}
    private void refresh(JSONObject sub)throws Exception { refresh(sub,false); }
    private void refresh(JSONObject sub,boolean quiet)throws Exception {
        requireOff();if(busy)throw new Exception("Дождитесь текущего обновления.");
        final String id=sub.getString("id"),url=sub.getString("url");
        final Map<String,String> headers=url.toLowerCase(Locale.ROOT).startsWith("https://")&&sub.optBoolean("sendHWID",state.optBoolean("sendHWID"))?DeviceInfo.headers(this):null;
        final boolean permit=sub.optBoolean("allowHTTP");busy=true;
        tasks.execute(()->{
            try {
                Vault.FetchResult response=Vault.fetchResult(url,headers,permit);List<JSONObject> incoming=response.profiles;
                runOnUiThread(()->{
                    busy=false;if(isDestroyed())return;
                    try {
                        requireOff();JSONObject current=Core.find(state.getJSONArray("subscriptions"),id);if(current==null)return;
                        JSONArray ps=state.getJSONArray("profiles");List<JSONObject> old=new ArrayList<>();
                        for(int i=0;i<ps.length();i++)if(ps.getJSONObject(i).optString("subscription").equals(id))old.add(ps.getJSONObject(i));
                        if(ps.length()-old.size()+incoming.size()>1000)throw new Exception("Слишком много профилей.");
                        Set<String> reused=new HashSet<>();
                        for(JSONObject p:incoming){p.put("subscription",id);for(JSONObject previous:old){String previousId=previous.getString("id");if(!reused.contains(previousId)&&previous.getString("type").equals(p.getString("type"))&&previous.getString("host").equals(p.getString("host"))&&previous.getInt("port")==p.getInt("port")&&previous.getString("credential").equals(p.getString("credential"))&&previous.getJSONObject("query").toString().equals(p.getJSONObject("query").toString())){p.put("id",previousId).put("favorite",previous.optBoolean("favorite"));reused.add(previousId);break;}}}
                        for(int i=ps.length()-1;i>=0;i--)if(ps.getJSONObject(i).optString("subscription").equals(id))ps.remove(i);
                        for(JSONObject p:incoming)ps.put(p);
                        current.remove("lastError");if(response.usage==null)current.remove("usage");else current.put("usage",response.usage);current.put("updated",System.currentTimeMillis());ensureSelection();save();render();
                        if(!quiet)message("Подписка обновлена: "+incoming.size()+" серверов.");
                    } catch(Exception e){subscriptionFailure(id,e,quiet);}
                });
            } catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed())subscriptionFailure(id,e,quiet);});}
        });
    }
    private void subscriptionFailure(String id,Exception error,boolean quiet) {
        String detail;
        if(error instanceof javax.net.ssl.SSLException)detail="Не удалось установить TLS-соединение. Проверка сертификата не отключена.";
        else if(error instanceof java.net.SocketTimeoutException)detail="Сервер не ответил вовремя. Ссылка сохранена.";
        else if(error instanceof java.net.UnknownHostException)detail="Не удалось разрешить DNS-имя сервера. Ссылка сохранена.";
        else if(error instanceof java.io.IOException)detail="Сетевая ошибка. Ссылка сохранена; обновите её позже.";
        else detail=error.getMessage()==null?"Не удалось обработать ответ сервера.":error.getMessage().replaceAll("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+","[адрес скрыт]");
        if(detail.length()>500)detail=detail.substring(0,500);
        try{JSONObject current=Core.find(state.getJSONArray("subscriptions"),id);if(current!=null){current.put("lastError",detail);save();}}catch(Exception ignored){}
        render();if(!quiet)message(detail);
    }

    private void editSubscription(JSONObject sub){LinearLayout form=ui.column();form.setPadding(ui.dp(20),0,ui.dp(20),0);EditText name=ui.input("Название",sub.optString("name"));ui.add(form,name);Switch hwid=ui.toggle("Отправлять HWID",sub.optBoolean("sendHWID",state.optBoolean("sendHWID")),v->{});ui.add(form,hwid);ui.add(form,ui.text("HWID AURA: "+DeviceInfo.hwid(this)+"\nПосле сохранения обновите подписку. Идентификатор другого клиента не переносится автоматически.",12,ui.muted));new AlertDialog.Builder(this).setTitle("Настройки подписки").setView(form).setPositiveButton("Сохранить",(d,w)->safe(()->{sub.put("name",name.getText().toString().trim()).put("sendHWID",hwid.isChecked());save();render();})).setNegativeButton("Отмена",null).setNeutralButton("Удалить",(d,w)->safe(()->{requireOff();String id=sub.getString("id");remove(state.getJSONArray("subscriptions"),id);JSONArray ps=state.getJSONArray("profiles");for(int i=ps.length()-1;i>=0;i--)if(ps.getJSONObject(i).optString("subscription").equals(id))ps.remove(i);ensureSelection();save();render();})).show();}
    private void measure(JSONObject p)throws Exception{
        if(busy)throw new Exception("Дождитесь текущей проверки.");
        JSONObject snapshot=new JSONObject(state.toString()),settings=Ping.settings(state);String id=p.getString("id");busy=true;render();
        tasks.execute(()->{JSONObject result=null;String error=null;try{result=Ping.measure(snapshot,p,settings);}catch(Exception e){error=e.getMessage();}final JSONObject completed=result;final String detail=error;runOnUiThread(()->safe(()->{busy=false;if(isDestroyed())return;JSONObject current=Core.find(state.getJSONArray("profiles"),id);if(current!=null){applyPing(current,completed,settings.getString("method"));save();}render();if(detail!=null)message(detail);}));});
    }
    private void applyPing(JSONObject p,JSONObject result,String method)throws Exception{
        p.remove("pingError");p.remove("latency");p.remove("pingStatus");p.put("pingMethod",method);p.put("latencyCheckedAt",System.currentTimeMillis());
        if(result==null)p.put("pingError",true);else{p.put("latency",result.getInt("ms"));p.put("pingStatus",result.getInt("status"));}
    }
    private void measureGroup(List<JSONObject> profiles)throws Exception{
        if(busy)throw new Exception("Дождитесь текущей проверки.");if(profiles.isEmpty())return;
        JSONObject snapshot=new JSONObject(state.toString()),settings=Ping.settings(state);List<JSONObject> copies=new ArrayList<>();for(JSONObject p:profiles)copies.add(new JSONObject(p.toString()));busy=true;batchProbing=true;cancelBatch=false;render();
        tasks.execute(()->{for(JSONObject p:copies){if(cancelBatch||Thread.currentThread().isInterrupted())break;JSONObject result=null;try{result=Ping.measure(snapshot,p,settings);}catch(Exception ignored){}final JSONObject completed=result;runOnUiThread(()->safe(()->{JSONObject current=Core.find(state.getJSONArray("profiles"),p.getString("id"));if(current!=null){applyPing(current,completed,settings.getString("method"));save();}if(!isDestroyed())render();}));}runOnUiThread(()->{busy=false;batchProbing=false;if(!isDestroyed())render();});});
    }
    private void pingSettings()throws Exception{
        JSONObject settings=Ping.settings(state);SettingsUi design=new SettingsUi(ui);SettingsUi.Page page=design.new Page("Настройки пинга");
        LinearLayout methodsCard=design.group(page.body,"ПРОТОКОЛ ПИНГА");
        final SettingsUi.Choice[] modeRef=new SettingsUi.Choice[1];final EditText[] urlRef=new EditText[1];final LinearLayout[] urlCardRef=new LinearLayout[1];
        java.util.function.Consumer<String> updateMethod=method->{boolean proxy=method.equals("GET")||method.equals("HEAD");if(modeRef[0]!=null)modeRef[0].enabled(proxy);if(urlRef[0]!=null){urlRef[0].setEnabled(proxy);urlCardRef[0].setAlpha(proxy?1:.4f);}};
        SettingsUi.Choice methods=design.choices(methodsCard,"ping.method",Ping.METHODS,new String[]{"Через прокси · GET","Через прокси · HEAD","TCP","ICMP"},new String[]{"Запрос через выбранный сервер","Только заголовки ответа через прокси","Время соединения с портом сервера","Ответ на ICMP-пакет · IPv4"},settings.getString("method"),updateMethod);
        LinearLayout modeCard=design.group(page.body,"РЕЖИМ ЧЕРЕЗ ПРОКСИ");
        SettingsUi.Choice mode=design.choices(modeCard,"ping.mode",Ping.MODES,new String[]{"Default","Double","Keepalive"},new String[]{"Один запрос через прокси","Лучший из двух отдельных соединений","Два запроса с повторным использованием соединения"},settings.getString("mode"),v->{});modeRef[0]=mode;
        design.note(page.body,"Режим применяется к GET и HEAD. Повторное использование соединения зависит от сервера.");
        LinearLayout timeoutCard=design.group(page.body,"ТАЙМАУТ");timeoutCard.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(12));TextView timeout=ui.title(settings.getInt("timeout")+" секунд",17);ui.add(timeoutCard,timeout);
        SeekBar slider=new SeekBar(this);slider.setTag("ping.timeout");slider.setContentDescription("Таймаут пинга");slider.setMax(10);slider.setProgress(settings.getInt("timeout")-5);slider.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.accent));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.accent));slider.setMinimumHeight(ui.dp(48));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){timeout.setText((value+5)+" секунд");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});ui.add(timeoutCard,slider);LinearLayout limits=ui.line();limits.addView(ui.text("5 с",12,ui.muted),new LinearLayout.LayoutParams(0,-2,1));limits.addView(ui.text("15 с",12,ui.muted));ui.add(timeoutCard,limits);design.note(page.body,"Общий таймаут на всю проверку сервера.");
        LinearLayout urlCard=design.group(page.body,"АДРЕС ДЛЯ ПРОВЕРКИ");urlCard.setPadding(ui.dp(14),ui.dp(4),ui.dp(14),ui.dp(4));EditText url=ui.input("https://example.com/generate_204",settings.getString("url"));url.setInputType(17);url.setSingleLine(true);url.setContentDescription("URL теста");url.setTag("ping.url");ui.add(urlCard,url);urlRef[0]=url;urlCardRef[0]=urlCard;
        LinearLayout displayCard=design.group(page.body,"ОТОБРАЖЕНИЕ");Switch display=design.toggle(displayCard,"HTTP-статус вместо времени",settings.optBoolean("displayStatus"),v->{});display.setTag("ping.display");design.note(page.body,"TCP и ICMP показывают время. ICMP может блокироваться сетью или системой. Прямые проверки используют текущий маршрут.");
        updateMethod.accept(methods.selected);
        page.save.setOnClickListener(v->{try{JSONObject changed=new JSONObject(settings.toString());changed.put("method",methods.selected).put("mode",mode.selected).put("timeout",slider.getProgress()+5).put("url",url.getText().toString().trim()).put("displayStatus",display.isChecked());Ping.validate(changed);state.put("ping",changed);save();page.dialog.dismiss();render();}catch(Exception error){page.error.setText(error.getMessage()==null?"Не удалось сохранить настройки":error.getMessage());page.error.setVisibility(View.VISIBLE);}});page.show();
    }
    private String modeTitle(){switch(state.optString("mode")){case "global":return "Весь трафик";case "direct":return "Напрямую";default:return "По правилам";}}
    private void rules()throws Exception{
        heading("Ваш трафик под контролем","Маршрутизация");LinearLayout modes=ui.card(body);ui.add(modes,button("Режим: "+modeTitle(),()->{requireOff();new AlertDialog.Builder(this).setTitle("Режим подключения").setItems(new String[]{"По правилам","Весь трафик","Напрямую"},(d,i)->safe(()->{state.put("mode",new String[]{"smart","global","direct"}[i]);save();render();})).show();}));ui.add(modes,ui.toggle("Локальная сеть напрямую",state.optBoolean("bypassLAN",true),v->safe(()->{requireOff();state.put("bypassLAN",v);save();})));
        JSONArray rules=state.getJSONArray("rules");for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i);final int index=i;LinearLayout c=info(body,rule.getString("value"),rule.getString("kind")+" → "+Core.actionTitle(rule.getString("action")));ui.add(c,ui.toggle("Правило включено",rule.optBoolean("enabled",true),v->safe(()->{requireOff();rule.put("enabled",v);save();})));ui.add(c,button("Изменить / порядок",()->ruleMenu(rule,index)));}
        ui.add(body,button("＋ Добавить правило",()->{requireOff();ruleEditor(null);}));ui.space(body,20);LinearLayout tester=ui.card(body);ui.add(tester,ui.title("Проверить маршрут",17));EditText host=ui.input("example.com или 192.168.1.1","");ui.add(tester,host);TextView result=ui.text("",13,ui.accent);ui.add(tester,button("Проверить",()->result.setText(Core.route(state,host.getText().toString()))));ui.space(tester,12);ui.add(tester,result);
        ui.add(body,ui.text("Первое совпадение определяет маршрут. Изменения применяются при следующем подключении.",12,ui.muted));
    }
    private void ruleMenu(JSONObject rule,int index)throws Exception{requireOff();new AlertDialog.Builder(this).setTitle("Правило").setItems(new String[]{"Изменить","Выше","Ниже","Удалить"},(d,i)->safe(()->{JSONArray rules=state.getJSONArray("rules");if(i==0){ruleEditor(rule);return;}if(i==3)rules.remove(index);else{int other=i==1?index-1:index+1;if(other>=0&&other<rules.length()){Object saved=rules.get(other);rules.put(other,rule);rules.put(index,saved);}}save();render();})).show();}
    private void ruleEditor(JSONObject existing)throws Exception{LinearLayout form=ui.column();form.setPadding(ui.dp(20),0,ui.dp(20),0);String[] kinds={"suffix","domain","keyword","cidr"},actions={"proxy","direct","block"};Spinner kind=ui.spinner(new String[]{"Домен и поддомены","Точный домен","Часть домена","IPv4 / CIDR"},existing==null?0:Arrays.asList(kinds).indexOf(existing.getString("kind")));EditText value=ui.input("example.com или 192.168.0.0/16",existing==null?"":existing.getString("value"));Spinner action=ui.spinner(new String[]{"Прокси","Напрямую","Блокировать"},existing==null?0:Arrays.asList(actions).indexOf(existing.getString("action")));ui.add(form,kind);ui.add(form,value);ui.add(form,action);new AlertDialog.Builder(this).setTitle("Правило").setView(form).setPositiveButton("Сохранить",(d,w)->safe(()->{requireOff();JSONObject rule=Core.obj("id",existing==null?UUID.randomUUID().toString():existing.getString("id"),"kind",kinds[kind.getSelectedItemPosition()],"value",value.getText().toString().trim().toLowerCase(Locale.ROOT),"action",actions[action.getSelectedItemPosition()],"enabled",existing==null||existing.optBoolean("enabled",true));Core.validateRule(rule);if(existing!=null)remove(state.getJSONArray("rules"),existing.getString("id"));state.getJSONArray("rules").put(rule);save();render();})).setNegativeButton("Отмена",null).show();}
    private void studio()throws Exception{
        heading("Всё в вашем ритме","Студия оформления");LinearLayout preview=info(body,"AURA · ваш стиль","Изменения видны сразу во всём приложении");Ui.Orb orb=new Ui.Orb(ui,false);preview.addView(orb,new LinearLayout.LayoutParams(-1,ui.dp(150)));
        LinearLayout accents=ui.card(body);ui.add(accents,ui.caption("АКЦЕНТ"));String[] keys={"mint","blue","violet","rose","amber"},titles={"Мята","Лазурь","Аметист","Роза","Янтарь"};for(int i=0;i<keys.length;i++){final String key=keys[i];ui.space(accents,6);ui.add(accents,button((state.optString("accent").equals(key)?"✓  ":"")+titles[i],()->{state.put("accent",key);save();render();}));}
        LinearLayout options=ui.card(body);ui.add(options,button("Фон: "+state.optString("backdrop"),()->choose("Атмосфера",new String[]{"Северное сияние","Океан","Сумерки","Минимализм"},new String[]{"aurora","ocean","dusk","minimal"},"backdrop")));ui.space(options,10);ui.add(options,button("Тема: "+state.optString("theme"),()->choose("Тема",new String[]{"Тёмная","Светлая","Как в системе"},new String[]{"dark","light","system"},"theme")));
        ui.add(options,ui.toggle("Компактные карточки",state.optBoolean("compact"),v->safe(()->{state.put("compact",v);save();render();})));ui.add(options,ui.toggle("Показывать адреса серверов",state.optBoolean("showHost",true),v->safe(()->{state.put("showHost",v);save();render();})));ui.add(options,ui.text("Скругление карточек",13,ui.muted));SeekBar radius=new SeekBar(this);radius.setMax(24);radius.setProgress(state.optInt("radius",28)-12);radius.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.accent));radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){safe(()->{state.put("radius",s.getProgress()+12);save();render();});}});ui.add(options,radius);
        LinearLayout widgets=info(body,"Блоки главного экрана","Нажмите на название, чтобы переместить блок выше");JSONArray order=state.getJSONArray("widgets"),hidden=state.getJSONArray("hiddenWidgets");for(int i=0;i<order.length();i++){String key=order.getString(i);final int index=i;ui.add(widgets,ui.toggle(widgetTitle(key),!contains(hidden,key),v->safe(()->{if(v)removeString(hidden,key);else if(!contains(hidden,key))hidden.put(key);save();})));ui.add(widgets,button("↑ "+widgetTitle(key),()->{if(index>0){Object before=order.get(index-1);order.put(index-1,key);order.put(index,before);save();render();}}));}
    }
    private String widgetTitle(String key){return key.equals("server")?"Группы серверов":key.equals("routing")?"Маршрутизация":"Избранное";}
    private void choose(String title,String[] labels,String[] values,String key){new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,i)->safe(()->{state.put(key,values[i]);save();render();})).show();}
    private void settings()throws Exception{
        SettingsUi design=new SettingsUi(ui);
        LinearLayout quick=design.group(body,"ПОДКЛЮЧЕНИЯ");
        design.row(quick,"server","Серверы и подписки","Обновление, HWID провайдера и управление",null,()->{tab=1;render();});design.divider(quick);
        design.row(quick,"add","Добавить подключение",null,null,()->importDialog(""));design.divider(quick);design.row(quick,"qr","Сканировать QR-код",null,null,this::scan);
        LinearLayout network=design.group(body,"СЕТЬ");design.row(network,"ping","Настройки пинга","Протокол, режим и таймаут",Ping.settings(state).getString("method"),()->safe(this::pingSettings));design.divider(network);design.row(network,"route","Маршрутизация",null,modeTitle(),()->{tab=2;render();});design.divider(network);design.row(network,"shield","Приложения без VPN",null,null,()->safe(this::excludedApps));design.divider(network);
        String dns=state.optString("dns");String dnsTitle=dns.contains("1.1.1.1")?"Cloudflare":dns.contains("8.8.8.8")?"Google":"Quad9";
        design.row(network,"server","DNS через туннель",null,dnsTitle,()->safe(()->{requireOff();choose("DNS",new String[]{"Cloudflare · DoH","Google · DoH","Quad9 · DoH"},new String[]{"https://1.1.1.1/dns-query","https://8.8.8.8/dns-query","https://9.9.9.9/dns-query"},"dns");}));
        LinearLayout appearance=design.group(body,"ИНТЕРФЕЙС");design.row(appearance,"palette","Оформление","Тема, акцент и фон",null,()->{tab=3;render();});design.divider(appearance);design.toggle(appearance,"Минимум анимаций",state.optBoolean("reduceMotion"),v->safe(()->{state.put("reduceMotion",v);save();}));design.divider(appearance);design.toggle(appearance,"Классический главный экран",state.optBoolean("classicHome"),v->safe(()->{state.put("classicHome",v);save();}));
        LinearLayout privacy=design.group(body,"КОНФИДЕНЦИАЛЬНОСТЬ");design.toggle(privacy,"Скрывать в переключателе",state.optBoolean("hideRecents",true),v->safe(()->{state.put("hideRecents",v);save();render();}));design.divider(privacy);design.toggle(privacy,"HWID для новых подписок",state.optBoolean("sendHWID"),v->safe(()->{state.put("sendHWID",v);save();}));design.divider(privacy);
        design.row(privacy,"copy","Скопировать HWID",DeviceInfo.hwid(this),null,()->{getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("HWID AURA",DeviceInfo.hwid(this)));Toast.makeText(this,"HWID скопирован",Toast.LENGTH_SHORT).show();});design.note(body,"HWID — идентификатор установки AURA. Отправляется только провайдеру подписки, для которой включена отправка.");
        LinearLayout tools=design.group(body,"ДАННЫЕ И ПОДДЕРЖКА");design.row(tools,"ping","Диагностика",null,null,()->safe(this::diagnostics));design.divider(tools);design.row(tools,"copy","Создать резервную копию",null,null,this::exportBackup);design.divider(tools);design.row(tools,"document","Восстановить копию",null,null,()->safe(this::restoreBackup));design.divider(tools);design.row(tools,"document","Исходники и лицензия","GPL-3.0-or-later",null,()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/ilbodw200/Aura"))));
        TextView version=ui.text("AURA Android · "+BuildConfig.VERSION_NAME+"\nsing-box 1.11.15",12,ui.muted);version.setGravity(Gravity.CENTER);version.setLineSpacing(ui.dp(5),1);version.setPadding(0,ui.dp(16),0,ui.dp(12));ui.add(body,version);
    }
    private void excludedApps()throws Exception{requireOff();JSONArray excluded=state.getJSONArray("excludedApps");Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);TreeMap<String,String> apps=new TreeMap<>();for(ResolveInfo info:getPackageManager().queryIntentActivities(launcher,0)){String pkg=info.activityInfo.packageName;if(!pkg.equals(getPackageName()))apps.put(info.loadLabel(getPackageManager())+"\n"+pkg,pkg);}String[] labels=apps.keySet().toArray(new String[0]),packages=apps.values().toArray(new String[0]);boolean[] checked=new boolean[packages.length];for(int i=0;i<packages.length;i++)checked[i]=contains(excluded,packages[i]);new AlertDialog.Builder(this).setTitle("Напрямую, без VPN").setMultiChoiceItems(labels,checked,(d,index,value)->checked[index]=value).setPositiveButton("Сохранить",(d,w)->safe(()->{requireOff();JSONArray result=new JSONArray();for(int i=0;i<packages.length;i++)if(checked[i])result.put(packages[i]);state.put("excludedApps",result);save();message("Изменения применятся при следующем подключении.");})).setNegativeButton("Отмена",null).show();}
    private void diagnostics()throws Exception{JSONObject selected=Core.selected(state);new AlertDialog.Builder(this).setTitle("Диагностика").setMessage("VPN: "+statusTitle()+"\nПрофилей: "+state.getJSONArray("profiles").length()+"\nПравил: "+state.getJSONArray("rules").length()+"\nЯдро: sing-box 1.11.15\n\nTCP-проверка не подтверждает работу VPN или Hysteria2/UDP. Статус подключения подтверждается VPN-сервисом после запуска ядра.").setPositiveButton("Готово",null).setNeutralButton("Проверить пинг",(d,w)->safe(()->{if(selected==null)throw new Exception("Выберите сервер.");measure(selected);})).show();}
    private void exportBackup(){EditText password=ui.input("Минимум 12 символов","");password.setInputType(129);new AlertDialog.Builder(this).setTitle("Пароль резервной копии").setMessage("AES-256-GCM и PBKDF2-SHA256. Пароль не сохраняется. Копии iOS и Android имеют разные форматы данных.").setView(password).setPositiveButton("Создать",(d,w)->safe(()->{String secret=password.getText().toString();JSONObject snapshot=new JSONObject(state.toString());background(()->Backup.export(snapshot,secret),bytes->{exportBytes=bytes;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"AURA-Android.aurabackup"),13);});})).setNegativeButton("Отмена",null).show();}
    private void restoreBackup()throws Exception{requireOff();EditText password=ui.input("Пароль копии","");password.setInputType(129);new AlertDialog.Builder(this).setTitle("Восстановить копию").setView(password).setPositiveButton("Выбрать файл",(d,w)->{restorePassword=password.getText().toString();startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),14);}).setNegativeButton("Отмена",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);IntentResult scan=IntentIntegrator.parseActivityResult(request,result,data);if(scan!=null){if(scan.getContents()!=null)importDialog(scan.getContents());return;}if(result!=RESULT_OK){restorePassword="";exportBytes=null;return;}if(request==10){startVPN();return;}if(data==null||data.getData()==null)return;Uri uri=data.getData();safe(()->{if(request==12)background(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new Exception("Не удалось открыть файл.");return new String(Vault.readLimit(in,2_000_000),StandardCharsets.UTF_8);}},this::importDialog);if(request==13&&exportBytes!=null){byte[] bytes=exportBytes;exportBytes=null;background(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("Не удалось сохранить файл.");out.write(bytes);}return true;},done->message("Копия сохранена."));}if(request==14){String secret=restorePassword;restorePassword="";background(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new Exception("Не удалось прочитать копию.");return Backup.restore(Vault.readLimit(in,8_000_000),secret);}},restored->new AlertDialog.Builder(this).setTitle("Заменить текущие данные?").setMessage("Будет восстановлено профилей: "+restored.getJSONArray("profiles").length()).setPositiveButton("Восстановить",(d,w)->safe(()->{requireOff();state=restored;save();render();})).setNegativeButton("Отмена",null).show());}});}
    interface Result<T>{void accept(T result)throws Exception;}
    private <T> void background(Callable<T> operation,Result<T> result)throws Exception{if(busy)throw new Exception("Дождитесь завершения текущей операции.");busy=true;Toast.makeText(this,"Обрабатываем…",Toast.LENGTH_SHORT).show();tasks.execute(()->{try{T value=operation.call();runOnUiThread(()->{busy=false;if(!isDestroyed())safe(()->result.accept(value));});}catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed())message("Не удалось выполнить операцию. Проверьте сеть, формат и пароль. Текущие данные сохранены.");});}});}
    private void message(String text){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("AURA").setMessage(text).setPositiveButton("Понятно",null).show();}
    private static boolean contains(JSONArray array,String value){for(int i=0;i<array.length();i++)if(value.equals(array.optString(i)))return true;return false;}
    private static void removeString(JSONArray array,String value){for(int i=array.length()-1;i>=0;i--)if(value.equals(array.optString(i)))array.remove(i);}
}
