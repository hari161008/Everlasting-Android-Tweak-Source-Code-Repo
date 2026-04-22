package com.musheer360.swiftslate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.ui.theme.EverlastingTheme
import com.musheer360.swiftslate.ui.CommandsScreen
import com.musheer360.swiftslate.ui.DashboardScreen
import com.musheer360.swiftslate.ui.KeysScreen
import com.musheer360.swiftslate.ui.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── Read theme prefs synchronously so the very first frame uses the
        //    correct theme — eliminates the dark-flash on open.
        //    runBlocking here is intentional and safe: DataStore's in-memory
        //    cache makes this instant on any frame after the first pref access.
        val initialThemeMode    = runBlocking { AppPreferences.get(AppPreferences.DARK_THEME,          0   ).first() }
        val initialDynamicColor = runBlocking { AppPreferences.get(AppPreferences.DYNAMIC_COLOR,       true).first() }
        val initialCustomColor  = runBlocking { AppPreferences.get(AppPreferences.CUSTOM_PRIMARY_COLOR, "" ).first() }

        setContent {
            // Collect live updates — initial values prevent any first-frame flash
            val themeMode     by AppPreferences.get(AppPreferences.DARK_THEME,          0   ).collectAsState(initialThemeMode)
            val dynamicColor  by AppPreferences.get(AppPreferences.DYNAMIC_COLOR,       true).collectAsState(initialDynamicColor)
            val customPrimary by AppPreferences.get(AppPreferences.CUSTOM_PRIMARY_COLOR,  "" ).collectAsState(initialCustomColor)

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                1    -> false       // force light
                2    -> true        // force dark
                3    -> true        // pure black
                4    -> false       // pure white
                else -> systemDark  // follow system
            }

            EverlastingTheme(
                darkTheme          = isDark,
                dynamicColor       = dynamicColor,
                themeMode          = themeMode,
                customPrimaryColor = customPrimary,
            ) {
                SwiftSlateMainScreen()
            }
        }
    }
}

sealed class Screen(
    val route        : String,
    val title        : String,
    val iconSelected : ImageVector,
    val iconDefault  : ImageVector,
) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home,              Icons.Outlined.Home)
    object Keys      : Screen("keys",      "Keys",      Icons.Filled.Key,               Icons.Outlined.Key)
    object Commands  : Screen("commands",  "Commands",  Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List)
    object Settings  : Screen("settings",  "Settings",  Icons.Filled.Settings,          Icons.Outlined.Settings)
}

@Composable
fun SwiftSlateMainScreen() {
    val navController = rememberNavController()
    val items  = listOf(Screen.Dashboard, Screen.Keys, Screen.Commands, Screen.Settings)
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        icon     = { Icon(if (selected) screen.iconSelected else screen.iconDefault, screen.title) },
                        label    = { Text(screen.title) },
                        selected = selected,
                        onClick  = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor   = MaterialTheme.colorScheme.primary,
                            indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Dashboard.route,
            modifier         = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Keys.route)      { KeysScreen()      }
            composable(Screen.Commands.route)  { CommandsScreen()  }
            composable(Screen.Settings.route)  { SettingsScreen()  }
        }
    }
}
