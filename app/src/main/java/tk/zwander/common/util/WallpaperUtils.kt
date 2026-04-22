package tk.zwander.common.util

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable

val Context.wallpaperUtils: WallpaperUtils
    get() = WallpaperUtils.getInstance(this)

class WallpaperUtils private constructor(private val context: Context) {
    @SuppressLint("PrivateApi")
    private fun Context.userIdCompat(): Int = try {
        javaClass.getMethod("getUserId").invoke(this) as Int
    } catch (_: Exception) { 0 }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: WallpaperUtils? = null

        @Synchronized
        fun getInstance(context: Context): WallpaperUtils {
            return instance ?: WallpaperUtils(context.safeApplicationContext).apply {
                instance = this
            }
        }

        @SuppressLint("PrivateApi")
        private fun getServiceManagerBinder(name: String): IBinder? = try {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, name) as? IBinder
        } catch (_: Exception) { null }
    }

    /** Raw IWallpaperManager proxy object, obtained via reflection. */
    private val iWallpaper: Any?
        @SuppressLint("PrivateApi")
        get() = try {
            val binder = getServiceManagerBinder(Context.WALLPAPER_SERVICE) ?: return null
            val stubClass = Class.forName("android.app.IWallpaperManager\$Stub")
            stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (_: Exception) { null }

    private val wallpaper = context.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager

    /**
     * Dynamic proxy implementing IWallpaperManagerCallback.Stub via reflection.
     * We extend Binder directly and register ourselves using reflection so we
     * don't need a compile-time dependency on the hidden interface.
     */
    private val callback: Any?
        @SuppressLint("PrivateApi")
        get() = try {
            val stubClass = Class.forName("android.app.IWallpaperManagerCallback\$Stub")
            val proxy = object : android.os.Binder() {
                override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                    return try {
                        data.enforceInterface("android.app.IWallpaperManagerCallback")
                        when (code) {
                            IBinder.FIRST_CALL_TRANSACTION -> { // onWallpaperChanged
                                context.logUtils.debugLog("Wallpaper changed, clearing cache.", null)
                                cachedWallpaper = null
                                reply?.writeNoException()
                                true
                            }
                            IBinder.FIRST_CALL_TRANSACTION + 1 -> { // onWallpaperColorsChanged
                                reply?.writeNoException()
                                true
                            }
                            else -> super.onTransact(code, data, reply, flags)
                        }
                    } catch (_: Exception) { false }
                }

                override fun getInterfaceDescriptor(): String =
                    "android.app.IWallpaperManagerCallback"
            }
            proxy
        } catch (_: Exception) { null }

    private val handler = Handler(Looper.getMainLooper())
    private var cachedWallpaper: Bitmap? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            wallpaper.addOnColorsChangedListener({ _, _ -> cachedWallpaper = null }, handler)
        }
    }

    val wallpaperDrawable: Drawable?
        @SuppressLint("MissingPermission")
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                peekWallpaperBitmap()?.toDrawable(context.resources) ?: wallpaper.drawable
            } else {
                wallpaper.drawable
            }
        }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun peekWallpaperBitmap(): Bitmap? {
        return if (cachedWallpaper != null && cachedWallpaper?.isRecycled == false) {
            context.logUtils.debugLog("Using cached wallpaper.")
            cachedWallpaper
        } else {
            context.logUtils.debugLog("Retrieving new wallpaper; isRecycled: ${cachedWallpaper?.isRecycled}.")
            val lockWallpaper = getWallpaper(WallpaperManager.FLAG_LOCK)
            val systemWallpaper = getWallpaper(WallpaperManager.FLAG_SYSTEM)
            val desc = lockWallpaper ?: systemWallpaper

            try {
                desc?.let { pfd ->
                    pfd.fileDescriptor?.let { fd ->
                        BitmapFactory.decodeFileDescriptor(fd)?.also { bmp ->
                            context.logUtils.debugLog("Caching new wallpaper $bmp.", null)
                            cachedWallpaper = bmp
                        }
                    }
                }
            } finally {
                lockWallpaper?.close()
                systemWallpaper?.close()
            }
        }
    }

    @SuppressLint("PrivateApi")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun getWallpaper(flag: Int): ParcelFileDescriptor? {
        val bundle = Bundle()
        val iw = iWallpaper ?: return null
        val cb = callback

        fun old(): ParcelFileDescriptor? = try {
            iw.javaClass.getMethod(
                "getWallpaper",
                String::class.java,
                Class.forName("android.app.IWallpaperManagerCallback"),
                Int::class.java,
                Bundle::class.java,
                Int::class.java,
            ).invoke(iw, context.packageName, cb, flag, bundle, context.userIdCompat()) as? ParcelFileDescriptor
        } catch (_: Exception) { null }

        @RequiresApi(Build.VERSION_CODES.R)
        fun withFeature(): ParcelFileDescriptor? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                iw.javaClass.getMethod(
                    "getWallpaperWithFeature",
                    String::class.java, String::class.java,
                    Class.forName("android.app.IWallpaperManagerCallback"),
                    Int::class.java, Bundle::class.java, Int::class.java, Boolean::class.java,
                ).invoke(
                    iw, context.packageName,
                    @SuppressLint("NewApi") context.attributionTag,
                    cb, flag, bundle, context.userIdCompat(), true,
                ) as? ParcelFileDescriptor
            } else {
                iw.javaClass.getMethod(
                    "getWallpaperWithFeature",
                    String::class.java, String::class.java,
                    Class.forName("android.app.IWallpaperManagerCallback"),
                    Int::class.java, Bundle::class.java, Int::class.java,
                ).invoke(
                    iw, context.packageName,
                    @SuppressLint("NewApi") context.attributionTag,
                    cb, flag, bundle, context.userIdCompat(),
                ) as? ParcelFileDescriptor
            }
        } catch (e: NoSuchMethodError) {
            context.logUtils.debugLog("Missing getWallpaperWithFeature, using getWallpaper instead.", e)
            old()
        } catch (_: Exception) { null }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                withFeature() ?: old()
            } else {
                old()
            }
        } catch (e: Exception) {
            context.logUtils.normalLog("Error retrieving wallpaper", e)
            null
        } catch (e: NoSuchMethodError) {
            context.logUtils.normalLog("Error retrieving wallpaper", e)
            null
        }
    }
}
