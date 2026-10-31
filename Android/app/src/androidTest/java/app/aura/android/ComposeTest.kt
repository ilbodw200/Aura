package app.aura.android

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun realVaultDrivesServerSelectionPingDraftAndAppearance() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val vault=Vault(context);val previous=vault.load();var activity:AuraActivity?=null
        val repository=AuraRepository(context.applicationContext as Application)
        val appearance=runBlocking{repository.appearance.first()}
        try {
            val state=Core.defaults().put("hideRecents",false)
            var last=""
            for(i in 1..57){val p=Core.link("socks5://server$i.example.invalid:1080#Test");p.put("name","🇩🇪 Сервер %02d".format(i));state.getJSONArray("profiles").put(p);if(i==1)state.put("selected",p.getString("id"));last=p.getString("id")}
            vault.save(state);runBlocking{repository.appearance(AuraAppearance(effects=false))}
            activity=instrumentation.startActivitySync(Intent(context,AuraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AuraActivity
            compose.waitUntil(10000){compose.onAllNodesWithText("AURA VPN").fetchSemanticsNodes().isNotEmpty()}
            capture("Aura-Compose-Home.png")
            compose.onNodeWithContentDescription("Серверы").performClick()
            compose.onNodeWithTag("servers.list").performScrollToNode(hasTestTag("server.$last"))
            compose.onNodeWithTag("server.$last").assertIsDisplayed()
            capture("Aura-Compose-Servers.png")
            compose.onNodeWithTag("servers.search").performTextInput("Сервер 57")
            compose.onNodeWithTag("server.$last").performClick()
            compose.waitUntil(5000){vault.load().optString("selected")==last}
            compose.onNodeWithContentDescription("В избранное").performClick()
            compose.waitUntil(5000){Core.find(vault.load().getJSONArray("profiles"),last).optBoolean("favorite")}
            compose.onNodeWithContentDescription("Настройки").performClick()
            capture("Aura-Compose-Settings.png")
            compose.onNodeWithContentDescription("Настройки пинга").performClick()
            capture("Aura-Compose-Ping.png")
            compose.onNodeWithText("Через прокси · HEAD").performClick()
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil(5000){Ping.settings(vault.load()).optString("method")=="HEAD"}
            compose.onNodeWithContentDescription("Настройки пинга").performClick()
            compose.onNodeWithText("TCP").performClick()
            compose.onNodeWithContentDescription("Назад").performClick()
            assertEquals("HEAD",Ping.settings(vault.load()).getString("method"))
            compose.onNodeWithText("Cyber Blue").performScrollTo().performClick()
            compose.waitUntil(5000){runBlocking{repository.appearance.first()}.accent=="blue"}
            compose.onNodeWithContentDescription("Главная").performClick()
            compose.onNodeWithText("🇩🇪 Сервер 57").assertIsDisplayed()
            compose.onNodeWithContentDescription("Подключить VPN").assertIsDisplayed()
        } finally {
            activity?.let { current->instrumentation.runOnMainSync {current.finish()} }
            vault.save(previous);runBlocking{repository.appearance(appearance)}
        }
    }
    private fun capture(name:String){val ui=InstrumentationRegistry.getInstrumentation();compose.waitForIdle();ui.uiAutomation.executeShellCommand("screencap -p /sdcard/$name").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.copyTo(java.io.OutputStream.nullOutputStream())}}}
}
