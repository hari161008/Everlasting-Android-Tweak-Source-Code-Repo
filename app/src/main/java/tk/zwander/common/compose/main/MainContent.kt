package tk.zwander.common.compose.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolappstore.everlastingandroidtweak.ui.theme.LocalBlurAmount
import com.coolappstore.everlastingandroidtweak.ui.theme.LocalBlurEnabled
import tk.zwander.common.compose.util.insetsContentPadding
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate

/**
 * Main content of the Lock Screen Widgets screen.
 *
 * CHANGES:
 *  - Removed DebugCard (Debug section).
 *  - Removed all link items (Translate, Privacy Policy, Mastodon, Website,
 *    Email, GitHub, Patreon, Supporters) and the divider preceding them.
 *  - Redesigned card layout to match the Everlasting Android Tweak app UI.
 *  - Syncs with Appearance settings via [LocalBlurEnabled] / [LocalBlurAmount]
 *    provided by EverlastingTheme (set in LSW's MainActivity).
 */
@Composable
fun MainContent() {
    val features = rememberFeatureCards()

    val hasFrameDelegateInstance =
        MainWidgetFrameDelegate.readOnlyInstance.collectAsState().value != null
    val hasDrawerDelegateInstance = DrawerDelegate.readOnlyInstance.collectAsState().value != null

    // ── Read Everlasting appearance prefs via CompositionLocals ────────────
    // LocalBlurEnabled / LocalBlurAmount are provided by EverlastingTheme
    // which is now the root theme wrapper in LSW's MainActivity.
    val blurEnabled = LocalBlurEnabled.current
    val blurAmount  = LocalBlurAmount.current
    val primary     = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {

        // ── Background layer ────────────────────────────────────────────────
        // When blur is enabled: draw a blurred gradient behind all cards so
        // the semi-transparent card surfaces (set by EverlastingTheme) produce
        // a frosted-glass look.  Content inside the cards is drawn in a
        // SEPARATE layer above this Box and is never blurred.
        if (blurEnabled) {
            Box(
                Modifier
                    .fillMaxSize()
                    // BUG FIX: blur is applied ONLY to this background Box.
                    // The LazyColumn / Card content below is in a sibling
                    // composable rendered above this Box — it stays fully sharp.
                    .blur(blurAmount.coerceIn(8f, 32f).dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.background,
                                primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            )
                        )
                    )
            )
        } else {
            // Solid background — fills the window so the Surface token colour
            // from EverlastingTheme is always visible.
            Surface(Modifier.fillMaxSize()) {}
        }

        // ── Scrollable content ──────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = insetsContentPadding(
                WindowInsets.systemBars,
                WindowInsets.ime,
                extraPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── App branding header ─────────────────────────────────────────
            item(key = "LswHeader") {
                LswHeaderCard(modifier = Modifier.animateItem())
            }

            // ── Accessibility / service-not-running warning ─────────────────
            if (!hasFrameDelegateInstance || !hasDrawerDelegateInstance) {
                item(key = "AccessibilityCard") {
                    AccessibilityCard(modifier = Modifier.animateItem())
                }
            }

            // ── Feature section label ───────────────────────────────────────
            item(key = "FeaturesLabel") {
                EverlastingSectionLabel(
                    text = "Widget Features",
                    modifier = Modifier.animateItem(),
                )
            }

            // ── Feature cards (Lock Screen Frame, Widget Drawer, Stacks) ────
            items(features.size, key = { features[it].title }) {
                FeatureCard(
                    info     = features[it],
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────────

/**
 * Section label styled to match the Everlasting app (primary-coloured,
 * bold labelLarge with the same horizontal indent).
 */
@Composable
private fun EverlastingSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

/**
 * Branded header card that mirrors the Everlasting "hero card" pattern:
 * rounded-corner card with a coloured icon bubble and app name / subtitle.
 */
@Composable
private fun LswHeaderCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier              = Modifier.padding(20.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector     = Icons.Default.Widgets,
                    contentDescription = null,
                    tint            = MaterialTheme.colorScheme.primary,
                    modifier        = Modifier.size(28.dp),
                )
            }

            Column {
                Text(
                    text       = "Lock Screen Widgets",
                    fontWeight = FontWeight.Black,
                    fontSize   = 20.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = "Add widgets to your lock screen",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
