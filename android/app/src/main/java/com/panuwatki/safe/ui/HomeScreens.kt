package com.panuwatki.safe.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.panuwatki.safe.calls.CallRecordings
import com.panuwatki.safe.notify.Alerts
import com.panuwatki.safe.setup.SetupGuide
import com.panuwatki.safe.speech.FileTranscriber

@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
fun ConsentScreen(onAccept: () -> Unit, onReadPolicy: () -> Unit) {
    ScreenColumn {
        Text("🛡️ SAFE", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text("ผู้ช่วยเตือนมิจฉาชีพทางโทรศัพท์", style = MaterialTheme.typography.titleLarge)
        SectionCard("SAFE ทำอะไรบ้าง") {
            Text("• เตือนเมื่อมีสายเข้าจากเบอร์ที่มีลักษณะเสี่ยง (เช่น +697) หรือเบอร์ที่คุณ/ชุมชนรายงานไว้", style = MaterialTheme.typography.bodyLarge)
            Text("• ตรวจไฟล์อัดเสียงสายหลังวางสาย แล้วเตือนถ้าบทสนทนาเข้าข่ายมิจฉาชีพ", style = MaterialTheme.typography.bodyLarge)
            Text("• ฟังผ่านลำโพงระหว่างคุย (ทดลอง)", style = MaterialTheme.typography.bodyLarge)
        }
        SectionCard("ข้อมูลของคุณ") {
            Text("• AI วิเคราะห์บทสนทนาในเครื่องนี้เท่านั้น ข้อความบทสนทนาไม่ถูกส่งออกจากเครื่อง", style = MaterialTheme.typography.bodyLarge)
            Text("• SAFE จดเบอร์ไม่รู้จักที่โทรเข้าไว้ในเครื่อง 7 วัน เพื่อให้คุณรายงานสายเมื่อกี้ได้ แล้วลบเอง", style = MaterialTheme.typography.bodyLarge)
            Text(
                "• ฐานข้อมูลเบอร์ชุมชน (ไม่บังคับ ปิดไว้ก่อน): ถ้าคุณเปิด SAFE จะดาวน์โหลดรายชื่อเบอร์ที่ถูกรายงาน " +
                    "และเมื่อคุณกดรายงาน จะส่งรหัสเบอร์ (hash) กับประเภทขึ้นคลาวด์ ไม่ส่งชื่อหรือข้อความใด ๆ",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "• การแปลงเสียงเป็นข้อความใช้บริการถอดเสียงของระบบ Android ในเครื่อง ถ้าเครื่องถอดภาษาไทยในเครื่องไม่ได้ " +
                    "บริการนั้น (เช่น ของ Google) อาจส่งเสียงไปประมวลผลที่เซิร์ฟเวอร์ของผู้ให้บริการ",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("• ไม่เก็บไฟล์เสียง เก็บเฉพาะข้อความที่ลบเบอร์โทร เลขบัญชี เลขบัตร และลิงก์ออกแล้ว", style = MaterialTheme.typography.bodyLarge)
            Text("• ผลที่คุณไม่ได้ยืนยันจะถูกลบเองใน 30 วัน และกดลบทั้งหมดได้ทุกเมื่อ", style = MaterialTheme.typography.bodyLarge)
            Text("• ข้อมูลจะออกจากเครื่องเฉพาะเมื่อคุณกด \"ส่งออก\" เองเท่านั้น", style = MaterialTheme.typography.bodyLarge)
        }
        SectionCard("ข้อควรทราบ") {
            Text("• AI อาจผิดพลาดได้ ใช้เป็นเครื่องช่วยเตือน ไม่ใช่คำตัดสิน", style = MaterialTheme.typography.bodyLarge)
            Text("• การอัดเสียงสายเป็นฟีเจอร์ของโทรศัพท์คุณ โปรดใช้ตามกฎหมายและแจ้งคู่สนทนาตามความเหมาะสม", style = MaterialTheme.typography.bodyLarge)
        }
        BigOutlinedButton("อ่านนโยบายความเป็นส่วนตัวฉบับเต็ม", onReadPolicy)
        BigButton("ยอมรับและเริ่มใช้งาน", onAccept)
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, actions: HomeActions, resumeTick: Int, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    // อ่านสถานะสิทธิ์ใหม่ทุกครั้งที่กลับเข้าแอป
    val roleHeld = remember(resumeTick) { isCallScreeningRoleHeld(context) }
    val canNotify = remember(resumeTick) { Alerts.canNotify(context) }
    val canReadAudio = remember(resumeTick) { CallRecordings.hasPermission(context) }
    val fileSupported = remember { FileTranscriber.isSupported(context) }
    val brand = remember {
        SetupGuide.detect(
            android.os.Build.MANUFACTURER ?: "",
            runCatching { context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
        )
    }

    ScreenColumn {
        Text("🛡️ SAFE", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text("ผู้ช่วยเตือนมิจฉาชีพทางโทรศัพท์", style = MaterialTheme.typography.titleMedium)
        BigOutlinedButton("🧭 ตั้งค่าทีละขั้น (ลูกหลานช่วยตั้งได้)", { onNavigate(Screen.Setup) })

        SectionCard("1) เตือนเบอร์อันตรายตอนสายเข้า") {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Text("ต้องใช้ Android 10 ขึ้นไป", style = MaterialTheme.typography.bodyLarge)
            } else {
                StatusLine(roleHeld, if (roleHeld) "ตั้ง SAFE เป็นแอปคัดกรองสายแล้ว" else "ยังไม่ได้ตั้ง SAFE เป็นแอปคัดกรองสาย")
                StatusLine(canNotify, if (canNotify) "แจ้งเตือนได้" else "ยังไม่อนุญาตการแจ้งเตือน")
                if (!roleHeld || !canNotify) BigButton("เปิดใช้การเตือนตอนสายเข้า", actions.requestCallScreening)
                Text(
                    "SAFE ไม่ตัดสายเอง (ยกเว้นเบอร์ที่คุณตั้ง \"ตัดสายอัตโนมัติ\") และจดเบอร์ไม่รู้จักไว้ในเครื่อง 7 วันเพื่อให้รายงานได้",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SectionCard("2) ตรวจสายอัตโนมัติหลังวางสาย") {
            val on = vm.autoRecording && canReadAudio
            StatusLine(on, if (on) "เปิดอยู่ — วางสายแล้ว SAFE จะตรวจให้เอง" else "ปิดอยู่")
            Text(
                "ต้องเปิด \"อัดเสียงการโทรอัตโนมัติ\" ในแอปโทรศัพท์ของเครื่องก่อน (Samsung, OPPO, Xiaomi, vivo หลายรุ่นทำได้)",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!SetupGuide.supportsAutoRecordingCheck(brand)) {
                Text(
                    "⚠️ เครื่องนี้ใช้แอปโทรศัพท์ของ Google ซึ่ง SAFE อ่านไฟล์อัดเสียงไม่ได้ การตรวจอัตโนมัติจึงใช้ไม่ได้ (ยังเลือกไฟล์เสียงมาตรวจเองได้)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!fileSupported) {
                Text("⚠️ การถอดเสียงจากไฟล์ต้องใช้ Android 13 ขึ้นไป", style = MaterialTheme.typography.bodyMedium)
            }
            if (on) BigOutlinedButton("ปิดการตรวจอัตโนมัติ", actions.disableAutoRecording)
            else BigButton("เปิดการตรวจอัตโนมัติ", actions.enableAutoRecording, enabled = fileSupported)
            BigOutlinedButton("เลือกไฟล์อัดเสียงสายมาตรวจเอง", actions.pickAudioFile, enabled = fileSupported)
        }

        SectionCard("3) ฟังผ่านลำโพงระหว่างคุย (ทดลอง)") {
            Text(
                "เปิดลำโพงตอนคุยสาย แล้วเปิดหน้านี้ไว้ SAFE จะฟังและเปลี่ยนสีให้ทันที " +
                    "บางรุ่นระบบไม่ให้ใช้ไมค์ระหว่างสาย ถ้าเป็นแบบนั้น SAFE จะแจ้งให้ทราบ",
                style = MaterialTheme.typography.bodyMedium,
            )
            BigButton("เริ่มฟัง", { onNavigate(Screen.Listen) })
        }

        SectionCard("4) สมุดเบอร์ / รายงานเบอร์") {
            Text(
                "บันทึกเบอร์มิจฉาชีพ/สแปม/ไว้ใจได้ รายงานสายเมื่อกี้ แชร์รายชื่อให้ครอบครัว และดูเบอร์ทางการสำหรับโทรกลับตรวจสอบ",
                style = MaterialTheme.typography.bodyMedium,
            )
            BigButton("เปิดสมุดเบอร์", { onNavigate(Screen.Numbers) })
        }

        BigOutlinedButton("📋 ประวัติ / ช่วยเพิ่มข้อมูลให้ AI", { onNavigate(Screen.History) })
        BigOutlinedButton("⚙️ ตั้งค่าและความเป็นส่วนตัว", { onNavigate(Screen.Settings) })
    }
}
