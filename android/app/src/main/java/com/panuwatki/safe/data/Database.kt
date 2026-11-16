package com.panuwatki.safe.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * ผลวิเคราะห์ 1 บทสนทนา — เก็บเฉพาะในเครื่อง (ไม่ส่งออกไปไหนเอง)
 * - transcript ผ่าน PiiRedactor แล้วเสมอ (ไม่มีเบอร์/เลขบัญชี/เลขบัตร/ลิงก์)
 * - ไม่เก็บไฟล์เสียง และไม่เก็บเบอร์โทรของคู่สาย
 * - userLabel = null คือผู้ใช้ยังไม่ได้ยืนยัน (ลบอัตโนมัติเมื่อครบ RETENTION_DAYS)
 */
@Entity(tableName = "analyses")
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val source: String,
    val transcript: String,
    val percent: Double,
    val color: String,
    val userLabel: Int? = null,
    val category: String? = null,
) {
    companion object {
        const val SOURCE_LIVE = "live"
        const val SOURCE_RECORDING = "recording"
        const val LABEL_NORMAL = 0
        const val LABEL_SCAM = 1
    }
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun get(id: Long): AnalysisEntity?

    @Insert
    suspend fun insert(entity: AnalysisEntity): Long

    @Query("UPDATE analyses SET userLabel = :label, category = :category, transcript = :transcript WHERE id = :id")
    suspend fun label(id: Long, label: Int, category: String?, transcript: String)

    @Query("SELECT * FROM analyses WHERE userLabel IS NOT NULL ORDER BY createdAt")
    suspend fun labeled(): List<AnalysisEntity>

    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM analyses")
    suspend fun deleteAll()

    @Query("DELETE FROM analyses WHERE userLabel IS NULL AND createdAt < :before")
    suspend fun purgeUnlabeledBefore(before: Long): Int
}

@Database(
    entities = [AnalysisEntity::class, NumberEntry::class, RecentCall::class, CommunityFlag::class],
    version = 2,
    exportSchema = false,
)
abstract class SafeDatabase : RoomDatabase() {
    abstract fun analyses(): AnalysisDao
    abstract fun numbers(): NumberDao

    companion object {
        /** ผลที่ผู้ใช้ไม่ได้ยืนยัน จะถูกลบอัตโนมัติหลังจากนี้ (ดู docs/SECURITY_POLICY.md) */
        const val RETENTION_DAYS = 30L

        /** เบอร์ไม่รู้จักที่โทรเข้า เก็บไว้กี่วัน (ดู docs/SECURITY_POLICY.md) */
        const val RECENT_DAYS = 7L

        @Volatile private var instance: SafeDatabase? = null

        fun get(context: Context): SafeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SafeDatabase::class.java, "safe.db")
                // ⚠️ ยังไม่เปิดตัวบน Play จึงล้างฐานข้อมูลเมื่อเปลี่ยนโครงสร้างได้
                // ก่อนเผยแพร่จริง ต้องเปลี่ยนเป็น Migration ที่เก็บข้อมูลผู้ใช้ไว้
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
