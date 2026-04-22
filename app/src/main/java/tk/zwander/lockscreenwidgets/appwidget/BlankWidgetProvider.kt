package tk.zwander.lockscreenwidgets.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.coolappstore.everlastingandroidtweak.R

class BlankWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val view = RemoteViews(context.packageName, R.layout.blank_widget)

        appWidgetManager.updateAppWidget(appWidgetIds, view)
    }
}