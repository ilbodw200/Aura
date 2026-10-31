package app.aura.android;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import io.nekohasekai.libbox.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class) public class SmokeTest {
    @Test public void nativeCoreAcceptsProviderMasksAndGrpcAuthority()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SetupOptions options=new SetupOptions();options.setBasePath(context.getFilesDir().getAbsolutePath());options.setWorkingPath(context.getFilesDir().getAbsolutePath());options.setTempPath(context.getCacheDir().getAbsolutePath());Libbox.setup(options);
        for(String link:ProviderFixtures.links()){
            JSONObject profile=Core.link(link),state=Core.defaults();state.getJSONArray("profiles").put(profile);state.put("selected",profile.getString("id"));Libbox.checkConfig(Core.config(state));
        }
    }
    @Test public void nativeCoreAcceptsAllSixProtocolConfigurations()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SetupOptions options=new SetupOptions();options.setBasePath(context.getFilesDir().getAbsolutePath());options.setWorkingPath(context.getFilesDir().getAbsolutePath());options.setTempPath(context.getCacheDir().getAbsolutePath());Libbox.setup(options);
        String uuid="11111111-2222-4333-8444-555555555555";String vmess=Base64.getEncoder().encodeToString(Core.obj("add","example.com","port",443,"id",uuid,"net","tcp","tls","tls").toString().getBytes(StandardCharsets.UTF_8));
        String[] links={"vless://"+uuid+"@example.com:443?security=tls","vmess://"+vmess,"trojan://password@example.com:443","ss://"+Base64.getEncoder().encodeToString("aes-128-gcm:password".getBytes(StandardCharsets.UTF_8))+"@example.com:8388","socks5://user:password@example.com:1080","hysteria2://password@example.com:443"};
        for(String link:links){JSONObject p=Core.link(link),state=Core.defaults();state.getJSONArray("profiles").put(p);state.put("selected",p.getString("id"));Libbox.checkConfig(Core.config(state));}
        assertFalse(Libbox.version().isEmpty());
    }
    @Test public void activityStarts(){android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();AuraActivity activity=(AuraActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),AuraActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));assertFalse(activity.isFinishing());instrumentation.runOnMainSync(activity::finish);}
}
