package tk.zwander.common.compose.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tk.zwander.common.compose.data.MainPageLink

/**
 * Returns an empty link list.
 *
 * All third-party / LSW-specific links (Translate, Privacy Policy, Mastodon,
 * Website, Email, GitHub, Patreon, Supporters) have been removed from the
 * Lock Screen Widgets screen as part of the Everlasting integration.
 * The links section and its divider are no longer rendered in MainContent.
 */
@Composable
fun rememberLinks(): List<MainPageLink> = remember { emptyList() }

// LinkItem composable retained so existing call sites compile without changes,
// but it is no longer called from MainContent.
@Composable
fun LinkItem(
    option: MainPageLink,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    // No-op: links are hidden in the Everlasting integration.
}
