import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// ค่า Firebase (ไม่บังคับ) — ถ้าไม่มีไฟล์นี้ แอปยัง build ได้ และใช้สมุดเบอร์แบบในเครื่องอย่างเดียว
// วิธีสร้างไฟล์: docs/FIREBASE_SETUP.md
val firebaseProps = Properties().apply {
    val f = rootProject.file("firebase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun fb(key: String): String = "\"" + (firebaseProps.getProperty(key) ?: "") + "\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.panuwatki.safe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.panuwatki.safe"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "FIREBASE_PROJECT_ID", fb("projectId"))
        buildConfigField("String", "FIREBASE_APP_ID", fb("appId"))
        buildConfigField("String", "FIREBASE_API_KEY", fb("apiKey"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// เก็บโครงสร้างฐานข้อมูลทุกเวอร์ชันไว้ใน repo -> ใช้ทำ AutoMigration และตรวจใน CI
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    // ฐานข้อมูลเบอร์ชุมชน (ใช้เฉพาะเมื่อผู้ใช้เปิดเอง)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
