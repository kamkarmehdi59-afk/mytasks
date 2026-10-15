package com.example.mytasks

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TaskWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { draw(c, m, it) }
    }

    companion object {
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, TaskWidget::class.java)).forEach { draw(c, m, it) }
        }

        private fun draw(c: Context, m: AppWidgetManager, id: Int) {
            val today = Jalali.todayIso()
            val items = Store.load(c).filter { it.date == today && !it.done }.take(5)
            val text = if (items.isEmpty()) "امروز کاری باقی نمانده"
            else items.joinToString(System.lineSeparator()) { Jalali.fa(it.time) + "   " + it.title }
            val v = RemoteViews(c.packageName, R.layout.widget_tasks)
            v.setTextViewText(R.id.w_title, "رویدادهای امروز · " + Jalali.text(today))
            v.setTextViewText(R.id.w_list, text)
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_root, pi)
            m.updateAppWidget(id, v)
        }
    }
}
