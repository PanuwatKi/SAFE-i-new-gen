package com.panuwatki.safe.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** สมุดเบอร์ของผู้ใช้ (อยู่ในเครื่องเท่านั้น) — number อยู่ในรูป PhoneNumbers.normalize() */
@Entity(tableName = "numbers")
data class NumberEntry(
    @PrimaryKey val number: String,
    val label: String,            // SCAM / SPAM / TRUSTED (CallerCheck.BookLabel)
    val category: String? = null,
    val note: String? = null,
    val autoReject: Boolean = false,
    val reportedToCommunity: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** เบอร์ไม่รู้จักที่โทรเข้าล่าสุด เก็บ RECENT_DAYS วัน เพื่อให้กด "รายงานสายเมื่อกี้" ได้ */
@Entity(tableName = "recent_calls")
data class RecentCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String?,          // null = ซ่อนเบอร์
    val at: Long,
    val level: String,            // NumberRiskLevel
    val title: String,
    val rejected: Boolean,
)

/** รายชื่อเบอร์ที่ชุมชนรายงาน (ดาวน์โหลดมาเก็บ) — เก็บเป็น hash ไม่ใช่เบอร์ */
@Entity(tableName = "community_flags")
data class CommunityFlag(
    @PrimaryKey val hashKey: String,
    val scam: Int,
    val spam: Int,
    val updatedAt: Long,
)

@Dao
interface NumberDao {
    @Query("SELECT * FROM numbers ORDER BY updatedAt DESC")
    fun observeBook(): Flow<List<NumberEntry>>

    @Query("SELECT * FROM numbers WHERE number = :number")
    suspend fun find(number: String): NumberEntry?

    @Upsert
    suspend fun upsert(entry: NumberEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entries: List<NumberEntry>): List<Long>

    @Query("DELETE FROM numbers WHERE number = :number")
    suspend fun delete(number: String)

    @Query("SELECT * FROM numbers ORDER BY updatedAt DESC")
    suspend fun allBook(): List<NumberEntry>

    // ---- สายล่าสุด ----
    @Insert
    suspend fun insertRecent(call: RecentCall): Long

    @Query("SELECT * FROM recent_calls ORDER BY at DESC")
    fun observeRecent(): Flow<List<RecentCall>>

    /** หาเบอร์ที่โทรเข้าในช่วงเวลาหนึ่ง (ใช้จับคู่กับไฟล์อัดเสียงสาย) */
    @Query("SELECT * FROM recent_calls WHERE at BETWEEN :from AND :to ORDER BY at DESC LIMIT 1")
    suspend fun recentBetween(from: Long, to: Long): RecentCall?

    @Query("DELETE FROM recent_calls WHERE at < :before")
    suspend fun purgeRecentBefore(before: Long): Int

    @Query("DELETE FROM recent_calls")
    suspend fun deleteAllRecent()

    // ---- ชุมชน ----
    @Query("SELECT * FROM community_flags WHERE hashKey = :key")
    suspend fun community(key: String): CommunityFlag?

    @Upsert
    suspend fun upsertCommunity(flags: List<CommunityFlag>)

    @Query("DELETE FROM community_flags")
    suspend fun deleteAllCommunity()
}

/** ไฟล์สมุดเบอร์สำหรับแชร์ให้ครอบครัว (ลูกหลานตั้งให้พ่อแม่) */
@Serializable
data class NumberBookFile(val format: String = FORMAT, val entries: List<Item>) {
    @Serializable
    data class Item(val number: String, val label: String, val category: String? = null, val note: String? = null)

    companion object {
        const val FORMAT = "safe-numbers-v1"
    }
}
