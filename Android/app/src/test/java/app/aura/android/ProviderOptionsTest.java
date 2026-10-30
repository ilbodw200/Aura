package app.aura.android;
import org.junit.Test;
import org.json.*;
import java.net.URLEncoder;
import static org.junit.Assert.*;

public class ProviderOptionsTest {
    @Test public void providerVariantsImportWithoutDiscardingMasks()throws Exception {
        for(String link:ProviderFixtures.links()){
            JSONObject profile=Core.link(link),state=Core.defaults();state.getJSONArray("profiles").put(profile);state.put("selected",profile.getString("id"));
            JSONObject outbound=new JSONObject(Core.config(state)).getJSONArray("outbounds").getJSONObject(0);
            if(profile.getString("type").equals("vless")&&profile.getJSONObject("query").has("fm"))assertEquals(10,outbound.getJSONObject("aura_tls_fragment").getInt("length_min"));
            if(!profile.getJSONObject("query").optString("authority").isEmpty())assertEquals("front.example.com",outbound.getJSONObject("transport").getString("authority"));
        }
        assertEquals(ProviderFixtures.links().length,Core.parse(String.join("\n",ProviderFixtures.links())).size());
    }
    @Test public void unsafeOrUnknownMasksAreRejected()throws Exception {
        String base="vless://11111111-2222-4333-8444-555555555555@example.com:443?security=tls&fm=";
        for(String fm:new String[]{"{broken","{\"tcp\":[{\"type\":\"sudoku\"}]}","{\"tcp\":[{\"type\":\"fragment\",\"settings\":{\"packets\":\"tlshello\",\"length\":\"0-20\",\"delay\":\"0\"}}]}"}){
            try{Core.link(base+URLEncoder.encode(fm,"UTF-8"));fail("unknown/unsafe fm accepted");}catch(Exception expected){}
        }
    }
}
