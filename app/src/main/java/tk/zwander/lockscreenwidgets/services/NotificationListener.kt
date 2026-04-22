package tk.zwander.lockscreenwidgets.services

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * The actual notification listener is [com.aistra.hail.services.AutoFreezeService].
 * This file only exists to keep the extension property [isNotificationListenerActive]
 * accessible from its existing import sites (IntroSlides, ComposeFrameSettingsActivity).
 *
 * We check for AutoFreezeService by package name so that granting notification access
 * once covers everything — there is now a single listener entry in Settings.
 */
val Context.isNotificationListenerActive: Boolean
    get() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?.contains(packageName) == true
