package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import kotlinx.coroutines.launch

@Composable
fun VisibleFeaturesScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val hiddenRoutesStr by AppPreferences.get(AppPreferences.HIDDEN_FEATURE_ROUTES, "").collectAsState("")
    val hiddenRoutes = remember(hiddenRoutesStr) {
        if (hiddenRoutesStr.isBlank()) emptySet<String>()
        else hiddenRoutesStr.split(",").filter { it.isNotBlank() }.toSet()
    }

    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf(emptySet<String>()) }

    val filteredFeatures = remember(searchQuery) {
        if (searchQuery.isBlank()) EverlastingAllFeatures
        else EverlastingAllFeatures.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.subtitle.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    val grouped = remember(searchQuery) {
        filteredFeatures.groupBy { it.category }
    }

    val totalCount   = EverlastingAllFeatures.size
    val visibleCount = EverlastingAllFeatures.count { it.route !in hiddenRoutes }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val iconSolid by AppPreferences.get(AppPreferences.ICON_STYLE_SOLID, false).collectAsState(false)

    Scaffold(
        topBar = {
            EverlastingTopBar(
                title = "Visible Features",
                navController = navController,
                actions = {
                    val allVisible = hiddenRoutes.isEmpty()
                    TextButton(onClick = {
                        scope.launch {
                            if (allVisible) {
                                AppPreferences.set(AppPreferences.HIDDEN_FEATURE_ROUTES,
                                    EverlastingAllFeatures.map { it.route }.joinToString(","))
                            } else {
                                AppPreferences.set(AppPreferences.HIDDEN_FEATURE_ROUTES, "")
                            }
                        }
                    }) {
                        Text(if (allVisible) "Hide All" else "Show All",
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // ── Search bar ────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search features…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(Modifier.height(6.dp))
            }

            // ── Summary header ────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.GridView, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$visibleCount of $totalCount features visible",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Tap a category to expand · Use checkboxes to toggle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.75f))
                        }
                        CircularProgressIndicator(
                            progress = { visibleCount.toFloat() / totalCount.coerceAtLeast(1) },
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(0.2f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (filteredFeatures.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
                        Text("No features match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Categories ────────────────────────────────────────────────────
            grouped.forEach { (category, features) ->
                val catExpanded  = category in expandedCategories || searchQuery.isNotBlank()
                val catVisible   = features.count { it.route !in hiddenRoutes }
                val allCatVisible = catVisible == features.size

                item(key = "cat_$category") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = if (catExpanded) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                else MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (searchQuery.isBlank()) {
                                        expandedCategories = if (category in expandedCategories)
                                            expandedCategories - category
                                        else expandedCategories + category
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (catExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                category,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (allCatVisible)
                                    MaterialTheme.colorScheme.primary.copy(0.15f)
                                else MaterialTheme.colorScheme.errorContainer.copy(0.5f)
                            ) {
                                Text(
                                    "$catVisible/${features.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (allCatVisible)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            Checkbox(
                                checked = allCatVisible,
                                onCheckedChange = { checked ->
                                    val routes = features.map { it.route }.toSet()
                                    val newHidden = if (checked) hiddenRoutes - routes
                                                    else hiddenRoutes + routes
                                    scope.launch {
                                        AppPreferences.set(AppPreferences.HIDDEN_FEATURE_ROUTES,
                                            newHidden.joinToString(","))
                                    }
                                }
                            )
                        }
                    }
                }

                // ── Expanded feature rows ─────────────────────────────────────
                if (catExpanded) {
                    items(features, key = { it.route }) { feat ->
                        val isVisible = feat.route !in hiddenRoutes
                        val iconColor = Color(feat.iconColor)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = if (feat == features.last())
                                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            else RoundedCornerShape(0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        val newHidden = if (isVisible) hiddenRoutes + feat.route
                                                        else hiddenRoutes - feat.route
                                        scope.launch {
                                            AppPreferences.set(AppPreferences.HIDDEN_FEATURE_ROUTES,
                                                newHidden.joinToString(","))
                                        }
                                    }
                                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Feature icon matching home screen style
                                Box(
                                    Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                                        .background(
                                            if (iconSolid)
                                                iconColor.copy(alpha = if (isDark) 0.88f else 0.85f)
                                            else
                                                iconColor.copy(alpha = if (isDark) 0.20f else 0.13f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(feat.icon, null,
                                        tint = if (iconSolid) Color.White else iconColor,
                                        modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        feat.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isVisible)
                                            MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(0.45f)
                                    )
                                    Text(
                                        feat.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            if (isVisible) 1f else 0.4f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = isVisible,
                                    onCheckedChange = { checked ->
                                        val newHidden = if (checked) hiddenRoutes - feat.route
                                                        else hiddenRoutes + feat.route
                                        scope.launch {
                                            AppPreferences.set(AppPreferences.HIDDEN_FEATURE_ROUTES,
                                                newHidden.joinToString(","))
                                        }
                                    }
                                )
                            }
                            if (feat != features.last()) {
                                HorizontalDivider(
                                    Modifier.padding(start = 62.dp, end = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.35f)
                                )
                            }
                        }
                    }
                }

                item(key = "spacer_$category") { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}
