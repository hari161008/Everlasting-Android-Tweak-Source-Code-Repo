package com.coolappstore.everlastingandroidtweak.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController

private const val FORM_URL =
    "https://docs.google.com/forms/d/e/1FAIpQLSfEikvyYSioKvGXQv74JmdNPHVgNGw-1SiNXcJUgGqgzBVImA/viewform?usp=header"

@Composable
fun RatingScreen(navController: NavController) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FORM_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        navController.popBackStack()
    }
}
