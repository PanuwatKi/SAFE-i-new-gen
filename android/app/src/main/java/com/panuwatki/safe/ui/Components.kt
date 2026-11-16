package com.panuwatki.safe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panuwatki.safe.ml.RiskColor

/** แผงสีใหญ่ แสดงผลเขียว/เหลือง/แดง + % */
@Composable
fun RiskPanel(color: RiskColor?, percent: Double?, subtitle: String? = null, modifier: Modifier = Modifier) {
    val bg = color?.let { RiskColors.of(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val fg = color?.let { RiskColors.onColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = color?.let { "${RiskColors.emoji(it)} ${RiskColors.title(it)}" } ?: "ฟังอยู่… ยังไม่พอจะตัดสิน",
            color = fg,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (percent != null && color != null) {
            Text("${percent.toInt()}%", color = fg, style = HugeNumber, fontWeight = FontWeight.Black)
        }
        if (subtitle != null) {
            Text(subtitle, color = fg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

/** คำแนะนำเมื่อมีความเสี่ยง */
@Composable
fun SafetyAdvice(color: RiskColor) {
    if (color == RiskColor.GREEN) return
    SectionCard(title = "ควรทำอย่างไร") {
        Text("• ห้ามโอนเงิน ห้ามบอกรหัส OTP หรือรหัสผ่าน", style = MaterialTheme.typography.bodyLarge)
        Text("• ห้ามกดลิงก์หรือติดตั้งแอปตามที่ปลายสายบอก", style = MaterialTheme.typography.bodyLarge)
        Text("• วางสาย แล้วโทรกลับเบอร์ทางการของหน่วยงานเอง", style = MaterialTheme.typography.bodyLarge)
        Text("• ปรึกษาลูกหลาน หรือโทรสายด่วน 1441", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "⚪", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack) { Text("‹ กลับ", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
