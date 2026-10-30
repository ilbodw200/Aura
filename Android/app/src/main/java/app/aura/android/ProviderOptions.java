package app.aura.android;
import org.json.*;
import java.util.*;

final class ProviderOptions {
    static JSONObject fragment(JSONObject p)throws Exception {
        JSONObject q=p.getJSONObject("query");String type=p.getString("type");
        if(!q.optString("mode").isEmpty()&&(!q.optString("type").equals("grpc")||!q.optString("mode").equals("gun")))throw new Exception("Поддерживается gRPC mode=gun; режим не был заменён.");
        String authority=q.optString("authority");if(!authority.isEmpty()&&(!q.optString("type").equals("grpc")||authority.length()>253||authority.matches(".*[\\s/@].*")))throw new Exception("Некорректный gRPC authority.");
        String raw=q.optString("fm");if(raw.isEmpty())return null;if(raw.length()>8192)throw new Exception("fm слишком большой.");
        JSONObject mask;try{mask=new JSONObject(raw);}catch(JSONException error){throw new Exception("fm должен содержать JSON FinalMask.");}
        if(mask.length()==0)return null;
        if(type.equals("hysteria2")&&mask.has("quicParams")){
            JSONObject quic=mask.optJSONObject("quicParams");if(mask.length()!=1||quic==null||!only(quic,"congestion","debug")||!quic.optString("congestion").equals("bbr")||(quic.has("debug")&&!Boolean.FALSE.equals(quic.get("debug"))))throw new Exception("Для Hysteria2 поддерживается fm.quicParams с BBR и выключенной отладкой.");
            return null;
        }
        JSONArray tcp=mask.optJSONArray("tcp");JSONObject entry=tcp!=null&&tcp.length()==1?tcp.optJSONObject(0):null;JSONObject settings=entry==null?null:entry.optJSONObject("settings");
        if(mask.length()!=1||entry==null||entry.length()!=2||!entry.optString("type").equals("fragment")||settings==null||!only(settings,"packets","length","delay")||!settings.optString("packets").equals("tlshello")||!Arrays.asList("tls","reality").contains(q.optString("security",type.equals("trojan")||type.equals("hysteria2")?"tls":"none")))throw new Exception("Этот fm пока не поддерживается. Доступна TCP-фрагментация tlshello с length/delay; маска не была удалена.");
        int[] length=range(settings.opt("length"),1,16384),delay=range(settings.opt("delay"),0,100);
        return Core.obj("length_min",length[0],"length_max",length[1],"delay_min",delay[0],"delay_max",delay[1]);
    }
    private static boolean only(JSONObject object,String... keys){Set<String> allowed=new HashSet<>(Arrays.asList(keys));Iterator<String> it=object.keys();while(it.hasNext())if(!allowed.contains(it.next()))return false;return true;}
    private static int[] range(Object object,int min,int max)throws Exception {
        if(!(object instanceof String)||!((String)object).matches("[0-9]{1,5}(-[0-9]{1,5})?"))throw new Exception("В fm нужны допустимые строковые диапазоны length/delay.");
        String[] parts=((String)object).split("-");int low=Integer.parseInt(parts[0]),high=Integer.parseInt(parts[parts.length-1]);if(low<min||low>high||high>max)throw new Exception("Диапазон fm вне допустимых границ.");return new int[]{low,high};
    }
}
