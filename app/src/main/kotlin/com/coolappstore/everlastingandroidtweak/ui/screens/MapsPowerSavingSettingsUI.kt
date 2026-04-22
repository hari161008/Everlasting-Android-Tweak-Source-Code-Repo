package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.coolappstore.everlastingandroidtweak.R
import com.coolappstore.everlastingandroidtweak.ui.screens.IconToggleItem
import com.coolappstore.everlastingandroidtweak.ui.screens.RoundedCardContainer
import com.coolappstore.everlastingandroidtweak.ui.screens.highlight
import com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsPowerSavingViewModel

@Composable
fun MapsPowerSavingSettingsUI(
    viewModel: MapsPowerSavingViewModel,
    modifier: Modifier = Modifier,
    highlightSetting: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Detection Channels",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp)
        )

        RoundedCardContainer(
            modifier = Modifier,
            spacing = 2.dp,
            cornerRadius = 24.dp
        ) {
            viewModel.mapsChannels.value.forEach { channel ->
                IconToggleItem(
                    iconRes = R.drawable.rounded_navigation_24,
                    title = channel.name,
                    isChecked = channel.isEnabled,
                    onCheckedChange = { checked ->
                        HapticUtil.performVirtualKeyHaptic(view)
                        viewModel.setMapsChannelDetected(channel.id, checked, context)
                    },
                    modifier = Modifier.highlight(highlightSetting == channel.id)
                )
            }

            if (viewModel.mapsChannels.value.isEmpty()) {
                Text(
                    text = "No Maps channels discovered yet. They will appear here once detected while navigating.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
