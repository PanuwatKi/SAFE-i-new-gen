package com.panuwatki.safe.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panuwatki.safe.calls.CallRecordings

sealed interface Screen {
    data object Home : Screen
    data object Listen : Screen
    data class Analyze(val uri: Uri) : Screen
    data object History : Screen
    data class Record(val id: Long) : Screen
    data object Settings : Screen
    data object Privacy : Screen
    data object Numbers : Screen
    data class NumberEdit(val number: String?) : Screen
    data object Official : Screen
}

/** สิ่งที่ MainActivity ส่งเข้ามาจาก Intent (แชร์ไฟล์เสียง / แตะการแจ้งเตือน) */
sealed interface LaunchRequest {
    data class AnalyzeUri(val uri: Uri) : LaunchRequest
    data class OpenRecord(val id: Long) : LaunchRequest
    data class ReportNumber(val number: String) : LaunchRequest
}

@Composable
fun AppRoot(launchRequest: LaunchRequest?, onLaunchHandled: () -> Unit, vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val resumeTick = remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick.intValue++ }

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    LaunchedEffect(launchRequest, vm.hasConsent) {
        if (launchRequest == null || !vm.hasConsent) return@LaunchedEffect
        when (launchRequest) {
            is LaunchRequest.AnalyzeUri -> { vm.resetFile(); go(Screen.Analyze(launchRequest.uri)) }
            is LaunchRequest.OpenRecord -> go(Screen.Record(launchRequest.id))
            is LaunchRequest.ReportNumber -> go(Screen.NumberEdit(launchRequest.number))
        }
        onLaunchHandled()
    }

    // ---------- ขอสิทธิ์ต่าง ๆ ----------
    val autoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[CallRecordings.audioPermission()] == true) {
            vm.updateAutoRecording(true)
        } else {
            toast(context, "ต้องอนุญาตให้อ่านไฟล์เสียง SAFE ถึงจะหาไฟล์อัดเสียงสายได้")
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resumeTick.intValue++
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.resetFile(); go(Screen.Analyze(uri)) }
    }

    val actions = remember {
        HomeActions(
            requestCallScreening = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = context.getSystemService(RoleManager::class.java)
                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    } else {
                        toast(context, "เครื่องนี้ไม่รองรับการคัดกรองสาย")
                    }
                }
            },
            enableAutoRecording = {
                val perms = buildList {
                    add(CallRecordings.audioPermission())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                autoPermissions.launch(perms.toTypedArray())
            },
            disableAutoRecording = { vm.updateAutoRecording(false) },
            pickAudioFile = { pickAudio.launch(arrayOf("audio/*")) },
        )
    }

    Scaffold { padding ->
        val inner: PaddingValues = padding
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (!vm.hasConsent) {
                when (stack.lastOrNull()) {
                    Screen.Privacy -> PrivacyScreen(onBack = ::back)
                    else -> ConsentScreen(onAccept = vm::acceptConsent, onReadPolicy = { go(Screen.Privacy) })
                }
                return@Column
            }
            when (val s = stack.last()) {
                Screen.Home -> HomeScreen(vm, actions, resumeTick.intValue, onNavigate = ::go)
                Screen.Listen -> ListenScreen(vm, onBack = ::back, onOpenRecord = { go(Screen.Record(it)) })
                is Screen.Analyze -> AnalyzeScreen(vm, s.uri, onBack = ::back, onOpenRecord = { go(Screen.Record(it)) })
                Screen.History -> HistoryScreen(vm, onBack = ::back, onOpenRecord = { go(Screen.Record(it)) })
                is Screen.Record -> RecordScreen(vm, s.id, onBack = ::back)
                Screen.Settings -> SettingsScreen(
                    vm, actions,
                    onBack = ::back,
                    onPrivacy = { go(Screen.Privacy) },
                    onDataDeleted = { stack.clear(); stack.add(Screen.Home) },
                )
                Screen.Privacy -> PrivacyScreen(onBack = ::back)
                Screen.Numbers -> NumbersScreen(
                    vm, onBack = ::back,
                    onEdit = { go(Screen.NumberEdit(it)) },
                    onOfficial = { go(Screen.Official) },
                )
                is Screen.NumberEdit -> NumberEditScreen(vm, s.number, onBack = ::back)
                Screen.Official -> OfficialNumbersScreen(onBack = ::back)
            }
        }
    }
}

class HomeActions(
    val requestCallScreening: () -> Unit,
    val enableAutoRecording: () -> Unit,
    val disableAutoRecording: () -> Unit,
    val pickAudioFile: () -> Unit,
)

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

fun isCallScreeningRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
