package com.huizhi.rubikey

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huizhi.rubikey.accessibility.RubiKeyAccessibilityService
import com.huizhi.rubikey.ble.CubeBleService
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
    val repository = remember { ActionMappingRepository(context) }
    var loaded by remember { mutableStateOf(repository.load()) }
    var editedMove by remember { mutableStateOf<CubeMove?>(null) }
    val bleState by CubeBleService.state.collectAsState()
    val accessibilityEnabled by RubiKeyAccessibilityService.enabled.collectAsState()
    val droppedActions by RubiKeyAccessibilityService.droppedActions.collectAsState()
    val gestureError by RubiKeyAccessibilityService.lastError.collectAsState()
    val permissions = buildList {
        add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        CubeBleService.command(context, CubeBleService.ACTION_SCAN)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("RubiKey") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                StatusSection(
                    bleText = bleState.connectedDevice?.let { "已连接 ${it.name}" } ?: if (bleState.scanning) "正在扫描" else "未连接",
                    accessibilityEnabled = accessibilityEnabled,
                    onScan = { permissionLauncher.launch(permissions.toTypedArray()) },
                    onDisconnect = { CubeBleService.command(context, CubeBleService.ACTION_DISCONNECT) },
                    onAccessibilitySettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
                bleState.errorMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
                loaded.errorMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
                gestureError?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
                if (droppedActions > 0) Text("手势队列已丢弃 $droppedActions 项", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                bleState.lastMove?.let { move ->
                    Text("最近转动：${move.notation}  ${bleState.lastMoveElapsedMs ?: 0} ms", modifier = Modifier.padding(16.dp))
                }
                Text("发现的 Moyu32", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
            }
            items(bleState.devices, key = { it.address }) { device ->
                Row(modifier = Modifier.fillMaxWidth().clickable { CubeBleService.command(context, CubeBleService.ACTION_CONNECT, device.address) }.padding(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) { Text(device.name); Text(device.address, style = MaterialTheme.typography.bodySmall) }
                    Text("连接", color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
            item { Text("转动映射", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp)) }
            items(CubeMove.entries, key = { it.name }) { move ->
                MappingRow(move, loaded.mapping[move]) { editedMove = move }
                HorizontalDivider()
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
}

@Composable
private fun StatusSection(
    bleText: String,
    accessibilityEnabled: Boolean,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onAccessibilitySettings: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("蓝牙：$bleText")
        Row { Button(onClick = onScan) { Text("扫描") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = onDisconnect) { Text("断开") } }
        Text("辅助功能：${if (accessibilityEnabled) "已启用" else "未启用"}")
        OutlinedButton(onClick = onAccessibilitySettings) { Text("打开辅助功能设置") }
    }
}

@Composable
private fun MappingRow(move: CubeMove, action: CubeAction, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(move.notation, style = MaterialTheme.typography.titleMedium)
        Text(action.label())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingEditor(move: CubeMove, action: CubeAction, onSave: (CubeAction) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(action) { mutableStateOf(action) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${move.notation} 的动作", style = MaterialTheme.typography.titleLarge)
            ActionChoice("无动作", selected is CubeAction.None) { selected = CubeAction.None }
            ActionChoice("点击", selected is CubeAction.Tap) { selected = selected as? CubeAction.Tap ?: CubeAction.Tap(0.5f, 0.5f) }
            ActionChoice("向上滑动", selected is CubeAction.Swipe && (selected as CubeAction.Swipe).endY < (selected as CubeAction.Swipe).startY) { selected = CubeAction.Swipe(0.5f, 0.7f, 0.5f, 0.3f) }
            ActionChoice("向下滑动", selected is CubeAction.Swipe && (selected as CubeAction.Swipe).endY > (selected as CubeAction.Swipe).startY) { selected = CubeAction.Swipe(0.5f, 0.3f, 0.5f, 0.7f) }
            ActionChoice("向左滑动", selected is CubeAction.Swipe && (selected as CubeAction.Swipe).endX < (selected as CubeAction.Swipe).startX) { selected = CubeAction.Swipe(0.7f, 0.5f, 0.3f, 0.5f) }
            ActionChoice("向右滑动", selected is CubeAction.Swipe && (selected as CubeAction.Swipe).endX > (selected as CubeAction.Swipe).startX) { selected = CubeAction.Swipe(0.3f, 0.5f, 0.7f, 0.5f) }
            if (selected is CubeAction.Tap) CoordinatePad(selected as CubeAction.Tap) { selected = it }
            Button(onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionChoice(text: String, checked: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected = checked, onClick = onClick); Text(text)
    }
}

@Composable
private fun CoordinatePad(action: CubeAction.Tap, onChange: (CubeAction.Tap) -> Unit) {
    Text("点击位置")
    val markerColor = MaterialTheme.colorScheme.primary
    val padColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier.fillMaxWidth().height(180.dp).pointerInput(Unit) {
            detectTapGestures { offset -> onChange(CubeAction.Tap(offset.x / size.width, offset.y / size.height)) }
        },
    ) {
        drawRect(padColor)
        drawCircle(markerColor, 12.dp.toPx(), Offset(action.x * size.width, action.y * size.height))
    }
}

private fun CubeAction.label(): String = when (this) {
    CubeAction.None -> "无动作"
    is CubeAction.Tap -> "点击"
    is CubeAction.Swipe -> "滑动"
}
