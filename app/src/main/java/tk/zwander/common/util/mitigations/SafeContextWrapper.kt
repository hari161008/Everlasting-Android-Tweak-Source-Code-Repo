package tk.zwander.common.util.mitigations

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.UserHandle
import tk.zwander.common.util.logUtils

open class SafeContextWrapper(context: Context) : ContextWrapper(context) {
    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        try {
            super.unregisterReceiver(receiver)
        } catch (e: Exception) {
            logUtils.debugLog("Unable to unregister receiver.", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    fun registerReceiverAsUser(
        receiver: BroadcastReceiver?,
        user: UserHandle?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
    ): Intent? {
        return try {
            baseContext.javaClass.getMethod(
                "registerReceiverAsUser",
                BroadcastReceiver::class.java, UserHandle::class.java,
                IntentFilter::class.java, String::class.java, Handler::class.java,
            ).invoke(baseContext, receiver, user, filter, broadcastPermission, scheduler) as? Intent
        } catch (e: Throwable) {
            logUtils.normalLog("Unable to register receiver.", e)
            null
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    fun registerReceiverAsUser(
        receiver: BroadcastReceiver?,
        user: UserHandle?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int,
    ): Intent? {
        return try {
            baseContext.javaClass.getMethod(
                "registerReceiverAsUser",
                BroadcastReceiver::class.java, UserHandle::class.java,
                IntentFilter::class.java, String::class.java, Handler::class.java, Int::class.java,
            ).invoke(baseContext, receiver, user, filter, broadcastPermission, scheduler, flags) as? Intent
        } catch (e: Throwable) {
            logUtils.normalLog("Unable to register receiver.", e)
            null
        }
    }
}