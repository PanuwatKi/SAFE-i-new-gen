package com.panuwatki.safe

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** งานสั้น ๆ ที่ต้องทำต่อแม้ Service จะถูกปิดไปแล้ว (เช่น บันทึกสายล่าสุดหลังตอบระบบ) */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
