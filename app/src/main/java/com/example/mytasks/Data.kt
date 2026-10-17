package com.example.mytasks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class Task(
    val id: Long, val title: String, val desc: String, val date: String,
    val time: String, val done: Boolean, val listId: Long = 0L,
    val repeat: String = "none", val every: Int = 1, val until: String = "",
    val doneOn: String = "", val amount: Long = 0L, val km: Long = 0L
)

data class Occ(val t: Task, val date: String)

data class TList(val id: Long, val name: String, val kind: String = "normal")

fun fmt(n: Long) = Jalali.fa(String.format(Locale.US, "%,d", n))

object Lists {
    val main = TList(0L, "کارهای روزانه", "normal")

    fun load(c: Context): List<TList> {
        val a = JSONArray(c.getSharedPreferences("lists", 0).getString("list", "[]") ?: "[]")
        val extra = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            TList(o.getLong("id"), o.getString("name"), o.optString("kind", "normal"))
        }
        return listOf(main) + extra
    }

    fun save(c: Context, l: List<TList>) {
        val a = JSONArray()
        l.filter { it.id != 0L }.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind))
        }
        c.getSharedPreferences("lists", 0).edit().putString("list", a.toString()).apply()
    }
}

object Store {
    fun load(c: Context): List<Task> {
        val a = JSONArray(c.getSharedPreferences("tasks", 0).getString("list", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Task(o.getLong("id"), o.getString("title"), o.getString("desc"), o.getString("date"),
                o.getString("time"), o.getBoolean("done"), o.optLong("listId", 0L),
                o.optString("repeat", "none"), o.optInt("every", 1), o.optString("until", ""),
                o.optString("doneOn", ""), o.optLong("amount", 0L), o.optLong("km", 0L))
        }.sortedWith(compareBy({ it.date }, { it.time }))
    }

    fun save(c: Context, l: List<Task>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("desc", it.desc)
                .put("date", it.date).put("time", it.time).put("done", it.done).put("listId", it.listId)
                .put("repeat", it.repeat).put("every", it.every).put("until", it.until)
                .put("doneOn", it.doneOn).put("amount", it.amount).put("km", it.km))
        }
        c.getSharedPreferences("tasks", 0).edit().putString("list", a.toString()).apply()
        TaskWidget.refresh(c)
    }
}

object Rep {
    fun dayNum(iso: String): Long {
        val p = iso.split("-").map { it.toInt() }
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(p[0], p[1] - 1, p[2])
        return c.timeInMillis / 86400000L
    }

    fun addDay(iso: String): String {
        val p = iso.split("-").map { it.toInt() }
        val c = Calendar.getInstance()
        c.set(p[0], p[1] - 1, p[2], 12, 0, 0)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return Jalali.iso(c)
    }

    fun occursOn(t: Task, d: String): Boolean {
        if (t.repeat == "none") return t.date == d
        if (d < t.date) return false
        if (t.until.isNotEmpty() && d > t.until) return false
        val n = if (t.every < 1) 1 else t.every
        return when (t.repeat) {
            "day" -> (dayNum(d) - dayNum(t.date)) % n == 0L
            "week" -> (dayNum(d) - dayNum(t.date)) % (7L * n) == 0L
            "month" -> {
                val a = Jalali.fromIso(t.date)
                val b = Jalali.fromIso(d)
                val diff = (b[0] * 12 + b[1]) - (a[0] * 12 + a[1])
                diff % n == 0 && b[2] == minOf(a[2], Jalali.monthLength(b[0], b[1]))
            }
            "year" -> {
                val a = Jalali.fromIso(t.date)
                val b = Jalali.fromIso(d)
                val diff = b[0] - a[0]
                diff % n == 0 && b[1] == a[1] && b[2] == minOf(a[2], Jalali.monthLength(b[0], b[1]))
            }
            else -> false
        }
    }

    fun next(t: Task, from: String): String? {
        var d = from
        for (i in 1..4000) {
            d = addDay(d)
            if (t.until.isNotEmpty() && d > t.until) return null
            if (occursOn(t, d)) return d
        }
        return null
    }

    fun isDone(t: Task, date: String): Boolean =
        if (t.repeat == "none") t.done else t.doneOn.split(",").contains(date)

    fun toggle(t: Task, date: String): Task {
        if (t.repeat == "none") return t.copy(done = !t.done)
        val s = t.doneOn.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (s.contains(date)) s.remove(date) else s.add(date)
        return t.copy(doneOn = s.joinToString(","))
    }

    fun label(rep: String, every: Int, until: String): String {
        if (rep == "none") return "بدون تکرار"
        val n = if (every < 1) 1 else every
        val u = when (rep) { "day" -> "روز"; "week" -> "هفته"; "month" -> "ماه"; else -> "سال" }
        val s = if (n == 1) "هر " + u else "هر " + Jalali.fa(n.toString()) + " " + u
        return if (until.isEmpty()) s else s + " · تا " + Jalali.text(until)
    }
}

object Jalali {
    val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val weekdays = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    fun fa(s: String) = s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

    fun toJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val g2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (g2 + 3) / 4 - (g2 + 99) / 100 + (g2 + 399) / 400 + gd + m[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + (if (days < 186) days % 31 else (days - 186) % 30)
        return intArrayOf(jy, jm, jd)
    }

    fun toGregorian(jy0: Int, jm: Int, jd: Int): IntArray {
        val jy = jy0 + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + jd + (if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { gy += (days - 1) / 365; days = (days - 1) % 365 }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val sa = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sa[gm]) { gd -= sa[gm]; gm++ }
        return intArrayOf(gy, gm, gd)
    }

    fun iso(c: Calendar) = String.format(Locale.US, "%04d-%02d-%02d", c[Calendar.YEAR], c[Calendar.MONTH] + 1, c[Calendar.DAY_OF_MONTH])
    fun todayIso() = iso(Calendar.getInstance())
    fun fromIso(s: String): IntArray { val p = s.split("-").map { it.toInt() }; return toJalali(p[0], p[1], p[2]) }
    fun text(s: String): String { val j = fromIso(s); return fa(j[2].toString()) + " " + months[j[1] - 1] + " " + fa(j[0].toString()) }

    fun isoOfJ(jy: Int, jm: Int, jd: Int): String {
        val g = toGregorian(jy, jm, jd)
        return String.format(Locale.US, "%04d-%02d-%02d", g[0], g[1], g[2])
    }

    fun weekdayIndex(iso: String): Int {
        val p = iso.split("-").map { it.toInt() }
        val c = Calendar.getInstance()
        c.set(p[0], p[1] - 1, p[2])
        return c[Calendar.DAY_OF_WEEK] % 7
    }

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> {
            val g = toGregorian(jy, 12, 30)
            if (toJalali(g[0], g[1], g[2])[1] == 12) 30 else 29
        }
    }

    fun full(s: String) = weekdays[weekdayIndex(s)] + " " + text(s)

    fun label(s: String, today: String): String {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, 1)
        val p = when (s) { today -> "امروز"; iso(c) -> "فردا"; else -> "" }
        return if (p.isEmpty()) full(s) else p + " · " + full(s)
    }
}
