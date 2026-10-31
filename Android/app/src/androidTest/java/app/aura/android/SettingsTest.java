package app.aura.android;

import android.app.Instrumentation;
import android.content.*;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String label){if(node==null)return null;if((node.getContentDescription()!=null&&node.getContentDescription().toString().equals(label))||(node.getText()!=null&&node.getText().toString().equals(label)))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=find(node.getChild(i),label);if(result!=null)return result;}return null;}
    private AccessibilityNodeInfo node(String label){long deadline=SystemClock.uptimeMillis()+5000;do{AccessibilityNodeInfo node=find(instrumentation.getUiAutomation().getRootInActiveWindow(),label);if(node!=null)return node;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<deadline);return null;}
    private void idle()throws Exception{instrumentation.waitForIdleSync();instrumentation.getUiAutomation().waitForIdle(250,5000);}
    private void click(String label)throws Exception{AccessibilityNodeInfo node=node(label);assertNotNull(label,node);assertTrue(label,node.performAction(AccessibilityNodeInfo.ACTION_CLICK));idle();}
    @Test public void styledSettingsSavePingAndCancelDraft()throws Exception{
        Context context=instrumentation.getTargetContext();Vault vault=new Vault(context);JSONObject previous=vault.load();MainActivity activity=null;
        try{
            JSONObject state=Core.defaults();state.put("hideRecents",false);vault.save(state);activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            assertNotNull(node("Настройки пинга"));click("Настройки AURA");assertNotNull(node("Настройки"));
            try(android.os.ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/Aura-Settings.png");java.io.FileInputStream input=new java.io.FileInputStream(fd.getFileDescriptor())){while(input.read()!=-1){}}
            click("Настройки пинга, GET");click("Через прокси · HEAD");click("Сохранить");assertEquals("HEAD",Ping.settings(vault.load()).getString("method"));
            click("Настройки пинга, HEAD");click("TCP");click("Назад");assertEquals("HEAD",Ping.settings(vault.load()).getString("method"));assertNotNull(node("На главный экран"));click("На главный экран");assertNotNull(node("Настройки AURA"));
        }finally{if(activity!=null){MainActivity current=activity;instrumentation.runOnMainSync(current::finish);}vault.save(previous);}
    }
}
