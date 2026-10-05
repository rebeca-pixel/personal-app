package com.knockturn.personalexecutive
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExecVm(app: Application) : AndroidViewModel(app) {
    var tasks by mutableStateOf<List<Task>>(emptyList()); var motivation by mutableStateOf("")
    var digest by mutableStateOf(""); var dash by mutableStateOf<List<String>>(emptyList())
    var leaves by mutableStateOf<List<String>>(emptyList()); var blockers by mutableStateOf<List<String>>(emptyList()); var overdue by mutableStateOf<List<String>>(emptyList())
    var report by mutableStateOf(""); var msg by mutableStateOf<String?>(null); var busy by mutableStateOf(false)
    fun <T> run(block: () -> T, ok: (T) -> Unit = {}) = viewModelScope.launch {
        busy = true
        try { ok(withContext(Dispatchers.IO) { block() }) } catch (e: Exception) { msg = e.message ?: "Something went wrong" }
        busy = false
    }
    fun loadEmployee() { run({ Api2.tasks() }) { tasks = it }; run({ Api2.ai("motivation") }) { motivation = it }; run({ Api2.myReport() }) { report = it } }
    fun loadAdmin() { run({ Api2.dashboard() }) { dash = it }; run({ Api2.blockers() }) { blockers = it }; run({ Api2.pendingLeaves() }) { leaves = it }
        run({ Api2.overdueTasks() }) { overdue = it }; run({ Api2.teamReport() }) { report = it } }
    fun home() = run({ Api2.reachedHome() }) { Reminders.done(getApplication<Application>(), "home"); msg = "Thanks — glad you're home safe" }
}

@Composable fun Onboarding(onDone: () -> Unit) {
    val c = LocalContext.current
    fun go(i: Intent) = try { c.startActivity(i) } catch (_: Exception) {}
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Let's make sure alerts reach you", style = MaterialTheme.typography.headlineSmall, color = Purple)
        Text("What we collect: your plans, answers, task updates, mood and check-ins — only to support your work. " +
            "The 'reached home' check is a simple tap; no location is tracked.")
        Text("Xiaomi / Oppo / Vivo / Realme: turn on Autostart and set Battery to 'No restrictions'.\nSamsung: Battery → Unrestricted, and remove the app from 'Sleeping apps'.")
        OutlinedButton({ go(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("1. Battery usage") }
        OutlinedButton({ go(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)) }, Modifier.fillMaxWidth()) { Text("2. Notifications & Do Not Disturb") }
        if (Build.VERSION.SDK_INT >= 31) OutlinedButton({ go(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.packageName))) }, Modifier.fillMaxWidth()) { Text("3. Alarms & reminders") }
        OutlinedButton({ go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))) }, Modifier.fillMaxWidth()) { Text("4. Autostart / app info") }
        Button({ c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("onb", true).apply(); onDone() }, Modifier.fillMaxWidth().height(50.dp)) { Text("Continue") }
    }
}

@Composable fun Box1(title: String, body: String) = Card(colors = CardDefaults.cardColors(containerColor = PurpleSoft), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = Purple); Text(body.ifBlank { "…" }) } }

@Composable fun EmployeeMain(vm: AppVm, x: ExecVm) {
    LaunchedEffect(Unit) { vm.refresh(); x.loadEmployee() }
    LaunchedEffect(x.msg) { x.msg?.let { vm.msg = it; x.msg = null } }
    Tabs(listOf("Today", "Questions", "Plan", "Update", "Report")) { tab ->
        when (tab) {
            0 -> Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box1("A note for you", x.motivation)
                Text("How are you feeling? (optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..5).forEach { n -> OutlinedButton({ x.run({ Api2.mood(n) }) { vm.msg = "Thanks!" } }, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) { Text("$n") } } }
                var blk by remember { mutableStateOf(false) }; var note by remember { mutableStateOf("") }
                Button({ blk = true }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = PurpleDark)) { Text("I'm blocked") }
                if (blk) AlertDialog({ blk = false }, title = { Text("What's blocking you?") },
                    text = { OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = { TextButton({ x.run({ Api2.blocker(note) }) { vm.msg = "Your lead has been notified" }; blk = false; note = "" }) { Text("Notify") } },
                    dismissButton = { TextButton({ blk = false }) { Text("Cancel") } })
                Button({ x.home() }, Modifier.fillMaxWidth().height(50.dp)) { Text("I reached home") }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.questions) { q ->
                    var a by remember { mutableStateOf("") }; var snz by remember { mutableStateOf(false) }; var why by remember { mutableStateOf("") }
                    val done = q.id in vm.answered
                    LaunchedEffect(q.id) { if (!done) x.run({ Api2.seen(q.id) }) }
                    Card(colors = CardDefaults.cardColors(containerColor = PurpleSoft)) { Column(Modifier.padding(16.dp)) {
                        if (q.urgent) Text("URGENT", color = Purple, style = MaterialTheme.typography.labelLarge)
                        Text(q.text, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                        if (done) Text("Answered ✓", color = Purple) else {
                            OutlinedTextField(a, { a = it }, label = { Text("Your answer") }, modifier = Modifier.fillMaxWidth())
                            Row { Button({ vm.answer(q, a) }, enabled = a.isNotBlank()) { Text("Send") }; Spacer(Modifier.width(8.dp))
                                if (!q.urgent && !Reminders.snoozed(vm.getApplication<Application>(), q.id)) TextButton({ snz = true }) { Text("Snooze once") } }
                        }
                        if (snz) AlertDialog({ snz = false }, title = { Text("Why snooze? (admin can see this)") },
                            text = { OutlinedTextField(why, { why = it }, modifier = Modifier.fillMaxWidth()) },
                            confirmButton = { TextButton({ x.run({ Api2.snooze(q.id, why) }) { Reminders.snooze(vm.getApplication<Application>(), q.id); vm.msg = "Snoozed for 30 min" }; snz = false }, enabled = why.isNotBlank()) { Text("Snooze") } })
                    } }
                }
            }
            2 -> { val sel = remember { mutableStateListOf<String>() }; var free by remember { mutableStateOf("") }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Pick today's tasks from PMS", style = MaterialTheme.typography.titleMedium) }
                    items(x.tasks) { t -> Row { Checkbox(t.id in sel, { if (it) sel.add(t.id) else sel.remove(t.id) }); Text(t.title, Modifier.padding(top = 12.dp)) } }
                    item { OutlinedTextField(free, { free = it }, label = { Text("Anything else (free text)") }, modifier = Modifier.fillMaxWidth()) }
                    item { Button({ x.run({ Api2.savePlan(free, sel.toList()) }) { vm.msg = "Plan saved" } }, Modifier.fillMaxWidth(), enabled = sel.isNotEmpty() || free.isNotBlank()) { Text("Submit plan") } }
                } }
            3 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Planned vs actual — update each task", style = MaterialTheme.typography.titleMedium) }
                items(x.tasks) { t ->
                    var st by remember { mutableStateOf("In progress") }; var pct by remember { mutableFloatStateOf(0f) }; var note by remember { mutableStateOf("") }
                    Card(colors = CardDefaults.cardColors(containerColor = PurpleSoft)) { Column(Modifier.padding(14.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Done", "In progress", "Blocked").forEach { s ->
                            FilterChip(st == s, { st = s }, label = { Text(s) }) } }
                        Slider(pct, { pct = it }, valueRange = 0f..100f, colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple)); Text("${pct.toInt()}%")
                        OutlinedTextField(note, { note = it }, label = { Text(if (st == "Done") "Notes" else "Reason / notes") }, modifier = Modifier.fillMaxWidth())
                        Button({ x.run({ Api2.taskUpdate(t, st, pct.toInt(), note) }) { vm.msg = "Synced to PMS" } }, enabled = st == "Done" || note.isNotBlank()) { Text("Save") }
                    } }
                } }
            else -> Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) { Box1("Your monthly report", x.report) }
        }
    }
}

@Composable fun AdminMain(vm: AppVm, x: ExecVm) {
    LaunchedEffect(Unit) { vm.refresh(); x.loadAdmin() }
    LaunchedEffect(x.msg) { x.msg?.let { vm.msg = it; x.msg = null } }
    Tabs(listOf("Dashboard", "Question", "Answers", "Assistant")) { tab ->
        when (tab) {
            0 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedButton({ x.loadAdmin() }) { Text("Refresh") } }
                items(x.dash) { Card(colors = CardDefaults.cardColors(containerColor = PurpleSoft)) { Text(it, Modifier.padding(14.dp)) } } }
            1 -> AdminQuestion(vm)
            2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.answers) { Card(colors = CardDefaults.cardColors(containerColor = PurpleSoft)) { Text(it, Modifier.padding(14.dp)) } } }
            else -> Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ x.run({ Api2.ai("digest") }) { x.digest = it } }, Modifier.fillMaxWidth()) { Text("Generate today's digest") }
                if (x.digest.isNotBlank()) Box1("Daily digest", x.digest)
                Box1("Open blockers", x.blockers.joinToString("\n\n").ifBlank { "None" })
                if (x.blockers.isNotEmpty()) OutlinedButton({ x.run({ Api2.resolveBlockers() }) { x.blockers = emptyList() } }) { Text("Mark all resolved") }
                Box1("Pending approvals (LMS)", x.leaves.joinToString("\n").ifBlank { "None" })
                Box1("Overdue PMS tasks", x.overdue.joinToString("\n").ifBlank { "None" })
                Box1("Team report", x.report)
            }
        }
    }
}
