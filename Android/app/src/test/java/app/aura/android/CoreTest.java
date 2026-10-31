package app.aura.android;
import org.junit.Test;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class CoreTest {
    private final String uuid="11111111-2222-4333-8444-555555555555";
    @Test public void realityAndIPv6()throws Exception{JSONObject p=Core.link("vless://"+uuid+"@[2001:db8::1]:443?security=reality&pbk=test&sid=ab#Hello%20World");assertEquals("Hello World",p.getString("name"));assertEquals("2001:db8::1",p.getString("host"));JSONObject s=Core.defaults();s.getJSONArray("profiles").put(p);s.put("selected",p.getString("id"));JSONObject o=new JSONObject(Core.config(s)).getJSONArray("outbounds").getJSONObject(0);assertEquals("test",o.getJSONObject("tls").getJSONObject("reality").getString("public_key"));}
    @Test public void atomicBatchRejectsUnsupportedTransport()throws Exception{String link="vless://"+uuid+"@example.com:443";assertEquals(2,Core.parse(Base64.getEncoder().encodeToString((link+"\ntrojan://secret@two.example:443").getBytes(StandardCharsets.UTF_8))).size());assertThrows(Exception.class,()->Core.parse(link+"\nunknown://bad"));assertThrows(Exception.class,()->Core.link(link+"?type=xhttp"));assertThrows(Exception.class,()->Core.link("happ://crypto"));}
    @Test public void shadowsocksLegacyWithoutFragment()throws Exception{String auth=Base64.getEncoder().encodeToString("aes-128-gcm:secret:colon".getBytes(StandardCharsets.UTF_8));JSONObject p=Core.link("ss://"+auth+"@example.com:8388");assertEquals("secret:colon",p.getString("credential"));assertEquals("example.com",p.getString("name"));String legacy=Base64.getEncoder().encodeToString("aes-128-gcm:secret@example.com:8388".getBytes(StandardCharsets.UTF_8));assertEquals(8388,Core.link("ss://"+legacy).getInt("port"));}
    @Test public void suffixAndCIDRBoundaries()throws Exception{JSONObject r=Core.obj("kind","suffix","value","example.com","action","direct");assertTrue(Core.matches(r,"api.example.com"));assertFalse(Core.matches(r,"notexample.com"));assertTrue(Core.cidr("255.255.255.255","0.0.0.0/0"));assertTrue(Core.cidr("192.168.255.255","192.168.0.0/16"));assertFalse(Core.cidr("192.169.0.0","192.168.0.0/16"));assertFalse(Core.cidr("999.1.1.1","0.0.0.0/0"));assertFalse(Core.cidr("1.2.3.4","1.2.3.0/33"));}
    @Test public void orderedRouting()throws Exception{JSONObject s=Core.defaults();JSONObject p=Core.link("trojan://secret@example.com:443");s.getJSONArray("profiles").put(p);s.put("selected",p.getString("id"));s.getJSONArray("rules").put(Core.obj("id","rule","kind","suffix","value","ads.example","action","block","enabled",true));assertTrue(Core.route(s,"api.ads.example").startsWith("Блокировать"));assertEquals("reject",new JSONObject(Core.config(s)).getJSONObject("route").getJSONArray("rules").getJSONObject(2).getString("action"));s.put("mode","global");assertFalse(Core.config(s).contains("ads.example"));}
    @Test public void backupAuthenticatesAndRestores()throws Exception{JSONObject s=Core.defaults();JSONObject p=Core.link("trojan://secret@example.com:443");s.getJSONArray("profiles").put(p);String password="test-long-password";byte[] data=Backup.export(s,password);assertFalse(new String(data,StandardCharsets.UTF_8).contains("secret"));assertEquals("secret",Backup.restore(data,password).getJSONArray("profiles").getJSONObject(0).getString("credential"));assertThrows(Exception.class,()->Backup.restore(data,"wrong-password"));assertThrows(Exception.class,()->Backup.export(s,"short"));}
    @Test public void stubProfileCannotConnect()throws Exception{JSONObject p=Core.link("vless://"+uuid+"@0.0.0.0:1#EnableHWID");JSONObject s=Core.defaults();s.getJSONArray("profiles").put(p);s.put("selected",p.getString("id"));assertThrows(Exception.class,()->Core.config(s));}
    @Test public void HTTPSCanSaveWithoutCheckingButHTTPCannot()throws Exception{
        Core.validateSubscriptionForSaving("https://provider.example/sub",false,null,0);
        assertThrows(Exception.class,()->Core.validateSubscriptionForSaving("http://provider.example/sub",false,null,0));
        assertThrows(Exception.class,()->Core.validateSubscriptionForSaving("http://provider.example/sub",true,null,1));
        assertThrows(Exception.class,()->Core.validateSubscriptionForSaving("http://provider.example/sub",true,"http://other.example/sub",1));
        Core.validateSubscriptionForSaving("http://provider.example/sub",true,"http://provider.example/sub",1);
    }
    @Test public void realityLinkDefaultsAreAccepted()throws Exception{
        JSONObject p=Core.link("vless://"+uuid+"@example.com:443?type=raw&security=reality&pbk=key&headerType=none&spx=%2F");assertEquals("tcp",p.getJSONObject("query").getString("type"));
    }
}
