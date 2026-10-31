package app.aura.android;

import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class RuntimeConfigTest {
    @Test public void mtuIPv6AndSplitPolicyAreRealConfiguration()throws Exception{
        JSONObject state=Core.defaults(),profile=Core.link("socks5://127.0.0.1:1080");state.getJSONArray("profiles").put(profile);state.put("selected",profile.getString("id")).put("mtu",1280);
        JSONObject config=new JSONObject(Core.config(state));assertEquals(1280,config.getJSONArray("inbounds").getJSONObject(0).getInt("mtu"));
        assertTrue(config.getJSONObject("route").getJSONArray("rules").toString().contains("ip_version"));
        state.put("blockIPv6",false);assertFalse(new JSONObject(Core.config(state)).getJSONObject("route").getJSONArray("rules").toString().contains("ip_version"));
        state.put("mtu",1200);assertThrows(Exception.class,()->Core.config(state));state.put("mtu",1400).put("splitMode","include");assertThrows(Exception.class,()->Core.config(state));state.put("includedApps",Core.array("example.app"));Core.config(state);
    }
    @Test public void ipBodyParsesChunkedAndBoundedResponses()throws Exception{
        assertEquals("203.0.113.2",TunnelHttp.body(stream("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nB\r\n203.0.113.2\r\n0\r\n\r\n")));
        assertEquals("203.0.113.2",TunnelHttp.body(stream("HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\n203.0.113.2")));
        assertThrows(IOException.class,()->TunnelHttp.body(stream("HTTP/1.1 302 Found\r\n\r\n")));
        assertThrows(IOException.class,()->TunnelHttp.body(stream("HTTP/1.1 200 OK\r\nContent-Length: 9000\r\n\r\n")));
    }
    private InputStream stream(String s){return new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));}
}
