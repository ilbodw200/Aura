package app.aura.android;
import org.json.JSONObject;
import java.util.Locale;

final class SubscriptionUsage {
    static JSONObject parse(String header)throws Exception {
        if(header==null||header.length()>4096)return null;
        JSONObject value=new JSONObject();
        for(String part:header.split(";")){
            String[] pair=part.trim().split("=",2);if(pair.length!=2)continue;
            String key=pair[0].trim().toLowerCase(Locale.ROOT);
            if(!java.util.Arrays.asList("upload","download","total","expire").contains(key))continue;
            try{long n=Long.parseLong(pair[1].trim());if(n>=0)value.put(key,n);}catch(NumberFormatException ignored){}
        }
        return value.length()==0?null:value;
    }
    static String summary(JSONObject usage){
        if(usage==null)return "";String value="";
        if(usage.has("upload")&&usage.has("download"))try{
            long used=Math.addExact(usage.optLong("upload"),usage.optLong("download"));
            value=bytes(used);if(usage.has("total"))value+=" / "+(usage.optLong("total")==0?"∞":bytes(usage.optLong("total")));
        }catch(ArithmeticException ignored){}
        long expire=usage.optLong("expire");if(expire>0&&expire<Long.MAX_VALUE/1000)value+=(value.isEmpty()?"":" · ")+"до "+new java.text.SimpleDateFormat("dd.MM.yyyy",Locale.ROOT).format(new java.util.Date(expire*1000));
        return value;
    }
    private static String bytes(long n){return n<1_000_000?String.format(Locale.ROOT,"%.1f KB",n/1000d):n<1_000_000_000?String.format(Locale.ROOT,"%.1f MB",n/1e6):String.format(Locale.ROOT,"%.1f GB",n/1e9);}
}
