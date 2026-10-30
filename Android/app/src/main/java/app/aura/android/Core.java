package app.aura.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Protocol parsing and configuration shared by the UI, service and JVM tests. */
public final class Core {
    private Core() {}
    public static JSONArray array(Object... values) { JSONArray out = new JSONArray(); for (Object value : values) out.put(value); return out; }
    public static JSONObject obj(Object... values) throws Exception { JSONObject out = new JSONObject(); for (int i=0;i<values.length;i+=2) out.put((String) values[i], values[i+1]); return out; }
    static String decode(String s) throws Exception { return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8"); }
    public static void validateSubscriptionURL(String value,boolean allowHTTP)throws Exception{
        URI uri=new URI(value);String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
        if((!scheme.equals("https")&&!(scheme.equals("http")&&allowHTTP))||uri.getHost()==null||uri.getUserInfo()!=null)throw new Exception("Нужна HTTPS-ссылка. Для HTTP требуется отдельное подтверждение и проверка.");
    }
    public static void validateSubscriptionForSaving(String url,boolean allowHTTP,String verifiedURL,int count)throws Exception{
        validateSubscriptionURL(url,allowHTTP);
        if(new URI(url).getScheme().equalsIgnoreCase("http")&&(!allowHTTP||!url.equals(verifiedURL)||count<1))throw new Exception("Сначала разрешите HTTP и проверьте доступность и формат этой ссылки.");
    }
    static byte[] base64(String s) { return Base64.getDecoder().decode(s.replace('-', '+').replace('_', '/').replaceAll("\\s", "")); }
    public static JSONObject defaults() throws Exception {
        return obj("platform", "android", "version", 1, "profiles", new JSONArray(), "subscriptions", new JSONArray(), "rules", new JSONArray(), "selected", "", "mode", "smart", "accent", "mint", "backdrop", "aurora", "theme", "dark", "compact", false, "showHost", true, "hideRecents", true, "radius", 28, "bypassLAN", true, "excludedApps", new JSONArray(), "widgets", array("server","routing","favorites"), "hiddenWidgets", new JSONArray(), "dns", "https://1.1.1.1/dns-query");
    }
    public static List<JSONObject> parse(String input) throws Exception {
        String text=input.trim();
        if (!text.contains("://")) { try { text=new String(base64(text), StandardCharsets.UTF_8); } catch (Exception e) { throw new Exception("Нужна ссылка конфигурации или список ссылок."); } }
        List<JSONObject> result=new ArrayList<>(); int line=0;
        for(String raw:text.split("\\r?\\n")) { line++; raw=raw.trim(); if(raw.isEmpty()||raw.startsWith("#"))continue;
            if(result.size()>=1000)throw new Exception("Максимум 1000 профилей.");
            try { result.add(link(raw)); } catch(Exception e) { throw new Exception("Строка "+line+": "+(e instanceof java.net.URISyntaxException?((java.net.URISyntaxException)e).getReason():e instanceof org.json.JSONException?"JSON-конфигурация повреждена.":e.getMessage())); }
        }
        if(result.isEmpty())throw new Exception("Список конфигураций пуст."); return result;
    }
    public static JSONObject link(String raw) throws Exception {
        int separator=raw.indexOf("://"); if(separator<1)throw new Exception("Неверная ссылка.");
        String type=raw.substring(0,separator).toLowerCase(Locale.ROOT); String payload=raw.substring(separator+3);
        JSONObject q=new JSONObject(), p; String name, host, credential, method="", username=""; int port;
        if(type.equals("happ"))throw new Exception("Фирменные зашифрованные ссылки Happ пока не поддерживаются.");
        if(type.equals("vmess")) {
            JSONObject v=new JSONObject(new String(base64(payload),StandardCharsets.UTF_8));
            host=v.getString("add");port=v.getInt("port");credential=v.getString("id");name=v.optString("ps",host);
            for(String key:new String[]{"sni","host","path","scy","aid"})if(v.has(key))q.put(key,v.get(key).toString());
            q.put("type",v.optString("net","tcp"));q.put("security",v.optString("tls").equals("tls")?"tls":"none");
        } else if(type.equals("ss")) {
            type="shadowsocks";int hash=payload.indexOf('#');name=hash>=0?decode(payload.substring(hash+1)):"";if(hash>=0)payload=payload.substring(0,hash);
            if(!payload.contains("@"))payload=new String(base64(payload),StandardCharsets.UTF_8);
            int at=payload.lastIndexOf('@');if(at<1)throw new Exception("Неверная Shadowsocks-ссылка.");
            String auth=payload.substring(0,at);if(!auth.contains(":"))auth=new String(base64(auth),StandardCharsets.UTF_8);
            int colon=auth.indexOf(':');if(colon<1)throw new Exception("В ссылке нет метода шифрования.");
            method=auth.substring(0,colon);credential=decode(auth.substring(colon+1));
            URI uri=new URI("ss://"+payload.substring(at+1));host=uri.getHost();port=uri.getPort();
            if(uri.getRawQuery()!=null&&!uri.getRawQuery().isEmpty())throw new Exception("Shadowsocks plugins пока не поддерживаются.");
        } else {
            if(type.equals("hy2"))type="hysteria2";if(type.equals("socks5"))type="socks";
            if(!Arrays.asList("vless","trojan","socks","hysteria2").contains(type))throw new Exception("Неподдерживаемый протокол.");
            URI uri=new URI(raw);host=uri.getHost();port=uri.getPort();name=uri.getRawFragment()==null?"":decode(uri.getRawFragment());
            String auth=uri.getRawUserInfo()==null?"":decode(uri.getRawUserInfo());
            if(type.equals("socks")){int colon=auth.indexOf(':');username=colon<0?auth:auth.substring(0,colon);credential=colon<0?"":auth.substring(colon+1);}else credential=auth;
            if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")){int equals=part.indexOf('=');q.put(decode(equals<0?part:part.substring(0,equals)),equals<0?"":decode(part.substring(equals+1)));}
        }
        if(host!=null&&host.startsWith("["))host=host.substring(1,host.length()-1);
        if(name.isEmpty())name=host;
        if(q.optString("type").equals("raw"))q.put("type","tcp");
        p=obj("id",UUID.randomUUID().toString(),"name",name,"type",type,"host",host,"port",port,"credential",credential,"username",username,"method",method,"query",q,"favorite",false,"source",raw);
        validate(p); return p;
    }
    public static void validate(JSONObject p) throws Exception {
        String type=p.getString("type"),host=p.getString("host");int port=p.getInt("port");
        if(!Arrays.asList("vless","vmess","trojan","shadowsocks","socks","hysteria2").contains(type))throw new Exception("Неизвестный протокол.");
        if(host.isEmpty()||host.length()>253||host.matches(".*\\s.*")||port<1||port>65535||p.getString("name").isEmpty())throw new Exception("Проверьте адрес, порт и название.");
        if(!type.equals("socks")&&p.optString("credential").isEmpty())throw new Exception("Нет ключа или пароля.");
        if(type.equals("vless")||type.equals("vmess")){try{String id=p.getString("credential");if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw new Exception();}catch(Exception e){throw new Exception("Нужен корректный UUID.");}}
        JSONObject q=p.getJSONObject("query");
        Set<String> allowed=new HashSet<>(Arrays.asList("type","security","sni","peer","fp","pbk","sid","flow","path","host","serviceName","alpn","encryption","scy","aid","allowInsecure","insecure","obfs","obfs-password","spx","headerType","packetEncoding","fm","mode","authority"));
        ProviderOptions.fragment(p);
        Iterator<String> keys=q.keys();while(keys.hasNext()){String key=keys.next();if(!allowed.contains(key))throw new Exception("Параметр "+key+" пока не поддерживается.");}
        if(!Arrays.asList("tcp","ws","grpc","http").contains(q.optString("type","tcp")))throw new Exception("Этот транспорт пока не поддерживается.");
        if(!Arrays.asList("none","tls","reality","").contains(q.optString("security","none")))throw new Exception("Неизвестный режим TLS.");
        if(q.optString("security").equals("reality")&&q.optString("pbk").isEmpty())throw new Exception("Для Reality нужен ключ pbk.");
        if(q.optString("insecure").equals("1")||q.optString("allowInsecure").equals("1"))throw new Exception("Проверка TLS обязательна; исправьте сертификат сервера.");
        if(!Arrays.asList("","xtls-rprx-vision").contains(q.optString("flow")))throw new Exception("Неподдерживаемый flow.");
        if(!Arrays.asList("","none").contains(q.optString("headerType")))throw new Exception("Этот TCP headerType пока не поддерживается.");
        if(q.has("packetEncoding")&&!Arrays.asList("xudp","packetaddr").contains(q.getString("packetEncoding")))throw new Exception("Неизвестный packetEncoding.");
        if(q.has("encryption")&&!q.getString("encryption").equals("none"))throw new Exception("Неподдерживаемый VLESS encryption.");
        if(type.equals("shadowsocks")&&!Arrays.asList("aes-128-gcm","aes-256-gcm","chacha20-ietf-poly1305","2022-blake3-aes-128-gcm","2022-blake3-aes-256-gcm","2022-blake3-chacha20-poly1305").contains(p.getString("method")))throw new Exception("Этот метод Shadowsocks пока не поддерживается.");
    }
    public static JSONObject selected(JSONObject state) throws Exception { return find(state.getJSONArray("profiles"),state.optString("selected")); }
    public static JSONObject find(JSONArray items,String id) throws Exception { for(int i=0;i<items.length();i++)if(items.getJSONObject(i).getString("id").equals(id))return items.getJSONObject(i);return null; }
    public static String config(JSONObject state) throws Exception {
        JSONObject p=selected(state);if(p==null)throw new Exception("Сначала выберите сервер.");validate(p);
        if(p.getString("host").equals("0.0.0.0")||p.getString("host").equals("::"))throw new Exception("Провайдер вернул служебный профиль. Включите отправку HWID и обновите подписку.");
        JSONObject q=p.getJSONObject("query");String type=p.getString("type");
        JSONObject out=obj("tag","proxy","type",type,"server",p.getString("host"),"server_port",p.getInt("port"));
        JSONObject fragment=ProviderOptions.fragment(p);if(fragment!=null)out.put("aura_tls_fragment",fragment);
        switch(type){
            case "vless":out.put("uuid",p.getString("credential")).put("flow",q.optString("flow"));break;
            case "vmess":out.put("uuid",p.getString("credential")).put("security",q.optString("scy","auto")).put("alter_id",Integer.parseInt(q.optString("aid","0")));break;
            case "trojan":case "hysteria2":out.put("password",p.getString("credential"));break;
            case "shadowsocks":out.put("method",p.getString("method")).put("password",p.getString("credential"));break;
            case "socks":out.put("version","5").put("username",p.optString("username")).put("password",p.optString("credential"));break;
        }
        String security=q.optString("security",type.equals("trojan")||type.equals("hysteria2")?"tls":"none");
        if((type.equals("vless")||type.equals("vmess"))&&q.has("packetEncoding"))out.put("packet_encoding",q.getString("packetEncoding"));
        if(security.equals("tls")||security.equals("reality")){
            JSONObject tls=obj("enabled",true,"server_name",q.optString("sni",q.optString("peer",p.getString("host"))));
            if(q.has("fp"))tls.put("utls",obj("enabled",true,"fingerprint",q.getString("fp")));
            if(q.has("alpn"))tls.put("alpn",new JSONArray(Arrays.asList(q.getString("alpn").split(","))));
            if(security.equals("reality"))tls.put("reality",obj("enabled",true,"public_key",q.getString("pbk"),"short_id",q.optString("sid")));
            out.put("tls",tls);
        }
        String transport=q.optString("type","tcp");
        if(!transport.equals("tcp")){
            JSONObject t=obj("type",transport);
            if(transport.equals("grpc")){t.put("service_name",q.optString("serviceName"));if(!q.optString("authority").isEmpty())t.put("authority",q.getString("authority"));}
            else{t.put("path",q.optString("path","/"));if(q.has("host")){if(transport.equals("http"))t.put("host",new JSONArray(Arrays.asList(q.getString("host").split(","))));else t.put("headers",obj("Host",q.getString("host")));}}
            out.put("transport",t);
        }
        if(type.equals("hysteria2")&&q.has("obfs")){if(!q.getString("obfs").equals("salamander")||q.optString("obfs-password").isEmpty())throw new Exception("Для Hysteria2 поддерживается salamander с паролем.");out.put("obfs",obj("type","salamander","password",q.getString("obfs-password")));}
        JSONArray rules=array(obj("protocol","dns","action","hijack-dns"));
        if(state.optString("mode","smart").equals("smart")){
            JSONArray input=state.getJSONArray("rules");for(int i=0;i<input.length();i++){JSONObject r=input.getJSONObject(i);if(!r.optBoolean("enabled",true))continue;validateRule(r);String key;switch(r.getString("kind")){case "domain":key="domain";break;case "suffix":key="domain_suffix";break;case "keyword":key="domain_keyword";break;default:key="ip_cidr";}
                JSONObject rule=obj(key,array(normalized(r.getString("value"))));String action=r.getString("action");if(action.equals("block"))rule.put("action","reject");else rule.put("outbound",action);rules.put(rule);
            }
        }
        if(state.optBoolean("bypassLAN",true))rules.put(obj("ip_is_private",true,"outbound","direct"));
        String finalRoute=state.optString("mode").equals("direct")?"direct":"proxy";
        return obj("log",obj("level","warn"),"dns",obj("servers",array(obj("tag","secure","address",state.optString("dns","https://1.1.1.1/dns-query"),"detour",finalRoute)),"strategy","ipv4_only"),"inbounds",array(obj("type","tun","tag","tun-in","address",array("172.19.0.1/30","fdfe:dcba:9876::1/126"),"mtu",1400,"auto_route",true,"stack","gvisor","sniff",true)),"outbounds",array(out,obj("type","direct","tag","direct")),"route",obj("rules",rules,"final",finalRoute,"auto_detect_interface",true)).toString();
    }
    static String normalized(String input){return input.trim().toLowerCase(Locale.ROOT).replaceAll("^\\.+|\\.+$","");}
    static long ipv4(String value){String[] parts=value.split("\\.",-1);if(parts.length!=4)return -1;long n=0;try{for(String p:parts){if(!p.matches("[0-9]{1,3}"))return -1;int v=Integer.parseInt(p);if(v>255)return -1;n=n*256+v;}return n;}catch(Exception e){return -1;}}
    public static boolean cidr(String host,String cidr){String[] parts=cidr.split("/",-1);if(parts.length!=2)return false;try{int bits=Integer.parseInt(parts[1]);long ip=ipv4(host),network=ipv4(parts[0]);if(bits<0||bits>32||ip<0||network<0)return false;long mask=bits==0?0:(0xffffffffL<<(32-bits))&0xffffffffL;return (ip&mask)==(network&mask);}catch(Exception e){return false;}}
    public static void validateRule(JSONObject r)throws Exception{
        String value=r.getString("value"),kind=r.getString("kind");
        if(!Arrays.asList("domain","suffix","keyword","cidr").contains(kind)||!Arrays.asList("proxy","direct","block").contains(r.getString("action")))throw new Exception("Неверный тип правила.");
        if(kind.equals("cidr")){if(!cidr(value.split("/",-1)[0],value))throw new Exception("Нужен IPv4/CIDR, например 192.168.0.0/16.");}
        else if(normalized(value).isEmpty()||value.matches(".*\\s.*")||value.contains("/")||value.contains(":"))throw new Exception("Укажите домен или ключевое слово без схемы и пути.");
    }
    public static boolean matches(JSONObject rule,String input)throws Exception{String host=normalized(input),needle=normalized(rule.getString("value"));switch(rule.getString("kind")){case "domain":return host.equals(needle);case "suffix":return host.equals(needle)||host.endsWith("."+needle);case "keyword":return host.contains(needle);default:return cidr(host,needle);}}
    public static String route(JSONObject s,String input)throws Exception{
        if(s.optString("mode").equals("smart")){JSONArray rules=s.getJSONArray("rules");for(int i=0;i<rules.length();i++){JSONObject r=rules.getJSONObject(i);if(r.optBoolean("enabled",true)&&matches(r,input))return actionTitle(r.getString("action"))+" · "+r.getString("value");}}
        if(s.optBoolean("bypassLAN",true))for(String range:new String[]{"10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","127.0.0.0/8","169.254.0.0/16"})if(cidr(input.trim(),range))return "Напрямую · локальная сеть";
        return (s.optString("mode").equals("direct")?"Напрямую":"Прокси")+" · по умолчанию";
    }
    public static String actionTitle(String action){return action.equals("direct")?"Напрямую":action.equals("block")?"Блокировать":"Прокси";}
    public static void validateState(JSONObject s)throws Exception{
        if(!s.optString("platform").equals("android")||s.optInt("version")!=1)throw new Exception("Нужна резервная копия AURA для Android.");
        JSONArray profiles=s.getJSONArray("profiles");if(profiles.length()>1000)throw new Exception("Слишком много профилей.");Set<String> ids=new HashSet<>();
        for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);validate(p);if(!ids.add(p.getString("id")))throw new Exception("Повтор идентификаторов в копии.");}
        for(int i=0;i<s.getJSONArray("rules").length();i++)validateRule(s.getJSONArray("rules").getJSONObject(i));
        if(!Arrays.asList("https://1.1.1.1/dns-query","https://8.8.8.8/dns-query","https://9.9.9.9/dns-query").contains(s.getString("dns")))throw new Exception("Неизвестный DNS.");
        s.put("radius",Math.max(12,Math.min(36,s.optInt("radius",28))));
    }
}
