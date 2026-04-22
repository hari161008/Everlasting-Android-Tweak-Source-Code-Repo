package com.aistra.hail.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.aistra.hail.HailApp.Companion.app
import com.coolappstore.everlastingandroidtweak.R
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailApi
import com.aistra.hail.app.HailApi.addTag
import com.aistra.hail.app.HailData
import com.coolappstore.everlastingandroidtweak.databinding.DialogInputBinding
import com.aistra.hail.extensions.*
import com.aistra.hail.ui.main.MainFragment
import com.aistra.hail.ui.theme.AppTheme
import com.aistra.hail.utils.*
import com.aistra.hail.work.HWork
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class PagerFragment : MainFragment(), MenuProvider {

    private var query: String = String()

    // Compose-backed list drives the grid (no PagerAdapter needed).
    private val displayList = mutableStateListOf<AppInfo>()

    private var multiselect: Boolean
        set(value) { (parentFragment as HomeFragment).multiselect = value }
        get() = (parentFragment as HomeFragment).multiselect

    private val selectedList   get() = (parentFragment as HomeFragment).selectedList
    private val tabs: TabLayout get() = (parentFragment as HomeFragment).binding.tabs
    private val pagerAdapter   get() = (parentFragment as HomeFragment).binding.pager.adapter as HomeAdapter
    private val tag: Pair<String, Int> get() = HailData.tags[tabs.selectedTabPosition]

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val menuHost = requireActivity() as MenuHost
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { AppTheme { AppFreezeScreen() } }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentList()
        updateBarTitle()
        tabs.getTabAt(tabs.selectedTabPosition)?.view?.setOnLongClickListener {
            if (isResumed) showTagDialog()
            true
        }
        activity.fab.setOnClickListener {
            // FIX: snapshot the list before iterating to avoid ConcurrentModificationException
            setListFrozen(true, displayList.toList().filterNot { it.whitelisted })
        }
        activity.fab.setOnLongClickListener {
            setListFrozen(true)
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    // ── Compose UI ────────────────────────────────────────────────────────────

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppFreezeScreen() {
        var isRefreshing by remember { mutableStateOf(false) }
        val pullState = rememberPullToRefreshState()

        if (displayList.isEmpty()) {
            EmptyState()
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    updateCurrentList()
                    isRefreshing = false
                },
                state    = pullState,
                modifier = Modifier.fillMaxSize()
            ) {
                val columns = if (HailData.compactIcon) 5 else 4
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(columns),
                    modifier              = Modifier.fillMaxSize(),
                    contentPadding        = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 128.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayList, key = { it.packageName }) { info ->
                        AppItem(
                            info        = info,
                            isSelected  = info in selectedList,
                            onClick     = { onItemClick(info) },
                            onLongClick = { onItemLongClick(info) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AppItem(
        info: AppInfo,
        isSelected: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Boolean,
    ) {
        val isFrozen   = info.state == AppInfo.State.FROZEN
        val isNotFound = info.state == AppInfo.State.NOT_FOUND

        val containerColor = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isFrozen   -> MaterialTheme.colorScheme.surfaceContainerHighest
            else       -> MaterialTheme.colorScheme.surfaceContainerLow
        }
        val strokeColor = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isFrozen   -> MaterialTheme.colorScheme.outlineVariant
            else       -> Color.Transparent
        }

        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .border(
                    width = if (isSelected || isFrozen) 1.5.dp else 0.dp,
                    color = strokeColor,
                    shape = RoundedCornerShape(18.dp)
                )
                // FIX: Use pointerInput instead of combinedClickable so we don't
                // need the experimental ExperimentalFoundationApi opt-in.
                .pointerInput(info.packageName, isSelected) {
                    detectTapGestures(
                        onTap      = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                },
            shape          = RoundedCornerShape(18.dp),
            color          = containerColor,
            tonalElevation = if (isFrozen) 0.dp else 1.dp,
        ) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Icon + status badges
                    Box(contentAlignment = Alignment.TopEnd) {
                        val iconSz = if (HailData.compactIcon) 38.dp else 46.dp
                        // Always attempt to load the icon — even when applicationInfo is null
                        // (e.g. app is hidden/disabled by some freeze methods)
                        AppIcon(
                            packageName = info.packageName,
                            appInfo     = info.applicationInfo,
                            isFrozen    = isFrozen,
                            modifier    = Modifier.size(iconSz)
                        )

                        // Frozen snowflake badge
                        if (isFrozen) {
                            Surface(
                                modifier = Modifier.size(15.dp).offset(x = 3.dp, y = (-3).dp),
                                shape    = CircleShape,
                                color    = MaterialTheme.colorScheme.primary,
                            ) {
                                Icon(
                                    imageVector        = Icons.Outlined.AcUnit,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.onPrimary,
                                    modifier           = Modifier.fillMaxSize().padding(2.5.dp)
                                )
                            }
                        }

                        // Whitelisted lock badge
                        if (info.whitelisted) {
                            Surface(
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x = if (isFrozen) (-14).dp else 3.dp, y = (-3).dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary,
                            ) {
                                Icon(
                                    imageVector        = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.onTertiary,
                                    modifier           = Modifier.fillMaxSize().padding(2.5.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text      = info.name.toString(),
                        style     = MaterialTheme.typography.labelSmall.copy(
                            fontSize = HailData.homeFontSize.sp
                        ),
                        maxLines  = 2,
                        overflow  = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color     = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isNotFound -> MaterialTheme.colorScheme.error
                            isFrozen && HailData.grayscaleIcon
                                       -> MaterialTheme.colorScheme.onSurfaceVariant
                            else       -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun AppIcon(
        packageName: String,
        appInfo    : android.content.pm.ApplicationInfo?,
        isFrozen   : Boolean,
        modifier   : Modifier = Modifier,
    ) {
        val context = LocalContext.current
        var bmp by remember(packageName, isFrozen) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(packageName, isFrozen) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val drawable = try {
                        if (appInfo != null) pm.getApplicationIcon(appInfo)
                        else pm.getApplicationIcon(packageName)
                    } catch (_: Exception) {
                        try { pm.getApplicationIcon(packageName) }
                        catch (_: Exception) { pm.defaultActivityIcon }
                    }
                    val size = 192
                    val raw  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(Canvas(raw))
                    bmp = if (isFrozen && HailData.grayscaleIcon) {
                        val grey  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        val paint = Paint().apply {
                            colorFilter = ColorMatrixColorFilter(
                                ColorMatrix().apply { setSaturation(0f) }
                            )
                        }
                        Canvas(grey).drawBitmap(raw, 0f, 0f, paint)
                        raw.recycle()
                        grey
                    } else raw
                }
            }
        }

        DisposableEffect(packageName, isFrozen) {
            onDispose { bmp?.recycle(); bmp = null }
        }

        bmp?.let {
            Image(
                painter            = BitmapPainter(it.asImageBitmap()),
                contentDescription = null,
                modifier           = modifier.clip(RoundedCornerShape(10.dp))
            )
        } ?: Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Icon(
                imageVector        = if (isFrozen) Icons.Outlined.AcUnit else Icons.Outlined.Apps,
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxSize(0.55f)
                    .align(Alignment.Center),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }

    @Composable
    private fun EmptyState() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(32.dp)
            ) {
                // Card-style icon container matching Everlasting's feature icon aesthetic
                Surface(
                    shape  = RoundedCornerShape(28.dp),
                    color  = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(96.dp),
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.AcUnit,
                            contentDescription = null,
                            modifier           = Modifier.size(52.dp),
                            tint               = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text      = "No frozen apps",
                    style     = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color     = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "Tap ❄ below to add apps to freeze",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // ── Tri-state tag dialog (Compose) ────────────────────────────────────────

    @Composable
    private fun TriStateTagList(
        initialStates: Array<ToggleableState>,
        states: SnapshotStateList<ToggleableState>
    ) = Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        HailData.tags.forEachIndexed { index, tagEntry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        states[index] = if (initialStates[index] == ToggleableState.Indeterminate)
                            when (states[index]) {
                                ToggleableState.On            -> ToggleableState.Off
                                ToggleableState.Off           -> ToggleableState.Indeterminate
                                ToggleableState.Indeterminate -> ToggleableState.On
                            }
                        else if (states[index] == ToggleableState.On) ToggleableState.Off
                        else ToggleableState.On
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriStateCheckbox(
                    state   = states[index],
                    onClick = null,
                    colors  = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary)
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    // FIX: renamed loop variable from 'tag' to 'tagEntry' to avoid shadowing
                    // the outer class member 'tag: Pair<String,Int>'
                    text  = tagEntry.first,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    // ── Business logic ────────────────────────────────────────────────────────

    private fun updateCurrentList() = HailData.checkedList.filter {
        if (query.isEmpty()) tag.second in it.tagIdList
        else ((HailData.nineKeySearch && NineKeySearch.search(query, it.packageName, it.name.toString()))
                || FuzzySearch.search(it.packageName, query)
                || FuzzySearch.search(it.name.toString(), query)
                || PinyinSearch.searchPinyinAll(it.name.toString(), query))
    }.sortedWith(NameComparator).let { sorted ->
        displayList.clear()
        displayList.addAll(sorted)
        app.setAutoFreezeService()
    }

    private fun updateBarTitle() {
        activity.supportActionBar?.title =
            if (multiselect) getString(R.string.msg_selected, selectedList.size.toString())
            else getString(R.string.app_name)
    }

    private fun onItemClick(info: AppInfo) {
        if (multiselect) {
            if (info in selectedList) selectedList.remove(info) else selectedList.add(info)
            updateCurrentList(); updateBarTitle()
            return
        }
        if (info.applicationInfo == null) {
            Snackbar.make(activity.fab, R.string.app_not_installed, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_remove_home) { removeCheckedApp(info.packageName) }.show()
            return
        }
        launchApp(info.packageName)
    }

    private fun onItemLongClick(info: AppInfo): Boolean {
        if (info.applicationInfo == null && (!multiselect || info !in selectedList)) {
            exportToClipboard(listOf(info)); return true
        }
        if (info in selectedList) { onMultiSelect(); return true }
        val pkg    = info.packageName
        val frozen = AppManager.isAppFrozen(pkg)
        val action = getString(if (frozen) R.string.action_unfreeze else R.string.action_freeze)
        MaterialAlertDialogBuilder(activity).setTitle(info.name).setItems(
            resources.getStringArray(R.array.home_action_entries).filter {
                (it != getString(R.string.action_freeze) || !frozen)
                    && (it != getString(R.string.action_unfreeze) || frozen)
                    && (it != getString(R.string.action_pin) || !info.pinned)
                    && (it != getString(R.string.action_unpin) || info.pinned)
                    && (it != getString(R.string.action_whitelist) || !info.whitelisted)
                    && (it != getString(R.string.action_remove_whitelist) || info.whitelisted)
                    && (it != getString(R.string.action_unfreeze_remove_home) || frozen)
            }.toTypedArray()
        ) { _, which ->
            when (which) {
                0 -> launchApp(pkg)
                1 -> setListFrozen(!frozen, listOf(info))
                2 -> {
                    val values  = resources.getIntArray(R.array.deferred_task_values)
                    val entries = arrayOfNulls<String>(values.size)
                    values.forEachIndexed { i, v ->
                        entries[i] = resources.getQuantityString(R.plurals.deferred_task_entry, v, v)
                    }
                    MaterialAlertDialogBuilder(activity).setTitle(R.string.action_deferred_task)
                        .setItems(entries) { _, i ->
                            HWork.setDeferredFrozen(pkg, !frozen, values[i].toLong())
                            Snackbar.make(
                                activity.fab,
                                resources.getQuantityString(R.plurals.msg_deferred_task, values[i], values[i], action, info.name),
                                Snackbar.LENGTH_INDEFINITE
                            ).setAction(R.string.action_undo) { HWork.cancelWork(pkg) }.show()
                        }.setNegativeButton(android.R.string.cancel, null).show()
                }
                3 -> { info.pinned = !info.pinned; HailData.saveApps(); updateCurrentList() }
                4 -> { info.whitelisted = !info.whitelisted; HailData.saveApps(); updateCurrentList() }
                5 -> tagDialog(info)
                6 -> if (tabs.tabCount > 1)
                    MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.action_unfreeze_tag)
                        .setItems(HailData.tags.map { it.first }.toTypedArray()) { _, index ->
                            HShortcuts.addPinShortcut(info, pkg, info.name,
                                HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg)
                                    .addTag(HailData.tags[index].first))
                        }.setPositiveButton(R.string.action_skip) { _, _ ->
                            HShortcuts.addPinShortcut(info, pkg, info.name,
                                HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg))
                        }.setNegativeButton(android.R.string.cancel, null).show()
                else HShortcuts.addPinShortcut(info, pkg, info.name,
                    HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg))
                7 -> exportToClipboard(listOf(info))
                8 -> removeCheckedApp(pkg)
                9 -> {
                    setListFrozen(false, listOf(info), false)
                    if (!AppManager.isAppFrozen(pkg)) removeCheckedApp(pkg)
                }
            }
        }.setNeutralButton(R.string.action_details) { _, _ ->
            HUI.startActivity(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, HPackages.packageUri(pkg))
        }.setNegativeButton(android.R.string.cancel, null).show()
        return true
    }

    private fun tagDialog(info: AppInfo) {
        val checked = BooleanArray(HailData.tags.size) { HailData.tags[it].second in info.tagIdList }
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set)
            .setMultiChoiceItems(HailData.tags.map { it.first }.toTypedArray(), checked) { _, i, c -> checked[i] = c }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                info.tagIdList.clear()
                checked.forEachIndexed { i, c -> if (c) info.tagIdList.add(HailData.tags[i].second) }
                if (info.tagIdList.isEmpty()) removeCheckedApp(info.packageName, false)
                HailData.saveApps(); updateCurrentList()
            }.setNeutralButton(R.string.action_tag_add) { _, _ -> showTagDialog(listOf(info)) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun deselect(update: Boolean = true) {
        selectedList.clear()
        if (!update) return
        updateCurrentList(); updateBarTitle()
    }

    private fun onMultiSelect() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(getString(R.string.msg_selected, selectedList.size.toString()))
            .setItems(intArrayOf(
                R.string.action_freeze, R.string.action_unfreeze, R.string.action_tag_set,
                R.string.action_export_clipboard, R.string.action_remove_home,
                R.string.action_unfreeze_remove_home
            ).map { getString(it) }.toTypedArray()) { _, which ->
                when (which) {
                    0 -> { setListFrozen(true,  selectedList.toList(), false); deselect() }
                    1 -> { setListFrozen(false, selectedList.toList(), false); deselect() }
                    2 -> triStateTagDialog()
                    3 -> { exportToClipboard(selectedList.toList()); deselect() }
                    4 -> {
                        selectedList.toList().forEach { removeCheckedApp(it.packageName, false) }
                        HailData.saveApps(); deselect()
                    }
                    5 -> {
                        setListFrozen(false, selectedList.toList(), false)
                        selectedList.toList().forEach {
                            if (!AppManager.isAppFrozen(it.packageName)) removeCheckedApp(it.packageName, false)
                        }
                        HailData.saveApps(); deselect()
                    }
                }
            }.setNegativeButton(R.string.action_deselect) { _, _ -> deselect() }
            .setNeutralButton(R.string.action_select_all) { _, _ ->
                selectedList.addAll(displayList.filterNot { it in selectedList })
                updateCurrentList(); updateBarTitle(); onMultiSelect()
            }.show()
    }

    private fun triStateTagDialog() {
        val init = Array(HailData.tags.size) { i ->
            val id = HailData.tags[i].second
            when (selectedList.count { id in it.tagIdList }) {
                selectedList.size -> ToggleableState.On
                0                 -> ToggleableState.Off
                else              -> ToggleableState.Indeterminate
            }
        }
        val states = mutableStateListOf(*init)
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set)
            .setView(ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { AppTheme { TriStateTagList(init, states) } }
            })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // FIX: Renamed lambda variable from 'app' to 'appInfo' to stop
                // shadowing the top-level com.aistra.hail.HailApp.Companion.app reference.
                selectedList.toList().forEach { appInfo ->
                    states.forEachIndexed { i, s ->
                        val id = HailData.tags[i].second
                        when (s) {
                            ToggleableState.On  -> if (id !in appInfo.tagIdList) appInfo.tagIdList.add(id)
                            ToggleableState.Off -> appInfo.tagIdList.remove(id)
                            else -> {}
                        }
                    }
                    if (appInfo.tagIdList.isEmpty()) removeCheckedApp(appInfo.packageName, false)
                }
                HailData.saveApps(); deselect()
            }.setNeutralButton(R.string.action_tag_add) { _, _ -> showTagDialog(selectedList.toList()) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun launchApp(packageName: String) {
        if (AppManager.isAppFrozen(packageName) && AppManager.setAppFrozen(packageName, false))
            updateCurrentList()
        app.packageManager.getLaunchIntentForPackage(packageName)?.let {
            HShortcuts.addDynamicShortcut(packageName)
            startActivity(it)
        } ?: HUI.showToast(R.string.activity_not_found)
    }

    private fun setListFrozen(
        frozen: Boolean,
        list: List<AppInfo> = HailData.checkedList,
        update: Boolean = true
    ) {
        if (HailData.workingMode == HailData.MODE_DEFAULT) {
            MaterialAlertDialogBuilder(activity).setMessage(R.string.msg_guide)
                .setPositiveButton(android.R.string.ok, null).show()
            return
        } else if (HailData.workingMode == HailData.MODE_SHIZUKU_HIDE) {
            runCatching { HShizuku.isRoot }.onSuccess {
                if (!it) {
                    MaterialAlertDialogBuilder(activity).setMessage(R.string.shizuku_hide_adb)
                        .setPositiveButton(android.R.string.ok, null).show()
                    return
                }
            }
        }
        val filtered = list.filter { AppManager.isAppFrozen(it.packageName) != frozen }
        when (val result = AppManager.setListFrozen(frozen, *filtered.toTypedArray())) {
            null -> HUI.showToast(R.string.permission_denied)
            else -> {
                if (update) updateCurrentList()
                HUI.showToast(if (frozen) R.string.msg_freeze else R.string.msg_unfreeze, result)
            }
        }
    }

    private fun showTagDialog(list: List<AppInfo>? = null) {
        val db = DialogInputBinding.inflate(layoutInflater)
        db.inputLayout.setHint(R.string.tag)
        list ?: db.editText.setText(tag.first)
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (list != null) R.string.action_tag_add else R.string.action_tag_set)
            .setView(db.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val tagName = db.editText.text.toString()
                val tagId   = tagName.hashCode()
                if (HailData.tags.any { it.first == tagName || it.second == tagId }) return@setPositiveButton
                if (list != null) {
                    HailData.tags.add(tagName to tagId)
                    pagerAdapter.notifyItemInserted(pagerAdapter.itemCount - 1)
                    if (query.isEmpty() && tabs.tabCount == 2) tabs.isVisible = true
                    if (list == selectedList) triStateTagDialog() else tagDialog(list.first())
                } else {
                    val pos       = tabs.selectedTabPosition
                    val isDefault = pos == 0
                    val oldId     = HailData.tags[pos].second
                    HailData.tags[pos] = tagName to if (isDefault) 0 else tagId
                    if (!isDefault) {
                        displayList.forEach { a ->
                            val idx = a.tagIdList.indexOf(oldId)
                            if (idx != -1) a.tagIdList[idx] = tagId
                        }
                        HailData.saveApps()
                    }
                    pagerAdapter.notifyItemChanged(pos)
                }
                HailData.saveTags()
            }.apply {
                val pos = tabs.selectedTabPosition
                if (list != null || pos == 0) return@apply
                setNeutralButton(R.string.action_tag_remove) { _, _ ->
                    val removeId = HailData.tags[pos].second
                    displayList.forEach { a ->
                        if (a.tagIdList.remove(removeId) && a.tagIdList.isEmpty())
                            removeCheckedApp(a.packageName, false)
                    }
                    HailData.tags.removeAt(pos)
                    pagerAdapter.notifyItemRemoved(pos)
                    if (tabs.tabCount == 1) tabs.isVisible = false
                    HailData.saveApps(); HailData.saveTags()
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun exportToClipboard(list: List<AppInfo>) {
        if (list.isEmpty()) return
        HUI.copyText(
            if (list.size > 1) JSONArray().run { list.forEach { put(it.packageName) }; toString() }
            else list[0].packageName
        )
        HUI.showToast(R.string.msg_exported, if (list.size > 1) list.size.toString() else list[0].name)
    }

    private fun importFromClipboard() = runCatching {
        val str  = HUI.pasteText() ?: throw IllegalArgumentException()
        val json = if (str.contains('['))
            JSONArray(str.substring(str.indexOf('[')..str.indexOf(']', str.indexOf('[')))) else
            JSONArray().put(str)
        var count = 0
        for (idx in 0 until json.length()) {
            val pkg = json.getString(idx)
            if (HPackages.getApplicationInfoOrNull(pkg) != null && !HailData.isChecked(pkg)) {
                HailData.addCheckedApp(pkg, tag.second, false); count++
            }
        }
        if (count > 0) { HailData.saveApps(); updateCurrentList() }
        HUI.showToast(getString(R.string.msg_imported, count.toString()))
    }

    private suspend fun importFrozenApp() = withContext(Dispatchers.IO) {
        HPackages.getInstalledApplications().map { it.packageName }
            .filter { AppManager.isAppFrozen(it) && !HailData.isChecked(it) }
            .onEach { HailData.addCheckedApp(it, tag.second, false) }.size
    }

    private fun removeCheckedApp(packageName: String, save: Boolean = true) {
        HailData.removeCheckedApp(packageName, save)
        if (save) updateCurrentList()
    }

    private fun MenuItem.updateIcon() = icon?.setTint(
        com.google.android.material.color.MaterialColors.getColor(
            activity.findViewById(R.id.toolbar),
            if (multiselect) androidx.appcompat.R.attr.colorPrimary
            else com.google.android.material.R.attr.colorOnSurface
        )
    )

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_multiselect -> {
                multiselect = !multiselect
                item.updateIcon()
                if (multiselect) { updateBarTitle(); HUI.showToast(R.string.tap_to_select) }
                else deselect()
            }
            R.id.action_freeze_current         -> setListFrozen(true, displayList.toList().filterNot { it.whitelisted })
            R.id.action_unfreeze_current       -> setListFrozen(false, displayList.toList())
            R.id.action_freeze_all             -> setListFrozen(true)
            R.id.action_unfreeze_all           -> setListFrozen(false)
            R.id.action_freeze_non_whitelisted -> setListFrozen(true, HailData.checkedList.filterNot { it.whitelisted })
            R.id.action_import_clipboard       -> importFromClipboard()
            R.id.action_import_frozen          -> lifecycleScope.launch {
                val size = importFrozenApp()
                if (size > 0) { HailData.saveApps(); updateCurrentList() }
                HUI.showToast(getString(R.string.msg_imported, size.toString()))
            }
            R.id.action_export_current -> exportToClipboard(displayList.toList())
            R.id.action_export_all     -> exportToClipboard(HailData.checkedList)
        }
        return false
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_home, menu)
        val sv = menu.findItem(R.id.action_search).actionView as SearchView
        if (HailData.nineKeySearch) {
            sv.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                .inputType = InputType.TYPE_CLASS_PHONE
        }
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            private var inited = false
            override fun onQueryTextChange(newText: String): Boolean {
                if (inited) {
                    query = newText
                    tabs.isVisible = query.isEmpty() && tabs.tabCount > 1
                    updateCurrentList()
                } else inited = true
                return true
            }
            override fun onQueryTextSubmit(query: String): Boolean = true
        })
        menu.findItem(R.id.action_multiselect).updateIcon()
    }
}
