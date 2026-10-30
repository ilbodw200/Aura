package app.aura.android;

import io.nekohasekai.libbox.Libbox;
import org.json.JSONObject;
import java.net.URI;

final class Ping {
    static final String[] METHODS={"GET","HEAD","TCP","ICMP"};
    static final String[] MODES={"default","double","keepalive"};
    static JSONObject settings(JSONObject state)throws Exception {
        JSONObject value=state.optJSONObject("ping");
        return value==null?Core.obj("method","GET","mode","default","timeout",7,"url","https://www.gstatic.com/generate_204","displayStatus",false):new JSONObject(value.toString());
    }
    static void validate(JSONObject settings)throws Exception {
        String method=settings.getString("method"),mode=settings.getString("mode");
        if(!java.util.Arrays.asList(METHODS).contains(method)||!java.util.Arrays.asList(MODES).contains(mode))throw new Exception("Неизвестный метод пинга.");
        int timeout=settings.getInt("timeout");if(timeout<5||timeout>15)throw new Exception("Таймаут: от 5 до 15 секунд.");
        URI uri=new URI(settings.getString("url"));
        if(!java.util.Arrays.asList("https","http").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new Exception("Укажите HTTP/HTTPS URL без логина и пароля.");
    }
    static JSONObject measure(JSONObject state,JSONObject profile,JSONObject settings)throws Exception {
        validate(settings);
        if(profile.optBoolean("example"))throw new Exception("Пример не содержит настоящего сервера.");
        JSONObject snapshot=new JSONObject(state.toString());snapshot.put("selected",profile.getString("id"));
        String method=settings.getString("method");
        String config=method.equals("GET")||method.equals("HEAD")?Core.config(snapshot):"{}";
        try {
            return new JSONObject(Libbox.auraProbe(config,profile.getString("host"),profile.getInt("port"),settings.getString("url"),method,settings.getString("mode"),settings.getInt("timeout")*1000));
        } catch(Exception error) { throw new Exception("Нет ответа: "+method+". Проверьте сеть, сервер и таймаут. ICMP может блокироваться."); }
    }
}
