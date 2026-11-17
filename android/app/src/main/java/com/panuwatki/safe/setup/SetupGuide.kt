package com.panuwatki.safe.setup

/**
 * คำแนะนำการตั้งค่าตามยี่ห้อมือถือ (ใช้ในหน้าตั้งค่าแบบทีละขั้น)
 * ชื่อเมนูอาจต่างกันเล็กน้อยตามรุ่น/เวอร์ชัน — หน้าจอจะบอกผู้ใช้ไว้เสมอ
 */
object SetupGuide {

    enum class Brand(val displayName: String) {
        SAMSUNG("Samsung"),
        XIAOMI("Xiaomi / Redmi / POCO"),
        OPPO("OPPO / realme / OnePlus"),
        VIVO("vivo"),
        GOOGLE_DIALER("แอปโทรศัพท์ของ Google"),
        OTHER("ยี่ห้ออื่น"),
    }

    const val GOOGLE_DIALER_PACKAGE = "com.google.android.dialer"

    /** แอปโทรศัพท์ของ Google เก็บไฟล์อัดเสียงไว้ในแอปตัวเอง SAFE จึงอ่านไม่ได้ ต้องเช็กก่อนยี่ห้อ */
    fun detect(manufacturer: String, defaultDialerPackage: String?): Brand {
        if (defaultDialerPackage == GOOGLE_DIALER_PACKAGE) return Brand.GOOGLE_DIALER
        val m = manufacturer.lowercase()
        return when {
            "samsung" in m -> Brand.SAMSUNG
            "xiaomi" in m || "redmi" in m || "poco" in m -> Brand.XIAOMI
            "oppo" in m || "realme" in m || "oneplus" in m -> Brand.OPPO
            "vivo" in m -> Brand.VIVO
            "google" in m -> Brand.GOOGLE_DIALER
            else -> Brand.OTHER
        }
    }

    /** SAFE ใช้ "ตรวจสายอัตโนมัติหลังวางสาย" กับยี่ห้อนี้ได้ไหม */
    fun supportsAutoRecordingCheck(brand: Brand): Boolean = brand != Brand.GOOGLE_DIALER

    fun recordingSteps(brand: Brand): List<String> = when (brand) {
        Brand.SAMSUNG -> listOf(
            "เปิดแอป \"โทรศัพท์\"",
            "แตะ ⋮ (มุมขวาบน) → \"การตั้งค่า\"",
            "แตะ \"บันทึกการโทร\"",
            "เปิด \"บันทึกการโทรอัตโนมัติ\" แล้วเลือก \"เบอร์ที่ไม่ได้บันทึก\" (แนะนำ) หรือ \"ทุกสาย\"",
        )
        Brand.XIAOMI -> listOf(
            "เปิดแอป \"โทรศัพท์\"",
            "แตะ ⋮ หรือไอคอนรูปเฟือง → \"การตั้งค่า\"",
            "แตะ \"การบันทึกการโทร\"",
            "เปิด \"บันทึกการโทรอัตโนมัติ\" แล้วเลือก \"เบอร์ที่ไม่รู้จัก\" (แนะนำ) หรือ \"ทุกเบอร์\"",
        )
        Brand.OPPO -> listOf(
            "เปิดแอป \"โทรศัพท์\"",
            "แตะ ⋮ (มุมขวาบน) → \"การตั้งค่า\"",
            "แตะ \"บันทึกการโทร\"",
            "เปิด \"บันทึกการโทรอัตโนมัติ\" แล้วเลือก \"เบอร์ที่ไม่รู้จัก\" (แนะนำ) หรือ \"ทุกสาย\"",
        )
        Brand.VIVO -> listOf(
            "เปิดแอป \"โทรศัพท์\"",
            "แตะ ⋮ หรือ \"การตั้งค่า\"",
            "แตะ \"บันทึกการโทร\"",
            "เปิด \"บันทึกการโทรอัตโนมัติ\" แล้วเลือกเบอร์ที่ไม่รู้จัก (ถ้ามีตัวเลือก)",
        )
        Brand.GOOGLE_DIALER -> listOf(
            "เครื่องนี้ใช้แอปโทรศัพท์ของ Google ซึ่งเก็บไฟล์อัดเสียงไว้ในแอปตัวเอง SAFE เปิดอ่านไม่ได้",
            "จึงใช้ \"ตรวจสายอัตโนมัติหลังวางสาย\" ไม่ได้ — ข้ามขั้นนี้ได้เลย",
            "ยังใช้ \"เตือนตอนสายเข้า\" และ \"ฟังผ่านลำโพง\" ได้ตามปกติ",
        )
        Brand.OTHER -> listOf(
            "เปิดแอป \"โทรศัพท์\"",
            "หาเมนู \"การตั้งค่า\" (มักอยู่ใต้ ⋮ มุมขวาบน)",
            "หาเมนู \"บันทึกการโทร\" แล้วเปิด \"บันทึกอัตโนมัติ\"",
            "ถ้าไม่มีเมนูนี้ แปลว่าเครื่องนี้อาจอัดเสียงสายไม่ได้ ให้ใช้ \"ฟังผ่านลำโพง\" แทน",
        )
    }

    /** มือถือบางยี่ห้อปิดแอปที่ทำงานเบื้องหลังเพื่อประหยัดแบต ทำให้ SAFE ตรวจไฟล์อัดเสียงไม่ทัน */
    fun backgroundSteps(brand: Brand): List<String> = when (brand) {
        Brand.SAMSUNG -> listOf(
            "กดปุ่ม \"เปิดหน้าตั้งค่าของ SAFE\" ด้านล่าง",
            "แตะ \"แบตเตอรี่\" → เลือก \"ไม่จำกัด\"",
        )
        Brand.XIAOMI -> listOf(
            "กดปุ่ม \"เปิดหน้าตั้งค่าของ SAFE\" ด้านล่าง",
            "เปิด \"เริ่มอัตโนมัติ\" (Autostart)",
            "แตะ \"ตัวประหยัดแบตเตอรี่\" → เลือก \"ไม่มีข้อจำกัด\"",
        )
        Brand.OPPO -> listOf(
            "กดปุ่ม \"เปิดหน้าตั้งค่าของ SAFE\" ด้านล่าง",
            "แตะ \"การใช้แบตเตอรี่\" → เปิด \"อนุญาตกิจกรรมเบื้องหลัง\" และ \"อนุญาตให้เริ่มอัตโนมัติ\"",
        )
        Brand.VIVO -> listOf(
            "กดปุ่ม \"เปิดหน้าตั้งค่าของ SAFE\" ด้านล่าง",
            "แตะ \"แบตเตอรี่\" → เปิด \"อนุญาตการใช้พลังงานเบื้องหลังสูง\"",
        )
        Brand.GOOGLE_DIALER, Brand.OTHER -> listOf(
            "กดปุ่ม \"เปิดหน้าตั้งค่าของ SAFE\" ด้านล่าง",
            "แตะ \"แบตเตอรี่\" → เลือก \"ไม่จำกัด\" หรือ \"ไม่ปรับให้เหมาะสม\"",
        )
    }
}
