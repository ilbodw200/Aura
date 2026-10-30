package app.aura.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.*;

final class DeviceInfo {
    static String hwid(Context context){
        SharedPreferences p=context.getSharedPreferences("device-identity",Context.MODE_PRIVATE);
        String value=p.getString("hwid",null);if(value==null){value=UUID.randomUUID().toString();p.edit().putString("hwid",value).apply();}return value;
    }
    static Map<String,String> headers(Context context){
        Map<String,String> result=new LinkedHashMap<>();String model=Build.MANUFACTURER+" "+Build.MODEL;
        result.put("X-HWID",hwid(context));result.put("X-Device-OS","Android");result.put("X-Ver-OS",Build.VERSION.RELEASE);result.put("X-Device-Model",model);result.put("X-Device-Info","AURA; "+model+"; Android "+Build.VERSION.RELEASE);return result;
    }
}
