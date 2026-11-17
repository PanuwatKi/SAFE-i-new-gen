package com.panuwatki.safe.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
        const val SOURCE_TEXT = "text"
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

/**
 * ฐานข้อมูลในเครื่อง — เปลี่ยนโครงสร้างต้องไม่ทำให้ข้อมูลผู้ใช้หาย
 *
 * วิธีเปลี่ยนโครงสร้าง (เพิ่มตาราง/คอลัมน์):
 *   1. แก้ Entity แล้วเพิ่ม version ขึ้น 1 (เช่น 2 -> 3)
 *   2. เพิ่ม AutoMigration(from = 2, to = 3) ใน autoMigrations ด้านล่าง
 *      (ถ้าเปลี่ยนชื่อ/ลบคอลัมน์ ต้องใส่ spec = ... หรือเขียน Migration เอง ดู MIGRATIONS)
 *   3. push -> CI จะสร้างไฟล์ schema ใหม่ใน app/schemas/ -> commit ไฟล์นั้นด้วย
 *      (CI จะ fail ถ้าโครงสร้างเปลี่ยนแต่ไม่ได้ commit schema)
 * ห้ามใช้ fallbackToDestructiveMigration กับเวอร์ชันที่เคยปล่อยให้ผู้ใช้แล้ว
 */
@Database(
    entities = [AnalysisEntity::class, NumberEntry::class, RecentCall::class, CommunityFlag::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // ตัวอย่างเมื่อมีเวอร์ชัน 3: AutoMigration(from = 2, to = 3),
    ],
)
abstract class SafeDatabase : RoomDatabase() {
    abstract fun analyses(): AnalysisDao
    abstract fun numbers(): NumberDao

    companion object {
        /** ผลที่ผู้ใช้ไม่ได้ยืนยัน จะถูกลบอัตโนมัติหลังจากนี้ (ดู docs/SECURITY_POLICY.md) */
        const val RETENTION_DAYS = 30L

        /** เบอร์ไม่รู้จักที่โทรเข้า เก็บไว้กี่วัน (ดู docs/SECURITY_POLICY.md) */
        const val RECENT_DAYS = 7L

        /** Migration ที่เขียนเอง (กรณี AutoMigration ทำไม่ได้) — เพิ่มต่อท้ายตามลำดับเวอร์ชัน */
        val MIGRATIONS: Array<Migration> = arrayOf()

        @Volatile private var instance: SafeDatabase? = null

        fun get(context: Context): SafeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SafeDatabase::class.java, "safe.db")
                .addMigrations(*MIGRATIONS)
                // เวอร์ชัน 1 ไม่เคยปล่อยให้ผู้ใช้ (มีแค่ตอนพัฒนา) จึงล้างได้เฉพาะเวอร์ชันนี้
                // เวอร์ชัน 2 ขึ้นไปต้องมี migration เสมอ — ถ้าขาด แอปจะ crash แทนที่จะลบข้อมูลผู้ใช้เงียบ ๆ
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
                .also { instance = it }
        }
    }
}
