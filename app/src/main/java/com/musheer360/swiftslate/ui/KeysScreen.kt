package com.musheer360.swiftslate.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.api.GeminiClient
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.ui.components.ExtraLargeShape
import com.musheer360.swiftslate.ui.components.IconBox
import com.musheer360.swiftslate.ui.components.InputShape
import com.musheer360.swiftslate.ui.components.slateCardColors
import com.musheer360.swiftslate.ui.components.slateTextFieldColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen() {
    val context        = LocalContext.current
    val haptic         = LocalHapticFeedback.current
    val keyManager     = remember { KeyManager(context) }
    var keys           by remember { mutableStateOf(keyManager.getKeys()) }
    var newKey         by remember { mutableStateOf("") }
    var isTesting      by remember { mutableStateOf(false) }
    var testResult     by remember { mutableStateOf<String?>(null) }
    val scope          = rememberCoroutineScope()
    val geminiClient   = remember { GeminiClient() }
    val openAIClient   = remember { OpenAICompatibleClient() }
    val prefs          = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val providerType   = remember { prefs.getString("provider_type", "gemini") ?: "gemini" }
    val customEndpoint = remember { prefs.getString("custom_endpoint", "") ?: "" }

    val isResultSuccess = testResult?.startsWith("Valid") == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("API Keys", fontWeight = FontWeight.Bold)
                        Text("Manage authentication keys",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start  = 16.dp,
                end    = 16.dp,
            ),
        ) {
            // ── Add key card ─────────────────────────────────────────────────
            item {
                Text("Add Key", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = ExtraLargeShape,
                    colors = slateCardColors(), elevation = CardDefaults.cardElevation(0.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value         = newKey,
                            onValueChange = { newKey = it; testResult = null },
                            label         = { Text("API Key") },
                            placeholder   = { Text("Paste your key here…") },
                            singleLine    = true,
                            leadingIcon   = {
                                Icon(Icons.Filled.Key, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = slateTextFieldColors(),
                            shape    = InputShape,
                        )
                        AnimatedVisibility(visible = isTesting, enter = fadeIn(), exit = fadeOut()) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(4.dp))
                                Text("Validating key…", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        AnimatedVisibility(visible = testResult != null && !isTesting, enter = fadeIn(), exit = fadeOut()) {
                            testResult?.let { msg ->
                                Spacer(Modifier.height(8.dp))
                                Text(msg, style = MaterialTheme.typography.bodySmall,
                                    color = if (isResultSuccess) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (newKey.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isTesting = true; testResult = null
                                    scope.launch {
                                        val trimmedKey = newKey.trim()
                                        if (keys.contains(trimmedKey)) {
                                            isTesting = false; testResult = "This key has already been added"; return@launch
                                        }
                                        val result = if (providerType == "custom" && customEndpoint.isNotBlank())
                                            openAIClient.validateKey(trimmedKey, customEndpoint)
                                        else geminiClient.validateKey(trimmedKey)
                                        isTesting = false
                                        if (result.isSuccess) {
                                            keyManager.addKey(trimmedKey); keys = keyManager.getKeys()
                                            newKey = ""; testResult = "Valid key added!"
                                        } else { testResult = result.exceptionOrNull()?.message ?: "Validation failed" }
                                    }
                                }
                            },
                            enabled  = newKey.isNotBlank() && !isTesting,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Text(if (isTesting) "Testing…" else "Add & Validate Key", fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Saved keys ───────────────────────────────────────────────────
            if (keys.isNotEmpty()) {
                item {
                    Text("Saved Keys", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = ExtraLargeShape,
                        colors = slateCardColors(), elevation = CardDefaults.cardElevation(0.dp)) {
                        keys.forEachIndexed { idx, key ->
                            ListItem(
                                headlineContent = {
                                    Text("••••••••${key.takeLast(6)}", fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace)
                                },
                                supportingContent = { Text("API key", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = {
                                    IconBox(color = Color(0xFF1976D2)) {
                                        Icon(Icons.Filled.Key, null, tint = Color(0xFF1976D2), modifier = Modifier.size(22.dp))
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        keyManager.removeKey(key); keys = keyManager.getKeys()
                                    }) {
                                        Icon(Icons.Outlined.Delete, "Delete Key", tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                            if (idx < keys.lastIndex) HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 56.dp),
                                color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
