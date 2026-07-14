package com.huizhi.rubikey

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huizhi.rubikey.accessibility.RubiKeyAccessibilityService
import com.huizhi.rubikey.ble.CubeBleService
import com.huizhi.rubikey.cube.CubeConnectionStatus
import com.huizhi.rubikey.cube.CubeDevice
import com.huizhi.rubikey.cube.CubeMove
import com.huizhi.rubikey.mapping.ActionMapping
import com.huizhi.rubikey.mapping.ActionMappingRepository
import com.huizhi.rubikey.mapping.CubeAction
import com.huizhi.rubikey.ui.theme.RubiKeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RubiKeyTheme { RubiKeyApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RubiKeyApp() {
    val context = LocalContext.current
    val repositoryUrl = stringResource(R.string.github_repository_url)
    val repository = remember { ActionMappingRepository(context) }
    var loaded by remember { mutableStateOf(repository.load()) }
    var editedMove by remember { mutableStateOf<CubeMove?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val bleState by CubeBleService.state.collectAsState()
    val accessibilityEnabled by RubiKeyAccessibilityService.enabled.collectAsState()
    val droppedActions by RubiKeyAccessibilityService.droppedActions.collectAsState()
    val gestureError by RubiKeyAccessibilityService.lastError.collectAsState()
    val bluetoothPermissions = remember {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    }

    val startScan: () -> Unit = {
        permissionMessage = null
        CubeBleService.command(context, CubeBleService.ACTION_SCAN)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        startScan()
        if (!granted) permissionMessage = "通知未授权，连接期间的系统通知可能受限"
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (bluetoothPermissions.all { grants[it] == true || context.hasPermission(it) }) {
            if (Build.VERSION.SDK_INT >= 33 && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startScan()
            }
        } else {
            permissionMessage = "需要允许“附近设备”权限才能扫描和连接魔方"
        }
    }
    val onScan: () -> Unit = {
        when {
            bluetoothPermissions.any { !context.hasPermission(it) } -> bluetoothPermissionLauncher.launch(bluetoothPermissions)
            Build.VERSION.SDK_INT >= 33 && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS) -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> startScan()
        }
    }

    val connectedDevice = bleState.connectedDevice
    val connectionText = when (connectedDevice?.connectionStatus) {
        CubeConnectionStatus.CONNECTING -> "正在连接 ${connectedDevice.name}"
        CubeConnectionStatus.CONNECTED -> "已连接 ${connectedDevice.name}"
        else -> if (bleState.scanning) "正在扫描附近设备" else "未连接"
    }
    val isConnecting = connectedDevice?.connectionStatus == CubeConnectionStatus.CONNECTING
    val isConnected = connectedDevice?.connectionStatus == CubeConnectionStatus.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RubiKey") },
                actions = { TextButton(onClick = { showAbout = true }) { Text("关于") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                RunStatusSection(
                    connectionText = connectionText,
                    scanning = bleState.scanning,
                    connecting = isConnecting,
                    connected = isConnected,
                    accessibilityEnabled = accessibilityEnabled,
                    permissionMessage = permissionMessage,
                    onScan = onScan,
                    onStopScan = { CubeBleService.command(context, CubeBleService.ACTION_STOP_SCAN) },
                    onDisconnect = { CubeBleService.command(context, CubeBleService.ACTION_DISCONNECT) },
                    onAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
            if (!isConnected && (bleState.scanning || bleState.devices.isNotEmpty())) {
                item {
                    DeviceSection(
                        devices = bleState.devices,
                        scanning = bleState.scanning,
                        onConnect = { device ->
                            CubeBleService.command(context, CubeBleService.ACTION_CONNECT, device.address)
                        },
                    )
                }
            }
            item {
                MappingSection(
                    mapping = loaded.mapping,
                    onEdit = { editedMove = it },
                )
            }
            val diagnostics = buildDiagnostics(
                bleError = bleState.errorMessage,
                mappingError = loaded.errorMessage,
                gestureError = gestureError,
                lastMove = bleState.lastMove,
                elapsedMs = bleState.lastMoveElapsedMs,
                droppedActions = droppedActions,
            )
            if (diagnostics.isNotEmpty()) {
                item { DiagnosticsSection(diagnostics) }
            }
        }
    }
    editedMove?.let { move ->
        MappingEditor(
            move = move,
            action = loaded.mapping[move],
            onSave = { action ->
                val next = loaded.mapping.with(move, action)
                repository.save(next)
                loaded = ActionMappingRepository.LoadResult(next)
                editedMove = null
            },
            onDismiss = { editedMove = null },
        )
    }
    if (showAbout) {
        AboutDialog(
            repositoryUrl = repositoryUrl,
            onRepositoryClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repositoryUrl)))
            },
            onDismiss = { showAbout = false },
        )
    }
}

@Composable
private fun AboutDialog(
    repositoryUrl: String,
    onRepositoryClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AboutInfoRow(label = "应用", value = "RubiKey")
                AboutInfoRow(label = "作者", value = "huizhiLLL")
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("开源仓库", style = MaterialTheme.typography.labelLarge)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRepositoryClick)
                            .semantics { contentDescription = "打开 GitHub 开源仓库" },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = "GitHub",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                repositoryUrl,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true,
                            )
                        }
                    }
                    Text(
                        "如果这个项目对你有帮助，希望能点个 Star 支持一下～",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("开源协议", style = MaterialTheme.typography.labelLarge)
                    Text("GPLv3", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RunStatusSection(
    connectionText: String,
    scanning: Boolean,
    connecting: Boolean,
    connected: Boolean,
    accessibilityEnabled: Boolean,
    permissionMessage: String?,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDisconnect: () -> Unit,
    onAccessibilitySettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("状态")
        StatusLine("蓝牙", connectionText)
        if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        when {
            scanning -> OutlinedButton(onClick = onStopScan, modifier = Modifier.fillMaxWidth()) { Text("停止扫描") }
            connecting -> OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("取消连接") }
            connected -> OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("断开连接") }
            else -> Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("扫描附近设备") }
        }
        permissionMessage?.let { message -> InlineMessage("权限", message) }
        HorizontalDivider()
        StatusLine("辅助功能", if (accessibilityEnabled) "已启用" else "未启用")
        if (!accessibilityEnabled) {
            OutlinedButton(onClick = onAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Text("前往系统设置启用 RubiKey")
            }
        }
    }
}

@Composable
private fun DeviceSection(
    devices: List<CubeDevice>,
    scanning: Boolean,
    onConnect: (CubeDevice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading("附近设备")
        if (devices.isEmpty()) {
            Text(
                "正在搜索附近可连接的设备",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            devices.forEach { device ->
                DeviceRow(device = device, onClick = { onConnect(device) })
            }
        }
    }
}

@Composable
private fun DeviceRow(device: CubeDevice, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "连接 ${device.name}" },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("连接", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MappingSection(mapping: ActionMapping, onEdit: (CubeMove) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading("转动映射")
        CubeMove.entries.chunked(2).forEach { moves ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                moves.forEach { move ->
                    MappingTile(
                        move = move,
                        action = mapping[move],
                        modifier = Modifier.weight(1f),
                        onClick = { onEdit(move) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MappingTile(move: CubeMove, action: CubeAction, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${move.notation}，${action.label()}" },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(move.notation, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                action.label(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsSection(diagnostics: List<DiagnosticItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading("诊断信息", "仅在有输入记录或需要处理的问题时显示")
        diagnostics.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.label,
                    modifier = Modifier.width(72.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    item.value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InlineMessage(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingEditor(
    move: CubeMove,
    action: CubeAction,
    onSave: (CubeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(action) { mutableStateOf(action) }
    val selectedMode = selected.mode()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${move.notation} 的动作", style = MaterialTheme.typography.titleLarge)
                Text(
                    "一次转动只执行一个全局操作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ActionMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { selected = mode.defaultAction(selected) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ActionMode.entries.size),
                        label = { Text(mode.label) },
                    )
                }
            }
            when (selected) {
                is CubeAction.Tap -> CoordinatePad(selected as CubeAction.Tap) { selected = it }
                is CubeAction.Swipe -> SwipePresetSelector(selected as CubeAction.Swipe) { selected = it }
                CubeAction.None -> Unit
            }
            Button(onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth()) { Text("保存动作") }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("取消") }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SwipePresetSelector(selected: CubeAction.Swipe, onChange: (CubeAction.Swipe) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("滑动方向", style = MaterialTheme.typography.titleSmall)
        Text(
            "使用固定的中轴预设，不提供自定义坐标",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwipePreset.entries.chunked(2).forEach { presets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    if (preset.matches(selected)) {
                        Button(
                            onClick = { onChange(preset.action) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(preset.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onChange(preset.action) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(preset.label)
                        }
                    }
                }
            }
        }
        val activePreset = SwipePreset.entries.firstOrNull { it.matches(selected) }
        if (activePreset == null) {
            Text(
                "已将非预设滑动保留为当前值，保存时请选择一个预设方向",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CoordinatePad(action: CubeAction.Tap, onChange: (CubeAction.Tap) -> Unit) {
    val padColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("点击位置", style = MaterialTheme.typography.titleSmall)
        Text(
            "按目标竖屏游戏的相对位置选择点击点",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .aspectRatio(9f / 16f)
                .align(Alignment.CenterHorizontally)
                .semantics { contentDescription = "点击位置选择区域" }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onChange(CubeAction.Tap(offset.x / size.width, offset.y / size.height))
                    }
                },
        ) {
            drawRect(padColor)
            drawRect(
                color = outlineColor,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = markerColor,
                radius = 10.dp.toPx(),
                center = Offset(action.x * size.width, action.y * size.height),
            )
        }
    }
}

private fun buildDiagnostics(
    bleError: String?,
    mappingError: String?,
    gestureError: String?,
    lastMove: CubeMove?,
    elapsedMs: Int?,
    droppedActions: Long,
): List<DiagnosticItem> = buildList {
    lastMove?.let { move ->
        add(DiagnosticItem("最近转动", move.notation))
        elapsedMs?.let { add(DiagnosticItem("处理延迟", "$it ms")) }
    }
    if (droppedActions > 0) add(DiagnosticItem("手势队列", "已丢弃 $droppedActions 项", isError = true))
    bleError?.takeUnless { it == "已断开" || it == "服务已停止" }?.let {
        add(DiagnosticItem("蓝牙", it, isError = true))
    }
    mappingError?.let { add(DiagnosticItem("映射", it, isError = true)) }
    gestureError?.let { add(DiagnosticItem("手势", it, isError = true)) }
}

private data class DiagnosticItem(val label: String, val value: String, val isError: Boolean = false)

private enum class ActionMode(val label: String) {
    NONE("无动作"),
    TAP("点击"),
    SWIPE("滑动");

    fun defaultAction(current: CubeAction): CubeAction = when (this) {
        NONE -> CubeAction.None
        TAP -> current as? CubeAction.Tap ?: CubeAction.Tap(0.5f, 0.5f)
        SWIPE -> current as? CubeAction.Swipe ?: SwipePreset.UP.action
    }
}

private enum class SwipePreset(
    val label: String,
    val action: CubeAction.Swipe,
) {
    UP("向上滑动", CubeAction.Swipe(0.5f, 0.7f, 0.5f, 0.3f)),
    DOWN("向下滑动", CubeAction.Swipe(0.5f, 0.3f, 0.5f, 0.7f)),
    LEFT("向左滑动", CubeAction.Swipe(0.7f, 0.5f, 0.3f, 0.5f)),
    RIGHT("向右滑动", CubeAction.Swipe(0.3f, 0.5f, 0.7f, 0.5f));

    fun matches(value: CubeAction.Swipe): Boolean = action == value
}

private fun CubeAction.mode(): ActionMode = when (this) {
    CubeAction.None -> ActionMode.NONE
    is CubeAction.Tap -> ActionMode.TAP
    is CubeAction.Swipe -> ActionMode.SWIPE
}

private fun CubeAction.label(): String = when (this) {
    CubeAction.None -> "未配置"
    is CubeAction.Tap -> "点击"
    is CubeAction.Swipe -> SwipePreset.entries.firstOrNull { it.matches(this) }?.label ?: "滑动"
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
