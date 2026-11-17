package com.panuwatki.safe.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.panuwatki.safe.ml.LinkChecker
import com.panuwatki.safe.ml.ScamChecklist

/**
 * ตรวจข้อความ SMS / LINE / Messenger (แชร์เข้ามา วาง หรือพิมพ์) + ตรวจลิงก์
 * ผู้ใช้ยังกดไมค์บนแป้นพิมพ์เพื่อ "พูดเล่า" ว่าคนที่โทรมาพูดอะไรได้ด้วย
 */
@Composable
fun TextCheckScreen(
    vm: MainViewModel,
    initialText: String?,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(initialText ?: "") }
    val result = vm.textCheck

    LaunchedEffect(initialText) {
        vm.clearTextCheck()
        if (!initialText.isNullOrBlank()) vm.checkText(initialText)
    }

    ScreenColumn {
        BackRow("ตรวจข้อความ / ลิงก์") { vm.clearTextCheck(); onBack() }
        Text(
            "ได้ SMS หรือ LINE แปลก ๆ? กดค้างที่ข้อความ → แชร์ → SAFE หรือวางข้อความที่นี่\n" +
                "เพิ่งคุยโทรศัพท์? กดไมค์บนแป้นพิมพ์ แล้วเล่าว่าคนที่โทรมาพูดอะไร",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; if (result != null) vm.clearTextCheck() },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("วางหรือพิมพ์ข้อความที่ได้รับ…") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigOutlinedButton("📋 วางข้อความ", {
                readClipboard(context)?.let { text = it; vm.clearTextCheck() } ?: toast(context, "ยังไม่ได้คัดลอกข้อความไว้")
            }, Modifier.weight(1f))
            BigButton("🔍 ตรวจ", { vm.checkText(text) }, Modifier.weight(1f), enabled = text.isNotBlank())
        }

        if (result != null) {
            when {
                result.color != null -> RiskPanel(result.color, result.verdict?.percent)
                else -> RiskPanel(null, null, "ข้อความสั้นเกินไป พิมพ์หรือวางข้อความให้ยาวกว่านี้")
            }
            if (result.links.isNotEmpty()) {
                SectionCard("🔗 ลิงก์ในข้อความ (${result.links.size})") {
                    result.links.forEach { LinkRow(it) }
                    Text("SAFE ไม่ได้เปิดลิงก์ ตรวจจากชื่อลิงก์อย่างเดียว", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (result.words.isNotEmpty()) {
                SectionCard("🔍 คำที่ทำให้สงสัย") { Text(result.words.joinToString("  "), style = MaterialTheme.typography.titleMedium) }
            }
            result.color?.let { SafetyAdvice(it) }
            if (result.color != null) {
                val saved = result.savedId
                if (saved == null) {
                    BigOutlinedButton("✔ บันทึกและยืนยันผล (ช่วยให้ AI เก่งขึ้น)", { vm.saveTextCheck() })
                } else {
                    BigOutlinedButton("✔ ยืนยันผล / แก้ข้อความ", { onOpenRecord(saved) })
                }
            }
        }
    }
}

@Composable
private fun LinkRow(f: LinkChecker.Finding) {
    val icon = when (f.level) {
        LinkChecker.Level.HIGH -> "🔴"
        LinkChecker.Level.CAUTION -> "🟡"
        LinkChecker.Level.INFO -> "⚪"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$icon ${f.host}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(f.reason, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun readClipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

/** ถามตอบ 5 ข้อหลังวางสาย — ไม่ต้องอัดเสียง ไม่ต้องพิมพ์ */
@Composable
fun ChecklistScreen(onBack: () -> Unit, onReportNumber: () -> Unit) {
    val context = LocalContext.current
    val answers = remember { mutableStateMapOf<ScamChecklist.Question, Boolean>() }
    var showResult by remember { mutableStateOf(false) }

    ScreenColumn {
        BackRow("ตอบ 5 ข้อหลังวางสาย", onBack)
        Text("นึกถึงสายที่เพิ่งคุย แล้วแตะ ใช่ หรือ ไม่ใช่ (ไม่แน่ใจ ข้ามได้)", style = MaterialTheme.typography.bodyLarge)

        ScamChecklist.Question.entries.forEachIndexed { i, q ->
            SectionCard("${i + 1}. ${q.text}") {
                Text(q.hint, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    val yes = { answers[q] = true; showResult = false }
                    val no = { answers[q] = false; showResult = false }
                    if (answers[q] == true) BigButton("ใช่", yes, Modifier.weight(1f)) else BigOutlinedButton("ใช่", yes, Modifier.weight(1f))
                    if (answers[q] == false) BigButton("ไม่ใช่", no, Modifier.weight(1f)) else BigOutlinedButton("ไม่ใช่", no, Modifier.weight(1f))
                }
            }
        }

        BigButton("ดูผล", { showResult = true }, enabled = answers.isNotEmpty())

        if (showResult) {
            val r = ScamChecklist.evaluate(answers.toMap())
            RiskPanel(r.color, null, r.title)
            SectionCard("เหตุผล") { r.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) } }
            SafetyAdvice(r.color)
            BigOutlinedButton("📒 รายงานเบอร์ที่โทรมา", onReportNumber)
            BigOutlinedButton("☎️ โทรปรึกษา AOC 1441", { dial(context, "1441") })
        }
    }
}
