package com.panuwatki.safe.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.panuwatki.safe.SafeEngine
import com.panuwatki.safe.ml.RiskColor
import com.panuwatki.safe.speech.LiveListener

@Composable
fun ListenScreen(vm: MainViewModel, onBack: () -> Unit, onOpenRecord: (Long) -> Unit) {
    val context = LocalContext.current
    val live = vm.live
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startLive() else toast(context, "ต้องอนุญาตให้ใช้ไมโครโฟน SAFE ถึงจะฟังได้")
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startLive()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ScreenColumn {
        BackRow("ฟังผ่านลำโพง (ทดลอง)") { vm.stopLive(); onBack() }
        val state = live.state
        val color = state?.takeIf { it.enoughData }?.peakColor
        RiskPanel(
            color = color,
            percent = state?.takeIf { it.enoughData }?.peak,
            subtitle = state?.latest?.let { "ล่าสุด ${it.percent.toInt()}% · สูงสุดในสายนี้ ${state.peak.toInt()}%" },
        )
        val statusText = when (live.status) {
            LiveListener.Status.LISTENING -> "🎙️ กำลังฟัง…"
            LiveListener.Status.NO_SPEECH -> "🔇 ไม่ได้ยินเสียงพูด — เปิดลำโพงหรือยัง? ถ้าเปิดแล้ว เครื่องนี้อาจไม่อนุญาตให้ใช้ไมค์ระหว่างสาย"
            LiveListener.Status.NO_PERMISSION -> "ไม่ได้รับอนุญาตให้ใช้ไมโครโฟน"
            LiveListener.Status.UNAVAILABLE -> "เครื่องนี้ไม่มีระบบถอดเสียง (ลองติดตั้ง/อัปเดตแอป Google)"
            LiveListener.Status.STOPPED -> if (live.lines.isEmpty()) "กด \"เริ่มฟัง\" แล้วเปิดลำโพงโทรศัพท์" else "หยุดฟังแล้ว"
        }
        Text(statusText, style = MaterialTheme.typography.bodyLarge)
        if (live.partial.isNotBlank()) Text("… ${live.partial}", style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (live.running) BigButton("หยุดฟัง", vm::stopLive, Modifier.weight(1f))
            else BigButton("เริ่มฟัง", ::start, Modifier.weight(1f))
            BigOutlinedButton("สายใหม่", vm::newCall, Modifier.weight(1f))
        }
        live.savedId?.let { id ->
            BigOutlinedButton("✔ ยืนยันผล / เพิ่มเป็นข้อมูลให้ AI", { onOpenRecord(id) })
        }
        if (live.words.isNotEmpty()) {
            SectionCard("🔍 คำที่ทำให้สงสัย") { Text(live.words.joinToString("  "), style = MaterialTheme.typography.titleMedium) }
        }
        color?.let { SafetyAdvice(it) }
        if (live.lines.isNotEmpty()) {
            SectionCard("สิ่งที่ได้ยิน") {
                live.lines.takeLast(8).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
fun AnalyzeScreen(vm: MainViewModel, uri: Uri, onBack: () -> Unit, onOpenRecord: (Long) -> Unit) {
    LaunchedEffect(uri) {
        if (vm.fileUi is MainViewModel.FileUi.Idle) vm.analyzeFile(uri)
    }
    ScreenColumn {
        BackRow("ตรวจไฟล์เสียงสาย") { vm.resetFile(); onBack() }
        when (val ui = vm.fileUi) {
            MainViewModel.FileUi.Idle, MainViewModel.FileUi.Running -> {
                CircularProgressIndicator()
                Text("กำลังถอดเสียงและวิเคราะห์… (อาจใช้เวลาเท่ากับความยาวสาย)", style = MaterialTheme.typography.bodyLarge)
            }
            is MainViewModel.FileUi.Done -> when (val o = ui.outcome) {
                is SafeEngine.RecordingOutcome.Success -> {
                    RiskPanel(o.state.peakColor, o.state.peak)
                    if (o.suspiciousWords.isNotEmpty()) {
                        SectionCard("🔍 คำที่ทำให้สงสัย") { Text(o.suspiciousWords.joinToString("  "), style = MaterialTheme.typography.titleMedium) }
                    }
                    SafetyAdvice(o.state.peakColor)
                    BigButton("✔ ยืนยันผล / เพิ่มเป็นข้อมูลให้ AI", { onOpenRecord(o.id) })
                    SectionCard("ข้อความที่ถอดได้") { Text(o.state.transcript, style = MaterialTheme.typography.bodyMedium) }
                }
                is SafeEngine.RecordingOutcome.NoSpeech -> {
                    RiskPanel(null, null, o.reason)
                }
                is SafeEngine.RecordingOutcome.Failure -> {
                    SectionCard("ตรวจไฟล์นี้ไม่สำเร็จ") {
                        Text(o.reason, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "ระบบถอดเสียงของเครื่องนี้อาจยังไม่รองรับการถอดเสียงจากไฟล์ " +
                                "ลองอัปเดตแอป Google / Speech Services by Google แล้วลองใหม่",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

fun riskColorOf(name: String): RiskColor = runCatching { RiskColor.valueOf(name) }.getOrDefault(RiskColor.GREEN)
