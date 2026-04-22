plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.atomicfu)
}

android {
    namespace = "com.coolappstore.everlastingandroidtweak"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.coolappstore.everlastingandroidtweak"
        minSdk = 31
        targetSdk = 34
        versionCode = 9
        versionName = "7.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Required by LockscreenWidgets' MigrationManager
        buildConfigField("Integer", "DATABASE_VERSION", project.properties["databaseVersion"]?.toString() ?: "9")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                // Required by LSW's ViewAdapter.kt (context(Context) syntax).
                // -Xcontext-parameters is the Kotlin 2.2+ name for context receivers.
                "-Xcontext-parameters",
            )
        }
    }

    buildFeatures {
        compose   = true
        buildConfig = true
        viewBinding = true
        aidl    = true      // Required for LSW's IShizukuService.aidl
    }

    packaging {
        resources.excludes.add("META-INF/library_release.kotlin_module")
    }
}

dependencies {
    // ── Core Everlasting ───────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.kotlinx.coroutines.android)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.palette:palette-ktx:1.0.0")
    debugImplementation(libs.androidx.ui.tooling)

    // ── Hail App Freezer ───────────────────────────────────────────────────────
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.9")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.9")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.belerweb:pinyin4j:2.5.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("dev.chrisbanes.insetter:insetter:0.6.1")
    implementation("io.github.iamr0s:Dhizuku-API:2.5.4")
    implementation("me.zhanghai.android.appiconloader:appiconloader:1.5.0")
    implementation("me.zhanghai.compose.preference:library:1.1.1")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // ── LockscreenWidgets ──────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:atomicfu:0.23.2")

    implementation("androidx.core:core-remoteviews:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.getkeepsafe.relinker:relinker:1.4.5")
    implementation("net.bytebuddy:byte-buddy-android:1.14.12")
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    implementation("com.anggrayudi:storage:2.2.0")
    implementation("com.ehsanmsz:msz-progress-indicator:0.8.0")

    // JitPack LSW libraries
    implementation("dev.zwander:composeintroslider:1.0.0")
    implementation("dev.zwander:patreonsupportersretrieval:1.0.0")
    implementation("dev.zwander:spannedgridlayoutmanager:1.0.0")
    implementation("com.github.skydoves:colorpicker-compose:1.1.0")
    implementation("sh.calvin.reorderable:reorderable:2.1.0")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.34.0")

    // Bugsnag – included so MeasuredComposable compiles; disabled at runtime
    implementation("com.bugsnag:bugsnag-android:6.8.0")
    implementation("com.bugsnag:bugsnag-plugin-android-exitinfo:6.8.0")
    implementation("com.bugsnag:bugsnag-android-performance:2.2.0")
    implementation("com.bugsnag:bugsnag-android-performance-appcompat:2.2.0")
    implementation("com.bugsnag:bugsnag-android-performance-compose:2.2.0")

    // Desugaring (Java 8+ API back-compat)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
