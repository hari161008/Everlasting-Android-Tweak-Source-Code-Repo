package tk.zwander.common.tiles.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import tk.zwander.common.activities.add.AddTileWidgetActivity
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.cropBitmapTransparency
import tk.zwander.common.util.createAppWidgetHostListenerProxy
import tk.zwander.common.util.density
import tk.zwander.common.util.getAppWidgetViewsReflect
import tk.zwander.common.util.getApplicationInfoInAnyState
import tk.zwander.common.util.getIAppWidgetService
import tk.zwander.common.util.getRemoteViewsToApplyCompat
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.providerInfo
import tk.zwander.common.util.setListenerReflect
import tk.zwander.common.util.textAsBitmap
import tk.zwander.common.util.toSafeBitmap
import com.coolappstore.everlastingandroidtweak.R
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents the base structure and logic for a widget tile on One UI.
 * Handles redirecting widget RemoteViews to Samsung's System UI.
 *
 * All hidden Android APIs (IAppWidgetService, ServiceManager, AppWidgetHostListener)
 * are accessed via reflection through helper functions in HiddenApiExtensions.kt.
 */
@Suppress("MemberVisibilityCanBePrivate")
@RequiresApi(Build.VERSION_CODES.N)
abstract class BaseWidgetTile : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {

    /** Raw IAppWidgetService proxy, obtained via reflection. */
    protected val iManager: Any? by lazy { getIAppWidgetService() }

    protected abstract val tileId: Int
    protected val widgetId: Int
        get() {
            val data = prefManager.customTiles[tileId] ?: return -1
            return data.widgetId
        }
    protected val widgetInfo: AppWidgetProviderInfo?
        get() {
            val widgetId = widgetId
            if (widgetId == -1) return null
            return appWidgetManager.getAppWidgetInfo(widgetId)
        }
    protected val widgetPackage: String?
        get() = widgetInfo?.providerInfo?.packageName
    protected val remoteResources: Resources?
        get() {
            return try {
                val packageName = widgetPackage ?: return null
                val appInfo = packageManager.getApplicationInfoInAnyState(packageName)
                packageManager.getResourcesForApplication(appInfo)
            } catch (e: Exception) {
                logUtils.debugLog("Error getting remote resources for $widgetPackage", e)
                null
            }
        }

    protected val views by lazy { AtomicReference(generateViews()) }

    /**
     * A proxy implementing [android.appwidget.AppWidgetHost.AppWidgetHostListener]
     * created via reflection. Returns null on API < 33 or if the interface is unavailable.
     */
    protected val appWidgetHostListener: Any? by lazy { createAppWidgetHostListenerProxyCompat() }

    @SuppressLint("NewApi")
    private fun createAppWidgetHostListenerProxyCompat(): Any? {
        return try {
            createAppWidgetHostListenerProxy(
                onUpdateProviderInfo = { _ ->
                    updateTile()
                    notifySystemUIOfChanges()
                },
                onViewDataChanged = { _ ->
                    notifySystemUIOfChanges()
                },
                updateAppWidget = { remoteViews ->
                    this@BaseWidgetTile.views.set(
                        remoteViews?.getRemoteViewsToApplyCompat(this@BaseWidgetTile)
                    )
                    notifySystemUIOfChanges()
                },
            )
        } catch (e: Throwable) {
            logUtils.debugLog("Error creating AppWidgetHostListener", e)
            null
        }
    }

    override fun onStartListening() {
        super.onStartListening()

        updateTile()
        notifySystemUIOfChanges()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                appWidgetHostListener?.let { listener ->
                    widgetHostCompat.setListenerReflect(widgetId, listener)
                }
            } catch (e: Throwable) {
                logUtils.debugLog("Error setting AppWidgetHostListener for $widgetId", e)
            }
        }
        widgetHostCompat.startListening(this)
    }

    override fun onStopListening() {
        super.onStopListening()
        widgetHostCompat.stopListening(this)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        prefManager.customTiles = prefManager.customTiles.apply { remove(tileId) }
    }

    @Suppress("unused")
    open fun semGetDetailViewTitle(): CharSequence? = qsTile?.label

    @Suppress("unused")
    open fun semGetDetailViewSettingButtonName(): CharSequence? = "Test"

    @Suppress("unused")
    open fun semIsToggleButtonExists(): Boolean = false

    @Suppress("unused")
    open fun semIsToggleButtonChecked(): Boolean = false

    @Suppress("unused")
    open fun semGetDetailView(): RemoteViews? {
        views.set(generateViews())
        return views.get()
    }

    open fun semGetSettingsIntent(): Intent? =
        AddTileWidgetActivity.createIntent(this, tileId)

    @Suppress("unused")
    open fun semSetToggleButtonChecked(checked: Boolean) {
        startActivity(semGetSettingsIntent())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PrefManager.KEY_CUSTOM_TILES) {
            updateTile()
            notifySystemUIOfChanges()
        }
    }

    private fun notifySystemUIOfChanges() {
        try {
            TileService::class.java.getMethod("semUpdateDetailView").invoke(this)
        } catch (e: Throwable) {
            logUtils.normalLog("Error updating widget view", e)
        }
    }

    private fun generateViews(): RemoteViews {
        var outerView = generateDefaultViews()

        try {
            // Use reflection to call IAppWidgetService.getAppWidgetViews
            val widgetView = iManager?.getAppWidgetViewsReflect(packageName, widgetId)
            if (widgetView != null) {
                logUtils.debugLog("Custom widget loaded for tile ID $tileId")
                outerView = widgetView.getRemoteViewsToApplyCompat(this)
            } else {
                logUtils.debugLog("Custom widget view is null for tile ID $tileId")
            }
        } catch (e: Exception) {
            logUtils.debugLog("Exception adding widget for tile ID $tileId", e)
        }

        return outerView
    }

    private fun generateDefaultViews(): RemoteViews {
        val views = RemoteViews(packageName, R.layout.default_tile_views)
        views.setOnClickPendingIntent(
            R.id.add, PendingIntent.getActivity(
                this, 100, semGetSettingsIntent(), PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }

    private fun updateTile() {
        widgetInfo.apply {
            if (this != null) {
                with(remoteResources) {
                    if (this != null) {
                        val iconResId = try {
                            this@apply.javaClass.getMethod("getIcon").invoke(this@apply) as? Int
                                ?: this@apply.icon
                        } catch (_: Exception) { this@apply.icon }
                        val iconDrawable = ResourcesCompat.getDrawable(this, iconResId, this.newTheme())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && iconDrawable is AdaptiveIconDrawable) {
                            val foreground = iconDrawable.foreground
                            qsTile?.icon = Icon.createWithBitmap(
                                foreground.toSafeBitmap(density, maxSize = 128.dp).cropBitmapTransparency()
                            )
                        } else if (iconDrawable is BitmapDrawable) {
                            qsTile?.icon = Icon.createWithBitmap(
                                qsTile?.label?.first()?.toString()?.textAsBitmap(128f, Color.WHITE)
                            )
                        } else {
                            qsTile?.icon = Icon.createWithResource(widgetPackage, iconResId)
                        }
                    } else {
                        qsTile?.icon = Icon.createWithResource(widgetPackage, this@apply.icon)
                    }
                }
                qsTile?.label = this.loadLabel(packageManager)
            } else {
                qsTile?.icon = Icon.createWithResource(
                    this@BaseWidgetTile.packageName, R.drawable.ic_baseline_launch_24
                )
                qsTile?.label = resources.getString(R.string.app_name)
            }
        }

        qsTile?.state = if (widgetInfo != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}
