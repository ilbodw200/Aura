package app.aura.android;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DashboardTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String description,String className){
        if(node==null)return null;
        if((description==null||(node.getContentDescription()!=null&&node.getContentDescription().toString().startsWith(description)))&&(className==null||className.contentEquals(node.getClassName())))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=find(node.getChild(i),description,className);if(result!=null)return result;}return null;
    }
    private AccessibilityNodeInfo node(String description,String className){return find(instrumentation.getUiAutomation().getRootInActiveWindow(),description,className);}
    private void capture(String file)throws Exception{try(android.os.ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/"+file);java.io.FileInputStream input=new java.io.FileInputStream(fd.getFileDescriptor())){while(input.read()!=-1){}}}
    @Test public void serverPanelScrollsSearchesAndSelectsLastOf57()throws Exception{
        Context context=instrumentation.getTargetContext();Vault vault=new Vault(context);JSONObject previous=vault.load();MainActivity activity=null;
        try{
            JSONObject state=Core.defaults();state.put("hideRecents",false).put("reduceMotion",true);String lastId="";
            for(int i=1;i<=57;i++){JSONObject p=Core.link("socks5://server"+i+".example.invalid:1080#Server");p.put("name",String.format(java.util.Locale.ROOT,"Сервер %02d",i));state.getJSONArray("profiles").put(p);if(i==1)state.put("selected",p.getString("id"));lastId=p.getString("id");}
            vault.save(state);activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();
            assertNotNull(node("Подключить VPN",null));capture("Aura-Minimal-Home.png");
            AccessibilityNodeInfo selector=node("Открыть серверы",null);assertNotNull(selector);assertTrue(selector.performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync();
            AccessibilityNodeInfo grid=node(null,"android.widget.GridView");assertNotNull(grid);
            for(int i=0;i<30&&node("Сервер 57,",null)==null;i++){assertTrue(grid.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));instrumentation.waitForIdleSync();grid=node(null,"android.widget.GridView");}
            assertNotNull("The final server is reachable by scrolling",node("Сервер 57,",null));capture("Aura-Minimal-Servers.png");
            AccessibilityNodeInfo search=node("Поиск серверов",null);assertNotNull(search);Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Сервер 57");assertTrue(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text));instrumentation.waitForIdleSync();
            AccessibilityNodeInfo last=node("Сервер 57,",null);assertNotNull(last);assertTrue(last.performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync();
            assertEquals(lastId,vault.load().getString("selected"));assertNotNull(node("Открыть серверы",null));assertNotNull(node("Подключить VPN",null));capture("Aura-Minimal-Selected.png");
        }finally{if(activity!=null){MainActivity current=activity;instrumentation.runOnMainSync(current::finish);}vault.save(previous);}
    }
}
