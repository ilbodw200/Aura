package app.aura.android;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class PingSettingsTest {
    @Test public void oldVaultGetsDefaultSettingsWithoutMutation()throws Exception {
        JSONObject state=Core.defaults();JSONObject settings=Ping.settings(state);
        assertEquals("GET",settings.getString("method"));assertEquals(7,settings.getInt("timeout"));
        assertFalse(state.has("ping"));Ping.validate(settings);
        settings.put("url","https://user:password@example.com");
        try{Ping.validate(settings);fail("credentials not permitted");}catch(Exception expected){}
        settings=Ping.settings(state);settings.put("timeout",16);
        try{Ping.validate(settings);fail("timeout must be bounded");}catch(Exception expected){}
    }
    @Test public void providerUsageIsOptionalAndBounded()throws Exception {
        assertNull(SubscriptionUsage.parse(null));assertNull(SubscriptionUsage.parse("download=-5; expire=bad"));
        JSONObject value=SubscriptionUsage.parse("upload=100; download=200; total=0; expire=1900000000");
        assertEquals(100,value.getLong("upload"));assertTrue(SubscriptionUsage.summary(value).contains("∞"));
        assertEquals("",SubscriptionUsage.summary(SubscriptionUsage.parse("upload=9223372036854775807; download=1")));
    }
}
