package app.aura.android;

import android.app.Instrumentation;
import android.content.*;
import android.net.VpnService;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.*;
import java.io.*;
import java.util.function.BooleanSupplier;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Uses a real Android TUN and native sing-box; the upstream is local and never reaches the Internet. */
@RunWith(AndroidJUnit4.class)
public class VpnLifecycleTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private void shell(String command)throws Exception{try(android.os.ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand(command);InputStream in=new FileInputStream(fd.getFileDescriptor())){while(in.read()!=-1){}}}
    private void until(BooleanSupplier predicate)throws Exception{long deadline=SystemClock.uptimeMillis()+15000;while(!predicate.getAsBoolean()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);assertTrue("VPN lifecycle condition timed out; status="+AuraVpnService.status,predicate.getAsBoolean());}
    private TunnelHttp.Endpoint observer()throws Exception{java.lang.reflect.Field owner=AuraVpnService.class.getDeclaredField("current");owner.setAccessible(true);Object service=owner.get(null);java.lang.reflect.Field field=AuraVpnService.class.getDeclaredField("endpoint");field.setAccessible(true);return (TunnelHttp.Endpoint)field.get(service);}
    private int observerPort()throws Exception{java.lang.reflect.Field owner=AuraVpnService.class.getDeclaredField("current");owner.setAccessible(true);Object service=owner.get(null);if(service==null)return -1;java.lang.reflect.Field field=AuraVpnService.class.getDeclaredField("endpoint");field.setAccessible(true);TunnelHttp.Endpoint endpoint=(TunnelHttp.Endpoint)field.get(service);return endpoint==null?-1:endpoint.port;}
    private boolean portClosed(int port){try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),200);return false;}catch(Exception e){return true;}}
    @Test public void nativeTunnelHasCountersSurvivesBackgroundAndSwitchesWithoutOverlap()throws Exception{
        Context context=instrumentation.getTargetContext();Vault vault=new Vault(context);JSONObject previous=vault.load();AuraActivity activity=null;
        try(LocalProxy proxy=new LocalProxy()){
            JSONObject state=Core.defaults(),first=Core.link("socks5://127.0.0.1:"+proxy.server.getLocalPort()+"#Local-one"),second=Core.link("socks5://127.0.0.1:"+proxy.server.getLocalPort()+"#Local-two");state.getJSONArray("profiles").put(first).put(second);state.put("selected",first.getString("id")).put("hideRecents",false).put("autoReconnect",true);vault.save(state);
            shell("appops set app.aura.android ACTIVATE_VPN ignore");assertNotNull("Missing permission must require Android consent",VpnService.prepare(context));
            activity=(AuraActivity)instrumentation.startActivitySync(new Intent(context,AuraActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();
            shell("appops set app.aura.android ACTIVATE_VPN allow");assertNull(VpnService.prepare(context));
            context.startForegroundService(new Intent(context,AuraVpnService.class).setAction("START"));until(()->AuraVpnService.status.equals("connected"));until(()->AuraVpnService.metricsAvailable);assertTrue(AuraVpnService.connectedAt>0);
            int oldPort=observerPort();assertTrue(oldPort>0);
            // Loopback observation cannot be used by unauthenticated local clients.
            try(Socket s=new Socket("127.0.0.1",oldPort)){s.setSoTimeout(2000);s.getOutputStream().write(new byte[]{5,1,0});assertEquals(5,s.getInputStream().read());assertEquals(255,s.getInputStream().read());}
            transfer(observer());until(()->AuraVpnService.rxTotal>=4096&&AuraVpnService.txTotal>0);
            shell("input keyevent 3");SystemClock.sleep(800);assertEquals("connected",AuraVpnService.status);
            shell("input keyevent 26");SystemClock.sleep(500);assertEquals("connected",AuraVpnService.status);shell("input keyevent 26");shell("wm dismiss-keyguard");
            state.put("selected",second.getString("id"));vault.save(state);context.startForegroundService(new Intent(context,AuraVpnService.class).setAction("RESTART"));until(()->AuraVpnService.status.equals("connected")&&portClosed(oldPort));until(()->AuraVpnService.metricsAvailable);int nextPort=observerPort();assertTrue(nextPort>0);assertNotEquals(oldPort,nextPort);
            context.startService(new Intent(context,AuraVpnService.class).setAction("STOP"));until(()->AuraVpnService.status.equals("disconnected"));assertFalse(AuraVpnService.metricsAvailable);assertTrue(portClosed(nextPort));assertEquals("",AuraVpnService.publicIP);
        }finally{
            if(AuraVpnService.status.equals("connected")||AuraVpnService.status.equals("connecting")||AuraVpnService.status.equals("reconnecting")){context.startService(new Intent(context,AuraVpnService.class).setAction("STOP"));until(()->AuraVpnService.status.equals("disconnected"));}
            if(activity!=null){AuraActivity current=activity;instrumentation.runOnMainSync(current::finish);}vault.save(previous);shell("appops set app.aura.android ACTIVATE_VPN ignore");
        }
    }
    private void transfer(TunnelHttp.Endpoint endpoint)throws Exception{
        try(Socket socket=new Socket("127.0.0.1",endpoint.port)){socket.setSoTimeout(5000);DataInputStream input=new DataInputStream(socket.getInputStream());DataOutputStream output=new DataOutputStream(socket.getOutputStream());output.write(new byte[]{5,1,2});output.flush();assertEquals(5,input.readUnsignedByte());assertEquals(2,input.readUnsignedByte());byte[] user=endpoint.user.getBytes("UTF-8"),password=endpoint.password.getBytes("UTF-8");output.writeByte(1);output.writeByte(user.length);output.write(user);output.writeByte(password.length);output.write(password);output.flush();assertEquals(1,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte());byte[] target="traffic.aura.invalid".getBytes("US-ASCII");output.write(new byte[]{5,1,0,3});output.writeByte(target.length);output.write(target);output.writeShort(80);output.flush();assertEquals(5,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte());input.readUnsignedByte();int type=input.readUnsignedByte();input.readFully(new byte[(type==1?4:type==4?16:input.readUnsignedByte())+2]);output.write("GET / HTTP/1.1\r\nHost: traffic.aura.invalid\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));output.flush();ByteArrayOutputStream response=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int count;while((count=input.read(buffer))!=-1)response.write(buffer,0,count);assertTrue(response.size()>=4096);}
    }
    private static final class LocalProxy implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));volatile boolean open=true;
        LocalProxy()throws Exception{Thread thread=new Thread(()->{while(open)try{Socket socket=server.accept();Thread client=new Thread(()->handle(socket));client.setDaemon(true);client.start();}catch(IOException ignored){}});thread.setDaemon(true);thread.start();}
        void handle(Socket socket){try(Socket s=socket){s.setSoTimeout(3000);DataInputStream in=new DataInputStream(s.getInputStream());if(in.readUnsignedByte()!=5)return;in.readFully(new byte[in.readUnsignedByte()]);s.getOutputStream().write(new byte[]{5,0});in.readUnsignedByte();in.readUnsignedByte();in.readUnsignedByte();int type=in.readUnsignedByte();int size=type==3?in.readUnsignedByte():type==1?4:16;in.readFully(new byte[size]);int port=in.readUnsignedShort();s.getOutputStream().write(new byte[]{5,(byte)(port==80?0:1),0,1,0,0,0,0,0,0});s.getOutputStream().flush();if(port==80){int matched=0;while(matched<4){int b=in.read();if(b<0)return;matched=(matched==0||matched==2)?(b==13?matched+1:0):(b==10?matched+1:0);}byte[] payload=new byte[4096];Arrays.fill(payload,(byte)'a');s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4096\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));s.getOutputStream().write(payload);s.getOutputStream().flush();}}catch(IOException ignored){}}
        public void close()throws Exception{open=false;server.close();}
    }
}
