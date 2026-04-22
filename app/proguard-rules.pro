# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontobfuscate

-keep class tk.zwander.lockscreenwidgets.** { *; }
-keep interface tk.zwander.lockscreenwidgets.** { *; }

-keep class net.bytebuddy.** { *; }
-keep interface net.bytebuddy.** { *; }

-keep class com.android.dx.** { *; }
-keep interface com.android.dx.** { *; }

# ── Kotlin metadata — prevents R8 from stripping @Metadata annotations ────────
# R8 needs to read Kotlin metadata to correctly shrink/optimize Kotlin code.
# Without this rule, R8 may emit "error parsing kotlin metadata" warnings and
# produce incorrect optimised output for Kotlin-specific constructs.
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}

# Keep Kotlin coroutines & flow internal machinery intact.
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# Keep Compose runtime internals so R8 doesn't accidentally strip
# generated composable lambdas or remember-slot infrastructure.
-keep class androidx.compose.runtime.** { *; }

# Keep all common/compose LSW internals (supplement the existing keep rules).
-keep class tk.zwander.common.** { *; }
-keep interface tk.zwander.common.** { *; }
-keep class tk.zwander.widgetdrawer.** { *; }
-keep interface tk.zwander.widgetdrawer.** { *; }
-keep class com.coolappstore.everlastingandroidtweak.** { *; }
-keep class com.aistra.hail.** { *; }

# ByteBuddy / JNA / FindBugs are desktop-only libs pulled in transitively
# by Bugsnag performance — they don't exist on Android, safe to suppress.
-dontwarn net.bytebuddy.**
-dontwarn com.sun.jna.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn java.lang.instrument.**