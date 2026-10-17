package com.example.mytasks

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Remind.fire(c)
    }
}

object Remind {
    const val ACTION = "com.example.mytasks.REMIND"
    private const val CH = "reminders_beep_v1"

    fun label(m: Int, kind: String): String = when (m) {
        -1 -> "بدون یادآوری"
        0 -> if (kind == "debt") "همان روز ۹ صبح" else "سر وقت"
        10 -> "۱۰ دقیقه قبل"
        60 -> "۱ ساعت قبل"
        1440 -> if (kind == "debt") "۱ روز قبل ۹ صبح" else "۱ روز قبل"
        else -> Jalali.fa(m.toString()) + " دقیقه قبل"
    }

    private fun ms(date: String, time: String): Long {
        val p = date.split("-").map { it.toInt() }
        val h = time.split(":").map { it.toInt() }
        val c = Calendar.getInstance()
        c.clear()
        c.set(p[0], p[1] - 1, p[2], h[0], h[1], 0)
        return c.timeInMillis
    }

    private fun nextTrigger(t: Task, after: Long): Long? {
        val off = t.remind * 60000L
        if (t.repeat == "none") {
            if (t.done) return null
            val tr = ms(t.date, t.time) - off
            return if (tr > after) tr else null
        }
        val c = Calendar.getInstance()
        c.timeInMillis = after + off
        var d = Jalali.iso(c)
        for (i in 0..4000) {
            if (Rep.occursOn(t, d) && !Rep.isDone(t, d)) {
                val tr = ms(d, t.time) - off
                if (tr > after) return tr
            }
            if (t.until.isNotEmpty() && d > t.until) return null
            d = Rep.addDay(d)
        }
        return null
    }

    private fun lastMark(c: Context): Long {
        val p = c.getSharedPreferences("remind", 0)
        val v = p.getLong("last", 0L)
        if (v > 0L) return v
        val now = System.currentTimeMillis()
        p.edit().putLong("last", now).apply()
        return now
    }

    private fun pending(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 2, Intent(c, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c)
        val last = lastMark(c)
        var best: Long? = null
        for (t in Store.load(c)) {
            if (t.remind < 0) continue
            val tr = nextTrigger(t, last) ?: continue
            if (best == null || tr < best) best = tr
        }
        am.cancel(pi)
        if (best == null) return
        val at = maxOf(best, System.currentTimeMillis() + 1500L)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun fire(c: Context) {
        val now = System.currentTimeMillis()
        val last = lastMark(c)
        for (t in Store.load(c)) {
            if (t.remind < 0) continue
            var tr = nextTrigger(t, last)
            var g = 0
            while (tr != null && tr <= now && g < 20) {
                if (now - tr <= 12 * 3600000L) post(c, t, tr)
                tr = nextTrigger(t, tr)
                g++
            }
        }
        c.getSharedPreferences("remind", 0).edit().putLong("last", now).apply()
        schedule(c)
    }

    private fun post(c: Context, t: Task, tr: Long) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val uri = Uri.parse("android.resource://" + c.packageName + "/" + R.raw.beep)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "یادآوری کارها", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(
                uri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val kind = Lists.load(c).firstOrNull { it.id == t.listId }?.kind ?: "normal"
        val title = if (kind == "debt") "سررسید طلب: " + t.title else t.title
        val extra = if (t.desc.isNotEmpty()) " · " + t.desc else ""
        val text = if (kind == "debt") fmt(t.amount) + " تومان" + extra else Jalali.fa(t.time) + extra
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CH)
        else Notification.Builder(c).setSound(uri)
        b.setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
        nm.notify((t.id xor tr).toInt(), b.build())
    }
}
