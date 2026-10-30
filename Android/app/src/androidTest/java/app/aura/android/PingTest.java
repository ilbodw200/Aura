package app.aura.android;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PingTest {
    @Test public void captureLibraryAndSettingsScreens()throws Exception {
        android.app.Instrumentation instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        android.content.Context context=instrumentation.getTargetContext();
        JSONObject state=Core.defaults();JSONObject sub=Core.obj("id","preview-subscription","name","Пример подписки","url","https://example.invalid/subscription","updated",System.currentTimeMillis());
        state.getJSONArray("subscriptions").put(sub);
        for(int i=0;i<2;i++){
            JSONObject profile=Core.link("socks5://server"+i+".example.invalid:1080#"+(i==0?"Amsterdam":"Helsinki"));
            profile.put("subscription","preview-subscription");state.getJSONArray("profiles").put(profile);
            if(i==0)state.put("selected",profile.getString("id"));
        }
        state.put("hideRecents",false);new Vault(context).save(state);
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();capture(instrumentation,"Aura-Library.png");
        instrumentation.runOnMainSync(()->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("pingSettings");method.setAccessible(true);method.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        instrumentation.waitForIdleSync();capture(instrumentation,"Aura-Ping.png");
        instrumentation.runOnMainSync(activity::finish);new Vault(context).save(Core.defaults());
    }
    private static void capture(android.app.Instrumentation instrumentation,String file)throws Exception {
        try(android.os.ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/"+file);java.io.FileInputStream input=new java.io.FileInputStream(fd.getFileDescriptor())){while(input.read()!=-1){}}
    }
    // The destination does not resolve. Only a SOCKS connection can answer it.
    @Test public void getHeadAndReuseActuallyTraverseSelectedProxy()throws Exception {
        try(FakeProxy proxy=new FakeProxy()) {
            JSONObject profile=Core.link("socks5://127.0.0.1:"+proxy.server.getLocalPort());
            JSONObject state=Core.defaults();state.getJSONArray("profiles").put(profile);
            for(String method:new String[]{"GET","HEAD"})for(String mode:Ping.MODES){
                int before=proxy.requests.get(),connections=proxy.connections.get();
                JSONObject settings=Ping.settings(state);settings.put("method",method).put("mode",mode).put("url","http://probe.aura.invalid/check");
                JSONObject result=Ping.measure(state,profile,settings);
                int expected=mode.equals("default")?1:2;
                assertEquals(204,result.getInt("status"));assertTrue(result.getInt("ms")>=1);
                assertEquals(expected,proxy.requests.get()-before);
                assertEquals(mode.equals("double")?2:1,proxy.connections.get()-connections);
            }
            assertEquals(5,proxy.gets.get());assertEquals(5,proxy.heads.get());
        }
    }
    @Test public void tcpAndFailedProxyAreNotReportedAsHttpSuccess()throws Exception {
        try(ServerSocket server=new ServerSocket(0)){
            JSONObject profile=Core.link("socks5://127.0.0.1:"+server.getLocalPort()),state=Core.defaults();state.getJSONArray("profiles").put(profile);
            JSONObject settings=Ping.settings(state);settings.put("method","TCP");
            assertEquals(0,Ping.measure(state,profile,settings).getInt("status"));
        }
        try(ServerSocket closed=new ServerSocket(0)){
            int port=closed.getLocalPort();closed.close();
            JSONObject profile=Core.link("socks5://127.0.0.1:"+port),state=Core.defaults();state.getJSONArray("profiles").put(profile);
            JSONObject settings=Ping.settings(state);settings.put("url","http://probe.aura.invalid/check");
            try{Ping.measure(state,profile,settings);fail("must not fall back to direct HTTP");}catch(Exception expected){assertTrue(expected.getMessage().contains("Нет ответа"));}
        }
    }
    private static final class FakeProxy implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0);
        final AtomicInteger requests=new AtomicInteger(),connections=new AtomicInteger(),gets=new AtomicInteger(),heads=new AtomicInteger();
        FakeProxy()throws IOException {
            Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread worker=new Thread(()->handle(socket));worker.setDaemon(true);worker.start();}catch(IOException ignored){}});accept.setDaemon(true);accept.start();
        }
        private void handle(Socket socket) {
            try(Socket s=socket){
                s.setSoTimeout(20000);DataInputStream in=new DataInputStream(s.getInputStream());OutputStream out=s.getOutputStream();
                if(in.readUnsignedByte()!=5)return;int methods=in.readUnsignedByte();in.readNBytes(methods);out.write(new byte[]{5,0});out.flush();
                if(in.readUnsignedByte()!=5||in.readUnsignedByte()!=1)return;in.readUnsignedByte();int type=in.readUnsignedByte();
                if(type==3)in.readNBytes(in.readUnsignedByte());else in.readNBytes(type==1?4:16);in.readUnsignedShort();
                connections.incrementAndGet();out.write(new byte[]{5,0,0,1,127,0,0,1,0,80});out.flush();
                BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.US_ASCII));String line;
                while((line=reader.readLine())!=null){if(line.startsWith("GET "))gets.incrementAndGet();else if(line.startsWith("HEAD "))heads.incrementAndGet();else return;
                    while((line=reader.readLine())!=null&&!line.isEmpty()){}
                    requests.incrementAndGet();out.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: keep-alive\r\n\r\n".getBytes(StandardCharsets.US_ASCII));out.flush();
                }
            }catch(IOException ignored){}
        }
        public void close()throws IOException{server.close();}
    }
}
