package com.panuwatki.safe.data

/**
 * ส่งออกข้อมูลที่ผู้ใช้ยืนยันแล้ว เป็น CSV ฟอร์แมตเดียวกับ ml/data/Dataset*.csv
 *   text,label,category,channel
 * นำไปวางใน ml/data/contributed/ แล้วรัน python train_model.py ได้ทันที
 * (โฟลเดอร์ contributed ถูก .gitignore ไว้ — ห้ามขึ้น repo เพราะเป็นข้อมูลจากผู้ใช้)
 */
object CsvExporter {
    const val HEADER = "text,label,category,channel"
    private const val BOM = "﻿" // ให้ Excel เปิดภาษาไทยถูก

    fun toCsv(rows: List<AnalysisEntity>): String = buildString {
        append(BOM).append(HEADER).append("\r\n")
        for (r in rows) {
            val label = r.userLabel ?: continue
            append(quote(r.transcript)).append(',')
            append(label).append(',')
            append(quote(r.category ?: "")).append(',')
            append("call").append("\r\n")
        }
    }

    fun quote(field: String): String {
        val oneLine = field.replace("\r", " ").replace("\n", " ")
        return "\"" + oneLine.replace("\"", "\"\"") + "\""
    }
}

/** หมวดหมู่ให้เลือกตอนติดป้าย (อิงจากหมวดใน Dataset ที่มีอยู่) */
object Categories {
    val scam = listOf(
        "ตำรวจ/หน่วยงานรัฐ", "ธนาคาร", "พัสดุ", "ลงทุน", "งานออนไลน์", "เงินกู้", "รางวัล",
        "ฟิชชิง", "ปลอมเป็นคนรู้จัก", "romance", "โทรคมนาคม", "โอนผิดบัญชี", "คืนภาษี",
        "ค่าน้ำ-ค่าไฟ", "แอปดูดเงิน", "แบล็กเมล", "อื่น ๆ",
    )
    val normal = listOf(
        "ครอบครัว", "เพื่อน", "งาน", "ร้านค้า", "ขนส่ง", "ธนาคาร", "บริการ", "สุขภาพ",
        "นัดหมาย", "เรียน", "ทักทาย", "อื่น ๆ",
    )
}
