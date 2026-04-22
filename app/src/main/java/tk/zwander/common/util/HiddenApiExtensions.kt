@file:Suppress("unused", "NOTHING_TO_INLINE", "deprecation")

package tk.zwander.common.util

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.IBinder
import android.os.UserHandle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

// ─── Constants ────────────────────────────────────────────────────────────────

const val CONTEXT_IGNORE_SECURITY: Int = 0x00000004
const val BIND_FOREGROUND_SERVICE_WHILE_AWAKE: Int = 0x02000000

// ─── AppWidgetProviderInfo.providerInfo ───────────────────────────────────────

val AppWidgetProviderInfo.providerInfo: ActivityInfo
    @SuppressLint("SoonBlockedPrivateApi")
    get() = try {
        val f = AppWidgetProviderInfo::class.java.getDeclaredField("providerInfo")
        f.isAccessible = true
        f.get(this) as ActivityInfo
    } catch (_: Exception) {
        ActivityInfo().also { info ->
            info.packageName = provider?.packageName ?: ""
            info.name = provider?.className ?: ""
        }
    }

// ─── Context hidden methods ───────────────────────────────────────────────────

@SuppressLint("PrivateApi")
fun Context.createApplicationContext(
    appInfo: android.content.pm.ApplicationInfo,
    flags: Int,
): Context = try {
    Context::class.java.getMethod(
        "createApplicationContext",
        android.content.pm.ApplicationInfo::class.java,
        Int::class.java,
    ).invoke(this, appInfo, flags) as Context
} catch (_: Exception) { this }

@SuppressLint("PrivateApi")
fun Context.createContextAsUser(user: UserHandle, flags: Int): Context = try {
    Context::class.java.getMethod(
        "createContextAsUser",
        UserHandle::class.java,
        Int::class.java,
    ).invoke(this, user, flags) as Context
} catch (_: Exception) { this }

@SuppressLint("PrivateApi")
fun Context.getServiceDispatcher(
    conn: ServiceConnection,
    handler: Handler,
    flags: Int,
): Any? = try {
    Context::class.java.getMethod(
        "getServiceDispatcher",
        ServiceConnection::class.java,
        Handler::class.java,
        Int::class.java,
    ).invoke(this, conn, handler, flags)
} catch (_: NoSuchMethodException) {
    try {
        Context::class.java.getMethod(
            "getServiceDispatcher",
            ServiceConnection::class.java,
            Handler::class.java,
            Long::class.java,
        ).invoke(this, conn, handler, flags.toLong())
    } catch (_: Exception) { null }
} catch (_: Exception) { null }

// ─── AppWidgetManager hidden methods ─────────────────────────────────────────

@SuppressLint("PrivateApi")
fun AppWidgetManager.bindRemoteViewsService(
    context: Context,
    appWidgetId: Int,
    intent: Intent,
    connection: Any?,
    flags: Int,
) {
    if (connection == null) return
    try {
        val iscClass = Class.forName("android.app.IServiceConnection")
        AppWidgetManager::class.java.getMethod(
            "bindRemoteViewsService",
            Context::class.java,
            Int::class.java,
            Intent::class.java,
            iscClass,
            Int::class.java,
        ).invoke(this, context, appWidgetId, intent, connection, flags)
    } catch (_: Exception) { }
}

// ─── IAppWidgetService helpers ────────────────────────────────────────────────

@SuppressLint("PrivateApi")
fun getIAppWidgetService(): Any? = try {
    val smClass = Class.forName("android.os.ServiceManager")
    val binder = smClass.getMethod("getService", String::class.java)
        .invoke(null, "appwidget") as? IBinder ?: return null
    val stubClass = Class.forName("com.android.internal.appwidget.IAppWidgetService\$Stub")
    stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
} catch (_: Exception) { null }

@SuppressLint("PrivateApi")
fun Any.getAppWidgetViewsReflect(packageName: String, widgetId: Int): android.widget.RemoteViews? = try {
    javaClass.getMethod("getAppWidgetViews", String::class.java, Int::class.java)
        .invoke(this, packageName, widgetId) as? android.widget.RemoteViews
} catch (_: Exception) { null }

// ─── AppWidgetHost.setListener ────────────────────────────────────────────────

@SuppressLint("PrivateApi")
fun android.appwidget.AppWidgetHost.setListenerReflect(widgetId: Int, listener: Any) {
    try {
        val listenerClass = Class.forName("android.appwidget.AppWidgetHost\$AppWidgetHostListener")
        javaClass.getMethod("setListener", Int::class.java, listenerClass)
            .invoke(this, widgetId, listener)
    } catch (_: Exception) { }
}

@SuppressLint("PrivateApi")
fun createAppWidgetHostListenerProxy(
    onUpdateProviderInfo: (AppWidgetProviderInfo?) -> Unit,
    onViewDataChanged: (Int) -> Unit,
    updateAppWidget: (android.widget.RemoteViews?) -> Unit,
): Any? = try {
    val cls = Class.forName("android.appwidget.AppWidgetHost\$AppWidgetHostListener")
    java.lang.reflect.Proxy.newProxyInstance(
        cls.classLoader,
        arrayOf(cls),
    ) { _, method, args ->
        when (method.name) {
            "onUpdateProviderInfo" -> onUpdateProviderInfo(args?.getOrNull(0) as? AppWidgetProviderInfo)
            "onViewDataChanged"    -> onViewDataChanged((args?.getOrNull(0) as? Int) ?: 0)
            "updateAppWidget"      -> updateAppWidget(args?.getOrNull(0) as? android.widget.RemoteViews)
        }
        null
    }
} catch (_: Exception) { null }

// ─── Intent.collectExtraIntentKeys ────────────────────────────────────────────

@SuppressLint("PrivateApi")
fun Intent.collectExtraIntentKeys() {
    try { Intent::class.java.getMethod("collectExtraIntentKeys").invoke(this) } catch (_: Exception) { }
}

// ─── PackageManager.getUserBadgeForDensity ────────────────────────────────────

@SuppressLint("PrivateApi")
fun PackageManager.getUserBadgeForDensity(user: UserHandle, density: Int): Drawable? = try {
    PackageManager::class.java.getMethod(
        "getUserBadgeForDensity",
        UserHandle::class.java,
        Int::class.java,
    ).invoke(this, user, density) as? Drawable
} catch (_: Exception) { null }

// ─── View / ViewRootImpl helpers ─────────────────────────────────────────────

@SuppressLint("SoonBlockedPrivateApi", "PrivateApi")
fun View.getViewRootImplRaw(): Any? = try {
    View::class.java.getMethod("getViewRootImpl").invoke(this)
} catch (_: NoSuchMethodException) {
    try {
        val f = View::class.java.getDeclaredField("mAttachInfo")
        f.isAccessible = true
        val ai = f.get(this) ?: return null
        val vri = ai.javaClass.getDeclaredField("mViewRootImpl")
        vri.isAccessible = true
        vri.get(ai)
    } catch (_: Exception) { null }
} catch (_: Exception) { null }

fun Any.isViewRootHardwareEnabled(): Boolean = try {
    javaClass.getMethod("isHardwareEnabled").invoke(this) as? Boolean ?: false
} catch (_: Exception) { false }

fun Any.createBackgroundBlurDrawableReflect(): Drawable? = try {
    javaClass.getMethod("createBackgroundBlurDrawable").invoke(this) as? Drawable
} catch (_: Exception) { null }

// ─── Insets.toRect ───────────────────────────────────────────────────────────

fun Insets.toRect(): Rect = Rect(left, top, right, bottom)

// ─── AccessibilityNodeInfo.isSealed ──────────────────────────────────────────

var AccessibilityNodeInfo.isSealed: Boolean
    @SuppressLint("SoonBlockedPrivateApi")
    get() = try {
        val f = AccessibilityNodeInfo::class.java.getDeclaredField("mSealed")
        f.isAccessible = true
        f.getBoolean(this)
    } catch (_: Exception) { false }
    @SuppressLint("SoonBlockedPrivateApi")
    set(value) {
        try {
            val f = AccessibilityNodeInfo::class.java.getDeclaredField("mSealed")
            f.isAccessible = true
            f.setBoolean(this, value)
        } catch (_: Exception) { }
    }

// ─── ShortcutInfo hidden accessors ───────────────────────────────────────────

@SuppressLint("PrivateApi")
fun android.content.pm.ShortcutInfo.getIconReflect(): android.graphics.drawable.Icon? = try {
    javaClass.getMethod("getIcon").invoke(this) as? android.graphics.drawable.Icon
} catch (_: Exception) { null }

fun android.content.pm.ShortcutInfo.toInsecureStringCompat(): String = try {
    javaClass.getMethod("toInsecureString").invoke(this)?.toString() ?: toString()
} catch (_: Exception) { toString() }
