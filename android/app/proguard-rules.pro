# kotlinx.serialization: เก็บ serializer ของคลาสโมเดล
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.panuwatki.safe.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.panuwatki.safe.**$$serializer { *; }

# ไม่เขียน log ระดับ debug/verbose ในรุ่น release (กันข้อความบทสนทนาหลุดไปใน logcat)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
