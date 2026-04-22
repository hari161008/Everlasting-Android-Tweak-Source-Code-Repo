package tk.zwander.common.util.shizuku

import android.os.Build
import android.os.UserHandle
import com.coolappstore.everlastingandroidtweak.BuildConfig
import tk.zwander.lockscreenwidgets.IShizukuService
import kotlin.system.exitProcess

class ShizukuService : IShizukuService.Stub() {
    override fun grantReadExternalStorage() {
        @Suppress("UNCHECKED_CAST")
        val ipm: Any = run {
            val binder = try {
                Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "package") as? android.os.IBinder
            } catch (_: Exception) { null } ?: return
            try {
                Class.forName("android.content.pm.IPackageManager\$Stub")
                    .getMethod("asInterface", android.os.IBinder::class.java)
                    .invoke(null, binder) ?: return
            } catch (_: Exception) { return }
        }
        fun callGrantRuntime(pkg: String, perm: String, userId: Int) {
            try {
                ipm.javaClass.getMethod(
                    "grantRuntimePermission",
                    String::class.java, String::class.java, Int::class.java,
                ).invoke(ipm, pkg, perm, userId)
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            callGrantRuntime(
                BuildConfig.APPLICATION_ID,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                0,
            )
        }

        callGrantRuntime(
            BuildConfig.APPLICATION_ID,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            0,
        )
    }

    override fun destroy() {
        exitProcess(0)
    }
}
