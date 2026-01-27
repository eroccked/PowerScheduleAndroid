package com.powerschedule.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class ScreenUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, PowerScheduleWidget::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)

            if (widgetIds.isNotEmpty()) {
                widgetIds.forEach { widgetId ->
                    PowerScheduleWidget.updateAppWidget(context, appWidgetManager, widgetId)
                }
            }
        }
    }
}