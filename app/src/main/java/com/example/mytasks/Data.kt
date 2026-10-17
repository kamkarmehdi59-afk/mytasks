package com.example.mytasks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class Task(
    val id: Long, val title: String, val desc: String, val date: String,
    val time: String, val done: Boolean, val listId: Long = 0L
)

data class TList(val id: Long, val name: String)

object Lists {
    val main = TList(0L, "کارهای روزانه")

    fun load(c: Context): List<TList> {
        val a = JSONArray(c.getSharedPreferences("lists", 0).getString("list", "[]") ?: "[]")
        val extra = (0 until a.length()).map {
            val o = a.getJSONObject(it)
            TList(o.getLong("id"), o.getString("name"))
        }
        return listOf(main) + extra
    }

    fun save(c: Context, l: List<TList>) {
        val a = JSONArray()
        l.filter { it.id != 0L }.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        c.getSharedPreferences("lists", 0).edit().putString("list", a.toString()).apply()
    }
}

object Store {
    fun load(c: Context): List<Task> {
        val a = JSONArray(c.getSharedPreferences("tasks", 0).getString("list", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Task(o.getLong("id"), o.getString("title"), o.getString("desc"), o.getString("date"),
                o.getString("time"), o.getBoolean("done"), o.optLong("listId", 0L))
        }.sortedWith(compareBy({ it.date }, { it.time }))
    }

    fun save(c: Context, l: List<Task>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("desc", it.desc)
                .put("date", it.date).put("time", it.time).put("done", it.done).put("listId", it.listId))
        }
        c.getSharedPreferences("tasks", 0).edit().putString("list", a.toString()).apply()
        TaskWidget.refresh(c)
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
