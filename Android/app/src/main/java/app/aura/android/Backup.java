package app.aura.android;

import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

final class Backup {
    private static SecretKeySpec derive(String password,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,210000,256);
        try{return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),"AES");}finally{spec.clearPassword();}
    }
    static byte[] export(JSONObject state,String password)throws Exception{
        if(password.length()<12)throw new Exception("Пароль должен содержать минимум 12 символов.");
        byte[] salt=new byte[16],nonce=new byte[12];new SecureRandom().nextBytes(salt);new SecureRandom().nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,derive(password,salt),new GCMParameterSpec(128,nonce));
        byte[] ciphertext=cipher.doFinal(state.toString().getBytes(StandardCharsets.UTF_8));byte[] combined=Arrays.copyOf(nonce,nonce.length+ciphertext.length);System.arraycopy(ciphertext,0,combined,12,ciphertext.length);
        return Core.obj("platform","android","version",1,"rounds",210000,"salt",Base64.getEncoder().encodeToString(salt),"ciphertext",Base64.getEncoder().encodeToString(combined)).toString().getBytes(StandardCharsets.UTF_8);
    }
    static JSONObject restore(byte[] bytes,String password)throws Exception{
        if(bytes.length>8_000_000)throw new Exception("Копия слишком большая.");JSONObject e=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!e.optString("platform").equals("android")||e.optInt("version")!=1||e.optInt("rounds")!=210000)throw new Exception("Неизвестный формат копии.");
        byte[] salt=Base64.getDecoder().decode(e.getString("salt")),combined=Base64.getDecoder().decode(e.getString("ciphertext"));if(salt.length!=16||combined.length<28)throw new Exception("Повреждённая копия.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,derive(password,salt),new GCMParameterSpec(128,Arrays.copyOfRange(combined,0,12)));
        JSONObject state=new JSONObject(new String(cipher.doFinal(combined,12,combined.length-12),StandardCharsets.UTF_8));Core.validateState(state);return state;
    }
}
