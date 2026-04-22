package tk.zwander.common.appwidget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.Keep
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers.named
import tk.zwander.common.util.BIND_FOREGROUND_SERVICE_WHILE_AWAKE
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.bindRemoteViewsService
import tk.zwander.common.util.collectExtraIntentKeys
import tk.zwander.common.util.getServiceDispatcher
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler

/**
 * A proxy [RemoteViewsService] that intercepts widget collection requests and
 * bridges them to the widget's real RemoteViewsService.
 *
 * The Factory inner class acts as a Binder service endpoint implementing the
 * IRemoteViewsFactory Binder interface. Rather than depending on the hidden
 * IRemoteViewsFactory AIDL class at compile time, we implement the Binder
 * protocol directly via [Binder.onTransact], matching the system's transaction
 * codes and Parcel read/write ordering exactly.
 *
 * The [wrapped] field holds a reference to the real factory (obtained via
 * reflection from the actual widget's RemoteViewsService) and all method calls
 * are dispatched to it via reflection.
 */
class RemoteViewsProxyService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory? = null

    override fun onBind(intent: Intent?): IBinder? {
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it != -1 } ?: return null
        val widgetIntent = IntentCompat.getParcelableExtra(intent, EXTRA_INTENT, Intent::class.java)
            ?: return null

        sFactories[widgetId]?.let {
            if (!it.created) it.onCreate()
            return it
        }

        val newFactory = Factory(widgetId, widgetIntent)
        newFactory.onCreate()
        sFactories[widgetId] = newFactory
        return newFactory
    }

    inner class Factory(
        private val widgetId: Int,
        private val widgetIntent: Intent,
    ) : Binder() {
        // IRemoteViewsFactory Binder transaction codes (AOSP ordering, 1-indexed).
        private val T_ON_DATA_SET_CHANGED        = IBinder.FIRST_CALL_TRANSACTION      // 1
        private val T_ON_DATA_SET_CHANGED_ASYNC  = IBinder.FIRST_CALL_TRANSACTION + 1  // 2
        private val T_ON_DESTROY                 = IBinder.FIRST_CALL_TRANSACTION + 2  // 3
        private val T_GET_COUNT                  = IBinder.FIRST_CALL_TRANSACTION + 3  // 4
        private val T_GET_VIEW_AT                = IBinder.FIRST_CALL_TRANSACTION + 4  // 5
        private val T_GET_LOADING_VIEW           = IBinder.FIRST_CALL_TRANSACTION + 5  // 6
        private val T_GET_VIEW_TYPE_COUNT        = IBinder.FIRST_CALL_TRANSACTION + 6  // 7
        private val T_GET_ITEM_ID                = IBinder.FIRST_CALL_TRANSACTION + 7  // 8
        private val T_HAS_STABLE_IDS             = IBinder.FIRST_CALL_TRANSACTION + 8  // 9
        private val T_IS_CREATED                 = IBinder.FIRST_CALL_TRANSACTION + 9  // 10
        private val T_GET_REMOTE_COLLECTION_ITEMS = IBinder.FIRST_CALL_TRANSACTION + 10 // 11

        private val DESCRIPTOR = "com.android.internal.widget.IRemoteViewsFactory"

        var created = false
        /** The real IRemoteViewsFactory, held as Any? and called via reflection. */
        var wrapped: Any? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                wrapped = asInterfaceReflect(service)
                created = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                wrapped = null
                created = false
                try { unbindService(this) } catch (_: IllegalArgumentException) {}
            }

            override fun onNullBinding(name: ComponentName?) {
                wrapped = null
                created = false
                try { unbindService(this) } catch (_: IllegalArgumentException) {}
            }
        }

        /** Calls IRemoteViewsFactory.Stub.asInterface via reflection. */
        @SuppressLint("PrivateApi")
        private fun asInterfaceReflect(binder: IBinder?): Any? = try {
            val stubClass = Class.forName("com.android.internal.widget.IRemoteViewsFactory\$Stub")
            stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (_: Exception) { null }

        /** Calls a no-arg void method on [wrapped] by name. */
        private fun callVoid(method: String) {
            try { wrapped?.javaClass?.getMethod(method)?.invoke(wrapped) } catch (_: Exception) {}
        }

        /** Calls getCount() on the wrapped factory via reflection. */
        private fun getCountReflect(): Int = try {
            wrapped?.javaClass?.getMethod("getCount")?.invoke(wrapped) as? Int ?: 0
        } catch (_: Exception) { 0 }

        /** Calls getViewAt(position) on the wrapped factory. */
        private fun getViewAtReflect(position: Int): RemoteViews? = try {
            wrapped?.javaClass?.getMethod("getViewAt", Int::class.java)
                ?.invoke(wrapped, position) as? RemoteViews
        } catch (_: Exception) { null }

        /** Calls getLoadingView() on the wrapped factory. */
        private fun getLoadingViewReflect(): RemoteViews? = try {
            wrapped?.javaClass?.getMethod("getLoadingView")?.invoke(wrapped) as? RemoteViews
        } catch (_: Exception) { null }

        /** Calls getViewTypeCount() on the wrapped factory. */
        private fun getViewTypeCountReflect(): Int = try {
            wrapped?.javaClass?.getMethod("getViewTypeCount")?.invoke(wrapped) as? Int ?: 1
        } catch (_: Exception) { 1 }

        /** Calls getItemId(position) on the wrapped factory. */
        private fun getItemIdReflect(position: Int): Long = try {
            wrapped?.javaClass?.getMethod("getItemId", Int::class.java)
                ?.invoke(wrapped, position) as? Long ?: 0L
        } catch (_: Exception) { 0L }

        /** Calls hasStableIds() on the wrapped factory. */
        private fun hasStableIdsReflect(): Boolean = try {
            wrapped?.javaClass?.getMethod("hasStableIds")?.invoke(wrapped) as? Boolean ?: false
        } catch (_: Exception) { false }

        /** Calls isCreated() on the wrapped factory. */
        private fun isCreatedReflect(): Boolean = try {
            wrapped?.javaClass?.getMethod("isCreated")?.invoke(wrapped) as? Boolean ?: false
        } catch (_: Exception) { false }

        /** Calls getRemoteCollectionItems(capSize, capBitmapSize) on the wrapped factory (API 31+). */
        private fun getRemoteCollectionItemsReflect(
            capSize: Int,
            capBitmapSize: Int,
        ): RemoteViews.RemoteCollectionItems? = try {
            wrapped?.javaClass
                ?.getMethod("getRemoteCollectionItems", Int::class.java, Int::class.java)
                ?.invoke(wrapped, capSize, capBitmapSize) as? RemoteViews.RemoteCollectionItems
        } catch (_: Exception) { null }

        @SuppressLint("PrivateApi")
        fun onCreate() {
            if (wrapped != null) {
                val binder = try {
                    wrapped?.javaClass?.getMethod("asBinder")?.invoke(wrapped) as? IBinder
                } catch (_: Exception) { null }
                if (binder?.isBinderAlive == true) return
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appWidgetManager.bindRemoteViewsService(
                        this@RemoteViewsProxyService,
                        widgetId,
                        widgetIntent,
                        getServiceDispatcher(connection, mainHandler, 0),
                        BIND_AUTO_CREATE or BIND_FOREGROUND_SERVICE_WHILE_AWAKE,
                    )
                } else {
                    val proxyClass = Class.forName(
                        "com.android.internal.widget.IRemoteViewsAdapterConnection\$Stub",
                    )
                    val handler = object {
                        @Keep
                        fun onServiceConnected(service: IBinder?) {
                            connection.onServiceConnected(
                                ComponentName(this@RemoteViewsProxyService, Factory::class.java),
                                service,
                            )
                        }

                        @Keep
                        fun onServiceDisconnected() {
                            connection.onServiceDisconnected(
                                ComponentName(this@RemoteViewsProxyService, Factory::class.java),
                            )
                        }
                    }
                    val proxy = ByteBuddy().subclass(proxyClass)
                        .name("com.android.internal.widget.RemoteViewsAdapterConnectionProxy")
                        .method(named("onServiceConnected"))
                        .intercept(MethodDelegation.to(handler))
                        .method(named("onServiceDisconnected"))
                        .intercept(MethodDelegation.to(handler))
                        .make()
                        .load(Factory::class.java.classLoader, AndroidClassLoadingStrategy.Wrapping(cacheDir))
                        .loaded
                        .getDeclaredConstructor()
                        .newInstance() as IInterface

                    AppWidgetManager::class.java
                        .getDeclaredMethod(
                            "bindRemoteViewsService",
                            String::class.java,
                            Int::class.java,
                            Intent::class.java,
                            IBinder::class.java,
                        )
                        .invoke(
                            appWidgetManager,
                            packageName,
                            widgetId,
                            widgetIntent,
                            proxy.asBinder(),
                        )
                }
            } catch (e: Exception) {
                logUtils.debugLog("Error binding widget service $widgetId, $widgetIntent", e)
            }
        }

        /**
         * Implements the IRemoteViewsFactory Binder protocol manually.
         * Transaction codes and Parcel read/write order match AOSP exactly.
         */
        @SuppressLint("NewApi")
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                T_ON_DATA_SET_CHANGED -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    if (!created || isCreatedReflect() == false) callVoid("onDataSetChanged")
                    reply?.writeNoException()
                    return true
                }
                T_ON_DATA_SET_CHANGED_ASYNC -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    callVoid("onDataSetChangedAsync")
                    reply?.writeNoException()
                    return true
                }
                T_ON_DESTROY -> {
                    data.enforceInterface(DESCRIPTOR)
                    val intentArg = data.readParcelable<Intent>(Intent::class.java.classLoader)
                    try { unbindService(connection) } catch (_: IllegalArgumentException) {}
                    sFactories.remove(widgetId)
                    reply?.writeNoException()
                    return true
                }
                T_GET_COUNT -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    val result = getCountReflect()
                    reply?.writeNoException()
                    reply?.writeInt(result)
                    return true
                }
                T_GET_VIEW_AT -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    val position = data.readInt()
                    val result = getViewAtReflect(position)
                    reply?.writeNoException()
                    if (result != null) {
                        reply?.writeInt(1)
                        result.writeToParcel(reply!!, 0)
                    } else {
                        reply?.writeInt(0)
                    }
                    return true
                }
                T_GET_LOADING_VIEW -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    val result = getLoadingViewReflect()
                    reply?.writeNoException()
                    if (result != null) {
                        reply?.writeInt(1)
                        result.writeToParcel(reply!!, 0)
                    } else {
                        reply?.writeInt(0)
                    }
                    return true
                }
                T_GET_VIEW_TYPE_COUNT -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    val result = getViewTypeCountReflect()
                    reply?.writeNoException()
                    reply?.writeInt(result)
                    return true
                }
                T_GET_ITEM_ID -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    val position = data.readInt()
                    val result = getItemIdReflect(position)
                    reply?.writeNoException()
                    reply?.writeLong(result)
                    return true
                }
                T_HAS_STABLE_IDS -> {
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    val result = hasStableIdsReflect()
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                T_IS_CREATED -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = created
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                T_GET_REMOTE_COLLECTION_ITEMS -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
                    data.enforceInterface(DESCRIPTOR)
                    onCreate()
                    val capSize = data.readInt()
                    val capBitmapSize = data.readInt()
                    val result = getRemoteCollectionItemsReflect(capSize, capBitmapSize)
                    reply?.writeNoException()
                    if (result != null) {
                        reply?.writeInt(1)
                        result.writeToParcel(reply!!, 0)
                    } else {
                        reply?.writeInt(0)
                    }
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }

        override fun getInterfaceDescriptor(): String = DESCRIPTOR
    }

    override fun onDestroy() {
        sFactories.forEach { (_, factory) ->
            try { unbindService(factory.connection) } catch (_: IllegalArgumentException) {}
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INTENT = "intent"

        private val sFactories = hashMapOf<Int, Factory>()

        fun createProxyIntent(context: Context, widgetId: Int, widgetIntent: Intent?): Intent {
            val intent = Intent(context, RemoteViewsProxyService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            intent.putExtra(EXTRA_INTENT, widgetIntent)
            intent.data = "widgetproxy://${widgetId}".toUri()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                intent.collectExtraIntentKeys()
            }

            return intent
        }
    }
}
