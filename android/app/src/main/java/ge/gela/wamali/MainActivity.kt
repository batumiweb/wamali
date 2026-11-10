package ge.gela.wamali

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Teal = Color(0xFF1D9E75)
val TealDark = Color(0xFF0F6E56)
val TealLight = Color(0xFFE1F5EE)
val Orange = Color(0xFFBA7517)
val Green = Color(0xFF3B6D11)
val Bg = Color(0xFFF2F2F7)
private val KA: Locale = Locale.forLanguageTag("ka")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MedStore.init(this)
        setContent { WamaliTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        Alarms.scheduleAll(this)
        UiState.resumeTick++
    }
}

@Composable
fun WamaliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal, secondary = TealDark, primaryContainer = TealLight,
            background = Bg, surface = Color.White
        ),
        content = content
    )
}

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Medication?>(null) }
    val current = editing
    if (current != null) {
        EditScreen(current) { editing = null }
    } else {
        val tabs: List<Pair<String, ImageVector>> = listOf(
            "დღეს" to Icons.Default.Home,
            "წამლები" to Icons.Default.Notifications,
            "ისტორია" to Icons.Default.DateRange
        )
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == 1) {
                    FloatingActionButton(
                        onClick = { editing = Medication() },
                        containerColor = Teal, contentColor = Color.White
                    ) { Icon(Icons.Default.Add, contentDescription = "დამატება") }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> TodayScreen()
                    1 -> MedsScreen { editing = it }
                    else -> HistoryScreen()
                }
            }
        }
    }
}

@Composable
fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
fun Thumb(m: Medication, size: Dp) {
    val bmp = remember(m.photo) { MedStore.loadPhoto(m)?.asImageBitmap() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Teal),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("💊", fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        content = content
    )
}

// ------------------------------------------------------------------ დღეს

@Composable
fun TodayScreen() {
    val now = System.currentTimeMillis()
    val slots = MedStore.slots(now)
    val low = MedStore.lowStock
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Title("დღეს", SimpleDateFormat("EEEE, d MMMM", KA).format(Date(now))) }
        item { PermissionCards() }
        items(low, key = { "low-" + it.id }) { m ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAEEDA))
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Orange)
                    Spacer(Modifier.width(10.dp))
                    Text("${m.name} — მარაგი თავდება, დარჩა ${m.stock}", color = Color(0xFF633806))
                }
            }
        }
        if (slots.isEmpty()) {
            item { EmptyCard("დღეს წამალი არ გაქვს", "დაამატე „წამლები“ ჩანართში +-ით") }
        }
        items(slots, key = { it.med.id + it.time.label }) { SlotRow(it) }
    }
}

@Composable
fun EmptyCard(title: String, sub: String) {
    WhiteCard {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💊", fontSize = 40.sp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(sub, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
fun SlotRow(s: DoseSlot) {
    val st = MedStore.status(s.med.id, s.at)
    val past = s.at < System.currentTimeMillis()
    WhiteCard {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(s.med, 50.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.med.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    listOf(s.time.label, s.med.dose, s.med.note).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color.Gray, fontSize = 13.sp
                )
            }
            when (st) {
                TAKEN -> IconButton(onClick = { MedStore.undo(s.med.id, s.at) }) {
                    Icon(Icons.Default.CheckCircle, "მიღებულია (დააჭირე გასაუქმებლად)", tint = Green, modifier = Modifier.size(34.dp))
                }
                SKIPPED -> IconButton(onClick = { MedStore.undo(s.med.id, s.at) }) {
                    Icon(Icons.Default.Close, "გამოტოვებულია", tint = Color.Gray, modifier = Modifier.size(30.dp))
                }
                else -> Button(
                    onClick = { MedStore.record(TAKEN, s.med.id, s.at) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (past) Orange else Teal)
                ) { Text("მივიღე") }
            }
        }
    }
}

@Composable
fun PermissionCards() {
    val ctx = LocalContext.current
    val tick = UiState.resumeTick
    var asked by rememberSaveable { mutableStateOf(false) }
    var localTick by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }
    LaunchedEffect(Unit) {
        if (!asked && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val pkg = ctx.packageName
    val notifOk = remember(tick, localTick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val fullOk = remember(tick, localTick) {
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val exactOk = remember(tick, localTick) { Alarms.canExact(ctx) }
    val batteryOk = remember(tick, localTick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notifOk) PermCard("შეტყობინებები გამორთულია", "ამის გარეშე ვერ დარეკავს.") {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        }
        if (!fullOk && Build.VERSION.SDK_INT >= 34) PermCard("სრულ ეკრანზე გამოჩენა", "ჩართე, რომ ჩაკეტილ ეკრანზე მთელ ეკრანზე დარეკოს.") {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
        }
        if (!exactOk && Build.VERSION.SDK_INT >= 31) PermCard("ზუსტი დრო", "ჩართე „მაღვიძარები და შეხსენებები“, რომ წუთის სიზუსტით დარეკოს.") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
        }
        if (!batteryOk) PermCard("ბატარეის დაზოგვა", "დაუშვი, რომ ტელეფონმა აპი არ „დააძინოს“ (Xiaomi/Samsung-ზე მნიშვნელოვანია).") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
        }
    }
}

@Composable
fun PermCard(title: String, sub: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCEBEB))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF791F1F))
                Text(sub, fontSize = 13.sp, color = Color(0xFF791F1F))
            }
            Button(
                onClick = { try { onClick() } catch (e: Exception) { e.printStackTrace() } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA32D2D))
            ) { Text("ჩართვა") }
        }
    }
}

// ------------------------------------------------------------------ წამლები

@Composable
fun MedsScreen(onEdit: (Medication) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 90.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Title("წამლები") }
        if (MedStore.meds.isEmpty()) item { EmptyCard("წამალი ჯერ არ არის", "დააჭირე + ღილაკს ქვემოთ") }
        items(MedStore.meds, key = { it.id }) { m ->
            WhiteCard(Modifier.clickable { onEdit(m) }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(m, 50.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(m.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(m.timesSummary, color = Color.Gray, fontSize = 13.sp)
                        m.stock?.let {
                            Text("მარაგი: $it", fontSize = 12.sp, color = if (it <= m.lowStock) Orange else Color.Gray)
                        }
                    }
                    if (!m.active) Text("გამორთული", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
fun StepperRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = onMinus) { Text("−", fontSize = 18.sp) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPlus) { Text("+", fontSize = 18.sp) }
    }
}

fun pickTime(ctx: Context, h: Int, m: Int, onPick: (DoseTime) -> Unit) {
    TimePickerDialog(ctx, { _, hh, mm -> onPick(DoseTime(hh, mm)) }, h, m, true).show()
}

@Composable
fun EditScreen(initial: Medication, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = remember { MedStore.med(initial.id) == null }
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var note by remember { mutableStateOf(initial.note) }
    val times = remember { mutableStateListOf<DoseTime>().apply { addAll(initial.times) } }
    var days by remember { mutableStateOf(initial.weekdays) }
    var track by remember { mutableStateOf(initial.stock != null) }
    var stock by remember { mutableIntStateOf(initial.stock ?: 30) }
    var low by remember { mutableIntStateOf(initial.lowStock) }
    var active by remember { mutableStateOf(initial.active) }
    var bitmap by remember { mutableStateOf<Bitmap?>(MedStore.loadPhoto(initial)) }
    var changed by remember { mutableStateOf(false) }
    var testSent by remember { mutableStateOf(false) }

    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b ->
        if (b != null) { bitmap = b; changed = true }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) MedStore.decode(ctx, uri)?.let { bitmap = it; changed = true }
    }
    BackHandler { onClose() }

    val canSave = name.isNotBlank() && times.isNotEmpty()

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("გაუქმება") }
            Text(
                if (isNew) "ახალი წამალი" else initial.name,
                Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(enabled = canSave, onClick = {
                val m = initial.copy(
                    name = name.trim(), dose = dose.trim(), note = note.trim(),
                    times = times.toList(), weekdays = days,
                    stock = if (track) stock else null, lowStock = low, active = active
                )
                MedStore.save(ctx, m, bitmap, changed)
                onClose()
            }) { Text("შენახვა", fontWeight = FontWeight.Bold) }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WhiteCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(150.dp).clip(RoundedCornerShape(32.dp)).background(TealLight),
                        contentAlignment = Alignment.Center
                    ) {
                        val b = bitmap
                        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text("📷", fontSize = 44.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { takePic.launch(null) }, modifier = Modifier.weight(1f)) { Text("📷 გადაღება") }
                        OutlinedButton(
                            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) { Text("🖼 გალერეა") }
                    }
                    Text("გადაუღე წამლის კოლოფს — დარეკვისას ამ ფოტოს დაინახავ", fontSize = 12.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }

            SectionLabel("წამალი")
            OutlinedTextField(name, { name = it }, label = { Text("სახელი (მაგ. ასპირინი)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dose, { dose = it }, label = { Text("დოზა (მაგ. 1 აბი)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("შენიშვნა (მაგ. ჭამის შემდეგ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            SectionLabel("დროები")
            WhiteCard {
                Column(Modifier.padding(12.dp)) {
                    times.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pickTime(ctx, t.hour, t.minute) { times[i] = it } }) {
                                Text(t.label, fontSize = 22.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { times.removeAt(i) }) { Icon(Icons.Default.Delete, "წაშლა", tint = Color.Gray) }
                        }
                    }
                    TextButton(onClick = {
                        val l = times.lastOrNull()
                        pickTime(ctx, minOf(23, (l?.hour ?: 8) + 4), l?.minute ?: 0) { times.add(it) }
                    }) { Text("+ დროის დამატება", fontSize = 16.sp) }
                }
            }

            SectionLabel("დღეები")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WEEK_ORDER.forEach { d ->
                    val on = d in days
                    Box(
                        Modifier.weight(1f).clip(CircleShape)
                            .background(if (on) Teal else Color(0xFFE3E3E6))
                            .clickable { days = if (on) days - d else days + d }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(DAY_SHORT[d], color = if (on) Color.White else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SectionLabel("მარაგი")
            WhiteCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("აბების დათვლა", Modifier.weight(1f))
                        Switch(track, { track = it })
                    }
                    if (track) {
                        StepperRow("დარჩენილია: $stock", { stock = maxOf(0, stock - 1) }, { stock++ })
                        StepperRow("გამაფრთხილე, როცა დარჩება $low", { low = maxOf(0, low - 1) }, { low++ })
                        Text("„მივიღე“-ზე ყოველ ჯერზე ერთი აკლდება", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            WhiteCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("შეხსენებები ჩართულია", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }

            if (!isNew) {
                OutlinedButton(
                    onClick = { Alarms.test(ctx, initial.id); testSent = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (testSent) "10 წამში დარეკავს — ჩაკეტე ეკრანი" else "🔔 ტესტი: დარეკე 10 წამში") }
                TextButton(
                    onClick = { MedStore.delete(ctx, initial); onClose() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("წამლის წაშლა", color = Color(0xFFA32D2D)) }
            } else {
                Text("შენახვის შემდეგ შეგეძლება ტესტის გაკეთება", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ------------------------------------------------------------------ ისტორია

@Composable
fun HistoryScreen() {
    val (taken, due) = MedStore.adherence()
    val pct = if (due == 0) 1f else taken.toFloat() / due
    val dayFmt = SimpleDateFormat("EEEE, d MMMM", KA)
    val timeFmt = SimpleDateFormat("HH:mm", KA)
    val grouped = MedStore.logs.takeLast(300).reversed().groupBy {
        Calendar.getInstance().apply {
            timeInMillis = it.time
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Title("ისტორია") }
        item {
            WhiteCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { pct }, modifier = Modifier.size(70.dp),
                            color = Teal, trackColor = Color(0xFFE3E3E6), strokeWidth = 7.dp
                        )
                        Text(if (due == 0) "—" else "${(pct * 100).toInt()}%", fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("ბოლო 7 დღე: $taken / $due", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("დროულად მიღებული დოზები", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        }
        if (grouped.isEmpty()) item { EmptyCard("ჯერ ჩანაწერი არ არის", "„მივიღე“-ს დაჭერის შემდეგ აქ გამოჩნდება") }
        grouped.forEach { (day, list) ->
            item(key = "d$day") { SectionLabel(dayFmt.format(Date(day))) }
            item(key = "c$day") {
                WhiteCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        list.forEach { log ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val (sym, col) = when (log.action) {
                                    TAKEN -> "✓" to Green
                                    SNOOZED -> "⏰" to Orange
                                    else -> "✕" to Color.Gray
                                }
                                Text(sym, color = col, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(log.medName)
                                    Text("გრაფიკით ${timeFmt.format(Date(log.scheduled))}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(timeFmt.format(Date(log.time)), color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
