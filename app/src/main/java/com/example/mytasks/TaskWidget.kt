package com.example.mytasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

class TaskWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        refresh(c)
    }

    override fun onEnabled(c: Context) {
        schedule(c)
    }

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        when (i.action) {
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, MIDNIGHT -> refresh(c)
        }
    }

    companion object {
        const val MIDNIGHT = "com.example.mytasks.MIDNIGHT"

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, TaskWidget::class.java))
            ids.forEach { draw(c, m, it) }
            if (ids.isNotEmpty()) schedule(c)
        }

        private fun schedule(c: Context) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 5)
            cal.set(Calendar.MILLISECOND, 0)
            val pi = PendingIntent.getBroadcast(
                c, 1, Intent(c, TaskWidget::class.java).setAction(MIDNIGHT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }

        private fun draw(c: Context, m: AppWidgetManager, id: Int) {
            val today = Jalali.todayIso()
            val items = Store.load(c).filter { it.listId == 0L && Rep.occursOn(it, today) && !Rep.isDone(it, today) }.sortedBy { it.time }.take(8)
            val text = if (items.isEmpty()) "امروز کاری باقی نمانده"
            else items.joinToString(System.lineSeparator()) { Jalali.fa(it.time) + "   " + it.title }
            val v = RemoteViews(c.packageName, R.layout.widget_tasks)
            v.setTextViewText(R.id.w_title, "رویدادهای امروز · " + Jalali.full(today))
            v.setTextViewText(R.id.w_list, text)
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_root, pi)
            m.updateAppWidget(id, v)
        }
    }
}
