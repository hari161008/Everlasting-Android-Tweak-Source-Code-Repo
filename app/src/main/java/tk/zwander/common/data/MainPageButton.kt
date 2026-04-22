package tk.zwander.common.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

data class MainPageButton(
    @DrawableRes
    val icon: Int,
    @StringRes
    val title: Int,
    val dependency: @Composable () -> Boolean = { true },
    val onClick: () -> Unit,
)