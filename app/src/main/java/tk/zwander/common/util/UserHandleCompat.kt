package tk.zwander.common.util

import android.annotation.SuppressLint
import android.os.UserHandle

object UserHandleCompat {
    @get:SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    val SYSTEM: UserHandle
        get() = try {
            UserHandle::class.java.getDeclaredField("SYSTEM")
                .also { it.isAccessible = true }.get(null) as UserHandle
        } catch (_: Exception) {
            try {
                // Construct UserHandle for user 0 (system user) as fallback
                UserHandle::class.java.getDeclaredConstructor(Int::class.java)
                    .also { it.isAccessible = true }.newInstance(0) as UserHandle
            } catch (_: Exception) {
                ownerFallback()
            }
        }

    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    private fun ownerFallback(): UserHandle = try {
        UserHandle::class.java.getDeclaredField("OWNER")
            .also { it.isAccessible = true }.get(null) as UserHandle
    } catch (_: Exception) {
        UserHandle::class.java.getDeclaredConstructor(Int::class.java)
            .also { it.isAccessible = true }.newInstance(0) as UserHandle
    }
}
