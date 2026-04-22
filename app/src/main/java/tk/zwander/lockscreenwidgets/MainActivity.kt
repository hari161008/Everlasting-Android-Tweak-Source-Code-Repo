package tk.zwander.lockscreenwidgets

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.bugsnag.android.performance.compose.MeasuredComposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.main.MainContent
import tk.zwander.common.tiles.widget.WidgetTileFive
import tk.zwander.common.tiles.widget.WidgetTileFour
import tk.zwander.common.tiles.widget.WidgetTileOne
import tk.zwander.common.tiles.widget.WidgetTileThree
import tk.zwander.common.tiles.widget.WidgetTileTwo
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.isOneUI
import tk.zwander.common.util.prefManager
import com.coolappstore.everlastingandroidtweak.BuildConfig
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.ui.theme.EverlastingTheme

/**
 * Hosts the main Lock Screen Widgets feature screen.
 *
 * Theme is now provided by [EverlastingTheme] so the screen automatically
 * respects all Appearance settings (dark/light mode, custom primary colour,
 * UI blur, etc.) configured in the Everlasting settings.
 */
class MainActivity : BaseActivity() {
    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable / disable One UI QS tiles depending on device.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            lifecycleScope.launch(Dispatchers.IO) {
                val components = arrayOf(
                    WidgetTileOne::class.java,
                    WidgetTileTwo::class.java,
                    WidgetTileThree::class.java,
                    WidgetTileFour::class.java,
                    WidgetTileFive::class.java,
                )
                components.forEach {
                    packageManager.setComponentEnabledSetting(
                        ComponentName(this@MainActivity, it),
                        if (isOneUI) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP or PackageManager.SYNCHRONOUS,
                    )
                }
            }
        }

        // Use EverlastingTheme so the LSW main screen syncs with the user's
        // Appearance settings (theme mode, dynamic colour, custom primary, blur).
        setContent {
            val dynamicColor  by AppPreferences.get(AppPreferences.DYNAMIC_COLOR, true).collectAsState(true)
            val themeMode     by AppPreferences.get(AppPreferences.DARK_THEME, 0).collectAsState(0)
            val blurEnabled   by AppPreferences.get(AppPreferences.UI_BLUR_ENABLED, false).collectAsState(false)
            val blurAmount    by AppPreferences.get(AppPreferences.UI_BLUR_AMOUNT, 16f).collectAsState(16f)
            val customPrimary by AppPreferences.get(AppPreferences.CUSTOM_PRIMARY_COLOR, "").collectAsState("")
            val systemDark    = isSystemInDarkTheme()

            // Mirror the same isDark logic used in the Everlasting MainActivity.
            val isDark = when (themeMode) {
                1    -> false        // Light
                2, 3 -> true         // Dark / Black
                4    -> false        // White
                else -> systemDark   // System / Auto
            }

            EverlastingTheme(
                darkTheme          = isDark,
                dynamicColor       = dynamicColor,
                themeMode          = themeMode,
                blurEnabled        = blurEnabled,
                blurAmount         = blurAmount,
                wallpaperActive    = false,
                customPrimaryColor = customPrimary,
            ) {
                MeasuredComposable(name = "MainContentLayout") {
                    MainContent()
                }
            }
        }

        // Mark firstRun false so LSW skips its own intro animation.
        if (prefManager.firstRun) {
            prefManager.firstRun = false
        }
    }

    override fun onStop() {
        super.onStop()
        eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.HIDE))
    }
}
