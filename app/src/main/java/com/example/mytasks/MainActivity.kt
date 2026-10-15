package com.example.mytasks

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    val j = remember { Jalali.fromIso(today) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var y by remember { mutableStateOf(j[0].toString()) }
    var mo by remember { mutableStateOf(j[1].toString()) }
    var d by remember { mutableStateOf(j[2].toString()) }
    var time by remember { mutableStateOf("09:00") }
    var err by remember { mutableStateOf("") }
    fun commit(l: List<Task>) { Store.save(ctx, l); tasks = Store.load(ctx) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("کارهای من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(Jalali.text(today))
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(d, { d = it }, Modifier.weight(1f), label = { Text("روز") }, keyboardOptions = num, singleLine = true)
                    OutlinedTextField(mo, { mo = it }, Modifier.weight(1f), label = { Text("ماه") }, keyboardOptions = num, singleLine = true)
                    OutlinedTextField(y, { y = it }, Modifier.weight(1.4f), label = { Text("سال") }, keyboardOptions = num, singleLine = true)
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
                    val yy = y.toIntOrNull(); val mm = mo.toIntOrNull(); val dd = d.toIntOrNull()
                    if (title.isBlank()) err = "عنوان را وارد کن"
                    else if (yy == null || mm == null || dd == null || mm !in 1..12 || dd !in 1..31) err = "تاریخ معتبر نیست"
                    else {
                        val g = Jalali.toGregorian(yy, mm, dd)
                        val iso = String.format(Locale.US, "%04d-%02d-%02d", g[0], g[1], g[2])
                        commit(tasks + Task(System.currentTimeMillis(), title.trim(), desc.trim(), iso, time, false))
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
        }    }
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
