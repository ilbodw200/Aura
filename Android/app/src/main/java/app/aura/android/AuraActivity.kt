package app.aura.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AuraActivity: ComponentActivity() {
    private val model: AuraViewModel by viewModels()
    private var waitingPermission=false
    private val consent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> waitingPermission=false;if(result.resultCode==RESULT_OK) startTunnel(false) else model.message("Разрешение VPN не предоставлено") }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent { AuraApplication(model, connect={toggle()}, selected={id->model.select(id) { restart->if(restart) startTunnel(true) }}, legacy={openLegacy(it)}, systemVPN={startActivity(Intent(Settings.ACTION_VPN_SETTINGS))}, share={shareReport()}, startup={if(!waitingPermission&&AuraVpnService.status=="disconnected") toggle()}) }
    }
    override fun onStart(){super.onStart();model.foreground=true;model.refresh()}
    override fun onStop(){model.foreground=false;super.onStop()}
    private fun toggle(){
        if(waitingPermission)return
        if(AuraVpnService.status in listOf("connected","connecting","reconnecting")){startService(Intent(this,AuraVpnService::class.java).setAction("STOP"));return}
        if(AuraVpnService.status=="disconnecting"||!model.canConnect())return
        val request=VpnService.prepare(this)
        if(request!=null){waitingPermission=true;consent.launch(request)}else startTunnel(false)
    }
    private fun startTunnel(restart:Boolean){
        try {
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            ContextCompat.startForegroundService(this,Intent(this,AuraVpnService::class.java).setAction(if(restart)"RESTART" else "START"))
        }catch(_:Exception){model.message("Android не разрешил запустить VPN. Повторите с открытым приложением.")}
    }
    private fun openLegacy(action:String){
        lifecycleScope.launch { model.appearance(model.state.value.appearance).join();startActivity(Intent(this@AuraActivity,MainActivity::class.java).putExtra("aura.action",action)) }
    }
    private fun shareReport(){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"AURA · диагностический отчёт").putExtra(Intent.EXTRA_TEXT,model.report()),"Экспорт диагностики"))}
}
