package com.coolappstore.everlastingandroidtweak.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coolappstore.everlastingandroidtweak.R
import com.coolappstore.everlastingandroidtweak.ui.screens.ReusableTopAppBar
// SegmentedDropdownMenu is in same package
// SegmentedDropdownMenuItem is in same package
// EditWatermarkSheet is in same package
// ExifSettingsSheet is in same package
// WatermarkControls is in same package
// WatermarkPreviewArea is in same package
import com.coolappstore.everlastingandroidtweak.features.watermark.WatermarkUiState
import com.coolappstore.everlastingandroidtweak.features.watermark.WatermarkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssentialsWatermarkScreen(
    initialUri: Uri?,
    onPickImage: () -> Unit,
    onBack: () -> Unit,
    viewModel: WatermarkViewModel
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showExifSheet by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }

    val options by viewModel.options.collectAsState()
    val previewState by viewModel.previewUiState.collectAsState()
    val saveState by viewModel.uiState.collectAsState()

    LaunchedEffect(initialUri) {
        initialUri?.let { viewModel.loadPreview(it) }
    }

    LaunchedEffect(saveState) {
        when (saveState) {
            is WatermarkUiState.Success -> {
                Toast.makeText(context, R.string.watermark_save_success, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }

            is WatermarkUiState.Error -> {
                Toast.makeText(
                    context,
                    (saveState as WatermarkUiState.Error).message,
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.resetState()
            }

            else -> {}
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ReusableTopAppBar(
                title = R.string.feat_watermark_title,
                hasBack = true,
                onBackClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onBack()
                },
                isSmall = true,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                actions = {
                    WatermarkActions(
                        initialUri = initialUri,
                        saveState = saveState,
                        onPickImage = onPickImage,
                        onShareClick = {
                            initialUri?.let { uri ->
                                viewModel.shareImage(uri) { sharedUri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, sharedUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.action_share)
                                        )
                                    )
                                }
                            }
                        },
                        onSaveClick = { initialUri?.let { viewModel.saveImage(it) } }
                    )
                }
            )
        },
        floatingActionButton = {
            if (initialUri != null) {
                FloatingActionButton(
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        showEditSheet = true
                    },
                    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_edit_24),
                        contentDescription = stringResource(R.string.action_edit)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { padding ->
        WatermarkPreviewArea(
            initialUri = initialUri,
            previewState = previewState,
            onPickImage = onPickImage,
            onRotate = { viewModel.rotate(it) },
            modifier = Modifier.padding(padding)
        ) {
            WatermarkControls(
                options = options,
                showLogo = viewModel.showLogo.collectAsState().value,
                logoResId = viewModel.logoResId.collectAsState().value,
                onStyleChange = { viewModel.setStyle(it) },
                onMoveToTopChange = { viewModel.setMoveToTop(it) },
                onLeftAlignChange = { viewModel.setLeftAlign(it) },
                onPaddingChange = { viewModel.setPadding(it) },
                onBrandTextSizeChange = { viewModel.setBrandTextSize(it) },
                onDataTextSizeChange = { viewModel.setDataTextSize(it) },
                onCustomTextSizeChange = { viewModel.setCustomTextSize(it) },
                onShowLogoChange = { viewModel.setShowLogo(it) },
                onLogoResIdChange = { viewModel.setLogoResId(it) },
                onLogoSizeChange = { viewModel.setLogoSize(it) },
                onBorderStrokeChange = { viewModel.setBorderStroke(it) },
                onBorderCornerChange = { viewModel.setBorderCorner(it) },
                onColorModeChange = { viewModel.setColorMode(it) },
                onShowExifClick = { showExifSheet = true }
            )
        }
    }

    if (showExifSheet) {
        ExifSettingsSheet(
            options = options,
            onDismissRequest = { showExifSheet = false },
            onShowExifChange = { viewModel.setShowExif(it) },
            onExifSettingsChange = { f, a, i, s, d -> viewModel.setExifSettings(f, a, i, s, d) }
        )
    }

    if (showEditSheet) {
        EditWatermarkSheet(
            options = options,
            currentBrand = viewModel.currentBrandText.collectAsState().value,
            currentCustom = viewModel.currentCustomText.collectAsState().value,
            currentDate = viewModel.currentDateText.collectAsState().value,
            formatDate = { viewModel.formatDate(it) },
            onDismissRequest = { showEditSheet = false },
            onSaveClick = { showBrand, brand, custom, date ->
                viewModel.setShowBrand(showBrand)
                viewModel.updateOverriddenTexts(brand, custom, date)
                showEditSheet = false
            }
        )
    }
}

@Composable
private fun WatermarkActions(
    initialUri: Uri?,
    saveState: WatermarkUiState,
    onPickImage: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val view = LocalView.current
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (initialUri == null) {
            Button(onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onPickImage()
            }) {
                Icon(
                    painter = painterResource(R.drawable.rounded_add_photo_alternate_24),
                    contentDescription = stringResource(R.string.watermark_pick_image),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.watermark_pick_image))
            }
        } else {
            OutlinedButton(onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onPickImage()
            }) {
                Icon(
                    painter = painterResource(R.drawable.rounded_add_photo_alternate_24),
                    contentDescription = stringResource(R.string.watermark_pick_image),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.size(8.dp))
            var showMenu by remember { mutableStateOf(false) }

            Box {
                Button(onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    showMenu = true
                }) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_save_24),
                        contentDescription = stringResource(R.string.action_save),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.action_save))
                }

                SegmentedDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    SegmentedDropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.rounded_share_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onShareClick()
                        },
                        enabled = saveState !is WatermarkUiState.Processing
                    )

                    SegmentedDropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.rounded_save_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onSaveClick()
                        },
                        enabled = saveState !is WatermarkUiState.Processing
                    )
                }
            }
        }
    }
}
