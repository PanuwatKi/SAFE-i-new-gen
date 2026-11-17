package com.panuwatki.safe.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.panuwatki.safe.BuildConfigInfo
import com.panuwatki.safe.SafeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    actions: HomeActions,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onDataDeleted: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var modelInfo by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val c = SafeEngine.classifier(context)
        modelInfo = "โมเดล ${c.featureCount} ฟีเจอร์ · สร้างเมื่อ ${c.modelCreatedAt.take(10)}"
    }

    ScreenColumn {
        BackRow("ตั้งค่าและความเป็นส่วนตัว", onBack)
        SectionCard("การเตือน") {
            SwitchRow("เตือนเบอร์อันตรายตอนสายเข้า", vm.numberWarning) { vm.updateNumberWarning(it) }
            SwitchRow("ตรวจสายอัตโนมัติหลังวางสาย", vm.autoRecording) { on ->
                if (on) actions.enableAutoRecording() else actions.disableAutoRecording()
            }
            BigOutlinedButton("เปิดหน้าสิทธิ์ของแอปในระบบ", { openAppSettings(context) })
        }
        CommunityCard(vm)
        SectionCard("ความเป็นส่วนตัว") {
            Text("• AI วิเคราะห์บทสนทนาในเครื่องนี้ ข้อความไม่ถูกส่งออก", style = MaterialTheme.typography.bodyMedium)
            Text("• ไม่เก็บไฟล์เสียง · เบอร์ไม่รู้จักที่โทรเข้าเก็บในเครื่อง 7 วัน", style = MaterialTheme.typography.bodyMedium)
            BigOutlinedButton("อ่านนโยบายความเป็นส่วนตัว", onPrivacy)
            BigOutlinedButton("🗑️ ลบข้อมูลทั้งหมดของ SAFE", { confirmDelete = true })
        }
        SectionCard("เกี่ยวกับแอป") {
            Text("SAFE เวอร์ชัน ${BuildConfigInfo.versionName(context)}", style = MaterialTheme.typography.bodyMedium)
            if (modelInfo.isNotEmpty()) Text(modelInfo, style = MaterialTheme.typography.bodyMedium)
            Text("แจ้งเหตุ/ปรึกษาภัยออนไลน์ ศูนย์ AOC: โทร 1441", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("ลบข้อมูลทั้งหมด?") },
            text = { Text("ประวัติ ข้อมูลที่ยืนยันไว้ และการตั้งค่าทั้งหมดจะถูกลบถาวร กู้คืนไม่ได้") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAllData {
                        toast(context, "ลบข้อมูลทั้งหมดแล้ว")
                        onDataDeleted()
                    }
                }) { Text("ลบทั้งหมด") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ยกเลิก") } },
        )
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) {
            context.assets.open("privacy_policy_th.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
    ScreenColumn {
        BackRow("นโยบายความเป็นส่วนตัว", onBack)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
