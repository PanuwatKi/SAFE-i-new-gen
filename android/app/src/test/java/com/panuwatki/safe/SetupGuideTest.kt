package com.panuwatki.safe

import com.panuwatki.safe.setup.SetupGuide
import com.panuwatki.safe.setup.SetupGuide.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideTest {

    @Test
    fun detectsBrandFromManufacturer() {
        assertEquals(Brand.SAMSUNG, SetupGuide.detect("samsung", "com.samsung.android.dialer"))
        assertEquals(Brand.XIAOMI, SetupGuide.detect("Xiaomi", "com.android.contacts"))
        assertEquals(Brand.XIAOMI, SetupGuide.detect("Redmi", null))
        assertEquals(Brand.OPPO, SetupGuide.detect("OPPO", null))
        assertEquals(Brand.OPPO, SetupGuide.detect("realme", null))
        assertEquals(Brand.OPPO, SetupGuide.detect("OnePlus", null))
        assertEquals(Brand.VIVO, SetupGuide.detect("vivo", null))
        assertEquals(Brand.OTHER, SetupGuide.detect("HONOR", null))
    }

    @Test
    fun googleDialerWinsOverManufacturer() {
        // มือถือ Xiaomi/OPPO รุ่นใหม่บางรุ่นใช้แอปโทรศัพท์ของ Google -> ไฟล์อัดเสียงอ่านไม่ได้
        assertEquals(Brand.GOOGLE_DIALER, SetupGuide.detect("Xiaomi", SetupGuide.GOOGLE_DIALER_PACKAGE))
        assertEquals(Brand.GOOGLE_DIALER, SetupGuide.detect("Google", null))
        assertFalse(SetupGuide.supportsAutoRecordingCheck(Brand.GOOGLE_DIALER))
        assertTrue(SetupGuide.supportsAutoRecordingCheck(Brand.SAMSUNG))
    }

    @Test
    fun everyBrandHasInstructions() {
        Brand.entries.forEach { b ->
            assertTrue("recording steps for $b", SetupGuide.recordingSteps(b).isNotEmpty())
            assertTrue("background steps for $b", SetupGuide.backgroundSteps(b).isNotEmpty())
        }
    }
}
