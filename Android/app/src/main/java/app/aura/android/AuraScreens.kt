package app.aura.android

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject
import kotlin.math.*

private val Violet=Color(0xFF9B7CFF)
private val Cyan=Color(0xFF65DEFF)
private val Ink=Color(0xFF080B12)
private val Rounded=RoundedCornerShape(24.dp)
private fun byteText(n:Long):String = when { n>=1024*1024*1024->"%.2f ГБ".format(n/1073741824.0);n>=1024*1024->"%.1f МБ".format(n/1048576.0);n>=1024->"%.1f КБ".format(n/1024.0);else->"$n Б" }
private fun pingText(s:AuraState,p:AuraServer?):String = when {p==null->"—";p.failed->"Нет ответа";s.raw.optJSONObject("ping")?.optBoolean("displayStatus")==true&&p.statusCode>0->"HTTP ${p.statusCode}";p.latency!=null->"${p.latency} мс";else->"—"}
private fun duration(n:Long)="%02d:%02d:%02d".format(n/3600,n/60%60,n%60)

@Composable private fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.clip(Rounded).background(MaterialTheme.colorScheme.surface.copy(alpha=.92f)).border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.22f),Rounded).padding(16.dp),content=content)}
@Composable private fun Kicker(text:String){Text(text.uppercase(),fontSize=11.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=4.dp,top=16.dp,bottom=10.dp))}
@Composable private fun Line(icon:ImageVector,title:String,subtitle:String?=null,value:String?=null,onClick:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth().then(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier).padding(vertical=12.dp).heightIn(min=38.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium);if(subtitle!=null)Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(value!=null)Text(value,fontSize=12.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.widthIn(max=120.dp));if(onClick!=null)Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable private fun Toggle(title:String,detail:String?=null,checked:Boolean,enabled:Boolean=true,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth().alpha(if(enabled)1f else .45f).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=15.sp);if(detail!=null)Text(detail,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked,change,modifier=Modifier.semantics{contentDescription=title},enabled=enabled)}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AuraApplication(model:AuraViewModel,connect:()->Unit,selected:(String)->Unit,legacy:(String)->Unit,systemVPN:()->Unit,share:()->Unit,startup:()->Unit){
    val s by model.state.collectAsStateWithLifecycle();var tab by rememberSaveable { mutableIntStateOf(0) };var protocolKind by rememberSaveable { mutableStateOf("Все") };var sheet by rememberSaveable { mutableStateOf(false) };var ping by rememberSaveable { mutableStateOf(false) };var diagnostic by rememberSaveable { mutableStateOf(false) };var protocol by rememberSaveable { mutableStateOf(false) };var started by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current;val accent=if(s.appearance.accent=="blue")Cyan else Violet
    val scheme=if(s.appearance.dark)darkColorScheme(primary=accent,secondary=Cyan,background=Ink,surface=Color(0xFF171A26),onBackground=Color(0xFFF1F3FF),onSurface=Color(0xFFF1F3FF),onSurfaceVariant=Color(0xFF9DA4BB),outline=Color(0xFF737B9A),error=Color(0xFFFF7186)) else lightColorScheme(primary=if(s.appearance.accent=="blue")Color(0xFF007C9D) else Color(0xFF6540C5),background=Color(0xFFF3F2FA),surface=Color.White,onSurfaceVariant=Color(0xFF62667C))
    MaterialTheme(colorScheme=scheme){
        val snackbar=remember { SnackbarHostState() };val serverSheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
        LaunchedEffect(s.appearance.dark){(context as? android.app.Activity)?.let{activity->androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply{isAppearanceLightStatusBars=!s.appearance.dark;isAppearanceLightNavigationBars=!s.appearance.dark}}}
        LaunchedEffect(s.error){s.error?.let { snackbar.showSnackbar(it);model.message(null) }}
        LaunchedEffect(s.loaded){if(s.loaded&&!started){started=true;if(s.raw.optBoolean("connectOnStartup")&&s.selected!=null)startup()}}
        LaunchedEffect(s.raw.optBoolean("hideRecents",true)){val activity=context as? android.app.Activity;if(Build.VERSION.SDK_INT>=33)activity?.setRecentsScreenshotEnabled(!s.raw.optBoolean("hideRecents",true)) else if(s.raw.optBoolean("hideRecents",true))activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
        BackHandler(tab!=0){tab=0}
        Scaffold(modifier=Modifier.imePadding(),containerColor=scheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={Column(Modifier.navigationBarsPadding().padding(horizontal=16.dp)){
            if(tab==0){QuickServer(s,{sheet=true},{legacy("import")});Spacer(Modifier.height(10.dp))}
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surface).border(1.dp,scheme.outline.copy(alpha=.2f),RoundedCornerShape(28.dp)).padding(5.dp),horizontalArrangement=Arrangement.SpaceEvenly){listOf("Главная" to Icons.Outlined.Home,"Серверы" to Icons.Outlined.Public,"Настройки" to Icons.Outlined.Tune).forEachIndexed { i,pair -> val tint by animateColorAsState(if(tab==i)scheme.primary else scheme.onSurfaceVariant,label="navigation");Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if(tab==i)scheme.primary.copy(alpha=.15f)else Color.Transparent).clickable{tab=i;if(i==1)protocolKind="Все"}.padding(vertical=10.dp).semantics { contentDescription=pair.first;this.selected=tab==i },horizontalAlignment=Alignment.CenterHorizontally){Icon(pair.second,null,tint=tint,modifier=Modifier.size(22.dp));Text(pair.first,fontSize=10.sp,color=tint)}}};Spacer(Modifier.height(8.dp))
        }}){padding->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal=20.dp)){
                Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha=.18f)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoAwesome,"AURA",tint=accent,modifier=Modifier.size(22.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(if(tab==0)"AURA VPN" else if(tab==1)"Серверы" else "Настройки",fontSize=21.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.sp);Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(5.dp).clip(CircleShape).background(if(s.selected?.failed==true)scheme.error else if(s.selected?.latency!=null)Cyan else scheme.onSurfaceVariant));Spacer(Modifier.width(5.dp));Text(if(tab==0)s.title else "PRIVATE BY DESIGN",fontSize=10.sp,color=scheme.onSurfaceVariant,letterSpacing=1.sp)}}
                    IconButton(onClick={ping=true},modifier=Modifier.size(48.dp).clip(CircleShape).background(scheme.surface)){Icon(Icons.Outlined.Speed,"Настройки пинга",modifier=Modifier.size(26.dp))};Spacer(Modifier.width(8.dp));IconButton(onClick={if(tab==2)tab=0 else tab=2},modifier=Modifier.size(48.dp).clip(CircleShape).background(scheme.surface)){Icon(if(tab==2)Icons.Outlined.Home else Icons.Outlined.Tune,if(tab==2)"На главную" else "Настройки AURA",modifier=Modifier.size(26.dp))}
                }
                AnimatedContent(tab,transitionSpec={fadeIn(tween(if(s.appearance.effects)180 else 0)) togetherWith fadeOut(tween(if(s.appearance.effects)120 else 0))},label="page",modifier=Modifier.weight(1f)){page->when(page){0->Home(s,connect,{AuraVpnService.checkPublicIP()},{s.selected?.let{model.probe(listOf(it.id))}});1->Servers(s,model,selected,{legacy("import")},{legacy("profiles")},protocolKind);else->SettingsScreen(s,model,legacy,systemVPN,{ping=true},{diagnostic=true},{protocol=true})}}
            }
        }
        if(sheet)ModalBottomSheet(sheetState=serverSheet,onDismissRequest={sheet=false},containerColor=scheme.background){Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal=20.dp)){Text("Ваши серверы",fontSize=22.sp,fontWeight=FontWeight.SemiBold);Servers(s,model,{sheet=false;selected(it)},{sheet=false;legacy("import")},{sheet=false;legacy("profiles")})}}
        if(ping)PingEditor(s,{ping=false}){value->model.savePing(value){ping=false}}
        if(diagnostic)AlertDialog(onDismissRequest={diagnostic=false},title={Text("Диагностика")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){Text("VPN: ${s.title}\nПротокол: ${s.selected?.kind ?: "—"}\nMTU: ${s.raw.optInt("mtu",1400)}\nAlways-on: ${AuraVpnService.alwaysOn}\nLockdown: ${AuraVpnService.lockdown}",fontSize=13.sp);HorizontalDivider(Modifier.padding(vertical=14.dp));AuraVpnService.events().ifEmpty{listOf("Событий пока нет")}.forEach{Text(it,fontSize=12.sp,modifier=Modifier.padding(vertical=4.dp))}}},confirmButton={TextButton(onClick=share){Icon(Icons.Outlined.Share,null);Text("Экспорт")}},dismissButton={TextButton(onClick={diagnostic=false}){Text("Закрыть")}})
        if(protocol)AlertDialog(onDismissRequest={protocol=false},title={Text("Протокол сервера")},text={Column{Text("Протокол задаётся конфигурацией провайдера. Выберите существующий сервер нужного типа.",fontSize=13.sp);s.servers.map{it.kind}.distinct().forEach{kind->TextButton(onClick={protocol=false;protocolKind=kind;tab=1}){Text("$kind · выбрать сервер")}};if(s.servers.isEmpty())Text("Сначала добавьте подписку")}},confirmButton={TextButton(onClick={protocol=false}){Text("Готово")}})
    }
}

@Composable private fun Home(s:AuraState,connect:()->Unit,ipCheck:()->Unit,ping:()->Unit){
    val colors=MaterialTheme.colorScheme;val view=LocalView.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){
        Spacer(Modifier.height(6.dp));AuraRing(s,{view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);connect()})
        Text(s.title,fontSize=23.sp,fontWeight=FontWeight.SemiBold,color=if(s.status=="error")colors.error else colors.onSurface)
        Text(if(s.status=="connected")duration(s.seconds) else if(s.status=="error")"Проверьте конфигурацию и разрешение VPN" else "ВАШЕ ЛИЧНОЕ ПРОСТРАНСТВО",fontSize=11.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=18.dp),letterSpacing=1.sp)
        Panel(Modifier.fillMaxWidth()){
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Shield,null,tint=if(s.status=="connected")Cyan else colors.onSurfaceVariant,modifier=Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("IP выбранного прокси",fontSize=11.sp,color=colors.onSurfaceVariant);Text(s.ip.ifEmpty{when(s.ipState){"checking"->"Проверка…";"unavailable"->"Проверка недоступна";else->"Не проверен"}},fontSize=14.sp,fontWeight=FontWeight.Medium)};IconButton(onClick=ipCheck,enabled=s.status=="connected"){Icon(Icons.Outlined.Refresh,"Проверить IP через VPN")}}
            HorizontalDivider(Modifier.padding(vertical=10.dp),color=colors.outline.copy(alpha=.15f))
            Row(Modifier.fillMaxWidth()){Metric(Modifier.weight(1f),"↓ ЗАГРУЗКА",if(s.metrics)byteText(s.rxRate)+"/с" else "—");Metric(Modifier.weight(1f),"↑ ОТДАЧА",if(s.metrics)byteText(s.txRate)+"/с" else "—")};Spacer(Modifier.height(12.dp));Row{Metric(Modifier.weight(1f),"ПОЛУЧЕНО",if(s.metrics)byteText(s.rx)else"—");Metric(Modifier.weight(1f),"ОТПРАВЛЕНО",if(s.metrics)byteText(s.tx)else"—")}
        }
        TextButton(onClick=ping,enabled=s.selected!=null&&s.probing==null){Icon(Icons.Outlined.NetworkCheck,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(s.probing!=null)"Проверка сервера…" else "Проверить доступность сервера",fontSize=12.sp)}
    }
}
@Composable private fun Metric(modifier:Modifier,label:String,value:String){Column(modifier){Text(label,fontSize=9.sp,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontSize=17.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=4.dp))}}

@Composable private fun AuraRing(s:AuraState,connect:()->Unit){
    val effects=s.appearance.effects&&ValueAnimator.areAnimatorsEnabled();val moving=effects&&s.appearance.intensity>0f&&s.running&&s.status!="disconnecting"
    val rotation=if(moving){val transition=rememberInfiniteTransition(label="aura");val angle by transition.animateFloat(0f,360f,infiniteRepeatable(tween(if(s.status=="connecting")5000 else 14000,easing=LinearEasing)),label="rotation");angle}else 0f
    val color by animateColorAsState(when(s.status){"connected"->Cyan;"error"->MaterialTheme.colorScheme.error;else->MaterialTheme.colorScheme.primary},label="connection-color")
    val strength=if(effects)s.appearance.intensity else 0f
    Box(Modifier.fillMaxWidth().height(238.dp),contentAlignment=Alignment.Center){
        Canvas(Modifier.size(236.dp)){
            val center=Offset(size.width/2,size.height/2);val radius=size.minDimension*.38f;val phase=if(moving)rotation*PI/180 else 0.0
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.16f+strength*.10f),Color.Transparent),center,size.minDimension*.5f),size.minDimension*.5f,center)
            for(layer in 0..3){val path=Path();for(i in 0..144){val angle=i*PI*2/144;val wave=if(effects)sin(angle*6+phase+layer*.35)*radius*.035*strength else 0.0;val r=radius+layer*size.minDimension*.024f+wave;val x=center.x+cos(angle)*r;val y=center.y+sin(angle)*r;if(i==0)path.moveTo(x.toFloat(),y.toFloat())else path.lineTo(x.toFloat(),y.toFloat())};path.close();drawPath(path,color.copy(alpha=.18f-layer*.025f+strength*.1f),style=Stroke((1.0f+layer*.15f).dp.toPx()))}
        }
        FilledIconButton(onClick=connect,enabled=s.loaded&&s.status!="disconnecting",shape=CircleShape,colors=IconButtonDefaults.filledIconButtonColors(containerColor=color.copy(alpha=.16f),contentColor=color),modifier=Modifier.size(132.dp).border(1.dp,color.copy(alpha=.5f),CircleShape).semantics{contentDescription=if(s.running)"Отключить VPN" else "Подключить VPN"}){Icon(Icons.Outlined.PowerSettingsNew,null,modifier=Modifier.size(48.dp))}
        if(effects&&(s.status=="connecting"||s.status=="reconnecting"))CircularProgressIndicator(Modifier.size(158.dp),color=color,strokeWidth=2.dp)
    }
}

@Composable private fun QuickServer(s:AuraState,open:()->Unit,add:()->Unit){val p=s.selected;Panel(Modifier.fillMaxWidth().clickable(onClick=if(p==null)add else open).semantics{contentDescription="Открыть серверы"}){Row(verticalAlignment=Alignment.CenterVertically){Text(p?.flag ?: "＋",fontSize=26.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(end=12.dp));Column(Modifier.weight(1f)){Text(p?.displayName ?: "Добавить подключение",fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(p?.let{"${it.kind} · ${it.transport}"} ?: "Ваша подписка или конфигурация",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Column(horizontalAlignment=Alignment.End){Text(pingText(s,p),fontSize=12.sp,color=if(p?.failed==true)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary);Text(if(p?.failed==true)"Нет ответа" else if(p?.latency!=null)"Проверен" else "Не проверен",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Outlined.ChevronRight,null)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Servers(s:AuraState,model:AuraViewModel,selected:(String)->Unit,add:()->Unit,manage:()->Unit,requestedKind:String="Все"){
    val listState=rememberLazyListState();val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    var query by rememberSaveable{mutableStateOf("")};var favorites by rememberSaveable{mutableStateOf(false)};var sort by rememberSaveable{mutableStateOf(false)};var kind by rememberSaveable{mutableStateOf("Все")};var group by rememberSaveable{mutableStateOf("")};var groups by remember{mutableStateOf(false)}
    LaunchedEffect(requestedKind){kind=requestedKind}
    LaunchedEffect(query,kind,group,favorites){listState.scrollToItem(0)}
    val subscriptions=s.raw.optJSONArray("subscriptions");val groupName=if(group.isEmpty())"Все подписки" else (0 until (subscriptions?.length()?:0)).map{subscriptions!!.getJSONObject(it)}.firstOrNull{it.optString("id")==group}?.optString("name") ?: "Все подписки"
    val filtered=s.servers.filter{(query.isBlank()||it.name.contains(query,true)||it.kind.contains(query,true))&&(!favorites||it.favorite)&&(kind=="Все"||it.kind==kind)&&(group.isEmpty()||it.group==group)}.let{if(sort)it.sortedBy{p->p.latency ?: Int.MAX_VALUE}else it}
    Column(Modifier.fillMaxSize()){
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(vertical=10.dp).testTag("servers.search").semantics{contentDescription="Поиск серверов"},placeholder={Text("Поиск серверов")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(22.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();focus.clearFocus()}))
        Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={groups=true},modifier=Modifier.weight(1f)){Text(groupName,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick={favorites=!favorites}){Icon(if(favorites)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Только избранные",tint=if(favorites)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={sort=!sort}){Icon(Icons.Outlined.Sort,"Сортировать по задержке",tint=if(sort)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick=add){Icon(Icons.Outlined.Add,"Добавить подключение")}}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(listOf("Все")+s.servers.map{it.kind}.distinct()).forEach{type->FilterChip(kind==type,{kind=type},label={Text(type,fontSize=11.sp)})}}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${filtered.size} серверов",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f));TextButton(onClick={if(s.probing!=null)model.cancelProbe() else model.probe(filtered.map{it.id})},enabled=filtered.isNotEmpty()){Text(if(s.probing!=null)"Остановить" else "Проверить все",fontSize=11.sp)}}
        if(filtered.isEmpty())Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Outlined.Public,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(40.dp));Text(if(s.servers.isEmpty())"Добавьте вашу подписку" else "Ничего не найдено",modifier=Modifier.padding(12.dp));TextButton(onClick=add){Text("Добавить подключение")}}
        else LazyColumn(Modifier.weight(1f).testTag("servers.list"),state=listState,verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)){items(filtered,key={it.id}){p->Panel(Modifier.fillMaxWidth().testTag("server."+p.id).clickable(enabled=!s.transition){selected(p.id)}.semantics{contentDescription="${p.name}, ${p.kind}, ${p.transport}";this.selected=s.selected?.id==p.id}){Row(verticalAlignment=Alignment.CenterVertically){Text(p.flag,fontSize=25.sp,modifier=Modifier.padding(end=12.dp));Column(Modifier.weight(1f)){Text(p.displayName,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis);Text("${p.kind} · ${p.transport}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Column(horizontalAlignment=Alignment.End){if(s.probing==p.id)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp) else Text(pingText(s,p),fontSize=11.sp,color=if(p.failed)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary);if(s.selected?.id==p.id)Icon(Icons.Outlined.Check,"Выбран",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)};IconButton(onClick={model.favorite(p.id)},modifier=Modifier.size(40.dp)){Icon(if(p.favorite)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,if(p.favorite)"Удалить из избранного" else "В избранное",Modifier.size(19.dp),tint=MaterialTheme.colorScheme.primary)}}}}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=manage,modifier=Modifier.weight(1f)){Text("Подписки",fontSize=12.sp)};Button(onClick={model.probe(filtered.map{it.id}){selected(it)}},enabled=filtered.isNotEmpty()&&s.probing==null&&!s.transition,modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Bolt,null,Modifier.size(17.dp));Text("Автовыбор",fontSize=12.sp)}}
    }
    if(groups)AlertDialog(onDismissRequest={groups=false},title={Text("Подписка")},text={Column(Modifier.verticalScroll(rememberScrollState())){TextButton(onClick={group="";groups=false}){Text("Все подписки")};for(i in 0 until (subscriptions?.length()?:0)){val sub=subscriptions!!.getJSONObject(i);TextButton(onClick={group=sub.optString("id");groups=false}){Text(sub.optString("name"))}}}},confirmButton={TextButton(onClick={groups=false}){Text("Готово")}})
}

@Composable private fun SettingsScreen(s:AuraState,model:AuraViewModel,legacy:(String)->Unit,systemVPN:()->Unit,ping:()->Unit,diagnostic:()->Unit,protocol:()->Unit){
    var dns by remember{mutableStateOf(false)};var mtu by remember{mutableStateOf(false)};var mtuText by remember(s.raw.optInt("mtu",1400)){mutableStateOf(s.raw.optInt("mtu",1400).toString())}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
        Kicker("Подключение");Panel{
            Toggle("Подключать при запуске","После открытия приложения",s.raw.optBoolean("connectOnStartup"),change={model.setting("connectOnStartup",it)})
            Toggle("Автопереподключение","Восстановление сети и повтор при ошибке ядра",s.raw.optBoolean("autoReconnect",true),!s.running){model.setting("autoReconnect",it)}
            Line(Icons.Outlined.Autorenew,"Auto Connect / Always-on","Автозапуск VPN средствами Android",onClick=systemVPN)
            Line(Icons.Outlined.Lock,"Kill Switch","Включите «Блокировать без VPN» в параметрах Android",onClick=systemVPN)
            Line(Icons.Outlined.Tune,"Протокол",value=s.selected?.kind ?: "—",onClick=protocol)
            Line(Icons.Outlined.Speed,"Настройки пинга",value=Ping.settings(s.raw).optString("method"),onClick=ping)
        }
        Kicker("Безопасность");Panel{
            Line(Icons.Outlined.Dns,"DNS через туннель","DoH с проверкой сертификата",if(s.raw.optString("dns").contains("1.1.1.1"))"Cloudflare"else if(s.raw.optString("dns").contains("8.8.8.8"))"Google"else"Quad9"){if(s.running)model.message("Сначала отключите VPN")else dns=true}
            Toggle("Блокировать IPv6","IPv6 захватывается туннелем и отклоняется",s.raw.optBoolean("blockIPv6",true),!s.running){model.setting("blockIPv6",it)}
            Line(Icons.Outlined.Apps,"Split Tunneling","Включённые или исключённые приложения",onClick={legacy("split")})
            Line(Icons.Outlined.Route,"Маршрутизация","Домены, IP, локальная сеть",onClick={legacy("rules")})
            Toggle("Скрывать превью приложения",null,s.raw.optBoolean("hideRecents",true),change={model.setting("hideRecents",it)})
            Line(Icons.Outlined.Fingerprint,"HWID и подписки","Отправка HWID только при включении для подписки",onClick={legacy("profiles")})
        }
        Kicker("Оформление");Panel{
            Toggle("Тёмная тема",null,s.appearance.dark,change={model.appearance(s.appearance.copy(dark=it))})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(s.appearance.accent=="purple",{model.appearance(s.appearance.copy(accent="purple"))},label={Text("Neon Purple",fontSize=12.sp)});FilterChip(s.appearance.accent=="blue",{model.appearance(s.appearance.copy(accent="blue"))},label={Text("Cyber Blue",fontSize=12.sp)})}
            Toggle("Визуальные эффекты","Отключите для экономии батареи",s.appearance.effects,change={model.appearance(s.appearance.copy(effects=it))})
            Text("Интенсивность свечения",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            var intensity by remember(s.appearance.intensity){mutableFloatStateOf(s.appearance.intensity)};Slider(intensity,{intensity=it},enabled=s.appearance.effects,onValueChangeFinished={model.appearance(s.appearance.copy(intensity=intensity))})
        }
        Kicker("Дополнительно");Panel{
            Line(Icons.Outlined.SettingsEthernet,"MTU","Применяется при следующем подключении",s.raw.optInt("mtu",1400).toString()){if(s.running)model.message("Сначала отключите VPN")else mtu=true}
            Line(Icons.Outlined.Terminal,"Диагностика и журнал","Без адресов подписок, ключей и конфигураций",onClick=diagnostic)
            Line(Icons.Outlined.Archive,"Резервные копии","AES-256-GCM · пароль пользователя",onClick={legacy("backup")})
            Line(Icons.Outlined.QrCodeScanner,"Сканировать QR-код",onClick={legacy("qr")})
            Line(Icons.Outlined.Info,"О приложении","AURA VPN ${BuildConfig.VERSION_NAME} · sing-box 1.11.15",onClick={legacy("about")})
        }
        Text("Секреты защищены Android Keystore.\nНет аналитики и рекламы.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().padding(20.dp));Spacer(Modifier.height(12.dp))
    }
    if(dns)AlertDialog(onDismissRequest={dns=false},title={Text("DNS через VPN")},text={Column{listOf("Cloudflare" to "https://1.1.1.1/dns-query","Google" to "https://8.8.8.8/dns-query","Quad9" to "https://9.9.9.9/dns-query").forEach{(name,url)->TextButton(onClick={model.setting("dns",url);dns=false}){Text(name)}}}},confirmButton={TextButton(onClick={dns=false}){Text("Готово")}})
    if(mtu)AlertDialog(onDismissRequest={mtu=false},title={Text("MTU туннеля")},text={Column{OutlinedTextField(mtuText,{mtuText=it.filter{c->c.isDigit()}.take(4)},label={Text("1280–9000")},singleLine=true);Text("Обычно 1400. Слишком большое значение может нарушить передачу данных.",fontSize=12.sp)}},confirmButton={TextButton(onClick={val value=mtuText.toIntOrNull();if(value!=null&&value in 1280..9000){model.setting("mtu",value);mtu=false}else model.message("MTU: от 1280 до 9000")}){Text("Сохранить")}},dismissButton={TextButton(onClick={mtu=false}){Text("Отмена")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PingEditor(s:AuraState,close:()->Unit,save:(JSONObject)->Unit){
    val original=remember{Ping.settings(s.raw)};var method by remember{mutableStateOf(original.optString("method","GET"))};var mode by remember{mutableStateOf(original.optString("mode","default"))};var timeout by remember{mutableFloatStateOf(original.optInt("timeout",7).toFloat())};var url by remember{mutableStateOf(original.optString("url"))};var display by remember{mutableStateOf(original.optBoolean("displayStatus"))};var error by remember{mutableStateOf("")};val proxy=method=="GET"||method=="HEAD"
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Scaffold(modifier=Modifier.fillMaxSize().systemBarsPadding().imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),topBar={TopAppBar(title={Text("Настройки пинга",fontSize=18.sp)},navigationIcon={IconButton(onClick=close){Icon(Icons.Outlined.ArrowBack,"Назад")}},actions={TextButton(onClick={val value=Core.obj("method",method,"mode",mode,"timeout",timeout.roundToInt(),"url",url.trim(),"displayStatus",display);try{Ping.validate(value);save(value)}catch(_:Exception){error="Укажите HTTP/HTTPS URL без логина и пароля"}}){Text("Сохранить")}})}){padding->Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)){
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp,modifier=Modifier.padding(vertical=8.dp))
            Kicker("Протокол пинга");Panel{listOf("GET","HEAD","TCP","ICMP").forEach{value->Row(Modifier.fillMaxWidth().clickable{method=value}.semantics{selected=method==value}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Text(if(value=="GET"||value=="HEAD")"Через прокси · $value" else value,Modifier.weight(1f));if(method==value)Icon(Icons.Outlined.Check,null,tint=MaterialTheme.colorScheme.primary)}}}
            Kicker("Режим через прокси");Panel(Modifier.alpha(if(proxy)1f else .4f)){listOf("default" to "Default","double" to "Double","keepalive" to "Keepalive").forEach{(value,label)->Row(Modifier.fillMaxWidth().clickable(enabled=proxy){mode=value}.padding(vertical=12.dp)){Text(label,Modifier.weight(1f));if(mode==value)Icon(Icons.Outlined.Check,null,tint=MaterialTheme.colorScheme.primary)}};Text("Default — один запрос. Double — лучший из двух соединений. Keepalive — два запроса с повторным использованием соединения.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Kicker("Таймаут");Panel{Text("${timeout.roundToInt()} секунд");Slider(timeout,{timeout=it},valueRange=5f..15f,steps=9);Row{Text("5 с",Modifier.weight(1f),fontSize=11.sp);Text("15 с",fontSize=11.sp)}}
            Kicker("Адрес для проверки");OutlinedTextField(url,{url=it},enabled=proxy,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("URL теста")},shape=Rounded)
            Kicker("Отображение");Panel{Toggle("HTTP-статус вместо времени",null,display,change={display=it});Text("TCP проверяет порт, ICMP — ответ IPv4. Они могут использовать текущий маршрут и не подтверждают работу прокси.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Spacer(Modifier.height(30.dp))
        }}
    }
}
