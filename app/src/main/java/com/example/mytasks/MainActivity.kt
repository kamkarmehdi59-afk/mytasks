package com.example.mytasks

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var tasks by remember { mutableStateOf(Store.load(ctx)) }
    val today = Jalali.todayIso()
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(today) }
    var showCal by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf("09:00") }
    var err by remember { mutableStateOf("") }
    fun commit(l: List<Task>) { Store.save(ctx, l); tasks = Store.load(ctx) }

    if (showCal) CalendarDialog(sel, today, { sel = it; showCal = false }, { showCal = false })

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("کارهای من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(Jalali.full(today))
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
                        commit(tasks + Task(System.currentTimeMillis(), title.trim(), desc.trim(), sel, time, false))
                        title = ""; desc = ""; err = ""
                    }
                }, Modifier.fillMaxWidth()) { Text("افزودن کار") }
            }
        }
        val sections = listOf(
            "امروز" to tasks.filter { it.date == today },
            "آینده" to tasks.filter { it.date > today },
            "گذشته" to tasks.filter { it.date < today }.sortedByDescending { it.date }
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
fun CalendarDialog(selected: String, today: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val start = Jalali.fromIso(selected)
    var jy by remember { mutableStateOf(start[0]) }
    var jm by remember { mutableStateOf(start[1]) }
    val offset = Jalali.weekdayIndex(Jalali.isoOfJ(jy, jm, 1))
    val cells = List(offset) { 0 } + (1..Jalali.monthLength(jy, jm)).toList()
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("بستن") } },
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
                            val isSel = iso == selected
                            val isToday = iso == today
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                    .clickable(enabled = day != 0) { onPick(iso) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (day != 0) Text(
                                    Jalali.fa(day.toString()),
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary
                                    else if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
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
