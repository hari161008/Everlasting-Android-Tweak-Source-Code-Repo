package tk.zwander.common.compose.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tk.zwander.common.activities.OnboardingActivity
import tk.zwander.common.compose.components.ContentColoredOutlinedButton
import tk.zwander.common.util.openAccessibilitySettings
import com.coolappstore.everlastingandroidtweak.R

/**
 * Accessibility warning card displayed when the accessibility service is not running.
 *
 * Redesigned to match the Everlasting app's card language:
 *  - Rounded-corner card (20 dp), 0 dp elevation
 *  - Error-tinted icon bubble + descriptive text
 *  - Action buttons in an Everlasting-style FlowRow
 */
@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
fun AccessibilityCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val errorColor = MaterialTheme.colorScheme.error
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = errorColor.copy(alpha = if (isDark) 0.18f else 0.10f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Icon + message row ─────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Box(
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(errorColor.copy(alpha = if (isDark) 0.30f else 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Accessibility,
                        contentDescription = null,
                        tint               = errorColor,
                        modifier           = Modifier.size(22.dp),
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text       = "Service not running",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = errorColor,
                    )
                    Text(
                        text  = stringResource(id = R.string.main_screen_accessibility_not_started),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(0.dp))

            // ── Action buttons ─────────────────────────────────────────
            FlowRow(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                ContentColoredOutlinedButton(
                    onClick = {
                        OnboardingActivity.start(
                            context,
                            OnboardingActivity.RetroMode.BATTERY,
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor   = errorColor,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = SolidColor(errorColor),
                    ),
                ) {
                    Text(text = stringResource(id = R.string.battery_whitelist))
                }

                ContentColoredOutlinedButton(
                    onClick = { context.openAccessibilitySettings() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor   = errorColor,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = SolidColor(errorColor),
                    ),
                ) {
                    Text(text = stringResource(id = R.string.accessibility_settings))
                }
            }
        }
    }
}
