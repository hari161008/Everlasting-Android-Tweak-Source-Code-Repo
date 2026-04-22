package com.musheer360.swiftslate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Shared shape constants — mirrors host app's extraLarge card shape
val ExtraLargeShape = RoundedCornerShape(20.dp)
val InputShape      = RoundedCornerShape(14.dp)
val ChipShape       = RoundedCornerShape(50.dp)
val IconBoxShape    = RoundedCornerShape(12.dp)

// Card colours that match host app's surfaceVariant cards
@Composable
fun slateCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
)

// Translucent icon box — matches the host app's FeatureListItem icon style
@Composable
fun IconBox(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier        = modifier
            .size(42.dp)
            .clip(IconBoxShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
        content          = { content() },
    )
}

// Consistent outlined text field colours for the warm palette
@Composable
fun slateTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor          = MaterialTheme.colorScheme.primary,
    focusedLabelColor    = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
)
