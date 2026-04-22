package com.aistra.hail

import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailData
import com.aistra.hail.services.AutoFreezeService
import com.aistra.hail.utils.HDhizuku
import com.aistra.hail.utils.HTarget
import tk.zwander.lockscreenwidgets.App

// Extends LSW's App so that App.instance is correctly set for all LSW components.
open class HailApp : App() {
    override fun onCreate() {
        super.onCreate()        // LSW App initialises hidden API bypass, widget host, etc.
        hailApp = this
        if (!HTarget.S) setAppTheme(HailData.appTheme)
        if (HailData.workingMode.startsWith(HailData.DHIZUKU)) HDhizuku.init()
    }

    fun setAutoFreezeService(
        autoFreezeAfterLock: Boolean = HailData.autoFreezeAfterLock,
        context: Context = hailApp
    ) {
        val start = autoFreezeAfterLock && HailData.checkedList.any {
            it.packageName != packageName &&
                    it.applicationInfo != null &&
                    !AppManager.isAppFrozen(it.packageName) &&
                    !it.whitelisted
        }
        val intent = Intent(hailApp, AutoFreezeService::class.java)
        if (start) {
            setAutoFreezeServiceEnabled(true)
            ContextCompat.startForegroundService(context, intent)
        } else {
            stopService(intent)
            setAutoFreezeServiceEnabled(false)
        }
    }

    fun setAutoFreezeServiceEnabled(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(hailApp, AutoFreezeService::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun setAppTheme(theme: String) {
        if (HTarget.S) getSystemService<UiModeManager>()!!.setApplicationNightMode(
            when (theme) {
                HailData.THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                HailData.THEME_DARK  -> UiModeManager.MODE_NIGHT_YES
                else                 -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
        else AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                HailData.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                HailData.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
                else                 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    companion object {
        lateinit var hailApp: HailApp private set
        // Backward-compat alias used by Hail code: `HailApp.Companion.app`
        val app: HailApp get() = hailApp
    }
}
