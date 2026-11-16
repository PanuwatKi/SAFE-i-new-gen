package com.panuwatki.safe.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.panuwatki.safe.ml.RiskColor

object RiskColors {
    val Red = Color(0xFFC62828)
    val Yellow = Color(0xFFF9A825)
    val Green = Color(0xFF2E7D32)

    fun of(c: RiskColor): Color = when (c) {
        RiskColor.RED -> Red
        RiskColor.YELLOW -> Yellow
        RiskColor.GREEN -> Green
    }

    fun onColor(c: RiskColor): Color = if (c == RiskColor.YELLOW) Color.Black else Color.White

    fun title(c: RiskColor): String = when (c) {
        RiskColor.RED -> "เสี่ยงมิจฉาชีพสูงมาก"
        RiskColor.YELLOW -> "น่าสงสัย ระวังไว้"
        RiskColor.GREEN -> "ปลอดภัย"
    }

    fun emoji(c: RiskColor): String = when (c) {
        RiskColor.RED -> "🔴"
        RiskColor.YELLOW -> "🟡"
        RiskColor.GREEN -> "🟢"
    }
}

private val Brand = Color(0xFF1565C0)

private val LightScheme = lightColorScheme(primary = Brand, secondary = Color(0xFF00897B))
private val DarkScheme = darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF80CBC4))

/** ตัวอักษรใหญ่กว่าค่าปกติ เพื่อให้ผู้สูงอายุอ่านง่าย */
private val BigTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontSize = 34.sp, lineHeight = 42.sp),
        headlineMedium = t.headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp),
        titleLarge = t.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
        titleMedium = t.titleMedium.copy(fontSize = 20.sp, lineHeight = 28.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 19.sp, lineHeight = 28.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
        labelLarge = t.labelLarge.copy(fontSize = 19.sp),
    )
}

@Composable
fun SafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = BigTypography,
        content = content,
    )
}

val HugeNumber = TextStyle(fontSize = 64.sp, lineHeight = 70.sp)
