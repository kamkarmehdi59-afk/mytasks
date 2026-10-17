package com.example.mytasks

import android.app.TimePickerDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var notifOn by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize().systemBarsPadding()) { App(notifOn) { openNotifSettings() } }
                }
            }
        }
    }

    private fun openNotifSettings() {
        val i = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + packageName))
        }
        startActivity(i)
    }

    override fun onResume() {
        super.onResume()
        notifOn = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
        TaskWidget.refresh(this)
        Remind.fire(this)
    }
}

fun buildSections(list: List<Task>, kind: String, today: String): List<Pair<String, List<Occ>>> {
    return when (kind) {
        "debt" -> listOf(
            "وصول‌نشده" to list.filter { !it.done }.sortedBy { it.date }.map { Occ(it, it.date) },
            "وصول‌شده" to list.filter { it.done }.sortedByDescending { it.date }.map { Occ(it, it.date) }
        )
        "car" -> listOf(
            "سوابق" to list.sortedByDescending { it.date }.map { Occ(it, it.date) }
        )
        else -> {
            val now = list.filter { Rep.occursOn(it, today) }.sortedBy { it.time }.map { Occ(it, today) }
            val fut = list.mapNotNull { t ->
                if (t.repeat == "none") {
                    if (t.date > today) Occ(t, t.date) else null
                } else {
                    Rep.next(t, today)?.let { Occ(t, it) }
                }
            }.sortedWith(compareBy({ it.date }, { it.t.time }))
            val past = list.filter { it.repeat == "none" && it.date < today }
                .map { Occ(it, it.date) }.sortedByDescending { it.date }
            listOf("امروز" to now, "آینده" to fut, "گذشته" to past)
        }
    }
}

@Composable
fun App(notifOn: Boolean, onFixNotif: () -> Unit) {
    val ctx = LocalContext.current
    var tasks by remember { mutableStateOf(Store.load(ctx)) }
    var lists by remember { mutableStateOf(Lists.load(ctx)) }
    var cur by remember { mutableStateOf(0L) }
    val today = Jalali.todayIso()
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(today) }
    var time by remember { mutableStateOf("09:00") }
    var err by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var kmText by remember { mutableStateOf("") }
    var rep by remember { mutableStateOf("none") }
    var every by remember { mutableStateOf("1") }
    var endDate by remember { mutableStateOf("") }
    var calFor by remember { mutableStateOf("") }
    var showRep by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var kindSel by remember { mutableStateOf("normal") }
    var remind by remember { mutableStateOf(-1) }
    fun commit(l: List<Task>) { Store.save(ctx, l); tasks = Store.load(ctx) }
    fun commitLists(l: List<TList>) { Lists.save(ctx, l); lists = Lists.load(ctx) }
    val curList = lists.firstOrNull { it.id == cur } ?: Lists.main
    val kind = curList.kind
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val unitName = when (rep) { "day" -> "روز"; "week" -> "هفته"; "month" -> "ماه"; "year" -> "سال"; else -> "" }
    val mine = tasks.filter { it.listId == cur }
    val view = remember(tasks, cur, today, kind) { buildSections(tasks.filter { it.listId == cur }, kind, today) }
    val onToggle: (Occ) -> Unit = { o -> commit(tasks.map { x -> if (x.id == o.t.id) Rep.toggle(x, o.date) else x }) }
    val onDel: (Occ) -> Unit = { o -> commit(tasks.filter { x -> x.id != o.t.id }) }

    if (showRep) {
        AlertDialog(
            onDismissRequest = { showRep = false },
            title = { Text("تکرار کار") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("none" to "بدون تکرار", "day" to "روز", "week" to "هفته", "month" to "ماه", "year" to "سال").forEach { (k, n) ->
                            if (rep == k) Button(onClick = { rep = k }) { Text(n) }
                            else OutlinedButton(onClick = { rep = k }) { Text(n) }
                        }
                    }
                    if (rep != "none") {
                        OutlinedTextField(every, { every = it.filter { c -> c.isDigit() } },
                            label = { Text("هر چند " + unitName) }, keyboardOptions = num, singleLine = true)
                        OutlinedButton(onClick = { calFor = "end" }) {
                            Text(if (endDate.isEmpty()) "پایان: بدون پایان" else "پایان: " + Jalali.text(endDate))
                        }
                        if (endDate.isNotEmpty()) TextButton(onClick = { endDate = "" }) { Text("حذف تاریخ پایان") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRep = false }) { Text("تأیید") } }
        )
    }

    if (calFor != "") {
        CalendarDialog(today, { iso ->
            if (calFor == "date") sel = iso else endDate = iso
            calFor = ""
        }, { calFor = "" })
    }

    if (dialog == "new" || dialog == "rename") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(if (dialog == "new") "لیست جدید" else "تغییر نام لیست") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nameText, { nameText = it }, singleLine = true, label = { Text("عنوان") })
                    if (dialog == "new") {
                        Text("نوع لیست")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("normal" to "عادی", "debt" to "طلب‌ها", "car" to "تعمیرات خودرو").forEach { (k, n) ->
                                if (kindSel == k) Button(onClick = { kindSel = k }) { Text(n) }
                                else OutlinedButton(onClick = { kindSel = k }) { Text(n) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = nameText.trim()
                    if (n.isNotEmpty()) {
                        if (dialog == "new") {
                            val id = System.currentTimeMillis()
                            commitLists(lists + TList(id, n, kindSel))
                            cur = id
                        } else {
                            commitLists(lists.map { if (it.id == cur) it.copy(name = n) else it })
                        }
                        dialog = ""
                    }
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("انصراف") } }
        )
    }

    if (dialog == "delete") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("حذف لیست") },
            text = { Text("این لیست و همه کارهای آن حذف می‌شود. مطمئنی؟") },
            confirmButton = {
                TextButton(onClick = {
                    commit(tasks.filter { it.listId != cur })
                    commitLists(lists.filter { it.id != cur })
                    cur = 0L
                    dialog = ""
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("انصراف") } }
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("کارهای من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(Jalali.full(today))
                if (!notifOn) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("اعلان‌ها خاموش است و یادآوری‌ها نمی‌آیند.")
                            Button(onClick = onFixNotif) { Text("روشن کردن اعلان‌ها") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lists.forEach { l ->
                        if (l.id == cur) Button(onClick = { cur = l.id; err = "" }) { Text(l.name) }
                        else OutlinedButton(onClick = { cur = l.id; err = "" }) { Text(l.name) }
                    }
                    OutlinedButton(onClick = { nameText = ""; kindSel = "normal"; dialog = "new" }) { Text("+ لیست جدید") }
                }
                if (cur != 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nameText = curList.name; dialog = "rename" }) { Text("تغییر نام لیست") }
                        TextButton(onClick = { dialog = "delete" }) { Text("حذف لیست") }
                    }
                }
                Text(curList.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (kind == "debt") {
                    Text("جمع طلب‌های وصول‌نشده: " + fmt(mine.filter { !it.done }.sumOf { it.amount }) + " تومان",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                if (kind == "car") {
                    Text("جمع هزینه‌ها: " + fmt(mine.sumOf { it.amount }) + " تومان",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                val titleLabel = when (kind) { "debt" -> "نام شخص"; "car" -> "نام قطعه یا تعمیر"; else -> "عنوان" }
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(titleLabel) }, singleLine = true)
                if (kind == "debt") {
                    OutlinedTextField(amountText, { amountText = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(),
                        label = { Text("مبلغ (تومان)") }, keyboardOptions = num, singleLine = true)
                }
                if (kind == "car") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(kmText, { kmText = it.filter { c -> c.isDigit() } }, Modifier.weight(1f),
                            label = { Text("کیلومتر") }, keyboardOptions = num, singleLine = true)
                        OutlinedTextField(amountText, { amountText = it.filter { c -> c.isDigit() } }, Modifier.weight(1f),
                            label = { Text("هزینه (تومان)") }, keyboardOptions = num, singleLine = true)
                    }
                }
                OutlinedButton(onClick = { calFor = "date" }, Modifier.fillMaxWidth()) {
                    Text((if (kind == "debt") "تاریخ سررسید: " else "تاریخ: ") + Jalali.full(sel))
                }
                if (kind == "normal") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            val (h, m) = time.split(":").map { it.toInt() }
                            TimePickerDialog(ctx, { _, hh, mm -> time = String.format(Locale.US, "%02d:%02d", hh, mm) }, h, m, true).show()
                        }) { Text("ساعت " + Jalali.fa(time)) }
                        OutlinedTextField(desc, { desc = it }, Modifier.weight(1f), label = { Text("توضیح کوتاه") }, singleLine = true)
                    }
                    OutlinedButton(onClick = { showRep = true }, Modifier.fillMaxWidth()) {
                        Text("تکرار: " + Rep.label(rep, every.toIntOrNull() ?: 1, endDate))
                    }
                } else {
                    OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("توضیح کوتاه") }, singleLine = true)
                }
                if (kind == "normal" || kind == "debt") {
                    Text("یادآوری (اعلان با صدای بوق)")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (if (kind == "debt") listOf(-1, 0, 1440) else listOf(-1, 0, 10, 60, 1440)).forEach { m ->
                            if (remind == m) Button(onClick = { remind = m }) { Text(Remind.label(m, kind)) }
                            else OutlinedButton(onClick = { remind = m }) { Text(Remind.label(m, kind)) }
                        }
                    }
                }
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    val amt = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    val km = kmText.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    val n = every.toIntOrNull() ?: 1
                    when {
                        title.isBlank() -> { err = if (kind == "debt") "نام شخص را وارد کن" else "عنوان را وارد کن" }
                        kind == "debt" && amt <= 0L -> { err = "مبلغ را وارد کن" }
                        else -> {
                            val r = if (kind == "normal") rep else "none"
                            commit(tasks + Task(System.currentTimeMillis(), title.trim(), desc.trim(), sel, time, false, cur,
                                r, if (n < 1) 1 else n, if (r == "none") "" else endDate, "", amt, km, if (kind == "car") -1 else remind))
                            title = ""; desc = ""; err = ""; sel = today
                            amountText = ""; kmText = ""; rep = "none"; every = "1"; endDate = ""; remind = -1
                        }
                    }
                }, Modifier.fillMaxWidth()) { Text("افزودن") }
            }
        }
        view.forEach { (name, occs) ->
            item(key = "sec-" + name) {
                Text(name + " (" + Jalali.fa(occs.size.toString()) + ")",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp))
            }
            if (occs.isEmpty()) item(key = "empty-" + name) {
                Text("چیزی نیست", style = MaterialTheme.typography.bodySmall)
            }
            if (kind == "normal") {
                occs.groupBy { it.date }.forEach { (date, group) ->
                    item(key = "d-" + name + date) {
                        Text(Jalali.label(date, today), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(group, key = { it.t.id.toString() + "@" + it.date }) { o ->
                        TaskRow(o, kind, today, { onToggle(o) }, { onDel(o) })
                    }
                }
            } else {
                items(occs, key = { it.t.id.toString() + "@" + it.date }) { o ->
                    TaskRow(o, kind, today, { onToggle(o) }, { onDel(o) })
                }
            }
        }
    }
}

@Composable
fun CalendarDialog(today: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val start = Jalali.fromIso(today)
    var jy by remember { mutableStateOf(start[0]) }
    var jm by remember { mutableStateOf(start[1]) }
    val offset = Jalali.weekdayIndex(Jalali.isoOfJ(jy, jm, 1))
    val cells = List(offset) { 0 } + (1..Jalali.monthLength(jy, jm)).toList()
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("بستن") } },
        dismissButton = { TextButton(onClick = { onPick(today) }) { Text("امروز") } },
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (jm == 1) { jm = 12; jy-- } else jm-- }) { Text("قبلی") }
                Text(Jalali.months[jm - 1] + " " + Jalali.fa(jy.toString()))
                TextButton(onClick = { if (jm == 12) { jm = 1; jy++ } else jm++ }) { Text("بعدی") }
            }
        },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val day = week.getOrNull(i) ?: 0
                            val iso = if (day == 0) "" else Jalali.isoOfJ(jy, jm, day)
                            val isToday = iso == today
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                    .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                                    .clickable(enabled = day != 0) { onPick(iso) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (day != 0) Text(
                                    Jalali.fa(day.toString()),
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun TaskRow(o: Occ, kind: String, today: String, onToggle: () -> Unit, onDelete: () -> Unit) {
    val t = o.t
    val done = Rep.isDone(t, o.date)
    val overdue = kind == "debt" && !done && t.date < today
    val line1 = when (kind) {
        "debt" -> t.title + " · " + fmt(t.amount) + " تومان"
        "car" -> t.title
        else -> Jalali.fa(t.time) + "   " + t.title
    }
    val rem = if (t.remind >= 0) "یادآوری: " + Remind.label(t.remind, kind) else ""
    val line2 = when (kind) {
        "debt" -> listOf("سررسید: " + Jalali.full(t.date) + (if (done) " · وصول شد" else ""), rem)
        "car" -> listOf(
            Jalali.full(t.date) +
                (if (t.km > 0L) " · " + Jalali.fa(t.km.toString()) + " کیلومتر" else "") +
                (if (t.amount > 0L) " · " + fmt(t.amount) + " تومان" else "")
        )
        else -> listOf(if (t.repeat != "none") "تکرار: " + Rep.label(t.repeat, t.every, t.until) else "", rem)
    }.filter { it.isNotEmpty() }.joinToString(" · ")
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (kind != "car") Checkbox(done, { onToggle() }) else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(line1, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (done && kind != "car") TextDecoration.LineThrough else null)
                if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (t.desc.isNotEmpty()) Text(t.desc, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onDelete) { Text("حذف") }
        }
    }
}
