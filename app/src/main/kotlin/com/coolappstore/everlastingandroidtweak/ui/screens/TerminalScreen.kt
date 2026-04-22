package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TerminalLine(val text: String, val type: LineType = LineType.OUTPUT)
enum class LineType { COMMAND, OUTPUT, ERROR, INFO, SUCCESS }

data class TerminalTheme(
    val name: String, val emoji: String,
    val background: Color, val commandColor: Color, val outputColor: Color,
    val errorColor: Color, val successColor: Color, val infoColor: Color,
    val promptColor: Color, val cursorColor: Color, val inputBg: Color
)

val terminalThemes = listOf(
    TerminalTheme("Hacker","💚",Color(0xFF0D0D0D),Color(0xFF00FF41),Color(0xFF00CC33),Color(0xFFFF0033),Color(0xFF00FF41),Color(0xFF00AA22),Color(0xFF00FF41),Color(0xFF00FF41),Color(0xFF1A1A1A)),
    TerminalTheme("Ocean","🌊",Color(0xFF0A1628),Color(0xFF64FFDA),Color(0xFFB2EBF2),Color(0xFFFF5252),Color(0xFF69F0AE),Color(0xFF80DEEA),Color(0xFF4FC3F7),Color(0xFF64FFDA),Color(0xFF0D1F3C)),
    TerminalTheme("Dracula","🧛",Color(0xFF282A36),Color(0xFFFF79C6),Color(0xFFF8F8F2),Color(0xFFFF5555),Color(0xFF50FA7B),Color(0xFF8BE9FD),Color(0xFFBD93F9),Color(0xFFF8F8F2),Color(0xFF44475A)),
    TerminalTheme("Sunset","🌅",Color(0xFF1A0A00),Color(0xFFFFD700),Color(0xFFFFCC80),Color(0xFFFF5252),Color(0xFF8BC34A),Color(0xFFFFB74D),Color(0xFFFF6F00),Color(0xFFFFD700),Color(0xFF2D1500)),
    TerminalTheme("Arctic","❄️",Color(0xFF1C2333),Color(0xFFE0F7FA),Color(0xFFB0BEC5),Color(0xFFEF9A9A),Color(0xFFA5D6A7),Color(0xFF80CBC4),Color(0xFF80DEEA),Color(0xFFE0F7FA),Color(0xFF253040)),
    TerminalTheme("Neon","⚡",Color(0xFF000014),Color(0xFFE040FB),Color(0xFF00E5FF),Color(0xFFFF1744),Color(0xFF76FF03),Color(0xFF00BFA5),Color(0xFFE040FB),Color(0xFF00E5FF),Color(0xFF0A0A28)),
    TerminalTheme("Monokai","🎨",Color(0xFF272822),Color(0xFFF92672),Color(0xFFF8F8F2),Color(0xFFF92672),Color(0xFFA6E22E),Color(0xFF66D9E8),Color(0xFFE6DB74),Color(0xFFF8F8F2),Color(0xFF3E3D32)),
    TerminalTheme("Ubuntu","🟠",Color(0xFF300A24),Color(0xFFFFFFFF),Color(0xFFAAAAAA),Color(0xFFFF6666),Color(0xFF88FF88),Color(0xFF88AAFF),Color(0xFFE95420),Color(0xFFFFFFFF),Color(0xFF3D0F2B)),
    TerminalTheme("Solarized","☀️",Color(0xFF002B36),Color(0xFF268BD2),Color(0xFF839496),Color(0xFFDC322F),Color(0xFF859900),Color(0xFF2AA198),Color(0xFFCB4B16),Color(0xFF93A1A1),Color(0xFF073642)),
    TerminalTheme("Retro","📺",Color(0xFF0A0800),Color(0xFFFFAA00),Color(0xFFCC8800),Color(0xFFFF4400),Color(0xFF88AA00),Color(0xFFAA8800),Color(0xFFFFCC00),Color(0xFFFFAA00),Color(0xFF161200))
)

@Composable
fun TerminalScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showThemePicker by remember { mutableStateOf(false) }

    val savedThemeIndex by AppPreferences.get(AppPreferences.TERMINAL_THEME_INDEX, 0).collectAsState(0)
    val theme = terminalThemes.getOrElse(savedThemeIndex) { terminalThemes[0] }

    val lines = remember {
        mutableStateListOf(
            TerminalLine("╔═══════════════════════════════════╗", LineType.INFO),
            TerminalLine("║   Everlasting Terminal  v2.0      ║", LineType.SUCCESS),
            TerminalLine("╚═══════════════════════════════════╝", LineType.INFO),
            TerminalLine("Type 'help' for available commands.", LineType.INFO),
            TerminalLine("Theme: ${theme.emoji} ${theme.name}  •  tap 🎨 to change", LineType.INFO),
        )
    }

    // AUTO-SHOW KEYBOARD: request focus immediately so keyboard pops up
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Update welcome line when theme changes
    LaunchedEffect(savedThemeIndex) {
        val idx = lines.indexOfFirst { it.text.startsWith("Theme:") }
        if (idx >= 0) lines[idx] = TerminalLine("Theme: ${theme.emoji} ${theme.name}  •  tap 🎨 to change", LineType.INFO)
    }

    fun execute(cmd: String) {
        if (cmd.isBlank()) return
        lines.add(TerminalLine("$ $cmd", LineType.COMMAND))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    when (cmd.trim().lowercase()) {
                        "help" -> "SUCCESS:Commands: ls, pwd, date, id, uname -a, getprop\n  df -h, free, ps, pm list packages\n  cat /proc/cpuinfo, cat /proc/meminfo\n  clear, theme, exit"
                        "clear" -> "CLEAR"
                        "theme" -> "INFO:Tap 🎨 in the top bar to pick from 10 themes.\nCurrent: ${theme.emoji} ${theme.name}"
                        "exit"  -> "INFO:Tap the back arrow to exit the terminal."
                        else -> {
                            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                            val out = process.inputStream.bufferedReader().readText()
                            val err = process.errorStream.bufferedReader().readText()
                            process.waitFor()
                            when {
                                err.isNotEmpty() -> "ERROR:$err"
                                out.isNotEmpty() -> out
                                else -> "SUCCESS:(done — no output)"
                            }
                        }
                    }
                } catch (e: Exception) { "ERROR:${e.message ?: "Unknown error"}" }
            }
            when {
                result == "CLEAR" -> lines.clear()
                result.startsWith("ERROR:") -> lines.add(TerminalLine(result.removePrefix("ERROR:").trim(), LineType.ERROR))
                result.startsWith("SUCCESS:") -> result.removePrefix("SUCCESS:").lines().forEach { lines.add(TerminalLine(it, LineType.SUCCESS)) }
                result.startsWith("INFO:") -> result.removePrefix("INFO:").lines().forEach { lines.add(TerminalLine(it, LineType.INFO)) }
                else -> result.lines().forEach { lines.add(TerminalLine(it, LineType.OUTPUT)) }
            }
            if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
        }
    }

    // Theme picker dialog
    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            containerColor = Color(0xFF1A1A2E),
            title = { Text("🎨  Terminal Theme", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    terminalThemes.forEachIndexed { i, t ->
                        val selected = i == savedThemeIndex
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) t.commandColor.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable {
                                    scope.launch { AppPreferences.set(AppPreferences.TERMINAL_THEME_INDEX, i) }
                                    showThemePicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(t.commandColor, t.outputColor, t.successColor, t.errorColor, t.infoColor).forEach { c ->
                                    Box(Modifier.size(9.dp).clip(CircleShape).background(c))
                                }
                            }
                            Text("${t.emoji} ${t.name}",
                                color = if (selected) t.commandColor else Color(0xFFCCCCCC),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            if (selected) Icon(Icons.Default.Check, null, tint = t.commandColor, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text("Close", color = Color.White) } }
        )
    }

    Scaffold(
        topBar = {
            EverlastingTopBar("Terminal  ${theme.emoji}", navController, actions = {
                IconButton(onClick = { showThemePicker = true }) {
                    Icon(Icons.Default.Palette, "Theme", tint = theme.promptColor)
                }
                IconButton(onClick = { lines.clear() }) {
                    Icon(Icons.Default.ClearAll, "Clear", tint = theme.errorColor)
                }
            })
        },
        bottomBar = {
            Surface(color = theme.inputBg, shadowElevation = 10.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("❯", color = theme.promptColor, fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        placeholder = {
                            Text("Enter command…", fontFamily = FontFamily.Monospace,
                                color = theme.outputColor.copy(alpha = 0.4f), fontSize = 13.sp)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = theme.commandColor),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { execute(input); input = "" }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.promptColor,
                            unfocusedBorderColor = theme.promptColor.copy(alpha = 0.35f),
                            cursorColor = theme.cursorColor,
                            focusedContainerColor = theme.inputBg,
                            unfocusedContainerColor = theme.inputBg
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    FilledIconButton(
                        onClick = { execute(input); input = "" },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = theme.promptColor.copy(alpha = 0.2f),
                            contentColor = theme.promptColor
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Run", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(theme.background)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(lines) { line ->
                val color = when (line.type) {
                    LineType.COMMAND -> theme.commandColor
                    LineType.ERROR   -> theme.errorColor
                    LineType.SUCCESS -> theme.successColor
                    LineType.INFO    -> theme.infoColor
                    LineType.OUTPUT  -> theme.outputColor
                }
                Text(
                    text = when (line.type) {
                        LineType.ERROR -> "✗ ${line.text}"
                        LineType.SUCCESS -> if (line.text.startsWith("╔") || line.text.startsWith("║") || line.text.startsWith("╚")) line.text else "✓ ${line.text}"
                        else -> line.text
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = color,
                    lineHeight = 18.sp
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
