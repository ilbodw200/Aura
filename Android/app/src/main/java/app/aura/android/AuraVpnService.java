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
    public static volatile String status="disconnected",lastError="";
    public static volatile long connectedAt;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private BoxService box;
    private ParcelFileDescriptor tunnel;
    private JSONObject snapshot;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback callback;
    private volatile InterfaceUpdateListener listener;
    private static final String CHANNEL="aura-vpn";

    @Override public void onCreate(){super.onCreate();connectivity=getSystemService(ConnectivityManager.class);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Подключение AURA",NotificationManager.IMPORTANCE_LOW));}
    private void state(String value){status=value;getSharedPreferences("runtime",MODE_PRIVATE).edit().putLong("revision",System.nanoTime()).apply();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"STOP".equals(intent.getAction())){state("disconnecting");worker.execute(this::stopCore);return START_NOT_STICKY;}
        if(status.equals("connecting")||status.equals("connected"))return START_NOT_STICKY;
        lastError="";state("connecting");startForeground(7,notification("Подключение…"));worker.execute(this::startCore);return START_NOT_STICKY;
    }
    private android.app.Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,AuraVpnService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new android.app.Notification.Builder(this,CHANNEL).setSmallIcon(app.aura.android.R.drawable.ic_notification).setContentTitle("AURA").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new android.app.Notification.Action.Builder(null,"Отключить",stop).build()).build();
    }
    private void startCore(){try{
        if(VpnService.prepare(this)!=null)throw new Exception("Нет системного разрешения VPN.");
        snapshot=new Vault(this).load();String config=Core.config(snapshot);
        SetupOptions setup=new SetupOptions();setup.setBasePath(getFilesDir().getAbsolutePath());setup.setWorkingPath(new File(getFilesDir(),"box").getAbsolutePath());setup.setTempPath(getCacheDir().getAbsolutePath());Libbox.setup(setup);Libbox.setMemoryLimit(false);
        box=Libbox.newService(config,this);box.start();
        if(tunnel==null)throw new Exception("Туннель не установлен.");
        connectedAt=System.currentTimeMillis();state("connected");getSystemService(NotificationManager.class).notify(7,notification("VPN подключён"));
    }catch(Exception e){lastError="Не удалось подключиться. Проверьте конфигурацию, сеть и доступность сервера.";stopCore();}}
    private void closeCore(){if(box!=null){try{box.close();}catch(Exception ignored){}box=null;}if(tunnel!=null){try{tunnel.close();}catch(Exception ignored){}tunnel=null;}try{closeDefaultInterfaceMonitor(listener);}catch(Exception ignored){}}
    private void stopCore(){closeCore();connectedAt=0;state("disconnected");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onRevoke(){state("disconnecting");worker.execute(this::stopCore);}
    @Override public void onDestroy(){worker.execute(()->{closeCore();connectedAt=0;state("disconnected");});worker.shutdown();super.onDestroy();}

    @Override public int openTun(TunOptions options)throws Exception{
        if(VpnService.prepare(this)!=null)throw new Exception("VPN permission revoked");
        Builder builder=new Builder().setSession("AURA").setMtu(options.getMTU()).setConfigureIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));
        if(Build.VERSION.SDK_INT>=29)builder.setMetered(false);
        RoutePrefixIterator v4=options.getInet4Address();while(v4.hasNext()){RoutePrefix p=v4.next();builder.addAddress(p.address(),p.prefix());}
        RoutePrefixIterator v6=options.getInet6Address();while(v6.hasNext()){RoutePrefix p=v6.next();builder.addAddress(p.address(),p.prefix());}
        builder.addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer(options.getDNSServerAddress().getValue());
        // Control traffic and native core sockets must never be captured by their own TUN.
        builder.addDisallowedApplication(getPackageName());
        JSONArray excluded=snapshot.optJSONArray("excludedApps");if(excluded!=null)for(int i=0;i<excluded.length();i++){String pkg=excluded.getString(i);if(pkg.equals(getPackageName()))continue;try{builder.addDisallowedApplication(pkg);}catch(PackageManager.NameNotFoundException ignored){}}
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
        try{LinkProperties p=connectivity.getLinkProperties(preferred);NetworkCapabilities c=connectivity.getNetworkCapabilities(preferred);if(p==null||p.getInterfaceName()==null){target.updateDefaultInterface("",-1,false,false);return;}java.net.NetworkInterface it=java.net.NetworkInterface.getByName(p.getInterfaceName());target.updateDefaultInterface(p.getInterfaceName(),it==null?-1:it.getIndex(),c!=null&&!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),false);}catch(Exception ignored){target.updateDefaultInterface("",-1,false,false);}
    }
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
