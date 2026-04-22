package com.musheer360.swiftslate.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.ui.components.ExtraLargeShape
import com.musheer360.swiftslate.ui.components.IconBox
import com.musheer360.swiftslate.ui.components.InputShape
import com.musheer360.swiftslate.ui.components.slateCardColors
import com.musheer360.swiftslate.ui.components.slateTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val prefs   = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var providerType     by remember { mutableStateOf(prefs.getString("provider_type", "gemini") ?: "gemini") }
    var providerExpanded by remember { mutableStateOf(false) }

    var selectedModel by remember { mutableStateOf(prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite") }
    var modelExpanded by remember { mutableStateOf(false) }
    val geminiModels  = listOf("gemini-2.5-flash-lite", "gemini-3-flash-preview", "gemini-3.1-flash-lite-preview")

    var customEndpoint by remember { mutableStateOf(prefs.getString("custom_endpoint", "") ?: "") }
    var customModel    by remember { mutableStateOf(prefs.getString("custom_model", "") ?: "") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.Bold)
                        Text("Configure AI provider & model",
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
            // ── Provider ─────────────────────────────────────────────────────
            item {
                Text("Provider", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = ExtraLargeShape,
                    colors = slateCardColors(), elevation = CardDefaults.cardElevation(0.dp)) {
                    ListItem(
                        headlineContent   = { Text("AI Provider", fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(if (providerType == "gemini") "Google Gemini" else "Custom (OpenAI Compatible)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        leadingContent = {
                            IconBox(color = Color(0xFF009688)) {
                                Icon(Icons.Filled.Cloud, null, tint = Color(0xFF009688), modifier = Modifier.size(22.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), thickness = 0.5.dp)
                    Column(modifier = Modifier.padding(16.dp)) {
                        ExposedDropdownMenuBox(
                            expanded         = providerExpanded,
                            onExpandedChange = { providerExpanded = !providerExpanded },
                        ) {
                            OutlinedTextField(
                                value         = if (providerType == "gemini") "Google Gemini" else "Custom (OpenAI Compatible)",
                                onValueChange = {},
                                readOnly      = true,
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                                modifier      = Modifier.menuAnchor().fillMaxWidth(),
                                colors        = slateTextFieldColors(),
                                shape         = InputShape,
                            )
                            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                                DropdownMenuItem(text = { Text("Google Gemini") }, onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    providerType = "gemini"; prefs.edit().putString("provider_type", "gemini").apply(); providerExpanded = false
                                })
                                DropdownMenuItem(text = { Text("Custom (OpenAI Compatible)") }, onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    providerType = "custom"; prefs.edit().putString("provider_type", "custom").apply(); providerExpanded = false
                                })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Gemini model or custom fields ─────────────────────────────────
            item {
                Text("Model", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = ExtraLargeShape,
                    colors = slateCardColors(), elevation = CardDefaults.cardElevation(0.dp)) {
                    ListItem(
                        headlineContent   = { Text(if (providerType == "gemini") "Gemini Model" else "Custom Model", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(if (providerType == "gemini") selectedModel else customModel.ifBlank { "Not set" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = {
                            IconBox(color = Color(0xFF6200EE)) {
                                Icon(Icons.Filled.SmartToy, null, tint = Color(0xFF6200EE), modifier = Modifier.size(22.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), thickness = 0.5.dp)
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (providerType == "gemini") {
                            ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = !modelExpanded }) {
                                OutlinedTextField(
                                    value = selectedModel, onValueChange = {}, readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    colors = slateTextFieldColors(), shape = InputShape,
                                )
                                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                    geminiModels.forEach { model ->
                                        DropdownMenuItem(text = { Text(model) }, onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedModel = model; prefs.edit().putString("model", model).apply(); modelExpanded = false
                                        })
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(value = customEndpoint,
                                onValueChange = { customEndpoint = it; prefs.edit().putString("custom_endpoint", it).apply() },
                                label = { Text("Endpoint") }, placeholder = { Text("https://api.example.com/v1") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                colors = slateTextFieldColors(), shape = InputShape)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = customModel,
                                onValueChange = { customModel = it; prefs.edit().putString("custom_model", it).apply() },
                                label = { Text("Model") }, placeholder = { Text("gpt-4o, claude-3-haiku, etc.") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                colors = slateTextFieldColors(), shape = InputShape)
                        }
                    }
                }
            }
        }
    }
}
