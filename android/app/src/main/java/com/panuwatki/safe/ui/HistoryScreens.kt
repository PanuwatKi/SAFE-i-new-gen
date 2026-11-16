package com.panuwatki.safe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.panuwatki.safe.data.AnalysisEntity
import com.panuwatki.safe.data.Categories
import com.panuwatki.safe.data.SafeDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("d MMM yy HH:mm", Locale("th", "TH"))

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onOpenRecord: (Long) -> Unit) {
    val context = LocalContext.current
    val items by vm.history.collectAsState()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri) { n -> toast(context, "ส่งออก $n รายการแล้ว") }
    }
    val labeled = items.count { it.userLabel != null }

    ScreenColumn {
        BackRow("ประวัติ / เพิ่มข้อมูลให้ AI", onBack)
        SectionCard("ช่วยให้ AI เก่งขึ้น") {
            Text(
                "แตะแต่ละรายการเพื่อยืนยันว่าเป็นมิจฉาชีพจริงหรือไม่ แล้วกดส่งออกเป็นไฟล์ CSV " +
                    "เพื่อนำไปเทรนโมเดลรุ่นถัดไป ข้อมูลจะไม่ออกจากเครื่องจนกว่าคุณจะกดส่งออกเอง",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("ยืนยันแล้ว $labeled จาก ${items.size} รายการ", style = MaterialTheme.typography.bodyLarge)
            BigOutlinedButton("ส่งออกข้อมูลที่ยืนยันแล้ว (CSV)", { exporter.launch("safe_contributed.csv") }, enabled = labeled > 0)
            Text(
                "รายการที่ไม่ได้ยืนยันจะถูกลบอัตโนมัติเมื่อครบ ${SafeDatabase.RETENTION_DAYS} วัน",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (items.isEmpty()) Text("ยังไม่มีประวัติ", style = MaterialTheme.typography.bodyLarge)
        items.forEach { HistoryItem(it) { onOpenRecord(it.id) } }
    }
}

@Composable
private fun HistoryItem(e: AnalysisEntity, onClick: () -> Unit) {
    val color = riskColorOf(e.color)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${RiskColors.emoji(color)} ${RiskColors.title(color)} · ${e.percent.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            val source = if (e.source == AnalysisEntity.SOURCE_LIVE) "ฟังสด" else "ไฟล์อัดเสียง"
            Text("${dateFormat.format(Date(e.createdAt))} · $source", style = MaterialTheme.typography.bodyMedium)
            val labelText = when (e.userLabel) {
                AnalysisEntity.LABEL_SCAM -> "✔ ยืนยันแล้ว: มิจฉาชีพ" + (e.category?.let { " ($it)" } ?: "")
                AnalysisEntity.LABEL_NORMAL -> "✔ ยืนยันแล้ว: ไม่ใช่มิจฉาชีพ" + (e.category?.let { " ($it)" } ?: "")
                else -> "แตะเพื่อยืนยันผล"
            }
            Text(labelText, style = MaterialTheme.typography.bodyMedium)
            Text(e.transcript.take(80) + if (e.transcript.length > 80) "…" else "", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var entity by remember { mutableStateOf<AnalysisEntity?>(null) }
    var text by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        vm.record(id)?.let {
            entity = it
            text = it.transcript
            label = it.userLabel
            category = it.category
        }
    }

    ScreenColumn {
        BackRow("ยืนยันผล", onBack)
        val e = entity
        if (e == null) {
            Text("ไม่พบรายการนี้ (อาจถูกลบไปแล้ว)", style = MaterialTheme.typography.bodyLarge)
            return@ScreenColumn
        }
        val color = riskColorOf(e.color)
        Text("AI ประเมินว่า: ${RiskColors.emoji(color)} ${RiskColors.title(color)} (${e.percent.toInt()}%)", style = MaterialTheme.typography.titleMedium)

        SectionCard("1) ตรวจข้อความ") {
            Text(
                "SAFE ลบเบอร์โทร เลขบัญชี เลขบัตร และลิงก์ให้แล้ว กรุณาลบชื่อคนหรือข้อมูลส่วนตัวอื่นที่ยังเหลือ และแก้คำที่ถอดผิด",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
        }
        SectionCard("2) บทสนทนานี้เป็นมิจฉาชีพจริงไหม?") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                val pickScam = { label = AnalysisEntity.LABEL_SCAM; category = null }
                val pickNormal = { label = AnalysisEntity.LABEL_NORMAL; category = null }
                if (label == AnalysisEntity.LABEL_SCAM) BigButton("🔴 ใช่ มิจฉาชีพ", pickScam, Modifier.weight(1f))
                else BigOutlinedButton("🔴 ใช่ มิจฉาชีพ", pickScam, Modifier.weight(1f))
                if (label == AnalysisEntity.LABEL_NORMAL) BigButton("🟢 ไม่ใช่", pickNormal, Modifier.weight(1f))
                else BigOutlinedButton("🟢 ไม่ใช่", pickNormal, Modifier.weight(1f))
            }
        }
        label?.let { l ->
            SectionCard("3) ประเภท (ไม่บังคับ)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = if (l == AnalysisEntity.LABEL_SCAM) Categories.scam else Categories.normal
                    options.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c },
                            label = { Text(c, style = MaterialTheme.typography.bodyLarge) },
                        )
                    }
                }
            }
        }
        BigButton(
            "บันทึก",
            {
                vm.label(e.id, label!!, category, text)
                toast(context, "บันทึกแล้ว ขอบคุณที่ช่วยให้ AI เก่งขึ้น")
                onBack()
            },
            enabled = label != null && text.isNotBlank(),
        )
        BigOutlinedButton("🗑️ ลบรายการนี้", { vm.delete(e.id); onBack() })
    }
}
