package com.coolappstore.everlastingandroidtweak.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar

const val REVIEWS_URL =
    "https://hari161008.github.io/Website-For-Everlasting-Android-Tweak/Ratings%20Reviews/Ratings%20and%20Reviews%20v3.html"

@Composable
fun ReviewsWebViewScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            EverlastingTopBar(
                title = "Check Rate and Reviews",
                navController = navController,
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REVIEWS_URL))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in browser"
                        )
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(REVIEWS_URL)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
