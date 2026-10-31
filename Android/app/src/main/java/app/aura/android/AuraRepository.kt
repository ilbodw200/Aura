package app.aura.android

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

private val Context.auraPreferences by preferencesDataStore("aura_interface")
data class AuraAppearance(val dark: Boolean = true, val accent: String = "purple", val intensity: Float = .6f, val effects: Boolean = true)
data class AuraServer(val id: String, val name: String, val kind: String, val transport: String, val group: String, val favorite: Boolean, val latency: Int?, val failed: Boolean, val checkedAt: Long) {
    val flag: String get() { val p = name.codePoints().toArray(); for (i in 0 until p.size - 1) if (p[i] in 0x1f1e6..0x1f1ff && p[i+1] in 0x1f1e6..0x1f1ff) return String(p, i, 2); return "◎" }
}
data class AuraState(
    val raw: JSONObject = Core.defaults(), val loaded: Boolean = false, val appearance: AuraAppearance = AuraAppearance(),
    val status: String = "disconnected", val error: String? = null, val probing: String? = null,
    val rx: Long = 0, val tx: Long = 0, val rxRate: Long = 0, val txRate: Long = 0,
    val metrics: Boolean = false, val ip: String = "", val seconds: Long = 0
) {
    val running get() = status in listOf("connecting", "connected", "reconnecting", "disconnecting")
    val transition get() = status in listOf("connecting", "disconnecting", "reconnecting")
    val title get() = when(status) { "connected" -> "Подключено"; "connecting" -> "Подключение"; "disconnecting" -> "Отключение"; "reconnecting" -> "Восстановление сети"; "error" -> "Ошибка подключения"; else -> "Не подключено" }
    val servers: List<AuraServer> get() { val all=raw.optJSONArray("profiles") ?: return emptyList(); return (0 until all.length()).map { i -> val p=all.getJSONObject(i); val q=p.optJSONObject("query"); AuraServer(p.getString("id"),p.optString("name"),p.optString("type").uppercase(),if(q?.optString("security")=="reality") "REALITY" else q?.optString("type","tcp")?.uppercase() ?: "TCP",p.optString("subscription"),p.optBoolean("favorite"),if(p.has("latency")) p.optInt("latency") else null,p.optBoolean("pingError"),p.optLong("latencyCheckedAt")) } }
    val selected get() = servers.firstOrNull { it.id == raw.optString("selected") }
}

class AuraRepository(private val app: Application) {
    private val gate = Mutex()
    private val vault = Vault(app)
    val appearance: Flow<AuraAppearance> = app.auraPreferences.data.map { p -> AuraAppearance(p[booleanPreferencesKey("dark")] ?: true,p[stringPreferencesKey("accent")] ?: "purple",p[floatPreferencesKey("intensity")] ?: .6f,p[booleanPreferencesKey("effects")] ?: true) }
    suspend fun load(): JSONObject = withContext(Dispatchers.IO) { gate.withLock { vault.load() } }
    suspend fun change(block: (JSONObject) -> Unit): JSONObject = withContext(Dispatchers.IO) { gate.withLock { val next=vault.load();block(next);Core.validateState(next);vault.save(next);next } }
    suspend fun appearance(value: AuraAppearance) { app.auraPreferences.edit { it[booleanPreferencesKey("dark")]=value.dark;it[stringPreferencesKey("accent")]=value.accent;it[floatPreferencesKey("intensity")]=value.intensity.coerceIn(0f,1f);it[booleanPreferencesKey("effects")]=value.effects } }
}

class AuraViewModel(app: Application): AndroidViewModel(app) {
    private val repository = AuraRepository(app)
    private val mutable = MutableStateFlow(AuraState())
    val state = mutable.asStateFlow()
    private var probeJob: Job? = null
    var foreground = false
    init {
        viewModelScope.launch { repository.appearance.catch { message("Не удалось открыть настройки оформления") }.collect { p -> mutable.update { it.copy(appearance=p) } } }
        refresh()
        viewModelScope.launch { while(isActive) { if(foreground) { val now=System.currentTimeMillis();mutable.update { it.copy(status=AuraVpnService.status,rx=AuraVpnService.rxTotal,tx=AuraVpnService.txTotal,rxRate=AuraVpnService.rxRate,txRate=AuraVpnService.txRate,metrics=AuraVpnService.metricsAvailable,ip=AuraVpnService.publicIP,seconds=if(AuraVpnService.connectedAt>0) ((now-AuraVpnService.connectedAt)/1000).coerceAtLeast(0) else 0) } };delay(1000) } }
    }
    fun refresh() = viewModelScope.launch { try { val raw=repository.load();mutable.update { it.copy(raw=raw,loaded=true,status=AuraVpnService.status) } } catch(_:Exception) { message("Хранилище не открыто. Существующие данные не изменены.") } }
    fun message(text: String?) { mutable.update { it.copy(error=text) } }
    fun update(block: (JSONObject)->Unit, done: ()->Unit = {}) = viewModelScope.launch {
        if(!state.value.loaded) { message("Дождитесь открытия хранилища");return@launch }
        try { val next=repository.change(block);mutable.update { it.copy(raw=next) };done() } catch(_:Exception) { message("Не удалось сохранить изменения. Проверьте данные и состояние VPN.") }
    }
    fun setting(key: String, value: Any) { if(state.value.running && key in listOf("mtu","blockIPv6","dns","splitMode","autoReconnect")) { message("Сначала отключите VPN");return }; update({ it.put(key,value) }) }
    fun appearance(value: AuraAppearance) = viewModelScope.launch { try { repository.appearance(value);if(state.value.loaded) { val next=repository.change { it.put("theme",if(value.dark)"dark" else "light").put("accent",if(value.accent=="blue")"blue" else "violet").put("backdrop","minimal").put("reduceMotion",!value.effects) };mutable.update { it.copy(raw=next) } } } catch(_:Exception) { message("Оформление не сохранено") } }
    fun select(id: String, selected: (Boolean)->Unit) {
        if(state.value.transition) { message("Дождитесь завершения подключения");return }
        val restart=state.value.status=="connected"
        update({ if(Core.find(it.getJSONArray("profiles"),id)==null) error("Missing profile");it.put("selected",id) }, {selected(restart)})
    }
    fun favorite(id:String) { update({ val p=Core.find(it.getJSONArray("profiles"),id) ?: error("Missing profile");p.put("favorite",!p.optBoolean("favorite")) }) }
    fun savePing(value: JSONObject, done: ()->Unit) { try { Ping.validate(value);update({it.put("ping",value)},done) } catch(_:Exception) { message("Укажите HTTP/HTTPS URL без логина и пароля. Таймаут: 5–15 секунд.") } }
    fun canConnect(): Boolean = try { if(!state.value.loaded) error("Not loaded");Core.config(state.value.raw);true } catch(_:Exception) { message("Добавьте и выберите рабочий сервер. Для режима «только выбранные приложения» выберите приложения.");false }
    fun probe(ids: List<String>, best: ((String)->Unit)? = null) {
        if(probeJob?.isActive==true) { message("Проверка уже выполняется");return }
        probeJob=viewModelScope.launch {
            try {
                val settings=Ping.settings(state.value.raw)
                for(id in ids) {
                    ensureActive();val snapshot=JSONObject(state.value.raw.toString());val p=Core.find(snapshot.getJSONArray("profiles"),id) ?: continue
                    mutable.update { it.copy(probing=id) }
                    val result=withContext(Dispatchers.IO) { try { Ping.measure(snapshot,p,settings) } catch(_:Exception) { null } }
                    val next=repository.change { root -> Core.find(root.getJSONArray("profiles"),id)?.let { profile ->
                        profile.remove("latency");profile.remove("pingStatus");profile.remove("pingError");profile.put("pingMethod",settings.getString("method")).put("latencyCheckedAt",System.currentTimeMillis())
                        if(result==null) profile.put("pingError",true) else profile.put("latency",result.getInt("ms")).put("pingStatus",result.getInt("status"))
                    } }
                    mutable.update { it.copy(raw=next) }
                }
                if(best!=null) { val winner=state.value.servers.filter { it.id in ids && !it.failed && it.latency!=null }.minByOrNull { it.latency!! };if(winner==null)message("Ни один сервер не ответил на выбранную проверку") else best(winner.id) }
            } catch(e:CancellationException) { throw e } catch(_:Exception) { message("Проверка остановлена. Данные подписки не изменены.") } finally { mutable.update { it.copy(probing=null) } }
        }
    }
    fun cancelProbe() { probeJob?.cancel() }
    fun report(): String { val s=state.value;return "AURA VPN ${BuildConfig.VERSION_NAME}\nAndroid ${android.os.Build.VERSION.RELEASE}\nVPN: ${s.status}\nProtocol: ${s.selected?.kind ?: "none"}\nMTU: ${s.raw.optInt("mtu",1400)}\nProfiles: ${s.servers.size}\nIPv6 blocking: ${s.raw.optBoolean("blockIPv6",true)}\nCounters available: ${s.metrics}\n\n"+AuraVpnService.events().joinToString("\n") }
}
