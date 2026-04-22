package com.coolappstore.everlastingandroidtweak.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.features.appfreezer.AppFreezerHelper
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppProcess(
    val name: String,
    val packageName: String,
    val memoryMb: Float,
    val pid: Int = -1
)

@Composable
fun TaskManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var processes by remember { mutableStateOf<List<AppProcess>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var totalMb by remember { mutableLongStateOf(0L) }
    var availMb by remember { mutableLongStateOf(0L) }
    val ramHistory = remember { mutableStateListOf<Float>() }
    var intervalSec by remember { mutableIntStateOf(5) }
    var showIntervalPicker by remember { mutableStateOf(false) }
    val intervalOptions = listOf(2 to "2s", 5 to "5s", 10 to "10s", 30 to "30s", 60 to "1 min")
    var usingShizuku by remember { mutableStateOf(false) }
    var killingPid by remember { mutableStateOf(-1) }

    // Try to get process list — prefer Shizuku dumpsys, fallback to ActivityManager
    suspend fun loadProcesses() {
        withContext(Dispatchers.IO) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = context.packageManager
            val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            totalMb = memInfo.totalMem / 1024 / 1024
            availMb = memInfo.availMem / 1024 / 1024
            val usedPct = 1f - availMb.toFloat() / totalMb.toFloat()
            withContext(Dispatchers.Main) {
                ramHistory.add(usedPct.coerceIn(0f, 1f))
                if (ramHistory.size > 30) ramHistory.removeAt(0)
            }

            val shizukuReady = AppFreezerHelper.isShizukuReady()
            val result = mutableListOf<AppProcess>()

            if (shizukuReady) {
                // Shizuku path — use `dumpsys meminfo -a` for full process info
                try {
                    withContext(Dispatchers.Main) { usingShizuku = true }
                    val proc = Runtime.getRuntime().exec(
                        arrayOf("sh", "-c", "dumpsys meminfo --oom | grep -E '^\\s+[0-9]+' | head -60")
                    )
                    val lines = proc.inputStream.bufferedReader().readLines()
                    proc.waitFor()

                    lines.forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 3) {
                            val memKb = parts[0].toLongOrNull() ?: return@forEach
                            val pid = parts[1].toIntOrNull() ?: -1
                            val rawPkg = parts.lastOrNull() ?: return@forEach
                            val pkg = rawPkg.split(":").first()
                            val appName = try {
                                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                            } catch (_: Exception) { pkg }
                            result.add(AppProcess(appName, pkg, memKb / 1024f, pid))
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { usingShizuku = false }
                }
            }

            // Fallback or supplement with ActivityManager running processes
            if (result.isEmpty()) {
                withContext(Dispatchers.Main) { usingShizuku = false }
                val running = am.runningAppProcesses ?: emptyList()
                running.forEach { proc ->
                    val memMb = am.getProcessMemoryInfo(intArrayOf(proc.pid))
                        .firstOrNull()?.totalPss?.div(1024f) ?: 0f
                    val pkg = proc.processName.split(":").first()
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { proc.processName }
                    result.add(AppProcess(label, pkg, memMb, proc.pid))
                }
            }

            val sorted = result.sortedByDescending { it.memoryMb }
            withContext(Dispatchers.Main) { processes = sorted; loading = false }
        }
    }

    suspend fun killProcess(proc: AppProcess) {
        withContext(Dispatchers.IO) {
            try {
                if (AppFreezerHelper.isShizukuReady() && proc.pid > 0) {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "kill -9 ${proc.pid}")).waitFor()
                } else {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    am.killBackgroundProcesses(proc.packageName)
                }
                delay(300)
                loadProcesses()
            } catch (_: Exception) {}
        }
    }

    // Auto-refresh loop
    LaunchedEffect(intervalSec) {
        while (true) { loadProcesses(); delay(intervalSec * 1000L) }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(topBar = {
        EverlastingTopBar("Task Manager", navController, actions = {
            IconButton(onClick = { showIntervalPicker = true }) { Icon(Icons.Default.Timer, "Refresh interval") }
            IconButton(onClick = { scope.launch { loadProcesses() } }) { Icon(Icons.Default.Refresh, "Refresh") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Shizuku badge
            if (usingShizuku) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("Using Shizuku — showing all running processes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("ActivityManager mode — enable Shizuku for full process list",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // RAM graph card
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("RAM Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${totalMb - availMb} / $totalMb MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.height(8.dp))
                    val usedFraction = if (totalMb > 0) (totalMb - availMb).toFloat() / totalMb else 0f
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        color = when {
                            usedFraction > 0.85f -> Color(0xFFF44336)
                            usedFraction > 0.65f -> Color(0xFFFF9800)
                            else -> primaryColor
                        },
                        trackColor = primaryColor.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(12.dp))
                    if (ramHistory.size > 1) {
                        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                            val w = size.width; val h = size.height
                            val pts = ramHistory.size
                            val stepX = w / (pts - 1).coerceAtLeast(1)
                            val fillPath = Path().apply {
                                moveTo(0f, h)
                                ramHistory.forEachIndexed { i, v -> lineTo(i * stepX, h - v * h) }
                                lineTo((pts - 1) * stepX, h); close()
                            }
                            drawPath(fillPath, primaryColor.copy(alpha = 0.15f))
                            val linePath = Path().apply {
                                ramHistory.forEachIndexed { i, v ->
                                    if (i == 0) moveTo(0f, h - v * h) else lineTo(i * stepX, h - v * h)
                                }
                            }
                            drawPath(linePath, primaryColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                            drawCircle(primaryColor, radius = 4.dp.toPx(),
                                center = Offset((ramHistory.size - 1) * stepX, h - ramHistory.last() * h))
                        }
                    }
                    Text("${processes.size} processes  •  auto-refreshes every ${intervalSec}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(processes, key = { "${it.packageName}_${it.pid}" }) { proc ->
                        val maxMem = processes.firstOrNull()?.memoryMb ?: 1f
                        val fraction = proc.memoryMb / maxMem
                        val isKilling = killingPid == proc.pid

                        ListItem(
                            headlineContent = {
                                Text(proc.name, fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium)
                            },
                            supportingContent = {
                                Column {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(proc.packageName, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (proc.pid > 0)
                                            Text("PID ${proc.pid}", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth(0.65f).height(4.dp).clip(CircleShape),
                                        color = when {
                                            fraction > 0.7f -> Color(0xFFF44336)
                                            fraction > 0.4f -> Color(0xFFFF9800)
                                            else -> primaryColor
                                        },
                                        trackColor = primaryColor.copy(alpha = 0.12f)
                                    )
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${"%.1f".format(proc.memoryMb)} MB",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold)
                                    FilledTonalButton(
                                        onClick = {
                                            killingPid = proc.pid
                                            scope.launch {
                                                killProcess(proc)
                                                killingPid = -1
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        enabled = !isKilling
                                    ) {
                                        if (isKilling)
                                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                                        else
                                            Text("Kill", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showIntervalPicker) {
        AlertDialog(onDismissRequest = { showIntervalPicker = false },
            title = { Text("Refresh Interval") },
            text = {
                Column {
                    intervalOptions.forEach { (sec, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = intervalSec == sec, onClick = {
                                intervalSec = sec
                                scope.launch { AppPreferences.set(AppPreferences.TASK_UPDATE_INTERVAL, sec) }
                            })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIntervalPicker = false }) { Text("Done") } }
        )
    }
}
