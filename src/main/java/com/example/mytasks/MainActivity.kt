package com.example.mytasks

import android.app.TimePickerDialog
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize().systemBarsPadding()) { App() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TaskWidget.refresh(this)
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var tasks by remember { mutableStateOf(Store.load(ctx)) }
    var lists by remember { mutableStateOf(Lists.load(ctx)) }
    var cur by remember { mutableStateOf(0L) }
    val today = Jalali.todayIso()
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(today) }
    var showCal by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf("09:00") }
    var err by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    fun commit(l: List<Task>) { Store.save(ctx, l); tasks = Store.load(ctx) }
    fun commitLists(l: List<TList>) { Lists.save(ctx, l); lists = Lists.load(ctx) }
    val curName = lists.firstOrNull { it.id == cur }?.name ?: Lists.main.name

    if (showCal) CalendarDialog(today, { sel = it; showCal = false }, { showCal = false })

    if (dialog == "new" || dialog == "rename") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(if (dialog == "new") "لیست جدید" else "تغییر نام لیست") },
            text = { OutlinedTextField(nameText, { nameText = it }, singleLine = true, label = { Text("عنوان") }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = nameText.trim()
                    if (n.isNotEmpty()) {
                        if (dialog == "new") {
                            val id = System.currentTimeMillis()
                            commitLists(lists + TList(id, n))
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

    val mine = tasks.filter { it.listId == cur }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("کارهای من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(Jalali.full(today))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lists.forEach { l ->
                        if (l.id == cur) Button(onClick = { cur = l.id }) { Text(l.name) }
                        else OutlinedButton(onClick = { cur = l.id }) { Text(l.name) }
                    }
                    OutlinedButton(onClick = { nameText = ""; dialog = "new" }) { Text("+ لیست جدید") }
                }
                if (cur != 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nameText = curName; dialog = "rename" }) { Text("تغییر نام لیست") }
                        TextButton(onClick = { dialog = "delete" }) { Text("حذف لیست") }
                    }
                }
                Text(curName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان") }, singleLine = true)
                OutlinedButton(onClick = { showCal = true }, Modifier.fillMaxWidth()) {
                    Text("تاریخ: " + Jalali.full(sel))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val (h, m) = time.split(":").map { it.toInt() }
                        TimePickerDialog(ctx, { _, hh, mm -> time = String.format(Locale.US, "%02d:%02d", hh, mm) }, h, m, true).show()
                    }) { Text("ساعت " + Jalali.fa(time)) }
                    OutlinedTextField(desc, { desc = it }, Modifier.weight(1f), label = { Text("توضیح کوتاه") }, singleLine = true)
                }
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    if (title.isBlank()) err = "عنوان را وارد کن"
                    else {
                        commit(tasks + Task(System.currentTimeMillis(), title.trim(), desc.trim(), sel, time, false, cur))
                        title = ""; desc = ""; err = ""; sel = today
                    }
                }, Modifier.fillMaxWidth()) { Text("افزودن کار") }
            }
        }
        val sections = listOf(
            "امروز" to mine.filter { it.date == today },
            "آینده" to mine.filter { it.date > today },
            "گذشته" to mine.filter { it.date < today }.sortedByDescending { it.date }
        )
        sections.forEach { (name, all) ->
            item(key = "sec-" + name) {
                Text(name + " (" + Jalali.fa(all.size.toString()) + ")",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp))
            }
            if (all.isEmpty()) item(key = "empty-" + name) {
                Text("کاری نیست", style = MaterialTheme.typography.bodySmall)
            }
            all.groupBy { it.date }.forEach { (date, list) ->
                item(key = "d-" + name + date) {
                    Text(Jalali.label(date, today), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                }
                items(list, key = { it.id }) { t ->
                    TaskRow(t,
                        { commit(tasks.map { x -> if (x.id == t.id) x.copy(done = !x.done) else x }) },
                        { commit(tasks.filter { x -> x.id != t.id }) })
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
fun TaskRow(t: Task, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(t.done, { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(Jalali.fa(t.time) + "   " + t.title, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null)
                if (t.desc.isNotEmpty()) Text(t.desc, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onDelete) { Text("حذف") }
        }
    }
}
