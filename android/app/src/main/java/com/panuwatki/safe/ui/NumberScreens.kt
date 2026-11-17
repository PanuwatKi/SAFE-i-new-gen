package com.panuwatki.safe.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.panuwatki.safe.calls.CallerCheck.BookLabel
import com.panuwatki.safe.calls.NumberRiskLevel
import com.panuwatki.safe.calls.PhoneNumbers
import com.panuwatki.safe.cloud.CommunityReports
import com.panuwatki.safe.data.Categories
import com.panuwatki.safe.data.NumberEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("th-TH"))

private fun labelText(label: String): String = when (label) {
    BookLabel.SCAM.name -> "🔴 มิจฉาชีพ"
    BookLabel.SPAM.name -> "🟡 สแปม/ขายของ"
    else -> "🟢 ไว้ใจได้"
}

@Composable
fun NumbersScreen(vm: MainViewModel, onBack: () -> Unit, onEdit: (String?) -> Unit, onOfficial: () -> Unit) {
    val context = LocalContext.current
    val recent by vm.recentCalls.collectAsState()
    val book by vm.numberBook.collectAsState()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBook(uri) { n -> toast(context, "ส่งออก $n เบอร์แล้ว ส่งไฟล์นี้ให้ครอบครัวนำเข้าได้เลย") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBook(uri) { msg -> toast(context, msg) }
    }

    ScreenColumn {
        BackRow("สมุดเบอร์ / รายงานเบอร์", onBack)
        BigButton("➕ เพิ่มเบอร์เอง", { onEdit(null) })
        BigOutlinedButton("☎️ เบอร์ทางการสำหรับโทรตรวจสอบ / แจ้งเหตุ", onOfficial)

        SectionCard("สายจากเบอร์ไม่รู้จัก (${recent.size})") {
            Text("SAFE จดไว้ในเครื่อง 7 วัน แตะเพื่อรายงานหรือบันทึกเบอร์", style = MaterialTheme.typography.bodyMedium)
            if (recent.isEmpty()) Text("ยังไม่มี (ต้องตั้ง SAFE เป็นแอปคัดกรองสายก่อน)", style = MaterialTheme.typography.bodyMedium)
            recent.take(30).forEach { call ->
                val icon = when (call.level) {
                    NumberRiskLevel.HIGH.name -> "🔴"
                    NumberRiskLevel.CAUTION.name -> "🟡"
                    else -> "⚪"
                }
                Card(Modifier.fillMaxWidth().clickable(enabled = call.number != null) { onEdit(call.number) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text("$icon ${PhoneNumbers.display(call.number)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val extra = listOfNotNull(call.title.takeIf { it.isNotBlank() }, if (call.rejected) "ตัดสายแล้ว" else null)
                        Text((listOf(timeFormat.format(Date(call.at))) + extra).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        SectionCard("สมุดเบอร์ของฉัน (${book.size})") {
            if (book.isEmpty()) Text("ยังไม่มีเบอร์ที่บันทึกไว้", style = MaterialTheme.typography.bodyMedium)
            book.forEach { e ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(e.number) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${labelText(e.label)}  ${PhoneNumbers.display(e.number)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val details = listOfNotNull(
                            e.category, e.note?.takeIf { it.isNotBlank() },
                            if (e.autoReject) "ตัดสายอัตโนมัติ" else null,
                            if (e.reportedToCommunity) "รายงานชุมชนแล้ว" else null,
                        )
                        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        SectionCard("แชร์สมุดเบอร์กับครอบครัว") {
            Text("ลูกหลานส่งออกไฟล์ แล้วส่งให้พ่อแม่กด \"นำเข้า\" ได้ (เบอร์ที่มีอยู่แล้วจะไม่ถูกทับ)", style = MaterialTheme.typography.bodyMedium)
            BigOutlinedButton("ส่งออกสมุดเบอร์", { exporter.launch("safe_numbers.json") }, enabled = book.isNotEmpty())
            BigOutlinedButton("นำเข้าสมุดเบอร์", { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        }

        CommunityCard(vm)
    }
}

@Composable
fun CommunityCard(vm: MainViewModel) {
    if (!CommunityReports.isConfigured()) return
    SectionCard("ฐานข้อมูลเบอร์ชุมชน (ไม่บังคับ)") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("ร่วมฐานข้อมูลชุมชน", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = vm.communityEnabled, onCheckedChange = vm::setCommunity)
        }
        Text(
            "เปิดแล้ว SAFE จะดาวน์โหลดรายชื่อเบอร์ที่ผู้ใช้คนอื่นรายงาน (ตั้งแต่ ${com.panuwatki.safe.calls.CallerCheck.COMMUNITY_THRESHOLD} คนขึ้นไป) มาเช็กในเครื่อง " +
                "และคุณรายงานเบอร์ได้ โดยส่งแค่รหัสเบอร์ (hash) กับประเภท ไม่ส่งชื่อ ข้อความ หรือเบอร์ของคุณ",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (vm.communityEnabled) BigOutlinedButton("ซิงก์ตอนนี้", vm::syncCommunityNow)
        if (vm.communityStatus.isNotEmpty()) Text(vm.communityStatus, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberEditScreen(vm: MainViewModel, number: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(number?.let { PhoneNumbers.display(it) } ?: "") }
    var label by remember { mutableStateOf(BookLabel.SCAM.name) }
    var category by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var autoReject by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(vm.communityEnabled) }
    var existing by remember { mutableStateOf<NumberEntry?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(number) {
        if (number != null) vm.numberEntry(number)?.let {
            existing = it
            label = it.label
            category = it.category
            note = it.note ?: ""
            autoReject = it.autoReject
        }
    }
    val normalized = PhoneNumbers.normalize(input)

    ScreenColumn {
        BackRow(if (existing != null) "แก้ไขเบอร์" else "บันทึก / รายงานเบอร์", onBack)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("เบอร์โทร") },
            singleLine = true,
            enabled = number == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            textStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionCard("เบอร์นี้คือ") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(BookLabel.SCAM, BookLabel.SPAM, BookLabel.TRUSTED).forEach { l ->
                    val pick = { label = l.name; category = null }
                    if (label == l.name) BigButton(labelText(l.name), pick, Modifier.weight(1f))
                    else BigOutlinedButton(labelText(l.name), pick, Modifier.weight(1f))
                }
            }
        }
        if (label == BookLabel.SCAM.name) {
            SectionCard("แอบอ้างเป็นใคร (ไม่บังคับ)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Categories.scam.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                    }
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            label = { Text("โน้ตส่วนตัว (เก็บในเครื่องเท่านั้น)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (label != BookLabel.TRUSTED.name) {
            SectionCard("ตัวเลือก") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SwitchLine("ตัดสายเบอร์นี้อัตโนมัติ", autoReject) { autoReject = it }
                    Text("ต้องตั้ง SAFE เป็นแอปคัดกรองสายก่อน", style = MaterialTheme.typography.bodyMedium)
                }
                if (vm.communityEnabled) {
                    SwitchLine("รายงานไปยังฐานข้อมูลชุมชนด้วย", report && existing?.reportedToCommunity != true) { report = it }
                    if (existing?.reportedToCommunity == true) Text("เบอร์นี้รายงานชุมชนไปแล้ว", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (normalized == null && input.isNotBlank()) Text("รูปแบบเบอร์ไม่ถูกต้อง", color = MaterialTheme.colorScheme.error)
        BigButton(
            if (saving) "กำลังบันทึก…" else "บันทึก",
            {
                saving = true
                vm.saveNumber(
                    NumberEntry(
                        number = normalized!!,
                        label = label,
                        category = category,
                        note = note.trim().ifEmpty { null },
                        autoReject = autoReject && label != BookLabel.TRUSTED.name,
                        reportedToCommunity = existing?.reportedToCommunity == true,
                    ),
                    reportToCommunity = report && vm.communityEnabled,
                ) { msg -> toast(context, msg); onBack() }
            },
            enabled = normalized != null && !saving,
        )
        if (existing != null) BigOutlinedButton("🗑️ ลบออกจากสมุดเบอร์", { vm.deleteNumber(existing!!.number); onBack() })
        BigOutlinedButton("☎️ แจ้งเหตุ AOC 1441", { dial(context, "1441") })
    }
}

@Composable
private fun SwitchLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * เบอร์ทางการสำหรับ "วางสายแล้วโทรกลับเอง" — ใส่เฉพาะเบอร์ที่ตรวจจากแหล่งทางการแล้ว (ดู docs/SECURITY_POLICY.md)
 * เบอร์ธนาคารให้ดูจากหลังบัตรของผู้ใช้เอง เพื่อไม่ให้แอปมีเบอร์ผิด
 */
private data class Official(val name: String, val detail: String, val number: String)

private val officials = listOf(
    Official("AOC 1441", "ศูนย์ต่อต้านภัยออนไลน์ แจ้งเหตุ/ปรึกษา/ขออายัดบัญชี 24 ชม.", "1441"),
    Official("ธนาคารแห่งประเทศไทย 1213", "ศูนย์คุ้มครองผู้ใช้บริการทางการเงิน", "1213"),
    Official("เหตุด่วนเหตุร้าย 191", "ตำรวจ", "191"),
    Official("AIS 1185", "แจ้งกรณีมีคนแอบอ้างเป็นเจ้าหน้าที่ AIS", "1185"),
)

@Composable
fun OfficialNumbersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenColumn {
        BackRow("เบอร์ทางการ", onBack)
        SectionCard("หลักสำคัญ") {
            Text("ถ้ามีคนโทรมาอ้างเป็นธนาคาร ตำรวจ หรือหน่วยงานรัฐ: วางสายก่อน แล้วโทรกลับเบอร์ทางการเอง อย่าโทรกลับเบอร์ที่เขาให้", style = MaterialTheme.typography.bodyLarge)
            Text("เบอร์ธนาคารที่ถูกต้อง ดูได้จากด้านหลังบัตร ATM หรือบัตรเครดิตของคุณ", style = MaterialTheme.typography.bodyLarge)
        }
        officials.forEach { o ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(o.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(o.detail, style = MaterialTheme.typography.bodyMedium)
                    BigButton("โทร ${o.number}", { dial(context, o.number) })
                }
            }
        }
        BigOutlinedButton("🌐 แจ้งความออนไลน์ thaipoliceonline.com", {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://thaipoliceonline.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }
}

/** เปิดหน้าโทรออกพร้อมเบอร์ (ผู้ใช้กดโทรเอง — ไม่ต้องขอสิทธิ์โทรออก) */
fun dial(context: Context, number: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
