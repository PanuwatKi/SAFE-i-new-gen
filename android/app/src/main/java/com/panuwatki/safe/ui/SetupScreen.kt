package com.panuwatki.safe.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.panuwatki.safe.calls.CallRecordings
import com.panuwatki.safe.notify.Alerts
import com.panuwatki.safe.setup.SetupGuide
import com.panuwatki.safe.speech.FileTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SetupStep { WELCOME, NOTIFICATIONS, CALL_SCREENING, RECORDING, AUTO_CHECK, BACKGROUND, MICROPHONE, DONE }

/**
 * ตั้งค่า SAFE แบบทีละขั้น — ออกแบบให้ลูกหลานช่วยตั้งให้พ่อแม่ได้ใน ~3 นาที
 * แสดงอัตโนมัติครั้งแรกหลังยอมรับนโยบาย และเปิดใหม่ได้จากหน้าแรก/หน้าตั้งค่า
 */
@Composable
fun SetupScreen(vm: MainViewModel, resumeTick: Int, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val refresh = resumeTick + tick

    val steps = remember {
        buildList {
            add(SetupStep.WELCOME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(SetupStep.NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(SetupStep.CALL_SCREENING)
            add(SetupStep.RECORDING)
            add(SetupStep.AUTO_CHECK)
            add(SetupStep.BACKGROUND)
            add(SetupStep.MICROPHONE)
            add(SetupStep.DONE)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val brand = remember { SetupGuide.detect(Build.MANUFACTURER ?: "", defaultDialerPackage(context)) }

    // ---- สถานะปัจจุบัน (อ่านใหม่ทุกครั้งที่กลับเข้าแอป หรือหลังขอสิทธิ์) ----
    val notifOk = remember(refresh) { Alerts.canNotify(context) }
    val roleOk = remember(refresh) { isCallScreeningRoleHeld(context) }
    val audioOk = remember(refresh) { CallRecordings.hasPermission(context) }
    val micOk = remember(refresh) { granted(context, Manifest.permission.RECORD_AUDIO) }
    val batteryOk = remember(refresh) { ignoringBatteryOptimizations(context) }
    val autoOn = vm.autoRecording && audioOk
    var recordingTest by remember { mutableStateOf<String?>(null) }

    // ---- ขอสิทธิ์ ----
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.updateAutoRecording(true)
        tick++
    }

    ScreenColumn {
        Text("🧭 ตั้งค่า SAFE ทีละขั้น", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        val total = steps.size - 2
        if (step != SetupStep.WELCOME && step != SetupStep.DONE) {
            Text("ขั้นที่ $index จาก $total", style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(progress = { index / steps.lastIndex.toFloat() }, modifier = Modifier.fillMaxWidth())

        when (step) {
            SetupStep.WELCOME -> SectionCard("ยินดีต้อนรับ") {
                Text("ใช้เวลาประมาณ 3 นาที ลูกหลานช่วยตั้งให้คุณพ่อคุณแม่ได้", style = MaterialTheme.typography.bodyLarge)
                Text("แต่ละขั้นข้ามได้ และกลับมาตั้งใหม่ได้ทุกเมื่อจากหน้าแรก", style = MaterialTheme.typography.bodyLarge)
                Text("มือถือเครื่องนี้: ${brand.displayName}", style = MaterialTheme.typography.bodyMedium)
            }

            SetupStep.NOTIFICATIONS -> SectionCard("อนุญาตการแจ้งเตือน") {
                Text("SAFE ต้องแจ้งเตือนได้ ถึงจะเตือนตอนสายเข้าหรือหลังวางสายได้", style = MaterialTheme.typography.bodyLarge)
                StatusLine(notifOk, if (notifOk) "อนุญาตแล้ว" else "ยังไม่อนุญาต")
                if (!notifOk) {
                    BigButton("อนุญาตการแจ้งเตือน", {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                    OpenSettingsHint(context)
                }
            }

            SetupStep.CALL_SCREENING -> SectionCard("ตั้ง SAFE เป็นแอปคัดกรองสาย") {
                Text("ให้ SAFE เห็นเบอร์ที่ไม่อยู่ในรายชื่อตอนสายเข้า เพื่อเตือนเบอร์อันตราย (SAFE ไม่บล็อกสายเอง)", style = MaterialTheme.typography.bodyLarge)
                StatusLine(roleOk, if (roleOk) "ตั้งแล้ว" else "ยังไม่ได้ตั้ง")
                if (!roleOk) BigButton("ตั้ง SAFE เป็นแอปคัดกรองสาย", {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val rm = context.getSystemService(RoleManager::class.java)
                        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                        } else {
                            toast(context, "เครื่องนี้ไม่รองรับการคัดกรองสาย")
                        }
                    }
                })
                Text("ระบบจะถามว่า \"ตั้ง SAFE เป็นแอปสแปมและหมายเลขผู้โทรเริ่มต้น?\" ให้กด \"ตั้งเป็นค่าเริ่มต้น\"", style = MaterialTheme.typography.bodyMedium)
            }

            SetupStep.RECORDING -> SectionCard("เปิดการอัดเสียงสายในแอปโทรศัพท์") {
                Text("ยี่ห้อ: ${brand.displayName}", style = MaterialTheme.typography.titleMedium)
                SetupGuide.recordingSteps(brand).forEachIndexed { i, s ->
                    Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge)
                }
                if (SetupGuide.supportsAutoRecordingCheck(brand)) {
                    BigOutlinedButton("เปิดแอปโทรศัพท์", { openDialer(context) })
                    Text("ชื่อเมนูอาจต่างกันเล็กน้อยตามรุ่น · การอัดเสียงสายควรแจ้งคู่สนทนาตามความเหมาะสม", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SetupStep.AUTO_CHECK -> SectionCard("ให้ SAFE ตรวจสายอัตโนมัติหลังวางสาย") {
                when {
                    !SetupGuide.supportsAutoRecordingCheck(brand) ->
                        Text("ข้ามขั้นนี้ได้ (แอปโทรศัพท์ของเครื่องนี้ไม่ให้แอปอื่นอ่านไฟล์อัดเสียง)", style = MaterialTheme.typography.bodyLarge)
                    !FileTranscriber.isSupported(context) ->
                        Text("ต้องใช้ Android 13 ขึ้นไป และมีระบบถอดเสียงของ Google — ข้ามขั้นนี้ได้", style = MaterialTheme.typography.bodyLarge)
                    else -> {
                        Text("SAFE จะหาไฟล์อัดเสียงสายใหม่เอง ถอดเสียง วิเคราะห์ แล้วเตือนถ้าเสี่ยง (ไม่เก็บไฟล์เสียง)", style = MaterialTheme.typography.bodyLarge)
                        StatusLine(autoOn, if (autoOn) "เปิดอยู่" else "ยังไม่เปิด")
                        if (!autoOn) BigButton("อนุญาตและเปิดการตรวจอัตโนมัติ", {
                            if (audioOk) { vm.updateAutoRecording(true); tick++ } else audioLauncher.launch(CallRecordings.audioPermission())
                        })
                        if (audioOk) {
                            BigOutlinedButton("ทดสอบ: SAFE เห็นไฟล์อัดเสียงสายไหม", {
                                scope.launch {
                                    val n = withContext(Dispatchers.IO) { CallRecordings.countRecent(context, 30) }
                                    recordingTest = if (n > 0) {
                                        "✅ พบไฟล์อัดเสียงสาย $n ไฟล์ใน 30 วัน — SAFE ตรวจได้"
                                    } else {
                                        "⚪ ยังไม่พบ — ลองโทรหาเบอร์ใดก็ได้ 1 สาย คุยเกิน 5 วินาที แล้วกดทดสอบอีกครั้ง"
                                    }
                                }
                            })
                            recordingTest?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }

            SetupStep.BACKGROUND -> SectionCard("ให้ SAFE ทำงานเบื้องหลังได้") {
                Text("มือถือบางยี่ห้อปิดแอปเบื้องหลังเพื่อประหยัดแบต ทำให้ SAFE เตือนช้าหรือไม่เตือน", style = MaterialTheme.typography.bodyLarge)
                StatusLine(batteryOk, if (batteryOk) "ระบบไม่จำกัดแบตเตอรี่ของ SAFE แล้ว" else "ระบบยังจำกัดแบตเตอรี่ของ SAFE")
                SetupGuide.backgroundSteps(brand).forEachIndexed { i, s ->
                    Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge)
                }
                BigOutlinedButton("เปิดหน้าตั้งค่าของ SAFE", { openAppSettings(context) })
            }

            SetupStep.MICROPHONE -> SectionCard("ไมโครโฟน (ไม่บังคับ)") {
                Text("ใช้กับโหมด \"ฟังผ่านลำโพง\" เท่านั้น ถ้าไม่ใช้โหมดนี้ ข้ามได้", style = MaterialTheme.typography.bodyLarge)
                StatusLine(micOk, if (micOk) "อนุญาตแล้ว" else "ยังไม่อนุญาต")
                if (!micOk) BigOutlinedButton("อนุญาตไมโครโฟน", { micLauncher.launch(Manifest.permission.RECORD_AUDIO) })
            }

            SetupStep.DONE -> SectionCard("สรุปการตั้งค่า") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) StatusLine(notifOk, "การแจ้งเตือน")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) StatusLine(roleOk, "เตือนเบอร์อันตรายตอนสายเข้า")
                if (SetupGuide.supportsAutoRecordingCheck(brand)) StatusLine(autoOn, "ตรวจสายอัตโนมัติหลังวางสาย")
                StatusLine(batteryOk, "ทำงานเบื้องหลังได้เต็มที่")
                StatusLine(micOk, "ฟังผ่านลำโพง (ไม่บังคับ)")
                Text("ข้อที่ยังไม่ได้เปิด กลับมาตั้งได้ที่หน้าแรก → \"ตั้งค่าทีละขั้น\"", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (index > 0) BigOutlinedButton("‹ ย้อนกลับ", { index-- }, Modifier.weight(1f))
            if (step == SetupStep.DONE) BigButton("เสร็จสิ้น", onFinish, Modifier.weight(1f))
            else BigButton(if (step == SetupStep.WELCOME) "เริ่ม ›" else "ถัดไป ›", { index++ }, Modifier.weight(1f))
        }
        if (step != SetupStep.DONE) {
            TextButton(onClick = onFinish) { Text("ข้ามไปก่อน ตั้งทีหลัง", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun OpenSettingsHint(context: Context) {
    Text("ถ้ากดแล้วไม่มีหน้าต่างถาม ให้เปิดเองในหน้าตั้งค่าของแอป", style = MaterialTheme.typography.bodyMedium)
    BigOutlinedButton("เปิดหน้าตั้งค่าของ SAFE", { openAppSettings(context) })
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun defaultDialerPackage(context: Context): String? =
    runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

private fun ignoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** เปิดแอปโทรศัพท์ (หน้าโทรออก) ให้ผู้ใช้ไปหาเมนูตั้งค่าการอัดเสียงเอง */
private fun openDialer(context: Context) {
    context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
