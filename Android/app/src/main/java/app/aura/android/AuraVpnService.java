package app.aura.android;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;
import io.nekohasekai.libbox.*;
import org.json.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public final class AuraVpnService extends VpnService implements PlatformInterface {
    public static volatile String status="disconnected",lastError="",publicIP="";
    public static volatile long connectedAt,rxTotal,txTotal,rxRate,txRate;
    public static volatile boolean metricsAvailable,alwaysOn,lockdown;
    private static volatile AuraVpnService current;
    private static final Deque<String> EVENTS=new ArrayDeque<>();
    public static synchronized List<String> events(){return new ArrayList<>(EVENTS);}
    private static synchronized void event(String text){EVENTS.addFirst(new java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+" · "+text);while(EVENTS.size()>100)EVENTS.removeLast();}
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService probes=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean desired;
    private final java.util.concurrent.atomic.AtomicBoolean ipChecking=new java.util.concurrent.atomic.AtomicBoolean();
    private BoxService box;
    private CommandServer commandServer;
    private CommandClient commandClient;
    private TunnelHttp.Endpoint endpoint;
    private ParcelFileDescriptor tunnel;
    private JSONObject snapshot;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback callback;
    private volatile InterfaceUpdateListener listener;
    private static final String CHANNEL="aura-vpn";
    private int retries;
    @Override public void onCreate(){super.onCreate();current=this;connectivity=getSystemService(ConnectivityManager.class);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Подключение AURA",NotificationManager.IMPORTANCE_LOW));if(Build.VERSION.SDK_INT>=29){alwaysOn=isAlwaysOn();lockdown=isLockdownEnabled();}}
    private void state(String value){if(current!=this)return;status=value;getSharedPreferences("runtime",MODE_PRIVATE).edit().putLong("revision",System.nanoTime()).apply();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        boolean stop=intent!=null&&"STOP".equals(intent.getAction());
        if(stop){desired=false;lastError="";getSharedPreferences("runtime",MODE_PRIVATE).edit().putBoolean("desired",false).apply();long token=generation.incrementAndGet();state("disconnecting");worker.execute(()->{if(token!=generation.get())return;closeCore();connectedAt=0;publicIP="";state("disconnected");event("VPN отключён пользователем");stopForeground(STOP_FOREGROUND_REMOVE);if(startId==0)stopSelf();else stopSelfResult(startId);});return START_NOT_STICKY;}
        if(intent==null&&!alwaysOn&&!getSharedPreferences("runtime",MODE_PRIVATE).getBoolean("desired",false))return START_NOT_STICKY;
        if(desired&&!"RESTART".equals(intent==null?null:intent.getAction()))return START_STICKY;
        desired=true;retries=0;getSharedPreferences("runtime",MODE_PRIVATE).edit().putBoolean("desired",true).apply();long token=generation.incrementAndGet();lastError="";publicIP="";state("connecting");startForeground(7,notification("Подключение…"));worker.execute(()->startCore(token));return START_STICKY;
    }
    private android.app.Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,AuraActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,AuraVpnService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new android.app.Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("AURA VPN").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new android.app.Notification.Action.Builder(null,"Отключить",stop).build()).build();
    }
    private void startCore(long token){if(!desired||token!=generation.get())return;closeCore();try{
        if(VpnService.prepare(this)!=null)throw new SecurityException("VPN permission missing");
        snapshot=new Vault(this).load();JSONObject config=new JSONObject(Core.config(snapshot));
        int port;try(java.net.ServerSocket reservation=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){port=reservation.getLocalPort();}
        endpoint=new TunnelHttp.Endpoint(port,"aura",UUID.randomUUID().toString());
        config.getJSONArray("inbounds").put(Core.obj("type","socks","tag","aura-observe","listen","127.0.0.1","listen_port",port,"users",Core.array(Core.obj("username",endpoint.user,"password",endpoint.password))));
        JSONArray oldRules=config.getJSONObject("route").getJSONArray("rules"),rules=Core.array(Core.obj("inbound",Core.array("aura-observe"),"outbound","proxy"));for(int i=0;i<oldRules.length();i++)rules.put(oldRules.get(i));config.getJSONObject("route").put("rules",rules);
        SetupOptions setup=new SetupOptions();setup.setBasePath(getFilesDir().getAbsolutePath());setup.setWorkingPath(new File(getFilesDir(),"box").getAbsolutePath());setup.setTempPath(getCacheDir().getAbsolutePath());Libbox.setup(setup);Libbox.setMemoryLimit(false);
        box=Libbox.newService(config.toString(),this);box.start();if(tunnel==null)throw new Exception("TUN missing");
        if(!desired||token!=generation.get()){closeCore();return;}
        connectedAt=System.currentTimeMillis();lastError="";publicIP="";rxTotal=txTotal=rxRate=txRate=0;state("connected");event("Туннель установлен");getSystemService(NotificationManager.class).notify(7,notification("VPN включён"));
        startMetrics(token);checkPublicIP();
    }catch(Exception e){closeCore();connectedAt=0;lastError="Не удалось запустить VPN. Проверьте разрешение, конфигурацию и сеть.";event("Ошибка запуска туннеля");
        boolean retry=desired&&!(e instanceof SecurityException)&&snapshot!=null&&snapshot.optBoolean("autoReconnect",true)&&retries++<5;
        if(retry){state("reconnecting");int delay=Math.min(60,2<<retries);worker.schedule(()->{if(token==generation.get()&&desired)startCore(token);},delay,TimeUnit.SECONDS);}else{desired=false;getSharedPreferences("runtime",MODE_PRIVATE).edit().putBoolean("desired",false).apply();state("error");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    }}
    private void startMetrics(long token){try{
        commandServer=Libbox.newCommandServer(new CommandServerHandler(){public void serviceReload(){worker.execute(()->startCore(generation.get()));}public void postServiceClose(){stopRequested();}public SystemProxyStatus getSystemProxyStatus(){return new SystemProxyStatus();}public void setSystemProxyEnabled(boolean enabled){}},10);commandServer.setService(box);commandServer.start();
        CommandClientOptions options=new CommandClientOptions();options.setCommand(Libbox.CommandStatus);options.setStatusInterval(1000000000L);
        commandClient=Libbox.newCommandClient(new CommandClientHandler(){public void connected(){}public void disconnected(String message){if(token==generation.get())metricsAvailable=false;}public void clearLogs(){}public void writeLogs(StringIterator values){}public void writeGroups(OutboundGroupIterator groups){}public void initializeClashMode(StringIterator modes,String selected){}public void updateClashMode(String mode){}public void writeConnections(Connections connections){}
            public void writeStatus(StatusMessage message){if(token!=generation.get()||current!=AuraVpnService.this)return;metricsAvailable=message.getTrafficAvailable();rxTotal=message.getDownlinkTotal();txTotal=message.getUplinkTotal();rxRate=Math.max(0,message.getDownlink());txRate=Math.max(0,message.getUplink());}
        },options);commandClient.connect();
    }catch(Exception e){metricsAvailable=false;event("Счётчики ядра временно недоступны");}}
    public static void checkPublicIP(){AuraVpnService service=current;if(service==null||!status.equals("connected")||!service.ipChecking.compareAndSet(false,true))return;long token=service.generation.get();TunnelHttp.Endpoint target=service.endpoint;try{service.probes.execute(()->{try{if(token!=service.generation.get())return;String ip=TunnelHttp.publicIP(target);if(token==service.generation.get()&&service.desired){publicIP=ip;event("IP через выбранный прокси проверен");}}catch(Exception e){if(token==service.generation.get())event("Проверка IP недоступна; прямой запрос не выполнялся");}finally{service.ipChecking.set(false);}});}catch(RejectedExecutionException e){service.ipChecking.set(false);}}
    private void closeCore(){metricsAvailable=false;endpoint=null;if(commandClient!=null){try{commandClient.disconnect();}catch(Exception ignored){}commandClient=null;}if(commandServer!=null){try{commandServer.close();}catch(Exception ignored){}commandServer=null;}if(box!=null){try{box.close();}catch(Exception ignored){}box=null;}if(tunnel!=null){try{tunnel.close();}catch(Exception ignored){}tunnel=null;}try{closeDefaultInterfaceMonitor(listener);}catch(Exception ignored){}rxRate=txRate=0;}
    private void stopRequested(){onStartCommand(new Intent(this,AuraVpnService.class).setAction("STOP"),0,0);}
    @Override public void onRevoke(){event("Системное разрешение VPN отозвано");stopRequested();}
    @Override public void onDestroy(){desired=false;generation.incrementAndGet();worker.execute(()->{closeCore();if(current==this){connectedAt=0;state(lastError.isEmpty()?"disconnected":"error");current=null;}});worker.shutdown();probes.shutdownNow();super.onDestroy();}

    @Override public int openTun(TunOptions options)throws Exception{
        if(VpnService.prepare(this)!=null)throw new Exception("VPN permission revoked");
        Builder builder=new Builder().setSession("AURA").setMtu(options.getMTU()).setConfigureIntent(PendingIntent.getActivity(this,0,new Intent(this,AuraActivity.class),PendingIntent.FLAG_IMMUTABLE));
        if(Build.VERSION.SDK_INT>=29)builder.setMetered(false);
        RoutePrefixIterator v4=options.getInet4Address();while(v4.hasNext()){RoutePrefix p=v4.next();builder.addAddress(p.address(),p.prefix());}
        RoutePrefixIterator v6=options.getInet6Address();while(v6.hasNext()){RoutePrefix p=v6.next();builder.addAddress(p.address(),p.prefix());}
        builder.addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer(options.getDNSServerAddress().getValue());
        // Control traffic and native core sockets must never be captured by their own TUN.
        boolean include=snapshot.optString("splitMode","exclude").equals("include");JSONArray apps=snapshot.optJSONArray(include?"includedApps":"excludedApps");
        if(include&&(apps==null||apps.length()==0))throw new Exception("Choose at least one VPN application");
        if(!include)builder.addDisallowedApplication(getPackageName());
        if(apps!=null)for(int i=0;i<apps.length();i++){String pkg=apps.getString(i);if(pkg.equals(getPackageName()))continue;try{if(include)builder.addAllowedApplication(pkg);else builder.addDisallowedApplication(pkg);}catch(PackageManager.NameNotFoundException ignored){}}
        tunnel=builder.establish();if(tunnel==null)throw new Exception("Android did not establish TUN");return tunnel.getFd();
    }
    @Override public boolean usePlatformAutoDetectInterfaceControl(){return true;}
    @Override public void autoDetectInterfaceControl(int fd)throws Exception{if(!protect(fd))throw new Exception("Could not protect core socket");}
    @Override public boolean useProcFS(){return false;}
    @Override public boolean underNetworkExtension(){return false;}
    @Override public boolean includeAllNetworks(){return false;}
    @Override public void clearDNSCache(){}
    @Override public WIFIState readWIFIState(){return null;}
    @Override public void writeLog(String message){/* Native logs can contain endpoints and keys; not persisted. */}
    @Override public void sendNotification(io.nekohasekai.libbox.Notification n){/* Foreground connection notification is managed above. */}
    @Override public int findConnectionOwner(int protocol,String source,int sourcePort,String destination,int destinationPort)throws Exception{
        if(Build.VERSION.SDK_INT<29)throw new Exception("Connection ownership unavailable");
        return connectivity.getConnectionOwnerUid(protocol,new InetSocketAddress(source,sourcePort),new InetSocketAddress(destination,destinationPort));
    }
    @Override public String packageNameByUid(int uid){String[] values=getPackageManager().getPackagesForUid(uid);return values==null||values.length==0?"":values[0];}
    @Override public int uidByPackageName(String packageName)throws Exception{return getPackageManager().getApplicationInfo(packageName,0).uid;}
    @Override public void startDefaultInterfaceMonitor(InterfaceUpdateListener next){
        listener=next;
        callback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){updateNetwork();}@Override public void onLost(Network n){updateNetwork();}@Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){updateNetwork();}@Override public void onLinkPropertiesChanged(Network n,LinkProperties p){updateNetwork();}};
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),callback);updateNetwork();
    }
    private void updateNetwork(){
        InterfaceUpdateListener target=listener;if(target==null)return;
        Network preferred=connectivity.getActiveNetwork();NetworkCapabilities preferredCaps=connectivity.getNetworkCapabilities(preferred);
        if(preferredCaps==null||preferredCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){preferred=null;for(Network n:connectivity.getAllNetworks()){NetworkCapabilities c=connectivity.getNetworkCapabilities(n);if(c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){preferred=n;if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))break;}}}
        try{LinkProperties p=connectivity.getLinkProperties(preferred);NetworkCapabilities c=connectivity.getNetworkCapabilities(preferred);if(p==null||p.getInterfaceName()==null){target.updateDefaultInterface("",-1,false,false);networkState(false);return;}java.net.NetworkInterface it=java.net.NetworkInterface.getByName(p.getInterfaceName());target.updateDefaultInterface(p.getInterfaceName(),it==null?-1:it.getIndex(),c!=null&&!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),false);networkState(true);}catch(Exception ignored){target.updateDefaultInterface("",-1,false,false);}
    }
    private void networkState(boolean available){if(!desired)return;if(!available&&status.equals("connected")){event("Основная сеть потеряна");if(snapshot!=null&&!snapshot.optBoolean("autoReconnect",true)){stopRequested();return;}state("reconnecting");}else if(available&&status.equals("reconnecting")&&connectedAt>0){state("connected");event("Основная сеть восстановлена; маршрут ядра обновлён");}}
    @Override public void closeDefaultInterfaceMonitor(InterfaceUpdateListener ignored){listener=null;if(callback!=null){try{connectivity.unregisterNetworkCallback(callback);}catch(Exception unused){}callback=null;}}
    @Override public NetworkInterfaceIterator getInterfaces()throws Exception{
        List<io.nekohasekai.libbox.NetworkInterface> list=new ArrayList<>();
        Enumeration<java.net.NetworkInterface> all=java.net.NetworkInterface.getNetworkInterfaces();while(all.hasMoreElements()){
            java.net.NetworkInterface i=all.nextElement();if(!i.isUp()||i.isLoopback()||i.getName().startsWith("tun"))continue;
            io.nekohasekai.libbox.NetworkInterface item=new io.nekohasekai.libbox.NetworkInterface();item.setName(i.getName());item.setIndex(i.getIndex());item.setMTU(i.getMTU());item.setFlags(1|64);
            item.setType(i.getName().startsWith("wlan")?Libbox.InterfaceTypeWIFI:i.getName().startsWith("rmnet")?Libbox.InterfaceTypeCellular:Libbox.InterfaceTypeOther);
            List<String> addresses=new ArrayList<>();for(java.net.InterfaceAddress address:i.getInterfaceAddresses()){String ip=address.getAddress().getHostAddress();if(ip!=null)addresses.add(ip.split("%",2)[0]+"/"+address.getNetworkPrefixLength());}item.setAddresses(new Strings(addresses));item.setDNSServer(new Strings(Collections.emptyList()));list.add(item);
        }return new Interfaces(list);
    }
    private static final class Strings implements StringIterator{private final List<String> values;private int index;Strings(List<String> v){values=v;}public boolean hasNext(){return index<values.size();}public String next(){return values.get(index++);}public int len(){return values.size()-index;}}
    private static final class Interfaces implements NetworkInterfaceIterator{private final Iterator<io.nekohasekai.libbox.NetworkInterface> values;Interfaces(List<io.nekohasekai.libbox.NetworkInterface> v){values=v.iterator();}public boolean hasNext(){return values.hasNext();}public io.nekohasekai.libbox.NetworkInterface next(){return values.next();}}
}
