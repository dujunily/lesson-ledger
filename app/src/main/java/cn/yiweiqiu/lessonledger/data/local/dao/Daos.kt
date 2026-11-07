package cn.yiweiqiu.lessonledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import cn.yiweiqiu.lessonledger.data.local.entity.ChildEntity
import cn.yiweiqiu.lessonledger.data.local.entity.CourseEntity
import cn.yiweiqiu.lessonledger.data.local.entity.LessonEntity
import cn.yiweiqiu.lessonledger.data.local.entity.PaymentEntity
import cn.yiweiqiu.lessonledger.data.local.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children ORDER BY createdAt ASC")
    suspend fun getAll(): List<ChildEntity>

    @Query("SELECT * FROM children WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): ChildEntity?

    @Upsert
    suspend fun upsert(entity: ChildEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ChildEntity>)

    @Query("SELECT COUNT(*) FROM children WHERE updatedAt > :since")
    suspend fun countDirty(since: Long): Int

    @Query("DELETE FROM children")
    suspend fun clear()
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY createdAt ASC")
    suspend fun getAll(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE childId = :childId ORDER BY createdAt ASC")
    suspend fun byChild(childId: String): List<CourseEntity>

    @Upsert
    suspend fun upsert(entity: CourseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CourseEntity>)

    /** 冗余汇总字段维护，不触碰 updatedAt，避免制造脏记录 */
    @Query("UPDATE courses SET totalLessonsBoughtHalf = :half WHERE id = :id")
    suspend fun setTotalBought(id: String, half: Long)

    @Query("SELECT COUNT(*) FROM courses WHERE updatedAt > :since")
    suspend fun countDirty(since: Long): Int

    @Query("DELETE FROM courses")
    suspend fun clear()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY payDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY payDate DESC, createdAt DESC")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE courseId = :courseId ORDER BY payDate ASC, createdAt ASC")
    suspend fun byCourse(courseId: String): List<PaymentEntity>

    @Upsert
    suspend fun upsert(entity: PaymentEntity)

    @Upsert
    suspend fun upsertAll(entities: List<PaymentEntity>)

    @Query("SELECT COUNT(*) FROM payments WHERE updatedAt > :since")
    suspend fun countDirty(since: Long): Int

    @Query("DELETE FROM payments")
    suspend fun clear()
}

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons ORDER BY scheduledAt ASC")
    suspend fun getAll(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): LessonEntity?

    @Query("SELECT * FROM lessons WHERE courseId = :courseId ORDER BY scheduledAt ASC")
    suspend fun byCourse(courseId: String): List<LessonEntity>

    @Upsert
    suspend fun upsert(entity: LessonEntity)

    @Upsert
    suspend fun upsertAll(entities: List<LessonEntity>)

    /** FIFO 批次回填：只改 paymentId，不改 updatedAt（该字段是系统派生值） */
    @Query("UPDATE lessons SET paymentId = :paymentId WHERE id = :id")
    suspend fun setPaymentId(id: String, paymentId: String?)

    @Query("SELECT COUNT(*) FROM lessons WHERE updatedAt > :since")
    suspend fun countDirty(since: Long): Int

    @Query("DELETE FROM lessons")
    suspend fun clear()
}

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(log: SyncLogEntity): Long

    @Query("SELECT * FROM sync_logs ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SyncLogEntity>>

    @Query("DELETE FROM sync_logs WHERE id NOT IN (SELECT id FROM sync_logs ORDER BY at DESC, id DESC LIMIT 200)")
    suspend fun trim()

    @Query("DELETE FROM sync_logs")
    suspend fun clear()
}
