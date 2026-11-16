package com.panuwatki.safe.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.panuwatki.safe.BuildConfig
import com.panuwatki.safe.calls.PhoneNumbers
import com.panuwatki.safe.data.CommunityFlag
import com.panuwatki.safe.data.Prefs
import com.panuwatki.safe.data.SafeDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * ฐานข้อมูลเบอร์ของชุมชน (Firebase Firestore) — ไม่บังคับ ปิดเป็นค่าเริ่มต้น
 *
 * สิ่งที่ส่งขึ้นคลาวด์ (เฉพาะตอนผู้ใช้กด "รายงาน"):
 *   - hash ของเบอร์ (PhoneNumbers.communityKey) — ไม่ส่งเบอร์ตรง ๆ
 *   - ประเภท: scam / spam และหมวด (เลือกจากรายการ ไม่มีข้อความอิสระ)
 *   - รหัสผู้ใช้นิรนามของ Firebase (ไม่ผูกกับชื่อ อีเมล หรือเบอร์ของผู้ใช้)
 * สิ่งที่ดาวน์โหลดมา: รายการ hash + จำนวนรายงาน -> เก็บในเครื่อง แล้วเช็กตอนสายเข้า "ในเครื่อง"
 *   เซิร์ฟเวอร์จึงไม่รู้ว่าใครโทรหาผู้ใช้
 * กฎฝั่งเซิร์ฟเวอร์ (1 คนรายงาน 1 เบอร์ได้ครั้งเดียว, วันละไม่เกิน 20 ครั้ง): firebase/firestore.rules
 */
object CommunityReports {

    const val DAILY_LIMIT = 20
    private const val PAGE = 500
    private val lock = Mutex()

    enum class Type(val field: String) { SCAM("scam"), SPAM("spam") }

    class AlreadyReported : Exception("คุณเคยรายงานเบอร์นี้แล้ว")
    class DailyLimit : Exception("วันนี้รายงานครบ $DAILY_LIMIT ครั้งแล้ว ลองใหม่พรุ่งนี้")
    class NotActive : Exception("ยังไม่ได้เปิดฐานข้อมูลชุมชน")

    /** ผู้พัฒนาใส่ค่า Firebase ไว้ตอน build หรือยัง (ไม่ใส่ = แอปทำงานแบบในเครื่องอย่างเดียว) */
    fun isConfigured(): Boolean =
        BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank()

    fun isActive(context: Context): Boolean {
        val prefs = Prefs(context)
        return isConfigured() && prefs.hasConsent && prefs.communityEnabled
    }

    private suspend fun app(context: Context): FirebaseApp = lock.withLock {
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(
                context.applicationContext,
                FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .build(),
            ).also { AppCheckSetup.install(it) }
    }

    private suspend fun uid(app: FirebaseApp): String {
        val auth = FirebaseAuth.getInstance(app)
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    /** รายงานเบอร์ขึ้นคลาวด์ (ต้องเปิดฐานข้อมูลชุมชนก่อน) */
    suspend fun report(context: Context, normalizedNumber: String, type: Type, category: String?): Result<Unit> = runCatching {
        if (!isActive(context)) throw NotActive()
        val app = app(context)
        val uid = uid(app)
        val db = FirebaseFirestore.getInstance(app)
        val key = PhoneNumbers.communityKey(normalizedNumber)
        val numberRef = db.collection("numbers").document(key)
        val reportRef = numberRef.collection("reports").document(uid)
        val userRef = db.collection("users").document(uid)
        val today = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)

        db.runTransaction { tx ->
            if (tx.get(reportRef).exists()) throw AlreadyReported()
            val user = tx.get(userRef)
            val count = if (user.getLong("day") == today) (user.getLong("count") ?: 0L) + 1 else 1L
            if (count > DAILY_LIMIT) throw DailyLimit()
            tx.set(
                reportRef,
                mapOf("type" to type.field, "category" to (category ?: "").take(40), "createdAt" to FieldValue.serverTimestamp()),
            )
            tx.set(userRef, mapOf("day" to today, "count" to count))
            tx.set(
                numberRef,
                mapOf(type.field to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            Unit
        }.await()
    }.recoverCatching { e ->
        // runTransaction ห่อ exception ของเราไว้ข้างใน -> แกะออกมาให้ข้อความอ่านรู้เรื่อง
        throw generateSequence(e) { it.cause }.firstOrNull { it is AlreadyReported || it is DailyLimit } ?: e
    }

    /** ดาวน์โหลดรายชื่อเบอร์ที่ถูกรายงาน (เฉพาะที่เปลี่ยนตั้งแต่ครั้งก่อน) มาเก็บในเครื่อง */
    suspend fun sync(context: Context): Result<Int> = runCatching {
        if (!isActive(context)) throw NotActive()
        val app = app(context)
        uid(app)
        val prefs = Prefs(context)
        val dao = SafeDatabase.get(context).numbers()
        val db = FirebaseFirestore.getInstance(app)
        var since = prefs.communityLastSyncMs
        var total = 0
        while (true) {
            val snap = db.collection("numbers")
                .whereGreaterThan("updatedAt", Timestamp(Date(since)))
                .orderBy("updatedAt", Query.Direction.ASCENDING)
                .limit(PAGE.toLong())
                .get().await()
            val flags = snap.documents.mapNotNull { d ->
                val updated = d.getTimestamp("updatedAt")?.toDate()?.time ?: return@mapNotNull null
                CommunityFlag(d.id, (d.getLong("scam") ?: 0L).toInt(), (d.getLong("spam") ?: 0L).toInt(), updated)
            }
            if (flags.isEmpty()) break
            dao.upsertCommunity(flags)
            total += flags.size
            since = flags.maxOf { it.updatedAt }
            prefs.communityLastSyncMs = since
            if (snap.size() < PAGE) break
        }
        total
    }

    fun enable(context: Context) {
        Prefs(context).communityEnabled = true
        val wm = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CommunitySyncWorker>(12, TimeUnit.HOURS).setConstraints(network).build(),
        )
        wm.enqueueUniqueWork(
            SYNC_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CommunitySyncWorker>().setConstraints(network).build(),
        )
    }

    /** ปิดฐานข้อมูลชุมชน: หยุดซิงก์ ลบรายชื่อที่ดาวน์โหลดไว้ และออกจากบัญชีนิรนาม */
    suspend fun disable(context: Context) {
        val prefs = Prefs(context)
        prefs.communityEnabled = false
        prefs.communityLastSyncMs = 0L
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_NOW)
        SafeDatabase.get(context).numbers().deleteAllCommunity()
        if (isConfigured()) {
            FirebaseApp.getApps(context).firstOrNull()?.let { FirebaseAuth.getInstance(it).signOut() }
        }
    }

    private const val SYNC_PERIODIC = "community-sync"
    private const val SYNC_NOW = "community-sync-now"
}

class CommunitySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!CommunityReports.isActive(applicationContext)) return Result.success()
        return if (CommunityReports.sync(applicationContext).isSuccess) Result.success() else Result.retry()
    }
}
