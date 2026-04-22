package tk.zwander.common.compose.util

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import tk.zwander.common.util.setThemedContent
import tk.zwander.common.util.themedLayoutInflater
import com.coolappstore.everlastingandroidtweak.databinding.ComposeViewHolderBinding

@Suppress("OPT_IN_USAGE")
context(ctx: Context)
fun <T> T.createComposeViewHolder(block: @Composable T.(View) -> Unit): View {
    return ComposeViewHolderBinding.inflate(ctx.themedLayoutInflater).root.apply {
        // `this` here is the ComposeView root — setThemedContent is an extension on ComposeView
        setThemedContent { this@createComposeViewHolder.block(this@apply) }
    }
}
