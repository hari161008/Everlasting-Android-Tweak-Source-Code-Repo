package com.coolappstore.everlastingandroidtweak.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolappstore.everlastingandroidtweak.features.watermark.WatermarkViewModel
import com.coolappstore.everlastingandroidtweak.ui.screens.EssentialsWatermarkScreen
import com.coolappstore.everlastingandroidtweak.ui.theme.EverlastingTheme

class WatermarkActivity : ComponentActivity() {

    private var initialUri by mutableStateOf<Uri?>(null)

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                try {
                    val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flag)
                } catch (e: Exception) {
                    // Ignore if not persistable
                }
                initialUri = uri
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // Handle Share Intent
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let {
                initialUri = it
            }
        }

        setContent {
            EverlastingTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    val context = LocalContext.current
                    val viewModel: WatermarkViewModel = viewModel(
                        factory = WatermarkViewModel.provideFactory(context)
                    )
                    EssentialsWatermarkScreen(
                        initialUri = initialUri,
                        onPickImage = {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onBack = { finish() },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
