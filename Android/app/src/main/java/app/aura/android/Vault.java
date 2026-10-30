package app.aura.android;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class Vault {
    private static final String KEY="aura.local.v1";
    private final AtomicFile file;
    Vault(Context context){file=new AtomicFile(new File(context.getNoBackupFilesDir(),"profiles.aura"));}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY))return (SecretKey)store.getKey(KEY,null);
        KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return gen.generateKey();
    }
    synchronized JSONObject load()throws Exception{
        if(!file.getBaseFile().exists())return Core.defaults();
        byte[] combined=file.readFully();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(combined,0,12)));
        JSONObject state=new JSONObject(new String(cipher.doFinal(combined,12,combined.length-12),StandardCharsets.UTF_8));Core.validateState(state);return state;
    }
    synchronized void save(JSONObject state)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(state.toString().getBytes(StandardCharsets.UTF_8));FileOutputStream stream=null;
        try{stream=file.startWrite();stream.write(cipher.getIV());stream.write(encrypted);file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
    }
    static List<JSONObject> fetch(String text,Map<String,String> metadata)throws Exception{return fetch(text,metadata,false);}
    static List<JSONObject> fetch(String text,Map<String,String> metadata,boolean allowHTTP)throws Exception{
        boolean identityAllowed=true;
        URL url=new URL(text);for(int redirects=0;redirects<6;redirects++){
            Core.validateSubscriptionURL(url.toString(),allowHTTP);
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("User-Agent","Aura-Android/0.1");
            if(url.getProtocol().equals("https")&&identityAllowed&&metadata!=null)for(Map.Entry<String,String> entry:metadata.entrySet())connection.setRequestProperty(entry.getKey(),entry.getValue());
            try{int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new Exception("Нет адреса перенаправления.");URL next=new URL(url,location);if(url.getProtocol().equals("https")&&!next.getProtocol().equals("https"))throw new Exception("Переход с HTTPS на HTTP заблокирован.");if(!next.getHost().equalsIgnoreCase(url.getHost())||next.getPort()!=url.getPort())identityAllowed=false;url=next;continue;}if(code<200||code>=300)throw new Exception("Сервер подписки вернул HTTP "+code+".");
                try(InputStream input=connection.getInputStream()){return Core.parse(new String(readLimit(input,2_000_000),StandardCharsets.UTF_8));}
            }finally{connection.disconnect();}
        }throw new Exception("Слишком много перенаправлений.");
    }
    static byte[] readLimit(InputStream input,int limit)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>limit)throw new Exception("Файл слишком большой.");out.write(buffer,0,n);}return out.toByteArray();}
}
