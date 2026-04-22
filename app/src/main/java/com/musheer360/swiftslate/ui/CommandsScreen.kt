package com.musheer360.swiftslate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.ui.components.ExtraLargeShape
import com.musheer360.swiftslate.ui.components.IconBox
import com.musheer360.swiftslate.ui.components.InputShape
import com.musheer360.swiftslate.ui.components.slateCardColors
import com.musheer360.swiftslate.ui.components.slateTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen() {
    val context        = LocalContext.current
    val haptic         = LocalHapticFeedback.current
    val commandManager = remember { CommandManager(context) }
    var commands       by remember { mutableStateOf(commandManager.getCommands()) }
    var trigger        by remember { mutableStateOf("") }
    var prompt         by remember { mutableStateOf("") }
    var errorMessage   by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Commands", fontWeight = FontWeight.Bold)
                        Text("Custom AI triggers",
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
            // ── Add command card ─────────────────────────────────────────────
            item {
                Text("Add Command", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = ExtraLargeShape,
                    colors = slateCardColors(), elevation = CardDefaults.cardElevation(0.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value         = trigger,
                            onValueChange = { trigger = it; errorMessage = null },
                            label         = { Text("Trigger") },
                            placeholder   = { Text("e.g. ?code") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            colors        = slateTextFieldColors(),
                            shape         = InputShape,
                        )
                        Text("Must start with '?'", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp))
                        OutlinedTextField(
                            value         = prompt,
                            onValueChange = { prompt = it },
                            label         = { Text("Prompt") },
                            placeholder   = { Text("Describe what to do with the text…") },
                            modifier      = Modifier.fillMaxWidth().height(108.dp),
                            colors        = slateTextFieldColors(),
                            shape         = InputShape,
                        )
                        Text("Ask the AI for JUST the modified text (no extras)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                        errorMessage?.let { msg ->
                            Spacer(Modifier.height(8.dp))
                            Text("⚠ $msg", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val trimmedTrigger = trigger.trim()
                                if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                                    if (!trimmedTrigger.startsWith("?")) { errorMessage = "Trigger must start with '?'"; return@Button }
                                    if (commands.any { it.trigger == trimmedTrigger }) { errorMessage = "A command with this trigger already exists"; return@Button }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    commandManager.addCustomCommand(Command(trimmedTrigger, prompt.trim(), false))
                                    commands = commandManager.getCommands(); trigger = ""; prompt = ""; errorMessage = null
                                }
                            },
                            enabled  = trigger.isNotBlank() && prompt.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Command", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Command list ─────────────────────────────────────────────────
            if (commands.isNotEmpty()) {
                item {
                    Text("All Commands", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = ExtraLargeShape,
                        colors = slateCardColors(), elevation = CardDefaults.cardElevation(0.dp)) {
                        commands.forEachIndexed { idx, cmd ->
                            ListItem(
                                headlineContent = {
                                    Text(cmd.trigger, fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary)
                                },
                                supportingContent = {
                                    Text(cmd.prompt, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                },
                                leadingContent = {
                                    IconBox(color = Color(0xFF7C4DFF)) {
                                        Icon(Icons.Filled.Terminal, null,
                                            tint = Color(0xFF7C4DFF), modifier = Modifier.size(22.dp))
                                    }
                                },
                                trailingContent = {
                                    if (cmd.isBuiltIn) {
                                        Text("Built-in", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary)
                                    } else {
                                        IconButton(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            commandManager.removeCustomCommand(cmd.trigger)
                                            commands = commandManager.getCommands()
                                        }) {
                                            Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                            if (idx < commands.lastIndex) HorizontalDivider(
                                modifier  = Modifier.padding(horizontal = 56.dp),
                                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
